package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography

/** Displays a metric total (label and integer point value) for sprint summaries. */
@Composable
fun SprintTotal(
    label: String,
    points: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
    ) {
        ttText(text = label, style = ttTypography.captionSmall, color = ttLabelSecondary())
        ttText(
            text = points.toString(),
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
    }
}