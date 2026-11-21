package com.tasktracker.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared, hand-drawn glyphs for the grouped rows on the Settings screen. */
@Immutable
object ttSettingsIcons {
    val appearance: ImageVector = icon("Appearance") {
        circle(12f, 12f, 4f)
        for (index in 0 until 8) {
            val angle = index * (kotlin.math.PI / 4.0)
            val startX = 12f + (6.2 * kotlin.math.cos(angle)).toFloat()
            val startY = 12f + (6.2 * kotlin.math.sin(angle)).toFloat()
            val endX = 12f + (9.0 * kotlin.math.cos(angle)).toFloat()
            val endY = 12f + (9.0 * kotlin.math.sin(angle)).toFloat()
            moveTo(startX - 0.8f, startY - 0.8f)
            lineTo(endX - 0.8f, endY - 0.8f)
            lineTo(endX + 0.8f, endY + 0.8f)
            lineTo(startX + 0.8f, startY + 0.8f)
            close()
        }
    }

    val connectivity: ImageVector = icon("Connectivity") {
        // Three nested signal bands and a separate receiver dot.
        moveTo(2f, 8f)
        quadTo(12f, -1f, 22f, 8f)
        lineTo(19.5f, 10.5f)
        quadTo(12f, 4f, 4.5f, 10.5f)
        close()
        moveTo(6f, 12f)
        quadTo(12f, 6.5f, 18f, 12f)
        lineTo(15.5f, 14.5f)
        quadTo(12f, 11.5f, 8.5f, 14.5f)
        close()
        moveTo(10f, 16f)
        quadTo(12f, 14f, 14f, 16f)
        lineTo(12f, 18f)
        close()
        circle(12f, 21f, 1.5f)
    }

    val offline: ImageVector = icon("Offline") {
        // Airplane silhouette, used for the force-offline control.
        moveTo(21.5f, 2.5f)
        quadTo(23f, 4f, 21f, 6f)
        lineTo(14f, 13f)
        lineTo(15f, 19f)
        lineTo(12.5f, 20.5f)
        lineTo(10f, 15f)
        lineTo(6.5f, 18.5f)
        lineTo(6.5f, 21f)
        lineTo(4.5f, 22f)
        lineTo(4f, 18f)
        lineTo(2f, 17.5f)
        lineTo(3f, 15.5f)
        lineTo(5.5f, 15.5f)
        lineTo(9f, 12f)
        lineTo(3.5f, 9.5f)
        lineTo(5f, 7f)
        lineTo(11f, 8f)
        lineTo(18f, 1f)
        quadTo(20f, -1f, 21.5f, 2.5f)
        close()
    }

    val sync: ImageVector = icon("Sync") {
        // Two opposing arrows, kept as one compact mark at row-icon size.
        moveTo(4f, 7f)
        lineTo(7f, 4f)
        lineTo(10f, 7f)
        lineTo(8f, 7f)
        lineTo(8f, 10f)
        lineTo(6f, 10f)
        lineTo(6f, 7f)
        close()
        moveTo(20f, 17f)
        lineTo(17f, 20f)
        lineTo(14f, 17f)
        lineTo(16f, 17f)
        lineTo(16f, 14f)
        lineTo(18f, 14f)
        lineTo(18f, 17f)
        close()
        roundRect(9f, 5.5f, 18f, 8f, 0.75f)
        roundRect(6f, 16f, 15f, 18.5f, 0.75f)
    }

    val account: ImageVector = icon("Account") {
        circle(12f, 8f, 4f)
        moveTo(3f, 21f)
        quadTo(3f, 14f, 12f, 14f)
        quadTo(21f, 14f, 21f, 21f)
        close()
    }

    val lock: ImageVector = icon("Lock") {
        // A shackle arc rising over a rounded body, matching the row-icon weight above.
        moveTo(8f, 11f)
        lineTo(8f, 8f)
        arcTo(4f, 4f, 0f, false, true, 16f, 8f)
        lineTo(16f, 11f)
        roundRect(5f, 11f, 19f, 21f, 2f)
    }

