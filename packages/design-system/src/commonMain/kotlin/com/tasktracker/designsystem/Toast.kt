package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

/**
 * A short confirmation that floats over the surface that caused it.
 *
 * ## Why this is glass and not the banner's accent wash
 *
 * [ttBanner] is the component for a notice that has to stay until it is read, and it earns that
 * with a 10% accent fill over the page it sits in. A toast sits over whatever the user was
 * looking at — on the board, a translucent lane and the cards in it — so the same wash would
 * let that content through and the message would be competing with it. The glass material is
 * the app's answer to "text over something that is not a flat background"; it is the same
 * primitive the tab bar and the lane headings use for that problem.
 *
 * ## Why the message is [ttLabel]
 *
 * The toast is already a floating surface with an edge, so it does not need colour to be
 * noticed, and [ttLabel] on glass is the pairing the rest of the package holds to a measured
 * margin. A tinted toast would also imply a tone — success or failure — that it does not have:
 * it confirms an outcome, and the caller decides whether that is worth a banner instead.
 *
 * ## Timing is the caller's
 *
 * This draws the pill and nothing else. How long it stays, and what dismisses it, are
 * behaviour rather than appearance — the board counts copies and hides the toast on its own
 * clock, so the component cannot disagree with the screen about when the confirmation ended.
 *
 * [LiveRegionMode.Polite] is set on the pill because the message appears without a focus move:
 * a screen reader is told once, after it finishes the current utterance, rather than being
 * interrupted mid-sentence.
 */
@Composable
fun ttToast(
    message: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .testTag(TTToastTag)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.banner))
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = message,
            style = ttTypography.footnote,
            color = ttLabel(),
        )
    }
}

/** The toast's tag, so a screen's test can find the confirmation without its wording. */
const val TTToastTag = "design.toast"

@Preview(name = "Toast, dark", showBackground = true)
@Composable
private fun ToastDarkPreview() = ttPreviewSurface(ThemeScheme.Dark) {
    ttToast(message = "Copied to clipboard")
}

@Preview(name = "Toast, light", showBackground = true)
@Composable
private fun ToastLightPreview() = ttPreviewSurface(ThemeScheme.Light) {
    ttToast(message = "Copied to clipboard")
}
