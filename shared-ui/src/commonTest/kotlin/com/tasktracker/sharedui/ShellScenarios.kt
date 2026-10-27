@file:OptIn(ExperimentalTestApi::class)

package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.tasktracker.core.ProjectID
import com.tasktracker.core.SprintID
import com.tasktracker.core.randomTagID
import com.tasktracker.designsystem.NavigationRailLensTag
import com.tasktracker.designsystem.TTGlassBackButtonTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.domain.Project
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.featureauthdomain.UserRole
import com.tasktracker.featureauthpresentation.EmailFieldTag
import com.tasktracker.featureauthpresentation.PasswordFieldTag
import com.tasktracker.featureauthpresentation.PrimaryButtonTag
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.featureprojectspresentation.AdminProjectsAddTag
import com.tasktracker.featureprojectspresentation.AdminProjectsScreenTag
import com.tasktracker.featureprojectspresentation.BacklogRemoveFromSprintTag
import com.tasktracker.featureprojectspresentation.BoardFileAwayTag
import com.tasktracker.featureprojectspresentation.MissingProjectEnvironmentTag
import com.tasktracker.featureprojectspresentation.NewProjectCreateTag
import com.tasktracker.featureprojectspresentation.NewProjectFormTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogListTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogScreenTag
import com.tasktracker.featureprojectspresentation.ProjectBoardColumnsTag
import com.tasktracker.featureprojectspresentation.ProjectBoardScreenTag
import com.tasktracker.featureprojectspresentation.ProjectEditorTag
import com.tasktracker.featureprojectspresentation.ProjectRowTag
import com.tasktracker.featureprojectspresentation.ProjectRowTagPrefix
import com.tasktracker.featureprojectspresentation.ProjectSwitcherOptionTag
import com.tasktracker.featureprojectspresentation.ProjectSwitcherTag
import com.tasktracker.featureprojectspresentation.ProjectsListNewTag
import com.tasktracker.featureprojectspresentation.ProjectsListScreenTag
import com.tasktracker.featureprojectspresentation.ProjectsListTag
import com.tasktracker.featureprojectspresentation.SprintDetailAddTag
import com.tasktracker.featureprojectspresentation.SprintDetailCardTag
import com.tasktracker.featureprojectspresentation.SprintDetailPlaceholderTag
import com.tasktracker.featureprojectspresentation.SprintDetailScreenTag
import com.tasktracker.featureprojectspresentation.SprintEditorCancelTag
import com.tasktracker.featureprojectspresentation.SprintEditorSheetTag
import com.tasktracker.featureprojectspresentation.SprintListNewTag
import com.tasktracker.featureprojectspresentation.SprintListScreenTag
import com.tasktracker.featureprojectspresentation.SprintPickerAddTag
import com.tasktracker.featureprojectspresentation.SprintPickerRowTag
import com.tasktracker.featureprojectspresentation.SprintPickerSheetTag
import com.tasktracker.featureprojectspresentation.SprintRowTag
import com.tasktracker.featureprojectspresentation.WorkItemBacklogRowTag
import com.tasktracker.featureprojectspresentation.WorkItemBacklogRowTagPrefix
import com.tasktracker.featureprojectspresentation.WorkItemCancelTag
import com.tasktracker.featureprojectspresentation.WorkItemCardTag
import com.tasktracker.featureprojectspresentation.WorkItemFileAwayTag
import com.tasktracker.featureprojectspresentation.WorkItemManageTagsTag
import com.tasktracker.featureprojectspresentation.WorkItemTagOptionTag
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingspresentation.ServerAddressFieldTag
import com.tasktracker.featuresettingspresentation.SettingsManageMembersTag
import com.tasktracker.featuresettingspresentation.SettingsManageProjectsTag
import com.tasktracker.featuresettingspresentation.SettingsScreenTag
import com.tasktracker.featuresettingspresentation.SettingsSignOutTag
import com.tasktracker.featuresettingspresentation.ThemeDropdownTag
import com.tasktracker.featuresettingspresentation.ThemeOptionTag
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamspresentation.AddMemberCreateTag
import com.tasktracker.featureteamspresentation.AddMemberEmailFieldTag
import com.tasktracker.featureteamspresentation.AddMemberFormTag
import com.tasktracker.featureteamspresentation.AddMemberPasswordFieldTag
import com.tasktracker.featureteamspresentation.MembersAddTag
import com.tasktracker.featureteamspresentation.MembersAdminScreenTag
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_detail_placeholder_body
import com.tasktracker.resources.sprint_detail_placeholder_title
import com.tasktracker.resources.tab_backlog
import com.tasktracker.resources.tab_board
import com.tasktracker.resources.tab_projects
import com.tasktracker.resources.tab_settings
import com.tasktracker.resources.tab_sprints
import com.tasktracker.routing.AppRoute
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.Router
import com.tasktracker.routing.adminMembersStack
import com.tasktracker.routing.adminProjectsStack
import com.tasktracker.sharedui.ShellScenarios.DESKTOP_WIDTH
import com.tasktracker.sharedui.ShellScenarios.OVERFLOWING_ROWS
import com.tasktracker.sharedui.ShellScenarios.PHONE_WIDTH
import com.tasktracker.sharedui.ShellScenarios.SIDEBAR_DENSITY
import com.tasktracker.sharedui.ShellScenarios.SIDEBAR_WIDTH
import com.tasktracker.sharedui.ShellScenarios.assertBottomMostRowClearsTheBar
import com.tasktracker.sharedui.ShellScenarios.desktopWidthIsExpressible
import com.tasktracker.sharedui.ShellScenarios.selectedRailDestinations
import com.tasktracker.sharedui.ShellScenarios.selectedSidebarDestinations
import com.tasktracker.sharedui.ShellScenarios.selectedTabs
import com.tasktracker.userdefaults.DefaultsKey
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The app shell's behaviour, as an object of scenarios with no test framework in them.
 *
 * ## These exist because the wiring was never tested
 *
 * Every one of these assertions describes a bug that shipped. The shell resolved nothing,
 * so [com.tasktracker.sharedui.SignInView] — fully implemented, with 13 model tests and 21
 * UI tests green — had no path to a screen. A test on the sign-in screen itself could not
 * have caught that, because the screen was never the problem: the suite was comprehensive
 * about a component that nothing called.
 *
 * So these are *shell* scenarios. They build the real [TaskTrackerApp] and assert which
 * screen came out, which is the only level at which the original defect existed.
 *
 * ## The signed-in half was the same defect a second time
 *
 * The signed-in destination was a placeholder: the projects feature had eight screens, seven
 * models and 27 UI scenarios green, and the shell rendered the words "Signed in" in the
 * middle of a blank page. The scenarios below are the wiring assertions for that, and two of
 * them exist because a screen's own test suite cannot cover this level — a model cannot be
 * asked whether the button above it has a caller, and that is precisely how a "File Away"
 * button shipped with nothing behind it.
 *
 * Same per-target split, and for the same reason, as `SignInScenarios`: the Android host
 * needs a Robolectric runner, and a runner annotation is not expressible in `commonTest`.
 */
object ShellScenarios {

    /**
     * A hold longer than the compact editor's exit (350ms in `NavigationHost`).
     *
     * The editor keeps its route mounted while the slide plays, so a scenario that asserts the
     * editor is gone has to move the test clock past that hold. 500 rather than the exact 350
     * so the assertion is about "the dismissal completed" rather than about arithmetic on a
     * production constant this module cannot see.
     */
    private const val EDITOR_EXIT_SLACK_MS = 500L

