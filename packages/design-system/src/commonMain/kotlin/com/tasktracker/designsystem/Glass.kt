package com.tasktracker.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Glass surfaces, shared by every feature that renders content over something.
 *
 * ## The three rules, and why each one is a rule
 *
 * 1. **Glass goes on content; padding goes inside it.** Padding applied *outside* the
 *    material only pushes space around the bar — the bar itself stays the same size and the
 *    tap target gets worse. Every component in this file pads its label first and then
 *    becomes glass, so the material is exactly as large as the thing it contains.
 *
 * 2. **Neighbouring glass shares a surface.** On iOS 26 two `glassEffect` views side by
 *    side without a `GlassEffectContainer` render as two overlapping slabs; the container is
 *    what lets the system blend and merge them. **Compose Multiplatform 1.12.1 has no
 *    equivalent**, so this rule cannot be enforced by a container and the components
 *    instead put the glass on a *single* element that wraps all of their segments. That is
 *    the whole reason [TTGlassSegmentedPicker] is one slab and not one glass pill per
 *    segment, and it is a structural difference from the original rather than a rendering
 *    one — see [ttGlassSurface] for what was given up.
 *
 * 3. **Glass needs something behind it.** A material samples what is drawn behind it, so a
 *    glass card on the flat canvas reads as grey — which is what made the first version of
 *    the sign-in card look like a form in a box. [AmbientBackdrop] exists to give glass
 *    something to sample.
 *
 * ## What the material is here, and what it is not
 *
 * The original is iOS 26's Liquid Glass: a real material, sampled live from the content
 * behind it, refracting and specular-highlighting it. There is no Compose equivalent —
 * `Modifier.blur` blurs the *content*, not the backdrop, and Compose cannot read what is
 * behind a node at all. So this is a stand-in, and the differences are worth stating rather
 * than discovering:
 *
 * | | iOS 26 `glassEffect` | here |
 * | --- | --- | --- |
 * | what it samples | the composited backdrop | nothing; a fixed translucent fill |
 * | edge | a lit, refractive rim | a flat 0.5dp hairline, light on both appearances |
 * | response to press | physical, from the system | a scale, from [TTGlassButtonStyle] |
 * | neighbours | merged in a `GlassEffectContainer` | one surface wrapping the group |
 *
 * What is preserved is the part that was design: the corner radii, the 0.22 tint floor, the
 * hairline, the inside-padding rule, and the shape of every component. The visual difference
 * is accepted, because the alternative is no glass at all — and a flat card on a designed
 * backdrop reads far closer to the original than a `Material` card does.
 *
 * The tint is a [Color], not a style enum, for the original's reason: a feature passes the
 * colour it already has — a priority tint, the accent, nothing — and the component owns the
 * *how*.
 */
object ttGlass {

    /**
     * The floor a caller-supplied tint is knocked down to, from the original's
     * `tint.opacity(0.22)`.
     *
     * Low on purpose. The tint sits under a hairline and under a label, and a tint strong
     * enough to read as a colour stops being glass and starts being a button — which is
     * what [TTGlassButtonStyle]'s `prominent` flag is for.
     */
    const val TINT_ALPHA: Float = 0.22f

    /**
     * A prominent glass control's tint, from the original's `tint.opacity(0.85)`.
     *
     * High enough to read at a glance, and still not opaque, so the backdrop still shows
     * through and the control still belongs to the same family as the ones around it.
     */
    const val PROMINENT_TINT_ALPHA: Float = 0.85f

    /** The fill's own alpha, before any tint: glass over *something* is never opaque. */
    const val FILL_ALPHA: Float = 0.55f

