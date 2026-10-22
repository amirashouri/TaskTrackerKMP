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

/** The test tag on the icon-only strip a card or row draws. */
const val WorkItemTagIconsTag = "workItem.tags"

/** The test tag on the editor's tag picker. */
const val WorkItemTagPickerTag = "workItem.tagPicker"

/** The test tag on the admin-only "manage tags" affordance in an empty picker. */
const val WorkItemManageTagsTag = "workItem.tagPicker.manage"

/** The test tag on one option in the picker. */
fun WorkItemTagOptionTag(tag: ProjectTag): String =
    "workItem.tagPicker.${tag.id.stringValue}"

/** The test tag on one icon chip, on a card or a backlog row. */
fun WorkItemTagChipTag(tag: ProjectTag): String = "workItem.tagChip.${tag.id.stringValue}"

/**
 * Resolves an item's tag ids against the project's catalog, in the order the picker added them.
 *
 * Drops an id the catalog no longer defines. That is the whole delete story: removing a tag
 * from the project leaves its id on every item that carried it, and every reader silently
 * ignores what it cannot resolve rather than showing a blank chip or rewriting the rows.
 * One pass over the catalog per id — the catalog is at most [TagRules.MAX_PER_PROJECT] long
 * and the item at most five.
 */
internal fun List<ProjectTag>.resolveFor(ids: List<TagID>): List<ProjectTag> =
    ids.mapNotNull { id -> firstOrNull { it.id == id } }

/** The design-system tint for a domain colour. Exhaustive, so a new colour is a compile error. */
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

/** The drawn vector for a domain icon. Exhaustive for the same reason as [tint]. */
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

/**
 * One tag's glyph in a tinted tile: the whole visual vocabulary of a tag.
 *
 * A tile rather than a bare tinted vector because a lone 12dp glyph in ten colours is hard
 * to tell apart at a glance; the wash gives each one a silhouette. Alpha-derived from
 * [ttTagTint] so a new colour cannot arrive with a fill that belongs to a different tint,
 * the same pairing [com.tasktracker.designsystem.ttErrorFill] enforces for errors.
 */
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

/** The common case: a tag that already carries its colour and icon. */
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

/**
 * The icon-only strip a card or backlog row draws.
 *
 * Names are deliberately absent: the board is scanned, not read, and a card that spelled out
 * five tag names would push its own title off the card. The name is still announced — each
 * chip carries it as its content description — so the information is not lost to a screen
 * reader, only to the 12dp the card does not have.
 */
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
                // The strip is the only place the name is not drawn, so the chip's semantics
                // carry it. The glyph itself stays undescribed to avoid announcing it twice.
                contentDescription = null,
                modifier = Modifier
                    .semantics { contentDescription = tag.name }
                    .testTag(WorkItemTagChipTag(tag)),
            )
        }
    }
}

/**
 * The editor's tag section: every tag the project defines, ticked or not.
 *
 * Options rather than a dropdown because there is no dropdown dependency and because the set
 * is small by construction ([TagRules.MAX_PER_PROJECT]). At the five-tag cap the unselected
 * options dim and stop accepting taps — a visible answer, rather than a tap that vanishes.
 *
 * ## An empty catalog is a different answer for a member and for an admin
 *
 * A member sees nothing: an "assign tags" control with nothing to assign is a dead control,
 * and the editor already has enough of those. An admin sees the section with a way into the
 * project editor, because the empty catalog is theirs to fix — hiding it from them is hiding
 * the only path to a feature they administer. `onManageTags` is what makes that path real;
 * with no callback there is nowhere to send them, so the section stays hidden even for an
 * admin.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WorkItemTagPicker(
    tags: List<ProjectTag>,
    selectedIDs: List<TagID>,
    onToggle: (TagID) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the signed-in user administers the project's catalog. See the KDoc above. */
    isAdmin: Boolean = false,
    /** Opens the project editor, where an admin creates tags. `null` hides the affordance. */
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

/**
 * The label and the "n of five" count, shared by the picker and its empty state.
 *
 * One composable rather than two copies so an empty catalog cannot drift into a section
 * whose header says something the populated one does not.
 */
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

/**
 * The section an admin sees when the project defines no tags.
 *
 * It states the gap and offers the one action that closes it. Nothing is offered to a
 * member: they cannot edit the catalog, so naming it would be an explanation of a screen
 * they do not have.
 */
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

/** The 20dp tile a card draws. Small enough that five fit a 300dp card with room to spare. */
private val TAG_CHIP_SIZE = 20.dp

/** The glyph inside [TAG_CHIP_SIZE]. */
private val TAG_GLYPH_SIZE = 12.dp

/** The tile inside a picker option, where the name is also present. */
private val TAG_OPTION_GLYPH_TILE_SIZE = 22.dp

/** The glyph inside [TAG_OPTION_GLYPH_TILE_SIZE]. */
private val TAG_OPTION_GLYPH_SIZE = 13.dp

private const val TAG_FILL_ALPHA = 0.16f
private const val TAG_RIM_ALPHA = 0.38f
private const val TAG_OPTION_FILL_ALPHA = 0.35f
private const val TAG_OPTION_RIM_ALPHA = 0.30f
private const val TAG_OPTION_SELECTED_FILL_ALPHA = 0.20f
private const val TAG_OPTION_SELECTED_RIM_ALPHA = 0.70f
