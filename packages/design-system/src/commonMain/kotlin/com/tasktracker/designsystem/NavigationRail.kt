package com.tasktracker.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A navigation rail: [TTGlassTabBar] rotated onto its side.
 *
 * ## The vertical sibling of the tab bar, and the split is the same one
 *
 * The geometry, the travel, the settle and the per-destination tint live here, in the design
 * system, and the shell is left with the list of destinations, their labels and their glyphs —
 * the part that is about *this* app. [NavigationHost]'s `TabBar` documents why that split was
 * made in the first place: geometry in the shell meant anything else wanting a tab bar had to
 * re-derive the lens.
 *
 * ## Every rule the bar documents is inherited, not re-derived
 *
 * One glass surface for the whole rail, never one per destination — with no
 * `GlassEffectContainer` on these platforms there is nothing to merge neighbouring materials, so
 * per-destination glass reads as a stack of separate pills down the window edge.
 *
 * The lens is a plain wash and a rim drawn with [drawBehind], and is **not** [ttGlassSurface].
 * The material is already inside the rail's glass, and a second ramp inside the first stacks two
 * borders, which is a defect the bar's own wash would then have to be told not to draw.
 *
 * The lens **resizes** rather than scaling through a `graphicsLayer`. That is not a style
 * preference: measured on the JVM renderer, an empty box carrying only a scale layer darkened
 * the bar behind it by ~0.14 of relative luminance, which is the entire alpha of the lens's own
 * wash. See [TabLens].
 *
 * ## What is genuinely new here is the axis, and it has its own trap
 *
 * The bar's segment is `maxWidth / tabs.size`, and the width *is* offered — `fillMaxWidth`
 * resolves it before the bar is measured. A rail has no offered dimension at all: its height is
 * the sum of its cells, and its width is the widest caption. The bar's own KDoc records the
 * failure this invites in the other axis — "a 768pt-tall wrapper with the tabs parked dead
 * centre at y=363" — and taking `maxHeight / destinations.size` from the rail itself rather
 * than from the measured column would reproduce it exactly.
 *
 * So the segment height is read from a [BoxWithConstraints] sized to the cell column with
 * [matchParentSize], which reads its parent without contributing to it. Every other number the
 * lens needs comes from the same place, and none of them is read off the rail's own box.
 *
 * ## Both stretch tokens swap axis, and that is not a typo
 *
 * [ttGlass.TAB_LENS_WIDEN] and [ttGlass.TAB_LENS_SQUASH] describe the lens's behaviour along its
 * *travel*, not along the screen. On the bar the travel is horizontal, so the lens widens along
 * x. Here the travel is vertical, so the same two numbers apply along y: the lens grows taller
 * at the peak and draws in from the sides. Reusing them keeps one set of numbers for one
 * material rather than inventing a second pair that measures the same effect.
 */
