package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttDateLabel
import com.tasktracker.designsystem.ttEntityIcons
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.epic_archive
import com.tasktracker.resources.epic_edit
import com.tasktracker.resources.epic_progress
import com.tasktracker.resources.epic_progress_empty
import com.tasktracker.resources.epic_unarchive
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/**
 * An epic card: the epic glyph, its project chips, the derived status, progress and due date.
 *
 * ## Deliberately unlike the other two cards
 *
 * [ProjectRowView] leads with a circular key monogram and a task count; a [WorkItemCard] leads
 * with a key and priority. This card leads with a **square glyph and a progress bar**, which is
 * the one thing an epic has that the other two do not. The shape difference is the
 * recognition: a circle, a card and a layered square are told apart before their text is read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpicRowView(
    row: EpicListRow,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val epic = row.epic
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
            .testTag(EpicRowTag(epic.id.toString())),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(EPIC_GLYPH_BADGE_SIZE)
                    .clip(RoundedCornerShape(ttRadius.control))
                    .background(ttAccent().copy(alpha = EPIC_GLYPH_FILL_ALPHA))
                    .testTag(EpicGlyphTag),
                contentAlignment = Alignment.Center,
            ) {
                ttIcon(
                    image = ttEntityIcons.epic,
                    contentDescription = null,
                    tint = ttAccent(),
                    modifier = Modifier.size(EPIC_GLYPH_SIZE),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
            ) {
                ttText(
                    text = epic.title,
                    style = ttTypography.callout,
                    color = ttLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                EpicStatusCaption(status = row.status)
            }
            ttGlassIconButton(
                image = ttActionIcons.edit,
                contentDescription = stringResource(Res.string.epic_edit),
                onClick = onEdit,
                modifier = Modifier.testTag(EpicEditTag(epic.id.toString())),
            )
            ttGlassIconButton(
                image = ttActionIcons.archive,
                contentDescription = stringResource(
                    if (epic.isArchived) Res.string.epic_unarchive else Res.string.epic_archive,
                ),
                onClick = onToggleArchived,
                modifier = Modifier.testTag(EpicArchiveTag(epic.id.toString())),
            )
        }

        if (row.projectNames.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag(EpicProjectChipsTag),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
            ) {
                for (name in row.projectNames) {
                    ProjectChip(name)
                }
            }
        }

        EpicProgressBar(done = row.doneCount, total = row.totalCount)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = if (row.totalCount == 0) {
                    stringResource(Res.string.epic_progress_empty)
                } else {
                    stringResource(Res.string.epic_progress, row.doneCount, row.totalCount)
                },
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
                modifier = Modifier.weight(1f).testTag(EpicProgressCountTag),
            )
            epic.dueDate?.let { due ->
                ttText(
                    text = ttDateLabel(sprintLocalDate(due, TimeZone.currentSystemDefault())),
                    style = ttTypography.captionSmall,
                    color = ttLabelSecondary(),
                    modifier = Modifier.testTag(EpicDueTag),
                )
            }
        }
    }
}

/** One project the epic spans, as a small bordered chip. */
@Composable
private fun ProjectChip(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ttRadius.control))
            .background(ttSeparator().copy(alpha = PROJECT_CHIP_FILL_ALPHA))
            .padding(horizontal = ttSpacing.sm, vertical = ttSpacing.xxs),
    ) {
        ttText(
            text = name,
            style = ttTypography.captionSmall,
            color = ttLabel(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

const val EpicRowTagPrefix = "projects.epic.row"
const val EpicGlyphTag = "projects.epic.glyph"
const val EpicProjectChipsTag = "projects.epic.chips"
const val EpicProgressCountTag = "projects.epic.count"
const val EpicDueTag = "projects.epic.due"

fun EpicRowTag(id: String): String = "$EpicRowTagPrefix.$id"
fun EpicEditTag(id: String): String = "${EpicRowTag(id)}.edit"
fun EpicArchiveTag(id: String): String = "${EpicRowTag(id)}.archive"

private val EPIC_GLYPH_BADGE_SIZE = 40.dp
private val EPIC_GLYPH_SIZE = 22.dp
private const val EPIC_GLYPH_FILL_ALPHA = 0.16f
private const val PROJECT_CHIP_FILL_ALPHA = 0.10f
