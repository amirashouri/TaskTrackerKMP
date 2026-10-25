package com.tasktracker.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp as lerpFloat

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

    /**
 * How far the fill's **hue** travels from the tint toward the canvas by the bottom edge.
 *
 * The original's grammar is "lit at the top, sitting over the page at the bottom". The
 * first port expressed that by fading the fill's **alpha** to nothing — the top stop was
 * the tint at [FILL_ALPHA] and the bottom stop was the canvas at a third of an alpha
 * —
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
    /** @deprecated Use [GlassThickness.STANDARD.baseAlpha] instead. */
    @Deprecated("Use GlassThickness.STANDARD.baseAlpha", ReplaceWith("ttGlass.GlassThickness.STANDARD.baseAlpha"))
    val FILL_ALPHA: Float = GlassThickness.STANDARD.baseAlpha

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

    /**
     * The dark-appearance hairline that draws the material's edge:
     * `white.opacity(0.14)`, `lineWidth: 0.5`.
     *
     * White carries the edge only where the fill is dark enough to sit under it:
     * over the dark fill the rim renders 1.54:1, which is the bar
     * `GlassSurfacePixelTest` holds both appearances to. The light fill already
     * composites to about #C2CBD7, so white over it measures 1.07:1 and no white
     * above that fill reaches the bar — the light rim is [LIGHT_HAIRLINE_ALPHA],
     * a shadow, instead.
     */
    const val HAIRLINE_ALPHA: Float = 0.14f

    /**
     * The light-appearance hairline: black at 0.22, a shadow line rather than a
     * highlight.
     *
     * The light glass fill composites to about #C2CBD7, which is darker than the
     * canvas and near the top of what white can reach: measured, the white rim is
     * 1.07:1 as shipped, 1.30:1 at 0.5, and 1.64:1 fully opaque — a stark line,
     * not a hairline, and only barely over the 1.5:1 the dark appearance reaches.
     * A rim that reads in light has to come from the other direction: black at
     * 0.22 over the fill renders 1.65:1 on both rasterisers
     * (`GlassSurfacePixelTest`), a quiet shadow that reads as the same raised edge
     * the white line is in dark.
     */
    const val LIGHT_HAIRLINE_ALPHA: Float = 0.22f
    val hairlineWidth: Dp = 0.5.dp

    /** Press scale, from `TTGlassButtonStyle`'s `scaleEffect(0.95)`. */
    const val PRESSED_SCALE: Float = 0.95f

    /**
     * Glass thickness levels — controls the base fill alpha before intensity scaling.
     *
     * Three levels create visual hierarchy:
     * - THIN (0.45): inline elements (chips, badges) — subtle glass
     * - STANDARD (0.55): cards, segmented picker, flow picker slab — default glass
     * - THICK (0.65): modal sheets, full-screen overlays — prominent glass
     *
     * The actual rendered alpha is `thickness.baseAlpha * intensityScale` where
     * `intensityScale` comes from `LocalGlassIntensity` (1.0 = full glass, 0.0 = opaque).
     * At intensity 0 all thicknesses converge to opaque; at intensity 1 they separate.
     */
/**
 * Glass thickness levels — controls the base fill alpha before intensity scaling.
 *
 * Three levels create visual hierarchy:
 * - THIN (0.45): inline elements (chips, badges) — subtle glass
 * - STANDARD (0.55): cards, segmented picker, flow picker slab — default glass
 * - THICK (0.65): modal sheets, full-screen overlays — prominent glass
 *
 * The actual rendered alpha is `thickness.baseAlpha * intensityScale` where
 * `intensityScale` comes from `LocalGlassIntensity` (1.0 = full glass, 0.0 = opaque).
 * At intensity 0 all thicknesses converge to opaque; at intensity 1 they separate.
 */
