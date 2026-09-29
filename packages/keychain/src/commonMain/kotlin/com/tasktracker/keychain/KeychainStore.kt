package com.tasktracker.keychain

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage

/**
 * Well-known keychain keys. Centralised so a rename is a one-line change and so no
 * feature invents its own string.
 *
 * [wireValue] rather than the original's `rawValue`, for the same reason
 * [com.tasktracker.routing.AppTab] has one: here the string is literally what gets
 * written to the secure store, and naming it makes that role explicit.
 */
enum class KeychainKey(val wireValue: String) {
    AccessToken("auth.accessToken"),
    RefreshToken("auth.refreshToken"),
}

/**
 * Typed facade over any [KeychainAccess].
 *
 * Kept separate from the platform implementation so the auth feature can be tested
 * against an [InMemoryKeychain] without touching the real keychain.
 *
 * No lock of its own: every method is `suspend` and does nothing but forward to an
 * already-serialised [KeychainAccess], so a second layer of exclusion would only add
 * a place for a deadlock. The original used an actor here for the same job.
 */
class KeychainStore(private val access: KeychainAccess) {

    /** The bytes behind [key], or `null` when nothing is stored. */
    suspend fun data(key: KeychainKey): ByteArray? = access.data(key.wireValue)

    /** Raw write, for callers that are not encoding a `Codable` payload. */
    suspend fun setBytes(data: ByteArray, key: KeychainKey) {
        TTLogger.auth.debug {
            logMessage {
                append("keychain: storing ")
                append(key.wireValue)
            }
        }
        access.set(data, key.wireValue)
    }

    suspend fun remove(key: KeychainKey) = access.remove(key.wireValue)

    /**
     * Called on sign-out. Wipes the whole service namespace rather than the two known
     * keys: TaskTracker owns its namespace entirely, and a full wipe guarantees no
     * stale token survives a logout even if a third credential is added later without
     * being added to [KeychainKey].
     */
    suspend fun removeAll() {
        TTLogger.auth.info { logMessage { append("keychain: wiping all credentials") } }
        access.removeAll()
    }
}

// MARK: - Codable convenience
//
// The original hung these on a protocol extension taking a `Decodable` type argument.
// Kotlin's equivalent needs `reified`, and an `inline` function cannot be a member, so
// these are extensions rather than methods — which is why [KeychainStore.data] and
// [KeychainStore.setBytes] are public. Call sites read the same as the original.

/**
 * Decodes a payload, returning `null` when absent.
 *
 * Throws when the stored bytes are corrupt — deliberately, unlike
 * [com.tasktracker.userdefaults.UserDefaultsStore.value]. A keychain entry exists only
 * because the app wrote it, so unreadable bytes mean something is wrong and the auth
 * layer must see it. A UI preference that fails to decode is a different matter.
 */
suspend inline fun <reified T> KeychainStore.value(key: KeychainKey): T? {
    val bytes = data(key) ?: return null
    return JSONCoding.json.decodeFromString(bytes.decodeToString())
}

/** Stores a `Codable` payload under a well-known key. */
suspend inline fun <reified T> KeychainStore.set(value: T, key: KeychainKey) {
    setBytes(JSONCoding.json.encodeToString(value).encodeToByteArray(), key = key)
}
