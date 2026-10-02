package com.tasktracker.designsystem

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The four tab glyphs, drawn here rather than imported.
 *
 * ## Why these are hand-drawn instead of a dependency
 *
 * The native iOS shell draws its bar with SwiftUI's `TabView`, which supplies SF Symbols for
 * free. Nothing supplies them here, and the three usual answers are all bad for this project:
 *
 *  - `compose-material3` is excluded by the architecture, because its theming fights the
 *    design system rather than extending it.
 *  - `compose-materialIconsExtended` is on every target and is the obvious port, but it is
 *    several megabytes of vectors for **four** glyphs, it is a Material set rather than the
 *    SF set the native app uses, and it would be the first runtime icon dependency in a build
 *    whose stated approach is to own its own visuals.
 *  - An icon font brings its own licensing question and its own baseline problems.
 *
 * So the four shapes are drawn as [ImageVector]s. They are **not** traced SF Symbols: those
 * are Apple's assets and copying their outlines would be a licensing problem dressed up as
 * diligence. Each is a plain geometric drawing chosen to read the way the SF Symbol the native
 * project names does. [mapping] is that table, and it is the reason these four exist in this
 * order.
 *
 * ## The 24-unit grid
 *
 * Every glyph is drawn on a 24x24 viewport with a 3-unit margin, which is the cap height the SF
 * Symbols these stand in for use. Icons are therefore interchangeable: a fifth tab drawn on
 * this grid lines up with these four without anybody measuring anything, and one drawn off it
 * is visibly a size out rather than subtly a size out.
 *
 * ## One fill rule for the whole file
 *
 * Every glyph here is filled [PathFillType.EvenOdd], including the ones that need no hole.
 *
 * That is not tidiness. An outline is drawn as a shape with a smaller shape cut out of it, and
 * the smaller shape can be cut out either by winding order — the outer contour clockwise and
 * the inner one anticlockwise, so the two cancel — or by the even-odd rule, which only counts
 * how many contours a point is inside. Even-odd is used because it makes "cut a hole" a fact
 * about the path rather than a fact about **the order the parts happened to be added in**. A
 * `Path` that is built by a loop, as the gear is, has no order to rely on, and an outline
 * whose winding is decided by loop index is an outline that silently fills in on the third
 * tooth.
 */
object ttTabIcons {

    /**
     * `square.grid.2x2` — four rounded squares in a 2x2 grid.
     *
     * The native app's Projects glyph. A grid of squares rather than a single square, because
     * Projects is a *list of many*, and a lone square reads as "one project" or as "grid
     * view" depending on who is looking.
     */
    val projects: ImageVector = icon("Projects") {
        val inset = 3.5f
        val gap = 2.0f
        val cell = (GRID - 2f * inset - gap) / 2f
        for (row in 0..1) {
            for (column in 0..1) {
                val left = inset + column * (cell + gap)
                val top = inset + row * (cell + gap)
                solidRoundRect(left, top, left + cell, top + cell, radius = 1.75f)
            }
        }
    }

    /**
     * `rectangle.split.3x1` — a rounded rectangle split into three columns.
     *
     * The native app's Board glyph, and the reason this one is a *frame plus two dividers*
     * rather than three separate columns: three detached rounded rectangles read as three
     * items, which is the Backlog glyph's job. The dividers stop at the frame's inner edge so
     * the outline stays one continuous stroke, which is what makes this a container.
     */
    val board: ImageVector = icon("Board") {
        val left = 3.0f
        val top = 4.5f
        val right = 21.0f
        val bottom = 19.5f
        val stroke = 1.6f
        outlinedRoundRect(left, top, right, bottom, radius = 2.25f, stroke = stroke)

        for (third in 1..2) {
            val x = left + (right - left) * third / 3f
            solidRoundRect(
                left = x - stroke / 2f,
                top = top + stroke,
                right = x + stroke / 2f,
                bottom = bottom - stroke,
                radius = stroke / 2f,
            )
        }
    }

    /**
     * `tray` — an open container with two items in it.
     *
     * The native app's Backlog glyph. The two bars are the part that earns the name: a bare
     * rounded rectangle is a box, and "Backlog" is specifically *stuff waiting in* something.
     * The bars are centred and equal, and only two, because at 22dp a third collapses into the
     * outline it is supposed to be sitting inside.
     */
    val backlog: ImageVector = icon("Backlog") {
        val stroke = 1.6f
        outlinedRoundRect(3.0f, 5.0f, 21.0f, 19.0f, radius = 2.5f, stroke = stroke)

        val bar = 1.7f
        for (y in floatArrayOf(10.4f, 14.0f)) {
            solidRoundRect(7.0f, y, 17.0f, y + bar, radius = bar / 2f)
        }
    }

