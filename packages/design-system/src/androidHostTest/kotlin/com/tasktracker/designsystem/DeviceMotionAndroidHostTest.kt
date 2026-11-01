package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [DeviceMotionScenarios] on the Android host, under Robolectric.
 *
 * The scenarios are lifecycle claims with no pixel in them, so this host can verify all three.
 * Robolectric is still required for the composition itself, and its runner annotation cannot
 * live in `commonTest` beside the iOS target.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceMotionAndroidHostTest {

    @Test
    fun aProviderSharesOneControllerWithEveryReaderUnderIt() =
        runComposeUiTest(block = DeviceMotionScenarios.aProviderSharesOneControllerWithEveryReaderUnderIt)

    @Test
    fun theProviderStopsTheControllerWhenItLeavesComposition() =
        runComposeUiTest(block = DeviceMotionScenarios.theProviderStopsTheControllerWhenItLeavesComposition)

    @Test
    fun theStartStopDisciplineStartsOnEnterAndStopsOnLeave() =
        runComposeUiTest(block = DeviceMotionScenarios.theStartStopDisciplineStartsOnEnterAndStopsOnLeave)
}
