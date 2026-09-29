package com.tasktracker.keychain

/**
 * Raw byte-level access to a secret store.
 *
 * Every operation suspends rather than being synchronous on purpose: the production
 * implementation is serialised (an actor in the original, a lock here) so there is a
 * single point where a keystore or security-framework call can be audited, and a
 * synchronous requirement would force `nonisolated` methods that hand out
 * unsynchronised state.
 *
 * A plain `interface` rather than `expect interface`, because the whole point of
 * [InMemoryKeychain] is to be a drop-in that tests supply — an `expect` declaration
 * cannot be implemented outside the module.
 */
interface KeychainAccess {
    /**
     * `null` means "no item", which is a normal state and not an error. Every
     * implementation must distinguish it from a genuine read failure.
     */
    suspend fun data(key: String): ByteArray?

    suspend fun set(data: ByteArray, key: String)

    suspend fun remove(key: String)

    suspend fun removeAll()
}

/**
 * A keystore operation failed.
 *
 * [status] is the raw platform code — `OSStatus` on iOS, an `AndroidKeyStore`
 * exception type on Android — kept as an [Int] because neither name exists in common
 * code. The human-readable reason cannot live here either, because turning a status
 * into text needs a platform call; the platform actuals resolve it before constructing
 * this, and [reason] carries the result.
 *
 * [key] is the *well-known* key name, never the secret itself — this type is
 * constructed on a failure path that is about to be logged.
 */
class KeychainError(
    val status: Int,
    val operation: String,
    val key: String,
    val reason: String = "status $status",
) : Exception("Keychain $operation failed for \"$key\": $reason")
