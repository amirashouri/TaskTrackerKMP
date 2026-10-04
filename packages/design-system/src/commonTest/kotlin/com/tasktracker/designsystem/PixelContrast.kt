package com.tasktracker.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * WCAG's contrast arithmetic, written once for the whole module.
 *
 * ## Why this file exists, and why it says eight rather than four
 *
 * 23.4 introduced a fourth copy of the relative-luminance formula and noted that a fifth and
 * sixth already existed. Counting them properly found **eight** files carrying it:
 * `AccentContrastTest`, `AvatarContrastTest`, `BannerContrastTest`, `BodyLabelContrastTest`,
 * `GlassFillContrastTest`, `GlassSurfacePixelTest`, `PinnedAppearanceTest` and
 * `TabLensContrastTest`.
 *
 * The prediction 23.4 made about them was right, and the prediction about *how bad* it was
 * was not. Two of the eight — `GlassFillContrastTest` and `TabLensContrastTest` — used the
 * older **`0.04045`** sRGB breakpoint while the other six used **`0.03928`**. So the suite
 * was not one implementation copied eight times; it was **two implementations disagreeing**,
 * and a token sitting on the 0.03928–0.04045 band would have measured differently depending
 * on which file asked. Both values are in circulation in accessibility references, which is
 * exactly how that happens. `0.03928` is kept here, on the majority's side.
 *
 * The band is narrow enough that changing it moved no test, and that is recorded as the
 * reason the choice is not load-bearing today rather than as a reason it does not matter.
 * What matters is that the next file cannot pick the other one.
 *
 * ## The names are the old ones on purpose
 *
 * Everything here is a **top-level internal function with the name the call sites already
 * used** — `contrastRatio`, `relativeLuminance`, `over`, `Color.hex()`, `Double.ratioText()`.
 * That makes folding the eight copies in a pure deletion: no call site moves, so the diff is
 * eight deleted helper blocks and one new file, and there is no opportunity to
 * accidentally change an argument at the same time as the implementation underneath it.
 *
 * ## `over` had three shapes and now has one
 *
 * The copies disagreed on the parameter order as well as the breakpoint:
 * `over(foreground, alpha, background)` in three files, `over(top, bottom, alpha)` in
 * `GlassFillContrastTest`, and a two-argument `over(top, bottom)` that read the alpha off
 * `top` in `PinnedAppearanceTest`. Three shapes for one operation, each reading naturally at
 * its own call site and none at the others. There is one signature here —
 * `over(top, alpha, bottom)` — plus a two-argument overload for the "the top carries its own
 * alpha" case, because that one is genuinely a different operation rather than a different
 * spelling.
 *
 * ## What is deliberately *not* here
 *
 * A composite is not a measurement. `over` builds the colour a translucent token *would* make
 * against a known background, which is legitimate for **building the frame to render** and
 * for **cross-checking a rendered reading**. It is not an answer to "can this be read", and
 * `AGENTS.md` rule 2's reason is that the model is not the compositor: `PLAN.md` Phase 19
 * modelled `ttLabelSecondary` over a glass card at 2.81:1 and the two rasterisers both
 * reported 2.88:1, and modelled the hairline at 1.08:1 against a rendered 1.07:1. The model
 * was *close*, and being close is what made it believable.
 *
 * So anything whose answer is about legibility reads its two colours out of a rendered frame.
 * `strongestPixel` exists for that: the furthest pixel from a background inside a glyph's
 * bounds, because a glyph's edge pixels are antialiased blends and reporting one of those as
 * the ink understates the contrast in the direction that makes a test pass.
 */

// --- WCAG -------------------------------------------------------------------------------------

/** The sRGB linearisation breakpoint, in linear light. */
internal const val SRGB_LINEAR_THRESHOLD: Double = 0.03928
internal const val SRGB_DIVISOR: Double = 12.92
internal const val SRGB_OFFSET: Double = 0.055
internal const val SRGB_SCALE: Double = 1.055
internal const val SRGB_EXPONENT: Double = 2.4

internal const val RED_COEFFICIENT: Double = 0.2126
internal const val GREEN_COEFFICIENT: Double = 0.7152
internal const val BLUE_COEFFICIENT: Double = 0.0722

