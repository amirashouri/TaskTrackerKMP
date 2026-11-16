package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [TextFieldScenarios] on the iOS simulator.
 *
 * Identical entry points to [TextFieldJvmTest] and [TextFieldAndroidHostTest]; the reason they
 * are three files rather than one in `commonTest` is on [TextFieldScenarios].
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldIosTest {

    @Test
    fun aTapOnThePillsPaddingFocusesTheField() =
        runComposeUiTest(block = TextFieldScenarios.aTapOnThePillsPaddingFocusesTheField)

    @Test
    fun aLabelHoldingTheRequesterFocusesTheField() =
        runComposeUiTest(block = TextFieldScenarios.aLabelHoldingTheRequesterFocusesTheField)
}
