package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * A project in the list: its name, its task count, and its edit and delete actions.
 *
 * It takes a [ProjectSummary] rather than a [com.tasktracker.domain.Project] because the
 * summary is a *copy* - `Project` is mutable, and a row bound to one would keep changing
 * under itself when the store wrote to it. The summary is the domain's own answer to that,
 * so a view reading it is reading an immutable value by construction.
 *
 * ## The key prefix is drawn as a monogram, not as text
 *
 * `TT` in a tinted circle rather than a bare `TT` in the row. A project key is an
 * identifier, not a label, and on a list of six projects the labels are all similar-length
 * words while the keys are all two capitals - the monogram is the thing a user can pick out
 * of the corner of their eye, and it is the one part of a project row that is unique by
 * construction.
 *
 * ## Why the count is pluralised through a resource
 *
 * "1 task" and "2 tasks" is the smallest i18n correctness there is, and it is the one most
 * hand-rolled strings get wrong. [pluralStringResource] takes the count as a *quantity and*
 * a *format argument*, which is the whole point: a `count == 1` branch in Kotlin picks the
 * right English and is still wrong in every language that has more than two plural forms.
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
            .clickable(onClick = onOpen)
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
                    // The only archived string that exists states *why* a project was
                    // archived, which is the more useful half: a user finding this row wants
                    // to know its tasks are in the archive, not merely that it is closed.
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
        // No "open" button: the whole row is the open target, and a button labelled "Open"
        // next to a row that is also tappable says the row is not tappable. Deleting gets an
        // explicit buttons because these are actions on the row that are *not* opening it.
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

/**
 * The prefix, in a tinted circle.
 *
 * Greyscale when the project is archived, so an archived project is identifiable in a list
 * without reading its label. That is a third use of the same fact - the tint, the count,
 * and the label all agree - and the redundancy is the point: any one of them being missed
 * still leaves the row readable.
 */
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

@Preview
@Composable
private fun ProjectRowPreview() = ProjectsPreview(ThemeScheme.Dark) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        modifier = Modifier.width(PROJECT_ROW_PREVIEW_WIDTH),
    ) {
        ViewPreviewData.summaries().forEach { summary ->
            ProjectRowView(project = summary, onOpen = {}, onRemove = {})
        }
        ProjectRowView(
            project = ProjectSummary(ViewPreviewData.archivedProject, itemCount = 1),
            onOpen = {},
            onRemove = {},
        )
    }
}

const val ProjectRowTagPrefix = "projects.row"

/** Suffixed with the key prefix: unique by construction, unlike a name. */
fun ProjectRowTag(project: ProjectSummary): String = "$ProjectRowTagPrefix.${project.keyPrefix}"

fun ProjectRemoveTag(project: ProjectSummary): String = "${ProjectRowTag(project)}.remove"

fun ProjectEditTag(project: ProjectSummary): String = "${ProjectRowTag(project)}.edit"

private const val PROJECT_DETAILS_MAX_LINES = 2
private const val MONOGRAM_CHARACTERS = 2
private const val MONOGRAM_FILL_ALPHA = 0.16f
private const val MONOGRAM_RIM_ALPHA = 0.34f
private val monogramSize = 36.dp
private val PROJECT_ROW_PREVIEW_WIDTH = 360.dp
