package com.tasktracker.keychain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The same namespace `AndroidKeychain` defaults to, so one sign-out story reads the same. */
internal const val DEFAULT_KEYCHAIN_SERVICE = "com.tasktracker.app"

/**
 * The real macOS keychain, over `Security.framework` through JNA.
 *
 * This is the Desktop equivalent of `SystemKeychain` on iOS and `AndroidKeychain` on Android,
 * and it exists for the same reason those do: the JVM actual used to be an [InMemoryKeychain],
 * which meant a Desktop session did not survive its own process. Signing in, quitting, and
 * reopening asked for the password again, and `KeychainStore`'s two keys were written
 * somewhere that could never be read back.
 *
 * ## What is stored, and where
 *
 * One `kSecClassGenericPassword` item per key, under [service] as `kSecAttrService` and the
 * well-known key name (`auth.accessToken`) as `kSecAttrAccount`. The secret is the item's
 * `kSecValueData`. That is the standard shape for a generic password and is what makes the
 * items visible to the user in Keychain Access.app, which matters: a user who wants to know
 * whether an app is holding a token should be able to look.
 *
 * Accessible after first unlock rather than when-unlocked, matching iOS — see `MacOs`.
 *
 * ## Serialisation
 *
 * Guarded by a [Mutex] for the same reason [InMemoryKeychain] is: [KeychainAccess] is
 * `suspend`, so two coroutines can be inside a call at once, and the read-modify-write in
 * [set] — add, then fall back to update on a duplicate — is a genuine race without it. Two
 * concurrent sign-ins would otherwise have both tries fail and both updates run.
 *
 * ## Secrets never reach the log
 *
 * Every [KeychainError] this class raises carries the *key name*, never the bytes. That is a
 * property of [KeychainError] itself, and this class is the one place it would be easy to
 * break by passing a value into a message.
 */
class MacOsKeychain(
    private val service: String = DEFAULT_KEYCHAIN_SERVICE,
) : KeychainAccess {

    private val mutex = Mutex()

    override suspend fun data(key: String): ByteArray? = mutex.withLock {
        MacOs.withDictionary { dictionary, owned ->
            MacOs.put(dictionary, MacOs.kSecClass, MacOs.kSecClassGenericPassword)
            MacOs.putOwned(dictionary, MacOs.kSecAttrService, MacOs.cfString(service), owned)
            MacOs.putOwned(dictionary, MacOs.kSecAttrAccount, MacOs.cfString(key), owned)
            MacOs.put(dictionary, MacOs.kSecReturnData, MacOs.kCFBooleanTrue)
            MacOs.put(dictionary, MacOs.kSecMatchLimit, MacOs.kSecMatchLimitOne)
            MacOs.copyMatching(dictionary)
        }
    }

    /**
     * Writes the value, adding the item or replacing its data.
     *
     * `SecItemAdd` is tried first rather than `SecItemUpdate`-then-add because "the item is
     * not there yet" is the common case on a first sign-in, and because the failure it
     * produces on a re-sign-in is a specific, documented status
     * (`errSecDuplicateItem`) rather than a guess. The alternative order would make the
     * first sign-in pay for a failing call on every launch.
     */
    override suspend fun set(data: ByteArray, key: String): Unit = mutex.withLock {
        val status = MacOs.withDictionary { dictionary, owned ->
            MacOs.put(dictionary, MacOs.kSecClass, MacOs.kSecClassGenericPassword)
            MacOs.putOwned(dictionary, MacOs.kSecAttrService, MacOs.cfString(service), owned)
            MacOs.putOwned(dictionary, MacOs.kSecAttrAccount, MacOs.cfString(key), owned)
            MacOs.putOwned(dictionary, MacOs.kSecValueData, MacOs.cfData(data), owned)
            MacOs.put(dictionary, MacOs.kSecAttrAccessible, MacOs.kSecAttrAccessibleAfterFirstUnlock)
            MacOs.add(dictionary)
        }

        when (status) {
            SEC_SUCCESS -> Unit
            SEC_DUPLICATE_ITEM -> replaceData(data, key)
            else -> throw KeychainError(status, "insert", key, MacOs.reason(status))
        }
    }

    /**
     * Replaces an existing item's value.
     *
     * The query names the item and the update dictionary carries only `kSecValueData`, so the
     * item's identity and its accessibility are left alone. Replacing by delete-then-add would
     * work too and would briefly leave the token absent — which an in-flight read would see as
     * "signed out" rather than "mid-write", so this does not do that.
     */
    private fun replaceData(data: ByteArray, key: String) {
        MacOs.withDictionary { query, queryOwned ->
            MacOs.put(query, MacOs.kSecClass, MacOs.kSecClassGenericPassword)
            MacOs.putOwned(query, MacOs.kSecAttrService, MacOs.cfString(service), queryOwned)
            MacOs.putOwned(query, MacOs.kSecAttrAccount, MacOs.cfString(key), queryOwned)

            MacOs.withDictionary { attributes, attributesOwned ->
                MacOs.putOwned(attributes, MacOs.kSecValueData, MacOs.cfData(data), attributesOwned)
                MacOs.update(query, attributes)
            }
        }
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        MacOs.withDictionary { dictionary, owned ->
            MacOs.put(dictionary, MacOs.kSecClass, MacOs.kSecClassGenericPassword)
            MacOs.putOwned(dictionary, MacOs.kSecAttrService, MacOs.cfString(service), owned)
            MacOs.putOwned(dictionary, MacOs.kSecAttrAccount, MacOs.cfString(key), owned)
            MacOs.delete(dictionary)
        }
    }

    /**
     * Deletes every item in [service].
     *
     * The query deliberately omits `kSecAttrAccount`, which is what makes it a namespace wipe
     * rather than a single-key delete: `SecItemDelete` removes every item matching the
     * attributes given. `KeychainStore.removeAll` runs this on sign-out and documents that it
     * clears the whole service rather than the two known keys, so leaving a key out of the
     * query here is the behaviour that call relies on — a key added to [KeychainKey] later is
     * wiped by a sign-out without anyone remembering to update this method.
     *
     * Scoped to our own `kSecAttrService`, so it cannot reach another app's items even though
     * the login keychain is shared.
     *
     * `kSecMatchLimitAll` is load-bearing and not decoration: `SecItemDelete` defaults an
     * unspecified match limit to "one", so an earlier version of this method deleted a single
     * item, returned success, and left the second token in the keychain. See the measurement
     * on [MacOs.kSecMatchLimitAll].
     */
    override suspend fun removeAll(): Unit = mutex.withLock {
        MacOs.withDictionary { dictionary, owned ->
            MacOs.put(dictionary, MacOs.kSecClass, MacOs.kSecClassGenericPassword)
            MacOs.putOwned(dictionary, MacOs.kSecAttrService, MacOs.cfString(service), owned)
            MacOs.put(dictionary, MacOs.kSecMatchLimit, MacOs.kSecMatchLimitAll)
            MacOs.delete(dictionary)
        }
    }
}
