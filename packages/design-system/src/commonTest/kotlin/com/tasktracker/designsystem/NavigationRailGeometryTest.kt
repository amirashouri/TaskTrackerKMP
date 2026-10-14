package com.tasktracker.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rail's vertical geometry, asserted without a frame.
 *
 * ## Why these are pure functions and not a rendered shell
 *
 * The two decisions this file exists for — that a segment comes from the rail's *measured*
 * height, and that the lens is clamped inside the rail at the ends of its travel — are both
 * arithmetic. [TTGlassNavigationRail] reads them out of a [androidx.compose.foundation.layout.BoxWithConstraints]
 * on the measured cell column and hands them to [railSegmentHeight] and [railLensTop], so the
 * rules can be tried at any rail height without standing a composition up.
 *
 * That matters here more than it usually would: this module has no Robolectric runner, so a
 * `runComposeUiTest` in `commonTest` dies in its own environment setup on the Android host (see
 * [thisHostRasterisesText]). A composed assertion here would run on two hosts and quietly not
 * run on the third. These run on all three.
 *
 * ## What is deliberately *not* here
 *
 * The composed placement — the rail's actual width and height at the real caption metrics, and
 * whether the lens is inset across as well as along — is measured in `:shared-ui`'s shell
 * scenarios, which do have per-host entry points and drive the real widths through
 * `setUpShell(width = …)`. Putting them here would measure the rail without the app around it,
 * and would put a bounds assertion behind the rasterisation gate for no gain.
 */
class NavigationRailGeometryTest {

    /**
     * Five destinations divide the rail's height evenly, and nothing is lost at the bottom.
     *
     * The equality at the end is the half worth reading. Dividing a height that does not divide
     * evenly leaves a remainder, and a remainder has to land somewhere: if the segment were
     * rounded rather than divided, the last destination's slice would end short of the rail's
     * bottom edge and the lens would sit above the cell it belongs to. Asserting that the slices
     * add back up to the rail is what catches that, and 275 over 5 divides evenly while 100 over
     * 3 does not — so the next test is the one that can actually fail.
     */
    @Test
    fun `every destination gets an equal slice of the rail`() {
        val railHeight = 275.dp
        val destinations = 5

        val segment = railSegmentHeight(railHeight, destinations)

        assertEquals(
            55.dp,
            segment,
            "a ${railHeight.value}dp rail with $destinations destinations gives each one " +
                "${segment.value}dp, and every destination has to get the same slice or the " +
                "lens parks between two of them",
        )
        assertEquals(
            railHeight.value,
            segment.value * destinations,
            "five slices of ${segment.value}dp add up to ${segment.value * destinations}dp against " +
                "a rail of ${railHeight.value}dp, so the remainder is being dropped from the " +
                "bottom and the last destination's lens will sit above its own cell",
        )
    }

    /**
     * The slices still add up when the division does not come out even.
     *
     * The only test above that could fail by being made to use an awkward height, which is the
     * point of this one. 100 over 3 is 33.333…, and the question is whether the rail's height is
     * preserved or whether the rounding eats it.
     */
    @Test
    fun `the slices still add up when the division does not come out even`() {
        val railHeight = 100.dp
        val destinations = 3

        val segment = railSegmentHeight(railHeight, destinations)

        assertEquals(
            railHeight.value,
            segment.value * destinations,
            "three slices of ${segment.value}dp add up to ${segment.value * destinations}dp " +
                "against a rail of ${railHeight.value}dp. Dp division carries the fraction, so " +
                "this only fails if the segment is being rounded somewhere",
        )
    }

