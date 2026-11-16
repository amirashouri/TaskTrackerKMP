package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [TextFieldScenarios] on the JVM.
 *
 * The JVM is the fast loop for the field: the same scenario values run on the iOS simulator
 * ([TextFieldIosTest]) and the Android host ([TextFieldAndroidHostTest]). There is no
 * `commonTest` entry point on purpose — the Android host needs a Robolectric runner that
 * `commonTest` cannot carry.
 */
@OptIn(ExperimentalTestApi::class)
class TextFieldJvmTest {

    @Test
    fun aTapOnThePillsPaddingFocusesTheField() =
        runComposeUiTest(block = TextFieldScenarios.aTapOnThePillsPaddingFocusesTheField)

    @Test
    fun aLabelHoldingTheRequesterFocusesTheField() =
        runComposeUiTest(block = TextFieldScenarios.aLabelHoldingTheRequesterFocusesTheField)
}
