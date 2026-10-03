package com.tasktracker.featureprojectspresentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the board's lane row comes to rest.
 *
 * ## Why this is tested as arithmetic
 *
 * [boardLaneSnapOffset] has no layout in it: its inputs are a scroll position, a stride and a
 * lane count, and its output is a distance to travel. That is the point of pulling it out of
 * [BoardLaneSnapLayoutInfo] - the interesting behaviour ("a fling moves one lane, a nudge
 * rounds to the nearest one") is a handful of comparisons over arithmetic, and arithmetic can
 * be asserted exactly.
 *
 * The alternative is a UI test that flings a real row and reads the resulting scroll offset,
 * which asserts the same rule while also asserting Compose's fling animation, the frame clock
 * and the device density. It would be slower, it would fail for reasons that are not this
 * function's, and it would only run on a target with a Compose UI harness. The edge-to-edge
 * layout rule, which genuinely *is* a layout fact, is asserted as a scenario in
 * `BoardLaneScenarios` instead - each kind of assertion in the kind of test that can carry it.
 *
 * ## The assertions are about resting positions, not offsets
 *
 * Every test below asks where the row ends up, via [restingAt], rather than how far it was
 * asked to travel. The two differ whenever the gesture did not start on a boundary, and the
 * resting position is the one a user can see. Asserting the offset instead would pin down
 * the same behaviour while quietly claiming that a back-fling from mid-lane is exactly one
 * stride long - it is not, and it should not be, since the row has to get *to* the lane
 * behind rather than merely move one lane's worth.
 *
 * ## The numbers
 *
 * Four 300dp lanes 8dp apart on a 3x screen, so a stride of 924px - the geometry measured on
 * the emulator. Named constants rather than inlined numbers, so each test reads as "half a
 * stride past a boundary" rather than as arithmetic someone has to re-derive.
 */
class BoardLaneSnapTest {

    @Test
    fun `a fling forwards moves exactly one lane`() {
        assertEquals(STRIDE, restingAt(scrollValue = 0f, velocity = FORWARD_FLING), "from rest")
        assertEquals(
            STRIDE,
            restingAt(scrollValue = 40f, velocity = FORWARD_FLING),
            "and from just short of a boundary, where the fling's direction is the only input",
        )
    }

    @Test
    fun `a fling backwards moves exactly one lane`() {
        assertEquals(STRIDE, restingAt(scrollValue = STRIDE * 2, velocity = BACKWARD_FLING))
        assertEquals(
            STRIDE,
            restingAt(scrollValue = STRIDE * 2 + 40f, velocity = BACKWARD_FLING),
            "from just past a boundary, the lane behind it rather than the one under it",
        )
    }

    @Test
    fun `a fling never moves more than one lane however hard it is thrown`() {
        // Compose asks this question repeatedly as a fling runs, so how far the row travels is
        // how many times it is asked. What has to hold for every one of those calls is that it
        // advances a single lane - a call that resolved the whole decay would let a hard flick
        // skip the lanes the user flicked across, and a target that can only be a lane
        // boundary cannot be wrong by more than half a lane.
        assertEquals(
            restingAt(scrollValue = 0f, velocity = FORWARD_FLING),
            restingAt(scrollValue = 0f, velocity = FORWARD_FLING * 40f),
            "a hard fling steps as far as a gentle one",
        )
    }

    @Test
    fun `stepping a fling to its end still lands on a lane`() {
        // The gesture the user actually makes: repeated calls with a falling velocity, ending on
        // the quiet one. Walked here rather than asserted in one call, because this is the shape
        // of the real thing - the lane the row rests on is whichever step the walk ends on, and
        // every step of it has to be a lane.
        var scroll = 0f
        repeat(4) { step ->
            scroll = restingAt(scrollValue = scroll, velocity = FORWARD_FLING / (step + 1))
            assertTrue(
                scroll % STRIDE == 0f || scroll == MAX_SCROLL,
                "a fling settling at $scroll is not on a lane",
            )
        }
        assertEquals(
            MAX_SCROLL,
            scroll,
            "four steps from rest carry the row to the end of the row and no further",
        )
    }

    @Test
    fun `a drag too slow to be a decision rounds to the nearest lane`() {
        assertEquals(
            STRIDE,
            restingAt(scrollValue = STRIDE - 40f, velocity = 0f),
            "40px short of the next lane is nearer that one",
        )
        assertEquals(
            STRIDE,
            restingAt(scrollValue = STRIDE + 40f, velocity = 0f),
            "40px past a lane is nearer the lane it just left, not the one it has not reached",
        )
        assertEquals(
            STRIDE * 2,
            restingAt(scrollValue = STRIDE + 500f, velocity = 0f),
            // 500 is past half a stride (462), so the lane ahead is the nearer one. The value
            // here was 450, which is 12px short of the halfway mark and so asserts the
            // *opposite* of what this line says — and it never ran, because the module's
            // `commonTest` did not compile until the `ttTheme` call in `WorkItemCardScenarios`
            // was fixed. A green-looking suite is not evidence that a red test is absent.
            "past the halfway mark the next lane wins instead",
        )
    }

    @Test
    fun `letting go exactly between two lanes goes back to the one behind`() {
        // Half a stride is the one distance at which the choice is a tie, so the test states
        // which way it falls rather than leaving it to chance. Falling backwards means a drag
        // never advances a lane on the strength of the final pixel.
        assertEquals(0f, restingAt(scrollValue = STRIDE / 2f, velocity = 0f))
    }

    @Test
    fun `the first lane is the end of the row in that direction`() {
        assertEquals(
            0f,
            restingAt(scrollValue = 0f, velocity = BACKWARD_FLING),
            "there is no lane before the first, so a back-fling from it goes nowhere",
        )
    }

    @Test
    fun `the last lane rests at the end of the row rather than on its own boundary`() {
        // Four lanes of 816px is a 3432px row against a 1280px screen, so the last lane's own
        // boundary at 2448px is well past the end of the scroll range. Aiming at 2448px would
        // fling the row at a position it cannot reach, and it would stall against the end with
        // the last lane still half off the screen.
        assertEquals(
            MAX_SCROLL,
            restingAt(scrollValue = MAX_SCROLL, velocity = FORWARD_FLING),
            "there is no room to take a lane more",
        )
        assertTrue(
            LANES * STRIDE > MAX_SCROLL,
            "the last lane's own boundary is unreachable, which is what this test is about",
        )
    }

    /**
     * The clamp at the end of the row costs the distance a fling, not a whole lane.
     *
     * The last lane cannot rest on its own boundary, so it rests short of it - and a fling
     * from there has to be read as *leaving that lane*, not as leaving the lane the arithmetic
     * happens to name. Getting that wrong means the first back-fling at the end of the row
     * goes back two lanes, which reads to the user as the row having skipped something.
     */
    @Test
    fun `a back-fling at the end of the row goes back exactly one lane`() {
        assertEquals(
            STRIDE * 2,
            restingAt(scrollValue = MAX_SCROLL, velocity = BACKWARD_FLING),
            "one lane back from where the row actually came to rest",
        )
    }

    @Test
    fun `a single lane has nothing to snap to`() {
        assertEquals(0f, boardLaneSnapOffset(0f, MAX_SCROLL, FORWARD_FLING, 1, STRIDE, MIN_FLING))
    }

    @Test
    fun `a row that fits on screen does not snap`() {
        assertEquals(0f, boardLaneSnapOffset(0f, 0f, FORWARD_FLING, LANES, STRIDE, MIN_FLING))
    }

    @Test
    fun `no resting position is ever outside the scrollable range`() {
        // The whole range, end to end, forward and back, above and below the velocity
        // threshold. Clamping is the property that makes the edge case above safe, and
        // clamping is exactly what regresses when the arithmetic is edited.
        var scroll = 0f
        while (scroll <= MAX_SCROLL) {
            listOf(FORWARD_FLING, BACKWARD_FLING, 0f, MIN_FLING / 2f).forEach { velocity ->
                val rest = restingAt(scrollValue = scroll, velocity = velocity)
                assertTrue(
                    rest in 0f..MAX_SCROLL,
                    "resting at $rest from $scroll at velocity $velocity is off the row",
                )
                // Every rest is a lane boundary, except the clamp at the end of the row -
                // the one place where a lane cannot be where it wants to be.
                assertTrue(
                    rest % STRIDE == 0f || rest == MAX_SCROLL,
                    "resting at $rest from $scroll is neither a lane boundary nor the end of the row",
                )
            }
            scroll += 7f
        }
    }

    /**
     * Where [boardLaneSnapOffset] leaves the row, rather than how far it was asked to go.
     *
     * Scroll values are whole pixels in [androidx.compose.foundation.ScrollState] and the
     * arithmetic in between is `Float`, so this adds the two back together exactly as the
     * fling animation does.
     */
    private fun restingAt(scrollValue: Float, velocity: Float): Float = scrollValue +
        boardLaneSnapOffset(
            scrollValue = scrollValue,
            maxScrollValue = MAX_SCROLL,
            velocity = velocity,
            columnCount = LANES,
            laneStridePx = STRIDE,
            minFlingVelocityPx = MIN_FLING,
        )
}

private const val LANES = 4
private const val STRIDE = 924f
private const val SCREEN_WIDTH = 1280f

/** Two 16dp gutters either side of four 300dp lanes with 8dp between them, at 3x. */
private const val CONTENT_WIDTH = 48f + LANES * 900f + (LANES - 1) * 24f + 48f
private const val MAX_SCROLL = CONTENT_WIDTH - SCREEN_WIDTH
private const val MIN_FLING = 1200f
private const val FORWARD_FLING = 4000f
private const val BACKWARD_FLING = -4000f
