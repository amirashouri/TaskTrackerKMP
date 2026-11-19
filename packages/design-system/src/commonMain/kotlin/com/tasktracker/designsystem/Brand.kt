package com.tasktracker.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's mark: the Board composition — three kanban columns, their card dashes and a
 * white checkmark — on a rounded accent tile.
 *
 * Shared by the splash and by sign-in so the hand-off between them keeps the mark itself
 * perfectly still while everything around it cross-fades. Two copies of this view would
 * drift — a radius nudged, a gradient angle changed — and the drift would be visible
 * precisely where it is least welcome, in a 0.35s transition the user is watching.
 *
 * ## The geometry is the launcher's
 *
 * Every fraction in [AppMarkDefaults] is `tools/generate_app_icon.py`'s canvas fraction
 * verbatim: that canvas is the launcher's tile, so this tile is the same picture at 68dp.
 * Splash and sign-in show one mark, not two that merely resemble each other. Only the finish differs — the launcher has the full three-stop ramp and its
 * sheen, while this tile keeps the accent-to-accent fade and takes its ink from
 * `ttOnAccent`, the pairing already contrast-tested against the accent in every palette.
 *
 * ## The glyph is drawn, not imported
 *
 * The original used the SF Symbol `checklist.checked`. Compose Multiplatform has no
 * equivalent, and the two available routes are both worse: `material-icons-extended` is a
 * Material dependency this design system has deliberately refused throughout
 * (`Colors.kt` explains why), and an SF Symbol cannot be shipped at all.
 *
 * So the columns, dashes and checkmark are paths and round-capped lines. That is more code
 * than a vector asset and it buys three things worth having: no new dependency, an ink that
 * comes from the same tokens as everything else, and a mark identical on all three targets
 * instead of being whatever the platform happened to substitute.
 *
 * ## The tile's size is fixed, and that is the point
 *
 * Every other size in this design system is in `sp` and grows with the user's text-size
 * setting. This one cannot. It is a picture inside a fixed [size] tile: a mark that grew
 * with the text setting would overflow a 68dp tile at 200% and clip the very corners the
 * rounded tile exists to show. The original gets this for free — `.system(size: 30)` is a
 * fixed point size, not a text style — and here it has to be said out loud, because the
 * obvious spelling of a "30pt" in this codebase is `30.sp`, and that is the bug.
 */
@Composable
fun AppMark(
    modifier: Modifier = Modifier,
    size: Dp = AppMarkDefaults.size,
) {
    val accent = ttAccent()
    Box(
        modifier = modifier
            .size(size)
            // `accentColor.opacity(0.3)`, `radius: 12`, `y: 6`. Compose's shadow takes an
            // elevation rather than a radius and an offset, so 6dp is the closer of the
            // three: it is the `y`, and a larger value would darken the tile's underside
            // more than a 12pt blur radius over a 6pt drop does.
            .shadow(
                elevation = AppMarkDefaults.shadowY,
                shape = RoundedCornerShape(ttRadius.mark),
                ambientColor = accent.copy(alpha = AppMarkDefaults.shadowAlpha),
                spotColor = accent.copy(alpha = AppMarkDefaults.shadowAlpha),
            )
            .background(
                // No explicit start/end: the default is the whole drawing area, which is
                // exactly the original's `.topLeading` to `.bottomTrailing`. Naming them
                // would need the measured size, which `background` does not have.
                brush = Brush.linearGradient(
                    listOf(accent, accent.copy(alpha = AppMarkDefaults.gradientFloorAlpha)),
                ),
                shape = RoundedCornerShape(ttRadius.mark),
            ),
        contentAlignment = Alignment.Center,
    ) {
        AppMarkGlyph()
    }
}

/**
 * The Board composition, in fractions of the tile.
 *
 * Split out from [AppMark] so the geometry is described once and cannot drift between a
 * preview and the real component. Columns and cards are round-capped lines, so no arcs are
 * needed to fake a capsule; the check is a two-segment [Path] with a round join. Ink is read
 * once before the draw scope — the same `ttOnAccent` the tile's contrast pairing assumes.
 */
