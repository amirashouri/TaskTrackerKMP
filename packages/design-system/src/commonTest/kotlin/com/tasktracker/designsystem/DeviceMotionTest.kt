package com.tasktracker.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The motion plumbing's lifecycle, as composition behaviour.
 *
 * A real sensor cannot run on CI, so what is testable here is the plumbing the
 * platform actuals plug into: one shared controller per provider, and a sensor that
 * stops when its owner leaves composition. The pre-fix behaviour this pins against was
 * one listener per glass surface — remembered in `ttGlassSurface` with no dispose path,
 * so listeners also outlived the screen that registered them.
 *
 * The iOS attitude handler (the other half of the fix — the specular never moved on
 * iOS because no handler was ever installed) is host-only and asserted by the release
 * gate, not here.
 *
 * Skipped on the Android host through [rasterising], which throws
 * `AssumptionViolatedException` from the Android-only actual of
 * `assumeHostRasterisesText`. `org.junit.Assume` cannot be imported here directly:
 * `org.junit` is not on the iOS or Native compilations this same `commonTest` source
 * set has to satisfy, which is exactly why that skip is an `expect` function whose
 * only platform-specific import lives in the Android source set.
 */
@OptIn(ExperimentalTestApi::class)
class DeviceMotionTest {

    private class FakeMotion : DeviceMotionController {
        override val roll = mutableStateOf(0f)
        override val pitch = mutableStateOf(0f)
        var started = 0
        var stopped = 0
        override fun start() { started++ }
        override fun stop() { stopped++ }
    }

    @Test
    fun `a provider shares one controller with every reader under it`() {
        rasterising {
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
    }

    @Test
    fun `the provider stops the controller when it leaves composition`() {
        rasterising {
            val fake = FakeMotion()
            var shown by mutableStateOf(true)
            setContent {
                if (shown) DeviceMotionProvider(fake) { rememberDeviceMotion() }
            }
            runOnIdle { assertEquals(0, fake.stopped, "no stop while the provider is present") }
            runOnIdle { shown = false }
            runOnIdle { assertEquals(1, fake.stopped, "the sensor stops with the provider") }
        }
    }

    @Test
    fun `the start-stop discipline starts on enter and stops on leave`() {
        rasterising {
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
}