@Composable
fun <Tab> TTGlassNavigationRail(
    destinations: List<Tab>,
    selectedIndex: Int,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The destination's label. `@Composable` for the same reason [TTGlassTabBar.title] is: every
     * label in this app is a string resource, and `stringResource` cannot be called from a plain
     * lambda.
     */
    title: @Composable (Tab) -> String,
    /** The destination's glyph, as an [ImageVector]. See [TTGlassTabBar.icon]. */
    icon: (Tab) -> ImageVector,
    /**
     * A per-destination modifier, for the caller's own
     * [androidx.compose.ui.platform.testTag] or anything else that has to address one
     * destination specifically. A parameter rather than a hard-coded tag, for the reason
     * [TTGlassTabBar.tabModifier] gives: the design system cannot know what a caller calls its
     * destinations.
     */
    destinationModifier: (Tab) -> Modifier = { Modifier },
) {
    require(destinations.isNotEmpty()) { "TTGlassNavigationRail needs at least one destination" }
    require(selectedIndex in destinations.indices) {
        "TTGlassNavigationRail selectedIndex $selectedIndex is outside 0..${destinations.lastIndex}"
    }

    val accent = ttAccent()

    // The same two inks the bar's captions use, for the same reason: both put a label under the
    // same kind of glass selection lens. `TabLensContrastTest` holds the derivation and the
    // measured contrast on both rasterisers.
    val selectedInk = ttSelectionLabelInk(selected = true)
    val inactiveInk = ttSelectionLabelInk(selected = false)

    val density = LocalDensity.current

    Box(
        modifier = modifier
            // The rail's height comes from the cell column inside, not from here: this box wraps to
            // its tallest child. An earlier version carried a `fillMaxHeight` here, and it was
            // provably redundant — deleting it left every measurement identical — which made it
            // read as the source of the rail's height while mutating it turned no test red. That
            // is what a redundant modifier costs, so it is gone; `weight(1f)` on the cells owns
            // the height and mutating that does turn the shell's rail-height assertions red.
            //
            // The shadow sits outside the glass and the glass is drawn over it, so the rail's own
            // fill hides the shadow's middle and only the ring around the silhouette survives.
            // That is what makes it a shadow rather than a second, larger capsule.
            .shadow(
                elevation = ttGlass.tabBarShadowElevation,
                shape = CircleShape,
                ambientColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
                spotColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
            )
            .ttGlassSurface(shape = CircleShape)
            // Inside the material, after it, for the bar's reason: this is content inset, so
            // shrinking the content keeps the hairline the right distance from the glyphs.
            //
            // `tabBarVerticalPadding` applied *horizontally*, because a rail's own cross-axis
            // padding is its left and right. The number is the bar's vertical one rather than a
            // new one, and that is not laziness: it is the same job — keeping the hairline off the
            // glyphs by the same margin — on the other axis, and a second value would be a second
            // number to keep true for no visible gain. The edge inset from the window belongs to
            // the caller, which is where the bar's is applied too.
            .padding(ttSpacing.hairlineInset)
            .padding(horizontal = ttSpacing.tabBarVerticalPadding),
    ) {
        // `Box {`, with no fill of either axis, and that is load-bearing rather than lazy.
        //
        // The rail's height comes from its cells and nowhere else: every cell is `weight(1f)`, so
        // the column consumes the whole height it is offered. Two `fillMaxHeight` calls — one here
        // and one on the column — were carried while this was being built, and both were provably
        // redundant: mutating either left every measurement in the suite identical, because the
        // weighted cells had already taken the height. A modifier that looks like the source of a
        // value and is not costs exactly what it cost here, so both are gone and `weight(1f)` is
        // the single owner — which is why removing it *does* turn the shell's rail-height
        // assertions red, and removing a `fillMaxHeight` does not.
        //
        // The consequence is that the rail needs a *bounded* height from its caller, because
        // weighted children in an unbounded parent have nothing to divide. Both callers provide
        // one: the shell hands it a `Box(Modifier.fillMaxSize())`, and the screenshot harness
        // hands it an explicit `height(…)`. The width is never filled, so the rail stays as wide
        // as its widest caption rather than becoming the tab bar's full-width capsule.
        Box {
            // `CenterHorizontally` is what makes the glyphs line up, and it belongs here rather
            // than on the cells for a reason that is easy to get backwards.
            //
            // Cells are as wide as their own captions and the captions differ — "Board" is far
            // narrower than "Projects" — so this column is as wide as the *widest* cell and the
            // narrower ones are placed by this alignment. Centring each cell puts every cell's
            // midpoint on the column's centre line, and a cell centres its own glyph and caption on
            // its own midpoint, so every glyph lands on one shared axis. Left to itself every cell
            // would hug the leading edge and each glyph would sit at its own caption's centre,
            // which is how the first version drew them: "Board" visibly outboard of "Projects".
            //
            // Giving every cell the same width would fix it too, but that needs a width to give
            // them, and the widest caption is a typography measurement the design system cannot
            // know — which is the same finding that kept a rail-width token out of the tree. The
            // bar sidesteps the question with `weight(1f)` across a row that already spans the
            // width; a rail has no equivalent, so it centres instead.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for ((index, tab) in destinations.withIndex()) {
                    RailCell(
                        label = title(tab),
                        icon = icon(tab),
                        isSelected = index == selectedIndex,
                        accent = selectedInk,
                        inactiveInk = inactiveInk,
                        onClick = { onSelect(tab) },
                        // `weight(1f)`, load-bearing rather than cosmetic: the lens's segment is
                        // the rail's height divided by the destination count, so cells that did not
                        // share the height equally would put the lens in the wrong place on a tall
                        // window. This is the bar's `weight(1f)` on the other axis, for the same
                        // reason.
                        modifier = destinationModifier(tab).weight(1f),
                    )
                }
            }

            // `matchParentSize`, not `fillMaxSize`. This box must read the column's height without
            // contributing to it, or the column would be sized by a child that is sized by the
            // column. It is the rail's whole geometry: see this function's note on why neither
            // dimension here is offered.
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val segmentHeight = railSegmentHeight(
                    railHeight = maxHeight,
                    destinationCount = destinations.size,
                )
                val travel = animateFloatAsState(
                    targetValue = with(density) { (segmentHeight * selectedIndex).toPx() },
                    animationSpec = ttGlass.selectionSpring,
                    label = "railLensTravel",
                )
                RailLens(
                    travelPx = travel.value,
                    segment = segmentHeight,
                    railHeight = maxHeight,
                    lensWidth = maxWidth - ttSpacing.tabBarLensInset * 2,
                    lensHeight = segmentHeight - ttSpacing.tabBarLensInset * 2,
                    stretch = lensStretch(selectedIndex),
                )
            }
        }
    }
}