    /**
     * `gearshape` — an eight-toothed cog with a hole through the middle.
     *
     * The native app's Settings glyph. The teeth are computed rather than listed: eight sets
     * of hand-typed tooth coordinates is eight chances to make a cog with one short tooth, and
     * a cog with one short tooth is the most recognisable way for this glyph to look broken.
     * [gear] has the geometry.
     */
    val settings: ImageVector = icon("Settings") { gear() }

    /**
     * Every glyph, for the test that walks them.
     *
     * Exposed rather than kept private so a test can assert over the whole set instead of
     * restating four names, for the same reason [ttSpacing.ramp] and [ttRadius.all] are: a
     * copy of a collection in a test is a second thing to update, and it would keep passing
     * after the collection stopped being what the test claims it is.
     */
    val all: List<ImageVector> = listOf(projects, board, backlog, settings)

    /**
     * Each glyph against the SF Symbol name the native project uses for that tab.
     *
     * Recorded here, in the design system, rather than in the shell that draws them, because
     * the claim being made is about *these drawings* being deliberate stand-ins. A list in
     * the shell would be a comment in code; this is a table a test can walk, and a fifth tab
     * added without a row here fails that test instead of arriving unremarked.
     */
    val mapping: Map<String, ImageVector> = linkedMapOf(
        "square.grid.2x2" to projects,
        "rectangle.split.3x1" to board,
        "tray" to backlog,
        "gearshape" to settings,
    )
}

/** Icon dimensions, named for the same reason [ttSpacing] and [ttRadius] are. */
@Immutable
object ttIconSize {

    /**
     * 22dp — a tab bar glyph.
     *
     * Below the [ttTabIcons] grid, and below the 24dp Apple's own tab bar draws, for a reason
     * that is about the *pair*: the glyph sits above a caption, so at 24dp the two together
     * stop fitting a 49pt bar without one of them being shrunk. 22dp leaves the caption at its
     * own natural size and keeps the pair inside the bar.
     */
    val tab: Dp = 22.dp

    /** Increasing, for the same reason as [ttSpacing.ramp]. */
    val all: List<Dp> = listOf(tab)
}

/**
 * An [ImageVector] in this app's tint, sized by the caller.
 *
 * [Image] rather than Material's `Icon`, for the reason [ttIcon] exists at all: Material is
 * not a dependency, and tinting a painter is not worth importing a theme framework for.
 *
 * [contentDescription] is nullable, and callers should think before passing it. A glyph
 * *beside* its own text label is announced twice if both carry a description, so a tab bar
 * passes `null` and lets the button's label speak. The parameter is for a glyph that is the
 * only thing conveying something.
 */
@Composable
fun ttIcon(
    image: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = rememberVectorPainter(image),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

// MARK: - Geometry

/**
 * Builds a 24x24 vector from one filled path.
 *
 * [draw] fills the path it is given rather than returning one, so a glyph cannot accidentally
 * be a stroke — which at 22dp is wrong in a way that is invisible in code review and obvious
 * on a device.
 */
private fun icon(name: String, draw: PathBuilder.() -> Unit): ImageVector {
    val nodes = PathBuilder().apply(draw).nodes
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = GRID,
        viewportHeight = GRID,
    ).addPath(
        pathData = nodes,
        pathFillType = PathFillType.EvenOdd,
        // Explicitly opaque, and this is load-bearing rather than cosmetic. `addPath` leaves
        // its fill brush null when it is not given one, and a null brush rasterises to
        // *nothing* — every glyph built without it occupied its full layout box and painted
        // zero pixels, which no geometry assertion can see, because the path data was present
        // and inside the viewport the whole time. `ttIcon` tints with `ColorFilter.tint`,
        // which is `SrcIn`: it keeps the source and discards everything else, so a source with
        // no alpha leaves nothing to keep. The colour here is never seen — it supplies the
        // alpha that the tint filter multiplies through.
        fill = SolidColor(Color.Black),
    ).build()
}

/** The drawing grid: 24 units with a 3-unit margin, as [ttTabIcons]' class docs state. */
private const val GRID = 24f

/**
 * A filled rounded rectangle.
 *
 * Four straight edges joined by four quarter-circle `arcTo`s, which is the shortest honest
 * expression of a rounded rectangle and needs no geometry types. Each corner arc is the SVG
 * form of the same call — a radius, a sweep flag, and the corner point — and `isPositiveArc`
 * is `true` at every corner because the shape is being walked clockwise in screen coordinates,
 * where positive *is* clockwise.
 */
