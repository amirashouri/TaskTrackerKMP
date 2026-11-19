package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTStoryPoints
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.SprintMemberProgress
import com.tasktracker.domain.WorkItem
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.sprint_member_name_unknown
import com.tasktracker.resources.sprint_member_progress_closed
import com.tasktracker.resources.sprint_member_progress_delivered
import com.tasktracker.resources.sprint_member_progress_empty
import com.tasktracker.resources.sprint_member_progress_finished
import com.tasktracker.resources.sprint_member_progress_no_capacity
import com.tasktracker.resources.sprint_member_progress_remaining
import com.tasktracker.resources.sprint_member_progress_title
import com.tasktracker.resources.sprint_member_progress_unassigned
import org.jetbrains.compose.resources.stringResource

/**
 * A read-only report of what each rostered member delivered against their available points.
 *
 * The list is scrollable because a sprint can hold far more finished work than fits on one
 * screen; the same items appear in the summary, but here the per-person grouping is the
 * point rather than a supporting section.
 */
@Composable
fun SprintMemberProgressScreen(
    view: SprintMemberProgressView,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ttSpacing.xxl)
            .padding(top = ttSpacing.screenGutterLarge)
            .testTag(SprintMemberProgressScreenTag),
        contentPadding = PaddingValues(bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                ttGlassIconButton(
                    image = ttActionIcons.chevronLeft,
                    contentDescription = stringResource(Res.string.common_cancel),
                    onClick = onBack,
                    modifier = Modifier.testTag(SprintMemberProgressBackTag),
                )
                ttText(
                    text = stringResource(Res.string.sprint_member_progress_title, view.sprintName),
                    style = ttTypography.largeTitle,
                    color = ttLabel(),
                    maxLines = MEMBER_PROGRESS_TITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (view.participation.members.isEmpty()) {
            item {
                ttText(
                    text = stringResource(Res.string.sprint_member_progress_empty),
                    style = ttTypography.footnote,
                    color = ttBodyLabel(),
                    modifier = Modifier.testTag(SprintMemberProgressEmptyTag),
                )
            }
        } else {
            items(
                items = view.participation.members,
                key = { it.member.id },
            ) { row ->
                SprintMemberProgressCard(
                    row = row,
                    name = view.memberNames[row.member.userID]
                        ?: stringResource(Res.string.sprint_member_name_unknown),
                    modifier = Modifier.testTag(SprintMemberProgressRowTag(row.member.id)),
                )
            }
        }

        if (view.participation.unassigned.isNotEmpty()) {
            item {
                ttText(
                    text = stringResource(Res.string.sprint_member_progress_unassigned),
                    style = ttTypography.titleSmall,
                    color = ttLabel(),
                    modifier = Modifier.testTag(SprintMemberProgressUnassignedTag),
                )
            }
            items(
                items = view.participation.unassigned,
                key = { it.id.stringValue },
            ) { item ->
                MemberTaskRow(item = item)
            }
        }
    }
}

@Composable
private fun SprintMemberProgressCard(
    row: SprintMemberProgress,
    name: String,
    modifier: Modifier = Modifier,
) {
    val capacity = row.member.availableStoryPoints
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassCard()
            .padding(ttSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttText(
                text = name,
                style = ttTypography.title,
                color = ttLabel(),
                modifier = Modifier.weight(1f),
            )
            if (capacity > 0) {
                ttText(
                    text = stringResource(
                        Res.string.sprint_member_progress_delivered,
                        row.deliveredStoryPoints,
                        capacity,
                    ),
                    style = ttTypography.callout,
                    color = ttLabelSecondary(),
                )
            } else {
                ttText(
                    text = stringResource(Res.string.sprint_member_progress_no_capacity),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )
            }
        }

        SprintProgressBar(fraction = row.progressFraction)

        if (row.finished.isNotEmpty()) {
            ttText(
                text = stringResource(Res.string.sprint_member_progress_finished),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            row.finished.forEach { item -> MemberTaskRow(item = item) }
        }

        ttText(
            text = stringResource(Res.string.sprint_member_progress_closed, row.closed.size),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )
        ttText(
            text = stringResource(Res.string.sprint_member_progress_remaining, row.remainingCount),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )
    }
}

/** Shared by the progress report and the summary's compact member section. */
@Composable
internal fun MemberTaskRow(
    item: WorkItem,
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
            text = item.key.toString(),
            style = ttTypography.key,
            color = ttLabelSecondary(),
        )
        ttText(
            text = item.title,
            style = ttTypography.callout,
            color = ttLabel(),
            maxLines = MEMBER_TASK_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TTStoryPoints(points = item.storyPoints)
    }
}

private const val MEMBER_PROGRESS_TITLE_MAX_LINES = 2
private const val MEMBER_TASK_TITLE_MAX_LINES = 2

const val SprintMemberProgressScreenTag = "sprints.memberProgress"
const val SprintMemberProgressBackTag = "sprints.memberProgress.back"
const val SprintMemberProgressEmptyTag = "sprints.memberProgress.empty"
const val SprintMemberProgressUnassignedTag = "sprints.memberProgress.unassigned"

fun SprintMemberProgressRowTag(id: String): String = "sprints.memberProgress.row.$id"
