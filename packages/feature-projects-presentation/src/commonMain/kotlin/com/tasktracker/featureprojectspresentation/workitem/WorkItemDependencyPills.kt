package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.resources.Res
import com.tasktracker.resources.work_item_depends_add
import com.tasktracker.resources.work_item_depends_empty
import com.tasktracker.resources.work_item_depends_label
import com.tasktracker.resources.work_item_depends_remove
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WorkItemDependencyPills(
    blockers: List<WorkItem>,
    onOpenBlocker: (WorkItemID) -> Unit,
    onRemoveBlocker: (WorkItemID) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .testTag(WorkItemDependsSectionTag),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
    ) {
        ttText(
            text = stringResource(Res.string.work_item_depends_label),
            style = ttTypography.captionEmphasised,
            color = ttLabelSecondary(),
            modifier = Modifier.weight(1f),
        )
        ttGlassButton(
            label = stringResource(Res.string.work_item_depends_add),
            onClick = onAddClick,
            modifier = Modifier.testTag(WorkItemDependsAddTag),
        )
    }
    if (blockers.isNotEmpty()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ttSpacing.xs)
                .testTag(WorkItemDependsListTag),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            for (blocker in blockers) {
                DependencyPill(
                    item = blocker,
                    onOpen = { onOpenBlocker(blocker.id) },
                    onRemove = { onRemoveBlocker(blocker.id) },
                )
            }
        }
    } else {
        ttText(
            text = stringResource(Res.string.work_item_depends_empty),
            style = ttTypography.caption,
            color = ttLabelSecondary(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ttSpacing.xs)
                .testTag(WorkItemDependsEmptyTag),
        )
    }
}


@Composable
private fun DependencyPill(
    item: WorkItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ttRadius.control))
            .ttGlassCard()
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm)
            .testTag(WorkItemDependsPillTag(item)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = item.key.toString(),
            style = ttTypography.key,
            color = ttLabel(),
            modifier = Modifier.testTag(WorkItemDependsKeyTag(item)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ttText(
            text = "X",
            style = ttTypography.callout,
            color = ttLabelSecondary(),
            modifier = Modifier
                .clip(RoundedCornerShape(ttRadius.control))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(Res.string.work_item_depends_remove, item.key.toString()),
                    onClick = onRemove,
                )
                .testTag(WorkItemDependsRemoveTag(item)),
        )
    }
}

@Preview
@Composable
private fun WorkItemDependencyPillsPreview() = ProjectsPreview(ThemeScheme.Dark) {
    WorkItemDependencyPills(
        blockers = ViewPreviewData.workItems.take(3),
        onOpenBlocker = {},
        onRemoveBlocker = {},
        onAddClick = {},
    )
}

const val WorkItemDependsSectionTag = "workItem.editor.depends"
const val WorkItemDependsListTag = "workItem.editor.depends.list"
const val WorkItemDependsEmptyTag = "workItem.editor.depends.empty"
const val WorkItemDependsAddTag = "workItem.editor.depends.add"

fun WorkItemDependsPillTag(item: WorkItem): String = "workItem.editor.depends.pill.${item.key}"

fun WorkItemDependsKeyTag(item: WorkItem): String = "workItem.editor.depends.key.${item.key}"

fun WorkItemDependsRemoveTag(item: WorkItem): String = "workItem.editor.depends.remove.${item.key}"
