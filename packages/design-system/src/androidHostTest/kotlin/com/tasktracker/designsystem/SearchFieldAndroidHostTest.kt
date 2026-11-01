package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SearchFieldScenarios] on the Android host, under Robolectric.
 *
 * ## Why this file exists
 *
 * Every scenario in [SearchFieldScenarios] is a semantics-tree or geometry claim, so none of
 * them needs a rasteriser — but a Compose UI test on the Android host still needs Robolectric,
 * and Robolectric only engages for a class that names a JUnit 4 runner. `@RunWith` cannot sit in
 * `commonTest` beside the iOS target, so the entry points live here.
 *
 * Before this file existed the scenarios were wrapped in `assumeHostRasterisesText()`, which
 * meant the whole field — including "a disabled field is not enabled" and the pill's height
 * claim — was unverified on Android.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchFieldAndroidHostTest {

    @Test
    fun anEmptyFieldShowsTheHintAndNoClearControl() =
        runComposeUiTest(block = SearchFieldScenarios.anEmptyFieldShowsTheHintAndNoClearControl)

    @Test
    fun typingReachesTheFieldAndRevealsTheClearControl() =
        runComposeUiTest(block = SearchFieldScenarios.typingReachesTheFieldAndRevealsTheClearControl)

    @Test
    fun theClearControlEmptiesTheField() =
        runComposeUiTest(block = SearchFieldScenarios.theClearControlEmptiesTheField)

    @Test
    fun thePillAndTheFieldAreTwoNodes() =
        runComposeUiTest(block = SearchFieldScenarios.thePillAndTheFieldAreTwoNodes)

    @Test
    fun thePillIsAtLeastTheFieldsMinimumHeight() =
        runComposeUiTest(block = SearchFieldScenarios.thePillIsAtLeastTheFieldsMinimumHeight)

    @Test
    fun aDisabledFieldIsNotEnabled() =
        runComposeUiTest(block = SearchFieldScenarios.aDisabledFieldIsNotEnabled)

    @Test
    fun focusingTheFieldReportsIt() =
        runComposeUiTest(block = SearchFieldScenarios.focusingTheFieldReportsIt)
}