private fun PathBuilder.solidRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) {
    fun corner(x: Float, y: Float) = arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = x, y1 = y)
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    corner(right, top + radius)
    lineTo(right, bottom - radius)
    corner(right - radius, bottom)
    lineTo(left + radius, bottom)
    corner(left, bottom - radius)
    lineTo(left, top + radius)
    corner(left + radius, top)
    close()
}

/**
 * A rounded rectangle drawn as an outline [stroke] thick.
 *
 * An outer shape plus a smaller one, cut out by the file's even-odd rule. See the class docs:
 * this is why the fill type is not a per-glyph choice.
 */
private fun PathBuilder.outlinedRoundRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    stroke: Float,
) {
    solidRoundRect(left, top, right, bottom, radius)
    solidRoundRect(
        left = left + stroke,
        top = top + stroke,
        right = right - stroke,
        bottom = bottom - stroke,
        radius = (radius - stroke).coerceAtLeast(0f),
    )
}

/**
 * An eight-toothed cog with a hole, relying on the even-odd rule for both.
 *
 * ## Why the teeth are computed
 *
 * The alternative is eight hand-typed quads, and at this size a tooth 0.2 units short is
 * invisible in the source and obvious on screen. So the teeth come from one tooth's worth of
 * numbers and eight rotations of it.
 *
 * ## The numbers, and how they were arrived at
 *
 * A tooth is 45 degrees of arc (`step`). Within that, the tip runs a quarter of it
 * ([tipHalf] either side) and the root is flat for the rest. A narrow tip on a wide root is
 * what reads as a gear rather than as a star.
 *
 * [tipRadius] and [rootRadius] were originally 2.3 units apart, on the reasoning that a deeper
 * valley would look like a saw blade. Rasterising that version settled it: a 2.3-unit tooth on
 * a 24-unit grid is a 10% relief, and a circle with a 10% relief is a circle with notches. The
 * tooth is now 3.6 units deep, which is roughly a third again as deep and reads as a cog. The
 * lesson is recorded here rather than only in the diff because the wrong version was a
 * perfectly plausible-looking set of numbers — plausibility is what made it wrong, and the
 * only thing that caught it was drawing it.
 *
 * [holeRadius] is then the largest circle that still leaves a visible web between the hole and
 * the root, because a hole that nearly touches the root turns the gear into a thin ring with
 * bumps on it.
 */
private fun PathBuilder.gear() {
    val center = GRID / 2f
    val teeth = 8
    val step = 2.0 * PI / teeth
    val tipRadius = 10.1f
    val rootRadius = 6.5f
    val tipHalf = step * 0.125
    val rootStart = step * 0.29
    val holeRadius = 3.1f

    // A point on a construction circle of `radius`, at `angle` radians. Screen coordinates,
    // so increasing angle runs clockwise — which is why the hole's winding is left to the
    // fill rule instead of being reasoned about here.
    fun point(angle: Double, radius: Float): Pair<Float, Float> =
        (center + radius * cos(angle)).toFloat() to (center + radius * sin(angle)).toFloat()

    val (firstX, firstY) = point(-tipHalf, tipRadius)
    moveTo(firstX, firstY)

    for (tooth in 0 until teeth) {
        val angle = tooth * step
        if (tooth > 0) {
            // The flank: the previous tooth's flat root, up to this tooth's tip. Same angle,
            // two radii, so this is a straight radial edge rather than a curve.
            val (flankX, flankY) = point(angle - tipHalf, tipRadius)
            lineTo(flankX, flankY)
        }
        val (tipEndX, tipEndY) = point(angle + tipHalf, tipRadius)
        lineTo(tipEndX, tipEndY)
        val (rootAX, rootAY) = point(angle + rootStart, rootRadius)
        lineTo(rootAX, rootAY)
        val (rootBX, rootBY) = point(angle + step - tipHalf, rootRadius)
        lineTo(rootBX, rootBY)
    }
    close()

    // Two half-circle arcs rather than an oval helper, so the hole is drawn with the same
    // three members as everything else in the file. `isMoreThanHalf` is what makes each a
    // half rather than a quarter: without it, the arc takes the short way round and the
    // "circle" becomes a lens.
    moveTo(center - holeRadius, center)
    arcTo(holeRadius, holeRadius, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = center + holeRadius, y1 = center)
    arcTo(holeRadius, holeRadius, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = center - holeRadius, y1 = center)
    close()
}
