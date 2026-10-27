package com.tasktracker.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale, named.
 *
 * ## Why this file exists when the original had nothing
 *
 * The original had no spacing tokens at all — 78 numeric `padding`/`spacing` literals
 * across 22 files, spanning 1 to 68, with values like 7 and 9 next to values like 6 and
 * 10. That is not a style, it is an accident that happened to look consistent, and it has
 * the two costs every un-named scale has: nobody can tell whether `7` was chosen or
 * mistyped, and changing the rhythm means finding all 78 sites by hand.
 *
 * So the scale here is *derived* from that inventory rather than invented, and the names
 * are the ones the app's own layout already implies. Every value below is a number the
 * original actually used, so porting a screen is a lookup rather than a decision — and the
 * odd ones are named after what they are, because the whole point of naming a value is to
 * make `7` stop being a mystery.
 *
 * ## The progression, and where it stops
 *
 * The bulk of the app lives on a 2/4/6/8 progression, which is what [xxs] through [lg]
 * are. The original's outliers are 3, 7, 9, 10, 14, 18, 28, 32, 44, 48, 56 and 68, and
 * they are *not* in the base ramp on purpose:
 *
 *  - [hairlineInset] (3) and [controlPaddingV] (7) / [controlPaddingH] (12) exist because
 *    a glass slab's own inset and a segment's padding are chosen so the *slab* lands on a
 *    whole number of points, not because 3 is a step. Rounding them onto the ramp changes
 *    the glass.
 *  - [controlPaddingV] (9) and [controlPaddingH] (14) are the button equivalents of the
 *    same idea.
 *  - [screenGutterLarge] (32) and the board's [boardTopInset] (44) / [boardBottomInset]
 *    (48) are layout positions, not rhythm: they are chosen against the safe area and the
 *    minimum column height, so they belong to whoever owns the layout and are named as
 *    such.
 *
 * One outlier in that list did *not* survive the port as an off-ramp value. The original
 * separated the two sign-in fields by 28, and that number came across as [fieldStack] — but
 * it was off-ramp here only so that it could be "a step past [xxxl]", which is not a reason
 * a gap has that size. It is now [xxl]. 28 remains in the list above because that list
 * records what the original used, not what this port kept.
 *
 * A scale that pretended those were steps would be lying about why they are the values
 * they are, and the next person to "tidy" them would break the thing they encode.
 *
 * ## dp, and why not sp
 *
 * `dp`, because the original's values are in points and 1pt == 1dp by definition. Using
 * `sp` for spacing is a common and real mistake: `sp` scales with the user's font-size
 * preference, so a user who has turned text up to 200% would get padding that has doubled
 * too, and the layout would fall apart in exactly the direction they were trying to help.
 * Type is in `sp` ([ttTypography]); space is in `dp`, always.
 */
@Immutable
object ttSpacing {

    /** 2dp — the gap between a label and the value under it, and inside a card. */
    val xxs: Dp = 2.dp

    /** 4dp — hairline row padding, icon-to-text. */
    val xs: Dp = 4.dp

    /** 6dp — the rhythm unit for a label/value stack. The original's most common literal. */
    val sm: Dp = 6.dp

    /** 8dp — column spacing, and the distance above a section's first row. */
    val md: Dp = 8.dp

    /** 10dp — a two-line row's internal gap. */
    val lg: Dp = 10.dp

    /** 12dp — the workhorse. Card padding, field padding, inter-component gaps. */
    val xl: Dp = 12.dp

    /** 16dp — a card's outer padding, and a screen's inner content inset. */
    val xxl: Dp = 16.dp

    /** 24dp — the screen gutter, and the largest rhythm step in normal use. */
    val xxxl: Dp = 24.dp

    // MARK: The values that are not steps, and why they are here anyway

    /**
     * 3dp — the glass slab's own inset.
     *
     * A segmented picker's segments are separated by 2dp and the slab wraps them with 3dp.
     * These are chosen together so the slab's edges land on whole points at a 1pt grid
     * scale; snapping 3 to 4 leaves the segments off-grid, and the hairline stroke that
     * draws the slab edge is exactly what makes that visible.
     */
    val hairlineInset: Dp = 3.dp

    /** 7dp — a segmented picker's vertical segment padding. See [hairlineInset]. */
    val controlPaddingV: Dp = 7.dp

    /** 12dp — a segmented picker's horizontal segment padding. The one 12 here is on the ramp. */
    val controlPaddingH: Dp = 12.dp

    /** 9dp — a glass button's vertical padding. The counterpart of [controlPaddingV]. */
    val buttonPaddingV: Dp = 9.dp

    /** 14dp — a glass button's horizontal padding. 2dp past [xl], for the same reason 7 is 1 past 6. */
    val buttonPaddingH: Dp = 14.dp

    /** 56dp — the circular floating add action's visible and tappable diameter. */
    val floatingActionSize: Dp = 56.dp

    /** 44dp — a compact circular icon action's visible and tappable diameter. */
    val iconButtonSize: Dp = 44.dp

    /**
     * 48dp — a text field's minimum height.
     *
     * The smallest comfortable target for a control you type into, and the height the
     * settings sheets were each spelling as a `48.dp` literal before [ttTextField] owned it.
     * It is a minimum rather than a fixed height: a field grows with its text when the
     * platform font scale is turned up, and pinning it would clip the value instead.
     */
    val fieldMinHeight: Dp = 48.dp

    /** 18dp — the splash mark's distance to its title. */
    val splashStack: Dp = 18.dp

    /** 24dp — the sign-in form's stack spacing, which is [xxxl]. Named at the call site. */
    val formStack: Dp = 24.dp

