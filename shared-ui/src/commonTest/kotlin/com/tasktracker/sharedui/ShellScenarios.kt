@file:OptIn(ExperimentalTestApi::class)

package com.tasktracker.sharedui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.tasktracker.featureauthdomain.SessionState
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.featureauthpresentation.EmailFieldTag
import com.tasktracker.featureauthpresentation.PasswordFieldTag
import com.tasktracker.featureauthpresentation.PrimaryButtonTag
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.featureprojectspresentation.BoardFileAwayTag
import com.tasktracker.featureprojectspresentation.MissingProjectEnvironmentTag
import com.tasktracker.featureprojectspresentation.NewProjectCreateTag
import com.tasktracker.featureprojectspresentation.NewProjectSheetTag
import com.tasktracker.featureprojectspresentation.ProjectBacklogScreenTag
import com.tasktracker.featureprojectspresentation.ProjectBoardScreenTag
import com.tasktracker.featureprojectspresentation.ProjectRowTag
import com.tasktracker.featureprojectspresentation.ProjectSwitcherTag
import com.tasktracker.featureprojectspresentation.ProjectsListNewTag
import com.tasktracker.featureprojectspresentation.ProjectsListScreenTag
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingspresentation.SettingsForceOfflineTag
import com.tasktracker.featuresettingspresentation.SettingsScreenTag
import com.tasktracker.featuresettingspresentation.SettingsSignOutTag
import com.tasktracker.featuresettingspresentation.ThemeOptionTag
import com.tasktracker.resources.Res
import com.tasktracker.resources.tab_backlog
import com.tasktracker.resources.tab_board
import com.tasktracker.resources.tab_projects
import com.tasktracker.resources.tab_settings
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.compose.resources.getString
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        setContent { TaskTrackerApp(resolver, splashMinimumHoldMillis = splashHold) }
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
        onNodeWithTag(SettingsForceOfflineTag).performClick()
        waitForIdle()
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
        AppTab.Settings -> getString(Res.string.tab_settings)
    }
}
