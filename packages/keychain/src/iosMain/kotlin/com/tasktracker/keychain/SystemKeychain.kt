package com.tasktracker.keychain

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.UByteVar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreFoundation.CFBooleanRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecCopyErrorMessageString
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccessGroup
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

private const val DEFAULT_SERVICE = "com.tasktracker.app"

/**
 * `Keychain` backed storage using generic-password items scoped to a service name.
 *
 * Written against the CoreFoundation C API rather than the Objective-C one. The obvious
 * `platform.Foundation` route is not usable here: `asNSDictionary` does not exist in
 * this Kotlin version, `NSData` exposes no byte constructor, and using a `CFStringRef`
 * as an `NSCopying` dictionary key needs a toll-free-bridge cast the compiler cannot
 * check. `CFDictionaryCreateMutable` + `CFDictionarySetValue` take the `CFStringRef`
 * constants as they are, so nothing has to be bridged or cast.
 *
 * Wrapped in a [Mutex] for the same reason the original was an actor: the `Security`
 * framework is itself thread-safe, but a single serialisation point is wanted anyway so
 * there is one place to add auditing.
 */
@OptIn(ExperimentalForeignApi::class)
class SystemKeychain(
    private val service: String = DEFAULT_SERVICE,
    private val accessGroup: String? = null,
) : KeychainAccess {

    private val mutex = Mutex()

    override suspend fun data(key: String): ByteArray? = mutex.withLock {
        withQuery { query ->
            query.put(kSecClass, kSecClassGenericPassword)
            query.putServiceAndAccount(service, key, accessGroup)
            query.put(kSecReturnData, kCFBooleanTrue)
            query.put(kSecMatchLimit, kSecMatchLimitOne)

            memScoped {
                // Allocated explicitly rather than via the `ptr` operator: the
                // out-parameter is a CPointerVarOf, and a scratch variable's `.ptr`
                // resolves to the CVariableScope operator instead, which is not this type.
                val result = alloc<CPointerVarOf<CPointer<out CPointed>>>()
                val status = SecItemCopyMatching(query.build(), result.ptr)
                when (status) {
                    errSecSuccess -> result.value.readData()
                    // Absent is a normal state, not a failure.
                    errSecItemNotFound -> null
                    else -> throw KeychainError(status, "read", key, reasonFor(status))
                }
            }
        }
    }

    override suspend fun set(data: ByteArray, key: String): Unit = mutex.withLock {
        val payload = data.toCFData()
        try {
            withQuery { query ->
                query.put(kSecClass, kSecClassGenericPassword)
                query.putServiceAndAccount(service, key, accessGroup)
                val base = query.build()

                // Update first: adding a key that already exists fails with a
                // duplicate-item error, so update-then-insert is the shape worth keeping.
                val updateStatus = SecItemUpdate(
                    base,
                    oneDataEntry(kSecValueData, payload),
                )
                if (updateStatus == errSecSuccess) return@withQuery

                val insertStatus = SecItemAdd(
                    withQuery { insert ->
                        insert.put(kSecClass, kSecClassGenericPassword)
                        insert.putServiceAndAccount(service, key, accessGroup)
                        insert.put(kSecValueData, payload)
                        // After-first-unlock, this-device-only: background sync can read the
                        // token after a reboot, but a token restored from a backup onto
                        // another device is one the user never authorised there, so
                        // `ThisDeviceOnly` is the point rather than a default.
                        insert.put(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                        insert.build()
                    },
                    null,
                )
                if (insertStatus != errSecSuccess) {
                    throw KeychainError(insertStatus, "insert", key, reasonFor(insertStatus))
                }
            }
        } finally {
            CFRelease(payload)
        }
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        withQuery { query ->
            query.put(kSecClass, kSecClassGenericPassword)
            query.putServiceAndAccount(service, key, accessGroup)
            val status = SecItemDelete(query.build())
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw KeychainError(status, "delete", key, reasonFor(status))
            }
        }
    }

    override suspend fun removeAll(): Unit = mutex.withLock {
        withQuery { query ->
            query.put(kSecClass, kSecClassGenericPassword)
            query.put(kSecAttrService, service)
            val status = SecItemDelete(query.build())
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw KeychainError(status, "deleteAll", "*", reasonFor(status))
            }
        }
    }

    /**
     * `SecCopyErrorMessageString` hands back a +1 reference, so it has to be read and then
     * released; leaking it would accumulate one `CFString` per failed keychain call for
     * the life of the process.
     */
    private fun reasonFor(status: Int): String {
        val message = SecCopyErrorMessageString(status, null) ?: return "OSStatus $status"
        return try {
            message.toString()
        } finally {
            CFRelease(message)
        }
    }
}