@Composable
private fun AppMarkGlyph() {
    val ink = ttOnAccent
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val unit = size.minDimension

        // Columns first: the cards sit on them, and the check sits over both.
        for (cx in AppMarkDefaults.COLUMN_CENTRES) {
            drawLine(
                color = ink.copy(alpha = AppMarkDefaults.COLUMN_ALPHA),
                start = Offset(cx * w, (0.5f - AppMarkDefaults.COLUMN_HALF_STRAIGHT) * h),
                end = Offset(cx * w, (0.5f + AppMarkDefaults.COLUMN_HALF_STRAIGHT) * h),
                strokeWidth = AppMarkDefaults.COLUMN_STROKE * unit,
                cap = StrokeCap.Round,
            )
        }
        for (cy in AppMarkDefaults.CARD_CENTRES) {
            for (cx in AppMarkDefaults.COLUMN_CENTRES) {
                drawLine(
                    color = ink.copy(alpha = AppMarkDefaults.CARD_ALPHA),
                    start = Offset((cx - AppMarkDefaults.CARD_HALF_STRAIGHT) * w, cy * h),
                    end = Offset((cx + AppMarkDefaults.CARD_HALF_STRAIGHT) * w, cy * h),
                    strokeWidth = AppMarkDefaults.CARD_STROKE * unit,
                    cap = StrokeCap.Round,
                )
            }
        }
        // The same call [AppMarkCheck] makes, so the mark and a badge using it cannot show
        // two different checkmarks. `unit` because the tile is square: the fractions are of
        // its width and height alike.
        drawAppMarkCheck(tint = ink, origin = Offset.Zero, scale = unit)
    }
}

/**
 * The mark's checkmark: two round-capped segments from the same tile fractions the launcher's
 * icon uses, drawn at [scale] units per fraction with its first point at [origin].
 *
 * A stroke rather than a filled outline, because that is what the mark is: the original draws
 * the check as a path with `StrokeCap.Round` and `StrokeJoin.Round`, and reproducing those two
 * round terminals and the round joint as a filled contour would be a second drawing that could
 * drift from this one. Every caller gets this one shape.
 *
 * [scale] is a single number, not a width and a height, for the same reason: the check lives
 * on a square tile, and letting the two axes scale independently would allow a squashed
 * checkmark that no constant in [AppMarkDefaults] describes.
 */
