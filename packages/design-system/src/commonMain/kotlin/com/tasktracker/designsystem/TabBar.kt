package com.tasktracker.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A floating tab bar: one glass capsule, and a lens that slides between the tabs.
 *
 * ## The three decisions that make it read as iOS 26, and not as a segmented picker upside down
 *
 * **It floats.** The bar is inset from the screen edges by [ttSpacing.tabBarEdgeInset] and
 * carries a drop shadow. The bar is expected to be the only child of a bottom-aligned wrapper
 * that also applies the bottom safe-area inset — see that token for why the inset belongs
 * *outside* the glass. The bar does not apply either itself, because a component that also
 * decides where it sits in the tree is a component that cannot be reused anywhere else, and
 * the one place this would be wrong (over a sheet, over a player) is exactly the place the
 * inset is already needed.
 *
 * **The lens is drawn behind the tab, not inside it.** This is the difference from
 * [TTGlassSegmentedPicker], and it is not a matter of taste. The picker's selection *is* a
 * chip: it is filled with the accent, and its label is re-coloured to `ttOnAccent` so that the
 * pair is contrast-checked. A tab bar's selection cannot work that way, because the selected
 * tab keeps its own accent glyph and label — those are what the user is navigating by, and
 * replacing them with an accent fill and a re-coloured label would mean the selection is
 * carried by a colour the user has to re-learn at every tab. So the lens is a *neutral* wash
 * plus a rim, laid under the icon and label, and the accent does the identifying. This is why
 * [ttGlass.TAB_LENS_ALPHA] is so much lower than the picker's: the lens here sits underneath
 * accent-coloured text and spends its contrast margin on every pixel of the bar.
 *
 * **The lens travels, distorts, and settles.** See [TAB_LENS_WIDEN] and the `stretch` effect
 * below. A capsule that only translates is a highlight moving; one that leans into the travel
 * and recovers is material being displaced, and that is the difference between "the highlight
 * moved" and "the bar is liquid".
 *
 * ## Rules
 *
 * One glass surface for the whole bar, never one per tab (rule 2 of [ttGlass]). With no
 * `GlassEffectContainer` on this platform there is nothing to merge neighbouring materials, so
 * per-tab glass would read as four separate pills in a row.
 *
 * The lens is **not** glass and does not call [ttGlassSurface]. It is a plain translucent wash
 * and a rim, drawn with [drawBehind], precisely because the material is already inside the
 * bar's glass and a second [ttGlassSurface] inside it would stack two full ramps and two
 * borders, and the inner one would then have to be told not to draw its border.
 */
