package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tasktracker.core.TagID
import com.tasktracker.core.randomTagID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttTagTint
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.TagIcon
import com.tasktracker.domain.TagRules
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_project_tags_add
import com.tasktracker.resources.admin_project_tags_body
import com.tasktracker.resources.admin_project_tags_color
import com.tasktracker.resources.admin_project_tags_duplicate
import com.tasktracker.resources.admin_project_tags_empty
import com.tasktracker.resources.admin_project_tags_icon
import com.tasktracker.resources.admin_project_tags_max
import com.tasktracker.resources.admin_project_tags_name
import com.tasktracker.resources.admin_project_tags_name_required
import com.tasktracker.resources.admin_project_tags_remove
import com.tasktracker.resources.admin_project_tags_title
import com.tasktracker.resources.tag_color_amber
import com.tasktracker.resources.tag_color_blue
import com.tasktracker.resources.tag_color_gray
import com.tasktracker.resources.tag_color_green
import com.tasktracker.resources.tag_color_indigo
import com.tasktracker.resources.tag_color_orange
import com.tasktracker.resources.tag_color_pink
import com.tasktracker.resources.tag_color_purple
import com.tasktracker.resources.tag_color_red
import com.tasktracker.resources.tag_color_teal
import com.tasktracker.resources.tag_icon_bell
import com.tasktracker.resources.tag_icon_bolt
import com.tasktracker.resources.tag_icon_book
import com.tasktracker.resources.tag_icon_bug
import com.tasktracker.resources.tag_icon_flag
import com.tasktracker.resources.tag_icon_mobile
import com.tasktracker.resources.tag_icon_sparkle
import com.tasktracker.resources.tag_icon_star
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

const val AdminProjectTagsSectionTag = "admin.projectEditor.tags"
fun AdminProjectTagRowTag(tag: ProjectTag): String = "admin.projectEditor.tag.${tag.id.stringValue}"
const val AdminProjectTagNameFieldTag = "admin.projectEditor.tagName"
const val AdminProjectTagAddTag = "admin.projectEditor.tagAdd"
const val AdminProjectTagErrorTag = "admin.projectEditor.tagError"
fun AdminProjectTagColorTag(color: TagColor): String = "admin.projectEditor.tagColor.${color.wireValue}"
fun AdminProjectTagIconTag(icon: TagIcon): String = "admin.projectEditor.tagIcon.${icon.wireValue}"

