package com.tasktracker.userdefaults

import android.content.Context
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `SharedPreferences` backed storage.
 *
 * `commit()` rather than `apply()`: the whole point of persisting a preference is that
 * it is still there after the process dies, and `apply` writes asynchronously, so a
 * sign-out followed immediately by a kill can leave a token-adjacent cache behind. The
 * writes are small and infrequent, so the synchronous cost is not the bottleneck it
 * would be in a hot loop.
 */
class SharedPreferencesBackend(
    context: Context,
    fileName: String = "com.tasktracker.preferences",
) : PreferencesBackend {

    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun bytes(key: String): ByteArray? = mutex.withLock {
        // What is stored is already base64, so this decodes rather than encodes. A value
        // that is not valid base64 reads back as absent: a preference is recoverable
        // from its default, so dropping an unreadable one beats failing the read.
        prefs.getString(key, null)?.let { runCatching { it.decodeBase64() }.getOrNull() }
    }

    override suspend fun put(bytes: ByteArray, key: String): Unit = mutex.withLock {
        prefs.edit().putString(key, bytes.encodeBase64()).commit()
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        prefs.edit().remove(key).commit()
    }

    override suspend fun contains(key: String): Boolean = mutex.withLock {
        prefs.contains(key)
    }
}

/**
 * `SharedPreferences` has no byte-array type, so the payload is base64 — the same
 * representation the iOS backend uses, so the on-disk shape is recognisably identical
 * on both platforms and one decoder is under test rather than two.
 */
@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeBase64(): ByteArray = Base64.decode(this)

/**
 * The real Android preferences. [host] must be a `Context`.
 *
 * Throws [IllegalArgumentException] naming the required type rather than letting a
 * `ClassCastException` surface from the cast.
 */
actual fun platformPreferences(host: Any?): PreferencesBackend {
    val context = host as? Context ?: throw IllegalArgumentException(
        "platformPreferences() on Android requires a Context, but got " +
            "${host?.let { it::class.simpleName } ?: "null"}. Pass the application " +
            "context from the Android composition root.",
    )
    return SharedPreferencesBackend(context.applicationContext)
}