internal fun DrawScope.drawAppMarkCheck(
    tint: Color,
    origin: Offset,
    scale: Float,
) {
    val path = Path().apply {
        moveTo(
            origin.x + AppMarkDefaults.SHORT_ARM_X * scale,
            origin.y + AppMarkDefaults.VERTEX_Y * scale,
        )
        lineTo(
            origin.x + AppMarkDefaults.VERTEX_X * scale,
            origin.y + AppMarkDefaults.VERTEX_Y * scale,
        )
        lineTo(
            origin.x + AppMarkDefaults.TIP_X * scale,
            origin.y + AppMarkDefaults.TIP_Y * scale,
        )
    }
    drawPath(
        path = path,
        color = tint,
        style = Stroke(
            width = AppMarkDefaults.CHECK_STROKE * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/** The geometry, named so a preview and the real component cannot disagree. */
object AppMarkDefaults {

    /** 68dp, the original's fixed frame. */
    val size: Dp = 68.dp

    /** The gradient's second stop: `accentColor.opacity(0.7)`. */
    const val gradientFloorAlpha: Float = 0.7f

    /** The shadow's alpha: `accentColor.opacity(0.3)`. */
    const val shadowAlpha: Float = 0.3f

    /** The shadow's drop: the original's `y: 6`. */
    val shadowY: Dp = 6.dp

    // The Board, as fractions of this tile. These are tools/generate_app_icon.py's canvas
    // fractions verbatim (its `_ship(u) = (u - 0.08) / 0.84` applied to the concept): the
    // launcher's canvas is its tile, so these draw the same picture here at 68dp. Floats
    // carry eight decimals because the card/check clearance is sub-pixel even at 1024px —
    // rounding to four made two shapes touch that the launcher keeps apart.
    val COLUMN_CENTRES: List<Float> =
        listOf(0.27380952f, 0.5f, 0.72619048f)
    const val COLUMN_ALPHA: Float = 0.15f
    const val COLUMN_STROKE: Float = 0.18571429f
    const val COLUMN_HALF_STRAIGHT: Float = 0.18690476f

    val CARD_CENTRES: List<Float> = listOf(0.40476191f, 0.65476191f)
    const val CARD_ALPHA: Float = 0.24f
    const val CARD_STROKE: Float = 0.02857143f
    const val CARD_HALF_STRAIGHT: Float = 0.02441406f

    // The check's two segments, in the same tile fractions. Tip above vertex, as before.
    const val SHORT_ARM_X: Float = 0.35714286f
    const val VERTEX_X: Float = 0.46190476f
    const val VERTEX_Y: Float = 0.54761905f
    const val TIP_X: Float = 0.65476191f
    const val TIP_Y: Float = 0.36904762f
    const val CHECK_STROKE: Float = 0.09047619f
}

/**
 * The two drifting circles behind the splash and the sign-in screen.
 *
 * ## Why it exists at all
 *
 * It is decoration, and the reason it is in the design system rather than in one screen is
 * that it is load-bearing for the glass. A material samples what is drawn behind it: a
 * glass card over a flat colour reads as grey, which is what made the first version of the
 * sign-in card look like a form in a box. These circles give the glass something to
 * sample, and they are part of why [ttGlassCard] can work at all.
 *
 * ## Shared, so the background does not move
 *
 * The splash and sign-in both draw this, and the transition between them is a 0.35s
 * cross-fade of the *content* over an unchanged background. Two copies of this composable,
 * however identical, would each draw their circles from their own pass over the available
 * size, and any difference in the measured area would slide the backdrop under the
 * cross-fade — the one thing that would make the hand-off look like two screens rather
 * than one.
 *
 * ## It is hidden from accessibility, and it is the only thing here that is
 *
 * It carries no text and no control, so an accessibility reader that announced it would be
 * describing the wallpaper. This is the only `clearAndSetSemantics` in the design system:
 * the rule is that *decoration* is hidden, not that accessibility is switched off, and
 * the mark, the glass and every other component all carry meaning.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    // Read before the lambda: `drawBehind` takes a plain `DrawScope` block, so a token read
    // inside it does not compile. Reading here also means the answer is read once per
    // composition rather than once per draw, which matters for a node that repaints on
    // every frame of a cross-fade.
    val canvas = ttCanvas()
    val accent = ttAccent()
    val secondary = accentSecondaryGlow(accent)

    Box(
        modifier = modifier
            .fillMaxSize()
            // Decorative: no text, no control, nothing to announce or to focus.
            .clearAndSetSemantics { }
            .drawBehind {
                drawRect(canvas)
                drawAmbientCircles(accent = accent, secondary = secondary, canvas = canvas)
            },
    )
}

/**
 * The two circles, as soft radial gradients.
 *
 * Drawn with gradients rather than as two child composables with `Modifier.blur`. A
 * `Modifier.blur` is a render effect: it is more expensive, it is skipped entirely on some
 * configurations, and its falloff is a platform implementation detail that will not be the
 * same on iOS and Android. A gradient from the colour to transparent at the same radius is
 * the same soft edge for a fraction of the cost, and the stops are the same numbers on
 * every target — which is the property that lets a preview on one platform be believed
 * about another.
 *
 * The centres are expressed as `1.0 +` offsets rather than negative ones because the
 * circles hang off the corners: an offset of `-0.25w` from a centred circle is the same
 * point as a centre of `0.25w`, and the second circle sits just past the bottom-right
 * corner. Both are therefore drawn partly off-canvas, which is what makes them read as
 * ambient light rather than as two discs.
 *
 * ## The second circle used to paint nothing
 *
 * Its centre was `(1.45w, 1.55h)` with a `0.35w` radius, so its nearest edge was `1.10w`
 * from the left — the whole circle was outside the canvas. That is why the hardcoded blue
 * could survive six palettes: no pixel ever showed it. Pulling the centre to `(1.05w,
 * 1.10h)` puts the falloff across the bottom-right corner without changing the diameter or
 * the 10% alpha, and `AmbientBackdropPaletteTest` now reads the difference there.
 */
private fun DrawScope.drawAmbientCircles(accent: Color, secondary: Color, canvas: Color) {
    val w = size.width
    val h = size.height

    // The outer stop is the canvas rather than transparent, and that is deliberate. These
    // circles are translucent, so what they read against decides what they look like: a
    // gradient fading to transparent *black* would darken the canvas towards grey on the
    // way out, which is a different effect from the original's, where the blur simply
    // stopped. Fading to the canvas colour makes the edge land exactly back on the
    // background, so the circle is an addition to the canvas and not a tint on it.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = AmbientBackdropDefaults.PRIMARY_ALPHA),
                canvas,
            ),
            center = Offset(
                w * AmbientBackdropDefaults.PRIMARY_CENTRE_X,
                h * AmbientBackdropDefaults.PRIMARY_CENTRE_Y,
            ),
            radius = w * AmbientBackdropDefaults.PRIMARY_RADIUS_FRACTION,
        ),
        radius = w * AmbientBackdropDefaults.PRIMARY_RADIUS_FRACTION,
        center = Offset(
            w * AmbientBackdropDefaults.PRIMARY_CENTRE_X,
            h * AmbientBackdropDefaults.PRIMARY_CENTRE_Y,
        ),
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondary.copy(alpha = AmbientBackdropDefaults.SECONDARY_ALPHA),
                canvas,
            ),
            center = Offset(
                w * AmbientBackdropDefaults.SECONDARY_CENTRE_X,
                h * AmbientBackdropDefaults.SECONDARY_CENTRE_Y,
            ),
            radius = w * AmbientBackdropDefaults.SECONDARY_RADIUS_FRACTION,
        ),
        radius = w * AmbientBackdropDefaults.SECONDARY_RADIUS_FRACTION,
        center = Offset(
            w * AmbientBackdropDefaults.SECONDARY_CENTRE_X,
            h * AmbientBackdropDefaults.SECONDARY_CENTRE_Y,
        ),
    )
}

