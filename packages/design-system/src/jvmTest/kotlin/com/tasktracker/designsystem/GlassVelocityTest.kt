package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The selection lens's velocity, measured frame by frame on a stopped clock.
 *
 * ## What this replaced, and why the clock had to stop
 *
 * The estimate used to be differences of the lens's own position and `currentNanoTime()`, both
 * read during composition. Composition is not a frame: it happens when state changes, it can run
 * several times in one frame or not at all for many, and the delta-over-dt it produced was a
 * guess at what the lens was doing. It read 0 while the lens was moving and read large while it
 * was still, and because `ttGlassLens` draws its trail from this number, a wrong sign drew the
 * trail on the wrong side and a spike drew a full-strength blur.
 *
 * The velocity is now the spring's own — `Animatable.velocity`, in dp per second, which is the
 * unit [LensPlacement.velocityX] exposes. That makes it exact, and it makes it testable, but only
 * if the frames are: a spring integrated against wall-clock time is a different sequence on every
 * machine, so every test here stops the clock and steps it one frame at a time. Nothing in this
 * file reads the time of day, and nothing in it renders — the assertions are on the values
 * `ttLensPlacement` returns.
 *
 * ## What each test pins
 *
 * - Appearing is not travelling: the lens is drawn where the selection is, at rest, rather than
 *   sliding there from the origin and drawing a trail on its first frame.
 * - The sign is the direction of travel, in both directions.
 * - The magnitude is the distance being covered: three times the distance is three times the
 *   velocity, at the same frame, because it is the same spring.
 * - The travel is over inside [ttGlass.TAB_LENS_SETTLE_MILLIS]: a fraction of the peak velocity,
 *   and a fraction of a point per second by the time the spring has finished, with the lens on
 *   its target.
 */
@OptIn(ExperimentalTestApi::class)
class GlassVelocityTest {

    /**
     * A lens that has just appeared sits on its target and stays there.
     *
     * `positions` is populated here before the first composition, which is the frame the real
     * pickers reach once their segments have been measured — the frame the lens becomes visible
     * on. Six frames is a long enough window to catch a lens that had to travel to its place: at
     * this journey's distance the spring covers tens of dp in the first frame and hundreds by
     * the sixth, and every one of those dp would arrive with a velocity attached.
     */
    @Test
    fun `a lens that appears is already at rest`() {
        val appearance = journey(fromPx = 400, toPx = null, frames = 6)
        for (reading in appearance.frames) {
            assertTrue(
                abs(reading.xDp - appearance.targetDp) <= RESTING_TOLERANCE_DP,
                "on frame ${reading.millis} the lens is at ${reading.xDp} dp rather than the " +
                    "${appearance.targetDp} dp its selection is at. A lens that has just been " +
                    "placed must start where it belongs; travelling there from the animatable's " +
                    "zero sentinel is the phantom trail this estimate exists to avoid",
            )
            assertTrue(
                abs(reading.velocityDp) <= RESTING_VELOCITY_DP,
                "on frame ${reading.millis} the lens reports ${reading.velocityDp} dp/s while " +
                    "standing still. At rest the velocity must be zero, not merely small: " +
                    "ttGlassLens draws its trail from this number",
            )
        }
    }