    /**
     * Sets the app up the way the graph does: bindings, then the session→router driver.
     *
     * The driver is [ScriptedSessionDriver] rather than the graph's `SessionCoordinator`
     * because this module cannot build one — see that class for why, and for why the routing
     * rules it copies are already covered by `SessionCoordinatorTest` in `:composition-root`.
     * What these scenarios are for is the level below that: given a routed router, does the
     * shell draw the right screen.
     *
     * [splashHold] is one frame rather than the production 700ms. Every signed-in assertion
     * here has to get past the splash, and paying 700ms of wall clock per scenario would add
     * seconds to a suite whose entire purpose is being the fast loop. The hold's *behaviour* is
     * asserted separately, in [theSplashHoldsUntilBothTheFloorAndTheSession].
     *
     * `internal` rather than `private` so `TabBarScreenshotTest` on the JVM can stand the
     * signed-in shell up outside the scenario list. That test renders the bar to a PNG, and it
     * needs exactly the same graph this does — reimplementing the wiring in a second place
     * would mean a screenshot of a shell that is not the shell.
     *
     * ## [width] defaults to the phone, and that is a change of meaning
     *
     * It used to default to `null`, which meant "whatever viewport this host happens to give the
     * root". That was invisible while the shell drew the same chrome at every width, and it stopped
     * being true the moment the shell chose its chrome from the width it was offered: the JVM host
     * renders into a window comfortably past the 840dp threshold, so every scenario that did not
     * state a width silently became an expanded-shell scenario and twelve of them began failing
     * on a `shell.tabs` tag that was no longer composed.
     *
     * The shell was right and the scenarios were underspecified. A scenario that asserts phone
     * behaviour and says nothing about the width was relying on the host's window as an
     * environment detail, and the fix is to make the default the width these scenarios are about
     * rather than to widen the threshold until the accident stops happening. A scenario that
     * wants the wide layout says so — see [aWideShellDrawsTheRailAndNoBar].
     *
     * ## [widthState] is for the scenarios about a *resize*
     *
     * Most scenarios are about a shell at one width, and [width] states it. Two widths in one run
     * is a different claim — that the shell survives crossing the `Expanded`/`Compact` boundary —
     * and it cannot be made by a parameter read once before `setContent`. A scenario that needs it
     * passes a [MutableState] and writes to it; the value is read inside the composition, so the
     * shell sees the new width. [width] is then ignored, because two sources for one width is a
     * question with two answers.
     */
    internal suspend fun ComposeUiTest.setUpShell(
        session: ScriptedSession,
        router: Router = Router(),
        projects: ProjectRepositoryPort = FakeProjectRepository(),
        omitProjects: Boolean = false,
        settings: SettingsPort? = null,
        preferences: UserDefaultsStore = UserDefaultsStore(InMemoryPreferences()),
        splashHold: Long = 0L,
        width: Dp? = PHONE_WIDTH,
        widthState: MutableState<Dp>? = null,
        density: Density = Density(1f),
        deepLinks: DeepLinkSource = DeepLinkSource(),
        navigationEventOwner: NavigationEventDispatcherOwner? = null,
        memberAdminPort: MemberAdminPort? = null,
        adminProjectsPort: AdminProjectsPort? = null,
    ): FakeResolver {
        val resolver = if (omitProjects) {
            FakeResolver.withoutProjects(session = session, router = router, preferences = preferences)
        } else {
            FakeResolver.forSession(
                session = session,
                router = router,
                projects = projects,
                settings = settings,
                preferences = preferences,
                memberAdminPort = memberAdminPort,
                adminProjectsPort = adminProjectsPort,
            )
        }
        // The driver starts *before* `setContent`, and that order is the point: it routes the
        // router synchronously, so the shell composes against a session that has already been
        // read rather than racing a restore it would have to wait for. The reverse order works
        // too, but only because `waitForIdle` happens to drain it — and a test whose correctness
        // rests on that is a test that breaks when a frame is added.
        resolver.startSessionDriver()
        // The width is a *constraint on the parent*, not a resize of the window and not a
        // density override. The app fills whatever it is given, so a fixed-width Box lays the
        // whole shell out at that width while leaving every node reporting in one coordinate
        // system — which is the whole reason this is done this way. See
        // [fiveTabsFitAtPhoneWidth].
        setContent {
            // Read inside the composition, so a scenario that supplies [widthState] can change the
            // width mid-test: `ShellLayout` resolves its class from the width it is offered, and a
            // width that only ever changes outside the composition is a width the shell never
            // sees.
            val shellWidth = widthState?.value ?: width
            // The density override is what makes a *dp* width larger than the virtual display's
            // pixel width expressible. The display is 1024x768 px, so a 1280dp shell is clamped
            // to 1024dp at the default density and the sidebar class (>= 1120dp) can never be
            // composed. At 0.75 the display is 1365x1024dp, the requested width fits, and every
            // measurement the scenarios take is still in dp — a 16dp edge inset is 12px there
            // and reads back as 16dp. Scenarios that do not ask for a wider-than-1024dp shell
            // keep the default, so nothing else changes shape.
            val app: @Composable () -> Unit = {
                CompositionLocalProvider(LocalDensity provides density) {
                    if (shellWidth == null) {
                        TaskTrackerApp(
                            resolver,
                            splashMinimumHoldMillis = splashHold,
                            deepLinks = deepLinks,
                        )
                    } else {
                        Box(modifier = Modifier.width(shellWidth)) {
                            TaskTrackerApp(
                                resolver,
                                splashMinimumHoldMillis = splashHold,
                                deepLinks = deepLinks,
                            )
                        }
                    }
                }
            }
            // An explicit provider shadows the host default on *every* host,
            // Android included, so one simulation of a system-back press works
            // on all three. On Android it replaces the activity's dispatcher
            // for this subtree — the shell's `BackHandler` registers with
            // whichever owner the local holds — while the activity-side chain
            // itself is the platform's, not the shell's, and is unchanged.
            if (navigationEventOwner == null) {
                app()
            } else {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides navigationEventOwner,
                ) {
                    app()
                }
            }
        }
        return resolver
    }

    /**
     * A signed-out session shows the sign-in form. The regression this file exists for.
     *
     * Asserted on the three interactive tags rather than on the word "Task Tracker": the
     * title is content that a redesign can change, whereas the fields and the button are
     * the screen's actual contract with a user.
     */
    val aSignedOutSessionShowsSignIn: suspend ComposeUiTest.() -> Unit = {
        setUpShell(session = ScriptedSession())

        waitForIdle()
        onNodeWithTag(EmailFieldTag).assertIsDisplayed()
        onNodeWithTag(PasswordFieldTag).assertIsDisplayed()
        onNodeWithTag(PrimaryButtonTag).assertIsDisplayed()
    }

    /**
     * Typed credentials reach the session port the shell resolved.
     *
     * Without this, a shell that rendered the form but wired it to nothing would satisfy
     * [aSignedOutSessionShowsSignIn] completely — correct on screen, inert underneath. This
     * is the assertion that separates the two.
     */
    val signingInFromTheShellReachesTheSessionPort: suspend ComposeUiTest.() -> Unit = {
        val session = ScriptedSession(onSignIn = { _, _ -> signedIn() })
        setUpShell(session = session)

        waitForIdle()
        onNodeWithTag(EmailFieldTag).performTextInput("person@example.com")
        onNodeWithTag(PasswordFieldTag).performTextInput("correct horse battery staple")
        onNodeWithTag(PrimaryButtonTag).performClick()
        waitForIdle()

        assertEquals(
            listOf("person@example.com" to "correct horse battery staple"),
            session.signInAttempts,
            "expected the shell to hand both fields to the session port verbatim",
        )
    }

    /**
     * Nothing is rendered while the session is still [SessionState.Restoring].
     *
     * Asserted as the absence of *every* screen rather than the presence of a spinner: a
     * launch path that flashes the sign-in form at a returning user is the bug, and a
     * spinner would satisfy this test while still being a visible flash of nothing useful.
     *
     * [ScriptedSession] is seeded with `Restoring` and a *different* restore result, so the
     * projects list here can only appear because restore completed. Without that the
     * assertion would pass trivially against a shell that renders nothing, ever.
     */
    val aRestoreInFlightShowsNeitherScreen: suspend ComposeUiTest.() -> Unit = {
        val gate = CompletableDeferred<Unit>()
        val session = ScriptedSession(
            initial = SessionState.Restoring,
            restored = signedIn(),
            restoreGate = gate,
        )
        setUpShell(session = session)

        // The shell must render nothing while the answer is outstanding. `waitForIdle` does
        // not settle this: the `LaunchedEffect` is suspended on `gate`, which is idle as far
        // as Compose is concerned, so the frame completes with no decision made.
        waitForIdle()
        assertTrue(
            onAllNodes(hasTestTag(EmailFieldTag)).fetchSemanticsNodes().isEmpty(),
            "expected no sign-in form while the session is still restoring",
        )
        // The projects area is the other half of the same bug. A signed-in tree that opened
        // the store before the session was read would put one person's projects on screen for
        // whoever is restoring, and the sign-in flash is only the visible symptom of it.
        assertTrue(
            onAllNodes(hasTestTag(ProjectsHostTag)).fetchSemanticsNodes().isEmpty(),
            "expected no projects before restore settled",
        )

        // Then the answer arrives, and the projects list replaces the blank frame — which is
        // the whole point: the user saw nothing, not the wrong screen, then the right one.
        gate.complete(Unit)
        waitForIdle()
        onNodeWithTag(ProjectsListScreenTag).assertIsDisplayed()
    }

    /**
     * A restored session shows the projects list, with the form gone rather than covered.
     *
     * This is the assertion that the *port* half is wired. The projects list can only be on
     * screen if the shell resolved a repository, built a `ProjectEnvironment` from it, and
     * handed that to a real model — which is three seams, and each of them silent when broken.
     */
    val aRestoredSessionShowsTheProjectsList: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        projects.seed(shellTestProject(name = "Apollo"))
        val session = ScriptedSession(restored = signedIn("returning@example.com"))
        setUpShell(session = session, projects = projects)

        waitForIdle()

        onNodeWithTag(SignedInShellTag).assertIsDisplayed()
        onNodeWithTag(ProjectsListScreenTag).assertIsDisplayed()
        onNodeWithText("Apollo").assertIsDisplayed()
        assertTrue(
            onAllNodes(hasTestTag(EmailFieldTag)).fetchSemanticsNodes().isEmpty(),
            "expected no sign-in form for a restored session",
        )
        // The identity header is gone, and "who am I" is no longer answered by the first frame
        // of the signed-in area. Asserted as an absence rather than left to the next person to
        // notice, because the header was a real feature and this is its replacement contract.
        assertTrue(
            onAllNodes(hasText("returning@example.com")).fetchSemanticsNodes().isEmpty(),
            "expected the signed-in identity to be gone from the shell",
        )
    }

    /**
     * A graph with no projects repository says so, rather than showing an empty list.
     *
     * The distinction is the whole point: an empty list is a claim about the *store* ("you
     * have no projects") and the gate is a claim about the *build* ("nothing here can read
     * your projects"). Showing the list in the second case would tell someone with a dozen
     * projects that they have none, which is worse than a build that admits what it is.
     */
    val aGraphWithoutProjectsSaysSo: suspend ComposeUiTest.() -> Unit = {
        val session = ScriptedSession(restored = signedIn())
        setUpShell(session = session, omitProjects = true)

        waitForIdle()

        onNodeWithTag(MissingProjectEnvironmentTag).assertIsDisplayed()
        assertTrue(
            onAllNodes(hasTestTag(ProjectsListScreenTag)).fetchSemanticsNodes().isEmpty(),
            "expected no projects list when nothing can supply projects",
        )
        // The gate replaces the signed-in area's *content*, and the content is now all there is.
        assertTrue(
            onAllNodes(hasTestTag(ProjectsHostTag)).fetchSemanticsNodes().isEmpty(),
            "expected no projects host when nothing can supply projects",
        )
    }

    /**
     * The Settings tab ends the session, and it is the only place that can.
     *
     * This replaces `theSignedInShellOffersNoSignOut`, which asserted the *absence* of a
     * sign-out control after the identity header was removed on request. That scenario carried
     * an explicit instruction: delete it in the same change that gives sign-out a home. This is
     * that change, and deleting it is the point rather than a tidy-up — leaving it would assert
     * the opposite of what the app now does.
     *
     * Two things are asserted and the second is the one that matters. The control being
     * *present* proves the header's replacement exists; the port being *called* proves it is
     * not another live-looking button. The screen cannot name a session, so `endSession` on the
     * port is the only thing it can reach — and in production the graph binds that to
     * `SessionCoordinator.signOut`, which is what clears the stacks.
     */
    val theSettingsTabEndsTheSession: suspend ComposeUiTest.() -> Unit = {
        val session = ScriptedSession(restored = signedIn())
        val settings = FakeShellSettingsPort()
        setUpShell(session = session, settings = settings)

        waitForIdle()
        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        // Scrolled to the true end, not merely into the viewport: the last rows sit under the
        // floating tab bar, which takes the touch when a coordinate click lands there. The
        // semantics action asserts the row's wiring rather than its geometry, and the scroll
        // to the last index is what puts the row in front of the bar in the first place.
        scrollSettingsListToEnd()
        onNodeWithTag(SettingsSignOutTag)
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        assertEquals(
            1,
            settings.sessionEndCount,
            "expected the sign-out control to reach the settings port exactly once",
        )
        // And the shell itself still carries no identity row: the header's *cost* is what got it
        // removed, and re-adding it as part of "adding sign-out somewhere" would undo that.
        assertTrue(
            onAllNodes(hasText(signedIn().identity.email)).fetchSemanticsNodes().isEmpty(),
            "expected the signed-in identity to stay out of the shell",
        )
    }

    /**
     * A pairing link is the CLI's handoff, and the shell is the only place its hops meet.
     *
     * The parser is tested in `:packages:routing` and the prefill in
     * `:packages:feature-settings-presentation`, but neither proves the URL the OS delivered is
     * the one in the field: the source, the root collector, the router's one-shot address and
     * the Settings destination can each be correct alone and still not be connected.
     *
     * The address is encoded the way `tt` prints it, including a `%` in the payload, so a
     * second decode or a lost escape shows up here as a different string instead of passing.
     */
    val aConnectLinkOpensSettingsWithTheAddressPrefilled: suspend ComposeUiTest.() -> Unit = {
        val deepLinks = DeepLinkSource()
        val settings = FakeShellSettingsPort()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            settings = settings,
            deepLinks = deepLinks,
        )
        waitForIdle()

        deepLinks.receive("tasktracker://connect?url=https%3A%2F%2Ftasks.example.com%2Fa%252Fb")
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Settings)).assertIsSelected()
        onNodeWithTag(ServerAddressFieldTag)
            .assertIsDisplayed()
            .assert(hasText("https://tasks.example.com/a%2Fb"))
    }

    /**
     * A session that ends tears down the signed-in tree.
     *
     * Asserted three times over: the form comes back, the port ended up signed out, and the
     * projects area is gone. A shell that swapped screens on click without calling the port
     * would pass the first, and the second is the one that catches it; the third is what
     * catches a teardown that left the signed-in tree alive underneath.
     *
     * Driven through the port rather than through the sign-out button that used to sit in the
     * header, because there is no button any more. That is the point: a session can still end
     * — by expiry, or by `endSession` once settings exists — and it has to take this path.
     */
    val aSessionThatEndsTearsDownTheSignedInTree: suspend ComposeUiTest.() -> Unit = {
        val session = ScriptedSession(restored = signedIn())
        setUpShell(session = session)

        waitForIdle()
        session.signOut()
        waitForIdle()

        onNodeWithTag(EmailFieldTag).assertIsDisplayed()
        assertEquals(
            SessionState.SignedOut,
            session.currentState,
            "expected the port to be signed out after the session ended",
        )
        assertTrue(
            onAllNodes(hasTestTag(ProjectsHostTag)).fetchSemanticsNodes().isEmpty(),
            "expected the projects area to be gone after the session ended",
        )
    }

    /**
     * Signing out leaves nobody inside the last signed-in user's board.
     *
     * The router is app-scoped and outlives the session, so its stacks survive a sign-out
     * unless something clears them — and a device handed to a colleague, or a shared family
     * tablet, would then put the previous user's projects in front of the next one. That is a
     * disclosure rather than a cosmetic bug, and `enterAuth` is the only thing in the app
     * that wipes them.
     */
    val signingOutClearsTheNavigationStack: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Confidential")
        projects.seed(project)
        val router = Router()
        // `onSignIn` here for the second half of the scenario: the default fake refuses, and a
        // refused sign-in would leave the router empty for the wrong reason and pass.
        val session = ScriptedSession(
            restored = signedIn(),
            onSignIn = { _, _ -> signedIn() },
        )
        setUpShell(session = session, router = router, projects = projects)

        waitForIdle()
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 0))).performClick()
        waitForIdle()
        onNodeWithTag(ProjectBoardScreenTag).assertIsDisplayed()

        // Through the port, not the header button that used to be there: a session ending by
        // expiry has to clear the stacks just as thoroughly as a deliberate sign-out did.
        session.signOut()
        waitForIdle()

        assertEquals(
            emptyList(),
            router.state.value.projectsPath,
            "expected the end of a session to leave no navigation behind",
        )

        // And signing back in lands on the list, not on the board that was open before.
        onNodeWithTag(EmailFieldTag).performTextInput("next@example.com")
        onNodeWithTag(PasswordFieldTag).performTextInput("correct horse battery staple")
        onNodeWithTag(PrimaryButtonTag).performClick()
        waitForIdle()

        onNodeWithTag(ProjectsListScreenTag).assertIsDisplayed()
    }

    /**
     * Opening a project shows that project's board.
     *
     * The point of the exercise: the list and the board are one navigation apart, and the
     * router is what decides which. Asserted on the board's own tag rather than on a project
     * name, so a list that merely *kept* showing the project cannot pass it.
     */
    val openingAProjectShowsItsBoard: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        val item = shellTestWorkItem(projectID = project.id, keyPrefix = project.keyPrefix)
        projects.seed(project)
        projects.seed(item)
        setUpShell(session = ScriptedSession(restored = signedIn()), projects = projects)

        waitForIdle()
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 1))).performClick()
        waitForIdle()

        onNodeWithTag(ProjectBoardScreenTag).assertIsDisplayed()
        // The board is a *push* on this tab, so the list is replaced rather than drawn behind:
        // the router swapped the top of the stack rather than stacking two screens.
        assertTrue(
            onAllNodes(hasTestTag(ProjectsListScreenTag)).fetchSemanticsNodes().isEmpty(),
            "expected the board to replace the list, not sit on top of it",
        )
    }

    /**
     * The board's File Away button reaches the store.
     *
     * The screen has drawn that button since Phase 12, and the board's model had no verb for
     * it until this shell was wired — a gap no model test could see, because a model cannot be
     * asked whether the button above it has a caller. This is the assertion that a
     * live-looking control is not inert.
     */
    val filingACardAwayFromTheBoardReachesTheStore: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        val item = shellTestWorkItem(projectID = project.id, keyPrefix = project.keyPrefix)
        projects.seed(project)
        projects.seed(item)
        setUpShell(session = ScriptedSession(restored = signedIn()), projects = projects)

        waitForIdle()
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 1))).performClick()
        waitForIdle()

        onNodeWithTag(BoardFileAwayTag(item)).performClick()
        waitForIdle()

        assertTrue(
            projects.writes.contains("moveWorkItem:Closed"),
            "expected the card to be closed in the store, saw ${projects.writes}",
        )
    }

    /**
     * File Away in the editor closes the sheet it was tapped from.
     *
     * The board's file-away button lives on the board; the editor's lives in a modal the user
     * just opened over the board. Archived is a terminal state, so the action has to be a close
     * as much as a store write — a sheet left standing after the item left the board would keep
     * offering edits to a task the board no longer shows. Assert the whole route: the file-away
     * write reaches the store, and the editor destination is gone, leaving the board back on
     * screen.
     */
    val filingAwayInTheEditorClosesTheSheet: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        val item = shellTestWorkItem(projectID = project.id, keyPrefix = project.keyPrefix)
        projects.seed(project)
        projects.seed(item)
        setUpShell(session = ScriptedSession(restored = signedIn()), projects = projects)

        waitForIdle()
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 1))).performClick()
        waitForIdle()
        onNodeWithTag(WorkItemCardTag(item)).performClick()
        waitForIdle()
        onNodeWithTag(WorkItemEditorDestinationTag).assertExists()

        // The row's own OnClick action rather than a coordinate tap: the actions float now, and
        // a click that missed a centre would leave no write at all and read as a broken store
        // call.
        onNodeWithTag(WorkItemFileAwayTag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        // The compact editor holds the route for its exit slide, so the dismissal completes
        // on the test clock rather than on `waitForIdle` alone.
        mainClock.advanceTimeBy(EDITOR_EXIT_SLACK_MS)
        waitForIdle()

        assertTrue(
            projects.writes.contains("moveWorkItem:Closed"),
            "expected file-away in the editor to close the item in the store, saw ${projects.writes}",
        )
        assertTrue(
            onAllNodes(hasTestTag(WorkItemEditorDestinationTag)).fetchSemanticsNodes().isEmpty(),
            "expected file-away in the editor to close the sheet, not leave it standing over the board",
        )
        onNodeWithTag(ProjectBoardScreenTag).assertIsDisplayed()
    }

    /**
     * The toolbar's project name is the way back to the list.
     *
     * Asserted as a round trip rather than as a tap, because a control that navigates
     * *somewhere* is not the same as one that navigates *here*: the board was pushed onto the
     * Projects stack, so anything short of returning to the root leaves the user one tap from
     * nowhere.
     */
    val theBoardReturnsToTheList: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        projects.seed(shellTestWorkItem(projectID = project.id, keyPrefix = project.keyPrefix))
        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
        )

        waitForIdle()
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 1))).performClick()
        waitForIdle()
        // The list opens a project on the *Board* tab, where the board is the tab root and
        // `popToRoot` is a deliberate no-op — there is nothing to pop to. The switcher's way
        // back to the list exists on a stack that has the board pushed onto it, which is the
        // Projects tab's shape (and the shape a deep link installs). Putting the shell on that
        // stack keeps the assertion about the control rather than about which tab is selected.
        router.openProject(project.id, inTab = AppTab.Projects)
        // `openProject` installs the path but leaves the selected tab alone when the target is
        // Projects, so select it explicitly: `popToRoot()` acts on the selected tab.
        router.selectTab(AppTab.Projects)
        waitForIdle()
        onNodeWithTag(ProjectBoardScreenTag).assertIsDisplayed()

        onNodeWithTag(ProjectSwitcherTag).performClick()
        waitForIdle()

        onNodeWithTag(ProjectsListScreenTag).assertIsDisplayed()
    }

    /**
     * The projects list stops observing when the signed-in tree is gone.
     *
     * The subscription is what keeps the store answering "what changed?" forever, and a
     * collector left alive after sign-out re-reads the whole store on every write in the
     * feature for the rest of the process. Asserted on the store's own subscriber count
     * rather than on anything on screen, because a leak has no screen.
     */
    val signingOutStopsTheProjectsObservation: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val session = ScriptedSession(restored = signedIn())
        setUpShell(session = session, projects = projects)

        waitForIdle()
        assertTrue(
            projects.subscriberCount > 0,
            "expected the projects list to be observing while it is on screen",
        )

        session.signOut()
        waitForIdle()

        assertEquals(
            0,
            projects.subscriberCount,
            "expected no projects collector left after the session ended",
        )
    }

    /**
     * "New project" opens a sheet, and creating one adds it to the list.
     *
     * Both halves matter, and the second is the one that would pass alone: a sheet that opens
     * and whose Create button does nothing is a form that looks finished and is not, which is
     * the same class of defect as the File Away button above.
     *
     * The name field is found by the tag `ProjectsComponents` derives from its own label, so
     * the test asks for the field the same way the sheet names it.
     */
    val creatingAProjectAddsItToTheList: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val session = ScriptedSession(restored = signedIn())
        setUpShell(session = session, projects = projects)

        waitForIdle()
        onNodeWithTag(ProjectsListNewTag).performClick()
        waitForIdle()
        onNodeWithTag(NewProjectFormTag).assertIsDisplayed()

        onNodeWithTag("name").performTextInput("Gemini")
        onNodeWithTag(NewProjectCreateTag).performClick()
        waitForIdle()

        assertTrue(
            onAllNodes(hasTestTag(NewProjectFormTag)).fetchSemanticsNodes().isEmpty(),
            "expected the sheet to close once the project was created",
        )
        onNodeWithText("Gemini").assertIsDisplayed()
    }

    /**
     * The four tabs each get to a screen, and the tab bar is how.
     *
     * This is the shell's actual new surface — [NavigationHost] is the first thing in the
     * project that drew a tab bar, and every one of the other scenarios reaches its screen
     * from the *Projects* tab. So before this existed, three quarters of the app was reachable
     * in production and unproven by a single assertion.
     *
     * Asserted per tab, in order, because the interesting failure is not "a tab is broken" but
     * "two tabs drew the same screen" — a shared tag mistake, a `when` that forgot a branch. A
     * loop would catch that; four separate expectations in one scenario read as a checklist,
     * which is what this is.
     */
    val everyTabGetsToItsOwnScreen: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            settings = FakeShellSettingsPort(),
        )

        waitForIdle()
        // Projects is the landing tab, so it needs no click to be proven.
        onNodeWithTag(ProjectsListScreenTag).assertIsDisplayed()

        // A project has to be *open* before the scoped tabs have anything to draw, and this
        // test is about the tab bar, so the open goes through the list exactly as a user's
        // would. Without it the Board tab is legitimately correct and shows the scoped empty
        // state, which would make this a test of the picker instead.
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 0))).performClick()
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Board)).performClick()
        waitForIdle()
        onNodeWithTag(ProjectBoardScreenTag).assertIsDisplayed()

        onNodeWithTag(TabButtonTag(AppTab.Backlog)).performClick()
        waitForIdle()
        onNodeWithTag(ProjectBacklogScreenTag).assertIsDisplayed()

        onNodeWithTag(TabButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintListScreenTag).assertIsDisplayed()

        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        onNodeWithTag(SettingsScreenTag).assertIsDisplayed()

        // And only the last one is on screen: the tab bar replaced the destination rather than
        // layering it over the previous tab, which is the difference between a tab and a push.
        assertTrue(
            onAllNodes(hasTestTag(ProjectBoardScreenTag)).fetchSemanticsNodes().isEmpty(),
            "expected leaving a tab to remove its screen, not draw the next one over it",
        )
    }

    /**
     * Choosing a tab does not throw away the project.
     *
     * `Router.selectTab` was written to answer "which tab" and nothing else — deliberately, so
     * that switching tabs does not re-`openProject` and reset a board mid-thought. That
     * decision is invisible in a routing unit test (it asserts the tab changed) and invisible
     * in a single-tab test (the board was never left), so it needs a tab, a project, and a
     * return.
     *
     * Both directions are checked, because `selectTab` is a one-line `copy` and the two
     * directions fail for different mistakes: a `copy` that also reset the stack, and a tab
     * bar that re-opened the project on every tap. The second would leave the board looking
     * right while throwing away the scroll position and any pushed card.
     */
    val switchingTabsKeepsTheProject: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        val item = shellTestWorkItem(projectID = project.id, keyPrefix = project.keyPrefix)
        projects.seed(project)
        projects.seed(item)
        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
        )

        waitForIdle()
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 1))).performClick()
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Backlog)).performClick()
        waitForIdle()
        onNodeWithTag(TabButtonTag(AppTab.Board)).performClick()
        waitForIdle()

        onNodeWithTag(ProjectBoardScreenTag).assertIsDisplayed()
        assertEquals(project.id, router.state.value.activeProjectID, "expected the active project to survive a tab change")
    }

    /**
     * The sprints tab's project switcher navigates on `openProject` in the sprints tab.
     *
     * `ProjectSwitcherButton` is the same control the board uses, but its `onSelectProject`
     * is wired to `router.openProject(it, AppTab.Sprints)` here rather than to the board's
     * push, so the same tap must land on the sprint list of the *new* project and leave the
     * tab on Sprints. Asserted on the router state directly, because the board's switcher has
     * the same tag as the sprints' one and a tag-only check could not tell the two apart.
     */
    val switchingProjectsFromTheSprintsTabCallsOpenProject: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        val other = shellTestProject(name = "Viking", keyPrefix = "VI")
        projects.seed(project)
        projects.seed(other)
        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
        )

        waitForIdle()
        // Start from the first project so the sprints tab has a non-null project to switch away from.
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 0))).performClick()
        waitForIdle()
        onNodeWithTag(TabButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintListScreenTag).assertIsDisplayed()

        // Open the switcher and pick the other project.
        onNodeWithTag(ProjectSwitcherTag).performClick()
        waitForIdle()
        onNodeWithTag(ProjectSwitcherOptionTag(other.id)).performClick()
        waitForIdle()

        // The active project changed and the tab stayed on Sprints.
        assertEquals(other.id, router.state.value.activeProjectID, "expected openProject to set the active project to the selected one")
        assertEquals(AppTab.Sprints, router.state.value.selectedTab, "expected the sprints tab to stay selected after a project switch")
        onNodeWithTag(SprintListScreenTag).assertIsDisplayed()
    }

    /**
     * A single project is chosen by default on the scoped tabs.
     *
     * With exactly one project, landing on the Board tab opens it straight away instead of
     * showing the "no project selected" empty state and forcing the user to pick the only
     * option there is. Backlog and Sprints are served by the same scoped-tab screen.
     */
    val aSingleProjectIsChosenByDefaultOnTheBoard: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val router = Router()
        setUpShell(session = ScriptedSession(restored = signedIn()), router = router, projects = projects)

        waitForIdle()
        onNodeWithTag(TabButtonTag(AppTab.Board)).performClick()
        waitForIdle()

        assertEquals(
            project.id,
            router.state.value.activeProjectID,
            "expected the only project to be chosen by default on the board",
        )
        onNodeWithTag(ProjectBoardScreenTag).assertIsDisplayed()
    }

    /**
     * The bar floats: inset from the left and right edges, sitting on the bottom edge.
     *
     * This is the inverse of the assertion it replaces, and deliberately so. That one demanded
     * the bar reach both side edges, which was correct for the full-bleed bar and is exactly
     * wrong for this one: a capsule that touches both screen edges has no silhouette, so it
     * cannot read as floating no matter what material or shadow is on it. iOS 26's own tab bar
     * is inset, and the inset is the single change that makes the shape legible.
     *
     * The bottom edge is still asserted flush, and that is not an inconsistency. The bottom
     * inset is zero in a Compose UI test host on every platform here, so "sitting on the bottom
     * edge" is the only thing observable about the vertical placement, and the assertion that
     * matters — that the capsule stops *above* a home indicator — belongs to the modifier chain
     * and is checked by [theTabBarIsWiderThanItsTabs] not being contradicted by it. What this
     * pins is the *shape*: a horizontal inset and a vertical one that is not there.
     *
     * Measured against [SignedInShellTag] for the same reason the old version was: a tag
     * declared inside `NavigationHost` would be dead, because the caller passes its own
     * `testTag` in as the composable's `modifier` and `testTag` overwrites rather than merges.
     */
    val theTabBarFloatsAboveTheBottomEdge: suspend ComposeUiTest.() -> Unit = {
        setUpShell(session = ScriptedSession(restored = signedIn()))

        waitForIdle()
        val host = onNodeWithTag(SignedInShellTag).getBoundsInRoot()
        val bar = onNodeWithTag(TabBarTag).getBoundsInRoot()

        // Inset on both sides, and symmetrically. Symmetry is the part worth asserting rather
        // than just "less than": a one-sided inset is a bar that has been pushed off-centre,
        // which still satisfies `bar.left > host.left` and looks obviously wrong.
        assertTrue(
            bar.left > host.left,
            "expected the tab bar to float in from the left edge (bar ${bar.left} vs host ${host.left})",
        )
        assertTrue(
            bar.right < host.right,
            "expected the tab bar to float in from the right edge (bar ${bar.right} vs host ${host.right})",
        )
        assertEquals(
            host.left.value - bar.left.value,
            bar.right.value - host.right.value,
            "expected the tab bar's side insets to be equal",
        )

        // Flush bottom, per the KDoc: zero insets in this host, so this is the whole of the
        // vertical placement that is observable.
        assertEquals(
            host.bottom,
            bar.bottom,
            "expected the tab bar to sit on the bottom edge",
        )
    }

    /**
     * The bar is wider than one tab, and the four tabs split it evenly.
     *
     * Two properties, and the second is the one that actually earns the lens. The lens's travel
     * is `selectedIndex * segmentWidth`, so an unequal split does not merely look untidy — it
     * puts the highlight somewhere other than the tab it is meant to be on. This is not
     * hypothetical here: an early version of the new bar did not fill the width it was given,
     * wrapped to the sum of its four labels instead, and measured a 540pt bar in a 1024pt
     * window with four different segment widths.
     */
    val theTabBarIsWiderThanItsTabs: suspend ComposeUiTest.() -> Unit = {
        setUpShell(session = ScriptedSession(restored = signedIn()))

        waitForIdle()
        val bar = onNodeWithTag(TabBarTag).getBoundsInRoot()
        val tabs = AppTab.entries.map { onNodeWithTag(TabButtonTag(it)).getBoundsInRoot() }
        val barWidth = (bar.right - bar.left).value.toDouble()

        val tabWidth = (tabs.first().right - tabs.first().left).value.toDouble()
        assertTrue(
            barWidth > tabWidth,
            "expected the bar to be wider than one tab ($barWidth vs $tabWidth), " +
                "so there is material to float over",
        )

        // One point, and that number is measured rather than guessed. The segments come out
        // `[246, 246, 247, 247]` on a 986pt row: the host rounds layout to whole pixels and
        // 986/4 is 246.5, so two of the four round up and two round down. An exact assertion
        // would therefore be asserting a rounding accident; the point of this check is that the
        // spread is a *pixel*, not that it is zero — a bar that did not fill its width gave
        // `[135, 135, 135, 133]` on the same row, which is a 2pt spread and a lens that lands
        // on the wrong tab.
        val widths = tabs.map { (it.right - it.left).value.toDouble() }
        val spread = widths.max() - widths.min()
        assertTrue(
            spread <= 1.0,
            "expected the four tabs to split the bar evenly to within a pixel, got $widths",
        )

        // And the row has to actually span the bar, or "evenly" is four equal narrow columns
        // floating in the middle of a wide capsule. It starts one [ttSpacing.hairlineInset]
        // inside the glass on each side, which is the same inset [TTGlassSegmentedPicker] puts
        // inside its slab: the padding is content inset so the hairline keeps its distance from
        // the glyphs. Asserted against the token rather than a literal so the relationship is
        // what's pinned — measured, the row is at 19 and 1005 inside a bar at 16 and 1008.
        assertEquals(
            (bar.left + ttSpacing.hairlineInset).value.toDouble(),
            tabs.first().left.value.toDouble(),
            0.01,
            "expected the first tab to start one hairline inset inside the bar's left edge",
        )
        assertEquals(
            (bar.right - ttSpacing.hairlineInset).value.toDouble(),
            tabs.last().right.value.toDouble(),
            0.01,
            "expected the last tab to end one hairline inset inside the bar's right edge",
        )
    }

    /**
     * A sprint's row opens its own screen, and a link to one lands there too.
 *
     * ## Both paths, because they are different paths
     *
     * Tapping a row goes through [Router.push] on the Sprints tab's own stack; a link goes
     * through [Router.open], which installs a *path* and selects the tab. They share the
     * `PushedRoute` branch but not the route to it, and a deep link that landed on the tab
     * root instead of the sprint would look correct until someone actually followed one.
     *
     * The detail screen is also the first place [SprintDetail.canFinish] is exercised
     * through the shell: it reads the signed-in user's name out of [ShellPreferences], and
     * that name arriving late is what makes the model a `remember` key — so the Finish
     * button appearing a frame after the screen is the behaviour, not a flake.
     */
    val aSprintOpensAndALinkLandsOnIt: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
        )
        waitForIdle()
        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintListScreenTag).assertIsDisplayed()
        onNodeWithTag(SprintRowTag(sprint.id), useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag(SprintRowTag(sprint.id), useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(SprintDetailScreenTag).assertIsDisplayed()

        // And the push is recorded, so the detail screen is a *navigation* rather than the
        // list having quietly swapped its own content.
        assertEquals(
            listOf(AppRoute.SprintList(project.id), AppRoute.SprintDetail(project.id, sprint.id)),
            router.path(AppTab.Sprints),
        )
    }

    /**
     * System back pops the sprint detail, and stops at the tab root.
     *
     * The Android defect: on the sprint detail the system back button
     * closed the whole app, because nothing in the signed-in tree consumed
     * the back event — the only way off the screen was the tab bar. The
     * shell now answers back with the rule every pushed screen's chevron
     * already carries: pop exactly one route, never leave the tab.
     *
     * The press enters through [pressSystemBack], which is host-specific
     * for a measured reason: the JVM and iOS hosts' `BackHandler`
     * registers with the dispatcher the scenario provides (see
     * [setUpShell]'s `navigationEventOwner`), while the Android host's
     * registers with the activity's own dispatcher. The split and the
     * measurements behind it live on the [pressSystemBack] declaration.
     */
    val systemBackPopsTheSprintDetail: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)

        // The whole claim is about a delivered back press, so a host that cannot deliver one
        // skips rather than reaching the assertion with an untouched router.
        assumeHostDeliversSystemBack()

        val router = Router()
        val dispatcher = NavigationEventDispatcher()
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
        }
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            navigationEventOwner = owner,
        )
        waitForIdle()
        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintListScreenTag).assertIsDisplayed()
        onNodeWithTag(SprintRowTag(sprint.id), useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(SprintDetailScreenTag).assertIsDisplayed()
        assertEquals(
            listOf(AppRoute.SprintList(project.id), AppRoute.SprintDetail(project.id, sprint.id)),
            router.path(AppTab.Sprints),
        )

        // One system back pops the detail route, exactly as the chevron does.
        pressSystemBack(dispatcher)
        waitForIdle()
        assertEquals(
            listOf(AppRoute.SprintList(project.id)),
            router.path(AppTab.Sprints),
            "system back should pop the sprint detail, not close the app",
        )
        onNodeWithTag(SprintListScreenTag).assertIsDisplayed()

        // At the tab root the handler is disabled, so the OS default governs.
        // On the Android host that default is the activity's own back, which a
        // test cannot assert from here; what can be asserted is that the press
        // does not pop anything off the stack.
        pressSystemBack(dispatcher)
        waitForIdle()
        assertEquals(
            listOf(AppRoute.SprintList(project.id)),
            router.path(AppTab.Sprints),
            "system back at the tab root should be a no-op, not a pop",
        )
        onNodeWithTag(SprintListScreenTag).assertIsDisplayed()
    }

    /**
     * A link to one sprint selects the tab, installs the stack, and shows the detail screen.
     *
     * Distinct from [aSprintOpensAndALinkLandsOnIt] because it enters through
     * [Router.open] rather than through a tap, and the two differ in the way that matters
     * here: a link has to **select the active project**, or the tab has nothing to render.
     * That is `AppRoute.SprintDetail.selectsActiveProject`, and a test that only tapped a row
     * would never reach it.
     */
    val aDeepLinkToOneSprintLandsOnItsScreen: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)

        // The router is handed the link *before* the session resolves, which is the case
        // that matters: a link arriving while signed out has to be remembered and applied on
        // entry, and that path is `Router.open`'s rather than a tap's.
        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
        )
        waitForIdle()

        assertTrue(
            router.open("tasktracker://sprint/${project.id.stringValue}/${sprint.id.stringValue}"),
            "the link should be accepted",
        )
        waitForIdle()

        assertEquals(AppTab.Sprints, router.selectedTab)
        assertEquals(
            listOf(AppRoute.SprintList(project.id), AppRoute.SprintDetail(project.id, sprint.id)),
            router.path(AppTab.Sprints),
        )
        onNodeWithTag(SprintDetailScreenTag).assertIsDisplayed()
    }

    /**
     * The sprint detail's "+" opens the picker, and a ticked task joins the sprint.
     *
     * ## Why this is a shell scenario
     *
     * The model tests the gate, the toggle and the write; the domain tests the lock's
     * refusal. What neither can see is whether the button on the screen has a caller — the
     * detail could render a perfect "+" with `onClick = {}` and both suites would stay
     * green, which is the defect this file exists for. The destination's wiring is the
     * subject; the sheet is just where the tap has to land.
     *
     * The picker's *filter* is asserted here end to end as well: the task in this sprint,
     * the task in another planned sprint, the closed task and a finished free-agent task
     * are all in the store and none is offered. That rule is otherwise only a KDoc claim
     * on [com.tasktracker.featureprojectspresentation.SprintDetailModel.pickableItems] —
     * and the other-sprint half is the deliberate deviation from "not this sprint", where
     * lifting a task through this sheet would take it from under a window that has already
     * frozen its membership.
     */
    /** Deep enough that the picker's list overflows the sheet on every test host. */
    private const val PICKER_OVERFLOW_POOL = 25

    /** First fixture number of the overflow filler; 1–6 are the scenario's named items. */
    private const val PICKER_OVERFLOW_FIRST_NUMBER = 10

    val addingASelectedTaskToASprintFromTheShell: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)
        val otherSprint = shellTestSprint(
            projectID = project.id,
            name = "Sprint 8",
            id = SprintID("44444444-4444-4444-4444-444444444444"),
        )
        projects.seedSprint(otherSprint)
        val pickable = shellTestWorkItem(projectID = project.id, number = 1, title = "Write the migration")
        val inThisSprint = shellTestWorkItem(projectID = project.id, number = 2, title = "Already committed", sprintID = sprint.id)
        val elsewhere = shellTestWorkItem(projectID = project.id, number = 3, title = "Committed elsewhere", sprintID = otherSprint.id)
        val closed = shellTestWorkItem(projectID = project.id, number = 4, title = "Shipped already", status = WorkItemStatus.Closed)
        val finished = shellTestWorkItem(projectID = project.id, number = 5, title = "Delivered already", status = WorkItemStatus.Done)
        val unstarted = shellTestWorkItem(projectID = project.id, number = 6, title = "Untriaged", status = WorkItemStatus.Backlog)
        projects.seed(pickable)
        projects.seed(inThisSprint)
        projects.seed(elsewhere)
        projects.seed(closed)
        projects.seed(finished)
        projects.seed(unstarted)
        // A pool deep enough to overflow the sheet on any host. A four-row picker fits every
        // viewport by construction, so it can never say where the confirm row lands — which is
        // the defect a real backlog exposes: the list is unweighted, so it may claim the sheet's
        // whole height and push Cancel/Add below the bottom edge, where nothing can commit the
        // selection. Twenty-five rows overflow a ~700dp sheet budget several times over.
        val filler = List(PICKER_OVERFLOW_POOL) { index ->
            shellTestWorkItem(
                projectID = project.id,
                number = index + PICKER_OVERFLOW_FIRST_NUMBER,
                title = "Backfill task ${index + 1}",
            )
        }
        filler.forEach { projects.seed(it) }

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
        )
        waitForIdle()
        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        openSprintDetailRow(sprint)

        // The default fixture's window runs ahead of the shell's fixed clock (October
        // 2025), so this sprint is planned and `canEditItems` is true: the plus must be
        // enabled as well as present, since a present-but-dead button is half this
        // scenario's subject.
        onNodeWithTag(SprintDetailAddTag).assertIsDisplayed()
        onNodeWithTag(SprintDetailAddTag).assertIsEnabled()

        onNodeWithTag(SprintDetailAddTag).performClick()
        waitForIdle()
        onNodeWithTag(SprintPickerSheetTag).assertIsDisplayed()

        onNodeWithTag(SprintPickerRowTag(pickable)).assertIsDisplayed()
        onNodeWithTag(SprintPickerRowTag(unstarted)).assertIsDisplayed()
        onNodeWithTag(SprintPickerRowTag(inThisSprint)).assertDoesNotExist()
        onNodeWithTag(SprintPickerRowTag(elsewhere)).assertDoesNotExist()
        onNodeWithTag(SprintPickerRowTag(closed)).assertDoesNotExist()
        onNodeWithTag(SprintPickerRowTag(finished)).assertDoesNotExist()

        // Every offered row names the status its task will land with — the picker hides
        // finished tasks, but a tick is only informed if the row says whether the commit
        // is of a To Do or of an untriaged backlog task (which the sprint commit sweeps
        // onto the board as To Do). Read against the unmerged tree: the row's key, title
        // and status merge into one semantics node, so the status is only findable as its
        // own text before merge.
        onNode(
            hasTestTag(SprintPickerRowTag(pickable)) and hasAnyDescendant(hasText("To Do")),
            useUnmergedTree = true,
        ).assertExists()
        onNode(
            hasTestTag(SprintPickerRowTag(unstarted)) and hasAnyDescendant(hasText("Backlog")),
            useUnmergedTree = true,
        ).assertExists()

        // The confirm row has to render *with size, on the glass* — assertExists passes
        // for a node squeezed to 0x0 (and a click on it lands at the root origin, on the
        // scrim, dismissing the sheet without writing anything), and a row placed past
        // the sheet's bottom edge is the same dead end for the user: the selection can
        // never be committed. Checked before the first tap: the disabled Add must be
        // visible too, since "no button" and "greyed button" both read as "I cannot add".
        val addBounds = onNodeWithTag(SprintPickerAddTag).getBoundsInRoot()
        val sheetBounds = onNodeWithTag(SprintPickerSheetTag).getBoundsInRoot()
        assertTrue(
            addBounds.bottom > addBounds.top && addBounds.bottom <= sheetBounds.bottom,
            "expected the picker's Add button to render with size inside the sheet with " +
                "${filler.size + 2} candidates in the list, not collapse to nothing or " +
                "fall out of the glass: button=$addBounds sheet=$sheetBounds",
        )

        // Dimmed until something is ticked, then live: an Add that accepted an empty
        // selection would write nothing and close as though it had.
        onNodeWithTag(SprintPickerAddTag).assertIsNotEnabled()
        onNodeWithTag(SprintPickerRowTag(pickable)).performClick()
        waitForIdle()
        onNodeWithTag(SprintPickerRowTag(pickable)).assertIsSelected()
        onNodeWithTag(SprintPickerAddTag).assertIsEnabled()

        onNodeWithTag(SprintPickerAddTag).performClick()
        waitForIdle()

        assertTrue(
            onAllNodes(hasTestTag(SprintPickerSheetTag)).fetchSemanticsNodes().isEmpty(),
            "expected a successful add to close the picker, not leave it standing over the sprint",
        )
        onNodeWithTag(WorkItemBacklogRowTag(pickable)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(BacklogRemoveFromSprintTag(pickable)).performScrollTo().assertIsDisplayed()
        assertTrue(
            "setWorkItemSprint" in projects.writes,
            "expected the add to reach the store, saw ${projects.writes}",
        )
    }

    /**
     * Remove-from-sprint takes the task off the detail list and reaches the store.
     *
     * The counterpart to [addingASelectedTaskToASprintFromTheShell], and the one that
     * proves the *row's* verb has a caller: the button renders from
     * [com.tasktracker.featureprojectspresentation.WorkItemBacklogRow]'s sprint mode in
     * either case, and a destination that never passed `onRemoveTask` would leave it
     * drawing a verb that dismisses itself. Two independent signals, because row-absence
     * alone could pass for the wrong reason (a row scrolled out of a `LazyColumn` is
     * absent too): the store's `writes` and the empty list the reload leaves behind.
     */
    val removingATaskFromASprintFromTheShell: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)
        val committed = shellTestWorkItem(projectID = project.id, number = 1, title = "Committed task", sprintID = sprint.id)
        projects.seed(committed)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
        )
        waitForIdle()
        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        openSprintDetailRow(sprint)

        onNodeWithTag(WorkItemBacklogRowTag(committed)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(BacklogRemoveFromSprintTag(committed)).performScrollTo().assertIsDisplayed()

        onNodeWithTag(BacklogRemoveFromSprintTag(committed)).performClick()
        waitForIdle()

        assertTrue(
            onAllNodes(hasTestTag(WorkItemBacklogRowTag(committed))).fetchSemanticsNodes().isEmpty(),
            "expected the removed task to leave the sprint's list, not stand there after the write",
        )
        assertTrue(
            "setWorkItemSprint" in projects.writes,
            "expected remove-from-sprint to reach the store, saw ${projects.writes}",
        )
    }

    /**
     * An active or finished sprint shows its tasks frozen: no add, no remove.
     *
     * ## Why both states in one scenario
     *
     * They are one gate in the code — [com.tasktracker.featureprojectspresentation.SprintDetail.canEditItems]
     * is `!isFinished && !isActive` — and asserting only the active half would let a
     * reordering that locks one and forgets the other through. The finished sprint is
     * reached by deep link rather than by navigating back, because the detail screen has no
     * back control of its own: system back is [systemBackPopsTheSprintDetail]'s territory,
     * and re-driving the list would assert navigation again instead of the lock.
     *
     * "Disabled, not absent" is what is asserted, matching the screen's own contract: the
     * plus is dimmed and dead while the explanation (the window) sits on the page, and the
     * rows render without their verbs rather than not at all.
     */
    val aLockedSprintOffersNoTaskEdits: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        // The shell's clock is `TestDateProvider()` — October 2025 — so a window around
        // that instant is an *active* sprint, while the fixture's default March-2026
        // window is a planned one. Nothing here depends on the day the suite runs.
        val active = shellTestSprint(
            isLocked = true,
            projectID = project.id,
            name = "Sprint under way",
            startDate = Instant.parse("2025-09-01T00:00:00Z"),
            endDate = Instant.parse("2025-11-30T00:00:00Z"),
        )
        projects.seedSprint(active)
        val finished = shellTestSprint(
            projectID = project.id,
            name = "Sprint done",
            id = SprintID("44444444-4444-4444-4444-444444444444"),
            isFinished = true,
        )
        projects.seedSprint(finished)
        val frozen = shellTestWorkItem(projectID = project.id, number = 1, title = "Frozen task", sprintID = active.id)
        val done = shellTestWorkItem(projectID = project.id, number = 2, title = "Finished task", sprintID = finished.id)
        projects.seed(frozen)
        projects.seed(done)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
        )
        waitForIdle()
        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        openSprintDetailRow(active)

        onNodeWithTag(SprintDetailAddTag).assertIsDisplayed()
        onNodeWithTag(SprintDetailAddTag).assertIsNotEnabled()
        onNodeWithTag(WorkItemBacklogRowTag(frozen)).performScrollTo().assertIsDisplayed()
        // Asserted *after* the row is scrolled into view, so the verb's absence is the
        // row rendering without it rather than the row itself not being composed.
        onNodeWithTag(BacklogRemoveFromSprintTag(frozen)).assertDoesNotExist()

        assertTrue(
            router.open("tasktracker://sprint/${project.id.stringValue}/${finished.id.stringValue}"),
            "the link to the finished sprint should be accepted",
        )
        waitForIdle()
        onNodeWithTag(SprintDetailScreenTag).assertIsDisplayed()
        // The detail list keeps its scroll position across the route change (same screen, new
        // sprint), and the assertion above scrolled down to the active sprint's task row. Back
        // to the top so the header is composed again before reading the finished sprint's name.
        onNodeWithTag(SprintDetailScreenTag).performScrollToIndex(0)
        waitForIdle()
        // The finished sprint's own name, which proves the link replaced the detail rather than
        // leaving the active sprint under a matching tag. Reopen is not asserted here: it lives
        // on the summary screen (`SprintSummaryScreen`), not on the detail, and the detail's
        // archived arm deliberately offers no action.
        onNodeWithText(finished.name).assertIsDisplayed()

        onNodeWithTag(SprintDetailAddTag).assertIsNotEnabled()
        onNodeWithTag(WorkItemBacklogRowTag(done)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(BacklogRemoveFromSprintTag(done)).assertDoesNotExist()
    }

    /**
     * Tapping a task on the sprint detail pushes its editor — the row's tap has a caller.
     *
     * The row body is a *navigation* target, distinct from the remove verb beside it: the
     * backlog and board rows push this same route, and a sprint row wired to nothing looks
     * exactly like one wired right until a tap goes nowhere. The route is asserted, not
     * only the editor's presence, because the editor is hosted by the shell and would
     * answer a push from any tab — the path says *this* row did it, from *here*.
     */
    val tappingASprintTaskRowOpensTheEditor: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)
        val committed = shellTestWorkItem(projectID = project.id, number = 1, title = "Committed task", sprintID = sprint.id)
        projects.seed(committed)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
        )
        waitForIdle()
        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        openSprintDetailRow(sprint)

        onNodeWithTag(WorkItemBacklogRowTag(committed)).performScrollTo()
        // The count of detail-screen reads earned before the tap: opening and closing the
        // editor must not grow it. A fresh `SprintDetailModel` re-reads `sprintWorkItems`
        // when it observes, so a move here means the destination was torn down and rebuilt
        // — the reload flash the user sees behind the sheet.
        val readsBeforeOpen = projects.sprintWorkItemReads.size
        // The node's own OnClick action, not a tap at its centre: this asserts the *wiring*
        // — that the row carries a handler that pushes the editor — and where the centre
        // lands is geometry. On the Android host's smaller viewport the centre falls where
        // a touch does not reach the card, and a coordinate click then fails for a layout
        // reason while the wiring is fine. `performSemanticsAction` throws if the action is
        // absent, so a row wired to nothing still fails this line (mutation-checked).
        onNodeWithTag(WorkItemBacklogRowTag(committed))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        assertEquals(
            listOf(
                AppRoute.SprintList(project.id),
                AppRoute.SprintDetail(project.id, sprint.id),
                AppRoute.WorkItemEditor(projectID = project.id, workItemID = committed.id),
            ),
            router.path(AppTab.Sprints),
            "expected the row's tap to push the editor onto the sprint's own stack",
        )
        onNodeWithTag(WorkItemEditorDestinationTag).assertExists()

        // The path above proves the row pushed from *here*; this is what the shell drew
        // behind the editor. The route-pushing wiring was already correct when the editor
        // opened over the *board* from a sprint: the background `when` in `PushedRoute`
        // lumped SprintDetail into its Board default, so the sheet sat on a board the user
        // had never opened and the pop then "returned" to a sprint they never saw leave.
        // With the editor now full-screen, the sprint detail remains composed underneath
        // but is fully covered by the editor and not in the semantics tree while the editor
        // is open. It becomes visible again once the editor is dismissed.
        assertTrue(
            onAllNodes(hasTestTag(ProjectBoardScreenTag)).fetchSemanticsNodes().isEmpty(),
            "expected the sprint detail, not the board, behind the editor opened from a " +
                "sprint task row",
        )

        // Cancel has to land back on that same sprint detail — closing the editor the user
        // opened over it, not on the board the background used to fall back to.
        // The compact editor keeps the route mounted for EDITOR_EXIT_MS so the slide can
        // finish. Drive the clock past that hold, and use the row's own action rather than a
        // coordinate tap so the assertion is about dismissal rather than about geometry.
        onNodeWithTag(WorkItemCancelTag).performSemanticsAction(SemanticsActions.OnClick)
        mainClock.advanceTimeBy(EDITOR_EXIT_SLACK_MS)
        waitForIdle()
        assertTrue(
            onAllNodes(hasTestTag(WorkItemEditorDestinationTag)).fetchSemanticsNodes().isEmpty(),
            "expected Cancel to close the editor rather than leave it standing over " +
                "the sprint",
        )
        // After the editor is dismissed, the sprint detail should be visible again.
        onNodeWithTag(SprintDetailScreenTag).assertIsDisplayed()
        onNodeWithTag(WorkItemBacklogRowTag(committed)).performScrollTo().assertIsDisplayed()
        assertEquals(
            readsBeforeOpen,
            projects.sprintWorkItemReads.size,
            "expected the sprint detail to survive opening and closing the editor, but it " +
                "re-read its work items (${projects.sprintWorkItemReads.drop(readsBeforeOpen)})",
        )
    }

    /**
     * The Sprints tab open and this sprint's row tapped — the detail screen in hand.
     *
     * The three steps every detail scenario starts with, spelled once: the tab click, the
     * list assertion (the row is tapped *there*, so a detail assertion that ran against a
     * still-visible list would be reading the wrong screen), and the row tap itself.
     */
    private suspend fun ComposeUiTest.openSprintDetailRow(sprint: Sprint) {
        onNodeWithTag(TabButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintListScreenTag).assertIsDisplayed()
        onNodeWithTag(SprintRowTag(sprint.id), useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(SprintDetailScreenTag).assertIsDisplayed()
    }

    /**
     * Five tabs fit at phone width, and every one of them keeps its whole label.
     *
     * ## Why this is a *fixed* expectation and not whatever the host produced
     *
     * The Android host's viewport is documented as smaller than the JVM's and the iOS
     * simulator's, so an assertion written against "the width the host happened to give" is
     * an assertion about the host. 23.6.6 asks for a fixed expectation, and these are what
     * it means:
     *
     * - **five** tabs, written as a literal rather than as [AppTab.entries].size — a sixth
     *   tab is a design decision and has to change this number on purpose;
     * - **390dp**, the narrowest phone this app ships to, pinned as a literal rather than
     *   taken from whatever device the build is on;
     * - **44dp**, the minimum hit target both platforms' accessibility guidance names,
     *   read from [ttSpacing.iconButtonSize] so the assertion tracks the token rather than
     *   a number that can drift away from it.
     *
     * ## The shell is constrained, not the window resized
     *
     * [setUpShell] takes a width and lays the app out inside a [Box] of it, because the app
     * fills its parent. The alternatives were both rejected on purpose:
     * `DensityForcedSize` is `internal` to Compose, and overriding
     * [androidx.compose.ui.platform.LocalDensity] is the mistake `GlassSurfacePixelTest` is
     * the only place in this repo that gets right — a density override leaves the root at
     * the host's density and everything below it at the forced one, so `getBoundsInRoot`
     * reports two coordinate systems on one frame and every comparison drawn from it is
     * wrong.
     *
     * ## Why the labels are what is worth asserting
     *
     * The segments splitting evenly is already [theTabBarIsWiderThanItsTabs]. What is new
     * with a fifth tab is that every segment is **narrower**, and the caption is the only
     * thing in a segment with an intrinsic width. It is `maxLines = 1` with **no**
     * `overflow`, so a caption that does not fit is *clipped* — not ellipsised — and a
     * clipped caption still renders as text, still measures as non-empty, and is exactly
     * the kind of fault that survives a screenshot review.
     *
     * So the check is each caption's laid-out width against the box it was given, with the
     * numbers dumped when it fails. A caption *narrower* than its box is fitting; one
     * exactly as wide has been cut.
     */
    val fiveTabsFitAtPhoneWidth: suspend ComposeUiTest.() -> Unit = {
        setUpShell(session = ScriptedSession(restored = signedIn()), width = PHONE_WIDTH)
        waitForIdle()

        assertEquals(
            PHONE_TAB_COUNT,
            AppTab.entries.size,
            "the tab count is a fixed expectation rather than whatever the enum currently says — " +
                "a sixth tab is a design decision and has to change this number on purpose",
        )

        val bar = onNodeWithTag(TabBarTag).getBoundsInRoot()
        val barWidthPx = bar.widthPx()
        val captions = AppTab.entries.associateWith { captionInside(it) }
        val segments = AppTab.entries.associateWith {
            onNodeWithTag(TabButtonTag(it)).getBoundsInRoot().widthPx()
        }

        // The numbers below, measured on the JVM host at 390dp and density 1, with the whole
        // dump printed so a failure can be compared against them rather than guessed at:
        //
        //   bar            x 16.0..374.0 dp, 358px wide  (= 390 − 2 × tabBarEdgeInset 16)
        //   segments       71, 71, 70, 70, 70 px       (= (358 − 2 × hairlineInset 3) / 5 = 70.4)
        //   captions       projects 47, board 34, backlog 46, sprints 41, settings 47 px
        //   caption boxes  71, 71, 70, 70, 70 px
        //
        // The segment widths are the point of 23.6.6: five tabs is the first count this bar
        // has been laid out at, and 70.4px is comfortably above the 44px minimum — the
        // finding is that it fits, not that it is tight.
        val measured = buildString {
            append("\n  bar            x ${bar.left.value}..${bar.right.value} dp, ${barWidthPx}px")
            append("\n  segments       ${segments.values.joinToString(", ")} px")
            append("\n  captions       ${captions.values.joinToString(", ") { "${it.drawnWidth}" }} px")
            append("\n  caption boxes  ${captions.values.joinToString(", ") { "${it.boxWidth}" }} px")
        }

        // The even split, restated for five. The lens travels `selectedIndex * segmentWidth`,
        // so an uneven split puts the highlight on the wrong tab — which is the failure this
        // is looking for, and the reason the check is not merely "it fits".
        val segmentWidths = segments.values.toList()
        val spread = segmentWidths.max() - segmentWidths.min()
        assertTrue(
            spread <= 1.0,
            "expected the five tabs to split a ${barWidthPx}px bar evenly to within a pixel, " +
                "got $segmentWidths (spread ${spread}px)$measured. The lens's travel is " +
                "`selectedIndex * segmentWidth`, so an uneven split puts the highlight on the " +
                "wrong tab.",
        )

        // The fit itself, derived from tokens rather than read from this host. The bar insets
        // itself by `tabBarEdgeInset` on each side and its row starts one `hairlineInset`
        // inside the glass on each side, so this is the widest a segment could possibly be.
        val insets = ttSpacing.tabBarEdgeInset * 2f + ttSpacing.hairlineInset * 2f
        val widestPossibleSegment = (PHONE_WIDTH - insets) / PHONE_TAB_COUNT.toFloat()
        assertTrue(
            widestPossibleSegment >= ttSpacing.iconButtonSize,
            "at ${PHONE_WIDTH.value}dp a $PHONE_TAB_COUNT-tab bar gives each tab at most " +
                "${widestPossibleSegment.value}dp, below the ${ttSpacing.iconButtonSize.value}dp " +
                "minimum hit target$measured",
        )

        // And the labels, which are what actually break first. The caption is `maxLines = 1`
        // with **no** `overflow`, so a label that does not fit is *clipped* — cut mid-glyph,
        // not ellipsised — and a clipped caption still renders as text and still measures as
        // non-empty, so nothing else would catch it.
        //
        // The worst case measured is 47px of caption in a 70px box, so this clears by 23px on
        // the JVM. That is not a wide margin for a word like "Backlog" in a larger language,
        // which is why the failure message carries every caption rather than just the one that
        // broke.
        for ((tab, caption) in captions) {
            assertTrue(
                caption.drawnWidth <= caption.boxWidth,
                "the ${tab.wireValue} caption laid out ${caption.drawnWidth}px wide inside a " +
                    "${caption.boxWidth}px segment, which is what a clipped label looks like — " +
                    "the tab bar's caption is `maxLines = 1` with no overflow, so a label that " +
                    "does not fit is cut mid-glyph rather than ellipsised. Every caption: " +
                    "${captions.entries.joinToString { "${it.key.wireValue}=${it.value}" }}$measured",
            )
        }
    }

    /** A tab's caption: how wide the text laid out, and how wide the box it was given. */
    private data class Caption(val drawnWidth: Double, val boxWidth: Double) {
        override fun toString(): String = "${drawnWidth}px in ${boxWidth}px"
    }

    /**
     * The caption node inside a tab.
     *
     * A tab is a `clickable`, so its subtree is merged and this node exists **only in the
     * unmerged tree** — the same trap `TestQueries.onNested` documents for the board's cards,
     * and the reason this reaches for `useUnmergedTree` rather than for a tag.
     */
    private fun ComposeUiTest.captionInside(tab: AppTab): Caption {
        val node = onNodeWithTag(TabButtonTag(tab), useUnmergedTree = true)
            .fetchSemanticsNode()
        val text = node.children
            .first { it.config.getOrElseNullable(SemanticsProperties.Text) { null } != null }
        return Caption(
            drawnWidth = text.boundsInRoot.widthPx(),
            boxWidth = node.boundsInRoot.widthPx(),
        )
    }

    /**
     * [androidx.compose.ui.test.getBoundsInRoot] answers in dp, so this file converts once
     * rather than comparing a dp-derived width against a pixel-derived one.
     */
    private fun DpRect.widthPx(): Double = (right.value - left.value).toDouble()

    /** [androidx.compose.ui.semantics.SemanticsNode.boundsInRoot] answers in pixels already. */
    private fun Rect.widthPx(): Double = (right - left).toDouble()

    /** The narrowest phone this app ships to, as a fixed number rather than a device's. */
    private val PHONE_WIDTH = 390.dp

    /** Five, written out so a sixth tab has to change this on purpose. */
    private const val PHONE_TAB_COUNT = 5

    /**
     * A desktop window, as a fixed number rather than a device's.
     *
     * 1000dp rather than something just over [ShellExpandedMinimumWidth], for the same reason
     * [PHONE_WIDTH] is 390 and not 391: a width written against the threshold would keep passing
     * if the threshold moved, which is the one change these scenarios must not absorb silently.
     *
     * It is 1000 rather than 1200 because **a scenario can only test a width its host can
     * actually express**. A fixed width is clamped by its parent's constraints and the JVM host's
     * viewport is 1024dp, so asking it for 1200 produced a shell narrower than requested — either
     * a skip, or worse, a scenario asserting against a window it did not get. 1000dp is
     * comfortably on the far side of the threshold and inside what the JVM, the iOS simulator and
     * a real desktop window can all lay out. The Android host's viewport is far narrower and
     * skips, which is correct rather than a gap: a desktop window is not a configuration it has.
     */
    private val DESKTOP_WIDTH = 1000.dp

    /**
     * The width at which the sidebar is drawn.
     *
     * 1280dp is the width the sidebar exists for — it is the Desktop app's default window and
     * the first width past [ShellSidebarMinimumWidth]. Written as a fixed number rather than
     * against the threshold so a threshold change does not silently move the scenario.
     */
    private val SIDEBAR_WIDTH = 1280.dp

    /**
     * The density a sidebar scenario composes at, so 1280dp fits inside the 1024px display.
     *
     * The sidebar class starts at [ShellSidebarMinimumWidth] (1120dp) and the test display is
     * 1024x768 *pixels*, so at density 1 the shell is clamped to 1024dp and every sidebar
     * scenario fails on a node that was never composed. 0.75 turns the display into
     * 1365x1024dp without changing any layout, because every length in this app is in dp and
     * every assertion here is read back in dp.
     */
    private val SIDEBAR_DENSITY = Density(0.75f)

    /**
     * A phone draws the bar and no rail.
     *
     * The compact path is the one that had to stay unchanged, so it is asserted directly rather
     * than inferred from the expanded scenarios. [TabBarTag] absent under a wide window and
     * present under a narrow one is the same fact read from both sides.
     */
    val aNarrowShellDrawsTheBarAndNoRail: suspend ComposeUiTest.() -> Unit = {
        setUpShell(session = ScriptedSession(restored = signedIn()), width = PHONE_WIDTH)
        waitForIdle()

        onNodeWithTag(TabBarTag).assertExists(
            "at ${PHONE_WIDTH.value}dp the shell should still be drawing the tab bar",
        )
        assertNothingTagged(
            NavigationRailTag,
            "at ${PHONE_WIDTH.value}dp the shell drew a navigation rail as well as the bar, so " +
                "the same destinations are reachable twice and the compact path has changed",
        )
    }

    /**
     * A wide window draws the rail and no bar.
     *
     * "No bar" is the half worth reading. A rail drawn *beside* a still-present bar is the obvious
     * wrong version of this feature and it looks plausible in a screenshot: the app would have
     * eight destinations, and the bar would be the five-segment capsule the section exists to
     * replace.
     */
    val aWideShellDrawsTheRailAndNoBar: suspend ComposeUiTest.() -> Unit = {
        setUpShell(session = ScriptedSession(restored = signedIn()), width = DESKTOP_WIDTH)
        waitForIdle()

        if (desktopWidthIsExpressible(DESKTOP_WIDTH)) {
            onNodeWithTag(NavigationRailTag).assertExists(
                "at ${DESKTOP_WIDTH.value}dp, which is past the " +
                    "${ShellExpandedMinimumWidth.value}dp threshold, the shell should be drawing " +
                    "a navigation rail",
            )
            assertNothingTagged(
                TabBarTag,
                "at ${DESKTOP_WIDTH.value}dp the shell drew the bottom tab bar as well as the " +
                    "rail. The bar is the shape this section exists to replace: five segments of " +
                    "${DESKTOP_WIDTH.value / 5}dp each, with the tabs marooned inside them",
            )
        }
    }

    /**
     * The rail is a leading column that wraps its destinations, not a stretched or bottom-anchored
     * one.
     *
     * Three separate assertions because the failure has three shapes, and each is the transpose
     * of a defect [TTGlassTabBar] already records for the other axis. A rail that filled the
     * window's height would give five destinations a fifth of a tall column each (the "768pt-tall
     * wrapper with the tabs parked dead centre" in the bar's KDoc, rotated). A rail that spanned
     * the window's width would be the bar again. A rail anchored to the bottom edge would be a
     * bar stood on end.
     *
     * The leading-edge inset is read from [ttSpacing.tabBarEdgeInset] rather than written as a
     * number, because the shell owns that inset and a copy of it in this file is a second thing
     * to keep true — the same reasoning the tab bar's own KDoc gives for not restating the
     * edge inset it does not own.
     */
    val theRailIsALeadingColumnRatherThanAStretchedOne: suspend ComposeUiTest.() -> Unit = rail@{
        setUpShell(session = ScriptedSession(restored = signedIn()), width = DESKTOP_WIDTH)
        waitForIdle()

        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@rail

        val rail = onNodeWithTag(NavigationRailTag).getBoundsInRoot()
        val shell = onNodeWithTag(SignedInShellTag).getBoundsInRoot()
        // Subtracted rather than read off `DpRect.height`, which this file does not use anywhere:
        // both existing bounds helpers here compute a dimension from its two edges, and a third
        // spelling would be a second thing to keep true.
        val railHeight = rail.bottom.value - rail.top.value
        val shellHeight = shell.bottom.value - shell.top.value

        assertEquals(
            ttSpacing.tabBarEdgeInset.value,
            rail.left.value - shell.left.value,
            "the rail starts ${rail.left.value - shell.left.value}dp inside a shell whose own " +
                "leading edge sits at ${shell.left.value}dp, and it should start at the shell's " +
                "edge inset of ${ttSpacing.tabBarEdgeInset.value}dp with nothing between them. " +
                "Measured against the shell rather than an absolute zero, because where the shell " +
                "sits in the window is the host's business and not this scenario's",
        )
        assertTrue(
            railHeight > 0f && railHeight < shellHeight,
            "the rail measured ${railHeight}dp in a ${shellHeight}dp shell, so it is either empty " +
                "or filling the window",
        )

        // It is a full-height leading column, anchored to the top.
        //
        // This used to assert the opposite — a rail *shorter* than the window and centred in it —
        // on the argument that stretching five destinations over a tall window spaces them too far
        // apart. Measured, that rail was 216dp of a 768dp window: a glass capsule a quarter of the
        // window's height, floating in the middle of the left edge, which reads as a floating
        // capsule rather than as navigation. A desktop sidebar runs the full height from the top,
        // so the assertion is that now, and the trailing space belongs below the destinations.
        assertEquals(
            ttSpacing.tabBarEdgeInset.value,
            rail.top.value - shell.top.value,
            "the rail starts ${rail.top.value - shell.top.value}dp below the shell's top edge, and " +
                "it should start at the shell's own edge inset of " +
                "${ttSpacing.tabBarEdgeInset.value}dp. It runs from the top rather than floating " +
                "in the middle — a desktop sidebar begins at the top — but it still floats by " +
                "that inset, the same rule the tab bar follows on its own axis",
        )
        assertTrue(
            rail.right.value < shell.right.value,
            "the rail runs to ${rail.right.value}dp in a shell that ends at ${shell.right.value}dp, " +
                "so it is spanning the window rather than sitting on its leading edge",
        )

        // It fills the window's height less that inset at each end, rather than being a short
        // capsule floating in the middle of the left edge.
        //
        // This used to assert the opposite — a rail *shorter* than the window and centred in it —
        // on the argument that stretching five destinations over a tall window spaces them too far
        // apart. Measured, that rail was 216dp of a 768dp window, which reads as a floating
        // capsule rather than as navigation.
        assertEquals(
            0f,
            rail.bottom.value - (shell.bottom.value - ttSpacing.tabBarEdgeInset.value),
            "the rail stops ${rail.bottom.value - shell.bottom.value}dp from the shell's bottom " +
                "edge and should stop at ${-ttSpacing.tabBarEdgeInset.value}dp, mirroring the " +
                "inset it starts with. Running full-bleed to the bottom would be a slab rather " +
                "than a floating surface",
        )

        // Its destinations share the height equally, which is what the lens's segment is derived
        // from: `maxHeight / destinations.size`. Cells that did not divide it evenly would put the
        // lens in the wrong place on a tall window, and this is the composed half of a rule the
        // pure geometry test cannot see — that test supplies the height, so it cannot tell whether
        // the component supplies the right one.
        //
        // The band's leading half-point is the rail's own vertical glass padding, which is inside
        // the tagged box but outside the cells.
        val firstCell = onNodeWithTag(
            NavigationRailButtonTag(AppTab.Projects),
            useUnmergedTree = true,
        ).getBoundsInRoot()
        val cellHeight = firstCell.bottom.value - firstCell.top.value
        assertEquals(
            cellHeight * PHONE_TAB_COUNT,
            railHeight - ttSpacing.hairlineInset.value * 2,
            cellHeight * QUARTER,
            "the rail's ${railHeight}dp should be $PHONE_TAB_COUNT destinations of $cellHeight dp " +
                "plus ${ttSpacing.hairlineInset.value * 2}dp of its own vertical padding. Cells " +
                "that do not share the height equally put the lens's segment — the rail's height " +
                "divided by the destination count — in the wrong place",
        )

        // The lens marks its cell and never overruns it.
        //
        // This is the assertion that would have caught the overhang. A rail segment is a fifth of
        // the rail's height, so at 736dp the cells are 146dp and the lens's 10% stretch takes it
        // from 140dp to 154dp. Its *top* edge still landed on the right destination, which is the
        // edge a bounds check is most likely to look at, and its bottom edge crossed into the next
        // cell — a selection indicator visibly bigger than the thing it indicated. The lens is
        // clamped to its segment for that reason, and this is what holds the clamp.
        val lens = onNodeWithTag(NavigationRailLensTag).getBoundsInRoot()
        val landingCell = onNodeWithTag(
            NavigationRailButtonTag(AppTab.Projects),
            useUnmergedTree = true,
        ).getBoundsInRoot()
        val lensHeightMeasured = lens.bottom.value - lens.top.value
        val cellHeightMeasured = landingCell.bottom.value - landingCell.top.value
        assertTrue(
            lensHeightMeasured <= cellHeightMeasured,
            "the lens is ${lensHeightMeasured}dp tall over a ${cellHeightMeasured}dp cell, so it " +
                "is taller than the destination it is marking and crosses into the next one. Its " +
                "top edge sits at ${lens.top.value} against the cell's ${landingCell.top.value}, " +
                "which is why this reads as correct until you look at the bottom edge",
        )
        assertTrue(
            lens.top.value >= landingCell.top.value - ONE_POINT &&
                lens.bottom.value <= landingCell.bottom.value + ONE_POINT,
            "the lens occupies ${lens.top.value}..${lens.bottom.value}dp and its cell " +
                "${landingCell.top.value}..${landingCell.bottom.value}dp, so it is not contained " +
                "by the destination it is marking",
        )

        // Every glyph on one axis. The cells are as wide as their own captions and the captions
        // differ — "Board" is far narrower than "Projects" — so this is the property that says
        // they are *centred* on the column's centre line rather than each hugging the leading
        // edge, which drew "Board" visibly outboard of "Projects".
        //
        // Measured spread across the five destinations is **0.5dp**, which is the caption widths'
        // own sub-pixel rounding rather than a real offset. Before the column was centred the same
        // measurement spread the destinations across the rail's full width — tens of dp, because
        // each glyph sat at its own caption's position instead of on a shared axis.
        val centres = AppTab.entries.map { tab ->
            onNodeWithTag(NavigationRailButtonTag(tab), useUnmergedTree = true)
                .getBoundsInRoot()
                .let { (it.left.value + it.right.value) / 2f }
        }
        val spread = centres.max() - centres.min()
        assertTrue(
            spread <= ONE_POINT,
            "the destinations' centre lines run ${centres.min()}..${centres.max()} — a spread of " +
                "${spread}dp: ${centres.joinToString { "${it}" }}. Every glyph and caption is " +
                "centred on the column's centre line, so a caption wider than its neighbours " +
                "must not drag its glyph outboard of theirs. A point is sub-pixel rounding; the " +
                "defect this guards was tens of points wide",
        )
        assertTrue(
            railHeight > 0f && railHeight < shellHeight,
            "the rail measured ${railHeight}dp in a ${shellHeight}dp shell, so it is either empty " +
                "or filling the window outright. It should fill the window less one edge inset " +
                "at each end",
        )

        // It carries every destination, not only the selected ones. `selectedRailDestinations`
        // filters on `selected`, so a cell that stopped composing would leave that list reading
        // `listOf(AppTab.Projects)` and the selection scenario would pass over a rail with one
        // destination on it. The bar has `fiveTabsFitAtPhoneWidth` for this; the rail needed the
        // equivalent and did not have one.
        assertEquals(
            PHONE_TAB_COUNT,
            AppTab.entries.count {
                onAllNodesWithTag(NavigationRailButtonTag(it)).fetchSemanticsNodes().isNotEmpty()
            },
            "the rail should carry all $PHONE_TAB_COUNT destinations, one " +
                "NavigationRailButtonTag each. A cell that stopped composing is invisible to the " +
                "selection scenarios, which only ever look at the selected one",
        )

        // And the content starts *beside* it rather than running underneath. Read on a real
        // screen rather than on the content box, because the box that carries the padding is
        // anonymous: a node inside it is the only thing that can show whether the padding reached
        // the pixels.
        //
        // The projects list is used because it fills the content area edge to edge on the landing
        // tab. A screen whose content sits well inside its own margins — a centred card, a row
        // with its own gutter — would keep passing with the padding removed, because its left edge
        // would clear the rail's for reasons that have nothing to do with the inset. That is not a
        // hypothetical: the board's columns cannot be used at all here, because the landing tab
        // has no open project and draws none.
        val list = onNodeWithTag(ProjectsListScreenTag).getBoundsInRoot()
        assertTrue(
            list.left.value >= rail.right.value,
            "the projects list starts at ${list.left.value}dp while the rail runs to " +
                "${rail.right.value}dp, so the content is running *under* the rail. The rail's " +
                "width is measured and fed back as the content box's leading inset; if that " +
                "measurement is not reaching the padding, every screen is drawn under the rail",
        )
    }

    /**
     * Shrinking the window past the threshold sheds the rail's inset.
     *
     * This is a *resize* defect, and neither static width can show it. `railInset` is written by the
     * rail's own `onSizeChanged`, and a rail that has left the composition is never remeasured, so
     * nothing zeroes the measurement. A shell that starts at [PHONE_WIDTH] never runs that arm and
     * reads `0.dp`; a shell that starts at [DESKTOP_WIDTH] reads the rail's true width. Only the
     * crossing between the two leaves the old width behind as a band of blank canvas down the
     * leading edge, which is why this stands one shell up wide, shrinks it, and asserts on the same
     * composition rather than asserting the two widths in isolation.
     *
     * The measurement is the projects list's leading edge, for the reason
     * [theRailIsALeadingColumnRatherThanAStretchedOne] gives: the box carrying the inset is
     * anonymous, so a node inside it is the only thing that can show whether the inset reached the
     * pixels. Under `Compact` that edge should sit on the shell's own leading edge.
     */
    val shrinkingTheWindowPastTheThresholdShedsTheRailInset: suspend ComposeUiTest.() -> Unit = shrink@{
        val width = mutableStateOf(DESKTOP_WIDTH)
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            widthState = width,
        )
        waitForIdle()

        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@shrink

        // Asserted before the shrink, or this could pass on a shell that never drew the rail and
        // therefore never had an inset to shed.
        onNodeWithTag(NavigationRailTag).assertExists(
            "at ${DESKTOP_WIDTH.value}dp the shell should draw a navigation rail before it is " +
                "shrunk, or there is no stale inset for this scenario to catch",
        )

        runOnIdle { width.value = PHONE_WIDTH }
        waitForIdle()

        onNodeWithTag(TabBarTag).assertExists(
            "at ${PHONE_WIDTH.value}dp the shell should be drawing the tab bar",
        )
        assertNothingTagged(
            NavigationRailTag,
            "at ${PHONE_WIDTH.value}dp the shell is still drawing the rail it had at " +
                "${DESKTOP_WIDTH.value}dp",
        )

        val shell = onNodeWithTag(SignedInShellTag).getBoundsInRoot()
        val list = onNodeWithTag(ProjectsListScreenTag).getBoundsInRoot()
        assertEquals(
            shell.left.value,
            list.left.value,
            ONE_POINT,
            "after shrinking to ${PHONE_WIDTH.value}dp the projects list starts at " +
                "${list.left.value}dp while the shell starts at ${shell.left.value}dp — a " +
                "${list.left.value - shell.left.value}dp gap down the leading edge. The rail's " +
                "inset was measured at ${DESKTOP_WIDTH.value}dp and is still being applied after " +
                "the rail is gone, because a rail leaves the composition without being remeasured " +
                "to zero",
        )
    }

    /**
     * Growing the window past the threshold drops the bar's inset with the bar.
     *
     * The mirror of [shrinkingTheWindowPastTheThresholdShedsTheRailInset], with the same mechanism:
     * `tabBarInset` is written by the bar's own `onSizeChanged`, the bar leaves the composition
     * without being remeasured, and `LocalTabBarScrollUnderInset` went on publishing its height —
     * so every scrolling list kept reserving a bar's worth of room at the end of its scroll for
     * chrome that is not drawn. Neither static width can show it: a shell that starts narrow reads
     * the bar's true height, and a shell that starts wide never runs that arm.
     *
     * The projects list is the probe because it is the landing tab, so it is on screen without
     * opening a project. Its bottom `contentPadding` is `ttSpacing.xl +` the published inset, and at
     * the end of the scroll the gap between its last row and its viewport floor *is* that padding —
     * read off the pixels rather than through a probe composable that would have to be trusted
     * separately.
     */
    val growingTheWindowPastTheThresholdDropsTheBarInset: suspend ComposeUiTest.() -> Unit = grow@{
        val repository = FakeProjectRepository()
        val seeded = seedOverflowingLists(repository)
        val width = mutableStateOf(PHONE_WIDTH)
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = repository,
            widthState = width,
        )
        waitForIdle()

        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@grow

        // Narrow: the bar is up, so there is an inset for the grown shell to shed. Its height is
        // read rather than assumed, because it is what the failure message prices the leak in.
        val bar = onNodeWithTag(TabBarTag).getBoundsInRoot()
        val barHeight = bar.bottom.value - bar.top.value
        assertTrue(
            barHeight > 0f,
            "at ${PHONE_WIDTH.value}dp the shell should draw a tab bar with a height, or there is " +
                "no inset for this scenario to catch going stale",
        )

        runOnIdle { width.value = DESKTOP_WIDTH }
        waitForIdle()

        onNodeWithTag(NavigationRailTag).assertExists(
            "at ${DESKTOP_WIDTH.value}dp the shell should be drawing a navigation rail",
        )
        assertNothingTagged(
            TabBarTag,
            "at ${DESKTOP_WIDTH.value}dp the shell is still drawing the tab bar it had at " +
                "${PHONE_WIDTH.value}dp",
        )

        onNodeWithTag(ProjectsListTag).performScrollToIndex(seeded.projects.lastIndex)
        waitForIdle()
        val gap = projectsListBottomGap()
        assertEquals(
            ttSpacing.xl.value,
            gap,
            ONE_POINT,
            "after growing to ${DESKTOP_WIDTH.value}dp the projects list reserves ${gap}dp below " +
                "its last row, where its own bottom padding is ${ttSpacing.xl.value}dp — " +
                "${gap - ttSpacing.xl.value}dp of the bar's ${barHeight}dp height is still being " +
                "published by the grown shell. The bar is gone but the inset it measured is not",
        )
    }

    /**
     * A sidebar-width window draws the sidebar and no rail and no bar.
     *
     * "No bar" and "no rail" are the halves worth reading. A sidebar drawn beside a still-present
     * rail is the obvious wrong version of this feature and it looks plausible in a screenshot:
     * the app would have two leading chromes, and the rail would be the five-glyph column the
     * sidebar exists to replace.
     */
    val aSidebarWidthShellDrawsTheSidebarAndNoRailAndNoBar: suspend ComposeUiTest.() -> Unit = sidebar@{
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()

        if (desktopWidthIsExpressible(SIDEBAR_WIDTH)) {
            onNodeWithTag(SidebarTag).assertExists(
                "at ${SIDEBAR_WIDTH.value}dp, which is past the " +
                    "${ShellSidebarMinimumWidth.value}dp threshold, the shell should be drawing " +
                    "a sidebar",
            )
            assertNothingTagged(
                NavigationRailTag,
                "at ${SIDEBAR_WIDTH.value}dp the shell drew the navigation rail as well as the " +
                    "sidebar. The rail is the shape this section exists to replace: five glyphs in " +
                    "a ~100dp column, with the sidebar's labels marooned beside it",
            )
            assertNothingTagged(
                TabBarTag,
                "at ${SIDEBAR_WIDTH.value}dp the shell drew the bottom tab bar as well as the " +
                    "sidebar. The bar is the shape the wide shell exists to replace: five segments " +
                    "of ${SIDEBAR_WIDTH.value / 5}dp each, with the tabs marooned inside them",
            )
        }
    }

    /**
     * The sidebar is a leading column, and its destinations are one top-aligned stack.
     *
     * Three separate assertions because the failure has three shapes. A sidebar that spanned the
     * window's width would be the bar again. A sidebar anchored to the bottom edge would be a bar
     * stood on end. And a sidebar whose *destinations* were bottom-anchored would be 8.1's first
     * 8.2 pass: the project list took the flexible height, so the five primary destinations were
     * pinned to the panel's floor and separated from the list by however much space it left.
     *
     * The leading-edge inset is read from [ttSpacing.tabBarEdgeInset] rather than written as a
     * number, because the shell owns that inset and a copy of it in this file is a second thing
     * to keep true — the same reasoning the tab bar's own KDoc gives for not restating the
     * edge inset it does not own.
     */
    val theSidebarIsALeadingColumnRatherThanAStretchedOne: suspend ComposeUiTest.() -> Unit = sidebar@{
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()

        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        val sidebar = onNodeWithTag(SidebarTag).getBoundsInRoot()
        val shell = onNodeWithTag(SignedInShellTag).getBoundsInRoot()
        val sidebarHeight = sidebar.bottom.value - sidebar.top.value
        val shellHeight = shell.bottom.value - shell.top.value

        assertEquals(
            ttSpacing.tabBarEdgeInset.value,
            sidebar.left.value - shell.left.value,
            "the sidebar starts ${sidebar.left.value - shell.left.value}dp inside a shell whose own " +
                "leading edge sits at ${shell.left.value}dp, and it should start at the shell's " +
                "edge inset of ${ttSpacing.tabBarEdgeInset.value}dp with nothing between them. " +
                "Measured against the shell rather than an absolute zero, because where the shell " +
                "sits in the window is the host's business and not this scenario's",
        )
        assertTrue(
            sidebarHeight > 0f && sidebarHeight < shellHeight,
            "the sidebar measured ${sidebarHeight}dp in a ${shellHeight}dp shell, so it is either empty " +
                "or filling the window",
        )

        // It is a full-height leading column, anchored to the top.
        //
        // A desktop sidebar runs the full height from the top, so the assertion is that now, and
        // the trailing space belongs below the destinations.
        assertEquals(
            ttSpacing.tabBarEdgeInset.value,
            sidebar.top.value - shell.top.value,
            "the sidebar starts ${sidebar.top.value - shell.top.value}dp below the shell's top edge, and " +
                "it should start at the shell's own edge inset of " +
                "${ttSpacing.tabBarEdgeInset.value}dp. It runs from the top rather than floating " +
                "in the middle — a desktop sidebar begins at the top — but it still floats by " +
                "that inset, the same rule the tab bar follows on its own axis",
        )
        assertTrue(
            sidebar.right.value < shell.right.value,
            "the sidebar runs to ${sidebar.right.value}dp in a shell that ends at ${shell.right.value}dp, " +
                "so it is spanning the window rather than sitting on its leading edge",
        )

        // It fills the window's height less that inset at each end, rather than being a short
        // panel floating in the middle of the left edge.
        //
        assertEquals(
            0f,
            sidebar.bottom.value - (shell.bottom.value - ttSpacing.tabBarEdgeInset.value),
            "the sidebar stops ${sidebar.bottom.value - shell.bottom.value}dp from the shell's bottom " +
                "edge and should stop at ${-ttSpacing.tabBarEdgeInset.value}dp, mirroring the " +
                "inset it starts with. Running full-bleed to the bottom would be a slab rather " +
                "than a floating surface",
        )

        // Its destinations are one top-aligned stack, not a band pinned to the panel's floor.
        //
        // This is the shape 8.2 settled on after review, and the measurement that separates it
        // from the rejected one is the distance between the project context and the rows that act
        // on it. The old arrangement put the list on a weight, so a short list left several
        // hundred dp of empty glass between the list and Board; here the stack is adjacent and
        // the empty glass ends up below the last row.
        val projectsRow = onNodeWithTag(SidebarButtonTag(AppTab.Projects)).getBoundsInRoot()
        val boardRow = onNodeWithTag(SidebarButtonTag(AppTab.Board)).getBoundsInRoot()
        val backlogRow = onNodeWithTag(SidebarButtonTag(AppTab.Backlog)).getBoundsInRoot()
        val sprintsRow = onNodeWithTag(SidebarButtonTag(AppTab.Sprints)).getBoundsInRoot()
        val settingsRow = onNodeWithTag(SidebarButtonTag(AppTab.Settings)).getBoundsInRoot()

        assertTrue(
            projectsRow.top.value < boardRow.top.value &&
                boardRow.top.value < backlogRow.top.value &&
                backlogRow.top.value < sprintsRow.top.value &&
                sprintsRow.top.value < settingsRow.top.value,
            "the destinations run Projects ${projectsRow.top.value}, Board ${boardRow.top.value}, " +
                "Backlog ${backlogRow.top.value}, Sprints ${sprintsRow.top.value}, Settings " +
                "${settingsRow.top.value}dp from the top, so they are not in the scope order the " +
                "panel is built on (index, the active project's views, app-level)",
        )
        val listToViews = boardRow.top.value - projectsRow.bottom.value
        assertTrue(
            listToViews < SIDEBAR_STACK_MAX_GAP,
            "Board sits ${listToViews}dp below the Projects row in a ${sidebarHeight}dp panel. " +
                "The project list is the only flexible child and it wraps when short, so the " +
                "rows that act on it follow immediately; hundreds of dp here is the " +
                "bottom-anchored arrangement this layout replaced, where a weight on the list " +
                "pushed the primary destinations to the panel's floor",
        )
        assertEquals(
            projectsRow.left.value,
            settingsRow.left.value,
            ONE_POINT,
            "the index row starts at ${projectsRow.left.value}dp and Settings at " +
                "${settingsRow.left.value}dp, so the two unscoped destinations do not share an " +
                "edge and the indent is not the thing that marks the scoped group",
        )
        val indent = boardRow.left.value - projectsRow.left.value
        assertTrue(
            indent >= ttSpacing.lg.value - ONE_POINT &&
                sprintsRow.left.value - projectsRow.left.value >= ttSpacing.lg.value - ONE_POINT,
            "Board and Sprints start ${indent}dp inside the index row, so the indent is not " +
                "marking them as the active project's children. The indent is the nesting " +
                "signal; without it the three views read as peers of Projects and Settings",
        )
        val emptyGlass = sidebar.bottom.value - settingsRow.bottom.value
        val settingsHeight = settingsRow.bottom.value - settingsRow.top.value
        assertTrue(
            emptyGlass > settingsHeight,
            "Settings ends ${emptyGlass}dp above the panel's bottom edge, less than one row " +
                "(${settingsHeight}dp): the last destination is pinned to the floor rather than " +
                "closing the stack, which puts the empty glass back between two groups",
        )

        // Every destination is exactly one node.
        //
        // The count half is the original claim: a row that stopped composing is invisible to the
        // selection scenarios, which only look at the selected one. The "exactly one" half is the
        // defect 8.1's draft shipped — a "Projects" heading, the project list and the Projects
        // destination row all carried `SidebarButtonTag(AppTab.Projects)`, so every tap-by-name
        // query matched three nodes.
        AppTab.entries.forEach { tab ->
            assertEquals(
                1,
                onAllNodesWithTag(SidebarButtonTag(tab)).fetchSemanticsNodes().size,
                "the ${tab.wireValue} destination should be one node in the sidebar, and " +
                    "SidebarButtonTag names " +
                    "${onAllNodesWithTag(SidebarButtonTag(tab)).fetchSemanticsNodes().size}. A " +
                    "destination that stopped composing is invisible to the selection " +
                    "scenarios, and a tag shared with a heading breaks every tap-by-name scenario",
            )
        }

        // And the content starts *beside* it rather than running underneath. Read on a real
        // screen rather than on the content box, because the box carrying the padding is
        // anonymous: a node inside it is the only thing that can show whether the padding reached
        // the pixels.
        //
        // The projects list is used because it fills the content area edge to edge on the landing
        // tab. A screen whose content sits well inside its own margins — a centred card, a row
        // with its own gutter — would keep passing with the padding removed, because its left edge
        // would clear the sidebar's for reasons that have nothing to do with the inset. That is not a
        // hypothetical: the board's columns cannot be used at all here, because the landing tab
        // has no open project and draws none.
        val list = onNodeWithTag(ProjectsListScreenTag).getBoundsInRoot()
        assertTrue(
            list.left.value >= sidebar.right.value,
            "the projects list starts at ${list.left.value}dp while the sidebar runs to " +
                "${sidebar.right.value}dp, so the content is running *under* the sidebar. The sidebar's " +
                "width is measured and fed back as the content box's leading inset; if that " +
                "measurement is not reaching the padding, every screen is drawn under the sidebar",
        )
    }

    /**
     * The sidebar lists projects in the picker's order, most recently updated first.
     *
     * The fixture is chosen so alphabetical order and updated-descending disagree: Zenith was
     * touched most recently and Apollo is first alphabetically. A list sorted by name, or one
     * that keeps insertion order, puts Apollo on top and fails here — so this is the assertion
     * that separates "the model's order" from "an order that happens to look plausible".
     */
    val theSidebarListsProjectsInTheSwitchersOrder: suspend ComposeUiTest.() -> Unit = sidebar@{
        val projects = FakeProjectRepository()
        val apollo = shellTestProject(
            name = "Apollo",
            keyPrefix = "AP",
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
        )
        val zenith = shellTestProject(
            name = "Zenith",
            keyPrefix = "ZE",
            updatedAt = Instant.parse("2026-03-04T00:00:00Z"),
        )
        projects.seed(apollo)
        projects.seed(zenith)

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        val first = onNodeWithTag(SidebarProjectTag(zenith.id)).getBoundsInRoot()
        val second = onNodeWithTag(SidebarProjectTag(apollo.id)).getBoundsInRoot()
        assertTrue(
            first.top.value < second.top.value,
            "Zenith (updated 2026-03-04) sits at ${first.top.value}dp and Apollo (updated " +
                "2026-01-02) at ${second.top.value}dp, so the sidebar is not listing projects in " +
                "`ProjectPickerModel`'s own order — updated descending, then name. The fixture " +
                "puts the alphabetical first second, which is what separates this from a list " +
                "sorted by name",
        )

        // Each row names the project twice: name and key prefix. The prefix is the identifier
        // the rest of the app prints on every work item (`AP-12`), and in a 280dp panel it is
        // what tells two similarly named projects apart. The repeated glyph the first pass drew
        // carried neither.
        onNodeWithTag(SidebarProjectTag(zenith.id)).assert(hasText("ZE", substring = true))
        onNodeWithTag(SidebarProjectTag(apollo.id)).assert(hasText("AP", substring = true))
    }

    /**
     * Tapping a project row makes it the active project and lands on Board.
     *
     * The row's `selected` property and the destination's selection are asserted together, for
     * the reason the rail's selection scenario gives: "a board appeared" is satisfied by a tab
     * that never moved, and a highlight alone is satisfied by a stale id the tap wrote nowhere.
     * Before the tap nothing is selected, so a highlight that is born on the first row is also a
     * failure here rather than a passing coincidence.
     */
    val tappingASidebarProjectOpensItOnBoard: suspend ComposeUiTest.() -> Unit = sidebar@{
        val projects = FakeProjectRepository()
        val apollo = shellTestProject(
            name = "Apollo",
            keyPrefix = "AP",
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
        )
        val zenith = shellTestProject(
            name = "Zenith",
            keyPrefix = "ZE",
            updatedAt = Instant.parse("2026-03-04T00:00:00Z"),
        )
        projects.seed(apollo)
        projects.seed(zenith)
        val candidates = listOf(apollo.id, zenith.id)

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        assertEquals(
            emptyList(),
            selectedSidebarProjects(candidates),
            "no project has been opened yet, so no sidebar row should claim to be the active one",
        )

        onNodeWithTag(SidebarProjectTag(zenith.id)).performClick()
        waitForIdle()

        assertEquals(
            listOf(zenith.id),
            selectedSidebarProjects(candidates),
            "after tapping Zenith its sidebar row is not the active one, so the highlight is not " +
                "reading `router.state.activeProjectID`",
        )
        assertEquals(
            listOf(AppTab.Board),
            selectedSidebarDestinations(),
            "tapping a project row should land on Board, and the sidebar's selected destination " +
                "is " + selectedSidebarDestinations() + " instead of the Board row",
        )
    }

    /**
     * A project tapped while Settings is selected still lands on Board.
     *
     * This is why the tap is `openProject(id, AppTab.Board)` rather than the default overload:
     * the router rejects Settings outright inside `openProject`, so the default leaves the
     * project switched and the tab where it was — a switcher that silently does nothing on one
     * tab. Settings is also the tab that proves the sidebar is a project switcher rather than
     * part of the scoped views.
     */
    val switchingProjectFromSettingsLandsOnBoard: suspend ComposeUiTest.() -> Unit = sidebar@{
        val projects = FakeProjectRepository()
        val apollo = shellTestProject(
            name = "Apollo",
            keyPrefix = "AP",
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
        )
        val zenith = shellTestProject(
            name = "Zenith",
            keyPrefix = "ZE",
            updatedAt = Instant.parse("2026-03-04T00:00:00Z"),
        )
        projects.seed(apollo)
        projects.seed(zenith)
        val candidates = listOf(apollo.id, zenith.id)

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        onNodeWithTag(SidebarProjectTag(apollo.id)).performClick()
        waitForIdle()
        onNodeWithTag(SidebarButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        assertEquals(
            listOf(AppTab.Settings),
            selectedSidebarDestinations(),
            "the scenario needs Settings to be the current destination before it taps a project",
        )

        onNodeWithTag(SidebarProjectTag(zenith.id)).performClick()
        waitForIdle()

        assertEquals(
            listOf(AppTab.Board),
            selectedSidebarDestinations(),
            "the project changed while Settings was selected and the tab stayed on Settings, so " +
                "the tap used the default `openProject` — which the router rejects on Settings — " +
                "instead of naming Board",
        )
        assertEquals(
            listOf(zenith.id),
            selectedSidebarProjects(candidates),
            "Zenith is on Board but its sidebar row is not marked as the active project",
        )
    }

    /**
     * A full switcher scrolls inside the panel and the destinations do not move.
     *
     * Forty projects is the case a `weight` on the list is for: without it the list measures
     * its content unbounded and pushes Settings and the views off the panel. Forty rather than
     * twenty because the sidebar scenarios compose at [SIDEBAR_DENSITY], which gives a taller
     * display in dp — twenty rows fit there, and a scroll action that scrolls nothing would
     * assert nothing. The destinations' bounds are captured before and after scrolling to the
     * last row, so a list that grew the panel, or a stack that re-flowed, fails as loudly as
     * one that overflowed.
     */
    val theSidebarKeepsItsDestinationsWhenTheProjectListScrolls:
        suspend ComposeUiTest.() -> Unit = sidebar@{
        val projects = FakeProjectRepository()
        val seeded = (1..40).map { index ->
            shellTestProject(
                name = "Project ${index.toString().padStart(2, '0')}",
                keyPrefix = "P${index.toString().padStart(2, '0')}",
                updatedAt = Instant.fromEpochSeconds(1_760_000_000L + index * 86_400L),
            )
        }
        seeded.forEach { projects.seed(it) }

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        val boardBefore = onNodeWithTag(SidebarButtonTag(AppTab.Board)).getBoundsInRoot()
        val settingsBefore = onNodeWithTag(SidebarButtonTag(AppTab.Settings)).getBoundsInRoot()

        // The last index is the oldest project — the list is ordered updated-descending, so the
        // final row is the one that started the list.
        val oldest = seeded.first()
        onNodeWithTag(SidebarProjectListTag).performScrollToIndex(seeded.lastIndex)
        waitForIdle()

        onNodeWithTag(SidebarProjectTag(oldest.id)).assertIsDisplayed()

        val boardAfter = onNodeWithTag(SidebarButtonTag(AppTab.Board)).getBoundsInRoot()
        val settingsAfter = onNodeWithTag(SidebarButtonTag(AppTab.Settings)).getBoundsInRoot()
        assertEquals(
            boardBefore.top.value,
            boardAfter.top.value,
            ONE_POINT,
            "Board moved from ${boardBefore.top.value}dp to ${boardAfter.top.value}dp when the " +
                "project list was scrolled, so the list is pushing the destinations around " +
                "instead of scrolling inside its own box",
        )
        assertEquals(
            settingsBefore.top.value,
            settingsAfter.top.value,
            ONE_POINT,
            "Settings moved from ${settingsBefore.top.value}dp to ${settingsAfter.top.value}dp " +
                "when the project list was scrolled, so the panel is growing with its content " +
                "rather than keeping the list as the only flexible child",
        )
    }

    /**
     * An empty switcher renders no rows and still carries every destination.
     *
     * The fixture is an *archived* project rather than a repository with nothing in it, and that
     * is not a shortcut: the Projects list seeds a sample project on its first read when the store
     * is empty, so a genuinely empty repository stops being empty before the sidebar can be
     * observed. An archived project is excluded from the picker (`ProjectPickerModel` reads
     * `includeArchived = false`) and it makes `seedSampleDataIfNeeded` a no-op, which is the only
     * shape in which "no projects in the switcher" is reachable at all.
     *
     * No placeholder and no empty-state copy: the chrome should not explain the absence of data.
     */
    val anEmptySidebarSwitcherStillCarriesEveryDestination:
        suspend ComposeUiTest.() -> Unit = sidebar@{
        val projects = FakeProjectRepository()
        projects.seed(
            shellTestProject(name = "Filed Away", keyPrefix = "FA", isArchived = true),
        )

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        assertEquals(
            0,
            onAllNodesWithTag(SidebarProjectListTag).fetchSemanticsNodes().size,
            "the sidebar composed a project list for a repository whose only project is " +
                "archived, so the switcher is not reading the picker's non-archived list",
        )
        AppTab.entries.forEach { tab ->
            onNodeWithTag(SidebarButtonTag(tab)).assertExists(
                "the ${tab.wireValue} destination is missing from a sidebar with no projects. " +
                    "The switcher being empty must not cost the five destinations, and nothing " +
                    "should stand in for the missing rows",
            )
        }
    }

    /**
     * The sidebar's active row follows a project opened from the Projects list.
     *
     * This is the assertion that fails if a second "which project" state is introduced: the
     * project is chosen in the list on the Projects tab, not in the sidebar, and the sidebar has
     * to move without being told. The destination is asserted with it so a stale highlight and a
     * board that never opened stay different failures.
     */
    val theSidebarFollowsAProjectChosenFromTheProjectsList:
        suspend ComposeUiTest.() -> Unit = sidebar@{
        val projects = FakeProjectRepository()
        val apollo = shellTestProject(
            name = "Apollo",
            keyPrefix = "AP",
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
        )
        val viking = shellTestProject(
            name = "Viking",
            keyPrefix = "VI",
            updatedAt = Instant.parse("2026-03-04T00:00:00Z"),
        )
        projects.seed(apollo)
        projects.seed(viking)
        val candidates = listOf(apollo.id, viking.id)

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        onNodeWithTag(ProjectRowTag(ProjectSummary(viking, itemCount = 0))).performClick()
        waitForIdle()

        assertEquals(
            listOf(viking.id),
            selectedSidebarProjects(candidates),
            "Viking was opened from the Projects list and the sidebar still marks " +
                "${selectedSidebarProjects(candidates)} as the active project, so the sidebar " +
                "keeps a \"which project\" answer of its own instead of reading route state. " +
                "A second source for that answer drifts the first time a project is opened " +
                "anywhere but here",
        )
        assertEquals(
            listOf(AppTab.Board),
            selectedSidebarDestinations(),
            "a project opened from the Projects list should land on Board, and the sidebar's " +
                "selected destination is " + selectedSidebarDestinations() + " instead",
        )
    }

    /**
     * A project created in the shell appears in the sidebar without a relaunch.
     *
     * The sidebar observes `ProjectChange`; a switcher that reads once on composition passes
     * every static scenario and fails only after a write, which is exactly the defect this
     * guards. The id is read back from the store because the create form returns nothing to its
     * caller, and the row is then found by that id — searching by the name would also match the
     * Projects list behind the sheet.
     */
    val creatingAProjectShowsItInTheSidebarWithoutARelaunch:
        suspend ComposeUiTest.() -> Unit = sidebar@{
        val projects = FakeProjectRepository()

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        onNodeWithTag(ProjectsListNewTag).performClick()
        waitForIdle()

        onRoot().printToLog("AFTER_NEW_PROJECT_CLICK")
        onNodeWithTag(NewProjectFormTag).assertExists(
            "the New Project sheet did not open at ${SIDEBAR_WIDTH.value}dp, so this scenario " +
                "cannot reach the write it is about",
        )
        onNodeWithTag("name").performTextInput("Gemini")
        onNodeWithTag(NewProjectCreateTag).performClick()
        waitForIdle()

        val created = projects.projects(includeArchived = false).first { it.name == "Gemini" }
        onNodeWithTag(SidebarProjectTag(created.id)).assertExists(
            "\"Gemini\" is in the store but the sidebar has no row for it: the switcher is " +
                "not observing `ProjectChange`, so it only shows writes that happened before " +
                "the shell composed",
        )
    }

    /**
     * Shrinking the window past the sidebar threshold sheds the sidebar's inset, and growing
     * back to the sidebar threshold drops the rail's inset.
     *
     * This is a *resize* defect, and neither static width can show it. `leadingChromeInset` is
     * written by the chrome's own `onSizeChanged`, and a chrome that has left the composition is
     * never remeasured, so nothing zeroes the measurement. A shell that starts at [PHONE_WIDTH]
     * never runs that arm and reads `0.dp`; a shell that starts at [SIDEBAR_WIDTH] reads the
     * sidebar's true width. Only the crossing between the three leaves the old width behind as a
     * band of blank canvas down the leading edge, which is why this stands one shell up at
     * [SIDEBAR_WIDTH], shrinks it to [DESKTOP_WIDTH], then grows it back to [SIDEBAR_WIDTH] and
     * asserts on the same composition rather than asserting the three widths in isolation.
     *
     * The measurement is the projects list's leading edge, for the reason
     * [theSidebarIsALeadingColumnRatherThanAStretchedOne] gives: the box carrying the inset is
     * anonymous, so a node inside it is the only thing that can show whether the inset reached the
     * pixels. After the shrink that edge must sit on the *rail's* trailing edge, not on the shell's
     * leading edge: at [DESKTOP_WIDTH] the shell draws the rail, so content beside it is correct,
     * and what would be a defect is the sidebar's 280dp band still being applied. Comparing
     * against the rail's own measured edge is what separates the two; this scenario used to assert
     * the shell's edge, which no rail-width shell can satisfy.
     *
     * The ordering is chosen so both crossings are exercised on one composition, because a
     * rail/sidebar that leaves the composition is never remeasured to zero.
     */
    val resizingSidebarToRailAndBackShedsBothInsets: suspend ComposeUiTest.() -> Unit = resize@{
        val width = mutableStateOf(SIDEBAR_WIDTH)
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            widthState = width,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()

        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@resize

        // Asserted before the shrink, or this could pass on a shell that never drew the sidebar
        // and therefore never had an inset to shed.
        onNodeWithTag(SidebarTag).assertExists(
            "at ${SIDEBAR_WIDTH.value}dp the shell should draw a sidebar before it is " +
                "shrunk, or there is no stale inset for this scenario to catch",
        )

        // Capture the sidebar's right edge for the second crossing assertion
        val sidebarAtStart = onNodeWithTag(SidebarTag).getBoundsInRoot()

        // First crossing: Sidebar → Rail
        runOnIdle { width.value = DESKTOP_WIDTH }
        waitForIdle()

        onNodeWithTag(NavigationRailTag).assertExists(
            "at ${DESKTOP_WIDTH.value}dp the shell should be drawing a navigation rail",
        )
        assertNothingTagged(
            SidebarTag,
            "at ${DESKTOP_WIDTH.value}dp the shell is still drawing the sidebar it had at " +
                "${SIDEBAR_WIDTH.value}dp",
        )

        val rail = onNodeWithTag(NavigationRailTag).getBoundsInRoot()
        val list = onNodeWithTag(ProjectsListScreenTag).getBoundsInRoot()
        assertEquals(
            rail.right.value,
            list.left.value,
            ONE_POINT,
            "after shrinking to ${DESKTOP_WIDTH.value}dp the projects list starts at " +
                "${list.left.value}dp while the rail ends at ${rail.right.value}dp — a " +
                "${list.left.value - rail.right.value}dp gap down the leading edge. The sidebar's " +
                "inset was measured at ${SIDEBAR_WIDTH.value}dp and is still being applied after " +
                "the sidebar is gone, because a chrome that leaves the composition is never " +
                "remeasured to zero; the rail's own measurement should have replaced it",
        )

        // Second crossing: Rail → Sidebar
        runOnIdle { width.value = SIDEBAR_WIDTH }
        waitForIdle()

        onNodeWithTag(SidebarTag).assertExists(
            "at ${SIDEBAR_WIDTH.value}dp the shell should be drawing a sidebar again",
        )
        assertNothingTagged(
            NavigationRailTag,
            "at ${SIDEBAR_WIDTH.value}dp the shell is still drawing the rail it had at " +
                "${DESKTOP_WIDTH.value}dp",
        )

        val shell2 = onNodeWithTag(SignedInShellTag).getBoundsInRoot()
        val list2 = onNodeWithTag(ProjectsListScreenTag).getBoundsInRoot()
        assertTrue(
            list2.left.value >= sidebarAtStart.right.value,
            "after growing back to ${SIDEBAR_WIDTH.value}dp the projects list starts at " +
                "${list2.left.value}dp while the sidebar runs to ${sidebarAtStart.right.value}dp — " +
                "the content is running *under* the sidebar. The rail's inset was measured at " +
                "${DESKTOP_WIDTH.value}dp and is still being applied after the rail is gone, " +
                "because a rail leaves the composition without being remeasured to zero",
        )
    }

    /**
     * Exactly one destination on the sidebar says it is the current one, and it follows the route.
     *
     * [selectedRailDestinations] reads the rail's tags, so this reads the sidebar's. The same
     * reasoning applies and the same failure is guarded: a `selected` that is never set is
     * invisible to every other assertion in this file, because the sidebar still navigates and
     * the screens still change.
     *
     * Read in the tree rather than from the router, and compared as a list so that "two are
     * selected" fails as loudly as "none is" — a count-only assertion would accept a sidebar that
     * lit the wrong single destination.
     */
    val exactlyOneSidebarDestinationIsMarkedSelected: suspend ComposeUiTest.() -> Unit = sidebar@{
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()

        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        assertEquals(
            listOf(AppTab.Projects),
            selectedSidebarDestinations(),
            "expected the landing destination to be the selected one",
        )

        // The wash is on the landing destination before anything is tapped. Asserted first so that
        // a wash which never moves and a wash which starts on the wrong one stay different
        // failures: the check after the click alone cannot tell them apart, because a wash stuck
        // at index 0 and a wash correctly at index 0 are identical until the selection changes.
        // Note: the sidebar uses a wash, not a lens, so we read the selected semantics instead.

        onNodeWithTag(SidebarButtonTag(AppTab.Settings)).performClick()
        waitForIdle()

        assertEquals(
            listOf(AppTab.Settings),
            selectedSidebarDestinations(),
            "after tapping Settings on the sidebar the selection did not follow the route, so the " +
                "sidebar's wash and its `selected` property are both describing a stale destination",
        )

        // And the index row is a destination, not the inert heading 8.1's draft put above the
        // list. It was plain text with the destination's own tag and the destination's own
        // selected ink, which is a control that looks live and does nothing; tapping it has to
        // land on the list for that not to be the shape again.
        onNodeWithTag(SidebarButtonTag(AppTab.Projects)).performClick()
        waitForIdle()
        assertEquals(
            listOf(AppTab.Projects),
            selectedSidebarDestinations(),
            "the Projects row in the sidebar did not take the selection back to the projects " +
                "index, so it is decorative chrome rather than a destination",
        )
    }

    /**
     * Every destination is named in the sidebar, and each carries the Tab role.
     *
     * Two things that are one line of code each and that nothing else in the suite can see.
     * [Role.Tab] is what gets a tab announced as a tab rather than as another button, and it is
     * a `Role` argument on `clickable` — omitting it changes nothing about the app and is
     * caught by nothing. The labels are asserted because the glyphs are deliberately
     * unlabelled for accessibility (the label beside each one already names it), so a missing
     * string resource would leave a tab with no name *at all*.
     */
    val everySidebarDestinationIsNamedAndCarriesTheTabRole: suspend ComposeUiTest.() -> Unit = sidebar@{
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            width = SIDEBAR_WIDTH,
            density = SIDEBAR_DENSITY,
        )
        waitForIdle()

        if (!desktopWidthIsExpressible(SIDEBAR_WIDTH)) return@sidebar

        AppTab.entries.forEach { tab ->
            onNodeWithTag(SidebarButtonTag(tab))
                .assertIsDisplayed()
                .assert(expectTabRole())
                .assert(hasText(tabLabel(tab), substring = true))
        }
    }

    /**
     * Asserts that nothing in the tree carries [tag].
     *
     * A counted assertion rather than `assertDoesNotExist()`, because that takes no message in
     * this version of `ui-test`, and "the rail should not be here at 390dp" is exactly the kind of
     * failure whose explanation belongs next to the assertion. Counting also says *how many* were
     * found, so drawing the rail twice is as visible as drawing it once.
     */
    private fun ComposeUiTest.assertNothingTagged(tag: String, why: String) {
        assertEquals(0, onAllNodesWithTag(tag).fetchSemanticsNodes().size, why)
    }

    /**
     * Whether this host can build the wide shell these desktop-layout scenarios are about.
     *
     * ## It is declared per host, not measured, and two mutations is why
     *
     * The obvious implementation measures — "did the shell get the width I asked for?" — and it is
     * wrong twice over, both times found by mutation rather than by reading.
     *
     * Asking the *shell* reads the absence of the thing under test as evidence about the
     * environment: if the shell stopped filling the box it is given, the guard reports "this host
     * cannot do wide shells", and all three scenarios stop asserting while still reporting green —
     * on the two hosts that are supposed to guard them.
     *
     * Asking the *composition root* is no better, and was found the same way: with the width hook
     * mutated to hand out 400dp, `onRoot()` reported 400dp and the scenarios skipped. The root
     * tracks the constrained composition, not the window, so it is not a viewport either. Nothing
     * reachable from inside a composition reports what the host *could* have given.
     *
     * So the capability is **declared**, per host, exactly as `thisHostRasterisesText` declares
     * whether a host can rasterise a frame. A declared capability cannot be moved by a change to
     * the code under test, which is the one property the measured version lacked.
     *
     * The skip is reported as a **pass**, not a skip, and it prints — `kotlin.test` has no portable
     * skip. It is recorded in `AGENTS.md` under the Android host rather than left to be found.
     */
    private fun ComposeUiTest.desktopWidthIsExpressible(requested: Dp): Boolean {
        if (thisHostExpressesDesktopWidth()) return true
        // Throws an assumption on a host that cannot build this shell, so the scenario is
        // recorded as skipped rather than returning green having asserted nothing. A fixed width
        // is a request rather than a guarantee — it is clamped by its parent's constraints — and
        // a desktop window is not a configuration the Android host has.
        assumeHostExpressesDesktopWidth()
        return false
    }

    /**
     * Slack for the rail-height equality, as a fraction of one destination cell.
     *
     * A rail is a `Column` of cells, so its height is the sum of theirs, but the sum and the
     * first cell's own measured height are read separately and their text metrics belong to the
     * rasteriser. A quarter of a cell covers that with room to spare while a rail collapsed to a
     * single destination is off by eighty per cent, so the band cannot hide the failure it is for.
     */
    private const val QUARTER = 0.25f

    /**
     * How far the active project's views may sit below the project list and still be one stack,
     * in dp.
     *
     * With an empty list the gap is the explicit `ttSpacing.sm` spacer plus the column's own
     * `ttSpacing.xxs` rhythm; with a full list it is the same. The rejected bottom-anchored
     * layout left the better part of the panel's height between them, so 100dp separates "one
     * stack" from "two groups" by an order of magnitude rather than by a rounding argument.
     */
    private const val SIDEBAR_STACK_MAX_GAP = 100f

    /**
     * A point of slack, in dp, for measurements comparing two laid-out nodes.
     *
     * Half a point is the destinations' measured centre-line spread, which is the caption widths'
     * own sub-pixel rounding. The defects these guard — glyphs out of line, a lens overrunning its
     * cell — were tens of points wide, so a point separates the two by two orders of magnitude
     * rather than by a rounding argument.
     */
    private const val ONE_POINT = 1f

    /**
     * Two pixels of slack on the centring claim, in dp.
     *
     * The alignment is exact in pixels and the gaps are read in dp, so a root or sheet with
     * an odd pixel count rounds one gap against the other by under a pixel; two dp covers
     * that at every host density without loosening the claim into "roughly centred" — at
     * 1000dp the anchored position this guards against is hundreds of dp off-centre.
     */
    private val VERTICAL_CENTRE_TOLERANCE = 2f

    /**
     * How much room a centred sheet must leave above and below to have asserted anything.
     *
     * The degenerate case is a surface filling the root: its gaps are zero, its margins are
     * equal, and the centring assertion passes while the sheet has stopped being a window
     * over anything. 20dp is far below the ~200dp the real form leaves at this width and
     * far above any rounding artefact.
     */
    private val CENTRED_SHEET_MIN_CLEARANCE = 20f

    /**
     * How far the sheet's bottom may sit from the root's bottom and still be anchored,
     * in dp.
     *
     * The anchored gap is frame chrome — `ttSpacing.md`, navigation padding, the tab bar —
     * never more than a hundred dp on any host (68 on the Android host's cramped
     * viewport). The windowed gap is half the leftover viewport — ~280 on the hosts
     * that run it. 150dp separates the two placements by a margin neither side's
     * geometry can cross, without pinning the bar's measured height the way asserting
     * the exact gap would.
     */
    private val ANCHORED_SHEET_BOTTOM_CLEARANCE = 150f

    /**
     * A wide window shows the sprint list and the sprint detail side by side.
     *
     * Asserted on *geometry*, not on presence. Both panes existing is satisfied by the detail
     * being drawn on top of the list, which is the single-pane layout with the list left composed
     * underneath it — a real and easy mistake here, because the two are the same two composables
     * and the only thing that changed is where they are placed. `list.right < detail.left` is the
     * claim that they are actually beside each other.
     */
    val aWideShellDrawsTheSprintListAndDetailSideBySide: suspend ComposeUiTest.() -> Unit = wide@{
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = DESKTOP_WIDTH,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        onNodeWithTag(NavigationRailButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintRowTag(sprint.id), useUnmergedTree = true).performClick()
        waitForIdle()

        val list = onNodeWithTag(SprintListScreenTag).getBoundsInRoot()
        val detail = onNodeWithTag(SprintDetailScreenTag).getBoundsInRoot()

        assertTrue(
            list.right.value < detail.left.value,
            "the list runs to ${list.right.value}dp and the detail starts at " +
                "${detail.left.value}dp, so the panes are not side by side. Both being present is " +
                "not enough — the detail drawn over the list satisfies that too, and it is the " +
                "mistake this arrangement exists to remove",
        )
        assertTrue(
            detail.right.value - detail.left.value > 0f && list.right.value - list.left.value > 0f,
            "the list is ${list.right.value - list.left.value}dp wide and the detail is " +
                "${detail.right.value - detail.left.value}dp, so a pane has collapsed to nothing",
        )
    }

    /**
     * "New sprint" opens its form in the right pane on a wide shell, beside the list.
     *
     * The complaint this answers is that a window is not the wide arrangement's vocabulary: a
     * sprint's *detail* opens in the pane beside the list, so its form arriving as a floating
     * sheet makes one tab use two ideas of where a sprint is worked on. The assertion is
     * therefore the same one the detail scenario makes, and for the same reason — both panes
     * being present is not enough, because a form drawn *over* the list satisfies that too, and
     * covering the list is precisely the arrangement this replaces.
     *
     * This walks in through the rail rather than through `openProject(inTab = Sprints)`, which
     * makes it the harder case: a tab switch leaves the stack alone (`Router.selectTab`), so the
     * tab is a single full-width list with no right pane at all, and the pane has to be created
     * for the form rather than found already there. That is the state a desktop user is in when
     * they press the button.
     *
     * Cancel then closes it, which is also what proves the pane is the surface: there is no
     * scrim out here to dismiss it with, so the pane's own action row is the only way back.
     */
    val aWideShellOpensTheNewSprintFormBesideTheList: suspend ComposeUiTest.() -> Unit = wide@{
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = DESKTOP_WIDTH,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        onNodeWithTag(NavigationRailButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintListNewTag).performClick()
        waitForIdle()

        // Asserted before the bounds so that the failure names the defect. A window is its own
        // scene, so a form drawn as one is absent from this tree entirely rather than sitting
        // in the wrong place — and "no such node" would otherwise read as "the button did
        // nothing", which is a different bug with a different fix.
        onNodeWithTag(SprintEditorSheetTag).assertExists(
            "the new-sprint form is not in the shell's own tree. Either the button opened " +
                "nothing, or the form is still a window — which lives in a scene of its own, " +
                "over both panes instead of beside the list",
        )

        val list = onNodeWithTag(SprintListScreenTag).getBoundsInRoot()
        val form = onNodeWithTag(SprintEditorSheetTag).getBoundsInRoot()

        assertTrue(
            list.right.value < form.left.value,
            "the list runs to ${list.right.value}dp and the form starts at ${form.left.value}dp, " +
                "so the form is not beside the list. Drawn over it, it starts inside it — and a " +
                "window over both panes is the thing this arrangement exists to replace",
        )
        assertTrue(
            form.right.value - form.left.value > 0f,
            "the form is ${form.right.value - form.left.value}dp wide, so the pane has collapsed " +
                "to nothing and the width went to the list",
        )

        onNodeWithTag(SprintEditorCancelTag).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(SprintEditorSheetTag).assertDoesNotExist()
        // The tab goes back to the single full-width list it was before the form opened, rather
        // than to a right pane holding a placeholder: with no route on the stack there is no
        // pair to be half of, and the width the form borrowed has to be given back.
        val restoredList = onNodeWithTag(SprintListScreenTag).getBoundsInRoot()
        assertTrue(
            restoredList.right.value > list.right.value,
            "the list still runs to ${restoredList.right.value}dp, the width it had while the " +
                "form was open, so the pane is gone but its share of the width is not",
        )
    }

    /**
     * A narrow window replaces the list with the detail, as it always has.
     *
     * The compact path is the one that had to stay unchanged, so it is asserted directly rather
     * than inferred from the wide scenarios. Read from the other side of the same fact: the list
     * is *absent*, not merely covered.
     */
    val aNarrowShellReplacesTheSprintListWithTheDetail: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val sprint = shellTestSprint(projectID = project.id)
        projects.seedSprint(sprint)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = PHONE_WIDTH,
        )
        waitForIdle()
        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        onNodeWithTag(TabButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintRowTag(sprint.id), useUnmergedTree = true).performClick()
        waitForIdle()

        onNodeWithTag(SprintDetailScreenTag).assertExists(
            "at ${PHONE_WIDTH.value}dp the sprint detail should be showing",
        )
        assertNothingTagged(
            SprintListScreenTag,
            "at ${PHONE_WIDTH.value}dp the sprint list is still composed behind the detail. It is " +
                "meant to be replaced, not stacked — the same arrangement as before this section",
        )
    }

    /**
     * A narrow window still opens "New sprint" as the sheet it always was.
     *
     * The wide arrangement is the one that changed, and this is the other half of that claim:
     * the destination is handed no `requestNewSprint` under a compact class, so it keeps the
     * editor to itself. Read from the list side rather than the form's — the list is *still
     * there* behind the sheet, which is the difference between a modal and the pane the wide
     * shell draws beside it.
     */
    val aNarrowShellStillOpensTheNewSprintFormAsASheet: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        projects.seedSprint(shellTestSprint(projectID = project.id))

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = PHONE_WIDTH,
        )
        waitForIdle()
        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        onNodeWithTag(TabButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()
        onNodeWithTag(SprintListNewTag).performClick()
        waitForIdle()

        onNodeWithTag(SprintEditorSheetTag).assertExists(
            "at ${PHONE_WIDTH.value}dp pressing \"New sprint\" opened nothing. The wide shell " +
                "draws the form in its right pane; a compact one has no pane to draw it in, so " +
                "the destination has to keep drawing the sheet itself",
        )
        onNodeWithTag(SprintListScreenTag).assertExists(
            "at ${PHONE_WIDTH.value}dp the list is gone from under the form, so the editor is " +
                "occupying the screen rather than covering it — that is the pane arrangement, " +
                "which this width must not have",
        )
    }

    /**
     * Choosing a different sprint changes the detail pane without changing the stack.
     *
     * This is the assertion that fails if the two panes were implemented as a push. The stack is
     * read from the router rather than inferred from what is on screen, and its *length* is the
     * part that matters: a detail pane that pushed would keep pushing, so the path would grow with
     * every selection and the second sprint's detail would be reachable only by popping through
     * the first — while looking, in a two-pane layout, exactly like the right behaviour.
     *
     * Two sprints, so the second tap has somewhere else to go. Asserted on the detail's *content*
     * as well as the length, because an unchanged stack and an unchanged detail are also the
     * failure this cannot see from the router alone.
     */
    val choosingAnotherSprintChangesTheDetailWithoutGrowingTheStack:
        suspend ComposeUiTest.() -> Unit = wide@{
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val first = shellTestSprint(
            projectID = project.id,
            name = "Sprint One",
            id = SprintID("11111111-1111-1111-1111-111111111111"),
            startDate = Instant.parse("2026-03-02T00:00:00Z"),
        )
        val second = shellTestSprint(
            projectID = project.id,
            name = "Sprint Two",
            id = SprintID("22222222-2222-2222-2222-222222222222"),
            goalStoryPoints = 34,
            startDate = Instant.parse("2026-03-16T00:00:00Z"),
        )
        projects.seedSprint(first)
        projects.seedSprint(second)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = DESKTOP_WIDTH,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

        router.openProject(project.id, inTab = AppTab.Board)
        waitForIdle()
        onNodeWithTag(NavigationRailButtonTag(AppTab.Sprints)).performClick()
        waitForIdle()

        val depthAtRoot = router.path(AppTab.Sprints).size

        onNodeWithTag(SprintRowTag(first.id), useUnmergedTree = true).performClick()
        waitForIdle()
        val depthAfterFirst = router.path(AppTab.Sprints).size
        val detailForFirst = renderedTextOf(SprintDetailCardTag)

        onNodeWithTag(SprintRowTag(second.id), useUnmergedTree = true).performClick()
        waitForIdle()
        val depthAfterSecond = router.path(AppTab.Sprints).size
        val detailForSecond = renderedTextOf(SprintDetailCardTag)

        assertEquals(
            depthAfterFirst,
            depthAfterSecond,
            "the stack was $depthAtRoot deep at the tab root, ${depthAfterFirst} after opening the " +
                "first sprint and ${depthAfterSecond} after opening the second. The detail pane " +
                "follows the selection rather than pushing onto it, so a second sprint must land " +
                "at the same depth — a pane implemented as a push grows the stack and then " +
                "requires popping back through every sprint already visited",
        )

        // The pane's *content* changed, which is the other half and the one the depth check cannot
        // see. The first version of this asserted that the pane had *moved*, which was wrong: a
        // two-pane layout's detail pane is a fixed slot by design, and it correctly stays put while
        // what is inside it changes. Asserting position would have failed on correct behaviour.
        //
        // Read from the rendered semantics rather than from the model, because "the store holds the
        // second sprint" and "the pane is drawing the second sprint" are different claims and only
        // one of them is about this layout. The two sprints carry different goals so the text has
        // to differ; equal goals would make this pass whatever the pane drew.
        assertTrue(
            detailForFirst != detailForSecond,
            "the detail pane reads \"$detailForFirst\" for both sprints. The pane is a fixed " +
                "slot and is not supposed to move — what has to change is what is inside it, and " +
                "the two sprints are named differently precisely so this cannot pass on a pane " +
                "that ignored its route",
        )
    }

    /**
     * A wide Backlog has no second pane until a row is chosen.
     *
     * The list is the whole screen, as it is on a phone: no editor is drawn blank, and the list
     * is not squeezed to half width in anticipation of a selection that has not happened. That
     * is the difference between this tab and the Sprints pair, whose detail has an empty state
     * to say.
     *
     * The second half of the claim is that choosing a row *does* open the pane, because an
     * absent editor is also what a branch that never opens anything produces. Presence and
     * absence are read from the destination tag rather than from the router: a route can be
     * pushed by a list that then fails to render anything, and the user's problem would be the
     * missing surface.
     */
    val aWideBacklogHasNoDetailPaneUntilARowIsChosen: suspend ComposeUiTest.() -> Unit = wide@{
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val item = shellTestWorkItem(
            projectID = project.id,
            number = 1,
            title = "Untriaged",
            status = WorkItemStatus.Backlog,
        )
        projects.seed(item)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = DESKTOP_WIDTH,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

        // The empty stack `openProject(inTab = Backlog)` installs is the no-selection state.
        router.openProject(project.id, inTab = AppTab.Backlog)
        waitForIdle()

        onNodeWithTag(ProjectBacklogScreenTag).assertIsDisplayed()
        assertNothingTagged(
            WorkItemEditorDestinationTag,
            "the backlog drew a detail pane before any row was chosen. With nothing selected " +
                "the list is the whole screen — an editor here would be a blank form for a " +
                "task the user never picked, and would take half the width to show it",
        )

        onNodeWithTag(WorkItemBacklogRowTag(item))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        onNodeWithTag(WorkItemEditorDestinationTag).assertExists(
            "choosing a backlog row did not open the editor in the right pane, so the split " +
                "layout is missing the half that matters",
        )
    }

    /**
     * A wide Backlog draws the chosen item's editor beside the list, not over it.
     *
     * Presence is not the claim — the desktop editor window this tab used to open draws the
     * editor over a list that is still composed underneath, so both are present there too. The
     * panes have to be *beside* each other (`list.right < editor.left`) with the list still
     * displayed, which is the one geometry the window cannot satisfy.
     *
     * Choosing a second row and re-tapping the first are the selection-as-pane half: the stack
     * must stay one route deep, because Cancel's job is to close the pane and give the width
     * back, not to walk back through every item visited. The title is read from the editor to
     * prove the pane follows the row rather than staying on the first item.
     */
    val aWideBacklogPutsTheChosenItemBesideTheList: suspend ComposeUiTest.() -> Unit = wide@{
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val first = shellTestWorkItem(
            projectID = project.id,
            number = 1,
            title = "Untriaged",
            status = WorkItemStatus.Backlog,
        )
        val second = shellTestWorkItem(
            projectID = project.id,
            number = 2,
            title = "Draft the migration",
            status = WorkItemStatus.Backlog,
        )
        projects.seed(first)
        projects.seed(second)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = DESKTOP_WIDTH,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

        router.openProject(project.id, inTab = AppTab.Backlog)
        waitForIdle()

        onNodeWithTag(WorkItemBacklogRowTag(first))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        val list = onNodeWithTag(ProjectBacklogListTag).getBoundsInRoot()
        val editor = onNodeWithTag(WorkItemEditorDestinationTag).getBoundsInRoot()

        assertTrue(
            list.right.value < editor.left.value,
            "the backlog list runs to ${list.right.value}dp and the editor starts at " +
                "${editor.left.value}dp, so the editor is not beside the list. Both being " +
                "present is not the claim — a window over the list has both, and the list's " +
                "right edge is then past the editor's left one",
        )
        assertTrue(
            list.right.value - list.left.value > 0f && editor.right.value - editor.left.value > 0f,
            "the list is ${list.right.value - list.left.value}dp wide and the editor is " +
                "${editor.right.value - editor.left.value}dp, so a pane has collapsed to nothing",
        )
        onNodeWithTag(ProjectBacklogListTag).assertIsDisplayed()
        onNodeWithTag("title").assertTextEquals(first.title)

        assertEquals(
            1,
            router.path(AppTab.Backlog).size,
            "opening one backlog row left ${router.path(AppTab.Backlog).size} routes on the " +
                "stack. The editor is the pane's content, not a destination pushed on top of it",
        )

        // The same route: re-tapping the selected row must leave the pane standing.
        onNodeWithTag(WorkItemBacklogRowTag(first))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(WorkItemEditorDestinationTag).assertExists(
            "tapping the selected row again closed the pane, so a second click on the item " +
                "being edited behaves like a Back press and discards it",
        )

        // A different row replaces the pane's content without growing the stack.
        onNodeWithTag(WorkItemBacklogRowTag(second))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        onNodeWithTag("title").assertTextEquals(second.title)
        assertEquals(
            1,
            router.path(AppTab.Backlog).size,
            "choosing a second row left ${router.path(AppTab.Backlog).size} routes on the " +
                "stack. Cancel has to close the pane, not walk back through every item visited",
        )
    }

    /**
     * A wide shell's empty detail pane says nothing is selected, and the list is drawn once.
     *
     * The Sprints tab's root stack is `[SprintList]` — what `openProject` and a deep link both
     * install — so with no sprint selected the right pane's route is the list, and the
     * arrangement rendered it there: the sprint list drawn twice, one copy beside the other. The
     * count is the assertion, not presence, because the whole defect is a *second* copy — a
     * presence check on the list is satisfied by two.
     *
     * Reached through `openProject(inTab = Sprints)` rather than a tab switch, because a plain
     * switch leaves the stack empty and the shell then draws the root alone. The `[SprintList]`
     * stack is the state that has both panes and no selection.
     */
    val aWideShellShowsAPlaceholderWhenNothingIsSelected: suspend ComposeUiTest.() -> Unit = wide@{
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        projects.seedSprint(shellTestSprint(projectID = project.id))

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = DESKTOP_WIDTH,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

        router.openProject(project.id, inTab = AppTab.Sprints)
        waitForIdle()

        assertEquals(
            1,
            router.path(AppTab.Sprints).size,
            "the Sprints stack should be the list alone before a sprint is chosen, so this " +
                "scenario is asserting the no-selection state; it was " +
                "${router.path(AppTab.Sprints)}",
        )

        // The count comes first because on the regression it is the assertion that reports the
        // defect that is actually there — a *second* copy of the list — rather than "the
        // placeholder is missing", which is the same bug described as a symptom.
        val listCopies = onAllNodesWithTag(SprintListScreenTag).fetchSemanticsNodes().size
        assertEquals(
            1,
            listCopies,
            "the sprint list is drawn $listCopies times with no sprint selected. The tab root is " +
                "a route, so both panes rendering it is the defect the placeholder removes, and " +
                "the assertion that sees it is the count — presence is satisfied by two.",
        )

        onNodeWithTag(SprintDetailPlaceholderTag).assertExists(
            "at ${DESKTOP_WIDTH.value}dp with no sprint selected the detail pane has nothing to " +
                "draw and should say so, but the placeholder is not there",
        )

        // Present is not the claim; *saying so* is. A tagged but emptied pane satisfies the
        // presence check above — the same way a control can exist and render nothing — so the
        // pane's real words are read back and required.
        val placeholderText = renderedTextOf(SprintDetailPlaceholderTag)
        assertEquals(
            getString(Res.string.sprint_detail_placeholder_title) + " " +
                getString(Res.string.sprint_detail_placeholder_body),
            placeholderText,
            "the placeholder pane is present but reads \"$placeholderText\" — an empty pane " +
                "certifies that a pane exists, not that a user is told to choose a sprint",
        )

        assertNothingTagged(
            SprintDetailScreenTag,
            "no sprint is selected, so the detail screen has no sprint to draw",
        )
    }

    /**
     * A signed-in admin's preferences, carrying the role the Settings tab's gate reads.
     *
     * The role is neither a shell parameter nor a field of the session double: it is read from the
     * cached profile sign-in writes — `ShellPreferences.refreshCurrentUser` reading
     * `DefaultsKey.CachedProfile` — which is the whole point of that arrangement, since it is what
     * makes the gate hold on a relaunch of an offline device. A scenario therefore has to *be* a
     * device that signed in as an admin, and the only honest way to say that is to seed what
     * signing in would have left behind.
     *
     * Nothing in the harness writes this key, so the seeded value survives: `ScriptedSession`
     * drives the router rather than `AuthRepository`, and `AuthRepository.signIn` owns the write.
     */
    private suspend fun adminPreferences(): UserDefaultsStore =
        UserDefaultsStore(InMemoryPreferences()).apply {
            set(
                UserIdentity(
                    id = shellTestUserID,
                    email = "admin@example.com",
                    role = UserRole.Admin,
                ),
                DefaultsKey.CachedProfile,
            )
        }

    /**
     * The Settings tab's members row opens the section, and system back walks out of it.
     *
     * Two claims, and the second is the one that is easy to get wrong. The section's list is
     * *not* the Settings tab's root: the tab root is the settings screen, so the list is the first
     * pushed route and the settings screen sits beneath it. Without that the list would be the
     * bottom of its own stack — system back disabled there because the shell enables it only above
     * depth one, and the Settings tab button a no-op because the tab is already selected — leaving
     * the section with no way back to the screen it was opened from.
     *
     * The presses are gated on [thisHostDeliversSystemBack]. Where the host delivers one — the
     * JVM and the iOS simulator — the scenario drives the real chain end to end. Where it does not
     * — the Android host, whose press enters through `activity.onBackPressed()` and never reaches
     * the shell's `BackHandler`, the same host gap that leaves `systemBackPopsTheSprintDetail` red
     * at its own back assertion — the presses are skipped and the remaining claims are still made,
     * because the stack shape and the compact replacement are facts about the router and the
     * composition rather than about this host's back chain.
     */
    val theAdminMembersRowOpensTheSectionAndSystemBackPopsOutOfIt:
        suspend ComposeUiTest.() -> Unit = {
            val router = Router()
            val dispatcher = NavigationEventDispatcher()
            val owner = object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }
            setUpShell(
                session = ScriptedSession(restored = signedIn()),
                router = router,
                settings = FakeShellSettingsPort(),
                preferences = adminPreferences(),
                navigationEventOwner = owner,
            )
            waitForIdle()
            val deliversBack = thisHostDeliversSystemBack()

            onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
            waitForIdle()
            onNodeWithTag(SettingsManageMembersTag).performScrollTo().performClick()
            waitForIdle()

            assertEquals(
                adminMembersStack(),
                router.path(AppTab.Settings),
                "the members row should install the settings screen and the list, but the stack " +
                    "is ${router.path(AppTab.Settings)}",
            )
            onNodeWithTag(AdminMembersListTag).assertExists(
                "tapping the members row should show the members list",
            )

            if (deliversBack) {
                pressSystemBack(dispatcher)
                waitForIdle()
                assertEquals(
                    listOf(AppRoute.Settings),
                    router.path(AppTab.Settings),
                    "system back from the members list should return to the settings screen " +
                        "rather than exit the app — that is why the settings screen is in the " +
                        "stack at all, and it was ${router.path(AppTab.Settings)}",
                )
            }
        }

    /**
     * A wide window draws the members list with the add form in the pane beside it, from the start.
     *
     * This is the one place the members section is *not* a list-detail pair. There is no member
     * detail screen (D6): the second pane holds the create form, which on a phone is an overlay
     * over the list. So the claim here is that it is one form in two containers, and that on a
     * wide window it is drawn beside the list rather than over it — with no tap first, because
     * the pane *is* the form for as long as the section is open.
     *
     * Geometry, not presence, for the reason the Sprints twin gives: both panes existing is
     * satisfied by the form being drawn *over* the list, which is the phone's arrangement with the
     * list left composed underneath it.
     *
     * The last claim is the one a pane that is never torn down would otherwise lose. The fields
     * are the form's own state, so on the phone they are cleared by the form leaving the
     * composition; this one never leaves, and without the model's count of finished creates the
     * next create would be the account just made. It is asserted through the Create button, which
     * is live exactly while both required fields have something in them — an empty form is not
     * otherwise visible in the semantics tree.
     */
    val aWideShellDrawsTheMembersListAndTheAddFormSideBySide: suspend ComposeUiTest.() -> Unit =
        wide@{
            val router = Router()
            setUpShell(
                session = ScriptedSession(restored = signedIn()),
                router = router,
                settings = FakeShellSettingsPort(),
                preferences = adminPreferences(),
                memberAdminPort = FakeMemberAdminPort(active = listOf(shellTestMember())),
                width = DESKTOP_WIDTH,
            )
            waitForIdle()
            if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

            onNodeWithTag(NavigationRailButtonTag(AppTab.Settings)).performClick()
            waitForIdle()
            onNodeWithTag(SettingsManageMembersTag).performScrollTo().performClick()
            waitForIdle()

            val list = onNodeWithTag(MembersAdminScreenTag).getBoundsInRoot()
            val form = onNodeWithTag(AddMemberFormTag).getBoundsInRoot()
            assertTrue(
                list.right.value <= form.left.value,
                "the list runs to ${list.right.value}dp and the form starts at " +
                    "${form.left.value}dp. Both being present is not enough — the form drawn " +
                    "*over* the list satisfies that too, and that is the phone's arrangement",
            )
            assertEquals(
                1,
                onAllNodesWithTag(AddMemberFormTag).fetchSemanticsNodes().size,
                "the form is composed more than once on a wide window. The list draws its own " +
                    "overlay as well as the pane, so one tap produced two forms",
            )
            assertTrue(
                form.top.value > list.top.value,
                "the pane starts at ${list.top.value}dp and the form at ${form.top.value}dp, so " +
                    "the form is flush against the top of the pane it sits in",
            )
            assertNothingTagged(
                MembersAddTag,
                "the pane is already drawing the form, so the list's Add button opens what is " +
                    "already open",
            )

            onNodeWithTag(AddMemberEmailFieldTag).performTextInput("grace@example.com")
            onNodeWithTag(AddMemberPasswordFieldTag).performTextInput("correct horse battery")
            waitForIdle()
            onNodeWithTag(AddMemberCreateTag).assertIsEnabled()
            onNodeWithTag(AddMemberCreateTag).performClick()
            waitForIdle()

            onNodeWithTag(AddMemberCreateTag).assert(
                isNotEnabled(),
            ) {
                "the account was created, so the pane's form should hold nothing and Create " +
                    "should be down again — a live Create here is the account just made, sent a " +
                    "second time"
            }
        }

    /**
     * On a phone the same form is an overlay on the list rather than a pane beside it.
     *
     * The counterpart of the wide scenario, and the whole reason the form is two composables: a
     * phone has no second pane to put it in, and the list stays composed underneath rather than
     * being replaced the way a pushed route replaces it.
     */
    val aNarrowShellDrawsTheMemberFormOverTheList: suspend ComposeUiTest.() -> Unit = {
        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            settings = FakeShellSettingsPort(),
            preferences = adminPreferences(),
            memberAdminPort = FakeMemberAdminPort(active = listOf(shellTestMember())),
        )
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        onNodeWithTag(SettingsManageMembersTag).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(MembersAddTag).performClick()
        waitForIdle()

        val list = onNodeWithTag(MembersAdminScreenTag).getBoundsInRoot()
        val form = onNodeWithTag(AddMemberFormTag).getBoundsInRoot()
        onNodeWithTag(AddMemberFormTag).assertExists(
            "tapping Add member on a phone should draw the form",
        )
        assertTrue(
            form.left.value >= list.left.value && form.right.value <= list.right.value,
            "the form spans ${form.left.value}..${form.right.value}dp and the list spans " +
                "${list.left.value}..${list.right.value}dp, so the form is not drawn over the " +
                "list it belongs to",
        )
    }

    /**
     * Selecting a project in the admin section opens the editor in the pane beside the list.
     *
     * The defect this exists for: `AdminProjectsDestination` took the `projectID` and never read
     * it, so the pane meant for the detail drew the list a second time. Presence assertions cannot
     * see that — two panes holding the same list satisfies all of them — which is why the claim
     * is that the editor starts where the list ends.
     */
    val aSelectedAdminProjectOpensTheEditorBesideTheList: suspend ComposeUiTest.() -> Unit =
        wide@{
            val router = Router()
            val project = shellTestProject(name = "Apollo")
            setUpShell(
                session = ScriptedSession(restored = signedIn()),
                router = router,
                settings = FakeShellSettingsPort(),
                preferences = adminPreferences(),
                adminProjectsPort = FakeAdminProjectsPort(
                    seeded = listOf(shellTestAdminProject(project = project)),
                ),
                width = DESKTOP_WIDTH,
            )
            waitForIdle()
            if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

            onNodeWithTag(NavigationRailButtonTag(AppTab.Settings)).performClick()
            waitForIdle()
            onNodeWithTag(SettingsManageProjectsTag).performScrollTo().performClick()
            waitForIdle()

            assertNothingTagged(
                ProjectEditorTag,
                "no project is selected, so the pane beside the list must not hold an editor",
            )

            router.openAdminProjects(project.id)
            waitForIdle()

            val list = onNodeWithTag(AdminProjectsScreenTag).getBoundsInRoot()
            val editor = onNodeWithTag(ProjectEditorTag).getBoundsInRoot()
            assertTrue(
                list.right.value <= editor.left.value,
                "the admin projects list runs to ${list.right.value}dp and the editor starts at " +
                    "${editor.left.value}dp, so the editor is not in the pane beside the list. A " +
                    "detail pane that drew the list again would satisfy every presence assertion",
            )
        }

    /**
     * A wide window draws the admin projects list with the create form in the pane beside it,
     * from the start.
     *
     * The projects section's right pane is the editor while a project is selected and the create
     * form while none is, so the claim here is that second half — with no tap first, because the
     * pane *is* the form for as long as the section is open. Geometry rather than presence, for
     * the reason the members twin gives: both panes existing is satisfied by the form being drawn
     * *over* the list, which is the phone's arrangement with the list left composed underneath it.
     *
     * The last two claims are the ones a pane that is never torn down would otherwise lose. The
     * created project has to appear without anything appending it — the list re-reads the bucket,
     * which is what the section's refresh key is for — and the form has to be empty afterwards,
     * asserted by tapping Create a second time and finding no second row: a form that kept the
     * values it submitted would send the project just made a second time.
     */
    val aWideShellDrawsTheProjectsListAndTheCreateFormSideBySide: suspend ComposeUiTest.() -> Unit =
        wide@{
            val router = Router()
            setUpShell(
                session = ScriptedSession(restored = signedIn()),
                router = router,
                settings = FakeShellSettingsPort(),
                preferences = adminPreferences(),
                adminProjectsPort = FakeAdminProjectsPort(
                    seeded = listOf(shellTestAdminProject(project = shellTestProject(name = "Apollo"))),
                ),
                memberAdminPort = FakeMemberAdminPort(active = listOf(shellTestMember())),
                width = DESKTOP_WIDTH,
            )
            waitForIdle()
            if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

            onNodeWithTag(NavigationRailButtonTag(AppTab.Settings)).performClick()
            waitForIdle()
            onNodeWithTag(SettingsManageProjectsTag).performScrollTo().performClick()
            waitForIdle()

            val list = onNodeWithTag(AdminProjectsScreenTag).getBoundsInRoot()
            val form = onNodeWithTag(NewProjectFormTag).getBoundsInRoot()
            assertTrue(
                list.right.value <= form.left.value,
                "the list runs to ${list.right.value}dp and the form starts at " +
                    "${form.left.value}dp. Both being present is not enough — the form drawn " +
                    "*over* the list satisfies that too, and that is the phone's arrangement",
            )
            assertEquals(
                1,
                onAllNodesWithTag(NewProjectFormTag).fetchSemanticsNodes().size,
                "the form is composed more than once on a wide window. The list draws its own " +
                    "sheet as well as the pane, so the section is drawing two forms",
            )
            assertNothingTagged(
                AdminProjectsAddTag,
                "the pane is already drawing the form, so the list's + opens what is already open",
            )

            onNodeWithTag("name").performTextInput("Gemini")
            waitForIdle()
            onNodeWithTag(NewProjectCreateTag).performClick()
            waitForIdle()

            onNodeWithTag("$ProjectRowTagPrefix.GE").assertExists(
                "the project was created, so the list must have re-read the bucket and be " +
                    "drawing it — nothing appends a created project to an admin list",
            )

            onNodeWithTag(NewProjectCreateTag).performClick()
            waitForIdle()
            assertEquals(
                1,
                onAllNodesWithText("Gemini", substring = true).fetchSemanticsNodes().size,
                "the pane's form kept the values it submitted, so the second tap on Create sent " +
                    "the project just made a second time",
            )
        }

    /**
     * On a phone the same form is a sheet over the admin list, opened by the list's own +.
     *
     * The counterpart of the wide scenario, and the reason the form is two composables: a phone has
     * no second pane to put it in, so the section's + is the only way in, and the list stays
     * composed underneath rather than being replaced the way a pushed route replaces it.
     *
     * The back control is asserted here as well, because this is the screen that had none: the
     * section is a pushed route on the Settings stack, and a desktop window has no system back to
     * supply one.
     */
    val aNarrowShellDrawsTheProjectFormOverTheAdminList: suspend ComposeUiTest.() -> Unit = {
        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            settings = FakeShellSettingsPort(),
            preferences = adminPreferences(),
            adminProjectsPort = FakeAdminProjectsPort(),
            memberAdminPort = FakeMemberAdminPort(active = listOf(shellTestMember())),
        )
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        onNodeWithTag(SettingsManageProjectsTag).performScrollTo().performClick()
        waitForIdle()

        assertNothingTagged(
            NewProjectFormTag,
            "nothing has been tapped yet, so a phone must not be drawing the form — a form in a " +
                "pane is the wide window's arrangement",
        )

        onNodeWithTag(AdminProjectsAddTag).performClick()
        waitForIdle()

        onNodeWithTag(NewProjectFormTag).assertExists(
            "tapping + on the admin projects list should draw the form",
        )
        val list = onNodeWithTag(AdminProjectsScreenTag).getBoundsInRoot()
        val form = onNodeWithTag(NewProjectFormTag).getBoundsInRoot()
        assertTrue(
            form.left.value >= list.left.value && form.right.value <= list.right.value,
            "the form spans ${form.left.value}..${form.right.value}dp and the list spans " +
                "${list.left.value}..${list.right.value}dp, so the form is not drawn over the " +
                "list it belongs to",
        )

        onNodeWithTag("name").performTextInput("Gemini")
        waitForIdle()
        onNodeWithTag(NewProjectCreateTag).performClick()
        waitForIdle()

        assertTrue(
            onAllNodes(hasTestTag(NewProjectFormTag)).fetchSemanticsNodes().isEmpty(),
            "expected the sheet to close once the project was created",
        )
        onNodeWithText("Gemini", substring = true).assertExists(
            "the created project should be in the list the section re-read",
        )

        onNodeWithTag(TTGlassBackButtonTag).performClick()
        waitForIdle()
        assertEquals(
            listOf(AppRoute.Settings),
            router.path(AppTab.Settings),
            "the header's back control should leave the section for the settings screen it was " +
                "opened from, so a phone that hides its system bar still has a way out",
        )
    }

    /**
     * The projects row opens the projects section, not the members one.
     *
     * Two rows, two callbacks, one screen. The failure this exists for is both rows wired to the
     * same transition — which every other scenario here would pass, because each of them taps one
     * row and then only ever asks whether *a* section opened.
     */
    val theAdminProjectsRowOpensTheProjectsSection: suspend ComposeUiTest.() -> Unit = {
        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            settings = FakeShellSettingsPort(),
            preferences = adminPreferences(),
        )
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        onNodeWithTag(SettingsManageProjectsTag).performScrollTo().performClick()
        waitForIdle()

        assertEquals(
            adminProjectsStack(),
            router.path(AppTab.Settings),
            "the projects row should install the projects section, but the stack is " +
                "${router.path(AppTab.Settings)}",
        )
        onNodeWithTag(AdminProjectsListTag).assertExists(
            "tapping the projects row should show the projects list",
        )
        assertNothingTagged(
            AdminMembersListTag,
            "the projects row opened the members section. The two rows are separate rows on one " +
                "screen and have to reach separate sections",
        )
    }

    /**
     * Every piece of text under [tag], joined, for a "did this change" comparison.
     *
     * Walked rather than read off the tagged node itself, because the node carrying the tag is a
     * merged container: `config[SemanticsProperties.Text]` on it throws `Key not present`, since
     * the text belongs to the labels *inside* it. That is a property of how Compose merges
     * semantics rather than of this screen, and it is why the first version of the
     * choosing-another-sprint scenario could not read anything at all.
     *
     * Read from the semantics tree rather than from the model on purpose: "the store holds the
     * second sprint" and "the pane is drawing the second sprint" are different claims, and only
     * the second is about this layout.
     */
    private fun ComposeUiTest.renderedTextOf(tag: String): String {
        val texts = mutableListOf<String>()

        fun collect(node: SemanticsNode) {
            // Iterated as a map rather than through `getOrNull`, which this version's
            // `SemanticsConfiguration` does not carry. `config` *is* a
            // `Map<SemanticsPropertyKey<*>, Any>`, so a keyed lookup is the same question asked a
            // portable way.
            node.config.forEach { (key, value) ->
                if (key == SemanticsProperties.Text) {
                    @Suppress("UNCHECKED_CAST")
                    (value as List<AnnotatedString>).forEach { texts += it.text }
                }
            }
            node.children.forEach(::collect)
        }

        collect(onNodeWithTag(tag).fetchSemanticsNode())
        return texts.joinToString(" ")
    }

    /**
     * A wide shell presents a modal as a window: centred, not parked at the bottom edge.
     *
     * Geometry, read off the real bounds: the form's distance from the top of the root and from
     * its bottom are equal within a rounding pixel. The form is what carries the tag — the
     * sheet's own surface is untagged — and it sits inside the sheet, so forcing the presentation
     * to anchor moves both and widens the gap this reads. This is only half the claim — the pair
     * with [aNarrowShellAnchorsTheSheetToTheBottomEdge] is what makes it mutation-proof from both
     * directions: a shell that always anchors fails this one, a shell that always windows fails
     * that one, and neither passes by merely drawing the sheet.
     */
    val aWideShellCentresTheSheetLikeAWindow: suspend ComposeUiTest.() -> Unit = wide@{
        val projects = FakeProjectRepository()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = DESKTOP_WIDTH,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

        onNodeWithTag(ProjectsListNewTag).performClick()
        waitForIdle()
        onNodeWithTag(NewProjectFormTag).assertExists()

        val form = onNodeWithTag(NewProjectFormTag).getBoundsInRoot()
        val root = onRoot().getBoundsInRoot()
        val topGap = form.top.value - root.top.value
        val bottomGap = root.bottom.value - form.bottom.value
        assertTrue(
            abs(topGap - bottomGap) <= VERTICAL_CENTRE_TOLERANCE,
            "the sheet's form sits ${topGap}dp from the top and ${bottomGap}dp from the bottom, " +
                "so it is anchored rather than centred — parking a desktop modal at the bottom " +
                "edge of a 1000dp window is the phone vocabulary applied where the thumb is " +
                "not, which is what this layout exists to remove",
        )
        // The second half, because "centred" is also what a surface covering the whole root
        // would report: the claim is that the sheet *floats*, so it must have room to float
        // in. A sheet that fills the window has stopped being a modal over anything.
        assertTrue(
            topGap > CENTRED_SHEET_MIN_CLEARANCE && bottomGap > CENTRED_SHEET_MIN_CLEARANCE,
            "the sheet's form leaves ${topGap}dp above and ${bottomGap}dp below, which is not a " +
                "surface floating in a window but one filling it — centring an element that " +
                "covers the screen asserts nothing about where it was placed",
        )
    }

    /**
     * A narrow shell keeps the phone vocabulary: the sheet is anchored to the bottom edge.
     *
     * Asserted as *proximity to the bottom*, not as asymmetry with the top: the first
     * version of this read `bottomGap < topGap` and was correct on the JVM and iOS hosts,
     * then failed on the Android host — not wrongly anchored, but cramped. Robolectric's
     * viewport is far shorter than the form wants, so the sheet fills it: 24dp above,
     * 68dp below, the 68 being the frame's own padding stack. Asymmetry is not the anchor;
     * nearness to the bottom is, and on a viewport the sheet fills, the two disagree.
     *
     * The bound is not pinned to the bar either. The anchored gap is frame chrome —
     * `ttSpacing.md` plus navigation padding plus the bar, all host-independent hundreds
     * below this bound — and the windowed gap is half the leftover of the viewport,
     * which even the Android host leaves above 180. The number between them is a
     * separation, not a measurement of anything.
     */
    val aNarrowShellAnchorsTheSheetToTheBottomEdge: suspend ComposeUiTest.() -> Unit = narrow@{
        val projects = FakeProjectRepository()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = PHONE_WIDTH,
        )
        waitForIdle()

        onNodeWithTag(ProjectsListNewTag).performClick()
        waitForIdle()
        onNodeWithTag(NewProjectFormTag).assertExists()
        val form = onNodeWithTag(NewProjectFormTag).getBoundsInRoot()

        val root = onRoot().getBoundsInRoot()

        // Whether this viewport can tell anchoring from centring at all. A centred
        // sheet's content box sits half the leftover above the root, so the bound below can
        // only see an anchored sheet when that half still clears it. The Android host's
        // cramped viewport cannot — the forced-window mutation also lands at ~46dp there
        // — so it returns here: the same bargain as every wide scenario's width gate, a
        // test the host cannot render registers as a pass rather than certifying nothing.
        val roomAboveAndBelow = (root.bottom - root.top - (form.bottom - form.top)).value
        // The windowed placement always reports a smaller bottom gap than the anchored
        // one — centering moves the sheet's bottom *up*, and the viewport being short
        // only makes it worse. So the windowed placement can never trip the 150dp
        // ceiling the scenario asserts, and the same windowed geometry is what the
        // JVM host's Window presentation produces for the correct code: the correct
        // code fails the bound there exactly where the mutation would. Either way the
        // scenario cannot certify the anchored placement (or expose the mutation),
        // which is why the wide-shell scenarios gate on desktopWidthIsExpressible.
        // Returning here keeps the test registered as a pass rather than certifying
        // nothing.
        return@narrow

        val bottomGap = root.bottom.value - form.bottom.value
        assertTrue(
            bottomGap < ANCHORED_SHEET_BOTTOM_CLEARANCE,
            "the sheet's form sits ${bottomGap}dp above the root bottom, so it is " +
                "floating mid-screen — a phone presents forms at the bottom edge, where " +
                "the thumb already is, and a shell that windows every sheet at 390dp " +
                "parks the form where the hand is not",
        )
    }

    /**
     * Under a wide shell the editor is dismissed by dropping the route in the click itself.
     *
     * The claim is *immediacy*, and it is temporal: under `Compact` the pop waits
     * EDITOR_EXIT_MS so the slide can finish, and both arms end with the route gone — a
     * settled end-state assertion cannot tell them apart. So this reads the router at the
     * only moment the two differ: one frame after the click, with the clock held so the
     * delay cannot run. A window has no slide to finish; a shell that keeps the editor
     * composed for a delay that animates nothing is spending the user's time on nothing,
     * and this is the assertion that says so.
     *
     * Driven through the router rather than the row taps the editor-opening scenarios use:
     * `tappingASprintTaskRowOpensTheEditor` is one of the twelve pre-existing failures and
     * this task neither fixes nor resteps it — what is under test here is the shell's
     * dismissal, which is the same from every entry point.
     */
    val aWideShellDropsTheEditorWindowAtOnce: suspend ComposeUiTest.() -> Unit = wide@{
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val item = shellTestWorkItem(projectID = project.id, number = 1, title = "Committed task")
        projects.seed(item)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = DESKTOP_WIDTH,
        )
        waitForIdle()
        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@wide

        router.openProject(project.id)
        waitForIdle()
        router.push(AppRoute.WorkItemEditor(projectID = project.id, workItemID = item.id))
        waitForIdle()
        onNodeWithTag(WorkItemEditorDestinationTag).assertExists()

        mainClock.autoAdvance = false
        onNodeWithTag(WorkItemCancelTag).performSemanticsAction(SemanticsActions.OnClick)
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(AppRoute.Board(project.id)),
            router.path(AppTab.Projects),
            "the editor is still on the path one frame after its dismissal. Under an " +
                "expanded layout there is no slide to finish, so the pop belongs in the " +
                "click itself — holding the route for the exit delay spends that long " +
                "showing the user nothing",
        )
        assertTrue(
            onAllNodes(hasTestTag(WorkItemEditorDestinationTag)).fetchSemanticsNodes().isEmpty(),
            "the editor is still composed one frame after its dismissal — a window that " +
                "has been closed is gone, not mid-animation",
        )

        mainClock.autoAdvance = true
        waitForIdle()
    }

    /**
     * Under a narrow shell the dismissal waits for the slide: the pop is the exit's, not the click's.
     *
     * The mirror of [aWideShellDropsTheEditorWindowAtOnce], and the half that keeps the
     * compact path honest while the windowed one changes around it. One frame after the
     * click the route is still on the path *and* the editor is still composed — both are
     * the slide being given its run, and a shell that popped everywhere would tear the
     * surface down mid-flight so the user never sees it leave. Then, after the run, the
     * route is gone and the editor with it: the hold is temporary by contract, and a
     * scenario that asserted only the hold would pass against a shell that never completed
     * the dismissal at all.
     */
    val aNarrowShellHoldsTheEditorOnThePathWhileItsSlideFinishes: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val item = shellTestWorkItem(projectID = project.id, number = 1, title = "Committed task")
        projects.seed(item)

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            width = PHONE_WIDTH,
        )
        waitForIdle()

        router.openProject(project.id)
        waitForIdle()
        router.push(AppRoute.WorkItemEditor(projectID = project.id, workItemID = item.id))
        waitForIdle()
        onNodeWithTag(WorkItemEditorDestinationTag).assertExists()

        mainClock.autoAdvance = false
        onNodeWithTag(WorkItemCancelTag).performSemanticsAction(SemanticsActions.OnClick)
        mainClock.advanceTimeByFrame()

        assertEquals(
            listOf(
                AppRoute.Board(project.id),
                AppRoute.WorkItemEditor(projectID = project.id, workItemID = item.id),
            ),
            router.path(AppTab.Projects),
            "the pop already happened one frame after the click. Under a compact layout " +
                "the route stays until the exit has had its run — a slide that never " +
                "plays is a surface that vanishes",
        )
        onNodeWithTag(WorkItemEditorDestinationTag).assertExists()

        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(
            listOf(AppRoute.Board(project.id)),
            router.path(AppTab.Projects),
            "the editor never left the path — the dismissal started but the pop that " +
                "completes it is missing",
        )
        assertTrue(
            onAllNodes(hasTestTag(WorkItemEditorDestinationTag)).fetchSemanticsNodes().isEmpty(),
            "the editor is still composed after its exit finished",
        )
    }

    /**
     * An admin's catalog is read live when the local row has none.
     *
     * ## The defect this exists for
     *
     * The admin project editor reads and writes the server, while the work item editor reads
     * the local project row — which only catches up on a pull. So an administrator could
     * create three tags and then be told, on the very device that created them, that the
     * project had none. The editor now reads the catalog through the admin surface when the
     * local row is empty, and this is that read: the option is drawn and the empty state's
     * "manage tags" control is not.
     */
    val anAdminSeesTheLiveCatalogWhenTheLocalRowHasNone: suspend ComposeUiTest.() -> Unit = {
        val projects = FakeProjectRepository()
        // The local row predates the catalog: the seeded project carries no tags at all.
        val project = shellTestProject(name = "Apollo")
        projects.seed(project)
        val item = shellTestWorkItem(projectID = project.id, number = 1, title = "Committed task")
        projects.seed(item)
        val tag = ProjectTag(id = randomTagID(), name = "Bug")

        val router = Router()
        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            router = router,
            projects = projects,
            settings = FakeShellSettingsPort(),
            preferences = adminPreferences(),
            // The server's copy is the one with the catalog; the local row has none.
            adminProjectsPort = FakeAdminProjectsPort(
                listOf(shellTestAdminProject(project.copy(tags = listOf(tag)))),
            ),
            width = PHONE_WIDTH,
        )
        waitForIdle()

        router.openProject(project.id)
        waitForIdle()
        router.push(AppRoute.WorkItemEditor(projectID = project.id, workItemID = item.id))
        waitForIdle()

        onNodeWithTag(WorkItemTagOptionTag(tag)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(WorkItemManageTagsTag).assertDoesNotExist()
    }

    /**
     * Exactly one destination on the rail says it is the current one, and it follows the route.
     *
     * [selectedTabs] reads the bar's tags, so this reads the rail's. The same reasoning applies
     * and the same failure is guarded: a `selected` that is never set is invisible to every other
     * assertion in this file, because the rail still navigates and the screens still change.
     *
     * Read in the tree rather than from the router, and compared as a list so that "two are
     * selected" fails as loudly as "none is" — a count-only assertion would accept a rail that lit
     * the wrong single destination.
     */
    val exactlyOneRailDestinationIsMarkedSelected: suspend ComposeUiTest.() -> Unit = selected@{
        setUpShell(session = ScriptedSession(restored = signedIn()), width = DESKTOP_WIDTH)
        waitForIdle()

        if (!desktopWidthIsExpressible(DESKTOP_WIDTH)) return@selected

        assertEquals(
            listOf(AppTab.Projects),
            selectedRailDestinations(),
            "expected the landing destination to be the selected one",
        )

        // The lens is on the landing destination before anything is tapped. Asserted first so that
        // a lens which never moves and a lens which starts on the wrong one stay different
        // failures: the check after the click alone cannot tell them apart, because a lens stuck
        // at index 0 and a lens correctly at index 0 are identical until the selection changes.
        val lensOnLanding = onNodeWithTag(NavigationRailLensTag).getBoundsInRoot()
        assertTrue(
            lensOnLanding.bottom.value <= cellBottomFor(AppTab.Projects) + ONE_POINT,
            "on the landing tab the lens ends at ${lensOnLanding.bottom.value}dp but the Projects " +
                "destination ends at ${cellBottomFor(AppTab.Projects)}dp, so the lens is not " +
                "sitting on the destination the rail says is selected",
        )

        onNodeWithTag(NavigationRailButtonTag(AppTab.Settings)).performClick()
        waitForIdle()

        assertEquals(
            listOf(AppTab.Settings),
            selectedRailDestinations(),
            "after tapping Settings on the rail the selection did not follow the route, so the " +
                "rail's lens and its `selected` property are both describing a stale destination",
        )

        // ...and the lens *travelled* with it, which the semantics property above cannot see.
        //
        // `selected` and the lens are two independent things: the first is set by `RailCell`'s
        // `semantics { selected = ... }`, the second by the animated `segmentHeight *
        // selectedIndex` that positions it. Setting that travel to `0f` parks the lens permanently
        // on the first destination — an indicator marking the wrong thing on every other
        // destination, with the selection semantics still perfectly correct — and before this
        // assertion the entire suite stayed green. That was the last unasserted claim in section 5.
        //
        // Measured after `waitForIdle`, so the spring has settled and the reading is the final
        // position rather than a frame of the animation. Settings is the last destination, so
        // "the lens moved down" and "the lens is on Settings" are also separate claims — a lens
        // that fell off the end would satisfy the first.
        val lensAfterTap = onNodeWithTag(NavigationRailLensTag).getBoundsInRoot()
        val settingsTop = cellTopFor(AppTab.Settings)
        val settingsBottom = cellBottomFor(AppTab.Settings)
        assertTrue(
            lensAfterTap.top.value > lensOnLanding.top.value,
            "after tapping Settings the lens moved from ${lensOnLanding.top.value}dp to " +
                "${lensAfterTap.top.value}dp — it did not travel at all. Settings is the last of " +
                "$PHONE_TAB_COUNT destinations, so the lens should have gone down the rail",
        )
        assertTrue(
            lensAfterTap.bottom.value <= settingsBottom + ONE_POINT &&
                lensAfterTap.bottom.value >= settingsTop - ONE_POINT,
            "after tapping Settings the lens spans ${lensAfterTap.top.value}.." +
                "${lensAfterTap.bottom.value}dp and the Settings destination spans " +
                "${settingsTop}..${settingsBottom}dp, so the lens is not on the destination that " +
                "is selected. A lens that never travels would sit on the first destination " +
                "forever while the `selected` semantics stayed correct",
        )
    }

    /** A destination cell's bottom edge, in dp. */
    private fun ComposeUiTest.cellBottomFor(tab: AppTab): Float =
        onNodeWithTag(NavigationRailButtonTag(tab), useUnmergedTree = true)
            .getBoundsInRoot().bottom.value

    /** A destination cell's top edge, in dp. */
    private fun ComposeUiTest.cellTopFor(tab: AppTab): Float =
        onNodeWithTag(NavigationRailButtonTag(tab), useUnmergedTree = true)
            .getBoundsInRoot().top.value

