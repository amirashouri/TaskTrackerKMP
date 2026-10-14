package com.tasktracker.sharedui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The boundary of [ShellLayoutClass], and only the boundary.
 *
 * ## Why this needs no composition, and therefore no per-host entry points
 *
 * [ShellLayoutClass.forWidth] is a pure function of a `Dp`, so every question about *where the
 * line is* is answerable without a frame. `AGENTS.md` puts it as one question — can the failing
 * assertion be written without rendering anything — and here it can, which is why these are
 * plain `@Test`s that run on all three hosts with no `commonTest`-body-plus-entry-points split.
 *
 * The other half is deliberately not here. That `ShellLayout` actually reads the width it was
 * offered, that it publishes the class below itself, and that
 * [LocalShellLayoutClass] defaults to `Compact` outside the shell are all facts about a
 * composition, and they belong in `ShellScenarios` where `setUpShell(width = …)` already
 * constrains the parent and every host can drive it. Splitting them that way is what keeps this
 * file free of a Compose test harness for questions that do not need one.
 *
 * ## What the boundary assertions are for
 *
 * `>=` and `>` differ by exactly one value, and a test suite that only ever tries round numbers
 * a comfortable distance from the line cannot tell them apart — which is how a threshold
 * implemented with the wrong comparison ships green. So the line is tried from both sides, and
 * from *half a point* below, and the monotonicity sweep below catches an implementation that
 * flips back on the other side of the line.
 */
class ShellLayoutClassTest {

    /**
     * The phone case, at a real phone width.
     *
     * 390dp is the width the compact shell is actually designed against, and it is here rather
     * than a synthetic round number so that the assertion is about the app as shipped. If this
     * one ever goes red the phone layout is gone, whatever else passes.
     */
    @Test
    fun `a phone width is compact`() {
        assertEquals(ShellLayoutClass.Compact, ShellLayoutClass.forWidth(390.dp))
    }

    /** The last compact width, one point below the threshold. */
    @Test
    fun `a width just below the threshold is compact`() {
        assertEquals(ShellLayoutClass.Compact, ShellLayoutClass.forWidth(839.dp))
    }

    /**
     * Half a point below the threshold, which is what pins `>=` against `>`.
     *
     * The only assertion in this file that a `>` implementation fails and a `>=` one passes,
     * and it is worth its own test rather than being folded into the one above: at whole
     * numbers the two comparisons agree, so `839.dp` alone proves nothing about which was
     * written.
     */
    @Test
    fun `a fractional width just below the threshold is compact`() {
        assertEquals(ShellLayoutClass.Compact, ShellLayoutClass.forWidth(839.5.dp))
    }

    /**
     * The threshold itself is already expanded, which is the other half of the same decision.
     *
     * Written against the constant rather than against a literal, so this keeps testing the
     * *comparison* if the value ever moves. The value itself is pinned separately, below, so
     * the two assertions fail for different reasons and a reader can tell which one broke.
     */
    @Test
    fun `the threshold width itself is expanded`() {
        assertEquals(
            ShellLayoutClass.Expanded,
            ShellLayoutClass.forWidth(ShellExpandedMinimumWidth),
        )
    }

    /** A real desktop window, which is the case the whole section exists for. */
    @Test
    fun `a typical desktop window is expanded`() {
        assertEquals(ShellLayoutClass.Expanded, ShellLayoutClass.forWidth(1280.dp))
    }

    /**
     * Zero is compact rather than an error.
     *
     * A parent can genuinely offer no width — a collapsed split pane, a first frame before the
     * window is measured — and the answer has to be the layout that fits in nothing, not a
     * crash on a launch path. `Compact` is the only defensible reading of "no room".
     */
    @Test
    fun `a zero width is compact`() {
        assertEquals(ShellLayoutClass.Compact, ShellLayoutClass.forWidth(0.dp))
    }

    /**
     * An unbounded parent is expanded, and this is not a defensive branch.
     *
     * `BoxWithConstraints` reports `Dp.Infinity` for `maxWidth` inside a scrolling or otherwise
     * unbounded parent, so this is a value the app can really produce. The assertion exists to
     * stop the obvious later "fix" — an `isFinite` guard that treats unmeasured as compact —
     * from silently demoting a wide screen to the phone layout the moment it is put inside a
     * scrollable.
     */
    @Test
    fun `an unbounded width is expanded`() {
        assertEquals(ShellLayoutClass.Expanded, ShellLayoutClass.forWidth(Dp.Infinity))
    }

    /**
     * Widening never takes a screen back to the phone layout.
     *
     * A sweep rather than a sample, because the failure this catches is not a wrong constant:
     * it is an implementation with more than one condition in it — a floor as well as a ceiling,
     * an upper bound that forgets to be inclusive — which passes every single-point assertion
     * above while being wrong somewhere in between. `first` is found and then every width above
     * it is checked, so the test names the width where monotonicity actually broke rather than
     * reporting a boolean.
     */
    @Test
    fun `widening never returns to the compact layout`() {
        var firstExpanded: Dp? = null
        for (points in 0..2400) {
            val width = points.dp
            val resolved = ShellLayoutClass.forWidth(width)
            if (resolved == ShellLayoutClass.Expanded) {
                if (firstExpanded == null) firstExpanded = width
            } else {
                assertTrue(
                    firstExpanded == null,
                    "width $width resolved to Compact after $firstExpanded had resolved to Expanded",
                )
            }
        }
        assertTrue(
            firstExpanded != null,
            "no width up to 2400dp resolved to Expanded, so the wide layout is unreachable",
        )
    }

    /**
     * The threshold is held to its exact value.
     *
     * The `AGENTS.md` rule about numbers a test already holds to an exact value, applied to a
     * layout number rather than a colour: this is the one value in the file that cannot be
     * derived from anything else, and moving it silently changes which windows get which shell.
     * Pinning it makes the change a deliberate edit with a visible diff instead of a drift.
     */
    @Test
    fun `the threshold is pinned to the value it was chosen at`() {
        assertEquals(840.dp, ShellExpandedMinimumWidth)
    }
}