/**
 * Admin project tag catalog: displays configured project tags with delete controls
 * and provides a composer to add new tags with color and icon pickers.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProjectTagsSection(
    tags: List<ProjectTag>,
    onAdd: (name: String, color: TagColor, icon: TagIcon) -> Boolean,
    onRemove: (TagID) -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
) {
    var draftName by remember { mutableStateOf("") }
    var draftColor by remember { mutableStateOf(TagColor.Blue) }
    var draftIcon by remember { mutableStateOf(TagIcon.Bug) }
    var attempted by remember { mutableStateOf(false) }

    val refusal = refusalFor(draftName, tags)

    Column(
        modifier = modifier.fillMaxWidth().testTag(AdminProjectTagsSectionTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs)) {
            ttText(
                text = stringResource(Res.string.admin_project_tags_title),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ttText(
                text = stringResource(Res.string.admin_project_tags_body),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
        if (tags.isEmpty()) {
            ttText(
                text = stringResource(Res.string.admin_project_tags_empty),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        } else {
            for (tag in tags) {
                ExistingTagRow(tag = tag, onRemove = { onRemove(tag.id) })
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .ttGlassCard()
                .padding(ttSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            ProjectsTextField(
                title = stringResource(Res.string.admin_project_tags_name),
                value = draftName,
                onValueChange = {
                    draftName = it
                    attempted = false
                },
                modifier = Modifier.testTag(AdminProjectTagNameFieldTag),
            )
            ttText(
                text = stringResource(Res.string.admin_project_tags_color),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                for (color in TagColor.entries) {
                    ColorSwatch(
                        color = color,
                        selected = color == draftColor,
                        onClick = { draftColor = color },
                    )
                }
            }
            ttText(
                text = stringResource(Res.string.admin_project_tags_icon),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                for (icon in TagIcon.entries) {
                    IconChoice(
                        icon = icon,
                        color = draftColor,
                        selected = icon == draftIcon,
                        onClick = { draftIcon = icon },
                    )
                }
            }
            if (attempted && refusal != null) {
                ttText(
                    text = stringResource(refusal),
                    style = ttTypography.footnote,
                    color = ttError(),
                    modifier = Modifier.testTag(AdminProjectTagErrorTag),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.admin_project_tags_add),
                    onClick = {
                        if (!isBusy) {
                            val added = refusal == null && onAdd(draftName, draftColor, draftIcon)
                            if (added) {
                                draftName = ""
                                attempted = false
                            } else {
                                attempted = true
                            }
                        }
                    },
                    modifier = Modifier.testTag(AdminProjectTagAddTag),
                    style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                )
            }
        }
    }
}

@Composable
private fun ExistingTagRow(tag: ProjectTag, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ttGlassCard()
            .padding(horizontal = ttSpacing.sm, vertical = ttSpacing.xs)
            .testTag(AdminProjectTagRowTag(tag)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagGlyph(tag = tag, size = 26.dp, glyphSize = 15.dp)
        ttText(
            text = tag.name,
            style = ttTypography.body,
            color = ttLabel(),
            modifier = Modifier.weight(1f),
        )
        ttGlassIconButton(
            image = ttActionIcons.trash,
            contentDescription = stringResource(Res.string.admin_project_tags_remove, tag.name),
            onClick = onRemove,
        )
    }
}

@Composable
private fun ColorSwatch(color: TagColor, selected: Boolean, onClick: () -> Unit) {
    val tint = ttTagTint(color.tint())
    Box(
        modifier = Modifier
            .size(TAG_SWATCH_RING_SIZE)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, ttLabel(), CircleShape)
                } else {
                    Modifier
                },
            )
            .padding(TAG_SWATCH_RING_INSET)
            .clip(CircleShape)
            .background(tint)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(AdminProjectTagColorTag(color)),
    )
}

@Composable
private fun IconChoice(icon: TagIcon, color: TagColor, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(ttRadius.control)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    ttSurface().copy(alpha = TAG_ICON_SELECTED_FILL_ALPHA)
                } else {
                    ttSurface().copy(alpha = TAG_ICON_FILL_ALPHA)
                },
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ttTagTint(color.tint()) else ttLabelSecondary().copy(alpha = 0.25f),
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(ttSpacing.xs)
            .testTag(AdminProjectTagIconTag(icon)),
    ) {
        TagGlyph(icon = icon, color = color, size = TAG_ICON_TILE_SIZE, glyphSize = TAG_ICON_GLYPH_SIZE)
    }
}

/** Evaluates [TagRules] and returns localized error resource if draft is invalid, or null if accepted. */
private fun refusalFor(name: String, tags: List<ProjectTag>): StringResource? = when {
    !TagRules.isNameValid(name) -> Res.string.admin_project_tags_name_required
    tags.size >= TagRules.MAX_PER_PROJECT -> Res.string.admin_project_tags_max
    tags.any { it.name.equals(TagRules.normaliseName(name), ignoreCase = true) } ->
        Res.string.admin_project_tags_duplicate
    else -> null
}

private val TAG_SWATCH_RING_SIZE = 30.dp
private val TAG_SWATCH_RING_INSET = 3.dp
private val TAG_ICON_TILE_SIZE = 30.dp
private val TAG_ICON_GLYPH_SIZE = 17.dp
private const val TAG_ICON_FILL_ALPHA = 0.25f
private const val TAG_ICON_SELECTED_FILL_ALPHA = 0.40f