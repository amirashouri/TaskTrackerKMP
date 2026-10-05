package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp

/**
 * How much of the glass material the app is allowed to show, as a fraction in `0f..1f`.
 *
 * Defaults to `1f`, so every screen that does not provide this — every preview, every test
 * host that has not asked for it — paints exactly the glass it painted before this existed.
 * That is deliberate: the numbers on the [ttGlass] tokens are all *measured* numbers (see
 * `GlassFillContrastTest` and `GlassFillRampTest`), and they were measured at full intensity.
 * Making the default anything else would silently invalidate them.
 *
 * Provided by the shell from the user's own setting, so it lives in the composition rather
 * than in a global: the value is read at the point the alphas are *resolved*, which is
 * inside [ttGlassSurface] and the handful of components that stack a lens over something
 * solid.
 */
val LocalGlassIntensity = compositionLocalOf { 1f }

/**
 * The fill's alpha at [intensity], cross-fading from opaque to the measured glass fill.
 *
 * This is the whole idea in one line, and the direction is the part worth stating twice
 * because it is counter-intuitive: **more glass means *less* opaque**, so the fill's alpha
 * *falls* as the slider rises. At `0f` the alpha is `1f` and a glass surface is a solid
 * fill — which is what the platform's own Reduce Transparency switch does, and which is why
 * `0f` is modelled as opaque rather than as "no glass at all". A surface with no fill is
 * not reduced transparency, it is a hole in the page.
 *
 * At `1f` it is exactly [ttGlass.FILL_ALPHA], so the top of the slider is the arrangement
 * every existing measurement was taken at.
 */
internal fun glassFillAlpha(intensity: Float): Float =
    lerp(1f, ttGlass.FILL_ALPHA, intensity.coerceIn(0f, 1f))

/**
 * The lens wash over something solid, at [intensity].
 *
 * Fades to nothing rather than to a lower number, because the lens only exists to lighten a
 * translucent fill; once the fill is opaque there is nothing left for it to do. `0.08` at
 * full intensity, unchanged.
 *
 * Public, and read by the two components that stack a lens over a `base` and therefore have
 * to knock the *same* amount out of what sits under it to land on the intended composite.
 * Those are `BottomSheetScaffold`'s sheet and the project switcher button's capsule, and
 * they are separate files in two different modules — which is why this is an accessor rather
 * than a constant every caller recomputes.
 */
@Composable
fun ttGlassLensAlpha(): Float = ttGlass.LENS_ALPHA * LocalGlassIntensity.current.coerceIn(0f, 1f)

/**
 * The rim at [intensity]: [ttGlass.HAIRLINE_ALPHA] of white at full intensity in dark,
 * [ttGlass.LIGHT_HAIRLINE_ALPHA] of black in light, nothing at zero.
 *
 * The hairline is not decoration and it is not a border — it is what makes a surface read as
 * *raised* (see [ttGlassSurface]). In dark that job is done by being lighter than what
 * surrounds it, which white is. In light there is nothing lighter worth having: the glass
 * fill already composites to about #C2CBD7, so a white rim over it measures 1.07:1 and even
 * a fully opaque white line only 1.64:1 (both rendered, `GlassSurfacePixelTest`). The light
 * rim therefore comes from the other direction — a shadow, darker than its fill — and reads
 * as the same raised edge from underneath. Both branches fade with the slider because on an
 * opaque fill the job is already done by the fill's own edge, and a rim kept at `0f` would
 * draw an edge over a surface that has stopped being a lens.
 */
@Composable
internal fun ttGlassHairline(): Color {
    val scaled = LocalGlassIntensity.current.coerceIn(0f, 1f)
    return if (ttIsDarkTheme()) {
        Color.White.copy(alpha = ttGlass.HAIRLINE_ALPHA * scaled)
    } else {
        Color.Black.copy(alpha = ttGlass.LIGHT_HAIRLINE_ALPHA * scaled)
    }
}

/**
 * How far the fill's hue travels toward the canvas by the bottom edge, at [intensity].
 *
 * Scales to zero rather than to a lower fraction because the ramp models *light falling
 * across a translucent surface*. A Reduce Transparency fill is one flat colour, so a ramp
 * over it is a gradient over nothing — and [ttGlass.FILL_FADE]'s own doc records what a
 * gradient that keeps running at a stop it has reached does to the bottom edge of every card.
 */
@Composable
internal fun ttGlassFillFade(): Float = ttGlass.FILL_FADE * LocalGlassIntensity.current.coerceIn(0f, 1f)