sealed class GlassThickness(val baseAlpha: Float) {
    object THIN : GlassThickness(0.45f)
    object STANDARD : GlassThickness(0.55f)
    object THICK : GlassThickness(0.65f)
}

    /**
     * How far a disabled control is dimmed, from `.opacity(isEnabled ? 1 : 0.4)`.
     *
     * The same value `PrimaryButton` uses, and deliberately not raised: the disabled figure in
     * `docs/PORT-LOG.md`'s measurement table is 2.08–2.15:1, and it is left there because a control
     * the user cannot press *should* read as less available. Buying back its contrast would be
     * buying back the state the dim is communicating.
     *
     * That first sentence cited a *feature's* component for a design-system constant.
     * `PrimaryButton` still carries a private copy at the same number, so the claim described
     * a coincidence between two constants that have nothing to do with each other — which is
     * exactly the kind of note that makes a reader believe a coupling that does not exist.
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
     * (NOTE: [FILL_ALPHA] is deprecated and now equals [GlassThickness.STANDARD.baseAlpha].)
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
     * The selection wash's alpha, for a dropdown option row.
     *
     * 0.25, and the intent was already measured before this token existed: at this alpha over
     * the pixel test's near-black backdrop (0xFF1B1B1F) the *dimmest* palette's dark accent —
     * the default Classic, 0xFF5F6873 — composites to a channel distance of 21, and the
     * palette sweep runs 21…47, so every palette clears the test's 12-channel bar with margin
     * while the row still reads as glass rather than as a filled chip. The measurement lives in
     * `ThemeOptionScenarios.PAINTED_BAR` and `OptionBackdrop`; this is the token that holds the
     * value, so the feature and the test cannot drift.
     */
    const val SELECTION_WASH_ALPHA: Float = 0.25f

    /**
     * How far the rim's colour falls off toward its bottom, as a fraction of its own alpha.
     *
     * The rim is a gradient rather than a flat line because iOS's edge is a function of
     * the surface normal: it catches light along the top and darkens toward the bottom.
     * 0.6 keeps the bottom of the rim clearly present — it is still the same edge, just
     * dimmer — while the top stays at full [HAIRLINE_ALPHA] / [LIGHT_HAIRLINE_ALPHA], which
     * is the value `GlassSurfacePixelTest` measures. A value much below 0.4 would start
     * to read as a shadow at the bottom rather than as the same rim tapering.
     */
    const val RIM_BOTTOM_ALPHA: Float = 0.6f

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
     * The lens over a *solid, saturated* fill: the theme swatch's glass sheen.
     *
     * More than three times [TAB_LENS_ALPHA], and the reason is the backdrop.The tab lens is
     * a hint because it sits on a fill that is already [FILL_ALPHA] of white and *under an
     * accent label whose contrast margin the wash spends.
     *
     * NOTE: [FILL_ALPHA] is deprecated and now equals [GlassThickness.STANDARD.baseAlpha]. A palette swatch has neither: it is
     * a flat, fully saturated fill, and the lens is the only thing on it, so the wash has to
     * carry the whole "this is glass" reading rather than confirm one. At [TAB_LENS_ALPHA] the
     * selected swatch measured only a 1.15:1 top-to-bottom lift — a sheen a person cannot see,
     * which is the defect this value exists to fix. `AccentSwatchScenarios` holds the measured
     * lift on both rasterisers.
     */
    const val SOLID_LENS_ALPHA: Float = 0.45f

    /**
     * The rim for [SOLID_LENS_ALPHA], stronger than [TAB_LENS_RIM_ALPHA] for the same reason:
     * over a saturated fill the tab-strength rim muddies toward the fill's hue and stops
     * reading as a raised glass edge.
     */
    const val SOLID_LENS_RIM_ALPHA: Float = 0.6f

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
     * A specular highlight along a surface's top edge: white at this alpha, fading to
     * nothing over [SPECULAR_HEIGHT] of the surface.
     *
     * iOS glass is a dome, and the thing that makes it read as a *surface* rather than a
     * flat translucent card is the band of light it catches along its top edge. The fill
     * ramp already does "lit at the top, calmer at the bottom" but only as a **colour**
     * ramp — the top stop is the tint at [FILL_ALPHA], with no separate bright band, and
     * (NOTE: [FILL_ALPHA] is deprecated and now equals [GlassThickness.STANDARD.baseAlpha].)
     * without this every surface in the app is a tinted slab.
     *
     * This is the one addition above the original's grammar that is not a port of it,
     * and it is worth saying so because it is the part most likely to be mistaken for a
     * bug: the original gets its highlight for free from live backdrop sampling, and this
     * port cannot. A highlight band that is *there* is better than one that is *modelled*,
     * which is why it is painted rather than asserted into existence.
     *
     * 0.12, and low on purpose: over the dark fill white at this alpha composites to a
     * visible bright edge, and over the light fill — which is already near the top of
     * what white can reach — it is a quiet brightening. A band strong enough to read as
     * a white stripe on the light appearance would be the "white shadow" defect this
     * file already caught once, arriving from the other direction.
     */
    const val SPECULAR_ALPHA: Float = 0.12f

    /**
     * How far down the surface the highlight runs.
     *
     * A `Dp`, not a `Float`, because it is drawn through `toPx()` inside a `DrawScope` and
     * that is the only overload that exists. Narrow and **fixed in dp rather than as a
     * fraction of the surface**: a fraction was the first choice — 6% of a 240dp card is
     * 14dp, which is a plausible band — and it turned out to be the wrong shape for a rim
     * highlight. It is a property of the *edge*, not of the face, and a band that wide reads
     * as a second, whiter fill rather than as light catching a dome. It also broke
     * `StoryPointsTest`, whose background sample sits 6dp below the card's top edge and
     * therefore landed *inside* the band, reading the highlight as the "fill" and the rest
     * of the card as "painted" — 93% coverage, a false pill.
     *
     * 3dp is below that sample on any card tall enough to hold a numeral, and above the
     * antialiasing noise a rasteriser introduces at a hairline. At the host's density 1 it
     * is 3 device rows; at 3x it is 9, still a rim.
     */
    val SPECULAR_HEIGHT_DP: Dp = 3.dp

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
     * floor; with no tint the floor is [thickness.baseAlpha].
     *
     * [thickness] is the glass thickness level, which provides the base alpha.
     *
     * [intensity] is the user's glass setting, and it is a parameter rather than a
     * `compositionLocalOf` read *inside* here for one reason: this function is pure, and the
     * tests in `GlassFillRampTest` call it directly because two of the three defects that
     * lived in this arithmetic were invisible to every other test in the package. A function
     * that reads ambient composition cannot be called from a plain unit test without a
     * composition, which would put the arithmetic back behind a renderer. Defaulted to `1f`,
     * so every existing call and every existing assertion is unchanged.
     *
     * It enters as a cross-fade toward opaque rather than as a multiplier on the result,
     * because "less glass" and "more transparent" are the same request but not the same
     * arithmetic: multiplying `0.22` by a smaller number walks the fill toward *invisible*,
     * which is a hole in the page. Interpolating the floor from `1f` down to the measured
     * value walks it toward *solid*, which is Reduce Transparency.
     */
    fun fillRamp(
        tint: Color?,
        canvas: Color,
        accent: Color,
        thickness: GlassThickness = GlassThickness.STANDARD,
        intensity: Float = 1f,
        reducedSurface: Color,
    ): List<Color> {
        val top = tint ?: accent
        // Clamped rather than left over 1: the unclamped product was how a prominent control
        // reached opacity, and it left the stops holding an alpha no other draw path in the
        // package produces. `coerceIn` also keeps a caller passing 0 from inverting the ramp.
        //
        // This is the alpha the app painted before the slider existed, computed first and
        // *then* cross-faded — which is the order that matters. Fading the floor and letting
        // [fillAlpha] multiply the result looks equivalent and is not: `ttGlassSurface` defaults
        // [fillAlpha] to [FILL_ALPHA], so an untinted surface's shipped alpha is
        // `FILL_ALPHA * FILL_ALPHA` = 0.3025
        // NOTE: [FILL_ALPHA] is deprecated and now equals [GlassThickness.STANDARD.baseAlpha].
        // Fading its floor toward 1 and then multiplying
        // by 0.55 again left the bottom of the slider at 0.55 — translucent, which is the one
        // thing it must not be. Measured: that version rendered #90a1b6 where the accent is
        // #34537a. Interpolating the finished alpha reaches a true opaque 1.0 and still puts
        // intensity 1 exactly on the arithmetic above, to the bit.
        // The floor is the tint's own alpha (if provided) or the thickness's base alpha.
        // We do NOT multiply by thickness.baseAlpha again — the thickness IS the floor.
        val shipped = (tint?.alpha ?: thickness.baseAlpha).coerceIn(0f, 1f)
        val i = intensity.coerceIn(0f, 1f)
        val alpha = lerpFloat(1f, shipped, i)
        // The colour crosses over as well, and this is the second half of what Reduce
        // Transparency means. Fading only the *alpha* toward 1 makes every surface an opaque
        // slab of the **accent** — measured at 20% intensity in Light appearance, an 0.86-alpha
        // #34537a compositing to #506b8c over the page, which reads as the whole app going
        // dark and gaining a colour cast. That is the opposite of reducing transparency: the
        // accent *is* the glass vocabulary, and it is what has to go. So the colour travels to
        // [reducedSurface], the app's own neutral for a solid surface — the same colour
        // `ttGlassSurface`'s `base` is given by the sheet and the switcher capsule.
        //
        // At `i = 1f` both interpolations are the identity, so the top of the slider is the
        // arrangement that shipped, unchanged.
        //
        // `reducedSurface` has no default on purpose. A defaulted neutral would have been
        // silently wrong for every caller that did not pass one — an omitted argument is exactly
        // how the accent came to be the end of the travel in the first place, and a signature
        // that permits the mistake is a signature that will repeat it.
        val crossed = lerp(reducedSurface, top, i)
        return listOf(
            crossed.copy(alpha = alpha),
            lerp(crossed, canvas, FILL_FADE * i).copy(alpha = alpha),
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
 * [thickness] controls the base fill alpha (THIN=0.45, STANDARD=0.55, THICK=0.65).
 * The actual rendered alpha is scaled by [LocalGlassIntensity] (1.0 = full glass, 0.0 = opaque).
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
    thickness: ttGlass.GlassThickness = ttGlass.GlassThickness.STANDARD,
    borderWidth: Dp = ttGlass.hairlineWidth,
    base: Color? = null,
): Modifier {
    val accent = ttAccent()
    val canvas = ttCanvas()
    val intensity = LocalGlassIntensity.current
    val rim = ttGlassHairline()
    val isDark = LocalThemeScheme.current == ThemeScheme.Dark
    val deviceMotion = DeviceMotion()
    val roll = deviceMotion.roll()

    // Two different washes, because there are two different things being modelled. Without a
    // base the fill *is* the glass, so it runs from the tint at the top to a calmer version of
    // itself at the bottom — lit where the light is, and never so far toward the page that the
    // surface disappears into it. With a base the glass is a lens *over* something solid, so it
    // is white fading to nothing and the base shows through underneath, which is the original's
    // arrangement. In the second case thickness does not apply, because the tint's job is done
    // by the base.
    val wash = if (base == null) {
        ttGlass.fillRamp(
            tint = tint,
            canvas = canvas,
            accent = accent,
            thickness = thickness,
            intensity = intensity,
            // Where the fill arrives as transparency is reduced: the app's neutral, not the
            // accent. Resolved here rather than inside `fillRamp` so that stays a pure function
            // of its arguments and the neutral is a token the caller can vary.
            reducedSurface = ttSurface(),
        )
    } else {
        listOf(
            Color.White.copy(alpha = ttGlassLensAlpha()),
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

            // Subsurface scattering on wide cards: light enters the top edge and scatters
            // toward the sides, creating a subtle center-to-edge brightening.
            // Only on unbased surfaces (where base == null) and wide aspect ratio (> 2:1).
            if (base == null && size.width > 2f * size.height) {
                val radius = size.width * 0.6f
                val centerX = size.width / 2f
                val centerY = 0f
                val scatterAlpha = 0.025f // ≤ 0.03 at center, 0 at edges
                drawRect(
                    brush = Brush.radialGradient(
                        center = Offset(centerX, centerY),
                        radius = radius,
                        colors = listOf(
                            Color.White.copy(alpha = scatterAlpha),
                            Color.Transparent,
                        ),
                    ),
                )
            }

            // The rim highlight, drawn over the fill and under the border. See
            // [ttGlass.SPECULAR_ALPHA] for why this exists at all and why it is this low: it
            // is the one part of the look that is not a port of the original, and the original
            // gets it for free from live backdrop sampling. The band is painted here rather
            // than in the fill ramp because it is a property of the *edge* — it fades out
            // over a small fraction of the height — and belongs to the surface rather than
            // to the arithmetic the ramp does.
            //
            // Fixed in dp rather than a fraction of the surface (see [ttGlass.SPECULAR_HEIGHT_DP]):
            // a fraction grows with the card, and on a 240dp card the band reached 14dp — past
            // the point where a rim highlight stops being a rim and starts being a second fill.
            //
            // Specular parallax: shift horizontally based on device roll (-1..1).
            // Max offset = 15% of width, so at max tilt the highlight moves 15% across the surface.
            val specularOffset = roll * size.width * 0.15f
            val specularHeight = with(density) { ttGlass.SPECULAR_HEIGHT_DP.toPx() }
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = ttGlass.SPECULAR_ALPHA),
                        Color.White.copy(alpha = 0f),
                    ),
                    start = Offset(specularOffset, 0f),
                    end = Offset(specularOffset, specularHeight),
                ),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, specularHeight),
            )

            // Chromatic aberration: subtle prism effect at the refractive edge.
            // Only in dark mode (where rim is a highlight) and only when border exists.
            // Drawn just inside the top edge to avoid rounded-corner clipping.
            if (isDark && borderWidth > 0.dp && base == null) {
                val strokePx = with(density) { borderWidth.toPx() }
                val y = strokePx // Just inside the top edge, below the rounded corner clip
                val alpha = rim.alpha * 0.4f
                drawLine(
                    color = Color.Red.copy(alpha = alpha),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokePx,
                )
                drawLine(
                    color = Color.Green.copy(alpha = alpha * 1.5f),
                    start = Offset(0f, y + strokePx * 0.5f),
                    end = Offset(size.width, y + strokePx * 0.5f),
                    strokeWidth = strokePx,
                )
                drawLine(
                    color = Color.Blue.copy(alpha = alpha),
                    start = Offset(0f, y + strokePx),
                    end = Offset(size.width, y + strokePx),
                    strokeWidth = strokePx,
                )
            }
        }
        // The rim. It is not `ttSeparator`, because a separator is a
        // *structural* divider and this is an edge: the point of a rim is that the
        // surface reads as raised, which is lighter-than-the-fill in dark and —
        // because the light fill is already near the top of what white can reach —
        // darker-than-the-fill in light. See [ttGlass.HAIRLINE_ALPHA] and
        // [ttGlass.LIGHT_HAIRLINE_ALPHA] for the measurements behind both.
        //
        // Drawn with a `Brush` rather than a flat colour, and the reason is the rim's
        // **gradient**. iOS's edge is a function of the surface normal, so it is brightest
        // where it catches the light along the top and darkest along the bottom; a flat
        // border cannot express that, and a flat rim is the single biggest thing that
        // keeps these surfaces reading as a painted card rather than as material. The
        // strongest row is unchanged — it is still the top of the stroke at full rim
        // colour — so `GlassSurfacePixelTest`'s two rim figures hold without moving.
        //
        // `.border(brush=...)` rather than `.border(color=...)` is the only portable
        // way to get a gradient on the stroke: `drawOutline` and `Shape.createOutline`
        // are not in Compose Multiplatform 1.12.1's commonMain. The brush variant draws
        // the gradient along the stroke, which is what makes the top edge brighter than
        // the bottom.
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(
                colors = listOf(
                    rim,
                    rim.copy(alpha = rim.alpha * ttGlass.RIM_BOTTOM_ALPHA),
                ),
                startY = 0f,
                endY = Float.POSITIVE_INFINITY,
            ),
            shape = shape,
        )
}

