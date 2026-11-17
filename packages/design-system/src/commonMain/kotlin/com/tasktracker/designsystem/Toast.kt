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

const val TTToastTag = "design.toast"

/**
 * Floating confirmation toast drawn on glass.
 */
@Composable
fun ttToast(
    message: String,
    modifier: Modifier = Modifier,
) {
    TTGlassSurface(
        shape = RoundedCornerShape(ttRadius.banner),
        modifier = modifier
            .testTag(TTToastTag)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        // Padding belongs to the content, not to the surface's modifier: chained before
        // `ttGlassSurface` it shrank the glass to the text and left the sentence flush
        // against the rim.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.lg),
        ) {
            ttText(
                text = message,
                style = ttTypography.footnote,
                color = ttLabel(),
            )
        }
    }
}