    /**
     * How far the fill's **hue** travels from the tint toward the canvas by the bottom edge.
     *
     * The original's grammar is "lit at the top, sitting over the page at the bottom". The
     * first port expressed that by fading the fill's **alpha** to nothing — the top stop was
     * the tint at [FILL_ALPHA] and the bottom stop was the canvas at a third of an alpha —
     * and that is not that grammar, it is the material deleting itself. A canvas-coloured
     * stop composites to *exactly* the page wherever it sits on it, so the last rows of every
     * card and every glass button were the page. Measured off an emulator at the centre of a
     * project card: `(195,204,216)` at the top edge, ramping all the way to `(251,251,252)`
     * at the bottom edge, against a `(250,250,250)` page — and the prominent New Project
     * button ramping the accent `(82,108,142)` to `(251,251,251)`. A white smear along the
     * bottom of both, which is the "white shadow" reported on the sign-in button and then
     * again on the projects list once the gradient below started resolving at all.
     *
     * So the two things are separated, because they are two different things: **alpha** models
     * the glass being see-through and is now constant from top to bottom, and the **ramp**
     * models light falling across the surface and stops this far short of the page.
     * [GlassFillRampTest] holds both halves, because this is arithmetic no colour token can
     * see — `AccentContrastTest` was green the whole time the fill was vanishing.
     */
    const val FILL_FADE: Float = 0.4f

    /**
     * The lens over a [ttGlassSurface] that was given a `base`.
     *
     * White, to match the hairline, so a based surface is lit by the same light as its own
     * edge. Kept at 0.08 rather than at the hairline's 0.14, and the reason is arithmetic
     * rather than taste: this wash lands on the *only* fill in the app that carries a label,
     * so it spends the accent's contrast margin. The dark accent starts at 5.65:1 against
     * the label colour, and a 0.16 lens would drop it to 3.99:1 — a control that passes the
     * token suite and fails in dark mode. At 0.08 the dark variant composites to 4.73:1 and
     * the light one to 6.39:1. `GlassFillContrastTest` holds both numbers.
     */
    const val LENS_ALPHA: Float = 0.08f

    /** The hairline that draws the material's edge: `white.opacity(0.14)`, `lineWidth: 0.5`. */
    const val HAIRLINE_ALPHA: Float = 0.14f
    val hairlineWidth: Dp = 0.5.dp

    /** Press scale, from `TTGlassButtonStyle`'s `scaleEffect(0.95)`. */
    const val PRESSED_SCALE: Float = 0.95f

    /**
     * How far a disabled control is dimmed, from `.opacity(isEnabled ? 1 : 0.4)`.
     *
     * The same value `PrimaryButton` uses, and deliberately not raised: the disabled figure in
     * `PLAN.md`'s measurement table is 2.08–2.15:1, and it is left there because a control
     * the user cannot press *should* read as less available. Buying back its contrast would be
     * buying back the state the dim is communicating.
     */
    const val DISABLED_ALPHA: Float = 0.4f

    /**
     * Press spring, from the button's `.spring(duration: 0.2)`.
     *
     * `spring()` rather than `tween()` for both this and [selectionSpring], because the
     * original animated with springs and a spring settles rather than arriving — a button
     * that stops dead at 0.95 reads as a dropped frame.
     */
    val pressSpring: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

    /** Selection spring, from the picker's `.spring(duration: 0.3)`. Looser, so it slides. */
    val selectionSpring: SpringSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

    /**
     * How long the lens takes to settle after the stretch, in milliseconds.
     *
     * A [spring] would be the house style, and it is the wrong tool for this particular
     * animation. A spring's duration is a function of its damping and how far it has to
     * travel, so the same spec gives a snappy slide across one segment and a long, slow
     * settle across three. The stretch has to be *over* before the next one begins and to
     * land with the slide, which means a fixed duration: the lens arrives, then relaxes.
     */
    const val TAB_LENS_SETTLE_MILLIS: Int = 260