/**
 * The selection lens, as a modifier over whatever fill the node already paints.
 *
 * This is the shared half of the treatment [TTGlassSegmentedPicker], [TTGlassFlowPicker] and the
 * theme colour picker put on their selected item. The parts that move — the segment bounds, the
 * tab bar's travel and stretch — stay with those components; the wash-and-rim pair itself is one
 * object, so a new selected state cannot drift into drawing a differently-lit lens.
 *
 * It is a plain translucent wash plus a rim rather than a [ttGlassSurface], deliberately: the
 * pickers already sit inside a glass slab, and a second [ttGlassSurface] would stack two full
 * fill ramps and two borders. [ttGlass.TAB_LENS_ALPHA] and [ttGlass.TAB_LENS_RIM_ALPHA] carry
 * the measurements for both stops. The wash scales with [LocalGlassIntensity], so an opaque
 * appearance has no dome to catch it.
 *
 * [washAlpha] and [rimAlpha] default to that tab-lens pair. A caller placing the lens over a
 * *solid, saturated* fill instead — where there is no glass slab to carry the light and no
 * label to protect — raises them to [ttGlass.SOLID_LENS_ALPHA] and
 * [ttGlass.SOLID_LENS_RIM_ALPHA]; see those for why.
 *
 * The wash is clipped to [shape]; the rim is not, because `border` draws its own outline. That
 * is the same arrangement the tab bar's lens uses, and it is what keeps a rectangle wearing a
 * capsule's outline from ever reappearing (see the measurement in `TabBar.kt`'s `TabLens`).
 */
