package com.tasktracker.userdefaults

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults
import platform.Foundation.setObject
import platform.Foundation.stringWithString

/**
 * `NSUserDefaults` backed storage.
 *
 * Values are held as base64 in an `NSString` rather than as `NSData`. That is a
 * deliberate detour: `NSData` in Kotlin/Native exposes no constructor that takes a byte
 * pointer (only `NSData()` and `NSData(coder:)`), and the `+[NSData dataWithBytes:length:]`
 * factory is not surfaced in this version either. `NSString` is fully usable, and the
 * backend interface is byte-oriented, so base64 is the cheapest correct bridge. The
 * store's payloads are small JSON blobs read a few times per launch.
 *
 * The `suiteName` the original's tests relied on has no Kotlin/Native equivalent usable
 * from common code, and it only ever existed to stop tests contaminating each other —
 * which [InMemoryPreferences] now does directly. So this is
 * `NSUserDefaults.standardUserDefaults`.
 */
class NSUserDefaultsBackend(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PreferencesBackend {

    override suspend fun bytes(key: String): ByteArray? =
        (defaults.objectForKey(key) as? NSString)?.let { runCatching { it.toString().decodeBase64() }.getOrNull() }

    override suspend fun put(bytes: ByteArray, key: String) {
        // stringWithString is an extension on NSStringMeta, so it hangs off the companion
        // rather than off a Kotlin String.
        defaults.setObject(NSString.stringWithString(bytes.encodeBase64()), forKey = key)
    }

    override suspend fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    override suspend fun contains(key: String): Boolean = defaults.objectForKey(key) != null
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeBase64(): ByteArray = Base64.decode(this)

/** The real iOS preferences. [host] is unused; see [platformPreferences] in commonMain. */
actual fun platformPreferences(host: Any?): PreferencesBackend = NSUserDefaultsBackend()