    /**
     * The tab lens's top stop: white at this alpha over the bar's own fill.
     *
     * Half of [LENS_ALPHA]'s opposite extreme and, more importantly, a third of what it would
     * be if the lens were meant to carry the selection on its own. Two separate ceilings are
     * involved. [LENS_ALPHA]'s doc explains the first: this lens sits *under a text label*, in
     * accent, and every increment of white here spends that label's contrast margin. [HAIRLINE_ALPHA]
     * is the second: the lens is stacked on a fill that is already [FILL_ALPHA] of white, so
     * the two wash together and the tab bar ends up markedly lighter than any other glass in
     * the app. At 0.14 the lens is a hint, and that is the job: see [TAB_LENS_RIM_ALPHA] for
     * what is actually meant to make it visible.
     */
    const val TAB_LENS_ALPHA: Float = 0.14f

    /**
     * How far the tab lens's **alpha** falls off toward its bottom.
     *
     * Steeper than [FILL_FADE] because a lens is a dome catching light along its top edge, not
     * a slab sitting under one. A lens that faded as gently as the bar would be a second bar,
     * just smaller.
     */
    const val TAB_LENS_FADE: Float = 0.75f

    /**
     * The tab lens's rim, which is what actually makes it visible.
     *
     * Over three times [HAIRLINE_ALPHA]. This is the load-bearing value of the whole
     * selection treatment: the body's wash is capped low enough to protect the accent label
     * under it, so the rim is what has to read as *a raised edge*, and a rim at hairline
     * strength would be swallowed by the bar's own hairline. The rim sits on the lens's
     * perimeter, not behind any glyph, so this costs the label nothing.
     */
    const val TAB_LENS_RIM_ALPHA: Float = 0.45f

    /**
     * How much wider the lens gets at the peak of its travel, as a fraction of its resting
     * width.
     *
     * iOS's lens leans into the direction of travel and settles back, and the lean is most of
     * what makes the movement read as liquid: a capsule that only translates is a highlight
     * moving, whereas one that distorts and recovers is a piece of material being displaced.
     * Kept small, because this is scaled about the lens's centre and the end segments have
     * no room on the outer side — [TTGlassTabBar] clamps the result back inside the bar, and
     * the clamp is only invisible below about 1.12.
     */
    const val TAB_LENS_WIDEN: Float = 0.10f

    /** The matching height shrink at the peak, as a fraction of the lens's resting height. */
    const val TAB_LENS_SQUASH: Float = 0.06f

    /**
     * How far the selected glyph grows, over the resting size.
     *
     * Small, and it is a real iOS cue rather than decoration: the system enlarges the active
     * tab's symbol slightly, which is most of why the selection is legible at a glance without
     * reading the tint. Capped well below the point where it would shift the label's
     * baseline, because the icon and the label are one stacked unit and only the icon moves.
     */
    const val TAB_LENS_ICON_SCALE: Float = 1.08f

    /**
     * The floating bar's drop shadow, and its colour.
     *
     * A capsule with no shadow sits on the canvas as a sticker rather than above it. This is
     * most of the "floating" read, and it is a *drop* rather than a glow: offset downward and
     * tinted with the accent rather than black, so it belongs to the same light source as the
     * app's other shadows.
     *
     * 10dp rather than something larger, because the capsule's glass is already light and a
     * deep shadow under a light object reads as dirt. The restraint is also what lets the
     * bar work on both appearances without a per-appearance value: on a light background the
     * shadow is nearly invisible and the inset alone carries the depth, and on a dark one
     * there is nothing to cast onto anyway.
     */
    val tabBarShadowElevation: Dp = 10.dp
    const val TAB_BAR_SHADOW_ALPHA: Float = 0.22f

