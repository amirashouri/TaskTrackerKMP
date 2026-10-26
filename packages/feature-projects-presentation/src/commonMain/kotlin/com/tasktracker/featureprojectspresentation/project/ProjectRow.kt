package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparatorWidth
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_delete
import com.tasktracker.resources.project_archived_notice
import com.tasktracker.resources.project_edit
import com.tasktracker.resources.project_task_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * List row representing a project, displaying monogram prefix, task count, and edit/delete actions.
 * Consumes immutable [ProjectSummary] snapshot to prevent UI mutations during background store syncs.
 */
@Composable
fun ProjectRowView(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit = {},
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
                onClick = onOpen,
            )
            .padding(ttSpacing.xl)
            .testTag(ProjectRowTag(project)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectKeyMonogram(
                keyPrefix = project.keyPrefix,
                isArchived = project.isArchived,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
            ) {
                ttText(
                    text = project.name,
                    style = ttTypography.callout,
                    color = ttLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ttText(
                    text = pluralStringResource(Res.plurals.project_task_count, project.itemCount, project.itemCount),
                    style = ttTypography.captionSmall,
                    color = ttLabelSecondary(),
                )
            }
            if (project.isArchived) {
                ttText(
                    text = stringResource(Res.string.project_archived_notice, project.name),
                    style = ttTypography.captionSmall,
                    color = ttLabelSecondary(),
                )
            }
        }
        if (project.details.isNotBlank()) {
            ttText(
                text = project.details,
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                maxLines = PROJECT_DETAILS_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassIconButton(
                image = ttActionIcons.edit,
                contentDescription = stringResource(Res.string.project_edit),
                onClick = onEdit,
                modifier = Modifier.testTag(ProjectEditTag(project)),
            )
            ttGlassIconButton(
                image = ttActionIcons.trash,
                contentDescription = stringResource(Res.string.common_delete),
                onClick = onRemove,
                modifier = Modifier.testTag(ProjectRemoveTag(project)),
                tint = ttError(),
            )
        }
    }
}

/** Circular monogram badge for project key prefix (rendered greyscale when archived). */
@Composable
private fun ProjectKeyMonogram(
    keyPrefix: String,
    isArchived: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (isArchived) ttLabelSecondary() else ttAccent()
    val shape = CircleShape

    Column(
        modifier = modifier
            .size(monogramSize)
            .clip(shape)
            .background(tint.copy(alpha = MONOGRAM_FILL_ALPHA))
            .border(ttSeparatorWidth, tint.copy(alpha = MONOGRAM_RIM_ALPHA), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ttText(
            text = keyPrefix.take(MONOGRAM_CHARACTERS),
            style = ttTypography.captionEmphasised,
            color = ttLabel(),
        )
    }
}

const val ProjectRowTagPrefix = "projects.row"

fun ProjectRowTag(project: ProjectSummary): String = "$ProjectRowTagPrefix.${project.keyPrefix}"
fun ProjectRemoveTag(project: ProjectSummary): String = "${ProjectRowTag(project)}.remove"
fun ProjectEditTag(project: ProjectSummary): String = "${ProjectRowTag(project)}.edit"

private const val PROJECT_DETAILS_MAX_LINES = 2
private const val MONOGRAM_CHARACTERS = 2
private const val MONOGRAM_FILL_ALPHA = 0.16f
private const val MONOGRAM_RIM_ALPHA = 0.34f
private val monogramSize = 36.dp