package com.tasktracker.keychain

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

/**
 * The `Security.framework` entry points the keychain needs, and the CoreFoundation calls
 * required to build their arguments.
 *
 * `SecItem*` speaks in `CFDictionary`s of `CFTypeRef`s, so this is two libraries rather than
 * one: `Security` for the four item operations, `CoreFoundation` for constructing and
 * releasing the dictionaries they take. The JDK ships no binding to either, which is the
 * whole reason `jvmMain` needs JNA.
 *
 * ## Why the constants are read out of the framework instead of rebuilt
 *
 * `kSecClass`, `kSecAttrService` and friends are exported *global variables* holding
 * `CFStringRef`s, not `#define`s. The tempting shortcut is to recreate them with
 * `CFStringCreateWithCString("class")` and let CFEqual-based dictionary key comparison make it
 * work. That mostly works and is a trap: it silently depends on using the same callbacks the
 * framework would have, and it turns a typo in an attribute name into a query that matches
 * nothing rather than an error. [resolve] reads the real symbols, so a wrong name fails at
 * load with `UnsatisfiedLinkError` naming it instead of returning "no item".
 *
 * ## Ownership
 *
 * The two kinds of pointer here must never be confused. Everything reached through [resolve]
 * is owned by the framework and must **not** be released. Everything created through
 * [cfString] or [cfData] is owned by us and must be — that is the difference between [put] and
 * [putOwned], and [withDictionary] is what makes the second kind impossible to leak on an
 * early exit.
 */
internal interface CoreFoundationLibrary : Library {
    fun CFStringCreateWithCString(allocator: Pointer?, cString: ByteArray, encoding: Int): Pointer?
    fun CFDataCreate(allocator: Pointer?, bytes: ByteArray, length: Long): Pointer?
    fun CFDataGetBytePtr(data: Pointer): Pointer?
    fun CFDataGetLength(data: Pointer): Long
    fun CFDictionaryCreateMutable(
        allocator: Pointer?,
        capacity: Long,
        keyCallBacks: Pointer?,
        valueCallBacks: Pointer?,
    ): Pointer?

    fun CFDictionarySetValue(dictionary: Pointer, key: Pointer, value: Pointer)
    fun CFRelease(value: Pointer?)
}

/** The four item operations. `OSStatus` is a signed 32-bit int; 0 is success. */
internal interface SecurityLibrary : Library {
    fun SecItemAdd(attributes: Pointer, result: Pointer?): Int
    fun SecItemCopyMatching(query: Pointer, result: Pointer?): Int
    fun SecItemUpdate(query: Pointer, attributesToUpdate: Pointer): Int
    fun SecItemDelete(query: Pointer): Int
}

/**
 * The loaded frameworks, the resolved constants, and the ownership helpers.
 *
 * Loading happens once, lazily, in an `object`: the frameworks are process-wide singletons and
 * re-loading them per call would both cost and leak. Nothing here is reached unless the
 * process is a Desktop app on macOS; see `PlatformKeychain.jvm.kt`.
 */
internal object MacOs {

    private val coreFoundation: CoreFoundationLibrary =
        Native.load("CoreFoundation", CoreFoundationLibrary::class.java)

    private val security: SecurityLibrary =
        Native.load("Security", SecurityLibrary::class.java)

    private val coreFoundationLibrary: NativeLibrary = NativeLibrary.getInstance("CoreFoundation")

    private val securityLibrary: NativeLibrary = NativeLibrary.getInstance("Security")

    /** `kCFStringEncodingUTF8`. */
    private const val CF_STRING_ENCODING_UTF8 = 0x0800_0100

    /**
     * The `CFStringRef` stored in an exported global variable.
     *
     * The indirection is the point and is easy to get wrong: the exported symbol is the
     * *address of the variable*, so the `CFStringRef` is what that address points at. Passing
     * the address itself would hand `SecItem*` a pointer to a pointer, and it would reject the
     * dictionary.
     */
    private fun resolve(library: NativeLibrary, symbol: String): Pointer =
        library.getGlobalVariableAddress(symbol).getPointer(0)
            ?: throw KeychainError(STATUS_SYMBOL, "load", symbol, "$symbol resolved to a null pointer")

