package com.tasktracker.keychain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val ALGORITHM_AES = "AES"
private const val ALIAS = "com.tasktracker.app.credentials"
private const val PREFS = "com.tasktracker.keychain"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_BYTES = 12
private const val TAG_BITS = 128

/**
 * Android equivalent of the iOS keychain.
 *
 * Android's `KeyStore` cannot hold arbitrary bytes — it holds *keys*. So the bytes are
 * encrypted with an AES-GCM key that lives in `AndroidKeyStore` and never leaves it,
 * and only the ciphertext (with its IV prefixed) is written to `SharedPreferences`.
 * This is the whole reason the actual is not a two-line `prefs.edit().putString()`:
 * secrets written to prefs in the clear survive on a rooted device, in an `adb backup`,
 * and in any image of the filesystem.
 *
 * A fresh IV per write, which is what GCM requires. Reusing one across two
 * encryptions of the same key is catastrophic for GCM — it leaks the authentication
 * subkey and XORs of plaintext — so the IV is generated per call and never persisted
 * separately; it is prepended to the stored blob and read back with the ciphertext.
 */
class AndroidKeychain(
    context: Context,
    private val service: String = "com.tasktracker.app",
) : KeychainAccess {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun data(key: String): ByteArray? = mutex.withLock {
        val encoded = prefs.getString(storageKey(key), null) ?: return@withLock null
        val blob = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            throw KeychainError(STATUS_CORRUPT, "read", key, "stored value is not valid base64")
        }
        if (blob.size <= IV_BYTES) {
            throw KeychainError(STATUS_CORRUPT, "read", key, "stored value is shorter than its IV")
        }
        val iv = blob.copyOfRange(0, IV_BYTES)
        val ciphertext = blob.copyOfRange(IV_BYTES, blob.size)
        try {
            cipher(Cipher.DECRYPT_MODE, iv).doFinal(ciphertext)
        } catch (error: Exception) {
            // A wrong key alias after a reinstall, or tampered ciphertext, surfaces here.
            // Reported as a storage failure rather than swallowed: a silently empty
            // keychain would sign the user out with no way to tell why.
            throw KeychainError(STATUS_DECRYPT, "read", key, error.message ?: "decryption failed")
        }
    }

    override suspend fun set(data: ByteArray, key: String): Unit = mutex.withLock {
        val iv = ByteArray(IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val ciphertext = cipher(Cipher.ENCRYPT_MODE, iv).doFinal(data)
        val blob = iv + ciphertext
        val written = prefs.edit()
            .putString(storageKey(key), Base64.encodeToString(blob, Base64.NO_WRAP))
            .commit()
        if (!written) throw KeychainError(STATUS_WRITE, "insert", key, "SharedPreferences commit failed")
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        prefs.edit().remove(storageKey(key)).commit()
    }

    /**
     * Clears the ciphertexts but keeps the encryption key.
     *
     * Deleting the `KeyStore` alias as well would mean the next sign-in has to
     * re-generate it, which is fine — but it also means a sign-out on a device whose
     * keystore is broken leaves a half-wiped store. Removing the plaintext is the part
     * that matters for "no stale token survives a logout".
     */
    override suspend fun removeAll(): Unit = mutex.withLock {
        prefs.edit().clear().commit()
    }

    /** Forgets the encryption key too. Used by settings "reset app data". */
    fun deleteEncryptionKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
    }

    private fun storageKey(key: String): String = "$service.$key"

    private fun cipher(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        }

    /** Generated on first use, then reused; the key itself never leaves the keystore. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // The iOS side reads tokens after the first unlock following a
                    // reboot so background sync works; `setUserAuthenticationRequired(false)`
                    // is the Android equivalent — do not add it without checking that
                    // sync still runs on a locked device.
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKey()
        }
    }
}

private const val STATUS_CORRUPT = -1
private const val STATUS_DECRYPT = -2
private const val STATUS_WRITE = -3

/**
 * The real Android keychain. [host] must be a `Context`.
 *
 * Throws [IllegalArgumentException] naming the required type rather than letting a
 * `ClassCastException` surface from the cast: this is a wiring mistake made once, at
 * start-up, and the message should say what to pass.
 */
actual fun systemKeychain(host: Any?): KeychainAccess {
    val context = host as? Context ?: throw IllegalArgumentException(
        "systemKeychain() on Android requires a Context, but got " +
            "${host?.let { it::class.simpleName } ?: "null"}. Pass the application " +
            "context from the Android composition root.",
    )
    return AndroidKeychain(context.applicationContext)
}
