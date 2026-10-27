package com.tasktracker.sharedui

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import com.tasktracker.designsystem.DeviceMotionController
import com.tasktracker.designsystem.LocalReducedMotion
import com.tasktracker.designsystem.rememberDeviceMotion
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What the shell hands the glass material: the platform's motion preference, and one sensor.
 *
 * ## Why these are asserted on [ShellMotionProvider] rather than on the app root
 *
 * The shell's own entry point has no content slot, so a test cannot compose the app root and read
 * a composition local out of the middle of it. Reaching the local from outside is not merely
 * awkward but impossible: every use of `LocalReducedMotion` in `:packages:design-system` is a
 * draw-phase or animation decision — a specular offset, a press scale, a snap instead of a spring
 * — so no node in the semantics tree can be asked what it was told.
 *
 * So these pin the provider's contract, and the app root's *use* of it is pinned by the rest of
 * this module's suite: `ShellScenarios.setUpShell` composes `TaskTrackerApp` itself, so all of
 * those scenarios render the real shell with the real platform read and the real sensor provider
 * installed, and any failure in either takes them down with it.
 */
@OptIn(ExperimentalTestApi::class)
object ShellMotionScenarios {

    /**
     * The platform's answer reaches the material, both ways round.
     *
     * One composition rather than two, because a test may call `setContent` once: the preference
     * is flipped through state and read back, which is also what catches the failure that matters
     * here — a provider that installs a constant instead of what it was handed. Asserting only
     * the `true` case would pass against a local hard-wired to `true`, which would still the glass
     * for every user forever.
     */
    val theShellPassesTheReducedMotionPreferenceToTheMaterial: suspend ComposeUiTest.() -> Unit = {
        var reduceMotion by mutableStateOf(true)
        var seen: Boolean? = null

        setContent {
            ShellMotionProvider(reduceMotion = reduceMotion) {
                seen = LocalReducedMotion.current
            }
        }

        runOnIdle {
            assertTrue(
                seen == true,
                "a glass surface under the shell reads LocalReducedMotion as $seen while the " +
                    "platform is asking for reduced motion. The shell is what turns that answer " +
                    "into the local, so a false here means the preference never reaches the " +
                    "material and Reduce Motion does nothing",
            )
            reduceMotion = false
        }
        runOnIdle {
            assertTrue(
                seen == false,
                "with the platform no longer asking for reduced motion the material still reads " +
                    "LocalReducedMotion as $seen, so the provider is installing a constant " +
                    "rather than the preference it was given",
            )
        }
    }

    /**
     * Every glass surface under the shell shares the shell's controller.
     *
     * The assertion that matters is the identity, not the count of reads: a fallback controller
     * would also be non-null, and before the provider existed each surface created its own. On
     * this host the platform controller is a shared singleton, so a test that only compared two
     * reads with each other could pass without the provider doing anything — which is why the
     * controller is handed in rather than taken from the platform.
     */
    val everyGlassSurfaceUnderTheShellSharesOneMotionController: suspend ComposeUiTest.() -> Unit = {
        val controller = RecordingMotionController()
        var first: DeviceMotionController? = null
        var second: DeviceMotionController? = null

        setContent {
            ShellMotionProvider(motion = controller) {
                first = rememberDeviceMotion()
                second = rememberDeviceMotion()
            }
        }

        runOnIdle {
            assertSame(
                controller,
                first,
                "the first glass surface under the shell did not get the shell's controller, so " +
                    "the shell is not providing one and each surface is creating its own",
            )
            assertSame(
                controller,
                second,
                "a second glass surface under the same shell got a different controller",
            )
            assertEquals(
                1,
                controller.started,
                "the shell's controller was started ${controller.started} times. One instance " +
                    "for the whole app is the point of providing it at the root; a sensor " +
                    "registered per surface is the defect this replaced",
            )
        }
    }

    /**
     * And the shell's controller is released when the shell is gone.
     *
     * A provider that starts a sensor and never stops one is the leak the provider was introduced
     * to close, so the stop is asserted rather than assumed — along with the start not happening
     * twice, which is what a provider that re-created its controller on every recomposition
     * would do.
     */
    val theShellsMotionControllerStopsWhenTheShellLeaves: suspend ComposeUiTest.() -> Unit = {
        val controller = RecordingMotionController()
        var shown by mutableStateOf(true)

        setContent {
            if (shown) ShellMotionProvider(motion = controller) { rememberDeviceMotion() }
        }

        runOnIdle { assertEquals(0, controller.stopped, "no stop while the shell is on screen") }
        runOnIdle { shown = false }
        runOnIdle {
            assertEquals(
                1,
                controller.stopped,
                "the shell left composition and its motion controller was still running " +
                    "(stopped ${controller.stopped} times), so the sensor outlives the app it " +
                    "belongs to",
            )
            assertEquals(1, controller.started, "the controller was started more than once")
        }
    }
}

/**
 * A motion controller that records its lifecycle and reports a level device.
 *
 * Level rather than something that moves, because these scenarios are about who owns the
 * controller and not about what it reports; a nonzero roll would only invite a future assertion
 * about the specular to be written here, where nothing renders it.
 */
private class RecordingMotionController : DeviceMotionController {
    override val roll: State<Float> = mutableStateOf(0f)
    override val pitch: State<Float> = mutableStateOf(0f)

    var started = 0
        private set
    var stopped = 0
        private set

    override fun start() {
        started++
    }

    override fun stop() {
        stopped++
    }
}