/**
 * The height of one destination's slice of the rail.
 *
 * Divides the rail's **content** height by the destination count, and the "content" is the whole
 * point: a rail is not offered a height the way a bar is offered a width, so dividing anything
 * else produces a segment that is not where the cells are. [TTGlassNavigationRail] reads it from
 * a [BoxWithConstraints] on the measured cell column for exactly that reason.
 *
 * Extracted as a pure function so the rule is testable without a frame — the same move
 * `ShellLayoutClass.forWidth` makes for the shell's layout class.
 */
internal fun railSegmentHeight(railHeight: Dp, destinationCount: Int): Dp {
    require(destinationCount > 0) { "a rail with no destinations has no segments" }
    return railHeight / destinationCount
}

/**
 * The lens's top edge.
 *
 * ## The clamp is the part worth reading
 *
 * The lens is taller at the peak of its travel, and the first and last destinations have no room
 * on the outer side. Without a clamp it pushes straight through the rail's rounded end. Clamping
 * back to the rail's extent slides it inward instead, which costs about a pixel of travel and is
 * invisible; letting it overflow costs the rail its silhouette, and the silhouette is the entire
 * reason it is floating.
 *
 * `coerceAtLeast` on the bound rather than `coerceIn`, because `coerceIn` throws on an inverted
 * range rather than clamping, and a lens taller than the rail is a degenerate case a short
 * window can genuinely produce.
 *
 * In [Dp] throughout, including the travel. The bar's equivalent does its arithmetic in pixels
 * because it reads them out of an `animateFloatAsState`; doing the same here would mean this
 * function's only testable form needs a `Density` to exist at all, and a clamp that cannot be
 * called without a graphics context is a clamp nobody tests. The conversion happens at the one
 * call site.
 */
internal fun railLensTop(
    travelDp: Dp,
    segment: Dp,
    lensHeight: Dp,
    railHeight: Dp,
): Dp {
    val centred = travelDp + segment / 2f - lensHeight / 2f
    val farthest = (railHeight - lensHeight).coerceAtLeast(0.dp)
    return centred.coerceIn(0.dp, farthest)
}

/**
 * The lens's height along its travel, never taller than the segment it sits in.
 *
 * Extracted so the clamp is testable without driving an animation, which is the only reason it is
 * a function rather than an expression. [RailLens] applies the stretch; on a settled frame the
 * stretch is zero, the lens is `segment - 2 * inset` and fits with room to spare, so a test that
 * measures the rendered rail at rest cannot tell whether the clamp exists at all.
 *
 * The defect it exists for is real and only visible at the peak: a rail segment is a fifth of the
 * rail's height — 146dp measured — so 140dp x 1.1 is 154dp, eight points taller than the cell the
 * lens is drawn under, with its top edge still landing correctly and its bottom edge crossing into
 * the next destination.
 */
internal fun railLensHeight(lensHeight: Dp, segment: Dp, stretch: Float): Dp =
    minOf(lensHeight * (1f + ttGlass.TAB_LENS_WIDEN * stretch), segment)

/**
 * The lens: a rounded wash and a rim, sized and placed rather than scaled.
 *
 * The exact transpose of the bar's [TabLens] — see that function for why it resizes instead of
 * scaling, and for why the wash is clipped to the very shape the rim is drawn with. What changes
 * is the axis: the travel is vertical here, so the clamp is on the top edge, and the two stretch
 * tokens apply along y. See [TTGlassNavigationRail].
 */