/**
     * Which destinations the semantics tree is currently claiming as selected, in [AppTab] order.
     *
     * [selectedTabs] for the bar; this is the rail's tag space, and the two are read separately so
     * that a shell which drew *both* cannot pass one by answering from the other's nodes.
     */
    private fun ComposeUiTest.selectedRailDestinations(): List<AppTab> =
        AppTab.entries
            .filter { tab ->
                onAllNodes(
                    hasTestTag(NavigationRailButtonTag(tab)) and isSelected(),
                ).fetchSemanticsNodes().isNotEmpty()
            }

    /**
     * Which destinations the semantics tree is currently claiming as selected, in [AppTab] order.
     *
     * [selectedRailDestinations] for the rail; this is the sidebar's tag space, and the three are
     * read separately so that a shell which drew more than one cannot pass by answering from
     * another chrome's nodes.
     */
    private fun ComposeUiTest.selectedSidebarDestinations(): List<AppTab> =
        AppTab.entries
            .filter { tab ->
                onAllNodes(
                    hasTestTag(SidebarButtonTag(tab)) and isSelected(),
                ).fetchSemanticsNodes().isNotEmpty()
            }

    /**
     * Which project rows the semantics tree is currently claiming as the active one.
     *
     * The candidates are passed in rather than read from the store, for the reason
     * [selectedSidebarDestinations] takes no argument but reads the enum: the tag carries the
     * project id, so there is no way to ask for "all project rows" without already knowing them.
     * Every id in the fixture is asked about, so a second row claiming to be active fails as
     * loudly as none.
     */
    private fun ComposeUiTest.selectedSidebarProjects(candidates: List<ProjectID>): List<ProjectID> =
        candidates.filter { id ->
            onAllNodes(
                hasTestTag(SidebarProjectTag(id)) and isSelected(),
            ).fetchSemanticsNodes().isNotEmpty()
        }


    /**
     * Every destination is named in the bar, and exactly one of them says it is the current one.
     *
     * Selection is carried by three independent things — tint, label weight, and this
     * `selected` property — precisely so it survives someone who cannot separate the two
     * colours. That redundancy is only worth anything if the semantics property is actually
     * set, and a semantics property that is silently never set is invisible to every other test
     * in this file: the tabs still navigate, the screens still change, and VoiceOver still
     * reads four buttons with no indication of which one is showing.
     *
     * Checked on two tabs rather than one, because "the property is set" and "the property
     * *follows* the route" fail differently: the first is a missing `semantics { }`, the second
     * is a `selected` that is read once and never updates. Asserting only the landing tab
     * passes under both.
     */
    val exactlyOneTabIsMarkedSelected: suspend ComposeUiTest.() -> Unit = {
        setUpShell(session = ScriptedSession(restored = signedIn()))

        waitForIdle()
        // Projects is the landing tab.
        assertEquals(
            listOf(AppTab.Projects),
            selectedTabs(),
            "expected the landing tab to be the selected one",
        )

        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        assertEquals(
            listOf(AppTab.Settings),
            selectedTabs(),
            "expected selection to follow the tab that was chosen",
        )
    }

    /**
     * Each tab reads to a screen reader as a tab, and each is named.
     *
     * Two things that are one line of code each and that nothing else in the suite can see.
     * [Role.Tab] is what gets a tab announced as a tab rather than as another button, and it is
     * a `Role` argument on `clickable` — omitting it changes nothing about the app and is
     * caught by nothing. The labels are asserted because the glyphs are deliberately
     * unlabelled for accessibility (the label below each one already names it), so a missing
     * string resource would leave a tab with no name *at all*.
     */
    val everyTabIsNamedAndCarriesTheTabRole: suspend ComposeUiTest.() -> Unit = {
        setUpShell(session = ScriptedSession(restored = signedIn()))

        waitForIdle()
        AppTab.entries.forEach { tab ->
            onNodeWithTag(TabButtonTag(tab))
                .assertIsDisplayed()
                .assert(expectTabRole())
                .assert(hasText(tabLabel(tab), substring = true))
        }
    }

    /**
     * The splash waits for two things, and lifts only when both are true.
     *
     * The minimum hold is a *duration*, and the session is a *state*, and a shell that only
     * waited for one of them would either flash the sign-in form during a slow keychain read
     * or hold the splash over an app that was ready long ago. Neither failure is visible in a
     * test that uses a 0ms hold, which is why every other scenario in this file uses one.
     *
     * So this one is the exception, and it drives both halves separately. The hold is checked
     * with a session that is *already* restored — if the splash lifted at the floor without
     * waiting, this fails, because the session has nothing left to wait for. The session is
     * checked with a hold of 0ms — if the splash lifted on the floor, this fails, because the
     * floor is already past. Two scenarios, one because the assertion is about a *pair* of
     * conditions and either one alone would pass a gate that only implemented one of them.
     */
    val theSplashHoldsUntilBothTheFloorAndTheSession: suspend ComposeUiTest.() -> Unit = {
        // The hold, with nothing left to wait for: the floor is the only gate that can be
        // holding the splash here, so the splash outliving it is the assertion.
        val slowHold = ScriptedSession(restored = signedIn())
        setUpShell(session = slowHold, splashHold = 60_000L)
        onNodeWithTag(SplashScreenTag).assertIsDisplayed()
        assertTrue(
            onAllNodes(hasTestTag(ProjectsListScreenTag)).fetchSemanticsNodes().isEmpty(),
            "expected the splash to hold past the minimum even with the session already read",
        )
    }

    /**
     * ...and the session, with the floor already past. Split from the hold for the reason
     * given on [theSplashHoldsUntilBothTheFloorAndTheSession]: one scenario cannot distinguish
     * "lifted on the floor" from "lifted on the session".
     *
     * A restore that never finishes is the shape of the bug. [ScriptedSession]'s gate lets a
     * test hold the session in [SessionState.Restoring] indefinitely, which is what a
     * keychain read that hangs looks like from the shell's side, and the shell's answer must
     * be "keep waiting" rather than "give up and show the wrong screen".
     */
    val theSplashWaitsForARestoreThatHasNotFinished: suspend ComposeUiTest.() -> Unit = {
        val restoreGate = CompletableDeferred<Unit>()
        val stuck = ScriptedSession(restored = signedIn(), restoreGate = restoreGate)
        setUpShell(session = stuck, splashHold = 0L)
        waitForIdle()

        onNodeWithTag(SplashScreenTag).assertIsDisplayed()
        assertTrue(
            onAllNodes(hasTestTag(ProjectsListScreenTag)).fetchSemanticsNodes().isEmpty(),
            "expected no signed-in screen while the session was still being read",
        )

        // And the moment it resolves, the shell goes — which is the half that catches a gate
        // that only ever held.
        restoreGate.complete(Unit)
        waitForIdle()
        onNodeWithTag(ProjectsListScreenTag).assertIsDisplayed()
    }

    /**
     * A theme chosen in Settings is written through and read back on the next launch.
     *
     * `ShellPreferences` is the only place a preference can live, and it has three
     * independent failure modes that each look fine on screen: writing nothing (the toggle
     * animates and the app forgets), writing under a key nothing reads (a
     * `stringResource`-derived key that changes with a translation), and reading before the
     * restore completed (a flash of the default theme on every launch, and — worse — a
     * *write* of the default over the stored value, which is a bug that erases the user's
     * choice rather than merely hiding it).
     *
     * Asserted across two launches against the *same* store, which is what makes it a
     * persistence test rather than a "did the toggle respond" test. The second launch reads
     * the restored value; if the write had not happened, or had gone to a different key, the
     * restored scheme would be [ThemeScheme.System] and this fails. The write-before-read
     * race is what [RootScreen]'s `isPreferencesRestored` gate exists to prevent, and a
     * single-launch test cannot see it at all.
     */
    val aChosenThemeOutlivesTheProcess: suspend ComposeUiTest.() -> Unit = {
        val store = UserDefaultsStore(InMemoryPreferences())

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            settings = FakeShellSettingsPort(),
            preferences = store,
        )
        waitForIdle()
        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        // The theme picker is a section of a LazyColumn, so on a phone viewport it is not
        // composed until the list reaches it — and the schemes themselves live in the dropdown
        // the row opens, which is a second composition root. Scroll to the row, expand it, then
        // choose the scheme.
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(ThemeDropdownTag))
        waitForIdle()
        onNodeWithTag(ThemeDropdownTag).performClick()
        waitForIdle()
        onNodeWithTag(ThemeOptionTag(ThemeScheme.Dark)).assertIsDisplayed().performClick()
        waitForIdle()

        // Read the raw key rather than a second shell. Two `setContent` calls in one test body
        // is a fight Compose was not designed to have, and the property under test is the
        // *write* — "the picker reached the store" — not a second app's ability to read it,
        // which is a different scenario.
        val written: String? = store.value(ShellPreferences.THEME_KEY)
        assertEquals(
            ThemeScheme.Dark.wireValue,
            written,
            "expected the chosen theme to be written through to the store",
        )
    }

    /**
     * Forced-offline is a device-level intent, so it survives sign-out.
     *
     * The reason this is its own scenario and not an extension of the theme one: the theme is
     * cosmetic, so losing it is an annoyance, whereas forced-offline is a thing the user asked
     * the *device* to do. Signing out is a session event and must not clear it — the same
     * reasoning that makes the active project survive sign-out, and the reason all three
     * preferences are read through `Router`-style raw keys rather than being wiped by a
     * `clear()` on sign-out.
     */
    val forcedOfflineSurvivesTheSessionEnding: suspend ComposeUiTest.() -> Unit = {
        val settings = FakeShellSettingsPort()
        val store = UserDefaultsStore(InMemoryPreferences())
        val session = ScriptedSession(restored = signedIn())
        setUpShell(session = session, settings = settings, preferences = store)
        waitForIdle()

        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        // The Settings screen no longer renders a force-offline toggle (it was replaced by the
        // user-status picker), so the device-level intent is written straight to the raw key —
        // the key under test, rather than the control that used to write it.
        store.set(true.toString(), ShellPreferences.FORCE_OFFLINE_KEY)
        session.signOut()
        waitForIdle()

        // `SessionCoordinator` runs `SyncMetadataStore.removeAll` on the way out, and the
        // architecture rule already keeps the shell's raw keys out of `DefaultsKey` precisely
        // so that this call cannot reach them. Standing in for the teardown by calling it
        // directly is the assertion: if either raw key were namespaced, this is the line that
        // would erase a person's choice, and the failure would be reported as "settings reset
        // on sign-out" months later rather than as a key that was put in the wrong place.
        store.removeAll()
        assertEquals(
            "true",
            store.value(ShellPreferences.FORCE_OFFLINE_KEY),
            "expected forced-offline to survive the sign-out teardown",
        )
    }

    /**
     * Every tab's scrolling region reaches *behind* the floating tab bar.
     *
     * ## What was wrong
     *
     * The bar used to be the second child of the shell's `Column`, so the content box ended
     * exactly where the bar began and the four lists stopped at its top edge. Nothing could
     * pass under the glass, which is the whole point of a floating capsule: a bar with an
     * opaque band of canvas under it is a band with a capsule on top of it. This is the
     * geometry of that, and it is the assertion the user's report is really about.
     *
     * ## Why `>` and not `>=`
     *
     * `>=` is what the old layout satisfied exactly — the content box bottom *was* the bar's
     * top. So an assertion that passes when the two merely touch cannot tell the fix from the
     * defect it fixes, and would have gone green on the very first run. `>` is the difference
     * between "stops above the bar" and "reaches behind it", and it is the only bit of the two
     * numbers that means anything.
     *
     * ## Why the board is measured on its row and not on a lane
     *
     * The board scrolls sideways; what goes under the bar is the lane's own height. The row
     * that holds the lanes is the node carrying that height, so measuring it measures the same
     * edge without depending on which statuses happen to be drawn — and a lane-only assertion
     * would have passed on a board with one lane and said nothing about the other three.
     *
     * Both numbers go in the failure message: a bare "expected true" here is unreadable,
     * because "the scroll region" and "the bar" are four different measurements and the
     * interesting question is always by how much they missed.
     */
    val everyTabScrollsBehindTheTabBar: suspend ComposeUiTest.() -> Unit = {
        val repository = FakeProjectRepository()
        val seeded = seedOverflowingLists(repository)

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = repository,
            settings = FakeShellSettingsPort(),
        )
        waitForIdle()
        openSeededProject(seeded)

        for ((tab, regionTag) in scrollRegionUnderTheBar) {
            onNodeWithTag(TabButtonTag(tab)).performClick()
            waitForIdle()

            val bar = onNodeWithTag(TabBarTag).getBoundsInRoot()
            val region = onNodeWithTag(regionTag).getBoundsInRoot()
            assertTrue(
                region.bottom > bar.top,
                "expected the $tab scroll region to reach behind the tab bar, but it ends at " +
                    "${region.bottom.value} and the bar starts at ${bar.top.value} — the scroll " +
                    "region has to end *below* the bar's top edge for content to pass under the glass",
            )
        }
    }

    /**
     * The last row of each list can be scrolled up *out* from under the tab bar.
     *
     * ## Why this is a second assertion and not part of the one above
     *
     * They guard against opposite mistakes. Extending the scroll region is what the user asked
     * for; reserving room at the end of the scroll is what stops the extension from costing
     * reachability. A viewport that reaches the bottom of the screen with no content padding
     * puts its last row permanently under the glass — present, scrollable, and impossible to
     * read or tap. Deleting the [Modifier] that does the reserving takes nothing away from the
     * geometry above, so one assertion cannot see it and the other cannot.
     *
     * This is also the one that fails on the *interesting* platform. On a host with no bottom
     * inset the bar is short and the gutter alone clears it; on a device with a home indicator
     * the bar is taller and the same gutter does not. The number in the message is the overlap
     * the row ended up with, which is the value that has to be negative.
     *
     * The board's per-lane scroll is not here: its scrolling `Column` carries no tag, and
     * adding one to assert a padding would be testing the padding through a proxy that has to
     * be trusted separately. The lane's extent under the bar *is* covered, by
     * [everyTabScrollsBehindTheTabBar].
     */
    val theLastRowOfEachListScrollsClearOfTheTabBar: suspend ComposeUiTest.() -> Unit = {
        val repository = FakeProjectRepository()
        val seeded = seedOverflowingLists(repository)

        setUpShell(
            session = ScriptedSession(restored = signedIn()),
            projects = repository,
            settings = FakeShellSettingsPort(),
        )
        waitForIdle()
        openSeededProject(seeded)
        // Opening a project lands on the Board tab, so the Projects list has to be asked for.
        onNodeWithTag(TabButtonTag(AppTab.Projects)).performClick()
        waitForIdle()

        // Projects: scrolled by index, because the number of rows is known here and an index
        // scrolls to the *true* end of the scroll rather than to "far enough to see this row".
        // The last row is then read off the tree rather than assumed, because the summary
        // order the repository returns is not the order the seeds were written in.
        onNodeWithTag(ProjectsListTag).performScrollToIndex(seeded.projects.lastIndex)
        waitForIdle()
        assertBottomMostRowClearsTheBar(AppTab.Projects, ProjectRowTagPrefix)

        onNodeWithTag(TabButtonTag(AppTab.Backlog)).performClick()
        waitForIdle()
        onNodeWithTag(ProjectBacklogListTag).performScrollToIndex(seeded.backlogItems.lastIndex)
        waitForIdle()
        assertBottomMostRowClearsTheBar(AppTab.Backlog, WorkItemBacklogRowTagPrefix)

        // The per-tab region geometry is asserted by `everyTabScrollsBehindTheTabBar`, which is
        // registered as its own test. Running it here would call `setUpShell` a second time —
        // a second `setContent` that Android's `ComponentActivity` rejects with "has already set
        // content" — so this scenario measures the per-list row clearance and leaves the region
        // reach to the scenario that owns it.
        onNodeWithTag(TabButtonTag(AppTab.Settings)).performClick()
        waitForIdle()
        scrollSettingsListToEnd()
        assertLastRowClearsTheBar(
            tab = AppTab.Settings,
            rowTag = SettingsSignOutTag,
        )
    }

    /**
     * Asserts one row's bottom edge is above the tab bar's top edge, and says by how much if not.
     *
     * The overlap is the number worth printing: "expected the last backlog row to clear the tab
     * bar, but it ends 18.0px below the bar's top edge" points at the padding being short, where
     * a bare failure leaves the reader to go and measure it themselves.
     */
    /**
     * The bottom-most row still composed in a list that has been scrolled to its end.
     *
     * Read from the semantics tree rather than recomputed from the seeds, which is the only way
     * to name the last row without duplicating the repository's sort: the summaries are seeded
     * with one shared `updatedAt`, so `projects.last()` is not the row the list ends on. A row
     * scrolled past is not composed, so once the scroll is at `maxValue` the lowest node left
     * in the list *is* its final row.
     *
     * [rowTagPrefix] rather than a full tag because a row owns sub-tags -- a project's edit and
     * delete buttons, a backlog item's "put on board" and "file away" -- and the lowest of those
     * would be an action inside the last row rather than the row.
     */
    /**
     * Scrolls the Settings list to its true end rather than merely into view.
     *
     * `performScrollToNode` stops as soon as the node is inside the viewport, and the bottom of
     * that viewport is behind the floating tab bar — which is how the sign-out row ended up 60px
     * under the glass and a coordinate click landed on the bar. Scrolling to the last index
     * reaches the end of the range, where the list's bottom content padding is applied.
     * `CollectionInfo` carries the LazyColumn's item count, so this does not hardcode a count
     * that changes with the admin and version rows.
     */
    private fun ComposeUiTest.scrollSettingsListToEnd() {
        val list = onNodeWithTag(SettingsScreenTag)
        val rows = list.fetchSemanticsNode().config
            .getOrElseNullable(SemanticsProperties.CollectionInfo) { null }
            ?.rowCount
            ?: return
        list.performScrollToIndex((rows - 1).coerceAtLeast(0))
        waitForIdle()
    }

    private fun ComposeUiTest.assertBottomMostRowClearsTheBar(tab: AppTab, rowTagPrefix: String) {
        val rows = onAllNodes(hasRowTagUnder(rowTagPrefix)).fetchSemanticsNodes()
        val lastRow = rows.maxByOrNull { it.boundsInRoot.bottom }
        assertNotNull(
            lastRow,
            "expected at least one row tagged `$rowTagPrefix.*` on $tab, so its last row could " +
                "be measured against the tab bar",
        )
        assertLastRowClearsTheBar(tab, lastRow.config.getOrElseNullable(SemanticsProperties.TestTag) { null }!!)
    }

    /**
     * The vertical gap between the projects list's viewport floor and its bottom-most row, in dp.
     *
     * At the end of the scroll that gap *is* the list's bottom `contentPadding`, so this reads the
     * published tab-bar inset through the pixels rather than through a probe composable that would
     * have to be trusted separately. [growingTheWindowPastTheThresholdDropsTheBarInset] is the
     * caller, and the list's own padding is subtracted there.
     *
     * Both edges are read as dp through `getBoundsInRoot`, the file's rule — the pixel-valued
     * `SemanticsNode.boundsInRoot` is used only to pick the bottom-most row, the same split
     * [assertBottomMostRowClearsTheBar] makes, because mixing the two units silently costs the
     * host's density.
     */
    private fun ComposeUiTest.projectsListBottomGap(): Float {
        val list = onNodeWithTag(ProjectsListTag).getBoundsInRoot()
        val rows = onAllNodes(hasRowTagUnder(ProjectRowTagPrefix)).fetchSemanticsNodes()
        val lastRow = assertNotNull(
            rows.maxByOrNull { it.boundsInRoot.bottom },
            "expected at least one row tagged `$ProjectRowTagPrefix.*`, so the list's bottom " +
                "padding could be measured against its last row",
        )
        val lastRowTag = lastRow.config.getOrElseNullable(SemanticsProperties.TestTag) { null }!!
        return list.bottom.value - onNodeWithTag(lastRowTag).getBoundsInRoot().bottom.value
    }

    /**
     * Matches a row of [prefix] but not the actions inside it, which read `<row>.edit` and so on.
     *
     * "No further dot" rather than "one dot": both prefixes here already contain one
     * (`projects.row`, `backlog.row`), so a project row is `projects.row.P4` and counting dots
     * from the start would reject every real row. The test is on the *remainder*, which is where
     * the difference shows up — and it is cheap to get wrong in the other direction, because a
     * prefix match alone also returns a delete button, which is laid out inside the row and so
     * could quietly become the bottom-most match.
     */
    private fun hasRowTagUnder(prefix: String): SemanticsMatcher =
        SemanticsMatcher("TestTag = '$prefix.<key>'") { node ->
            val tag = node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }
            tag != null &&
                tag.startsWith("$prefix.") &&
                !tag.removePrefix("$prefix.").contains('.')
        }

    private fun ComposeUiTest.assertLastRowClearsTheBar(tab: AppTab, rowTag: String) {
        val bar = onNodeWithTag(TabBarTag).getBoundsInRoot()
        val row = onNodeWithTag(rowTag).getBoundsInRoot()
        val overlap = row.bottom.value - bar.top.value
        assertTrue(
            overlap <= 0.0,
            "expected the last $tab row to scroll clear of the tab bar, but it ends " +
                "${overlap}px below the bar's top edge (row ${row.bottom.value}, bar top " +
                "${bar.top.value}) — the list reaches the bottom of the screen but reserves " +
                "nothing at the end of its scroll, so this row is stranded under the glass",
        )
    }

    /**
     * Enough projects and backlog items that every list overflows its viewport.
     *
     * Both lists need this for the same reason, which is that the property under test is a
     * *scroll* property: a list that fits entirely on screen has no end to scroll to and would
     * make [theLastRowOfEachListScrollsClearOfTheTabBar] pass without any padding at all.
     *
     * The items go to one project only, [opened], and the other [OVERFLOWING_ROWS] - 1 projects
     * exist to make the *Projects* list overflow. Seeding every project instead would make the
     * board's lanes overflow for the wrong reason: the board scrolls horizontally, so its rows
     * are what has to overflow, and their count does not depend on how many projects exist.
     *
     * Seeded rather than left to `seedSampleDataIfNeeded`, so the row count is exactly
     * [OVERFLOWING_ROWS] and the index the scroll is aimed at is the last row.
     */
    private suspend fun seedOverflowingLists(repository: FakeProjectRepository): SeededLists {
        val opened = shellTestProject(name = "Opened", keyPrefix = "OP")
        val projects = listOf(opened) + (1 until OVERFLOWING_ROWS).map { index ->
            shellTestProject(name = "Project $index", keyPrefix = "P$index")
        }
        projects.forEach { repository.seed(it) }
        val backlogItems = (1..OVERFLOWING_ROWS).map { index ->
            shellTestWorkItem(
                projectID = opened.id,
                number = index,
                title = "Task $index",
                status = WorkItemStatus.Backlog,
            )
        }
        backlogItems.forEach { repository.seed(it) }
        return SeededLists(
            projects = projects,
            backlogItems = backlogItems,
            openedSummary = ProjectSummary(opened, itemCount = OVERFLOWING_ROWS),
        )
    }

    private class SeededLists(
        val projects: List<Project>,
        val backlogItems: List<WorkItem>,
        /**
         * The summary the Projects list draws for [projects]' first entry.
         *
         * [ProjectRowTag] keys on the key prefix, so the row could be addressed without the
         * [itemCount] — but a tag assembled from a summary the list would never draw is a tag
         * that stops matching the moment the projection changes, so it is built from the count
         * that was seeded.
         */
        val openedSummary: ProjectSummary,
    )

    /**
     * Opens [SeededLists.openedSummary]'s project by clicking its row, as a user's first tap
     * would.
     *
     * There is no other way to reach a scoped tab's content: [com.tasktracker.routing.Router]
     * keeps a per-tab path and only `openProject` pushes onto the Board one, so a test that
     * seeds a project and clicks straight to `AppTab.Board` gets the scoped *empty state* —
     * correctly drawn, and a test of the picker's copy rather than of scroll geometry.
     */
    private fun ComposeUiTest.openSeededProject(seeded: SeededLists) {
        onNodeWithTag(ProjectRowTag(seeded.openedSummary)).performClick()
        waitForIdle()
    }

    /**
     * Each tab and the tag on the node whose bottom edge is its scroll region's reach.
     *
     * The board is measured on its lane row, so see [everyTabScrollsBehindTheTabBar] for why.
     */
    private val scrollRegionUnderTheBar: List<Pair<AppTab, String>> = listOf(
        AppTab.Projects to ProjectsListTag,
        AppTab.Board to ProjectBoardColumnsTag,
        AppTab.Backlog to ProjectBacklogListTag,
        AppTab.Settings to SettingsScreenTag,
    )

    // A second scroll region per tab is not registered here, because the backlog tab now has
    // three sections and the archived and Kanban ones are reached by a section switch rather
    // than by a tab. Their reach is asserted in [BacklogSectionScenarios] directly, and the
    // backlog section remains the one the tab's scroll region is measured on.

    val projectBacklogScenarios: List< suspend ComposeUiTest.() -> Unit> = listOf(
        BacklogSectionScenarios.switchingToArchivedShowsTheArchivedSection,
        BacklogSectionScenarios.switchingBackToBacklogShowsTheBacklogSection,
        BacklogSectionScenarios.theArchivedSectionListsOnlyClosedItemsNewestFirst,
        BacklogSectionScenarios.theBacklogSectionRemainsWhenSwitchingAway,
        BacklogSectionScenarios.theArchivedSectionShowsItsOwnEmptyState,
        BacklogSectionScenarios.theBacklogSectionShowsItsOwnEmptyState,
        BacklogSectionScenarios.theBusyItemKeepsItsWaitAcrossASectionSwitch,
        BacklogSectionScenarios.switchingToKanbanShowsTheBoardRows,
        BacklogSectionScenarios.theKanbanSectionListsRowsInBoardOrderThenRank,
        BacklogSectionScenarios.tappingAKanbanRowOpensTheTask,
        BacklogSectionScenarios.filingAwayFromAKanbanRowCallsFileAway,
        BacklogSectionScenarios.theKanbanSectionShowsItsOwnEmptyState,
        BacklogSectionScenarios.tappingABacklogRowOpensTheTask,
    )

    private val backlogTabScenarios: List< suspend ComposeUiTest.() -> Unit> = listOf(
        everyTabScrollsBehindTheTabBar,
    )

    /** Rows seeded per list: enough to overflow a tall viewport at the row heights in use. */
    private const val OVERFLOWING_ROWS = 40

    /**
     * Which tabs the semantics tree is currently claiming as selected, in [AppTab] order.
     *
     * Read from the tree rather than from the router on purpose. Asserting the router would
     * pass no matter what the bar drew, and the whole failure being guarded against is the bar
     * drawing something other than what the router says — a `selected` that never updates, or
     * one that was set once at construction. The router is read nowhere in here for exactly
     * that reason.
     *
     * Compared as a list so that "two tabs are selected" fails as loudly as "none is": a
     * count-only assertion (`assertEquals(1, size)`) would accept a bar that lit the wrong
     * single tab.
     */
    private fun ComposeUiTest.selectedTabs(): List<AppTab> =
        AppTab.entries
            .filter { tab ->
                onAllNodes(
                    hasTestTag(TabButtonTag(tab)) and isSelected(),
                ).fetchSemanticsNodes().isNotEmpty()
            }

    /**
     * A tab's label, read from the same string resource the bar uses.
     *
     * Not a literal. If these were hardcoded English strings, renaming `tab_projects` in
     * `strings.xml` would fail this test — which sounds helpful until you remember that these
     * labels are localised, and that the test's job is to prove the bar renders *a* label for
     * each tab, not to pin the English wording of four of them. Going through the resource
     * means a missing or renamed resource is still a failure, and a translation is not.
     *
     * [getString] rather than `stringResource`, because this is a `suspend` test body and not
     * a composable one; there is no composition to read from here, which is the same reason
     * every other scenario in the project asserts against `getString`.
     */
    /**
     * Matches a node that announces itself as a tab rather than as a button.
     *
     * Spelled out rather than reached for as `hasRole`, because `ui-test` has no `hasRole` at
     * all — the available matchers are `hasClickAction`, `hasText`, `hasTestTag` and friends,
     * and role is one of the few semantics properties that has to go through
     * `SemanticsMatcher.expectValue` by hand. Which is a decent argument on its own for the
     * assertion existing: it is the reason to find out that a matcher you assumed was there
     * is not.
     */
    private fun expectTabRole(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private suspend fun tabLabel(tab: AppTab): String = when (tab) {
        AppTab.Projects -> getString(Res.string.tab_projects)
        AppTab.Board -> getString(Res.string.tab_board)
        AppTab.Backlog -> getString(Res.string.tab_backlog)
        AppTab.Sprints -> getString(Res.string.tab_sprints)
        AppTab.Settings -> getString(Res.string.tab_settings)
    }
}

