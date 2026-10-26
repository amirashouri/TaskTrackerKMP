package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.TagID
import com.tasktracker.designsystem.TagTint
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttTagIcons
import com.tasktracker.designsystem.ttTagTint
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.TagIcon
import com.tasktracker.domain.TagRules
import com.tasktracker.resources.Res
import com.tasktracker.resources.work_item_tags_count
import com.tasktracker.resources.work_item_tags_empty
import com.tasktracker.resources.work_item_tags_label
import com.tasktracker.resources.work_item_tags_limit
import com.tasktracker.resources.work_item_tags_manage
import org.jetbrains.compose.resources.stringResource

const val WorkItemTagIconsTag = "workItem.tags"
const val WorkItemTagPickerTag = "workItem.tagPicker"
const val WorkItemManageTagsTag = "workItem.tagPicker.manage"

fun WorkItemTagOptionTag(tag: ProjectTag): String = "workItem.tagPicker.${tag.id.stringValue}"
fun WorkItemTagChipTag(tag: ProjectTag): String = "workItem.tagChip.${tag.id.stringValue}"

/** Resolves item tag IDs against catalog, filtering out deleted/missing tags. */
internal fun List<ProjectTag>.resolveFor(ids: List<TagID>): List<ProjectTag> =
    ids.mapNotNull { id -> firstOrNull { it.id == id } }

internal fun TagColor.tint(): TagTint = when (this) {
    TagColor.Red -> TagTint.Red
    TagColor.Orange -> TagTint.Orange
    TagColor.Amber -> TagTint.Amber
    TagColor.Green -> TagTint.Green
    TagColor.Teal -> TagTint.Teal
    TagColor.Blue -> TagTint.Blue
    TagColor.Indigo -> TagTint.Indigo
    TagColor.Purple -> TagTint.Purple
    TagColor.Pink -> TagTint.Pink
    TagColor.Gray -> TagTint.Gray
}

internal fun TagIcon.vector(): ImageVector = when (this) {
    TagIcon.Bug -> ttTagIcons.bug
    TagIcon.Mobile -> ttTagIcons.mobile
    TagIcon.Sparkle -> ttTagIcons.sparkle
    TagIcon.Bolt -> ttTagIcons.bolt
    TagIcon.Star -> ttTagIcons.star
    TagIcon.Flag -> ttTagIcons.flag
    TagIcon.Book -> ttTagIcons.book
    TagIcon.Bell -> ttTagIcons.bell
}

@Composable
internal fun TagGlyph(
    icon: TagIcon,
    color: TagColor,
    modifier: Modifier = Modifier,
    size: Dp = TAG_CHIP_SIZE,
    glyphSize: Dp = TAG_GLYPH_SIZE,
    contentDescription: String? = null,
) {
    val tint = ttTagTint(color.tint())
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(ttRadius.control))
            .background(tint.copy(alpha = TAG_FILL_ALPHA))
            .border(1.dp, tint.copy(alpha = TAG_RIM_ALPHA), RoundedCornerShape(ttRadius.control)),
        contentAlignment = Alignment.Center,
    ) {
        ttIcon(
            image = icon.vector(),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(glyphSize),
        )
    }
}

@Composable
internal fun TagGlyph(
    tag: ProjectTag,
    modifier: Modifier = Modifier,
    size: Dp = TAG_CHIP_SIZE,
    glyphSize: Dp = TAG_GLYPH_SIZE,
    contentDescription: String? = null,
) = TagGlyph(
    icon = tag.icon,
    color = tag.color,
    modifier = modifier,
    size = size,
    glyphSize = glyphSize,
    contentDescription = contentDescription,
)

@Composable
fun WorkItemTagIcons(
    tags: List<ProjectTag>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    Row(
        modifier = modifier.testTag(WorkItemTagIconsTag),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tag in tags) {
            TagGlyph(
                tag = tag,
                contentDescription = null,
                modifier = Modifier
                    .semantics { contentDescription = tag.name }
                    .testTag(WorkItemTagChipTag(tag)),
            )
        }
    }
}