/** WCAG's 4.5:1, for text below 18sp (or 14sp bold). */
internal const val TEXT_CONTRAST_MINIMUM: Double = 4.5

/** WCAG's 3:1, for large text and for non-text UI boundaries. */
internal const val LARGE_TEXT_CONTRAST_MINIMUM: Double = 3.0

internal fun linearise(channel: Float): Double {
    val value = channel.toDouble()
    return if (value <= SRGB_LINEAR_THRESHOLD) value / SRGB_DIVISOR
    else ((value + SRGB_OFFSET) / SRGB_SCALE).pow(SRGB_EXPONENT)
}

internal fun relativeLuminance(color: Color): Double =
    RED_COEFFICIENT * linearise(color.red) +
        GREEN_COEFFICIENT * linearise(color.green) +
        BLUE_COEFFICIENT * linearise(color.blue)

/** WCAG 2.x contrast: the two relative luminances' ratio, plus 0.05 each. */
internal fun contrastRatio(first: Color, second: Color): Double {
    val lighter = max(relativeLuminance(first), relativeLuminance(second))
    val darker = min(relativeLuminance(first), relativeLuminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

// --- Compositing ------------------------------------------------------------------------------

/**
 * [top] at [alpha] over [bottom], by source-over.
 *
 * See the file docs for what this may and may not be used to answer.
 */
internal fun over(top: Color, alpha: Float, bottom: Color): Color = Color(
    red = top.red * alpha + bottom.red * (1f - alpha),
    green = top.green * alpha + bottom.green * (1f - alpha),
    blue = top.blue * alpha + bottom.blue * (1f - alpha),
    alpha = 1f,
)

/**
 * [top] over [bottom] at [top]'s own alpha.
 *
 * A separate function rather than an arity trick, because an RGBA token is not a colour on
 * its own: `ttLabelSecondary` is stored pre-composited precisely so that which colour shows
 * through depends on what is behind it, and making that a second spelling of `over` is how
 * the parameter orders ended up three different ways in the first place.
 */
internal fun over(top: Color, bottom: Color): Color = over(top, top.alpha, bottom)

// --- Reading a rendered frame -------------------------------------------------------------------

/** The largest absolute difference across the three channels, 0..1. */
internal fun channelDistance(first: Color, second: Color): Float = max(
    max(abs(first.red - second.red), abs(first.green - second.green)),
    abs(first.blue - second.blue),
)

internal fun pixelAt(pixels: PixelMap, x: Float, y: Float): Color = pixels[
    x.toInt().coerceIn(0, pixels.width - 1),
    y.toInt().coerceIn(0, pixels.height - 1),
]

/**
 * The furthest pixel from [background] inside the given box, which is a glyph's core.
 *
 * A glyph's edge pixels are antialiased blends of ink and fill, and at `captionSmall` on a
 * light background the lightest of them can be a third of the way to the background.
 * Sampling one of those and calling it the ink **understates** the contrast — in the
 * direction that makes a test pass, which is the wrong direction. So this takes the extreme,
 * and the caller reads the background from outside the glyph's own bounds so the two are
 * genuinely different pixels.
 */
internal fun strongestPixel(
    pixels: PixelMap,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    background: Color,
): Color {
    var best = background
    var bestDistance = 0f
    for (y in top.coerceAtLeast(0)..bottom.coerceAtMost(pixels.height - 1)) {
        for (x in left.coerceAtLeast(0)..right.coerceAtMost(pixels.width - 1)) {
            val candidate = pixels[x, y]
            val distance = channelDistance(candidate, background)
            if (distance > bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
    }
    return best
}

// --- Formatting --------------------------------------------------------------------------------

/** A ratio as a readable string. `String.format` is JVM-only, so this is written out. */
internal fun Double.ratioText(): String {
    val hundredths = round(this * 100).toInt()
    val whole = hundredths / 100
    val fraction = (hundredths % 100).toString().padStart(2, '0')
    return if (fraction == "00") "$whole" else "$whole.$fraction"
}

internal fun Color.hex(): String = "#" + listOf(red, green, blue).joinToString("") { channel(it) }

private fun channel(value: Float): String =
    round(value * 255f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')