@Composable
fun <Tab> TTGlassTabBar(
    tabs: List<Tab>,
    selectedIndex: Int,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The tab's label. `@Composable` because every label in this app is a string resource, and
     * `stringResource` cannot be called from a plain lambda — the same constraint, and the same
     * reasoning, as [TTGlassSegmentedPicker.title].
     */
    title: @Composable (Tab) -> String,
    /**
     * The tab's glyph, as an [ImageVector] rather than as a composable slot.
     *
     * The design system already owns [ttTabIcons], so handing it a *slot* would mean every
     * caller re-implements the same icon-plus-tint-plus-size wrapper and the selected/unselected
     * sizing rule is duplicated per call site. Owning it here is what makes the selected scale
     * and the tint a single implementation.
     */
    icon: (Tab) -> ImageVector,
    /**
     * A per-tab modifier, for the caller's own [androidx.compose.ui.platform.testTag] or
     * anything else that has to address one tab specifically.
     *
     * A parameter rather than a hard-coded tag because the design system cannot know what a
     * caller calls its tabs, and the tag is the one piece of per-tab information the component
     * cannot infer: it has the tab, its index, and its selection, and a caller asking "which tab
     * is under the cursor" needs a name from the caller's own vocabulary.
     */
    tabModifier: (Tab) -> Modifier = { Modifier },
) {
    require(tabs.isNotEmpty()) { "TTGlassTabBar needs at least one tab" }
    require(selectedIndex in tabs.indices) {
        "TTGlassTabBar selectedIndex $selectedIndex is outside 0..${tabs.lastIndex}"
    }

    val accent = ttAccent()

    // The selected and unselected caption inks are shared with the settings accent picker,
    // because both put a label under the same kind of glass selection lens. The derivation — why
    // dark is white and light a darkened accent, why the unselected caption is not the platform's
    // `secondaryLabel` — and the numbers on both rasterisers live on [ttSelectionLabelInk], which
    // `TabLensContrastTest` holds.
    val selectedInk = ttSelectionLabelInk(selected = true)
    val inactiveInk = ttSelectionLabelInk(selected = false)

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            // The bar spans the width it is offered. Not a style preference and not obvious:
            // without it the `Row` of tabs wraps to the sum of its four labels' natural widths,
            // the segments come out unequal, and every number the lens is derived from —
            // segment width, travel per step — is computed from a segment that is not there.
            // The first render of this measured a 540px bar in a 1024px window for exactly
            // this reason.
            .fillMaxWidth()
            // The shadow sits *outside* the glass and the glass is drawn over it, so the bar's
            // own fill hides the shadow's middle and only the ring around the silhouette
            // survives. That is what makes it a shadow rather than a second, larger capsule.
            .shadow(
                elevation = ttGlass.tabBarShadowElevation,
                shape = CircleShape,
                ambientColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
                spotColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
            )
            .ttGlassSurface(shape = CircleShape)
            // Inside the material, after it — the same order as [TTGlassSegmentedPicker], and
            // for the same reason: the padding is content inset, so shrinking the content keeps
            // the hairline the right distance from the glyphs. Putting it before the glass
            // instead would inset the *bar* from its caller, which is a different request and
            // is made by the caller (see the floating wrapper in the shell).
            .padding(ttSpacing.hairlineInset)
            .padding(vertical = ttSpacing.tabBarVerticalPadding),
    ) {
        val segmentWidth = maxWidth / tabs.size
        val travel = animateFloatAsState(
            targetValue = with(density) { (segmentWidth * selectedIndex).toPx() },
            animationSpec = ttGlass.selectionSpring,
            label = "tabLensTravel",
        )

        // The bar's height comes from the row, and only from the row. Not `fillMaxSize`: this
        // element is a child of a `Column` next to a `weight(1f)` content area, so "max size"
        // is the whole screen, and a bar that fills the screen is not a bar. The measured
        // consequence of getting this wrong was a 768pt-tall wrapper with the tabs parked
        // dead centre at y=363, because the `Row` inside was then centring four tabs in a
        // full screen.
        //
        // Two `BoxWithConstraints` because the lens needs both dimensions and they arrive from
        // different places: the width is the bar's own (`fillMaxWidth`, resolved by the outer)
        // and the height is the row's, which does not exist until the row is measured. The
        // inner one is `matchParentSize`, which reads the parent without contributing to it —
        // so the parent still wraps to the row rather than being sized by its own child.
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for ((index, tab) in tabs.withIndex()) {
                    TabCell(
                        label = title(tab),
                        icon = icon(tab),
                        isSelected = index == selectedIndex,
                        accent = selectedInk,
                        inactiveInk = inactiveInk,
                        onClick = { onSelect(tab) },
                        modifier = tabModifier(tab).weight(1f),
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                TabLens(
                    travelPx = travel.value,
                    segmentPx = with(density) { segmentWidth.toPx() },
                    barWidth = maxWidth,
                    lensWidth = segmentWidth - ttSpacing.tabBarLensInset * 2,
                    lensHeight = maxHeight - ttSpacing.tabBarLensInset * 2,
                    stretch = lensStretch(selectedIndex),
                )
            }
        }
    }
}

/**
 * How far through its stretch the lens is, 1 at the peak and 0 at rest.
 *
 * Extracted from the layout so that the decision to *not* stretch on first composition is
 * auditable next to the effect rather than buried inside a lambda in the middle of a modifier
 * chain. See the guard in here for why the guard is needed at all.
 */
