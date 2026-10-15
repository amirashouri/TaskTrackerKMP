package com.tasktracker.userdefaults

import kotlinx.coroutines.runBlocking

/**
 * The Desktop window's remembered size, in points.
 *
 * ## Why a value type rather than two loose integers
 *
 * A width and a height are only meaningful together — a half-written pair is a window that
 * opens at some other launch's width — so they travel as one value and are stored under one
 * key. The encoding is `WxH` (`"1280x800"`), the same shape a person would type into a
 * config file, so a hand-edited preferences file stays readable and [parse] has one thing to
 * understand.
 */
public data class WindowGeometry(
    public val width: Int,
    public val height: Int,
) {

    /** The stored form. `1280x800`. */
    public fun encode(): String = "${width}x$height"

    public companion object {

        /**
         * Reads `WxH`, or `null` for anything that is not one.
         *
         * `null` rather than a throw for every unreadable shape — absent, empty, a
         * hand-edited `wide`, a negative or zero side — because the caller's answer is the
         * same for all of them: open at the default size. The window is the *first* thing
         * the Desktop app does, so a preference nobody can parse must not stop the launch.
         */
        public fun parse(stored: String?): WindowGeometry? {
            val separator = stored?.indexOf('x') ?: return null
            if (separator <= 0 || separator == stored.lastIndex) return null
            val width = stored.substring(0, separator).toIntOrNull() ?: return null
            val height = stored.substring(separator + 1).toIntOrNull() ?: return null
            if (width <= 0 || height <= 0) return null
            return WindowGeometry(width = width, height = height)
        }
    }
}

/**
 * The Desktop window's size, persisted through the same preferences file the other
 * device-scoped settings use.
 *
 * ## Why the key is outside `DefaultsKey`
 *
 * `UserDefaultsStore.removeAll` runs on sign-out and walks `DefaultsKey.entries`, so a key in
 * that enum is cleared with the session. A window's size is a property of the *machine*, not
 * of the account: the next person to sign in on this desktop inherits the window the previous
 * one left, exactly as they inherit the appearance scheme. So it is stored through the store's
 * verbatim-key overload — the same mechanism `ThemeScheme.defaultsKey` and
 * `ShellPreferences.THEME_KEY` use — and survives sign-out by construction rather than by a
 * rule someone has to remember. `WindowGeometryDefaultsTest` asserts the survival rather than
 * trusting this paragraph.
 *
 * ## Why the read is synchronous, unlike every other preference
 *
 * Every other stored preference is read inside a `LaunchedEffect` and applied to a
 * `StateFlow`; the window is the one that cannot wait. The Desktop entry point sizes the
 * window *before* the composition starts — `rememberWindowState(size = …)` takes its value on
 * the first frame, and there is no coroutine scope to suspend in yet — so [load] blocks on a
 * single small `Properties` read and hands the size back in time. That
 * is the whole reason these functions exist rather than being calls on a shared store.
 *
 * ## Why the backend is a parameter with a default
 *
 * Desktop production wants `platformPreferences()`, which is the file-backed backend once
 * `isDesktopApp` is set. A test wants a throwaway backend and must not touch the developer's
 * real `preferences.properties`, so the backend is injectable and the default is the platform
 * one.
 */
public object WindowGeometryDefaults {

    /**
     * The properties key, used verbatim rather than namespaced.
     *
     * Verbatim for the same reason as the other raw keys: it is deliberately outside
     * `DefaultsKey`, and the store's `DefaultsKey` namespacing is the thing that marks a key
     * as session-scoped.
     */
    public const val KEY: String = "com.tasktracker.window.geometry"

    /** The remembered size, or `null` when nothing parseable is stored. */
    public fun load(backend: PreferencesBackend = platformPreferences()): WindowGeometry? =
        runBlocking {
            WindowGeometry.parse(UserDefaultsStore(backend).value(KEY))
        }

    /** Remembers [geometry] so the next launch opens at the same size. */
    public fun save(
        geometry: WindowGeometry,
        backend: PreferencesBackend = platformPreferences(),
    ): Unit = runBlocking {
        UserDefaultsStore(backend).set(geometry.encode(), KEY)
    }
}
