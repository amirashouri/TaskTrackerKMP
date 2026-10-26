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

const val TTToastTag = "design.toast"

/**
 * Floating confirmation toast drawn on glass.
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
