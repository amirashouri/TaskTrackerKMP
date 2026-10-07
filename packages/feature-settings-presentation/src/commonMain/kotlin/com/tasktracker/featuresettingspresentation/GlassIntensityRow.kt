package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSlider
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_glass_label
import com.tasktracker.resources.settings_glass_reduced
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

const val GlassSliderTag = "settings.glassSlider"
const val GlassAmountTag = "settings.glassAmount"

@Composable
internal fun GlassIntensityRow(
    intensity: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ttSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = stringResource(Res.string.settings_glass_label),
                style = ttTypography.subheadline,
                color = ttLabel(),
            )
            ttText(
                text = glassAmountLabel(intensity),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(GlassAmountTag),
            )
        }
        ttSlider(
            value = intensity,
            onValueChange = onPreview,
            onValueChangeFinished = onCommit,
            modifier = Modifier.testTag(GlassSliderTag),
        )
    }
}

/**
 * The slider's readout.
 *
 * A percentage everywhere except the very bottom, which reads as the platform's Reduce
 * Transparency.
 */
@Composable
private fun glassAmountLabel(intensity: Float): String =
    if (intensity <= 0.005f) {
        stringResource(Res.string.settings_glass_reduced)
    } else {
        "${(intensity * 100f).roundToInt()}%"
    }