    /**
     * Resolved once, on first use.
     *
     * `kSecAttrAccessibleAfterFirstUnlock` is the attribute the iOS implementation uses and
     * the Android one documents: a token has to be readable after a reboot before the first
     * unlock, or background sync stops working on a locked device. The default,
     * `kSecAttrAccessibleWhenUnlocked`, is stronger and would break that.
     */
    private val constants: Map<String, Pointer> by lazy {
        listOf(
            "kSecClass",
            "kSecClassGenericPassword",
            "kSecAttrService",
            "kSecAttrAccount",
            "kSecValueData",
            "kSecReturnData",
            "kSecMatchLimit",
            "kSecMatchLimitOne",
            "kSecMatchLimitAll",
            "kSecAttrAccessible",
            "kSecAttrAccessibleAfterFirstUnlock",
        ).associateWith { resolve(securityLibrary, it) } +
            ("kCFBooleanTrue" to resolve(coreFoundationLibrary, "kCFBooleanTrue"))
    }

    fun constant(symbol: String): Pointer =
        constants[symbol] ?: throw KeychainError(STATUS_SYMBOL, "load", symbol, "unknown symbol $symbol")

    // ---------------------------------------------------------------------------------
    // The constants, by name
    // ---------------------------------------------------------------------------------

    val kSecClass get() = constant("kSecClass")
    val kSecClassGenericPassword get() = constant("kSecClassGenericPassword")
    val kSecAttrService get() = constant("kSecAttrService")
    val kSecAttrAccount get() = constant("kSecAttrAccount")
    val kSecValueData get() = constant("kSecValueData")
    val kSecReturnData get() = constant("kSecReturnData")
    val kSecMatchLimit get() = constant("kSecMatchLimit")
    val kSecMatchLimitOne get() = constant("kSecMatchLimitOne")

    /**
     * Required for a namespace wipe, and the reason is not obvious.
     *
     * `SecItemDelete` treats an unspecified `kSecMatchLimit` as "one", the same default
     * `SecItemCopyMatching` has. Measured on macOS 26 with two items under one
     * `kSecAttrService`: a query naming only the class and the service deleted exactly one of
     * them and returned `errSecSuccess`, so a sign-out left the second token in the keychain
     * with nothing to report it. `kSecMatchLimitAll` is what makes the delete cover every
     * match.
     */
    val kSecMatchLimitAll get() = constant("kSecMatchLimitAll")
    val kSecAttrAccessible get() = constant("kSecAttrAccessible")
    val kSecAttrAccessibleAfterFirstUnlock get() = constant("kSecAttrAccessibleAfterFirstUnlock")
    val kCFBooleanTrue get() = constant("kCFBooleanTrue")

    // ---------------------------------------------------------------------------------
    // Ownership helpers
    // ---------------------------------------------------------------------------------

    /**
     * A string as a `CFStringRef` we own.
     *
     * `Native.toByteArray` rather than `encodeToByteArray` because the C string needs its NUL
     * terminator; a byte array without one makes `CFStringCreateWithCString` read past the end
     * of the buffer.
     */
    fun cfString(value: String): Pointer =
        coreFoundation.CFStringCreateWithCString(
            null,
            Native.toByteArray(value),
            CF_STRING_ENCODING_UTF8,
        ) ?: throw KeychainError(STATUS_CREATE, "create", value, "CFStringCreateWithCString returned null")

    /** Bytes as a `CFDataRef` we own. */
    fun cfData(bytes: ByteArray): Pointer =
        coreFoundation.CFDataCreate(null, bytes, bytes.size.toLong())
            ?: throw KeychainError(STATUS_CREATE, "create", "data", "CFDataCreate returned null")

    /** The bytes inside a `CFDataRef`, copied out of it. */
    fun bytesOf(data: Pointer): ByteArray {
        val length = coreFoundation.CFDataGetLength(data).toInt()
        val pointer = coreFoundation.CFDataGetBytePtr(data)
            ?: throw KeychainError(STATUS_CORRUPT, "read", "data", "CFDataGetBytePtr returned null")
        return pointer.getByteArray(0, length)
    }

    fun release(value: Pointer?) = coreFoundation.CFRelease(value)

