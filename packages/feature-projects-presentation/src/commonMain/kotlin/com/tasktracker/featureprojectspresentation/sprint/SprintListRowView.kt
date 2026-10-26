package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.SprintID
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttDateRange
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_finished
import com.tasktracker.resources.sprint_open
import com.tasktracker.resources.sprint_progress
import org.jetbrains.compose.resources.stringResource

/**
 * List row representing a sprint, displaying name, date window, story point progress,
 * and lifecycle status badge (Open/Finished).
 */
@Composable
fun SprintListRowView(
    row: SprintListRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(ttSpacing.xl)
            .testTag(SprintRowTag(row.sprint.id)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttText(
                text = row.sprint.name,
                style = ttTypography.headline,
                color = ttLabel(),
                maxLines = SPRINT_NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SprintStatusBadge(isFinished = row.sprint.isFinished)
        }
        ttText(
            text = ttDateRange(row.startDate, row.endDate),
            style = ttTypography.captionSmall,
            color = ttLabelSecondary(),
        )
        SprintProgressBar(fraction = row.progressFraction)
        ttText(
            text = stringResource(
                Res.string.sprint_progress,
                row.achievedStoryPoints,
                row.sprint.goalStoryPoints,
            ),
            style = ttTypography.caption,
            color = ttBodyLabel(),
        )
    }
}

@Composable
private fun SprintStatusBadge(
    isFinished: Boolean,
    modifier: Modifier = Modifier,
) {
    ttText(
        text = stringResource(
            if (isFinished) Res.string.sprint_finished else Res.string.sprint_open,
        ),
        style = ttTypography.captionSmall,
        color = ttLabelSecondary(),
        modifier = modifier.testTag(SprintStatusBadgeTag),
    )
}

fun SprintRowTag(sprintID: SprintID): String = "sprints.row.${sprintID.stringValue}"

const val SprintStatusBadgeTag = "sprints.row.badge"

private const val SPRINT_NAME_MAX_LINES = 2