/**
 * The second glow: the accent's hue nudged cool rather than a fixed blue.
 *
 * A palette has to change the light in the room, not only the controls in it. The shift is
 * +25° in HSV with the accent's own saturation and value, so Classic (213°) lands on 238° —
 * within two degrees of the `Color.Blue` this replaces — while Sunset lands on orange and
 * Violet on magenta. No branch and no second palette table: the accent is the only input.
 *
 * HSV rather than HSL because the shift is a rotation of hue at constant chroma, which is
 * exactly what HSV's saturation/value pair holds; re-deriving lightness from an HSL triple
 * would move the glow's brightness between palettes for no design reason.
 */
private fun accentSecondaryGlow(accent: Color): Color {
    val high = maxOf(accent.red, maxOf(accent.green, accent.blue))
    val low = minOf(accent.red, minOf(accent.green, accent.blue))
    val delta = high - low
    val hue = if (delta == 0f) {
        0f
    } else {
        when (high) {
            accent.red -> 60f * (((accent.green - accent.blue) / delta) % 6f)
            accent.green -> 60f * ((accent.blue - accent.red) / delta + 2f)
            else -> 60f * ((accent.red - accent.green) / delta + 4f)
        }.let { if (it < 0f) it + 360f else it }
    }
    val saturation = if (high == 0f) 0f else delta / high
    val shifted = (hue + AmbientBackdropDefaults.SECONDARY_HUE_SHIFT) % 360f
    return Color.hsv(shifted, saturation, high)
}

/** The backdrop's geometry, so a preview cannot quietly draw a different one. */
object AmbientBackdropDefaults {

    /** First circle: `0.9 × width` across, at `(-0.25w, -0.15h)`, accent at 18%. */
    const val PRIMARY_DIAMETER_FRACTION: Float = 0.9f
    const val PRIMARY_RADIUS_FRACTION: Float = PRIMARY_DIAMETER_FRACTION / 2f
    const val PRIMARY_CENTRE_X: Float = 0.25f
    const val PRIMARY_CENTRE_Y: Float = 0.35f
    const val PRIMARY_ALPHA: Float = 0.18f

    /**
     * Second circle: `0.7 × width` across, just past the bottom-right corner, at 10%.
     *
     * The centre used to be `(1.45w, 1.55h)`, which with a `0.35w` radius put the entire
     * circle outside the canvas; see [drawAmbientCircles].
     */
    const val SECONDARY_DIAMETER_FRACTION: Float = 0.7f
    const val SECONDARY_RADIUS_FRACTION: Float = SECONDARY_DIAMETER_FRACTION / 2f
    const val SECONDARY_CENTRE_X: Float = 1.05f
    const val SECONDARY_CENTRE_Y: Float = 1.10f
    const val SECONDARY_ALPHA: Float = 0.10f

    /** Degrees the accent's hue is rotated toward blue for the second glow. */
    const val SECONDARY_HUE_SHIFT: Float = 25f
}