/**
 * Builds a keychain query dictionary and releases the `CFString`s it allocated from
 * Kotlin strings.
 *
 * The owned references are released when the block returns rather than in [build],
 * because the same query object is built at most once but a caller may also want to
 * inspect it; a `build()` that released would be a double-release waiting to happen.
 * `CFDictionary` retains its keys and values, so the caller's +1 references are dead
 * once the dictionary has been handed to `Security`.
 */
@OptIn(ExperimentalForeignApi::class)
private inline fun <T> withQuery(block: (CFQuery) -> T): T {
    val query = CFQuery()
    return try {
        block(query)
    } finally {
        query.releaseOwnedStrings()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CFQuery {
    private val dictionary: CFDictionaryRef? =
        CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
    private val owned = mutableListOf<CFStringRef?>()

    fun put(key: CFStringRef?, value: CFStringRef?) {
        CFDictionarySetValue(dictionary, key, value)
    }

    fun put(key: CFStringRef?, value: String) {
        val created = CFStringCreateWithCString(null, value, kCFStringEncodingUTF8)
        owned += created
        CFDictionarySetValue(dictionary, key, created)
    }

    fun put(key: CFStringRef?, value: CFBooleanRef?) =
        CFDictionarySetValue(dictionary, key, value)

    fun put(key: CFStringRef?, value: CFDataRef?) =
        CFDictionarySetValue(dictionary, key, value)

    fun putServiceAndAccount(service: String, account: String, accessGroup: String?) {
        put(kSecAttrService, service)
        put(kSecAttrAccount, account)
        accessGroup?.let { put(kSecAttrAccessGroup, it) }
    }

    fun build(): CFDictionaryRef? = dictionary

    fun releaseOwnedStrings() = owned.forEach { CFRelease(it) }
}

/** The attributes-only dictionary `SecItemUpdate` takes. */
@OptIn(ExperimentalForeignApi::class)
private fun oneDataEntry(key: CFStringRef?, value: CFDataRef?): CFDictionaryRef? =
    CFQuery().apply { put(key, value) }.build()

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toCFData(): CFDataRef? = usePinned { pinned ->
    CFDataCreate(null, pinned.addressOf(0).reinterpret<UByteVar>(), size.toLong())
}

/** Reads a `CFDataRef` out of a `SecItemCopyMatching` result. */
@OptIn(ExperimentalForeignApi::class)
private fun CPointer<out CPointed>?.readData(): ByteArray? {
    // A plain pointer-to-pointer cast rather than `reinterpret`: `CFDataRef` is a
    // *nullable* typealias, and `reinterpret<T>()` requires a non-null `NativePointed`
    // target. Both sides are pointers to opaque structs, so the cast is a no-op.
    val data = this as CFDataRef? ?: return null
    val size = CFDataGetLength(data).toInt()
    if (size == 0) return ByteArray(0)
    val source = CFDataGetBytePtr(data) ?: return null
    return ByteArray(size).also { out ->
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), source, size.toULong()) }
    }
}

/**
 * The real iOS keychain. [host] is unused here; see [systemKeychain] in commonMain for
 * why the parameter exists at all.
 */
actual fun systemKeychain(host: Any?): KeychainAccess = SystemKeychain()
