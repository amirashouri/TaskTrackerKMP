package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/** [DeviceMotionScenarios] on the JVM. */
@OptIn(ExperimentalTestApi::class)
class DeviceMotionJvmTest {

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
