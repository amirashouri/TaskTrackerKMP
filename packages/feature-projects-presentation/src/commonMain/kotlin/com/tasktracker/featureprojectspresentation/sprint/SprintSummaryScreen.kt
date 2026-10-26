package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.designsystem.TTStoryPoints
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.SprintSummary
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.sprintSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.sprint_achieved
import com.tasktracker.resources.sprint_committed
import com.tasktracker.resources.sprint_summary_done
import com.tasktracker.resources.sprint_summary_empty
import com.tasktracker.resources.sprint_summary_hit
import com.tasktracker.resources.sprint_summary_incomplete
import com.tasktracker.resources.sprint_summary_miss
import com.tasktracker.resources.sprint_summary_no_goal
import com.tasktracker.resources.sprint_summary_none
import com.tasktracker.resources.sprint_summary_title
import org.jetbrains.compose.resources.stringResource

/**
 * Finished sprint summary screen displaying story point totals, goal verdict, and done/incomplete task sections.
 */
@Composable
fun SprintSummaryScreen(
    summary: SprintSummary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.screenGutterLarge)
            .testTag(SprintSummaryScreenTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttGlassIconButton(
                image = ttActionIcons.chevronLeft,
                contentDescription = stringResource(Res.string.common_cancel),
                onClick = onBack,
                modifier = Modifier.testTag(SprintSummaryBackTag),
            )
            ttText(
                text = stringResource(Res.string.sprint_summary_title, summary.sprint.name),
                style = ttTypography.largeTitle,
                color = ttLabel(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            SprintTotal(
                label = stringResource(Res.string.sprint_committed),
                points = summary.committedStoryPoints,
                modifier = Modifier.weight(1f),
            )
            SprintTotal(
                label = stringResource(Res.string.sprint_achieved),
                points = summary.achievedStoryPoints,
                modifier = Modifier.weight(1f),
            )
        }
        ttText(
            text = when (sprintVerdict(summary.didHitGoal)) {
                SprintVerdict.Hit -> stringResource(Res.string.sprint_summary_hit)
                SprintVerdict.Miss -> stringResource(Res.string.sprint_summary_miss)
                SprintVerdict.NoGoal -> stringResource(Res.string.sprint_summary_no_goal)
            },
            style = ttTypography.titleSmall,
            color = when (sprintVerdict(summary.didHitGoal)) {
                SprintVerdict.Hit -> ttAccent()
                else -> ttLabelSecondary()
            },
            modifier = Modifier.testTag(SprintSummaryVerdictTag),
        )
        if (summary.isEmpty) {
            ttText(
                text = stringResource(Res.string.sprint_summary_empty),
                style = ttTypography.footnote,
                color = ttBodyLabel(),
                modifier = Modifier.testTag(SprintSummaryEmptyTag),
            )
        } else {
            SprintSummarySection(
                title = stringResource(Res.string.sprint_summary_done),
                items = summary.done,
                modifier = Modifier.testTag(SprintSummaryDoneTag),
            )
            SprintSummarySection(
                title = stringResource(Res.string.sprint_summary_incomplete),
                items = summary.incomplete,
                modifier = Modifier.testTag(SprintSummaryIncompleteTag),
            )
        }
    }
}

@Composable
private fun SprintSummarySection(
    title: String,
    items: List<WorkItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(text = title, style = ttTypography.titleSmall, color = ttLabel())
        if (items.isEmpty()) {
            ttText(
                text = stringResource(Res.string.sprint_summary_none),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        } else {
            items.forEach { item ->
                SprintItemRow(
                    key = item.key.toString(),
                    title = item.title,
                    storyPoints = item.storyPoints,
                )
            }
        }
    }
}

/** Three-way outcome of whether a sprint achieved its point goal. */
internal enum class SprintVerdict {
    Hit,
    Miss,
    NoGoal,
}

/** Maps nullable boolean goal result into [SprintVerdict] enum. */
internal fun sprintVerdict(didHitGoal: Boolean?): SprintVerdict = when (didHitGoal) {
    true -> SprintVerdict.Hit
    false -> SprintVerdict.Miss
    null -> SprintVerdict.NoGoal
}

@Composable
private fun SprintItemRow(
    key: String,
    title: String,
    storyPoints: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        ttText(
            text = key,
            style = ttTypography.key,
            color = ttLabelSecondary(),
        )
        ttText(
            text = title,
            style = ttTypography.callout,
            color = ttLabel(),
            maxLines = SPRINT_ITEM_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TTStoryPoints(points = storyPoints)
    }
}

private const val SPRINT_ITEM_TITLE_MAX_LINES = 2

const val SprintSummaryScreenTag = "sprints.summary"
const val SprintSummaryBackTag = "sprints.summary.back"
const val SprintSummaryVerdictTag = "sprints.summary.verdict"
const val SprintSummaryEmptyTag = "sprints.summary.empty"
const val SprintSummaryDoneTag = "sprints.summary.done"
const val SprintSummaryIncompleteTag = "sprints.summary.incomplete"