@Composable
fun Modifier.ttGlassLens(
    shape: Shape,
    washAlpha: Float = ttGlass.TAB_LENS_ALPHA,
    rimAlpha: Float = ttGlass.TAB_LENS_RIM_ALPHA,
    velocityX: Dp = 0.dp,
): Modifier {
    val intensity = LocalGlassIntensity.current
    val density = LocalDensity.current
    // Motion blur threshold: 500 dp/s triggers blur, max radius 8dp
    val velocityThreshold = 500.dp
    val maxBlurRadius = 8.dp
    val speed = kotlin.math.abs(velocityX.value)
    val blurRadius: Float = if (speed > velocityThreshold.value) {
        ((speed - velocityThreshold.value) * 0.01f).coerceIn(0f, maxBlurRadius.value)
    } else 0f
    val blurPx = with(density) { Dp(blurRadius).toPx() }
    return this
        .clip(shape)
        .drawBehind {
            val lensAlpha = washAlpha * intensity.coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = lensAlpha),
                        Color.White.copy(alpha = lensAlpha * (1f - ttGlass.TAB_LENS_FADE)),
                    ),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            // Motion blur simulation: when moving fast, draw a semi-transparent trail
            if (blurRadius > 0f) {
                val trailAlpha = (blurRadius / maxBlurRadius.value * 0.15f).coerceIn(0f, 0.15f)
                val direction = if (velocityX.value > 0) 1f else -1f
                val trailWidth = (blurPx * 2f).coerceIn(0f, size.width * 0.5f)
                val trailX = if (velocityX.value > 0) -trailWidth else 0f
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = trailAlpha),
                            Color.White.copy(alpha = 0f),
                        ),
                        startX = 0f,
                        endX = trailWidth,
                    ),
                    topLeft = Offset(trailX, 0f),
                    size = Size(trailWidth, size.height),
                )
            }
        }
        .border(
            width = ttGlass.hairlineWidth,
            color = Color.White.copy(alpha = rimAlpha),
            shape = shape,
        )
}

