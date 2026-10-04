package com.tasktracker.sharedui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [ShellScenarios] on the iOS simulator.
 *
 * The same scenarios on the iOS simulator, which is the only place the real iOS keychain and
 * * preferences backends are exercised through a real `AppGraph`.
 *
 * No `commonTest` entry point, for the reason [ShellScenarios] gives: an Android-host
 * Compose UI test only runs under Robolectric, and that runner annotation is not
 * expressible in `commonTest`.
 */
@OptIn(ExperimentalTestApi::class)
class TaskTrackerAppIosTest {

    @Test
    fun aSignedOutSessionShowsSignIn() = runComposeUiTest(block = ShellScenarios.aSignedOutSessionShowsSignIn)
    @Test
    fun signingInFromTheShellReachesTheSessionPort() = runComposeUiTest(block = ShellScenarios.signingInFromTheShellReachesTheSessionPort)
    @Test
    fun aRestoreInFlightShowsNeitherScreen() = runComposeUiTest(block = ShellScenarios.aRestoreInFlightShowsNeitherScreen)
    @Test
    fun aRestoredSessionShowsTheProjectsList() = runComposeUiTest(block = ShellScenarios.aRestoredSessionShowsTheProjectsList)
    @Test
    fun aGraphWithoutProjectsSaysSo() = runComposeUiTest(block = ShellScenarios.aGraphWithoutProjectsSaysSo)
    @Test
    fun theSettingsTabEndsTheSession() = runComposeUiTest(block = ShellScenarios.theSettingsTabEndsTheSession)
    @Test
    fun signingOutClearsTheNavigationStack() = runComposeUiTest(block = ShellScenarios.signingOutClearsTheNavigationStack)
    @Test
    fun openingAProjectShowsItsBoard() = runComposeUiTest(block = ShellScenarios.openingAProjectShowsItsBoard)
    @Test
    fun filingACardAwayFromTheBoardReachesTheStore() = runComposeUiTest(block = ShellScenarios.filingACardAwayFromTheBoardReachesTheStore)
    @Test
    fun theBoardReturnsToTheList() = runComposeUiTest(block = ShellScenarios.theBoardReturnsToTheList)
    @Test
    fun signingOutStopsTheProjectsObservation() = runComposeUiTest(block = ShellScenarios.signingOutStopsTheProjectsObservation)
    @Test
    fun creatingAProjectAddsItToTheList() = runComposeUiTest(block = ShellScenarios.creatingAProjectAddsItToTheList)
    @Test
    fun everyTabGetsToItsOwnScreen() = runComposeUiTest(block = ShellScenarios.everyTabGetsToItsOwnScreen)
    @Test
    fun switchingTabsKeepsTheProject() = runComposeUiTest(block = ShellScenarios.switchingTabsKeepsTheProject)
    @Test
    fun theTabBarFloatsAboveTheBottomEdge() = runComposeUiTest(block = ShellScenarios.theTabBarFloatsAboveTheBottomEdge)
    @Test
    fun theTabBarIsWiderThanItsTabs() = runComposeUiTest(block = ShellScenarios.theTabBarIsWiderThanItsTabs)
    @Test
    fun fiveTabsFitAtPhoneWidth() = runComposeUiTest(block = ShellScenarios.fiveTabsFitAtPhoneWidth)
    @Test
    fun aSprintOpensAndALinkLandsOnIt() = runComposeUiTest(block = ShellScenarios.aSprintOpensAndALinkLandsOnIt)
    @Test
    fun aDeepLinkToOneSprintLandsOnItsScreen() = runComposeUiTest(block = ShellScenarios.aDeepLinkToOneSprintLandsOnItsScreen)
    @Test
    fun everyTabScrollsBehindTheTabBar() = runComposeUiTest(block = ShellScenarios.everyTabScrollsBehindTheTabBar)
    @Test
    fun theLastRowOfEachListScrollsClearOfTheTabBar() = runComposeUiTest(block = ShellScenarios.theLastRowOfEachListScrollsClearOfTheTabBar)
    @Test
    fun exactlyOneTabIsMarkedSelected() = runComposeUiTest(block = ShellScenarios.exactlyOneTabIsMarkedSelected)
    @Test
    fun everyTabIsNamedAndCarriesTheTabRole() = runComposeUiTest(block = ShellScenarios.everyTabIsNamedAndCarriesTheTabRole)
    @Test
    fun theSplashHoldsUntilBothTheFloorAndTheSession() = runComposeUiTest(block = ShellScenarios.theSplashHoldsUntilBothTheFloorAndTheSession)
    @Test
    fun theSplashWaitsForARestoreThatHasNotFinished() = runComposeUiTest(block = ShellScenarios.theSplashWaitsForARestoreThatHasNotFinished)
    @Test
    fun aChosenThemeOutlivesTheProcess() = runComposeUiTest(block = ShellScenarios.aChosenThemeOutlivesTheProcess)
    @Test
    fun forcedOfflineSurvivesTheSessionEnding() = runComposeUiTest(block = ShellScenarios.forcedOfflineSurvivesTheSessionEnding)
}
