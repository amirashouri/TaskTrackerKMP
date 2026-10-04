package com.tasktracker.sharedui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.DateProvider
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.AmbientBackdrop
import com.tasktracker.designsystem.SystemBarAppearance
import com.tasktracker.designsystem.ttIsDarkTheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.di.Resolver
import com.tasktracker.featureauthdomain.AuthSessionPort
import com.tasktracker.featureauthpresentation.ProvideAuthSession
import com.tasktracker.featureauthpresentation.SignInView
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectspresentation.MissingProjectEnvironmentView
import com.tasktracker.featureprojectspresentation.ProjectEnvironment
import com.tasktracker.featureprojectspresentation.ProvideProjectEnvironment
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.routing.RootDestination
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.delay

/**
 * The single view that decides which half of the app is on screen.
 *
 * ## It renders the router's answer and never makes one
 *
 * [RootDestination] is owned by [Router], and only the session layer moves it. This composable
 * reads it and branches. That is the whole of its authority, and it is why it holds no "which
 * half am I" flag of its own: two answers to one question is how a sign-out ends up applied to
 * the tree and not to the navigation, or the other way round.
 *
 * The session layer it delegates to is [com.tasktracker.compositionroot.SessionCoordinator],
 * resolved from the graph rather than reimplemented here. The shell used to subscribe to the
 * session and route it itself, and that duplication is exactly what a graph-owned coordinator
 * removes: a screen's own subscription can miss the transition that is tearing it down.
 *
 * ## [ttTheme] wraps *this*, not the entry point
 *
 * The appearance is a [ShellPreferences] value read from the store during the splash, and the
 * splash is the frame that covers that read. Applying the theme in the platform entry point
 * instead would mean the entry point had to resolve [ShellPreferences] before anything else
 * exists — so the theme, the preferences that carry it, and the screen that restores them
 * would be three objects constructed in two files with the ordering implied rather than
 * written down. Wrapping here keeps the read and its use in one place.
 *
 * The first composed frame is therefore `system` and it settles during the hold, which is
 * invisible: the splash is drawn over the backdrop, and the backdrop is what changes.
 *
 * ## The splash gate needs **both** halves, and each alone is a real bug
 *
 * `!hasHeldMinimum || navigation.isRestoringSession`.
 *
 * Without the floor, a keychain read that finishes in milliseconds flashes the mark for two
 * frames — a glitch rather than a launch.
 *
 * Without the session, a *slow* read hands off to the sign-in form for somebody who is already
 * signed in and then replaces it with their board a moment later. That is the flash the launch
 * screen exists to prevent, and it is why a fixed hold is not sufficient on its own: the hold
 * bounds how long the launch *looks* like a launch, and only the session says whether there is
 * anything to hand off to.
 *
 * [splashMinimumHoldMillis] is a floor, not a wait. The session is read *during* it, so it
 * never adds to startup — it only decides how long the splash is drawn. It is a parameter so a
 * test can hold for one frame; production passes the default.
 *
 * ## Preferences restore **before** anything writes, and that ordering is load-bearing
 *
 * One effect calls [ShellPreferences.restore], and the two effects that *write* are gated on
 * [isPreferencesRestored]. Without the gate they race the read: the active project starts
 * `null`, so `rememberActiveProject(null)` writes an empty string, and a relaunch then reads
 * back nothing. That is a silent, intermittent loss of the user's place — the kind that only
 * reproduces on a fast launch, which is exactly the kind that gets reported as flaky.
 *
 * ## The last project is restored on **both** roots, and both calls are idempotent
 *
 * Keyed on [RootDestination.Main], so it runs on first composition *and* again whenever the
 * root becomes `Main`.
 *
 * At launch the router is on the auth gate, and the session restore that flips it to `Main` is
 * asynchronous — so a launch-only restore races the sign-in and usually loses. That race is the
 * entire bug: the user lands on the dashboard instead of the board they left, intermittently,
 * depending on how fast the keychain read was. A single effect keyed on the root rather than
 * on `Unit` covers both, because the root arriving *is* the signal.
 *
 * ## The cross-fade, and why the backdrop is not animated
 *
 * [AmbientBackdrop] is drawn once and the two contents fade over it. Animating the backdrop
 * instead would mean repainting a gradient on every frame of a transition that is only ever
 * between two states of the same theme — the same pixels, so the cost buys nothing.
 *
 * The backdrop fills the full window, while one foreground container applies the top safe-drawing
 * inset to both content and splash. Applying the inset before this screen shrinks the backdrop
 * too, exposing the window's light background behind a notch when the app is pinned dark.
 * `windowInsetsPadding` consumes the inset, so child screens cannot apply it twice.
 *
 * The fade is [CONTENT_FADE_MILLIS] on both layers, and equal on purpose: a fade that started
 * before the splash finished moving its own content, or outlasted it, would show two things at
 * once, which is the flash the gate exists to prevent reappearing as a gentler version of
 * itself.
 */