/**
 * A selection wash: the accent at a fixed alpha, animated in and out on selection change.
 *
 * The settings theme picker's selected row, and the user position / status pickers that share
 * its dropdown vocabulary. It is the dropdown's answer to the same question the tab bar's lens
 * answers with travel: **how does a selection announce itself?**
 *
 * ## Why it is animated rather than swapped
 *
 * The picker's options are full-width rows in a vertical popup column, so there is no
 * horizontal distance to travel and no capsule to slide between them — a lens that *moved*
 * here would be a lens sliding down a list, which is a different effect entirely and not the
 * one the tab bar does. What the picker *can* do is fade: the accent wash arrives with a
 * spring and leaves with one, and that is enough to make the change read as a response rather
 * than a repaint.
 *
 * ## Why it lives here rather than in the feature
 *
 * Three dropdowns already painted this wash, and the alpha was duplicated across them as
 * `SelectedOptionFillAlpha` in `feature-settings-presentation`. A wash that is one token is a
 * wash that cannot drift: the feature can pick the accent and the rounding, but the *how* —
 * the alpha, the spring, the fact that it animates at all — is the design system's to own.
 *
 * ## What it does not do
 *
 * It is not a lens and does not call [ttGlassSurface]: the picker rows sit inside a glass
 * column and stacking a second full fill ramp on top of one would be two materials where one
 * belongs. It is a translucent accent wash over the row's own background, which is the
 * vocabulary the dropdown uses.
 */