    /**
     * The sign of the velocity is the direction the lens is going.
     *
     * The frames before the spring starts are skipped rather than asserted on: the effect that
     * starts it is launched from the recomposition the selection change causes, and an animation's
     * first frame follows that. Skipped by magnitude rather than by equality, because the value a
     * not-yet-integrated spring reports is zero only up to float noise — a millionth of a dp per
     * second is not a velocity and it is not a direction. The count is then checked, because
     * "every frame is positive" is free if no frame is moving at all.
     *
     * Ten frames is deliberate. A spring at this damping ratio passes through its target at about
     * 220 ms, and past that the velocity legitimately points backwards while the lens settles back
     * onto the target; asserting a strict sign through the overshoot would be asserting that the
     * spring is not a spring. This window ends well inside the approach.
     */
    @Test
    fun `the velocity points the way the lens is going`() {
        val right = journey(fromPx = 0, toPx = 400, frames = 10)
        val left = journey(fromPx = 400, toPx = 0, frames = 10)
        val rightApproach = right.frames.dropWhile { abs(it.velocityDp) <= NOISE_VELOCITY_DP }
        val leftApproach = left.frames.dropWhile { abs(it.velocityDp) <= NOISE_VELOCITY_DP }

        assertTrue(
            rightApproach.size >= MOVING_FRAMES,
            "travelling right the lens reported ${right.velocities()}, of which only " +
                "${rightApproach.size} frames are moving. There is nothing here to take a " +
                "direction from",
        )
        assertTrue(
            rightApproach.all { it.velocityDp > 0f },
            "travelling right the lens reported ${right.velocities()}. Every frame of the " +
                "approach must be positive; a negative one draws the trail on the side the " +
                "lens is not moving toward",
        )
        assertTrue(
            leftApproach.all { it.velocityDp < 0f },
            "travelling left the lens reported ${left.velocities()}. Every frame of the " +
                "approach must be negative; a positive one draws the trail on the side the " +
                "lens is not moving toward",
        )
        assertTrue(
            right.frames.last().xDp > right.originDp && left.frames.last().xDp < left.originDp,
            "the lens did not travel: it went from ${right.originDp} to " +
                "${right.frames.last().xDp} dp on the right-hand journey and from " +
                "${left.originDp} to ${left.frames.last().xDp} dp on the left-hand one",
        )
    }

    /**
     * Three times the distance is three times the velocity.
     *
     * The springs are the same spring, started from rest on the same frame grid, and a
     * mass-spring-damper is linear in its displacement: v(t) = d * f(t) for both journeys, so the
     * ratio is the ratio of the distances and nothing else. A velocity that came from frame
     * timing instead could land anywhere — which is exactly the noise this replaced.
     */
    @Test
    fun `velocity grows with the distance the lens is covering`() {
        val near = journey(fromPx = 0, toPx = NEAR_TRAVEL_PX, frames = 10)
        val far = journey(fromPx = 0, toPx = NEAR_TRAVEL_PX * 3, frames = 10)

        assertTrue(
            near.peakVelocityDp > 0f,
            "the near journey drew no velocity at all, so there is nothing to scale",
        )
        val ratio = far.peakVelocityDp / near.peakVelocityDp
        assertTrue(
            abs(ratio - 3f) <= SCALING_TOLERANCE,
            "three times the distance travelled at ${ratio} times the velocity: the near " +
                "journey peaked at ${near.peakVelocityDp} dp/s and the far one at " +
                "${far.peakVelocityDp} dp/s. They are the same spring from rest, so the " +
                "magnitude has to scale with the distance rather than with the frame timing",
        )
    }

    /**
     * The travel is over inside the settle window the bar already commits to.
     *
     * [ttGlass.TAB_LENS_SETTLE_MILLIS] is the window the tab bar's own lens stretch runs for, and
     * the one this estimate has to be quiet inside: at the mark the velocity is a few per cent of
     * its peak, and by the time the spring has finished the lens is on its target reporting
     * nothing at all.
     *
     * The window is deliberately not asserted as "the lens has arrived". At this damping ratio the
     * spring overshoots its target by about four per cent and comes back, so at the mark the lens
     * is still 20 dp out on a 480 dp journey with its velocity pointing the other way — which is
     * the spring that was already here, not something this file introduced. What is asserted is
     * the part the trail depends on: by the mark the velocity has given up its peak, and it ends
     * at rest on the target rather than creeping toward it forever.
     *
     * The share is scale-free on purpose. A velocity scales with the distance travelled, so an
     * absolute figure would be a statement about this journey rather than about the spring — a
     * “quiet by X dp/s” assertion would have to be a different number for a two-tab segmented
     * control and a ten-chip flow picker, which is the shape the trail's own 500 dp/s threshold
     * already has and is wrong for the same reason.
     */
    @Test
    fun `the spring has settled inside the settle window`() {
        val travel = journey(fromPx = 0, toPx = 480, frames = 90)
        val peak = travel.peakVelocityDp
        assertTrue(peak > 0f, "the journey under test never moved")

        val atWindow = travel.frameAt(ttGlass.TAB_LENS_SETTLE_MILLIS.toLong())
        assertTrue(
            abs(atWindow.velocityDp) <= peak * SETTLED_SHARE,
            "at ${atWindow.millis} ms — the ${ttGlass.TAB_LENS_SETTLE_MILLIS} ms settle window " +
                "— the lens is still reporting ${atWindow.velocityDp} dp/s against a peak of " +
                "$peak dp/s. The spring has to have given up by then",
        )

        val finished = travel.frames.last()
        assertTrue(
            abs(finished.velocityDp) <= FINISHED_VELOCITY_DP,
            "at ${finished.millis} ms the spring is still moving at ${finished.velocityDp} " +
                "dp/s, so it is not settling at all — a trail out of a lens that has stopped " +
                "is the one this estimate must never draw",
        )
        assertTrue(
            abs(finished.xDp - travel.targetDp) <= FINISHED_TOLERANCE_DP,
            "at ${finished.millis} ms the lens has stopped at ${finished.xDp} dp rather than " +
                "the ${travel.targetDp} dp of its selection",
        )
    }

