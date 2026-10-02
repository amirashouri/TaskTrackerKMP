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
 * The app's mark: a checkmark on a rounded accent tile.
 *
 * Shared by the splash and by sign-in so the hand-off between them keeps the mark itself
 * perfectly still while everything around it cross-fades. Two copies of this view would
 * drift — a radius nudged, a gradient angle changed — and the drift would be visible
 * precisely where it is least welcome, in a 0.35s transition the user is watching.
 *
 * ## The glyph is drawn, not imported
 *
 * The original used the SF Symbol `checklist.checked`. Compose Multiplatform has no
 * equivalent, and the two available routes are both worse: `material-icons-extended` is a
 * Material dependency this design system has deliberately refused throughout
 * (`Colors.kt` explains why), and an SF Symbol cannot be shipped at all.
 *
 * So the checkmark is a [Path] of two strokes. That is more code than a vector asset and it
 * buys three things worth having: no new dependency, a glyph that takes its colour from the
 * same token as everything else, and a mark that is identical on all three targets instead
 * of being whatever the platform happened to substitute.
 *
 * ## The glyph's size is fixed, and that is the point
 *
 * Every other size in this design system is in `sp` and grows with the user's text-size
 * setting. This one cannot. It is a picture inside a fixed [size] tile: a glyph that grew
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
        AppMarkGlyph(size = size)
    }
}

/**
 * The checkmark itself, sized and weighted as a fraction of the tile.
 *
 * Split out from [AppMark] so the geometry is described once and cannot drift between a
 * preview and the real thing. The stroke is round-capped and round-joined because the
 * original's SF Symbol has rounded terminals, and butt caps on a two-segment path read as
 * a different logo at 68dp.
 */
@Composable
private fun AppMarkGlyph(size: Dp) {
    // Plain `Dp` arithmetic, and no density here at all: both values are already in `dp`
    // and `Canvas` resolves them to pixels itself. The tempting version of this wraps the
    // two in `with(LocalDensity)` to reach `toDp`, which is both unnecessary and the place
    // a fixed size would quietly become a font-scaled one.
    val glyph = size * AppMarkDefaults.glyphFraction
    val stroke = size * AppMarkDefaults.strokeFraction

    Canvas(modifier = Modifier.size(glyph)) {
        drawCheckmark(strokeWidth = stroke.toPx())
    }
}

/**
 * The checkmark path, in a unit box.
 *
 * A long arm and a short one meeting at a vertex. The proportions are the original glyph's
 * and not arbitrary: the long arm is more than twice the short, which is what stops it
 * reading as a tick, and the vertex sits below the centre so the mark looks seated rather
 * than floating high in the tile.
 */
private fun DrawScope.drawCheckmark(strokeWidth: Float) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * AppMarkDefaults.SHORT_ARM_X, h * AppMarkDefaults.VERTEX_Y)
        lineTo(w * AppMarkDefaults.VERTEX_X, h * AppMarkDefaults.VERTEX_Y)
        lineTo(w * AppMarkDefaults.TIP_X, h * AppMarkDefaults.TIP_Y)
    }
    drawPath(
        path = path,
        color = ttOnAccent,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/** The mark's geometry, named so a preview and the real component cannot disagree. */
object AppMarkDefaults {

    /** 68dp, the original's fixed frame. */
    val size: Dp = 68.dp

    /** Glyph size as a fraction of [size]: 30/68, the original's `size: 30`. */
    const val glyphFraction: Float = 30f / 68f

    /** Stroke weight as a fraction of [size], tuned so both arms read at 68dp. */
    const val strokeFraction: Float = 0.085f

    /** The gradient's second stop: `accentColor.opacity(0.7)`. */
    const val gradientFloorAlpha: Float = 0.7f

    /** The shadow's alpha: `accentColor.opacity(0.3)`. */
    const val shadowAlpha: Float = 0.3f

    /** The shadow's drop: the original's `y: 6`. */
    val shadowY: Dp = 6.dp

    // Checkmark geometry, as fractions of the glyph box.
    const val SHORT_ARM_X: Float = 0.10f
    const val VERTEX_X: Float = 0.38f
    const val VERTEX_Y: Float = 0.80f
    const val TIP_X: Float = 0.90f
    const val TIP_Y: Float = 0.18f
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

    Box(
        modifier = modifier
            .fillMaxSize()
            // Decorative: no text, no control, nothing to announce or to focus.
            .clearAndSetSemantics { }
            .drawBehind {
                drawRect(canvas)
                drawAmbientCircles(accent = accent, canvas = canvas)
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
 * original's circles hang off the corners: an offset of `-0.25w` from a centred circle is
 * the same point as a centre of `0.25w`, and the second circle's `0.45w` past the far edge
 * is `1.45w`. Both circles are therefore drawn partly off-canvas, which is what makes them
 * read as ambient light rather than as two discs.
 */
private fun DrawScope.drawAmbientCircles(accent: Color, canvas: Color) {
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
                Color.Blue.copy(alpha = AmbientBackdropDefaults.SECONDARY_ALPHA),
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

/** The backdrop's geometry, so a preview cannot quietly draw a different one. */
object AmbientBackdropDefaults {

    /** First circle: `0.9 × width` across, at `(-0.25w, -0.15h)`, accent at 18%. */
    const val PRIMARY_DIAMETER_FRACTION: Float = 0.9f
    const val PRIMARY_RADIUS_FRACTION: Float = PRIMARY_DIAMETER_FRACTION / 2f
    const val PRIMARY_CENTRE_X: Float = 0.25f
    const val PRIMARY_CENTRE_Y: Float = 0.35f
    const val PRIMARY_ALPHA: Float = 0.18f

    /** Second circle: `0.7 × width` across, at `(0.45w, 0.55h)`, blue at 10%. */
    const val SECONDARY_DIAMETER_FRACTION: Float = 0.7f
    const val SECONDARY_RADIUS_FRACTION: Float = SECONDARY_DIAMETER_FRACTION / 2f
    const val SECONDARY_CENTRE_X: Float = 1.45f
    const val SECONDARY_CENTRE_Y: Float = 1.55f
    const val SECONDARY_ALPHA: Float = 0.10f
}