@Composable
fun RootScreen(
    resolver: Resolver,
    modifier: Modifier = Modifier,
    splashMinimumHoldMillis: Long = SPLASH_MINIMUM_HOLD_MILLIS,
) {
    val router = remember(resolver) { resolver.resolve(Router::class) }
    // Handed to `ProvideAuthSession` for the sign-in form's own model. Not a subscription:
    // the session→router transition belongs to the graph's coordinator.
    val session = remember(resolver) { resolver.resolve(AuthSessionPort::class) }
    val preferences = remember(resolver) {
        ShellPreferences(store = resolver.resolve(UserDefaultsStore::class), router = router)
    }

    // The projects collaborators, resolved rather than constructed. `getOrNull` on both
    // because a graph can legitimately be built without projects — the exact case
    // `MissingProjectEnvironmentView` exists for, and a `resolve` here would turn "not wired"
    // into a crash on the first frame rather than a sentence on it.
    val environment = remember(resolver) {
        val repository = resolver.getOrNull(ProjectRepositoryPort::class)
        val clock = resolver.getOrNull(DateProvider::class)
        if (repository == null || clock == null) null else ProjectEnvironment(repository, clock)
    }

    // Handed down to the Settings tab rather than resolved by its screen, so a missing port is
    // one tab's sentence rather than a crash or a whole-shell fallback.
    val settings = remember(resolver) { resolver.getOrNull(SettingsPort::class) }

    var hasHeldMinimum by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(splashMinimumHoldMillis)
        hasHeldMinimum = true
    }

    // One restore, and everything that writes waits for it. See the note on the ordering.
    var isPreferencesRestored by remember { mutableStateOf(false) }
    LaunchedEffect(preferences) {
        preferences.restore()
        isPreferencesRestored = true
    }

    // One collected snapshot for both `root` and `isRestoringSession`, rather than two reads.
    // They are two fields of one published value, and reading them a frame apart is a frame
    // where the splash and the tree disagree about the session.
    var navigation by remember(router) { mutableStateOf(router.state.value) }
    LaunchedEffect(router) { router.state.collect { navigation = it } }

    val showsSplash = !hasHeldMinimum || navigation.isRestoringSession

    // The active project is remembered on every change rather than on sign-out, so the value
    // on disk is the last one the user actually had — and never before the read above.
    val activeProjectID = navigation.activeProjectID
    LaunchedEffect(isPreferencesRestored, activeProjectID) {
        if (isPreferencesRestored) preferences.rememberActiveProject(activeProjectID)
    }

    LaunchedEffect(isPreferencesRestored, navigation.root, environment) {
        if (isPreferencesRestored && navigation.root == RootDestination.Main) {
            restoreLastProject(router, preferences, environment)
        }
    }

    // The signed-in person's name, read once the main tree is actually up — not in
    // `preferences.restore()`, which runs before anybody has signed in and so would hand the
    // board's "show only my tasks" filter a null on every cold launch. A sign-in writes the
    // cached profile before the session coordinator moves the root, so arriving here *is* the
    // signal that the profile on disk belongs to the session now in front of us.
    //
    // Keyed on the root rather than run once for the same reason as the two effects above: the
    // first `Main` after an auth-gate launch is the first moment the answer can differ from
    // `null`, and a sign-out back to `Auth` followed by another sign-in has to re-read it.
    LaunchedEffect(isPreferencesRestored, navigation.root) {
        if (isPreferencesRestored && navigation.root == RootDestination.Main) {
            preferences.refreshCurrentUser()
        }
    }

    val themeScheme by preferences.themeScheme.collectAsState()
    val accentPalette by preferences.accentPalette.collectAsState()
    val glassIntensity by preferences.glassIntensity.collectAsState()
    val isDark = ttIsDarkTheme(themeScheme)
    SystemBarAppearance(isDark)

    // The two alphas are deliberately not each other's inverse. The splash fades out on a
    // timer and takes itself out of the composition when it reaches zero; the content is
    // mounted the moment the gate opens and fades in over the same length. Composing both and
    // cross-fading them is what makes this a handoff rather than a cut — and mounting the
    // content *only* at the gate is the part that matters for cost: a board's models must not
    // be built and start observing behind a splash that has not finished drawing.
    val splashAlpha = animateFloatAsState(
        targetValue = if (showsSplash) 1f else 0f,
        animationSpec = tween(durationMillis = CONTENT_FADE_MILLIS),
        label = "splashFade",
    )
    val contentAlpha = animateFloatAsState(
        targetValue = if (showsSplash) 0f else 1f,
        animationSpec = tween(durationMillis = CONTENT_FADE_MILLIS),
        label = "rootContentFade",
    )

    // Latched, not derived from `showsSplash`: the content has to be mounted for the frame
    // that starts its fade-in, and a derived value would not be.
    var isContentMounted by remember { mutableStateOf(false) }
    LaunchedEffect(showsSplash) { if (!showsSplash) isContentMounted = true }

    ttTheme(themeScheme, accentPalette, glassIntensity) {
        Box(modifier = modifier.fillMaxSize().testTag(RootScreenTag)) {
            // Drawn once, outside both fading layers, so the transition is a change of what is
            // *over* the backdrop rather than a change of the backdrop.
            AmbientBackdrop()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            ) {
                if (isContentMounted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(contentAlpha.value)
                            .testTag(RootContentTag),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (navigation.root) {
                            RootDestination.Auth -> ProvideAuthSession(session) {
                                SignInView(onAuthenticated = {})
                            }

                            RootDestination.Main -> ProvideAuthSession(session) {
                                SignedInArea(
                                    environment = environment,
                                    settings = settings,
                                    preferences = preferences,
                                    router = router,
                                )
                            }
                        }
                    }
                }

                // Still composed while it fades, which is the other half of a cross-fade. Taken
                // out at zero rather than left at `alpha(0f)`, so a splash that has faded is
                // also a splash that is no longer in the tree.
                if (showsSplash || splashAlpha.value > 0f) {
                    SplashScreen(
                        isWorking = navigation.isRestoringSession,
                        modifier = Modifier.alpha(splashAlpha.value),
                    )
                }
            }
        }
    }
}