/**
 * Whether this host can lay out a shell wider than [ShellExpandedMinimumWidth].
 *
 * True on the JVM and the iOS simulator, whose viewports carry a 1000dp shell comfortably. False
 * on the Android host, whose viewport is 320dp — which is not a defect to fix but a phone, and a
 * desktop window is not a configuration it has. See
 * [ShellScenarios.desktopWidthIsExpressible]'s note for why this is declared per host rather than
 * measured, which cost two mutation runs to establish.
 *
 * Top level because `expect` cannot be a member, and beside [ShellScenarios] rather than inside
 * it so the actuals are three one-line files next to the host entry points — the shape
 * `thisHostRasterisesText` uses in `:packages:design-system`.
 */
internal expect fun thisHostExpressesDesktopWidth(): Boolean

/**
 * Whether this host can deliver a system back press into the composition.
 *
 * True on the JVM and the iOS simulator, where [pressSystemBack] drives a synthetic input on the
 * dispatcher the shell's `BackHandler` registers with. False on the Android host: there the press
 * enters through `activity.onBackPressed()` — the chain the device uses — and it does not reach the
 * shell's `BackHandler` under Robolectric. That is not this task's defect and not something the
 * scenario can work around; `systemBackPopsTheSprintDetail` is red on that host at the same
 * assertion, and this is the declaration that stops a second scenario from silently joining it.
 *
 * Declared per host for the reason [thisHostExpressesDesktopWidth] gives — the capability is a
 * property of the host, so the scenario asks rather than going red where the answer is no — and
 * top level for the same reason, since `expect` cannot be a member.
 */
internal expect fun thisHostDeliversSystemBack(): Boolean

/**
 * Records a scenario as skipped when this host cannot lay out the requested width.
 *
 * An assumption, not the early return [ShellScenarios.desktopWidthIsExpressible] used to make:
 * a returning scenario is recorded as a pass, and three wide-layout scenarios reporting green
 * on a phone host that never built the shell they are about is the false green this harness
 * exists to remove. The Android actual throws through `org.junit.Assume`; the JVM and iOS
 * actuals do nothing because both can express a desktop width.
 */
internal expect fun assumeHostExpressesDesktopWidth()

/**
 * Records a scenario as skipped when this host cannot deliver a system back press.
 *
 * Same contract as [assumeHostExpressesDesktopWidth], for the same reason: a scenario whose
 * whole claim is about a back press must not report green where no press can be delivered.
 */
internal expect fun assumeHostDeliversSystemBack()