    /**
     * The two stops an unbased glass surface fills with, as [ttGlassSurface] paints them.
     *
     * Extracted as a pure function for the same reason [LENS_ALPHA] is a constant rather than
     * a literal: this is compositing arithmetic, and the two defects that have lived in it —
     * a gradient that collapsed onto its last stop, and a bottom stop that composited away to
     * the page — were both invisible to every other test in the package. A function a test can
     * call is the only reason the third one is findable.
     *
     * [tint] is the caller's colour and `null` means "no tint", in which case [accent] stands
     * in — the same resolution the composable does, kept here so the arithmetic under test is
     * the arithmetic that ships. A caller's tint carries its own alpha and that alpha is the
     * floor; with no tint the floor is [FILL_ALPHA].
     *
     * [fillAlpha] scales that floor, which is how [PROMINENT_TINT_ALPHA] is reached without a
     * second code path: the ratio is passed in and the product is clamped, so the stops are
     * legal colours even when the caller asks for more than the floor can carry.
     */
    fun fillRamp(tint: Color?, canvas: Color, accent: Color, fillAlpha: Float): List<Color> {
        val top = tint ?: accent
        // Clamped rather than left over 1: the unclamped product was how a prominent control
        // reached opacity, and it left the stops holding an alpha no other draw path in the
        // package produces. `coerceIn` also keeps a caller passing 0 from inverting the ramp.
        val alpha = ((tint?.alpha ?: FILL_ALPHA) * fillAlpha).coerceIn(0f, 1f)
        return listOf(
            top.copy(alpha = alpha),
            lerp(top, canvas, FILL_FADE).copy(alpha = alpha),
        )
    }
}

/**
 * The one element that *is* the glass.
 *
 * Everything else in this file composes this, and it is public so a feature with a shape
 * the design system has never seen — a hex, a pill, a custom polygon — can become glass
 * without a new component here.
 *
 * [tint] is knocked down to [ttGlass.TINT_ALPHA] rather than used as given, and
 * `null` means no tint at all: a plain translucent fill. Both are the original's behaviour,
 * and both matter for the pairing in `AccentContrastTest` — glass carries labels, and a
 * tint strong enough to swallow the text behind it is the same class of bug as an accent
 * too light for its canvas.
 *
 * [base] is the escape hatch for a control that has to carry a label it cannot afford to lose
 * against. A translucent fill *cannot* do that: see [PrimaryButton], where the tint is a
 * 62%-alpha accent over a near-white canvas and the result lands at 1.07:1 against a white
 * label — measured on a device, not reasoned about. Passing an opaque colour here puts a solid
 * fill under the lens, which is the structure the original's sign-in button actually had (a
 * solid `Color.accentColor` background with a `glassEffect` lens over it) and the structure
 * this port had lost.
 */
@Composable
fun Modifier.ttGlassSurface(
    shape: Shape,
    tint: Color? = null,
    fillAlpha: Float = ttGlass.FILL_ALPHA,
    borderWidth: Dp = ttGlass.hairlineWidth,
    base: Color? = null,
): Modifier {
    val accent = ttAccent()
    val canvas = ttCanvas()

    // Two different washes, because there are two different things being modelled. Without a
    // base the fill *is* the glass, so it runs from the tint at the top to a calmer version of
    // itself at the bottom — lit where the light is, and never so far toward the page that the
    // surface disappears into it. With a base the glass is a lens *over* something solid, so it
    // is white fading to nothing and the base shows through underneath, which is the original's
    // arrangement. In the second case `fillAlpha` does not apply, because the tint's job is done
    // by the base.
    val wash = if (base == null) {
        ttGlass.fillRamp(tint = tint, canvas = canvas, accent = accent, fillAlpha = fillAlpha)
    } else {
        listOf(
            Color.White.copy(alpha = ttGlass.LENS_ALPHA),
            Color.Transparent,
        )
    }

    return this
        .clip(shape)
        .background(color = base ?: Color.Transparent, shape = shape)
        // A vertical wash rather than one flat translucent colour, so the top of a card reads
        // as "lit" and the bottom reads as "calmer" — which is the visual grammar of the
        // original. "Calmer" is the whole of it, though: the bottom stop is [ttGlass.FILL_FADE]
        // toward the canvas and no further, because a stop that *reaches* the canvas is a stop
        // that has painted over the surface's own bottom edge with the page. See
        // [ttGlass.FILL_FADE] for the measurement.
        //
        // Drawn through `drawBehind` with an explicit `endY` rather than handed to
        // `.background` as a `Brush.verticalGradient`, and that is not a style preference.
        // `Brush.verticalGradient` defaults to `endY = Float.POSITIVE_INFINITY`, which has no
        // meaningful value for an element whose height is not known yet: an infinite end
        // collapses the gradient onto its *last* stop. That is what made the sign-in button a
        // near-white slab — the accent stop was computed, then thrown away, and only the
        // `canvas` stop was ever painted. A modifier is built before it is measured, so the
        // gradient has to be resolved against the size the element actually got, which is
        // what `drawBehind` hands over. The two defects are independent and both were real:
        // fixing the end without fixing the stop is what left the white shadow behind.
        .drawBehind {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = wash,
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }
        // The hairline, in white on both appearances. It is not `ttSeparator`, because a
        // separator is a *structural* divider and this is a highlight: the point of a rim
        // highlight is that it is lighter than its surroundings at the top and edge, which
        // is true of white everywhere and is what makes the surface read as raised.
        .border(
            width = borderWidth,
            color = Color.White.copy(alpha = ttGlass.HAIRLINE_ALPHA),
            shape = shape,
        )
}