    /**
     * The lens is centred on the selected destination at every index, not just the first.
     *
     * This is the assertion that distinguishes a segment derived from the rail's measured
     * content from one derived from an offered height. Both give the right answer at index 0 —
     * everything starts at the top — and they part company as soon as the lens travels: travel
     * accumulates one segment per index, so if the segment is even slightly wrong the error
     * compounds and the last destination's lens lands somewhere nobody put it.
     *
     * ## Why it asserts an exact centre and not a containment
     *
     * The first version of this test asked only that the lens's centre fell *inside* its
     * destination's slice, and it passed against a mutation that dropped the half-segment
     * centring entirely. That is not a near miss and the arithmetic says why: at 55dp per
     * segment the lens is 49dp tall, so the centre may sit a quarter of a segment either side of
     * the middle and still be inside — roughly 27dp of slack on each side. A containment check
     * over a shape that nearly fills the thing containing it is not a check.
     *
     * Exact equality is available and is the right assertion here because at rest the clamp
     * cannot engage: the lens is shorter than its segment by construction (`segment − 2 × inset`),
     * so the first destination's centred position is already positive and the last one's is still
     * within the rail's extent. Nothing rounds, because [Dp] division carries the fraction.
     *
     * ## The inset is not in this arithmetic, and that is the other half
     *
     * An earlier version added [ttSpacing.tabBarLensInset] to the lens's top edge and failed on
     * every index by exactly the inset. The lens's *travel* axis is y and its cross axis is x, so
     * the inset belongs on x — the rail's width, not its height — and the y position is the
     * clamped centre and nothing else. [TTGlassNavigationRail] writes the pair as
     * `offset(y = top, x = …)`, which is the bar's `offset(x = left, y = …)` with the axes
     * swapped, and reading it the other way round costs a whole inset of drift on every index.
     */
    @Test
    fun `the lens is centred on the selected destination at every index`() {
        val railHeight = 275.dp
        val destinations = 5
        val segment = railSegmentHeight(railHeight, destinations)
        val lensHeight = segment - ttSpacing.tabBarLensInset * 2

        val readings = (0 until destinations).map { index ->
            val sliceCentre = segment * index + segment / 2f
            val top = railLensTop(
                travelDp = segment * index,
                segment = segment,
                lensHeight = lensHeight,
                railHeight = railHeight,
            )
            (index to (top + lensHeight / 2f)) to sliceCentre
        }

        val misplaced = readings.filter { (placement, sliceCentre) ->
            placement.second != sliceCentre
        }

        assertTrue(
            misplaced.isEmpty(),
            "the lens is not centred on destination(s) " +
                misplaced.joinToString { "${it.first.first}" } + " of $destinations. Rail " +
                "${railHeight.value}dp, segment ${segment.value}dp, lens ${lensHeight.value}dp " +
                "at rest. " + readings.joinToString { (placement, sliceCentre) ->
                    val (index, centre) = placement
                    "index $index centre ${centre.value} should be ${sliceCentre.value} " +
                        "(off by ${(centre - sliceCentre).value})"
                },
        )
    }

    /**
     * A lens taller than the rail is clamped, and does not throw.
     *
     * `coerceIn` throws on an inverted range rather than clamping, so a rail short enough for the
     * lens to be taller than it is a real crash rather than an ugly placement. A short desktop
     * window can genuinely produce it. `coerceAtLeast` on the bound is what makes this return
     * instead.
     */
    @Test
    fun `a lens taller than its rail is clamped rather than throwing`() {
        val railHeight = 40.dp
        val lensHeight = 60.dp

        val top = railLensTop(
            travelDp = 0.dp,
            segment = 20.dp,
            lensHeight = lensHeight,
            railHeight = railHeight,
        )

        assertEquals(
            0.dp,
            top,
            "with a ${lensHeight.value}dp lens in a ${railHeight.value}dp rail the range " +
                "0..${(railHeight - lensHeight).value}dp is inverted, and coerceIn throws on that " +
                "rather than clamping. The lens should sit at the top of the rail it cannot fit in",
        )
    }