@Composable
fun Modifier.ttSelectionWash(
    selected: Boolean,
    alpha: Float = ttGlass.SELECTION_WASH_ALPHA,
): Modifier {
    val target = if (selected) alpha else 0f
    val washAlpha by animateFloatAsState(
        targetValue = target,
        animationSpec = ttGlass.selectionSpring,
        label = "selectionWash",
    )
    val accent = ttAccent()
    return this.background(
        color = accent.copy(alpha = washAlpha),
        shape = RoundedCornerShape(ttRadius.control),
    )
}

/**
 * Where a traveling lens should sit, as four animated Dp values.
 *
 * A lens that *moves* is a lens that reads as material being displaced rather than a highlight
 * that jumped: it travels, and while it travels it leans into the motion and settles. The
 * segmented picker, the flow picker, and — after this — the accent palette picker all do the
 * same four-value animation, and the four were inline in two of them before this existed,
 * which is how two copies of the same spring and the same `IntRect`-to-Dp arithmetic came to
 * sit in three places. This is the one copy.
 *
 * The four values are animated rather than computed once, because the lens has to *arrive* at
 * the new position — the first composition of a picker has no previous bounds to travel from,
 * and a lens that snaps to its cell on open looks like a repaint, not a response.
 */
@Composable
fun <Option> ttLensPlacement(
    positions: Map<Option, IntRect>,
    selection: Option,
    density: Density,
): LensPlacement {
    val selectedBounds = positions[selection]
    val lensSpring: AnimationSpec<Dp> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

    val targetX = with(density) { (selectedBounds?.left ?: 0).toDp() }
    val targetY = with(density) { (selectedBounds?.top ?: 0).toDp() }
    val targetWidth = with(density) { (selectedBounds?.width ?: 0).toDp() }
    val targetHeight = with(density) { (selectedBounds?.height ?: 0).toDp() }

    val lensX by animateDpAsState(targetValue = targetX, animationSpec = lensSpring, label = "lensX")
    val lensY by animateDpAsState(targetValue = targetY, animationSpec = lensSpring, label = "lensY")
    val lensWidth by animateDpAsState(targetValue = targetWidth, animationSpec = lensSpring, label = "lensWidth")
    val lensHeight by animateDpAsState(targetValue = targetHeight, animationSpec = lensSpring, label = "lensHeight")

    // Use a plain class rather than mutableStateOf so writes don't trigger recomposition
    class VelocityTracker(var prevX: Dp, var prevTime: Long)
    val tracker = remember { VelocityTracker(lensX, 0L) }

    val currentTime = currentNanoTime()
    val dtSeconds = if (tracker.prevTime > 0) (currentTime - tracker.prevTime) / 1_000_000_000.0 else 0.0
    val deltaX = lensX.value - tracker.prevX.value
    val currentVelocityX = if (dtSeconds > 0) (deltaX / dtSeconds).dp else 0.dp

    tracker.prevX = lensX
    tracker.prevTime = currentTime

    return LensPlacement(lensX, lensY, lensWidth, lensHeight, currentVelocityX)
}

