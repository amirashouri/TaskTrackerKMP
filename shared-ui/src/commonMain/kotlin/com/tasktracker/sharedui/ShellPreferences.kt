package com.tasktracker.sharedui

import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.featureauthdomain.UserRole
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.DefaultsKey
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
     * How much of the glass material the app shows, as a fraction in `0f..1f`.
     *
     * Lives beside the appearance preferences rather than in the settings *domain*, because it
     * is not a business rule and nothing about the domain needs to know it exists: it is read
     * by the design system at the point it resolves an alpha, and every surface in the app
     * resolves one. `1f` is the default and the whole reason the measured glass numbers in
     * `ttGlass` are still valid — see `LocalGlassIntensity`.
     *
     * Clamped on the way in and on the way out. A slider is the only writer, but the store is
     * not: a hand-edited value, or a future caller that passes a percentage, must not be able
     * to hand the design system an alpha outside `0f..1f`, which `lerp` past its endpoints
     * without complaint.
     */
    private val _glassIntensity = MutableStateFlow(1f)
    val glassIntensity: StateFlow<Float> = _glassIntensity.asStateFlow()

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

    private val _currentUserName = MutableStateFlow<String?>(null)

    /**
     * Who the signed-in person *is*, as an id, or `null` before [refreshCurrentUser] has run.
     *
     * The identity half of [currentUserName], read from the same cached profile for the same
     * reason and at the same moment. It replaces the name in every comparison that used to
     * fuzzy-match on first names: the board's "My Tasks" filter and the editor's "assign to
     * me" are exact-id questions, and a name was always a lossy stand-in for one.
     */
    private val _currentUserID = MutableStateFlow<UserID?>(null)
    val currentUserID: StateFlow<UserID?> = _currentUserID.asStateFlow()

    /**
     * The signed-in person's account role, or `null` before [refreshCurrentUser] has run.
     *
     * Read from the same cached profile as [currentUserID] and at the same moment, for the same
     * reason: the role decides whether Settings offers the admin management screens, and that
     * gate has to hold on a relaunch of a device that is offline. `null` means "nobody is signed
     * in" rather than "a member" — the profile's own default already answers the second question
     * ([UserRole.Member]) — so the shell never has to invent an identity before sign-in.
     */
    private val _currentUserRole = MutableStateFlow<UserRole?>(null)
    val currentUserRole: StateFlow<UserRole?> = _currentUserRole.asStateFlow()

    /**
     * Who to call the signed-in person, or `null` before [refreshCurrentUser] has run.
     *
     * A [StateFlow] and not a `val` for two reasons, and the first is mechanical: the read is
     * `UserDefaultsStore.value`, which is `suspend` because it may cross a platform boundary,
     * and the reader is a `remember` block in a composition — not a coroutine. A property
     * cannot be the shape the call needs.
     *
     * The second is that the value has to be *re-read*, not captured once. The cached profile
     * is written by sign-in (`AuthRepository.signIn` -> `DefaultsKey.CachedProfile`) and cleared
     * by sign-out, so anything read during [restore] is stale by the time it matters: on a cold
     * launch `restore` runs before anybody has signed in, and the board's "show only my tasks"
     * filter would be handed `null` and match nothing. [refreshCurrentUser] is therefore called
     * when the root moves to the main tree, not from [restore], and the flow is what lets a
     * later board pick up a name that arrived after this screen was composed.
     *
     * `preferredGreeting` rather than a local display-name-else-email expression: it is the one
     * definition of what to call this person, it is on the type that owns it, and re-spelling it
     * here is how the shell's greeting and the sign-in screen's greeting drift apart.
     */
    val currentUserName: StateFlow<String?> = _currentUserName.asStateFlow()

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
        // Parsed rather than defaulted through a nullable: a stored value that will not parse
        // is a user who gets full glass, which is the appearance they had before they ever
        // touched the slider. Same reasoning as the reads above — one bad key must not cost
        // them the others.
        _glassIntensity.value = runCatching {
            store.value(GLASS_INTENSITY_KEY)?.toFloatOrNull()
        }.getOrNull()?.coerceIn(0f, 1f) ?: 1f

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
     * Re-reads the signed-in person's name from the cached profile.
     *
     * Called when the router's root becomes `Main`, which is the first moment a cached profile
     * *can* be the current session's — a sign-in writes it before it moves the root, so this
     * cannot miss a fresh session, and it overwrites rather than accumulating, so a stale name
     * cannot survive a new sign-in as a different account's.
     *
     * No `runCatching`, unlike the raw-key reads in [restore], and that is a considered
     * difference rather than an oversight: those reads have a documented fallback this one
     * cannot invent. `UserDefaultsStore.value` already answers `null` for both "absent" and
     * "undecodable", and `null` is the *correct* answer to each — nobody is signed in, or a
     * profile written by an older build is not readable — whereas the fallback [restore] wants
     * is a default appearance. Swallowing a throw here would have to either keep the previous
     * name, which leaves one account's name on the board after another account signs in, or
     * invent a name, which filters a real person's own work out of their own board.
     */
    suspend fun refreshCurrentUser() {
        val profile = store.value<UserIdentity>(DefaultsKey.CachedProfile)
        _currentUserName.value = profile?.preferredGreeting
        _currentUserID.value = profile?.id
        // The raw role, not an `isAdmin` boolean: which role opens which screen is a decision for
        // the screen that gates on it, and the profile's own default has already failed closed to
        // `Member` for a missing or unrecognised claim.
        _currentUserRole.value = profile?.role
    }

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
     * Applies a new glass amount *without* persisting it.
     *
     * Split from [commitGlassIntensity] because the slider is continuous: the appearance has to
     * follow the thumb on every frame of a drag, and the store has to see one write when the
     * finger lifts. One method doing both would put a disk write behind every frame.
     */
    fun previewGlassIntensity(intensity: Float) {
        _glassIntensity.value = intensity.coerceIn(0f, 1f)
    }

    /**
     * Applies and persists the glass amount the user settled on.
     *
     * Clamped on the way in, because the store is not only written by the slider: a stored
     * value that has been hand-edited, or a caller that passes a percentage, must not be able
     * to hand the design system an alpha outside `0f..1f`, which `lerp` walks past its
     * endpoints without complaint. State first, write regardless — same reasoning as
     * [setThemeScheme]: a full disk should not make the slider inert.
     */
    suspend fun commitGlassIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        _glassIntensity.value = clamped
        runCatching { store.set(clamped.toString(), GLASS_INTENSITY_KEY) }
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

        /**
         * The glass amount, stored as the float's own `toString` rather than as an integer
         * percentage.
         *
         * A fraction because that is what the design system interpolates on: rounding to a
         * percentage at the storage layer would put a quantisation step between the user's
         * thumb and the alpha, and the slider would visibly stick at the ends of every
         * percent.
         */
        const val GLASS_INTENSITY_KEY: String = "com.tasktracker.settings.glassIntensity"

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
