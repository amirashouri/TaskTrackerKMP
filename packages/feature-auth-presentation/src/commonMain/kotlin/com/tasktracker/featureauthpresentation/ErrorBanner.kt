package com.tasktracker.featureauthpresentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttErrorFill
import com.tasktracker.designsystem.ttErrorRim
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography

/**
 * A failed attempt, shown inline rather than as a bare red label under the field.
 *
 * The shape is the original's: the glyph and the text share a top-aligned row so a two-line
 * message keeps its glyph beside the *first* line rather than floating at the top of a tall
 * box, and the whole thing is a card with a 10% fill and a 25% rim so it reads as a notice
 * rather than as an error *state* on one field.
 *
 * ## The glyph needs no accessibility treatment, and that is load-bearing
 *
 * It is a [Canvas] with no semantics, so it contributes nothing to the tree and a screen
 * reader already reads this banner as its message and nothing else. The obvious "fix" —
 * `clearAndSetSemantics` on the whole banner with the message as its `contentDescription` —
 * was written, tested, and removed for exactly that reason: it works, and it *deletes the
 * text from the semantics tree*. A UI test could no longer find the message with
 * `hasText`, and neither could an automated accessibility audit. Trading a real, queryable
 * text node for a redundant description is a loss with no matching gain, so the banner keeps
 * its text as text.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.banner)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ErrorBannerTag)
            .clip(shape)
            .background(ttErrorFill())
            .border(width = 1.dp, color = ttErrorRim(), shape = shape)
            .padding(BANNER_PADDING),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        WarningGlyph(color = ttError(), knockout = ttErrorFill())
        ttText(
            text = message,
            style = ttTypography.subheadline,
            color = ttLabel(),
        )
    }
}

/**
 * The banner's test tag.
 *
 * A named constant rather than a literal at the call site, because a test that has to guess
 * the string stops being a test of behaviour and becomes a test of this file.
 */
const val ErrorBannerTag = "signIn.error"

/** The original's `padding(14)`. */
private val BANNER_PADDING = 14.dp

/**
 * The exclamation triangle, drawn rather than imported.
 *
 * The original uses `exclamationmark.triangle.fill` from SF Symbols. This module has no icon
 * set — the design system ships no assets, deliberately, so no feature can grow a private
 * picture vocabulary — so the glyph is a [Canvas] path in the same spirit as
 * [com.tasktracker.designsystem.AppMark]: themeable, and no new dependency.
 *
 * The proportions are drawn in a unit square and scaled by the caller's size, so the shape
 * holds at any size the design system asks for rather than being fixed to one 16dp.
 */
@Composable
private fun WarningGlyph(
    color: Color,
    /** The banner's own fill, used to knock the mark out of the triangle. */
    knockout: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(ttSpacing.xl)) {
        val unit = minOf(size.width, size.height)
        val left = (size.width - unit) / 2f
        val top = (size.height - unit) / 2f

        val triangle = Path().apply {
            // A rounded triangle would need arcs at three corners; the original's glyph is
            // visually a sharp triangle, and at 12dp the difference is not resolvable.
            moveTo(left + unit * 0.5f, top)
            lineTo(left + unit, top + unit * 0.92f)
            lineTo(left, top + unit * 0.92f)
            close()
        }
        drawPath(path = triangle, color = color)

        // The exclamation mark: a stem and a dot, knocked out of the triangle. Drawn in the
        // banner's own fill colour rather than white, so it reads against whatever the
        // banner is sitting on instead of only against a background we chose.
        val barWidth = unit * 0.13f
        drawRect(
            color = knockout,
            topLeft = Offset(left + unit * 0.5f - barWidth / 2f, top + unit * 0.30f),
            size = Size(barWidth, unit * 0.28f),
        )
        drawCircle(
            color = knockout,
            radius = barWidth * 0.72f,
            center = Offset(left + unit * 0.5f, top + unit * 0.76f),
        )
    }
}

@Preview(name = "Error banner", showBackground = true, widthDp = 360)
@Composable
private fun ErrorBannerPreview() {
    ttTheme(ThemeScheme.System) {
        ErrorBanner(
            message = "That email or password is not right.",
            modifier = Modifier
                .background(ttCanvas())
                .padding(ttSpacing.screenGutterLarge),
        )
    }
}