    /**
     * The clamp pulls an over-tall lens back inside the rail at both ends of its travel.
     *
     * ## The lens has to be made over-tall on purpose, and this is why
     *
     * The first version of this test applied the real stretch token to the real lens and asserted
     * that the lens stayed inside the rail. It passed with the clamp deleted outright, because with
     * the tokens this app ships the clamp never engages: a 275dp rail over five destinations is a
     * 55dp segment, the lens at rest is 49dp, and [ttGlass.TAB_LENS_WIDEN] takes it to **53.9dp —
     * still shorter than the segment it sits in**. The assertion was `coerceIn`'s own
     * postcondition, so it could not fail while the clamp was present, and did not fail without it.
     *
     * The clamp engages only when the lens is *taller than its segment*, which at the shipped
     * stretch factor needs a segment over 66dp. Five destinations in an ordinary window do not
     * produce one, so in the app today this is a guard against a degenerate configuration — a very
     * short window, or a rail carrying far more destinations than a caption's height — rather than
     * a live code path. That is worth stating rather than leaving a test that implies otherwise,
     * so the lens here is constructed taller than its segment and the clamp is made to do its job.
     *
     * The assertion is on where the lens *ends up*, not on the inequality the clamp guarantees,
     * because restating the guarantee is precisely what made the first version untestable.
     */
    @Test
    fun `the clamp pulls an over-tall lens back inside the rail at both ends of its travel`() {
        val railHeight = 275.dp
        val destinations = 5
        val segment = railSegmentHeight(railHeight, destinations)
        val lensHeight = segment + OVER_TALL_BY

        // First destination: the lens overhangs the top, so the low clamp has to pull it down.
        assertEquals(
            0.dp,
            railLensTop(
                travelDp = 0.dp,
                segment = segment,
                lensHeight = lensHeight,
                railHeight = railHeight,
            ),
            "a ${lensHeight.value}dp lens at the first destination sits " +
                "${(segment / 2f - lensHeight / 2f).value}dp above the rail's top edge, and the " +
                "clamp has to hold it at the edge rather than let it overhang",
        )

        // Last destination: the lens overhangs the bottom, so the high clamp has to pull it up.
        assertEquals(
            railHeight - lensHeight,
            railLensTop(
                travelDp = segment * (destinations - 1),
                segment = segment,
                lensHeight = lensHeight,
                railHeight = railHeight,
            ),
            "at the last destination the lens runs ${OVER_TALL_BY.value}dp past the rail's bottom " +
                "edge, and the clamp has to pull it back to end exactly at ${railHeight.value}dp " +
                "rather than pushing through the rail's rounded end",
        )

        // And unclamped it would not, which is the only thing that makes the two assertions above
        // mean something. Checked separately because a test that cannot tell "the clamp worked"
        // from "the clamp was never needed" is the defect this file exists to avoid.
        val unclampedEnd = segment * (destinations - 1) + segment / 2f - lensHeight / 2f
        assertTrue(
            unclampedEnd + lensHeight > railHeight,
            "without the clamp the lens would end at ${unclampedEnd + lensHeight}dp in a " +
                "${railHeight.value}dp rail, which is inside it — so the two assertions above " +
                "would be proving that the clamp had nothing to do",
        )
    }

    /** How far past its segment the constructed lens overhangs, at each end of the travel. */
    private val OVER_TALL_BY = 9.dp

    /**
     * The lens never grows taller than the cell it is marking, at any point in its settle.
     *
     * ## Why a settled frame cannot see this
     *
     * At rest the stretch is zero and the lens is `segment - 2 * inset` — 140dp inside a 146dp
     * cell — so it fits with room to spare and a test that renders the rail and measures it says
     * nothing about whether the stretch is capped. The overrun happens only at the **peak** of the
     * settle, which is also what a person sees. Driving the animation from a test proved awkward
     * and unreliable: stepping one frame into the settle with a manual clock did not consistently
     * catch the clamp being removed. Hence a pure function and no clock.
     *
     * The figures are the ones measured on the rail this ships: a 736dp rail, five destinations,
     * a 146dp segment and a 140dp lens.
     */
    @Test
    fun `the lens never grows taller than the cell it is marking`() {
        val segment = 146.dp
        val lensAtRest = segment - ttSpacing.tabBarLensInset * 2

        val atRest = railLensHeight(lensAtRest, segment, stretch = 0f)
        val atPeak = railLensHeight(lensAtRest, segment, stretch = 1f)

        assertEquals(
            lensAtRest,
            atRest,
            "at rest the lens should be its own size, ${lensAtRest.value}dp",
        )
        assertTrue(
            atRest.value <= segment.value,
            "at rest the lens is ${atRest.value}dp inside a ${segment.value}dp segment",
        )
        assertEquals(
            segment,
            atPeak,
            "a ${lensAtRest.value}dp lens stretched by ${ttGlass.TAB_LENS_WIDEN} wants " +
                "${lensAtRest.value * (1f + ttGlass.TAB_LENS_WIDEN)}dp, which is " +
                "${lensAtRest.value * (1f + ttGlass.TAB_LENS_WIDEN) - segment.value}dp past the " +
                "${segment.value}dp cell it is drawn under. The clamp should hold it at the " +
                "segment: a selection indicator may not be taller than the thing it indicates, and " +
                "its top edge still lands correctly either way, which is why this only shows up " +
                "when the bottom edge is measured",
        )
    }
}