/**
 * The four animated dimensions of a traveling lens, plus velocity for motion blur.
 *
 * A data class rather than four separate return values so a caller writes one destructuring
 * and cannot update one without the other — the four have to move together or the lens distorts.
 * Velocity is in dp/s for horizontal travel (x-axis).
 */
data class LensPlacement(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val velocityX: Dp = 0.dp,
)

/**
 * A glass card, for content that floats over a scrolling surface.
 *
 * The original's only real consumer was the work item card, at
 * `ttGlassCard(tint: tint, cornerRadius: 14)`. The default here is [ttRadius.surface] (16)
 * to match the original's signature default; the card on the board passes
 * [ttRadius.control] explicitly, exactly as the original did.
 *
 * Uses STANDARD thickness by default for card-level glass.
 */
@Composable
fun Modifier.ttGlassCard(
    tint: Color? = null,
    cornerRadius: Dp = ttRadius.surface,
    thickness: ttGlass.GlassThickness = ttGlass.GlassThickness.STANDARD,
): Modifier = ttGlassSurface(shape = RoundedCornerShape(cornerRadius), tint = tint, thickness = thickness)

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
    testTag: @Composable (Option) -> String? = { null },
    // `@Composable`, not a plain lambda. Every label in this app comes from a string
    // resource, and `stringResource` is `@Composable` - so a non-composable `title` makes
    // the control unusable for its actual purpose, and the only way out is for a caller to
    // pre-resolve every label into a `List<String>` and lose the type it was given. Kotlin
    // still accepts a plain lambda literal here, so this costs existing callers nothing.
    title: @Composable (Option) -> String,
) {
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<Option, IntRect>() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val selectedBounds = positions[selection]

    // Lens animation — mirrors the bottom nav's moving pill. The four values come from
    // [ttLensPlacement], which is the one copy of this spring and this arithmetic; the
    // segmented picker, the flow picker, and the accent palette picker all travel the same
    // way and were inline in two of them before this existed.
    val (lensX, lensY, lensWidth, lensHeight, velocityX) = ttLensPlacement(positions, selection, density)

    val lensShape = RoundedCornerShape(ttRadius.control)

    Box(
        modifier = modifier
            // Rule 1, in code: the slab's own inset is *inside* the material, and the
            // segment gaps are inside the slab. Nothing here adds space outside the glass,
            // because a caller that did would get a bar the same size with worse hit
            // targets.
            .ttGlassSurface(
                shape = lensShape,
                tint = tint,
                thickness = ttGlass.GlassThickness.STANDARD,
                borderWidth = ttGlass.hairlineWidth,
            )
            .padding(ttSpacing.hairlineInset)
            .onGloballyPositioned { containerCoordinates = it },
    ) {
        // Segments (bottom layer)
        Row(
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (option in options) {
                val optionTestTag = testTag(option)
                Segment(
                    label = title(option),
                    isSelected = option == selection,
                    onClick = { onSelect(option) },
                    onPositioned = { coordinates ->
                        val parent = containerCoordinates
                        if (parent != null) {
                            val position = parent.localPositionOf(coordinates, Offset.Zero)
                            positions[option] = IntRect(
                                left = position.x.roundToInt(),
                                top = position.y.roundToInt(),
                                right = position.x.roundToInt() + coordinates.size.width,
                                bottom = position.y.roundToInt() + coordinates.size.height,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).let { if (optionTestTag != null) it.testTag(optionTestTag) else it },
                )
            }
        }

        // Clear glass selection lens (top layer) — like the tab bar's selection lens.
        // No backdrop, no translucent accent fill.
        if (selectedBounds != null) {
            Box(
                modifier = Modifier
                    .size(lensWidth, lensHeight)
                    .offset(x = lensX, y = lensY)
                    .ttGlassLens(lensShape, velocityX = velocityX),
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
    val (lensX, lensY, lensWidth, lensHeight, velocityX) = ttLensPlacement(positions, selection, density)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(
                shape = RoundedCornerShape(ttRadius.surface),
                tint = tint,
                thickness = ttGlass.GlassThickness.STANDARD,
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
                    .ttGlassLens(CircleShape, velocityX = velocityX),
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            )
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
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .onGloballyPositioned(onPositioned)
            .then(
                Modifier.padding(
                    vertical = if (compact) ttSpacing.xs else ttSpacing.controlPaddingV,
                    horizontal = if (compact) ttSpacing.sm else ttSpacing.controlPaddingH,
                ),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        val content = ttLabel()
        Box(modifier = Modifier.semantics { contentDescription = label }) {
            ttText(
                text = label,
                style = if (isSelected) ttTypography.subheadlineSelected else ttTypography.subheadline,
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
 * `.opacity(isEnabled ? 1 : 0.4)` did the same, and the measurement in `docs/PORT-LOG.md` records
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
    loading: Boolean = false,
) {
    val accent = ttAccent()
    val resolvedTint = if (style.prominent) (style.tint ?: accent) else style.tint
    val content = if (style.prominent) ttOnAccent else ttLabel()

    // A loading button does not answer a tap. Not `enabled = false`: that carries
    // [ttGlass.DISABLED_ALPHA] with it, and a busy control is not a dimmed control — dimming
    // it says "you cannot use this", when what it means is "this is working, do not press it
    // again". A second tap on a sign-in button is the failure this exists to prevent.
    val interactive = enabled && !loading

    // The press scale [ttGlass.PRESSED_SCALE] exists for, and which no component applied
    // until now. It was a constant with a KDoc explaining it and zero call sites, while
    // `PrimaryButton` carried a private `0.97f` of its own — the same drift this file's
    // other constants were written to prevent.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && interactive) ttGlass.PRESSED_SCALE else 1f,
        animationSpec = ttGlass.pressSpring,
        label = "ttGlassButton press",
    )

    // Visual button with glass surface, internal padding
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
            .scale(scale)
            .ttGlassSurface(
                shape = CircleShape,
                tint = resolvedTint,
                thickness = if (style.prominent) ttGlass.GlassThickness.THICK else ttGlass.GlassThickness.STANDARD,
            )
            // Rule 1 again, and the reason the padding is *inside* the glass: applied after
            // the material it would enlarge the row without enlarging the control.
            .padding(
                vertical = ttSpacing.buttonPaddingV,
                horizontal = ttSpacing.buttonPaddingH,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = interactive,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
            // Both children stay composed and cross-fade, so the button does not change width as
            // the label becomes a spinner. Removing the label from the tree instead would make the
            // button collapse to the spinner and then spring back — a layout jump on every press.
            Box(
                modifier = Modifier
                    .alpha(if (loading) 0f else 1f)
                    .semantics { contentDescription = label },
            ) {
                ttText(
                    text = label,
                    style = ttTypography.button,
                    color = content,
                    textAlign = TextAlign.Center,
                )
            }
            if (loading) {
                Box(modifier = Modifier.alpha(1f - if (loading) 0f else 1f)) {
                    ttSpinner(color = content)
}
        }
    }
}

/**
 * A circular glass add action for a screen-level create affordance.
 *
 * **Unused.** It was written for a floating create action no screen ended up needing, and it is
 * the one component in this package with zero call sites and zero tests. Kept for now because the
 * affordance is plausible; see `docs/PORT-LOG.md` Phase 19. If the next screen that wants a floating
 * create does not reach for it, it should go rather than sit here reading as available.
 */
@Composable
fun ttGlassAddButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ttSpacing.floatingActionSize)
            .ttGlassSurface(shape = CircleShape, borderWidth = ttGlass.hairlineWidth)
            // Block touches from passing through to underlying content
            .background(Color.Transparent)
            .pointerInput(Unit) { /* consume touches */ }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = "+",
            style = ttTypography.title,
            color = ttLabel(),
        )
    }
}

/**
 * A compact circular glass action with an accessible label and a design-system glyph.
 *
 * [enabled] dims the control with [ttGlass.DISABLED_ALPHA] and makes it inert, the same
 * pair [ttGlassButton] applies: a disabled control is supposed to look less available, and
 * a button that looks inert while still firing is worse than one that looks live and
 * briefly does nothing. It lives here rather than at the call site because "disabled" is a
 * behaviour of the whole control — the alpha and the dead click travel together, and a
 * caller that dimmed without gating (or gated without dimming) would be half of the state.
 */
@Composable
fun ttGlassIconButton(
    image: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = ttLabel(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(ttSpacing.iconButtonSize)
            .alpha(if (enabled) 1f else ttGlass.DISABLED_ALPHA)
            .ttGlassSurface(shape = CircleShape, borderWidth = ttGlass.hairlineWidth)
            // Block touches from passing through to underlying content
            .background(Color.Transparent)
            .pointerInput(Unit) { /* consume touches */ }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        ttIcon(
            image = image,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ttIconSize.tab),
        )
    }
}
