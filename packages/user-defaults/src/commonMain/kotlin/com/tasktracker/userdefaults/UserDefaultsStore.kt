package com.tasktracker.userdefaults

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage

/**
 * Typed facade over a [PreferencesBackend].
 *
 * Created from a namespace rather than handed a live backend object: the platform
 * backends are not thread-safe and are built inside the store, which keeps all access
 * serialised behind `suspend` functions. That is the Kotlin analogue of the original's
 * actor, and the reason its doc comment called out `UserDefaults` being non-`Sendable`.
 */
class UserDefaultsStore(
    // internal + @PublishedApi rather than private: `value` and `set` are public inline
    // functions, and an inline function cannot reach a private member. The alternative
    // is giving up reified type parameters and taking a KSerializer per call, which
    // would make every call site longer for a rule the compiler already enforces here.
    @PublishedApi internal val backend: PreferencesBackend,
    private val namespace: String = "com.tasktracker",
) {

    /**
     * Decodes a stored value, or `null` when absent **or unreadable**.
     *
     * The swallow is deliberate and the opposite of `KeychainStore.value`. A UI
     * preference that fails to decode — written by an older build, or half-synced from
     * a backend bug — should fall back to its default and let the user carry on. A
     * corrupt *token* is a different situation and does throw, because failing open
     * there would mean continuing with a credential nobody can parse.
     */
    suspend inline fun <reified T> value(key: DefaultsKey): T? {
        val bytes = backend.bytes(namespaced(key)) ?: return null
        return try {
            JSONCoding.json.decodeFromString(bytes.decodeToString())
        } catch (error: Exception) {
            TTLogger.settings.error {
                logMessage {
                    append("defaults: discarding unreadable ")
                    append(key.wireValue)
                    append(" - ")
                    append(error.message ?: error::class.simpleName)
                }
            }
            null
        }
    }

    /** Encodes and stores a value. Returns false if it could not be encoded. */
    suspend inline fun <reified T> set(value: T, key: DefaultsKey): Boolean {
        val encoded = try {
            JSONCoding.json.encodeToString(value).encodeToByteArray()
        } catch (error: Exception) {
            // No `throws` here, matching the original: a preference that cannot be
            // encoded is a bug to report, not a reason to tear down a settings screen.
            TTLogger.settings.error {
                logMessage {
                    append("defaults: could not encode ")
                    append(key.wireValue)
                    append(" - ")
                    append(error.message ?: error::class.simpleName)
                }
            }
            return false
        }
        backend.put(encoded, namespaced(key))
        return true
    }

    suspend fun remove(key: DefaultsKey) = backend.remove(namespaced(key))

    /**
     * Clears every [DefaultsKey] this store owns, and nothing else.
     *
     * The "and nothing else" is load-bearing, and it is the whole reason the appearance
     * scheme is not a [DefaultsKey]. Sign-out calls this, and the enum is what it walks —
     * so a key outside the enum survives, by construction rather than by a rule somebody has
     * to remember. See [value] for the one key that is deliberately outside.
     */
    suspend fun removeAll() {
        DefaultsKey.entries.forEach { backend.remove(namespaced(it)) }
    }

    /** Whether the key currently holds anything, decodable or not. */
    suspend fun hasValue(key: DefaultsKey): Boolean = backend.contains(namespaced(key))

    /** The exact plist key for [key], exposed so tests can poison an entry directly. */
    fun namespaced(key: DefaultsKey): String = "$namespace.${key.wireValue}"

    // MARK: - Keys outside DefaultsKey

    /**
     * Reads a key that is deliberately **not** in [DefaultsKey], as a plain string.
     *
     * Exactly one key is stored this way: the appearance scheme, which
     * `com.tasktracker.designsystem.ThemeScheme` owns. It has to live outside the enum
     * because [removeAll] runs on sign-out and an enum member would be wiped with it —
     * someone's light/dark choice is a property of the device, not of the session, and
     * silently reverting it at every sign-out is the bug this overload exists to prevent.
     *
     * Plain strings rather than the JSON encoding [value] uses, so the stored bytes are
     * `system` and not `"system"`. That is also what makes the value readable by anything
     * that has to resolve the appearance before the async store is available — see
     * `ThemeScheme.defaultsKey`.
     *
     * No such-value return for an unreadable entry either: the callers are appearance
     * resolution, which has its own fallback (`system`) and must not have two.
     *
     * ## The key is used **verbatim**, unlike a [DefaultsKey]
     *
     * Every other key in this store is a [DefaultsKey], whose values are *relative* —
     * `navigation.lastSeenProject` — and which [namespaced] prefixes with `com.tasktracker.`
     * to get the real plist key. This overload does not, and that difference is the whole
     * reason it exists.
     *
     * `ThemeScheme.defaultsKey` is `com.tasktracker.settings.themeScheme`: already absolute,
     * because the original reads it with `@AppStorage`, which uses the key exactly as given
     * and never applies the store's namespace. So the value here is a plist key that some
     * other component — the original, or anything else using `UserDefaults` directly — may
     * already have written.
     *
     * Prefixing it would have produced
     * `com.tasktracker.com.tasktracker.settings.themeScheme`, and every symptom would have
     * been invisible: the read and the write would both use the doubled key, so the setting
     * would work perfectly for anyone who set it *in this build*, and silently come back
     * as `system` for anyone whose value was written by the original. A doubled prefix is a
     * key that no other tool looks at, so nothing would ever find it.
     */
    suspend fun value(key: String): String? {
        val bytes = backend.bytes(key) ?: return null
        return bytes.decodeToString()
    }

    /** Stores a raw string under a key outside [DefaultsKey]. The key is used verbatim. */
    suspend fun set(value: String, key: String): Boolean {
        backend.put(value.encodeToByteArray(), key)
        return true
    }

    /** Removes a key outside [DefaultsKey]. The key is used verbatim. */
    suspend fun remove(key: String) = backend.remove(key)

    /**
     * The plist key for an un-enummed key — which is [key], unchanged.
     *
     * Kept for the same reason as [namespaced] takes a [DefaultsKey]: so a test can poison
     * an entry directly and the key it poisons is visibly the one production uses. The
     * identity is the assertion — that this does *not* prepend the namespace is the
     * behaviour [RawKeyTest] exists to hold.
     */
    fun plistKey(key: String): String = key
}