@Composable
private fun RailLens(
    travelPx: Float,
    segment: Dp,
    railHeight: Dp,
    lensWidth: Dp,
    lensHeight: Dp,
    stretch: Float,
) {
    val density = LocalDensity.current

    // The tokens describe the travel, not the screen — see this file's note. The lens grows along
    // its direction of travel and draws in across it, and the growth is capped at its own segment
    // so a tall cell can never hand the lens more room than it has — see [railLensHeight] for the
    // measurement that made that necessary.
    val width = lensWidth * (1f - ttGlass.TAB_LENS_SQUASH * stretch)
    val height = railLensHeight(lensHeight = lensHeight, segment = segment, stretch = stretch)

    val top = railLensTop(
        travelDp = with(density) { travelPx.toDp() },
        segment = segment,
        lensHeight = height,
        railHeight = railHeight,
    )

    Box(
        modifier = Modifier
            .size(width, height)
            // `y = top, x = the inset` — the bar's `x = left, y = the inset` with the axes
            // swapped, because the travel runs down a rail instead of across a bar.
            //
            // The inset is on the cross axis in both, and it has to be: a [Box] places its content
            // at the origin, so the width arithmetic above — which already subtracts the inset
            // from both sides of the lens — is not enough on its own. Without this the lens would
            // sit against the rail's leading edge and float clear of its trailing one, which is a
            // lens leaning rather than a lens inset. Nothing similar is wanted on y: the travel
            // axis is exactly `top`, and adding the inset there would shift the lens off its own
            // destination by a full inset at every index.
            .offset(y = top, x = ttSpacing.tabBarLensInset)
            // The lens is addressable so its size can be compared with the cell it is marking.
            // Nothing asserted that before the rail grew to full height, and the defect it missed
            // is exactly that one: a lens 154dp tall over a 146dp cell looks correct from its top
            // edge, which is the edge a measurement is most likely to look at.
            .testTag(NavigationRailLensTag)
            // Clipped to the same [CircleShape] the rim is drawn with, and that is the whole
            // point: the two are the same object, so the fill and the outline cannot disagree
            // about where the lens ends. Without it the `drawRect` fills all four corners of its
            // own bounding box and the rim is drawn around the parts that stick out — a rectangle
            // wearing a capsule's outline. Measured on the bar by `TabLensCornerTest`, which found
            // the wash reaching `#09` past the glass in the top-left corner on the light bar and
            // `#21` on the dark one.
            .clip(CircleShape)
            .drawBehind {
                // `endY` spelled out: an unbounded `Brush.verticalGradient` collapses onto its last
                // stop, and this element's height is not known when the brush is built.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = ttGlass.TAB_LENS_ALPHA),
                            Color.White.copy(
                                alpha = ttGlass.TAB_LENS_ALPHA * (1f - ttGlass.TAB_LENS_FADE),
                            ),
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
            .border(
                width = ttGlass.hairlineWidth,
                color = Color.White.copy(alpha = ttGlass.TAB_LENS_RIM_ALPHA),
                shape = CircleShape,
            ),
    )
}

/**
 * One destination: glyph over label, both re-coloured and the glyph re-sized by [isSelected].
 *
 * The bar's cell is the same component with one difference: it is handed `weight(1f)` so five of
 * them divide a bar that fills its width. A rail's cells have no width to divide — the rail is as
 * wide as its widest caption — so this one wraps its content and the column sets the measure.
 * The single-column stacking, the two inks, the icon scale, the `Role.Tab` and the `selected`
 * semantics are the bar's, unchanged.
 */
@Composable
private fun RailCell(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    accent: Color,
    inactiveInk: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One spring per cell rather than one shared value, for the bar's reason: this is a *per-item*
    // change, and a shared value would have all five breathe together.
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) ttGlass.TAB_LENS_ICON_SCALE else 1f,
        animationSpec = ttGlass.selectionSpring,
        label = "railIconScale",
    )

    Column(
        modifier = modifier
            .clickable(
                // No indication, for the bar's reason and with the bar's measurement: the default
                // one paints the pressed cell as a rectangle the cell has no clip to bound, and on
                // glass this light it darkens the cell by 0.14 of relative luminance — more than
                // twice what the lens itself contributes. The lens is what confirms the selection.
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            )
            // The label carries the selected state for accessibility, because a tint and a
            // translucent wash are not states.
            .semantics { selected = isSelected }
            .padding(vertical = ttSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centred in the cell, not packed against its top edge.
        //
        // `Arrangement.spacedBy(gap)` alone packs to the top, which was correct while a cell was
        // exactly as tall as its content and was silently wrong the moment cells were given
        // `weight(1f)`. The first destination then sat hard against the rail's top edge while the
        // last had most of a tall cell empty beneath it — measured at 146dp per cell in a 736dp
        // rail, so the same 5dp above the first icon and 109dp below the last.
        //
        // Centring the pair makes those two ends symmetric, which is the comparison the eye
        // actually makes on a rail this tall. The icon-to-caption gap is unchanged: `spacedBy` with
        // an alignment still spaces its children by that gap, it only places the resulting group in
        // the middle rather than at the start.
        verticalArrangement = Arrangement.spacedBy(
            space = ttSpacing.tabBarIconLabelGap,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        ttIcon(
            image = icon,
            contentDescription = null,
            tint = if (isSelected) accent else inactiveInk,
            modifier = Modifier
                .size(ttIconSize.tab)
                .scale(iconScale),
        )
        ttText(
            text = label,
            style = ttTypography.caption,
            color = if (isSelected) accent else inactiveInk,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * The travelling selection lens, so its size can be compared with the cell it is marking.
 *
 * Exists because of a defect it caught rather than a convenience it offers. Once the rail grew to
 * fill its window, a segment became 146dp where the bar's is about 49dp, and the lens's 10% stretch
 * — 140dp to 154dp — no longer fitted inside the cell it was drawn under. The lens's *top* edge
 * still landed on the right destination, which is the edge a screenshot or a bounds check is most
 * likely to look at, and its bottom edge crossed into the next cell. A tag on the lens is what
 * makes the second thing measurable.
 */
const val NavigationRailLensTag = "rail.lens"