    /**
     * 16dp — the gap between the two sign-in fields, which is [xxl]. Named at the call site.
     *
     * Less than [formStack] on purpose, and the reason is grouping rather than rhythm. The two
     * credential fields are one unit, and a group holds together by its members sitting
     * *closer* together than the group sits to whatever surrounds it. At 28dp this was the
     * widest gap in the form — wider than the title above it and the button below it — so the
     * two fields read as separate blocks rather than as one form, and the value was measured
     * on a device at 28.0dp against a 62.7dp card to confirm it.
     *
     * It was off-ramp only to be "a step past [xxxl]", which is an argument about where a
     * number sits in a progression rather than about the relationship between two fields,
     * and that argument is what produced the wrong value. Every other off-ramp value in this
     * section is chosen against something the value has to agree with — a safe area, a
     * minimum column height, a whole-point grid — and there was no such constraint here.
     */
    val fieldStack: Dp = 16.dp

    /**
     * 32dp — a screen's *vertical* outer gutter, where the safe area is not already providing
     * one. Vertical only, and deliberately not the horizontal one: 32dp across a phone leaves a
     * third of the width inside a card, so a board's lanes and a header row that both use it are
     * inset past the point where the screen edge reads as the boundary. The horizontal gutter
     * is [xxl], which is what every screen and lane uses.
     */
    val screenGutterLarge: Dp = 32.dp

    /** 44dp — the board's top inset, below the tab and project switcher. */
    val boardTopInset: Dp = 44.dp

    /** 48dp — the board's bottom inset, so the last column can scroll clear of the tab bar. */
    val boardBottomInset: Dp = 48.dp

    /**
     * 6dp — a tab bar's padding above its content and below the safe area.
     *
     * [sm] on the ramp rather than a new number, and the reason it is not [controlPaddingV] (7)
     * is the pair it sits in: the bar's height is `6 + 22 (glyph) + 2 (gap) + caption + 6`, and
     * 6 keeps that inside the 49pt Apple's own tab bar occupies. 7 puts the pair a point over,
     * which on a bar is a point of dead space the eye reads as the bar being slightly too tall.
     *
     * Applied inside the material, above and below the row of tabs, and *not* to the safe-area
     * inset: see [tabBarEdgeInset] for where the inset belongs.
     */
    val tabBarVerticalPadding: Dp = 6.dp

    /** 2dp — the gap between a tab bar's glyph and its label. [xxs], for the same reason. */
    val tabBarIconLabelGap: Dp = 2.dp

    /** 2dp — a tab bar's side inset, so the outer glyphs are not against the screen edge. */
    val tabBarHorizontalPadding: Dp = 2.dp

    /**
     * How far the tab bar floats in from each screen edge.
     *
     * This is what makes the bar read as *floating* rather than as a band, and it is the
     * geometry iOS 26 gives its own tab bar. It is also where the bottom safe-area inset
     * belongs — the inset is applied to the wrapper that carries this padding, so the capsule's
     * own surface stops above the home indicator instead of running under it. Applied the other
     * way round (inset inside the glass, which is what a full-bleed bar wants) the material
     * keeps going to the bottom of the screen and the bar stops looking like glass at all,
     * because glass that touches every edge has nothing to float over.
     *
     * 16dp rather than 20: 20 is Apple's iPhone inset, but this bar carries four text labels,
     * and at 20dp the end labels sit close enough to the capsule's curve to read as clipped on
     * the longest of them ("Projects") at large text sizes.
     */
    val tabBarEdgeInset: Dp = 16.dp

    /**
     * The gap between the tab bar's inner edge and the selection lens.
     *
     * Small, because the lens has to look like it belongs to the *tab* rather than float in the
     * middle of the bar. But not zero: at zero the lens's rim sits on the capsule's rim and the
     * two outlines touch, which reads as a rendering fault rather than as a highlight.
     */
    val tabBarLensInset: Dp = 3.dp

    /** 56dp — the sign-in form's top inset, below the mark and title. */
    val signInTopInset: Dp = 56.dp

    /**
     * The scale in ramp order, for a test that asserts the ramp is actually increasing.
     *
     * Exposed rather than kept private so `SpacingTest` can walk it instead of restating
     * the numbers: a copy of a scale in a test is a second thing to update, and the test
     * would keep passing after the scale stopped being ordered.
     */
    val ramp: List<Dp> = listOf(xxs, xs, sm, md, lg, xl, xxl, xxxl)
}

/**
 * Corner radii, named for the same reason [ttSpacing] is.
 *
 * Five values cover the original's ten literals, and the duplication is itself
 * informative: `14` is both the button and the field radius because a text field and a
 * filled button are the same shape at the same role, and `16` is both the glass card and
 * the board column because a column and a card are the same kind of floating surface.
 * Collapsing them is the useful outcome; keeping two names for one number would be the
 * confusing one.
 */
@Immutable
object ttRadius {

    /** 10dp — a dense row: a work item in a list, a project row, a small column. */
    val row: Dp = 10.dp

    /** 12dp — the error banner. */
    val banner: Dp = 12.dp

    /** 14dp — an interactive control: the primary button, a labelled field, a glass card. */
    val control: Dp = 14.dp

    /** 16dp — a large floating surface: a glass card, a board column. */
    val surface: Dp = 16.dp

    /** 20dp — the app mark. The roundest corner in the app, on the smallest tile. */
    val mark: Dp = 20.dp

    /** Increasing, for the same reason as [ttSpacing.ramp]. */
    val all: List<Dp> = listOf(row, banner, control, surface, mark)
}
