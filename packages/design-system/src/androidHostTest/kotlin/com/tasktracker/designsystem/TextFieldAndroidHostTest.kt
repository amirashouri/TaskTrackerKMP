package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [TextFieldScenarios] on the Android host, under Robolectric.
 *
 * Robolectric only engages for a class that names a JUnit 4 runner, and `@RunWith` cannot sit in
 * `commonTest` beside the iOS target — so the entry points live here, as they do for
 * [SearchFieldAndroidHostTest] and for the same reason.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextFieldAndroidHostTest {

    @Test
    fun aTapOnThePillsPaddingFocusesTheField() =
        runComposeUiTest(block = TextFieldScenarios.aTapOnThePillsPaddingFocusesTheField)

    @Test
    fun aLabelHoldingTheRequesterFocusesTheField() =
        runComposeUiTest(block = TextFieldScenarios.aLabelHoldingTheRequesterFocusesTheField)
}