    val about: ImageVector = icon("About", PathFillType.EvenOdd) {
        // The filled disc, knockout center, and separate dot and stem make a clear “i”.
        circle(12f, 12f, 10f)
        circle(12f, 12f, 7.6f)
        circle(12f, 7.5f, 1.15f)
        roundRect(10.9f, 10f, 13.1f, 18f, 1.1f)
    }

    val email: ImageVector = icon("Email") {
        // Envelope silhouette: a V-flap top over a rectangular body.
        moveTo(4f, 6f)
        lineTo(12f, 13f)
        lineTo(20f, 6f)
        lineTo(20f, 18f)
        lineTo(4f, 18f)
        close()
    }

    val linkedin: ImageVector = icon("LinkedIn", PathFillType.EvenOdd) {
        // A rounded square frame with "in" knocked back in inside it. EvenOdd lets the two
        // inner shapes read as filled again after the frame's inner cut-out.
        roundRect(2f, 2f, 22f, 22f, 4.5f)
        roundRect(5.4f, 5.4f, 18.6f, 18.6f, 2.2f)
        circle(8.6f, 9.2f, 1.35f)
        roundRect(7.3f, 11.5f, 9.9f, 18.6f, 0.5f)
        roundRect(11.7f, 11.5f, 14f, 18.6f, 0.5f)
        moveTo(14f, 13.4f)
        quadTo(14f, 11.5f, 16.1f, 11.5f)
        quadTo(18.3f, 11.5f, 18.3f, 13.5f)
        lineTo(18.3f, 18.6f)
        lineTo(16f, 18.6f)
        lineTo(16f, 14.4f)
        quadTo(16f, 13.4f, 14f, 13.4f)
        close()
    }

    val members: ImageVector = icon("Members") {
        // Two heads over two shoulders: a group rather than a single person.
        circle(8f, 7f, 3f)
        circle(16f, 7f, 3f)
        moveTo(3f, 21f)
        quadTo(3f, 14f, 8f, 14f)
        quadTo(13f, 14f, 13f, 21f)
        close()
        moveTo(11f, 21f)
        quadTo(11f, 14f, 16f, 14f)
        quadTo(21f, 14f, 21f, 21f)
        close()
    }

    val signOut: ImageVector = icon("SignOut") {
        // A door plus an arrow pointing out of it.
        roundRect(3f, 4f, 13f, 20f, 2f)
        moveTo(14f, 10.5f)
        lineTo(20f, 10.5f)
        lineTo(20f, 9f)
        lineTo(23f, 12f)
        lineTo(20f, 15f)
        lineTo(20f, 13.5f)
        lineTo(14f, 13.5f)
        close()
    }

    val role: ImageVector = icon("Role") {
        // A briefcase: body plus a handle on top.
        roundRect(3f, 9f, 21f, 19f, 2f)
        roundRect(9f, 5f, 15f, 9f, 1.5f)
    }

    val status: ImageVector = icon("Status", PathFillType.EvenOdd) {
        // A ring with a filled centre dot: a status indicator.
        circle(12f, 12f, 10f)
        circle(12f, 12f, 6f)
        circle(12f, 12f, 2.5f)
    }
}

/** Icon tile dimensions shared by settings rows. */
@Immutable
object ttSettingsIconSize {
    val tile: Dp = 30.dp
    val glyph: Dp = 17.dp
}

private fun icon(
    name: String,
    fillType: PathFillType = PathFillType.NonZero,
    draw: PathBuilder.() -> Unit,
): ImageVector {
    val nodes = PathBuilder().apply(draw).nodes
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = nodes,
        pathFillType = fillType,
        fill = SolidColor(Color.Black),
    ).build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx + radius, cy)
    arcTo(radius, radius, 0f, false, true, cx - radius, cy)
    arcTo(radius, radius, 0f, false, true, cx + radius, cy)
    close()
}

private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    arcTo(radius, radius, 0f, false, true, right, top + radius)
    lineTo(right, bottom - radius)
    arcTo(radius, radius, 0f, false, true, right - radius, bottom)
    lineTo(left + radius, bottom)
    arcTo(radius, radius, 0f, false, true, left, bottom - radius)
    lineTo(left, top + radius)
    arcTo(radius, radius, 0f, false, true, left + radius, top)
    close()
}
