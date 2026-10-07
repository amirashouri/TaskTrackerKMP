@file:OptIn(ExperimentalTestApi::class)

package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.featureauthpresentation.EmailFieldTag
import com.tasktracker.featureauthpresentation.PasswordFieldTag
import com.tasktracker.featureauthpresentation.PrimaryButtonTag
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.domain.Project
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.core.SprintID
import com.tasktracker.featureprojectspresentation.BoardFileAwayTag
import com.tasktracker.featureprojectspresentation.BacklogRemoveFromSprintTag
import com.tasktracker.featureprojectspresentation.MissingProjectEnvironmentTag
import com.tasktracker.featureprojectspresentation.NewProjectCreateTag
import com.tasktracker.featureprojectspresentation.NewProjectSheetTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogListTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogScreenTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogArchivedListTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogEmptyBacklogTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogEmptyArchivedTag
import com.tasktracker.featureprojectspresentation.SectionIndicatorTag
import com.tasktracker.featureprojectspresentation.ProjectBoardColumnsTag
import com.tasktracker.featureprojectspresentation.ProjectBoardScreenTag
import com.tasktracker.featureprojectspresentation.ProjectRowTag
import com.tasktracker.featureprojectspresentation.ProjectRowTagPrefix
import com.tasktracker.featureprojectspresentation.ProjectSwitcherTag
import com.tasktracker.featureprojectspresentation.ProjectSwitcherOptionTag
import com.tasktracker.featureprojectspresentation.WorkItemBacklogRowTagPrefix
import com.tasktracker.featureprojectspresentation.WorkItemCancelTag
import com.tasktracker.featureprojectspresentation.WorkItemCardTag
import com.tasktracker.featureprojectspresentation.WorkItemFileAwayTag
import com.tasktracker.featureprojectspresentation.ProjectsListNewTag
import com.tasktracker.featureprojectspresentation.ProjectsListTag
import com.tasktracker.featureprojectspresentation.ProjectsListScreenTag
import com.tasktracker.featureprojectspresentation.SprintDetailScreenTag
import com.tasktracker.featureprojectspresentation.SprintDetailAddTag
import com.tasktracker.featureprojectspresentation.SprintDetailReopenTag
import com.tasktracker.featureprojectspresentation.SprintListScreenTag
import com.tasktracker.featureprojectspresentation.SprintPickerAddTag
import com.tasktracker.featureprojectspresentation.SprintPickerRowTag
import com.tasktracker.featureprojectspresentation.SprintPickerSheetTag
import com.tasktracker.featureprojectspresentation.SprintRowTag
import com.tasktracker.featureprojectspresentation.SprintDetailSummaryTag
import com.tasktracker.featureprojectspresentation.SprintSummaryBackTag
import com.tasktracker.featureprojectspresentation.WorkItemBacklogRowTag
import com.tasktracker.routing.AppRoute
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingspresentation.SettingsScreenTag
import com.tasktracker.featuresettingspresentation.SettingsSignOutTag
import com.tasktracker.featuresettingspresentation.ThemeOptionTag
import com.tasktracker.resources.Res
import com.tasktracker.resources.tab_backlog
import com.tasktracker.resources.tab_board
import com.tasktracker.resources.tab_projects
import com.tasktracker.resources.tab_settings
import com.tasktracker.resources.tab_sprints
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.compose.resources.getString
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
     */
    internal suspend fun ComposeUiTest.setUpShell(
        session: ScriptedSession,
        router: Router = Router(),
        projects: ProjectRepositoryPort = FakeProjectRepository(),
        omitProjects: Boolean = false,
        settings: SettingsPort? = null,
        preferences: UserDefaultsStore = UserDefaultsStore(InMemoryPreferences()),
        splashHold: Long = 0L,
        width: Dp? = null,
        navigationEventOwner: NavigationEventDispatcherOwner? = null,
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
            val app: @Composable () -> Unit = {
                if (width == null) {
                    TaskTrackerApp(resolver, splashMinimumHoldMillis = splashHold)
                } else {
                    Box(modifier = Modifier.width(width)) {
                        TaskTrackerApp(resolver, splashMinimumHoldMillis = splashHold)
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
        // Scrolled to, not merely clicked: the User details header now leads the list, so on a
        // short viewport the sign-out row is past the fold and — a LazyColumn that has not
        // reached it leaves it out of the tree entirely, where a plain click finds nothing.
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsSignOutTag))
        waitForIdle()
        onNodeWithTag(SettingsSignOutTag).performClick()
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

        onNodeWithTag(WorkItemFileAwayTag).performScrollTo().performClick()
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
        setUpShell(session = ScriptedSession(restored = signedIn()), projects = projects)

        waitForIdle()
        onNodeWithTag(ProjectRowTag(ProjectSummary(project, itemCount = 1))).performClick()
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
        onNodeWithTag(NewProjectSheetTag).assertIsDisplayed()

        onNodeWithTag("name").performTextInput("Gemini")
        onNodeWithTag(NewProjectCreateTag).performClick()
        waitForIdle()

        assertTrue(
            onAllNodes(hasTestTag(NewProjectSheetTag)).fetchSemanticsNodes().isEmpty(),
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
        // Reopen only draws for a finished sprint, so this also proves the link replaced
        // the detail rather than leaving the active one under a matching tag.
        onNodeWithTag(SprintDetailReopenTag).assertIsDisplayed()

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
        // behind the sheet. The route-pushing wiring was already correct when the editor
        // opened over the *board* from a sprint: the background `when` in `PushedRoute`
        // lumped SprintDetail into its Board default, so the sheet sat on a board the user
        // had never opened and the pop then "returned" to a sprint they never saw leave.
        onNodeWithTag(SprintDetailScreenTag).assertIsDisplayed()
        assertTrue(
            onAllNodes(hasTestTag(ProjectBoardScreenTag)).fetchSemanticsNodes().isEmpty(),
            "expected the sprint detail, not the board, behind the editor opened from a " +
                "sprint task row",
        )

        // Cancel has to land back on that same sprint detail — closing the sheet the user
        // opened over it, not on the board the background used to fall back to.
        onNodeWithTag(WorkItemCancelTag).performScrollTo().performClick()
        waitForIdle()
        assertTrue(
            onAllNodes(hasTestTag(WorkItemEditorDestinationTag)).fetchSemanticsNodes().isEmpty(),
            "expected Cancel to close the editor rather than leave the sheet standing over " +
                "the sprint",
        )
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
        onNodeWithTag(ThemeOptionTag(ThemeScheme.Dark)).performClick()
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
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsSignOutTag))
        waitForIdle()
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
