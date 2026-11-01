package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [SearchFieldScenarios] on the iOS simulator.
 *
 * Identical entry points to [SearchFieldJvmTest] and [SearchFieldAndroidHostTest]; the reason
 * they are three files rather than one in `commonTest` is on [SearchFieldScenarios].
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldIosTest {

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