/**
 * Applies the persisted last project, validating it against the store first.
 *
 * Idempotent by construction: [Router.restoreLastVisitedProject] guards on the current root and
 * assigns, so calling this on both triggers costs one extra store read and nothing else.
 *
 * The validator is the load-bearing half. A default pointing at a row this device no longer has
 * is *dropped*, not restored — otherwise every visit to a deleted project's board starts with a
 * fetch that finds nothing, and the id survives every launch to be re-validated forever. Which
 * is why the read is a `try`/`catch` and not `runCatching { … != null }`: the answer has to
 * stay tri-state, because `true` restores, `false` drops a deleted default, and `null` — the
 * read itself failed — keeps the id and skips, since unreadable is not gone.
 *
 * A `null` environment answers `null` too: there is nothing to validate against, so nothing is
 * restored and nothing is dropped.
 */
private suspend fun restoreLastProject(
    router: Router,
    preferences: ShellPreferences,
    environment: ProjectEnvironment?,
) {
    val stored = preferences.lastVisitedProjectID ?: return
    val projectID = runCatching { ProjectID(stored) }.getOrNull() ?: return

    router.restoreLastVisitedProject(projectID) { id ->
        val repository = environment?.repository ?: return@restoreLastVisitedProject null
        try {
            repository.project(id) != null
        } catch (error: Exception) {
            null
        }
    }
}

/**
 * The signed-in half: the four tabs, and the preference providers their screens read.
 *
 * A missing settings port does *not* replace the whole signed-in area. It replaces the Settings
 * tab, because that is the only tab that needs it: showing "settings is unavailable" in place
 * of Projects, Board and Backlog would break three working screens to explain a fourth. So the
 * port is handed to [NavigationHost] and the gate is drawn there.
 *
 * A missing projects environment, by contrast, *does* replace the signed-in content, because
 * every tab below it needs the repository — the same decision `MissingProjectEnvironmentView`
 * documents.
 *
 * The chrome is [NavigationHost]'s and not a wrapper here: the tab bar belongs to the thing that
 * decides which tab is on screen, so "what is in the signed-in area" has one answer. The identity
 * header that used to sit above it was removed on request — it cost the board real estate the
 * columns needed — which left the app with no way to end a session by hand. Settings is that
 * home now, via `SettingsPort.endSession`, which the graph binds to the session coordinator.
 */
@Composable
private fun SignedInArea(
    environment: ProjectEnvironment?,
    settings: SettingsPort?,
    preferences: ShellPreferences,
    router: Router,
) {
    val themeScheme by preferences.themeScheme.collectAsState()

    if (environment == null) {
        MissingProjectEnvironmentView()
        return
    }

    ProvideProjectEnvironment(environment) {
        NavigationHost(
            router = router,
            environment = environment,
            settings = settings,
            preferences = preferences,
            themeScheme = themeScheme,
            modifier = Modifier.testTag(SignedInShellTag),
        )
    }
}

/** The handoff's cross-fade length. Equal to nothing else on purpose — see the class note. */
const val CONTENT_FADE_MILLIS: Int = 350

/** The root's own container. */
const val RootScreenTag = "shell.root"

/** The signed-in or signed-out content, as opposed to the splash drawn over it. */
const val RootContentTag = "shell.rootContent"

/** The signed-in area as a whole, so a test asserts on the shell rather than its content. */
const val SignedInShellTag = "shell.signedIn"
