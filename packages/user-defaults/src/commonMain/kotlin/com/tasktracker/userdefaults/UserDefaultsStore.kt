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

    /** Clears every [DefaultsKey] this store owns, and nothing else. */
    suspend fun removeAll() {
        DefaultsKey.entries.forEach { backend.remove(namespaced(it)) }
    }

    /** Whether the key currently holds anything, decodable or not. */
    suspend fun hasValue(key: DefaultsKey): Boolean = backend.contains(namespaced(key))

    /** The exact plist key for [key], exposed so tests can poison an entry directly. */
    fun namespaced(key: DefaultsKey): String = "$namespace.${key.wireValue}"
}
