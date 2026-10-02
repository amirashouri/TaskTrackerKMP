package com.tasktracker.sharedui

import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The preferences the *shell* owns, rather than any one feature.
 *
 * ## Why these device preferences live here and not on a feature
 *
 * Each of them outlives the session, and each of them is about the device rather than about
 * anything on screen:
 *
 *  - the appearance, because it has to be right on the first composed frame, which is before
 *    any feature screen — let alone the settings one — exists;
 *  - the accent palette, for the same reason: design-system colours are read throughout the
 *    tree and must resolve consistently on the first frame;
 *  - the forced-offline intent, because it is a *debugging* switch a person sets once before
 *    handing a device to somebody else to exercise a failing bug report;
 *  - the last visited project, because the Board and Backlog tabs are scoped to it and a
 *    relaunch has to land on the board the user left.
 *
 * Routing them through a feature would mean the thing that decides them is only constructible
 * once the session exists, which is precisely the ordering that cannot work for a first frame.
 *
 * ## Why the theme key is outside `DefaultsKey`
 *
 * `UserDefaultsStore.removeAll` runs on sign-out, and it clears every `DefaultsKey` member.
 * An appearance stored as one would revert to `system` at every sign-out on a shared device —
 * a person's light/dark choice is a property of their hands, not of their session. So the
 * theme is stored through the raw-key overload, whose key is used *verbatim*, and this class
 * owns the two raw keys rather than inventing a home for them elsewhere.
 *
 * Same reasoning for the forced-offline key: it is set deliberately and must survive a sign
 * out and in, or the next person gets a device that is online when it was left offline.
 *
 * ## Observable state, because the theme *is* the tree's state
 *
 * [themeScheme] is the one preference that a composition reads directly — `ttTheme` wraps the
 * whole app in it. It is a [StateFlow] rather than a `var` on the app so that [RootScreen]
 * and the settings screen read the *same* value; two copies is how the picker and the tree
 * end up disagreeing for one frame.
 *
 * The other two are read-and-forget: the last project is applied once and then re-applied on
 * a root change, and the offline intent is dispatched to the port rather than drawn.
 */
class ShellPreferences(
    private val store: UserDefaultsStore,
    private val router: Router,
) {

    private val _themeScheme = MutableStateFlow(ThemeScheme.System)
    val themeScheme: StateFlow<ThemeScheme> = _themeScheme.asStateFlow()

    private val _accentPalette = MutableStateFlow(AccentPalette.Classic)
    val accentPalette: StateFlow<AccentPalette> = _accentPalette.asStateFlow()

    /**
     * The remembered forced-offline intent.
     *
     * Held separately from the settings model's own flag because the model is rebuilt every
     * time the Settings tab is entered, and a rebuilt model that starts at `false` would show
     * the switch off while the monitor is genuinely overridden — a control that disagrees
     * with the thing it controls.
     */
    private val _forceOffline = MutableStateFlow(false)
    val forceOffline: StateFlow<Boolean> = _forceOffline.asStateFlow()

    /**
     * Loads the stored shell preferences once at launch.
     *
     * Idempotent and safe to call from a `LaunchedEffect`, because a recomposition cannot
     * re-run it: the effect is keyed on the store, which does not change.
     *
     * Each read is independent and each falls back rather than failing the others. A theme
     * key that cannot be decoded is a user who gets the default appearance; refusing to also
     * restore their board because of it would be a worse answer than either half alone.
     */
    suspend fun restore() {
        _themeScheme.value = ThemeScheme.fromStoredValue(
            runCatching { store.value(THEME_KEY) }.getOrNull(),
        )
        _accentPalette.value = AccentPalette.fromStoredValue(
            runCatching { store.value(ACCENT_PALETTE_KEY) }.getOrNull(),
        )

        val offline = runCatching { store.value(FORCE_OFFLINE_KEY) }.getOrNull()
        _forceOffline.value = offline == TRUTHY

        val projectID = runCatching { store.value(router.lastProjectDefaultsKey) }.getOrNull()
        lastVisitedProjectID = projectID?.takeIf { it.isNotBlank() }
    }

    /**
     * The persisted last-visited project id, or `null`.
     *
     * Read during [restore] and kept here rather than re-read by [RootScreen], because the
     * restore is validated against the store and a *failed* read has to be distinguishable
     * from a project that is gone. See [com.tasktracker.routing.Router.restoreLastVisitedProject].
     */
    var lastVisitedProjectID: String? = null
        private set

    /**
     * Writes a new appearance.
     *
     * The state is updated first so the frame is already correct, and the write happens
     * regardless of whether the store accepts it: a preference that failed to persist should
     * still change the running app, or a full disk would make the settings screen inert.
     */
    suspend fun setThemeScheme(scheme: ThemeScheme) {
        _themeScheme.value = scheme
        runCatching { store.set(scheme.wireValue, THEME_KEY) }
    }

    /** Applies and persists the selected accent palette. */
    suspend fun setAccentPalette(palette: AccentPalette) {
        _accentPalette.value = palette
        runCatching { store.set(palette.wireValue, ACCENT_PALETTE_KEY) }
    }

    /**
     * Records the forced-offline intent, keeping the state and the store in step.
     *
     * The caller dispatches the intent to the settings port; this only remembers it. Split
     * that way so that restoring the choice on the way *into* the Settings tab is the same
     * code path as a user changing it — one place that decides what "offline" means, rather
     * than a flag the screen reads and a switch the screen writes.
     */
    suspend fun setForceOffline(forced: Boolean) {
        _forceOffline.value = forced
        runCatching { store.set(forced.toString(), FORCE_OFFLINE_KEY) }
    }

    /**
     * Records the active project, or clears it.
     *
     * Called on every change rather than on sign-out, so the value on disk is always the last
     * one the user actually had. An empty string for "none" rather than a removal, because
     * [UserDefaultsStore.hasValue] exists to answer "was this ever set" and a cleared
     * preference should not read as an unset one.
     */
    suspend fun rememberActiveProject(projectID: ProjectID?) {
        runCatching { store.set(projectID?.stringValue.orEmpty(), router.lastProjectDefaultsKey) }
    }

    companion object {
        /**
         * Verbatim, matching the original's `@AppStorage(ThemeScheme.defaultsKey)`, and
         * therefore *not* namespaced. See the class note on why it is not a `DefaultsKey`.
         */
        const val THEME_KEY: String = ThemeScheme.defaultsKey

        /** Verbatim to keep the device appearance choice outside sign-out clearing. */
        const val ACCENT_PALETTE_KEY: String = "com.tasktracker.settings.accentPalette"

        /** Verbatim, and out of `DefaultsKey` for the same reason. */
        const val FORCE_OFFLINE_KEY: String = "com.tasktracker.settings.forceOffline"

        /**
         * `UserDefaultsStore` JSON-encodes a `DefaultsKey` value, but the raw-key overload
         * does not — it stores the string as typed, which is the point of it. So `true` is
         * the literal five characters and nothing else is truthy.
         */
        private const val TRUTHY = "true"
    }
}