    /**
     * Reduced motion still moves the lens, silently.
     *
     * The glass-4 local snaps this spring rather than stretching it, and a snapped lens is a lens
     * whose velocity is zero on every frame — including the frames it is moving on. A trail drawn
     * from a jump would be a full-strength blur for exactly one frame, which is the one thing
     * reduced motion is asked not to do.
     */
    @Test
    fun `a reduced-motion lens moves without a velocity`() {
        val travel = journey(fromPx = 0, toPx = 400, frames = 4, reducedMotion = true)
        for (reading in travel.frames) {
            assertTrue(
                abs(reading.velocityDp) <= RESTING_VELOCITY_DP,
                "under reduced motion the lens reports ${reading.velocityDp} dp/s on frame " +
                    "${reading.millis}; a snap has no spring to take a velocity from",
            )
        }
        val finished = travel.frames.last()
        assertTrue(
            abs(finished.xDp - travel.targetDp) <= RESTING_TOLERANCE_DP,
            "under reduced motion the lens is at ${finished.xDp} dp after ${finished.millis} ms " +
                "rather than the ${travel.targetDp} dp it was sent to; snapping means the target " +
                "is reached at once, not that the lens stops",
        )
    }

    /**
     * One lens over two segments, stepped frame by frame.
     *
     * [toPx] is the left edge in pixels of the segment the selection is switched to, or `null` for
     * a journey that never switches — the composition where the lens simply appears and the
     * frames that follow must not move it. Everything is in pixels because a segment rectangle is
     * what the real hosts measure, and the expectations are converted to dp against the density
     * the composition itself reported rather than against the host's, so the numbers hold on any
     * display.
     */
    private fun journey(
        fromPx: Int,
        toPx: Int?,
        frames: Int,
        reducedMotion: Boolean = false,
    ): Journey {
        var readings: List<Reading> = emptyList()
        var originDp = 0f
        var targetDp = 0f

        runComposeUiTest {
            mainClock.autoAdvance = false

            var selection by mutableStateOf(0)
            val probe = LensProbe()
            val positions = buildMap {
                put(0, segment(fromPx))
                if (toPx != null) put(1, segment(toPx))
            }

            setContent {
                LensHost(
                    positions = positions,
                    selection = selection,
                    probe = probe,
                    reducedMotion = reducedMotion,
                )
            }
            // The frame the lens is placed on. In the journey that switches, this is the frame
            // that recomposes with the new selection, so the spring starts from here.
            mainClock.advanceTimeByFrame()
            val placed = probe.require(frame = 0)
            originDp = placed.x.value
            targetDp = (toPx ?: fromPx) / probe.density

            // The clock's own reading, so the settle window is measured from the moment the
            // selection changed rather than from an assumed frame length.
            val changedAt = mainClock.currentTime
            if (toPx != null) selection = 1
            val collected = mutableListOf<Reading>()
            for (frame in 1..frames) {
                mainClock.advanceTimeByFrame()
                val placement = probe.require(frame)
                collected += Reading(
                    millis = mainClock.currentTime - changedAt,
                    velocityDp = placement.velocityX.value,
                    xDp = placement.x.value,
                )
            }
            readings = collected
        }

        return Journey(readings, originDp, targetDp)
    }

    /** A segment rectangle 200 px wide, at [left]. */
    private fun segment(left: Int) = IntRect(
        left = left,
        top = 0,
        right = left + SEGMENT_WIDTH_PX,
        bottom = SEGMENT_HEIGHT_PX,
    )

