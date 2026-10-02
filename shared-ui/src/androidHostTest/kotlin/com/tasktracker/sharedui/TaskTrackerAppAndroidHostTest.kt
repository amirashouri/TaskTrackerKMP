package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [ShellScenarios] on the Android host.
 *
 * The only target of the three that needs a runner annotation, which is precisely why the
 * assertions live in `commonTest` and this file contributes nothing but entry points. See
 * [ShellScenarios] for the full explanation — against the host's stub `android.jar`,
 * `Build.FINGERPRINT` is null and Compose's own idling strategy dereferences it, so without
 * Robolectric every test dies in its constructor.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskTrackerAppAndroidHostTest {

    /** A signed-out session shows the sign-in form. The regression this file exists for. Asserted on the three interactive tags rather than on the word "Task Tracker": the title is content that a redesign can change, whereas the fields and the button are the screen's actual contract with a user. */
    @Test
    fun aSignedOutSessionShowsSignIn() = runComposeUiTest(block = ShellScenarios.aSignedOutSessionShowsSignIn)

    /** Typed credentials reach the session port the shell resolved. Without this, a shell that rendered the form but wired it to nothing would satisfy [aSignedOutSessionShowsSignIn] completely — correct on screen, inert underneath. This is the assertion that separates the two. */
    @Test
    fun signingInFromTheShellReachesTheSessionPort() = runComposeUiTest(block = ShellScenarios.signingInFromTheShellReachesTheSessionPort)

    /** Nothing is rendered while the session is still [SessionState.Restoring]. Asserted as the absence of *every* screen rather than the presence of a spinner: a launch path that flashes the sign-in form at a returning user is the bug, and a spinner would satisfy this test while still being a visible flash of nothing useful. [ScriptedSession] is seeded with `Restoring` and a *different* restore result, so the projects list here can only appear because restore completed. Without that the assertion would pass trivially against a shell that renders nothing, ever. */
    @Test
    fun aRestoreInFlightShowsNeitherScreen() = runComposeUiTest(block = ShellScenarios.aRestoreInFlightShowsNeitherScreen)

    /** A restored session shows the projects list, with the form gone rather than covered. This is the assertion that the *port* half is wired. The projects list can only be on screen if the shell resolved a repository, built a `ProjectEnvironment` from it, and handed that to a real model — which is three seams, and each of them silent when broken. */
    @Test
    fun aRestoredSessionShowsTheProjectsList() = runComposeUiTest(block = ShellScenarios.aRestoredSessionShowsTheProjectsList)

    /** A graph with no projects repository says so, rather than showing an empty list. The distinction is the whole point: an empty list is a claim about the *store* ("you have no projects") and the gate is a claim about the *build* ("nothing here can read your projects"). Showing the list in the second case would tell someone with a dozen projects that they have none, which is worse than a build that admits what it is. */
    @Test
    fun aGraphWithoutProjectsSaysSo() = runComposeUiTest(block = ShellScenarios.aGraphWithoutProjectsSaysSo)

    /** The Settings tab ends the session, and it is the only place that can. Replaces `theSignedInShellOffersNoSignOut`, which asserted the *absence* of a sign-out control and carried an instruction to delete it in the same change that gave sign-out a home. The control being present proves the header's replacement exists; the port being *called* proves it is not another live-looking button. */
    @Test
    fun theSettingsTabEndsTheSession() = runComposeUiTest(block = ShellScenarios.theSettingsTabEndsTheSession)

    /** Signing out leaves nobody inside the last signed-in user's board. The router is app-scoped and outlives the session, so its stacks survive a sign-out unless something clears them — and a device handed to a colleague, or a shared family tablet, would then put the previous user's projects in front of the next one. That is a disclosure rather than a cosmetic bug, and `enterAuth` is the only thing in the app that wipes them. */
    @Test
    fun signingOutClearsTheNavigationStack() = runComposeUiTest(block = ShellScenarios.signingOutClearsTheNavigationStack)

    /** Opening a project shows that project's board. The point of the exercise: the list and the board are one navigation apart, and the router is what decides which. Asserted on the board's own tag rather than on a project name, so a list that merely *kept* showing the project cannot pass it. */
    @Test
    fun openingAProjectShowsItsBoard() = runComposeUiTest(block = ShellScenarios.openingAProjectShowsItsBoard)

    /** The board's File Away button reaches the store. The screen has drawn that button since Phase 12, and the board's model had no verb for it until this shell was wired — a gap no model test could see, because a model cannot be asked whether the button above it has a caller. This is the assertion that a live-looking control is not inert. */
    @Test
    fun filingACardAwayFromTheBoardReachesTheStore() = runComposeUiTest(block = ShellScenarios.filingACardAwayFromTheBoardReachesTheStore)

    /** The toolbar's project name is the way back to the list. Asserted as a round trip rather than as a tap, because a control that navigates *somewhere* is not the same as one that navigates *here*: the board was pushed onto the Projects stack, so anything short of returning to the root leaves the user one tap from nowhere. */
    @Test
    fun theBoardReturnsToTheList() = runComposeUiTest(block = ShellScenarios.theBoardReturnsToTheList)

    /** The projects list stops observing when the signed-in tree is gone. The subscription is what keeps the store answering "what changed?" forever, and a collector left alive after sign-out re-reads the whole store on every write in the feature for the rest of the process. Asserted on the store's own subscriber count rather than on anything on screen, because a leak has no screen. */
    @Test
    fun signingOutStopsTheProjectsObservation() = runComposeUiTest(block = ShellScenarios.signingOutStopsTheProjectsObservation)

    /** "New project" opens a sheet, and creating one adds it to the list. Both halves matter, and the second is the one that would pass alone: a sheet that opens and whose Create button does nothing is a form that looks finished and is not, which is the same class of defect as the File Away button above. The name field is found by the tag `ProjectsComponents` derives from its own label, so the test asks for the field the same way the sheet names it. */
    @Test
    fun creatingAProjectAddsItToTheList() = runComposeUiTest(block = ShellScenarios.creatingAProjectAddsItToTheList)

    /** All four tabs reach their own screen, in order. `NavigationHost` is the first thing in the project that drew a tab bar, and every other scenario here reaches its screen from the *Projects* tab — so three quarters of the app was reachable in production and unproven by a single assertion. Asserted in order because the interesting failure is not "a tab is broken" but "two tabs drew the same screen": a shared tag mistake, or a `when` that forgot a branch. */
    @Test
    fun everyTabGetsToItsOwnScreen() = runComposeUiTest(block = ShellScenarios.everyTabGetsToItsOwnScreen)

    /** Changing tab keeps the active project. `Router.selectTab` answers "which tab" and nothing else, on purpose, so that switching tabs does not re-`openProject` and reset a board mid-thought. That is invisible in a routing unit test (which only asserts the tab changed) and invisible in a single-tab test (the board was never left) — it needs a tab, a project, and a return. */
    @Test
    fun switchingTabsKeepsTheProject() = runComposeUiTest(block = ShellScenarios.switchingTabsKeepsTheProject)
    @Test
    fun theTabBarFloatsAboveTheBottomEdge() = runComposeUiTest(block = ShellScenarios.theTabBarFloatsAboveTheBottomEdge)
    @Test
    fun theTabBarIsWiderThanItsTabs() = runComposeUiTest(block = ShellScenarios.theTabBarIsWiderThanItsTabs)
    @Test
    fun exactlyOneTabIsMarkedSelected() = runComposeUiTest(block = ShellScenarios.exactlyOneTabIsMarkedSelected)
    @Test
    fun everyTabIsNamedAndCarriesTheTabRole() = runComposeUiTest(block = ShellScenarios.everyTabIsNamedAndCarriesTheTabRole)

    /** The splash outlives the minimum hold even with the session already read. A shell that only waited for the session would flash the sign-in form during a slow keychain read; a shell that only waited for the floor would hold over an app that was ready long ago. Every other scenario here uses a 0ms hold, which passes a gate that implements neither. */
    @Test
    fun theSplashHoldsUntilBothTheFloorAndTheSession() = runComposeUiTest(block = ShellScenarios.theSplashHoldsUntilBothTheFloorAndTheSession)

    /** ...and it still waits when the floor is long past but the restore has not finished. Split from the hold because one scenario cannot distinguish "lifted on the floor" from "lifted on the session" — both bugs pass a single check. A restore that never returns is what a hung keychain read looks like from the shell's side, and the answer must be "keep waiting" rather than "show the wrong screen". */
    @Test
    fun theSplashWaitsForARestoreThatHasNotFinished() = runComposeUiTest(block = ShellScenarios.theSplashWaitsForARestoreThatHasNotFinished)

    /** A theme chosen in Settings is written through to the store. The raw key is the assertion: `ShellPreferences` deliberately stores it outside `DefaultsKey` so `removeAll` on sign-out cannot reach it, and a namespaced key would work perfectly for anyone who set it *in this build* while silently coming back as `system` for anyone whose value was written by the original app. */
    @Test
    fun aChosenThemeOutlivesTheProcess() = runComposeUiTest(block = ShellScenarios.aChosenThemeOutlivesTheProcess)

    /** Forced-offline survives the sign-out teardown. The theme is cosmetic, so losing it is an annoyance; forced-offline is a thing a user asked the *device* to do before handing it to somebody else to reproduce a bug. The scenario calls `removeAll` directly — that is the line that would erase the choice, and a single-launch test cannot reach it. */
    @Test
    fun forcedOfflineSurvivesTheSessionEnding() = runComposeUiTest(block = ShellScenarios.forcedOfflineSurvivesTheSessionEnding)
}
