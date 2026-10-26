package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttSeparator

/** Linear progress indicator representing sprint story point completion ratio. */
@Composable
fun SprintProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SPRINT_PROGRESS_BAR_HEIGHT)
            .clip(RoundedCornerShape(SPRINT_PROGRESS_BAR_RADIUS))
            .background(ttSeparator())
            .testTag(SprintProgressBarTag),
    ) {
        if (fraction != null && fraction > 0f) {
            Box(
                modifier = Modifier
                    // Clamp max fill width to 1.0 to prevent layout crashes if fraction exceeds 100%
                    .fillMaxWidth(fraction.coerceAtMost(SPRINT_PROGRESS_BAR_MAX_WIDTH))
                    .height(SPRINT_PROGRESS_BAR_HEIGHT)
                    .clip(RoundedCornerShape(SPRINT_PROGRESS_BAR_RADIUS))
                    .background(ttAccent()),
            )
        }
    }
}

const val SprintProgressBarTag = "sprints.row.progress"

private val SPRINT_PROGRESS_BAR_HEIGHT = 6.dp
private val SPRINT_PROGRESS_BAR_RADIUS = 3.dp
private const val SPRINT_PROGRESS_BAR_MAX_WIDTH = 1f