package com.tasktracker.userdefaults

/**
 * Raw byte-level preference storage.
 *
 * The original's store talked to `UserDefaults` directly, which worked because its
 * tests ran on macOS against a throwaway suite. There is no such thing in
 * `commonTest`, so the byte layer is separated from the JSON layer: the typed
 * [UserDefaultsStore] is then testable on every target, including the ones where the
 * real backend is unavailable.
 *
 * Bytes rather than typed values because that is what all three platforms actually
 * store, and because a typed backend would have to reimplement JSON on top of it.
 */
interface PreferencesBackend {
    /** `null` when the key holds nothing. */
    suspend fun bytes(key: String): ByteArray?

    suspend fun put(bytes: ByteArray, key: String)

    suspend fun remove(key: String)

    /**
     * Whether the key holds anything, including a value that fails to decode.
     *
     * Kept separate from [bytes] on purpose: the original asked `object(forKey:)`, which
     * sees the stored entry rather than the decoded result, so a corrupt preference is
     * still reported as present. The store relies on that to tell "reset to default"
     * apart from "this user really did turn it off".
     */
    suspend fun contains(key: String): Boolean
}

/**
 * Every preference key the app uses.
 *
 * Raw strings in a store are a common source of typos that only show up as a silent
 * "the setting didn't save" bug, so they are all declared here in one place.
 *
 * [wireValue] rather than the original's `rawValue`: the string is what actually lands
 * in the plist, and naming it makes that role explicit.
 */
enum class DefaultsKey(val wireValue: String) {
    /** Non-sensitive UI and navigation preferences, safe in an unencrypted plist. */
    LastSeenProject("navigation.lastSeenProject"),
    BoardShowsBacklog("board.showsBacklogColumn"),
    HasSeenSyncHint("sync.hasSeenHint"),

    /**
     * Debug-only fault injection, persisted so the simulator can be put into an offline
     * state and stay there across launches.
     */
    NetworkSimulation("debug.networkSimulation"),

    /**
     * Cached copy of the signed-in profile, so the UI has something to render before the
     * database has been opened. **Never stores credentials** — that is
     * [com.tasktracker.keychain.KeychainKey]'s job, and putting a token in an
     * unencrypted plist would defeat the split.
     */
    CachedProfile("auth.cachedProfile"),
}
