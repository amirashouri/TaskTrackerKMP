package com.tasktracker.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp

/**
 * How a [ttBanner] reads: a failure, or an outcome the user should know about.
 *
 * ## Why this is a parameter and not two components
 *
 * The projects screens were already using their banner for both — `ProjectsErrorBanner`
 * carried `errorMessage` *and* `RemovalNotice`, so "3 items archived instead, 4 deleted"
 * was painted in the failure red. Two components would fix the duplication and keep the
 * confusion; one component with a tone is the honest shape, because the thing being
 * parameterised is a colour and not a layout.
 */
enum class ttBannerTone {

    /** A failure. Red fill, red rim, red glyph. */
    Error,

    /**
     * An outcome that is not a failure — a destructive action that archived instead of
     * deleting, for instance. Accent-toned, and legible by exactly the same margin.
     */
    Accent,
}

/**
 * A notice the user has to read, inline rather than as a bare red label under a field.
 *
 * ## Why this component exists here and not in two features
 *
 * There were two of these, and they drifted in the exact way duplicated code drifts:
 *
 * | | `ErrorBanner` (auth) | `ProjectsErrorBanner` (projects) |
 * | --- | --- | --- |
 * | message colour | `ttLabel()` | `ttErrorRim()` |
 * | radius | `ttRadius.banner` (12dp) | `ttRadius.control` (14dp) |
 * | padding | a literal `14.dp` | `ttSpacing.xl` |
 * | dismiss | not supported | supported |
 *
 * The message colour is the row that matters. `ttErrorRim()` is the *rim* token — error at
 * 25% alpha — drawn over this banner's own 10% fill, which composites the message to
 * **1.37:1** in light and **1.39:1** in dark: pale pink on pale pink, in both, on the one
 * component whose entire job is to be read. The auth copy was right and the projects copy
 * was not, and nothing caught it because each module's tests could only see its own.
 *
 * The lesson is not "be careful with token roles". It is that a component two modules both
 * needed belonged in the module both modules already depended on.
 *
 * ## The message is [ttLabel], and the reason is arithmetic
 *
 * The obvious repair for the drift above is `ttError()`. It is not sufficient: `#FF3B30` on
 * the 10% fill measures **3.10:1** in light, which is a different bug rather than the
 * absence of one. [ttLabel] measures **18.37:1** on the same fill in light and **19.53:1**
 * in dark. The banner's tone is already carried three times over — the fill, the rim and the
 * glyph — so the message does not also have to be coloured, and an unreadable failure notice
 * is worse than a legible one that is not red.
 *
 * ## The glyph needs no accessibility treatment, and that is load-bearing
 *
 * It is a [Canvas] with no semantics, so it contributes nothing to the tree and a screen
 * reader already reads this banner as its message and nothing else. The obvious "fix" —
 * `clearAndSetSemantics` over the whole banner with the message as its `contentDescription` —
 * was written, tested and removed on the auth screen for exactly that reason: it works, and
 * it *deletes the text from the semantics tree*. A UI test could no longer find the message
 * with `hasText`, and neither could an automated accessibility audit. Trading a real,
 * queryable text node for a redundant description is a loss with no matching gain.
 */
@Composable
fun ttBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: ttBannerTone = ttBannerTone.Error,
    onDismiss: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(ttRadius.banner)
    val base = when (tone) {
        ttBannerTone.Error -> ttError()
        ttBannerTone.Accent -> ttAccent()
    }
    val fill = base.copy(alpha = ttBannerMetrics.FILL_ALPHA)
    val rim = base.copy(alpha = ttBannerMetrics.RIM_ALPHA)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TTBannerTag)
            .clip(shape)
            .background(fill)
            .border(width = ttSeparatorWidth, color = rim, shape = shape)
            .padding(ttBannerMetrics.PADDING),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        WarningGlyph(color = base, knockout = fill)
        ttText(
            text = message,
            style = ttTypography.subheadline,
            // See the class docs: `ttLabel`, not `base`, and the measured margin is why.
            color = ttLabel(),
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            ttText(
                text = "×",
                style = ttTypography.subheadline,
                color = ttLabel(),
                modifier = Modifier
                    .testTag(TTBannerDismissTag)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}

/**
 * The banner's tag.
 *
 * A named constant rather than a literal at the call site, because a test that has to guess
 * the string stops being a test of behaviour and becomes a test of this file. The auth screen
 * shipped its own (`"signIn.error"`), which meant the tag was a property of a feature rather
 * than of the component.
 */
const val TTBannerTag = "design.banner"

/** The dismiss affordance's own tag, because "the banner has a dismiss" is not an assertion. */
const val TTBannerDismissTag = "design.banner.dismiss"

/**
 * The alphas and padding, named so a tone change is one edit in one place.
 *
 * `internal` rather than `private` so `BannerContrastTest` measures the numbers the banner
 * actually paints. A test that restates `0.10f` as its own constant passes while the banner
 * is drawing something else, which is the failure mode a contrast test exists to prevent.
 */
internal object ttBannerMetrics {

    /** Matches `ttErrorFill()`'s 10%, so the two spellings composite identically. */
    const val FILL_ALPHA: Float = 0.10f

    /** Matches `ttErrorRim()`'s 25%. */
    const val RIM_ALPHA: Float = 0.25f

    /** The original's `padding(14)`. */
    val PADDING = 14.dp
}

/**
 * The exclamation triangle, drawn rather than imported.
 *
 * The original uses `exclamationmark.triangle.fill` from SF Symbols. The design system ships
 * no assets, deliberately, so no feature can grow a private picture vocabulary — so the glyph
 * is a [Canvas] path in the same spirit as [AppMark]: themeable, and no new dependency.
 *
 * The proportions are drawn in a unit square and scaled by the caller's size, so the shape
 * holds at any size rather than being fixed to one 16dp.
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
        // banner's own fill colour rather than white, so it reads against whatever the banner
        // is sitting on instead of only against a background we chose.
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