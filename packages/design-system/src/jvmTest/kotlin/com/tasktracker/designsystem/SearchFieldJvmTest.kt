package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [SearchFieldScenarios] on the JVM.
 *
 * The JVM is the fast loop for the field: the same scenario values run on the iOS simulator
 * ([SearchFieldIosTest]) and the Android host ([SearchFieldAndroidHostTest]); this file is the
 * one that runs in seconds. There is no `commonTest` entry point on purpose — the Android host
 * needs a Robolectric runner that `commonTest` cannot carry.
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldJvmTest {

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
