package com.tasktracker.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The motion plumbing's lifecycle, as composition behaviour.
 *
 * A real sensor cannot run on CI, so what is testable here is the plumbing the platform
 * actuals plug into: one shared controller per provider, and a sensor that stops when its
 * owner leaves composition. The pre-fix behaviour this pins against was one listener per glass
 * surface — remembered in `ttGlassSurface` with no dispose path, so listeners also outlived the
 * screen that registered them.
 *
 * The iOS attitude handler (the other half of the fix — the specular never moved on iOS because
 * no handler was ever installed) is host-only and asserted by the release gate, not here.
 *
 * ## Why this is not gated on rasterisation
 *
 * These are lifecycle claims about composition and the fake controller, not claims about any
 * pixel. They used to run through `rasterising {}`, which skipped them on the Android host for
 * a reason that had nothing to do with what they assert. The bodies live here and each host
 * contributes entry points ([DeviceMotionJvmTest], [DeviceMotionIosTest],
 * [DeviceMotionAndroidHostTest]) — the same split [SignInScenarios] documents.
 */
@OptIn(ExperimentalTestApi::class)
object DeviceMotionScenarios {

    private class FakeMotion : DeviceMotionController {
        override val roll = mutableStateOf(0f)
        override val pitch = mutableStateOf(0f)
        var started = 0
        var stopped = 0
        override fun start() { started++ }
        override fun stop() { stopped++ }
    }

    val aProviderSharesOneControllerWithEveryReaderUnderIt: suspend ComposeUiTest.() -> Unit = {
        val fake = FakeMotion()
        var first: DeviceMotionController? = null
        var second: DeviceMotionController? = null
        setContent {
            DeviceMotionProvider(fake) {
                first = rememberDeviceMotion()
                second = rememberDeviceMotion()
            }
        }
        runOnIdle {
            assertSame(fake, first)
            assertSame(fake, second)
            assertEquals(1, fake.started, "the shared controller starts exactly once")
        }
    }

    val theProviderStopsTheControllerWhenItLeavesComposition: suspend ComposeUiTest.() -> Unit = {
        val fake = FakeMotion()
        var shown by mutableStateOf(true)
        setContent {
            if (shown) DeviceMotionProvider(fake) { rememberDeviceMotion() }
        }
        runOnIdle { assertEquals(0, fake.stopped, "no stop while the provider is present") }
        runOnIdle { shown = false }
        runOnIdle { assertEquals(1, fake.stopped, "the sensor stops with the provider") }
    }

    val theStartStopDisciplineStartsOnEnterAndStopsOnLeave: suspend ComposeUiTest.() -> Unit = {
        val fake = FakeMotion()
        var shown by mutableStateOf(true)
        setContent {
            if (shown) StartStopMotion(fake)
        }
        runOnIdle { assertEquals(1, fake.started, "started on entering composition") }
        runOnIdle { shown = false }
        runOnIdle {
            assertEquals(1, fake.stopped, "stopped once when leaving composition")
            assertEquals(1, fake.started, "and not started a second time")
        }
    }
}