    private companion object {
        const val SEGMENT_WIDTH_PX = 200
        const val SEGMENT_HEIGHT_PX = 80
        const val NEAR_TRAVEL_PX = 160

        /**
         * The most movement allowed of a lens that is supposed to be at rest.
         *
         * A hundredth of a dp, against a trail threshold of five hundred and an estimate that
         * used to report thousands while the lens stood still. This is not room for an estimate to
         * be a little wrong — it is the float noise of a velocity that is genuinely zero, and the
         * step from here to a phantom trail is five orders of magnitude wide.
         */
        const val RESTING_VELOCITY_DP = 0.01f

        /** The most a resting lens may differ from its target, in dp. */
        const val RESTING_TOLERANCE_DP = 0.5f

        /**
         * How much of the peak velocity may still be showing at the settle mark.
         *
         * A tenth, against a measured three hundredths on a 480 dp journey: the spring's envelope
         * at that mark is about a fortieth of its peak, so what is measured is the decay the
         * spring is supposed to have and not a threshold raised to pass. A tenth leaves an
         * assertion that would have to lose three quarters of that decay to fail.
         */
        const val SETTLED_SHARE = 0.10f

        /**
         * What "settled" means once the spring has finished: a hundredth of a dp per second.
         *
         * The same number as [RESTING_VELOCITY_DP], and for the same reason — the spring ends
         * where a lens at rest is, and the assertion that it does is what stops a change that
         * leaves it creeping forever from passing.
         */
        const val FINISHED_VELOCITY_DP = 0.01f

        /** How close to its target a finished lens has to be, in dp. */
        const val FINISHED_TOLERANCE_DP = 0.5f

        /** How far the velocity ratio of two proportional springs may drift. */
        const val SCALING_TOLERANCE = 0.02f

        /** The fewest moving frames a ten-frame approach has to show to be worth asserting on. */
        const val MOVING_FRAMES = 6

        /**
         * A millionth of a dp per second: the noise floor between "the spring has not integrated
         * yet" and "it has".
         *
         * The frames before an animation's first step report a velocity of zero, and what arrives
         * instead is the float residue of the integration — this was written after one such frame
         * reported `-9.094947E-13` and a strict `== 0f` read it as travelling backwards. It is not
         * a velocity: the trail's threshold is five hundred dp/s, and this is nine orders of
         * magnitude below it.
         */
        const val NOISE_VELOCITY_DP = 1e-6f
    }
}

/** What the lens reported on one frame. */
private class Reading(val millis: Long, val velocityDp: Float, val xDp: Float)

/**
 * A stepped journey, and where it started and was going.
 *
 * [millis] on a [Reading] is the test clock's own reading, relative to the moment the selection
 * changed, so an assertion about the settle window is an assertion about the time the spring has
 * had rather than about a frame count.
 */
private class Journey(
    val frames: List<Reading>,
    val originDp: Float,
    val targetDp: Float,
) {
    val peakVelocityDp: Float = frames.maxOf { abs(it.velocityDp) }

    fun velocities(): List<Float> = frames.map { it.velocityDp }

    /** The first frame at or after [millis]. */
    fun frameAt(millis: Long): Reading = frames.first { it.millis >= millis }
}

/**
 * Writes down what `ttLensPlacement` returned, and the density it was composed at.
 *
 * A plain class rather than Compose state on purpose: a `mutableStateOf` here would invalidate
 * the composition it is written from, and the frame being measured would be measuring the probe.
 */
private class LensProbe {
    var density: Float = 0f
        private set
    private var latest: LensPlacement? = null

    fun record(placement: LensPlacement, density: Float) {
        this.latest = placement
        this.density = density
    }

    fun require(frame: Int): LensPlacement = requireNotNull(latest) {
        "the host reported no placement by frame $frame, so there is nothing to measure"
    }
}

/**
 * The lens, over [positions], with nothing around it.
 *
 * Deliberately not the pickers. They are what call `ttLensPlacement`, but they also measure their
 * segments from a laid-out tree and place the lens with `.offset()`, and neither of those is what
 * this file asserts: the velocity is a function of the placement's target and the spring, so the
 * rectangles go in directly and the value that comes back is the whole subject.
 */
@Composable
private fun LensHost(
    positions: Map<Int, IntRect>,
    selection: Int,
    probe: LensProbe,
    reducedMotion: Boolean,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        probe.record(ttLensPlacement(positions, selection, density), density.density)
    }
}