/**
 * A glass card, for content that floats over a scrolling surface.
 *
 * The original's only real consumer was the work item card, at
 * `ttGlassCard(tint: tint, cornerRadius: 14)`. The default here is [ttRadius.surface] (16)
 * to match the original's signature default; the card on the board passes
 * [ttRadius.control] explicitly, exactly as the original did.
 */
@Composable
fun Modifier.ttGlassCard(
    tint: Color? = null,
    cornerRadius: Dp = ttRadius.surface,
): Modifier = ttGlassSurface(shape = RoundedCornerShape(cornerRadius), tint = tint)

/**
 * A segmented control as one glass slab with a highlighted selection.
 *
 * Used for the project switcher on the board and backlog tabs. The slab is **one** glass
 * element wrapping all the segments, never one per segment — rule 2 above, and the reason
 * is structural rather than cosmetic: without a `GlassEffectContainer` there is nothing to
 * merge neighbouring materials, so per-segment glass would read as four separate pills
 * sitting in a row instead of one control.
 *
 * The selection is a solid tinted capsule that animates between positions, and the labels
 * are plain text so that a user who has turned text up gets a *wider slab* rather than a
 * clipped one. The label weight swaps with the selection, as the original's did, so the
 * selected segment is identifiable without relying on colour alone.
 */