/**
 * Interactive project tag picker supporting caps up to [TagRules.MAX_PER_ITEM]
 * and admin catalog management prompts.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WorkItemTagPicker(
    tags: List<ProjectTag>,
    selectedIDs: List<TagID>,
    onToggle: (TagID) -> Unit,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    onManageTags: (() -> Unit)? = null,
) {
    if (tags.isEmpty()) {
        if (!isAdmin || onManageTags == null) return
        TagSectionEmpty(onManageTags = onManageTags, modifier = modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxWidth().testTag(WorkItemTagPickerTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        TagSectionHeader(selectedCount = selectedIDs.size)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            for (tag in tags) {
                val selected = tag.id in selectedIDs
                val enabled = selected || selectedIDs.size < TagRules.MAX_PER_ITEM
                TagOption(
                    tag = tag,
                    selected = selected,
                    enabled = enabled,
                    onClick = { onToggle(tag.id) },
                )
            }
        }
        if (selectedIDs.size >= TagRules.MAX_PER_ITEM) {
            ttText(
                text = stringResource(Res.string.work_item_tags_limit),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
            )
        }
    }
}

@Composable
private fun TagSectionHeader(selectedCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = stringResource(Res.string.work_item_tags_label),
            style = ttTypography.captionEmphasised,
            color = ttLabelSecondary(),
            modifier = Modifier.weight(1f),
        )
        ttText(
            text = stringResource(
                Res.string.work_item_tags_count,
                selectedCount,
                TagRules.MAX_PER_ITEM,
            ),
            style = ttTypography.caption,
            color = ttLabelSecondary(),
        )
    }
}

@Composable
private fun TagSectionEmpty(
    onManageTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(WorkItemTagPickerTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        TagSectionHeader(selectedCount = 0)
        ttText(
            text = stringResource(Res.string.work_item_tags_empty),
            style = ttTypography.caption,
            color = ttLabelSecondary(),
        )
        ttGlassButton(
            label = stringResource(Res.string.work_item_tags_manage),
            onClick = onManageTags,
            modifier = Modifier.testTag(WorkItemManageTagsTag),
        )
    }
}

@Composable
private fun TagOption(
    tag: ProjectTag,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = ttTagTint(tag.color.tint())
    val shape = RoundedCornerShape(ttRadius.control)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    tint.copy(alpha = TAG_OPTION_SELECTED_FILL_ALPHA)
                } else {
                    ttSurface().copy(alpha = TAG_OPTION_FILL_ALPHA)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) tint.copy(alpha = TAG_OPTION_SELECTED_RIM_ALPHA) else ttLabelSecondary().copy(alpha = TAG_OPTION_RIM_ALPHA),
                shape = shape,
            )
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = ttSpacing.sm, vertical = ttSpacing.xs)
            .testTag(WorkItemTagOptionTag(tag)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagGlyph(tag = tag, size = TAG_OPTION_GLYPH_TILE_SIZE, glyphSize = TAG_OPTION_GLYPH_SIZE)
        ttText(
            text = tag.name,
            style = ttTypography.caption,
            color = if (selected) tint else ttLabel(),
        )
    }
}

private val TAG_CHIP_SIZE = 20.dp
private val TAG_GLYPH_SIZE = 12.dp
private val TAG_OPTION_GLYPH_TILE_SIZE = 22.dp
private val TAG_OPTION_GLYPH_SIZE = 13.dp

private const val TAG_FILL_ALPHA = 0.16f
private const val TAG_RIM_ALPHA = 0.38f
private const val TAG_OPTION_FILL_ALPHA = 0.35f
private const val TAG_OPTION_RIM_ALPHA = 0.30f
private const val TAG_OPTION_SELECTED_FILL_ALPHA = 0.20f
private const val TAG_OPTION_SELECTED_RIM_ALPHA = 0.70f