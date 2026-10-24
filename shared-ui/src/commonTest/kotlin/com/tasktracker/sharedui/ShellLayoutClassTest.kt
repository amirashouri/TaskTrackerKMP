package com.tasktracker.sharedui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /** A wide desktop window below the sidebar threshold, which must keep the rail. */
    @Test
    fun `a typical desktop window is expanded`() {
        assertEquals(ShellLayoutClass.Expanded, ShellLayoutClass.forWidth(1000.dp))
    }

    /**
     * The last rail width, one point below the sidebar threshold.
     *
     * 1000dp is the width every existing wide scenario uses, and it must keep resolving to the
     * rail; this is the guard that makes adding the sidebar a one-class change for the tests that
     * already existed.
     */
    @Test
    fun `a width just below the sidebar threshold is expanded`() {
        assertEquals(ShellLayoutClass.Expanded, ShellLayoutClass.forWidth(1119.dp))
    }

    /** Half a point below the sidebar threshold, which pins `>=` against `>` there too. */
    @Test
    fun `a fractional width just below the sidebar threshold is expanded`() {
        assertEquals(ShellLayoutClass.Expanded, ShellLayoutClass.forWidth(1119.5.dp))
    }

    /** The sidebar threshold itself is already a sidebar, which is the other half of that pin. */
    @Test
    fun `the sidebar threshold itself is a sidebar`() {
        assertEquals(
            ShellLayoutClass.Sidebar,
            ShellLayoutClass.forWidth(ShellSidebarMinimumWidth),
        )
    }

    /** The Desktop app's default window, and the case the sidebar exists for. */
    @Test
    fun `a very wide desktop window is a sidebar`() {
        assertEquals(ShellLayoutClass.Sidebar, ShellLayoutClass.forWidth(1280.dp))
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
     * An unbounded width is the sidebar, and this is not a defensive branch.
     *
     * `BoxWithConstraints` reports `Dp.Infinity` for `maxWidth` inside a scrolling or otherwise
     * unbounded parent, so this is a value the app can really produce. The assertion exists to
     * stop the obvious later "fix" — an `isFinite` guard that treats unmeasured as compact —
     * from silently demoting a wide screen to the phone layout the moment it is put inside a
     * scrollable.
     */
    @Test
    fun `an unbounded width is a sidebar`() {
        assertEquals(ShellLayoutClass.Sidebar, ShellLayoutClass.forWidth(Dp.Infinity))
    }

    /**
     * Widening never returns to a narrower layout.
     *
     * A sweep rather than a sample, because the failure this catches is not a wrong constant:
     * it is an implementation with more than one condition in it — a floor as well as a ceiling,
     * an upper bound that forgets to be inclusive — which passes every single-point assertion
     * above while being wrong somewhere in between. The rank is compared rather than a single
     * "is expanded" boolean, so a width that steps *back* from `Sidebar` to `Expanded` is caught
     * as loudly as one that falls to `Compact`. The message names the width where it broke rather
     * than reporting a boolean.
     */
    @Test
    fun `widening never returns to a narrower layout`() {
        var previous: Pair<Dp, ShellLayoutClass>? = null
        for (points in 0..2400) {
            val width = points.dp
            val resolved = ShellLayoutClass.forWidth(width)
            previous?.let { (previousWidth, previousClass) ->
                assertTrue(
                    resolved.rank() >= previousClass.rank(),
                    "width $width resolved to $resolved after $previousWidth resolved to " +
                        "$previousClass, so widening moved the shell backwards",
                )
            }
            previous = width to resolved
        }
        assertEquals(
            ShellLayoutClass.Sidebar,
            ShellLayoutClass.forWidth(2400.dp),
            "the sidebar is unreachable within the swept range",
        )
    }

    /**
     * The sidebar boundary is the expanded minimum plus the sidebar's own width.
     *
     * An expression, not a copied `1120.dp`: the sidebar is only safe where the content it
     * leaves behind is still the narrowest genuine list-detail pair, so moving either input has
     * to move the boundary. A literal here would let a wider panel starve the pane it sits
     * beside while both numbers kept passing their own tests.
     */
    @Test
    fun `the sidebar threshold is the expanded minimum plus the sidebar width`() {
        assertEquals(ShellExpandedMinimumWidth + ShellSidebarWidth, ShellSidebarMinimumWidth)
    }

    /** The panel's width, held to its value for the reason the threshold is. */
    @Test
    fun `the sidebar width is pinned to the value it was chosen at`() {
        assertEquals(280.dp, ShellSidebarWidth)
    }

    /**
     * The one question every consumer actually asks, answered for every class.
     *
     * These are the three answers the nine old `== Expanded` comparisons were reaching for. The
     * assertion is exhaustive over the enum on purpose: a fourth class must decide, rather than
     * inheriting an answer by default.
     */
    @Test
    fun `only the compact shell is not wide`() {
        assertFalse(ShellLayoutClass.Compact.isWide, "a phone must not draw leading chrome")
        assertTrue(ShellLayoutClass.Expanded.isWide, "the rail's shell is a wide shell")
        assertTrue(ShellLayoutClass.Sidebar.isWide, "the sidebar's shell is a wide shell")
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

/**
 * The width order of the three classes, for the monotonicity sweep only.
 *
 * Local to the test rather than a property on the enum: nothing in production orders the classes,
 * and an `ordinal`-like surface would invite a consumer that should have asked [ShellLayoutClass.isWide]
 * instead. The `when` is exhaustive, so a fourth class is a compile error here — which is exactly
 * what the sweep needs.
 */
private fun ShellLayoutClass.rank(): Int = when (this) {
    ShellLayoutClass.Compact -> 0
    ShellLayoutClass.Expanded -> 1
    ShellLayoutClass.Sidebar -> 2
}
