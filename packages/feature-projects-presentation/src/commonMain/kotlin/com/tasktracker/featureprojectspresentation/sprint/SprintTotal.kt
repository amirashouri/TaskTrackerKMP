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

/**
 * One of the two totals, side by side.
 *
 * ## Shared by two screens, which is why it is its own file
 *
 * [SprintDetailScreen] shows the pair for a sprint under way and [SprintSummaryScreen] shows
 * it again for a finished one — committed against achieved, in the same two cells. It is the
 * one piece those two screens have in common, so it lives here rather than being private in
 * one of them; a second copy would be two renderings of the same pair to keep in step.
 *
 * [SprintSummary]'s numbers rather than the summary's own goal — the goal is a target, not
 * an achievement, so it belongs beside both rather than being one of them. The points are
 * read through [TTStoryPoints] so a "no estimate" reading would look the same here as it
 * does on a card, rather than this screen inventing a fourth treatment of the absence.
 */
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