@Composable
private fun lensStretch(selectedIndex: Int): Float {
    // Starts at rest, not at the peak. The peak is only *entered* by a selection change, and
    // the effect below is what enters it. Initialising to 1f instead left the lens permanently
    // 10% wide and 6% short on first composition — and, because `LaunchedEffect` does not run
    // its body when the index has not changed, permanently on the tab the bar opened on, for
    // the whole life of the composition. It looked like a deliberate wide resting lens, which
    // is exactly the kind of thing that survives review.
    val stretch = remember { Animatable(0f) }
    var previousIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (previousIndex != selectedIndex) {
            previousIndex = selectedIndex
            stretch.snapTo(1f)
            stretch.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = ttGlass.TAB_LENS_SETTLE_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    return stretch.value
}

/**
 * The lens: a rounded wash and a rim, sized and placed rather than scaled.
 *
 * ## Why it resizes instead of scaling
 *
 * The obvious way to lean into the travel is a `graphicsLayer` with a `scaleX`/`scaleY` on one
 * fixed-size box. Measured on the JVM renderer that produced a visible defect: a completely
 * empty box carrying only that layer darkened the bar by ~0.14 of relative luminance behind it,
 * which is the entire alpha of the lens's own wash. The composable was correct and the pixels
 * were wrong, so the layer was dropped and the width and height are animated directly. The
 * measured cost is a remeasure per frame instead of a cheap transform; the benefit is that the
 * one thing being measured is the one thing being drawn.
 *
 * The clamp is the part worth reading. The lens is wider at the peak of the travel, and the
 * *first* and *last* tabs have no room on the outer side, so without a clamp the lens pushes
 * straight through the capsule's curved end. Clamping its left edge back to zero slides it
 * inward instead, which costs about a pixel of travel at [ttGlass.TAB_LENS_WIDEN] and is
 * invisible; letting it overflow costs the bar its silhouette, and the silhouette is the entire
 * reason it is floating.
 */
@Composable
private fun TabLens(
    travelPx: Float,
    segmentPx: Float,
    barWidth: Dp,
    lensWidth: Dp,
    lensHeight: Dp,
    stretch: Float,
) {
    val density = LocalDensity.current
    val width = lensWidth * (1f + ttGlass.TAB_LENS_WIDEN * stretch)
    val height = lensHeight * (1f - ttGlass.TAB_LENS_SQUASH * stretch)

    // Clamped in composition rather than in an `offset {}` lambda, because the lambda overload
    // of `offset` gives a `Density` and nothing else — no placeable, no coordinates — so the
    // bar's width is not reachable from it. The enclosing `BoxWithConstraints` already has it.
    val left = with(density) {
        val lensPx = width.toPx()
        val barPx = barWidth.toPx()
        val centred = travelPx + segmentPx / 2f - lensPx / 2f
        // `coerceAtLeast` on the bound, because `coerceIn` throws on an inverted range rather
        // than clamping, and a lens wider than the bar is a degenerate case a narrow window can
        // genuinely produce.
        val farthest = (barPx - lensPx).coerceAtLeast(0f)
        centred.coerceIn(0f, farthest).toDp()
    }

    Box(
        modifier = Modifier
            .size(width, height)
            // Inset on the vertical as well as the horizontal. The height above already
            // subtracts the inset at *both* ends, but a Box places its content at the origin,
            // so without this the six points come off the bottom and none off the top: the
            // lens sat flush against the row's first pixel and floated six points clear of the
            // last, which is a lens leaning, not a lens inset.
            .offset(x = left, y = ttSpacing.tabBarLensInset)
            // The wash is clipped to the very same [CircleShape] the rim below is drawn with, and
            // that is the whole point: the two are the same object, so the fill and the outline
            // cannot disagree about where the lens ends. Without this the `drawRect` below fills
            // all four corners of its own bounding box and the rim is drawn *around* the parts
            // that stick out, which is a rectangle wearing a capsule's outline — the indicator
            // showed two squared blocks at its top corners. The top two are the visible ones
            // because the gradient is strongest at `startY = 0` and the bottom wedges, at a
            // quarter of that alpha, went unnoticed.
            //
            // Measured off the rendered frames by `TabLensCornerTest`, unclipped: the wash reached
            // `#09` past the bar's glass in the top-left corner on the light bar and `#21` on the
            // dark one. The two hosts agree on both to the byte, which is the one thing this
            // component's other measurements do not do — a hard-edged geometric overhang is not a
            // stroke-coverage question, so unlike the captions it does not need two rasterisers
            // to see it.
            //
            // A clip layer rather than a `drawOutline` inside the same `drawBehind`, because the
            // lens already remeasures and redraws every frame of its travel and settle and this
            // adds no allocation to that path. The rim is unaffected: `border` draws its own
            // outline and does not read the clip.
            .clip(CircleShape)
            .drawBehind {
                // `endY` spelled out, for the reason documented on `ttGlassSurface`: an
                // unbounded `Brush.verticalGradient` collapses onto its last stop, and this
                // element's height is not known when the brush is built.
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
 * One tab: glyph over label, both re-coloured and the glyph re-sized by [isSelected].
 *
 * The glyph and the label are a single [Column] rather than a [Row] because iOS stacks them, and
 * a tab bar is a navigation target rather than a menu: stacking is what lets the label sit
 * directly under the glyph it belongs to and lets both stay legible at a fixed bar height.
 */
@Composable
private fun TabCell(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    accent: Color,
    inactiveInk: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One spring per tab rather than one shared value, because unlike the lens this is a
    // *per-item* change: only the two tabs involved in a change are animating, and the other
    // two are holding a constant 1f. A shared value would have all four breathe together.
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) ttGlass.TAB_LENS_ICON_SCALE else 1f,
        animationSpec = ttGlass.selectionSpring,
        label = "tabIconScale",
    )

    Column(
        modifier = modifier
            .clickable(
                // No indication, and this is the same call the picker scrim makes. The default
                // one paints the pressed cell as a *rectangle* — the cell has no clip, so the
                // indication is not bounded by the lens's capsule — and on a bar this light it
                // darkens the segment by 0.14 of relative luminance, which is more than twice
                // what the lens itself contributes. The measured number is in
                // `TabBarScreenshotTest`: a pressed tab read as *darker* than its unselected
                // neighbours in every frame, and `advanceTimeBy` does not clear it, because a
                // resting pointer is not an animation in flight. So it sat there after the lens
                // had already moved, which is the opposite of what the indicator is for.
                //
                // What replaces it is the lens itself: travel, the settle, and the glyph's
                // recolour and scale all start on click, so the selection is confirmed by the
                // same motion that carries it there.
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            )
            // The label carries the selected state for accessibility, because a tint and a
            // translucent wash are not states: without this a screen reader user has no way to
            // tell the selected tab from the other three, and neither has anyone who cannot
            // separate the accent from the secondary label colour. `isSelected` rather than a
            // parameter named `selected` — the semantics property shadows any local of that name.
            .semantics { selected = isSelected }
            .padding(vertical = ttSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.tabBarIconLabelGap),
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