@Composable
fun <Option> TTGlassSegmentedPicker(
    options: List<Option>,
    selection: Option,
    onSelect: (Option) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    // `@Composable`, not a plain lambda. Every label in this app comes from a string
    // resource, and `stringResource` is `@Composable` - so a non-composable `title` makes
    // the control unusable for its actual purpose, and the only way out is for a caller to
    // pre-resolve every label into a `List<String>` and lose the type it was given. Kotlin
    // still accepts a plain lambda literal here, so this costs existing callers nothing.
    title: @Composable (Option) -> String,
) {
    val accent = ttAccent()
    val canvas = ttCanvas()

    Row(
        modifier = modifier
            // Rule 1, in code: the slab's own inset is *inside* the material, and the
            // segment gaps are inside the slab. Nothing here adds space outside the glass,
            // because a caller that did would get a bar the same size with worse hit
            // targets.
            .ttGlassSurface(
                shape = CircleShape,
                tint = tint,
                borderWidth = ttGlass.hairlineWidth,
            )
            .padding(ttSpacing.hairlineInset),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (option in options) {
            Segment(
                label = title(option),
                isSelected = option == selection,
                accent = accent,
                canvas = canvas,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A wrapping glass choice group for compact spaces and variable length labels.
 *
 * Use this design-system component when a fixed segmented row would squeeze labels or
 * overflow at larger text sizes. Options keep their natural width, wrap onto another row,
 * and share the same selected glass treatment as [TTGlassSegmentedPicker].
 */
@Composable
fun <Option> TTGlassFlowPicker(
    options: List<Option>,
    selection: Option,
    onSelect: (Option) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    title: @Composable (Option) -> String,
) {
    val accent = ttAccent()
    val canvas = ttCanvas()
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<Option, IntRect>() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val selectedBounds = positions[selection]
    val lensX by animateDpAsState(
        targetValue = with(density) { (selectedBounds?.left ?: 0).toDp() },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "flowPickerLensX",
    )
    val lensY by animateDpAsState(
        targetValue = with(density) { (selectedBounds?.top ?: 0).toDp() },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "flowPickerLensY",
    )
    val lensWidth by animateDpAsState(
        targetValue = with(density) { (selectedBounds?.width ?: 0).toDp() },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "flowPickerLensWidth",
    )
    val lensHeight by animateDpAsState(
        targetValue = with(density) { (selectedBounds?.height ?: 0).toDp() },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "flowPickerLensHeight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(
                shape = RoundedCornerShape(ttRadius.surface),
                tint = tint,
                borderWidth = ttGlass.hairlineWidth,
            )
            .padding(ttSpacing.sm)
            .onGloballyPositioned { containerCoordinates = it },
    ) {
        if (selectedBounds != null) {
            Box(
                modifier = Modifier
                    .offset(x = lensX, y = lensY)
                    .size(lensWidth, lensHeight)
                    .clip(CircleShape)
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = ttGlass.TAB_LENS_ALPHA),
                                    Color.White.copy(
                                        alpha = ttGlass.TAB_LENS_ALPHA * (1f - ttGlass.TAB_LENS_FADE),
                                    ),
                                ),
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

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        ) {
            options.forEach { option ->
                FlowPickerChip(
                    label = title(option),
                    isSelected = option == selection,
                    accent = accent,
                    onClick = { onSelect(option) },
                    onPositioned = { coordinates ->
                        val parent = containerCoordinates
                        if (parent != null) {
                            val position = parent.localPositionOf(coordinates, Offset.Zero)
                            val left = position.x.roundToInt()
                            val top = position.y.roundToInt()
                            val measured = IntRect(
                                left = left,
                                top = top,
                                right = left + coordinates.size.width,
                                bottom = top + coordinates.size.height,
                            )
                            if (positions[option] != measured) positions[option] = measured
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FlowPickerChip(
    label: String,
    isSelected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .onGloballyPositioned(onPositioned)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = ttSpacing.xs, horizontal = ttSpacing.sm)
            .semantics {
                contentDescription = label
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = label,
            style = if (isSelected) ttTypography.subheadlineSelected else ttTypography.subheadline,
            color = if (isSelected) ttLabel() else lerp(accent, Color.White, FLOW_PICKER_INACTIVE_LIGHTENING),
            textAlign = TextAlign.Center,
        )
    }
}

private const val FLOW_PICKER_INACTIVE_LIGHTENING = 0.18f

@Composable
private fun Segment(
    label: String,
    isSelected: Boolean,
    accent: Color,
    canvas: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // One shared animation drives the selected pill's fill. The label stays opaque while the
    // fill moves, so inactive options remain readable throughout the transition.
    val selectionAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = ttGlass.selectionSpring,
        label = "segmentSelection",
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                color = if (isSelected) accent.copy(alpha = selectionAlpha) else canvas.copy(alpha = 0f),
                shape = CircleShape,
            )
            .then(
                Modifier.padding(
                    vertical = if (compact) ttSpacing.xs else ttSpacing.controlPaddingV,
                    horizontal = if (compact) ttSpacing.sm else ttSpacing.controlPaddingH,
                ),
            )
            .clickable(role = Role.Tab, onClick = onClick)
            // The label carries the selected state for accessibility, because a tint alone
            // is not a state: a screen reader user has no way to tell a selected segment
            // from an unselected one otherwise, and neither has anyone who cannot separate
            // the two colours.
            // `isSelected` rather than a parameter named `selected`: the semantics
            // property has that name too, and the two shadow each other into an
            // unassignable `val`.
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        // The label colour is chosen against the pill, not against the canvas — and the
        // pill only exists when selected, which is why this is a two-way choice rather than
        // a modifier chain. `ttOnAccent` is the pairing `AccentContrastTest` enforces.
        val content = if (isSelected) ttOnAccent else ttLabel()
        Box(modifier = Modifier.semantics { contentDescription = label }) {
            ttText(
                text = label,
                style = if (isSelected) {
                    ttTypography.subheadlineSelected
                } else {
                    ttTypography.subheadline
                },
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * How a [ttGlassButton] looks.
 *
 * The port's stand-in for the original's `TTGlassButtonStyle: ButtonStyle`, and named after
 * it deliberately. A Material `ButtonStyle` exists so a style can be *named once and
 * applied at many call sites* — that is the part worth keeping, and it does not need
 * Material to work.
 *
 * [tint] is `null` for the resting state rather than a colour, because "no tint" and "tint
 * with the canvas" are not the same: the first lets the backdrop show through, the second
 * paints over it. An earlier draft took a `prominent: Boolean` instead, which cannot express
 * a destructive red or a disabled state without a second boolean.
 */
data class TTGlassButtonStyle(
    /** The fill for a resting button, or `null` for untinted glass. */
    val tint: Color? = null,
    /** Whether the label sits on [ttOnAccent] rather than [ttLabel]. Set with the tint. */
    val prominent: Boolean = false,
) {
    internal companion object {
        /** The resting style: glass, and nothing else. */
        val resting = TTGlassButtonStyle()

        /** An accent-filled button, for a primary or destructive action. */
        fun prominent(tint: Color? = null) = TTGlassButtonStyle(tint = tint, prominent = true)
    }
}

/**
 * A toolbar button on glass, for actions that float over scrolling content.
 *
 * The platform's own toolbar already sits on system glass; this is for *in-content*
 * controls — a board column's Add button, a floating action — where the material has to
 * grow with its label rather than float in a fixed band.
 *
 * [style] carries the appearance, so a screen can define one button style and reuse it the
 * way the original reuses its `ButtonStyle`.
 *
 * ## [enabled] is a separate parameter, not a style field
 *
 * A disabled control is still the same control wearing a different appearance, and there is
 * exactly one thing to get right about it: it must not answer a tap. Folding that into
 * [TTGlassButtonStyle] would make it a *style* nobody can switch off, because a caller
 * writing `style = TTGlassButtonStyle.prominent()` would silently get an enabled button and
 * a caller writing `disabled = true` into the style would be describing a look rather than
 * a behaviour. So `enabled` is here, next to `onClick`, which is the pair it actually
 * belongs with.
 *
 * The dim is [ttGlass.DISABLED_ALPHA] over the whole control, label included. The original's
 * `.opacity(isEnabled ? 1 : 0.4)` did the same, and the measurement in `PLAN.md` records
 * why the dim is kept: a disabled control is *supposed* to look less available, so buying
 * back contrast on it is buying back the thing the state is communicating.
 */
@Composable
fun ttGlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TTGlassButtonStyle = TTGlassButtonStyle.resting,
    enabled: Boolean = true,
) {
    val accent = ttAccent()
    val resolvedTint = if (style.prominent) (style.tint ?: accent) else style.tint

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
            .ttGlassSurface(
                shape = CircleShape,
                tint = resolvedTint,
                fillAlpha = if (style.prominent) ttGlass.PROMINENT_TINT_ALPHA / ttGlass.FILL_ALPHA else 1f,
            )
            // Rule 1 again, and the reason the padding is *inside* the glass: applied after
            // the material it would enlarge the row without enlarging the control.
            .padding(
                vertical = ttSpacing.buttonPaddingV,
                horizontal = ttSpacing.buttonPaddingH,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val content = if (style.prominent) ttOnAccent else ttLabel()
        Box(modifier = Modifier.semantics { contentDescription = label }) {
            ttText(
                text = label,
                style = ttTypography.button,
                color = content,
                textAlign = TextAlign.Center,
            )
        }
    }
}