    /**
     * Builds a mutable dictionary with `kCFType` callbacks, runs [block], then releases the
     * dictionary and every owned value [putOwned] recorded.
     *
     * The tracking is why this exists rather than each caller pairing `CFDictionaryCreate`
     * with a `finally`: a key or value added to the dictionary is retained *by* it, so the
     * caller's own reference is the one that leaks if forgotten, and every path out of a
     * `SecItem*` call — including the throwing ones — has to drop it.
     *
     * Not `inline`: it reads private members of this object, which the compiler forbids an
     * inline function to do.
     */
    fun <T> withDictionary(block: (dictionary: Pointer, owned: MutableList<Pointer>) -> T): T {
        val dictionary = coreFoundation.CFDictionaryCreateMutable(
            null,
            0,
            coreFoundationLibrary.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
            coreFoundationLibrary.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"),
        ) ?: throw KeychainError(STATUS_CREATE, "create", "dictionary", "CFDictionaryCreateMutable returned null")

        val owned = mutableListOf<Pointer>()
        try {
            return block(dictionary, owned)
        } finally {
            owned.forEach { coreFoundation.CFRelease(it) }
            coreFoundation.CFRelease(dictionary)
        }
    }

    /** Sets a framework-owned constant. Never released — see the ownership note above. */
    fun put(dictionary: Pointer, key: Pointer, frameworkOwned: Pointer) {
        coreFoundation.CFDictionarySetValue(dictionary, key, frameworkOwned)
    }

    /** Sets a value we created, and records it so [withDictionary] releases it. */
    fun putOwned(dictionary: Pointer, key: Pointer, value: Pointer, owned: MutableList<Pointer>) {
        coreFoundation.CFDictionarySetValue(dictionary, key, value)
        owned += value
    }

    // ---------------------------------------------------------------------------------
    // The item operations
    // ---------------------------------------------------------------------------------

    fun add(attributes: Pointer): Int = security.SecItemAdd(attributes, null)

    /**
     * The stored bytes, or `null` on `errSecItemNotFound` — which is a normal answer, not a
     * failure, and the one case every caller has to distinguish.
     *
     * The `CFDataRef` handed back is ours, so it is released after the bytes are copied.
     */
    fun copyMatching(query: Pointer): ByteArray? {
        val out = PointerByReference()
        return when (val status = security.SecItemCopyMatching(query, out.pointer)) {
            SEC_SUCCESS -> {
                val data = out.value
                    ?: throw KeychainError(STATUS_CORRUPT, "read", "data", "SecItemCopyMatching returned no data")
                try {
                    bytesOf(data)
                } finally {
                    coreFoundation.CFRelease(data)
                }
            }

            SEC_ITEM_NOT_FOUND -> null
            else -> throw KeychainError(status, "read", "matching item", reason(status))
        }
    }

    fun update(query: Pointer, attributes: Pointer) {
        val status = security.SecItemUpdate(query, attributes)
        if (status != SEC_SUCCESS) throw KeychainError(status, "update", "matching item", reason(status))
    }

    fun delete(query: Pointer) {
        val status = security.SecItemDelete(query)
        // Deleting something that is not there is the state the caller asked for.
        if (status != SEC_SUCCESS && status != SEC_ITEM_NOT_FOUND) {
            throw KeychainError(status, "delete", "matching item", reason(status))
        }
    }

    /** `OSStatus` as words, since the raw number is opaque in a log. */
    fun reason(status: Int): String = when (status) {
        SEC_DUPLICATE_ITEM -> "an item with these attributes already exists (errSecDuplicateItem)"
        SEC_ITEM_NOT_FOUND -> "no matching item (errSecItemNotFound)"
        SEC_AUTH_FAILED -> "the keychain refused the request (errSecAuthFailed)"
        SEC_USER_CANCELED -> "the user cancelled the keychain prompt (errSecUserCanceled)"
        else -> "OSStatus $status"
    }
}

internal const val SEC_SUCCESS = 0
internal const val SEC_DUPLICATE_ITEM = -25299
internal const val SEC_ITEM_NOT_FOUND = -25300
internal const val SEC_AUTH_FAILED = -25293
internal const val SEC_USER_CANCELED = -128

/** Statuses that are ours rather than the framework's, for the failures CoreFoundation reports. */
internal const val STATUS_CREATE = -1001
internal const val STATUS_CORRUPT = -1002
internal const val STATUS_SYMBOL = -1003
