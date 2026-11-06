package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassFlowPicker
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttTagTint
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.StatusRules
import com.tasktracker.domain.TagColor
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_project_statuses_add
import com.tasktracker.resources.admin_project_statuses_body
import com.tasktracker.resources.admin_project_statuses_cancel
import com.tasktracker.resources.admin_project_statuses_category
import com.tasktracker.resources.admin_project_statuses_color
import com.tasktracker.resources.admin_project_statuses_duplicate
import com.tasktracker.resources.admin_project_statuses_edit
import com.tasktracker.resources.admin_project_statuses_empty
import com.tasktracker.resources.admin_project_statuses_max
import com.tasktracker.resources.admin_project_statuses_move_down
import com.tasktracker.resources.admin_project_statuses_move_up
import com.tasktracker.resources.admin_project_statuses_name
import com.tasktracker.resources.admin_project_statuses_name_required
import com.tasktracker.resources.admin_project_statuses_remove
import com.tasktracker.resources.admin_project_statuses_save
import com.tasktracker.resources.admin_project_statuses_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

const val AdminProjectStatusesSectionTag = "admin.projectEditor.statuses"
const val AdminProjectStatusNameFieldTag = "admin.projectEditor.statusName"
const val AdminProjectStatusAddTag = "admin.projectEditor.statusAdd"
const val AdminProjectStatusSaveTag = "admin.projectEditor.statusSave"
const val AdminProjectStatusCancelTag = "admin.projectEditor.statusCancel"
const val AdminProjectStatusErrorTag = "admin.projectEditor.statusError"
fun AdminProjectStatusRowTag(status: StatusDefinition): String =
    "admin.projectEditor.status.${status.id}"
fun AdminProjectStatusEditTag(status: StatusDefinition): String = "${AdminProjectStatusRowTag(status)}.edit"
fun AdminProjectStatusRemoveTag(status: StatusDefinition): String = "${AdminProjectStatusRowTag(status)}.remove"
fun AdminProjectStatusMoveUpTag(status: StatusDefinition): String = "${AdminProjectStatusRowTag(status)}.up"
fun AdminProjectStatusMoveDownTag(status: StatusDefinition): String = "${AdminProjectStatusRowTag(status)}.down"
fun AdminProjectStatusColorTag(color: TagColor): String =
    "admin.projectEditor.statusColor.${color.wireValue}"

/**
 * Admin workflow-stage catalog: lists the project's ordered stages with rename, reorder and
 * remove controls, plus a composer that doubles as the rename editor.
 *
 * The composer adds and edits in one place on purpose. A stage has three properties (name,
 * category, colour), and editing them in a second sheet would repeat every picker; selecting
 * a row loads it into the same form instead, with Save replacing Add.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProjectStatusesSection(
    statuses: List<StatusDefinition>,
    onAdd: (name: String, category: WorkItemStatus, color: TagColor) -> Boolean,
    onUpdate: (id: String, name: String, category: WorkItemStatus, color: TagColor) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
) {
    var draftName by remember { mutableStateOf("") }
    var draftCategory by remember { mutableStateOf(WorkItemStatus.Todo) }
    var draftColor by remember { mutableStateOf(TagColor.Blue) }
    var editingID by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }

    val editing = statuses.firstOrNull { it.id == editingID }
    val refusal = refusalFor(draftName, statuses, editingID)

    fun resetDraft() {
        draftName = ""
        draftCategory = WorkItemStatus.Todo
        draftColor = TagColor.Blue
        editingID = null
        attempted = false
    }

    Column(
        modifier = modifier.fillMaxWidth().testTag(AdminProjectStatusesSectionTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs)) {
            ttText(
                text = stringResource(Res.string.admin_project_statuses_title),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ttText(
                text = stringResource(Res.string.admin_project_statuses_body),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
        if (statuses.isEmpty()) {
            ttText(
                text = stringResource(Res.string.admin_project_statuses_empty),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        } else {
            for ((index, status) in statuses.withIndex()) {
                ExistingStatusRow(
                    status = status,
                    isEditing = status.id == editingID,
                    canMoveUp = index > 0 && !isBusy,
                    canMoveDown = index < statuses.lastIndex && !isBusy,
                    onEdit = {
                        editingID = status.id
                        draftName = status.name
                        draftCategory = status.category
                        draftColor = status.color
                        attempted = false
                    },
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                    onRemove = { onRemove(status.id) },
                )
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
                title = stringResource(Res.string.admin_project_statuses_name),
                value = draftName,
                onValueChange = {
                    draftName = it
                    attempted = false
                },
                fieldTag = AdminProjectStatusNameFieldTag,
            )
            ttText(
                text = stringResource(Res.string.admin_project_statuses_category),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            TTGlassFlowPicker(
                options = WorkItemStatus.entries,
                selection = draftCategory,
                onSelect = { draftCategory = it },
                title = { statusLabel(it) },
            )
            ttText(
                text = stringResource(Res.string.admin_project_statuses_color),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                for (color in TagColor.entries) {
                    TagColorSwatch(
                        color = color,
                        selected = color == draftColor,
                        onClick = { draftColor = color },
                        testTag = AdminProjectStatusColorTag(color),
                    )
                }
            }
            if (attempted && refusal != null) {
                ttText(
                    text = stringResource(refusal),
                    style = ttTypography.footnote,
                    color = ttError(),
                    modifier = Modifier.testTag(AdminProjectStatusErrorTag),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                if (editing != null) {
                    ttGlassButton(
                        label = stringResource(Res.string.admin_project_statuses_cancel),
                        onClick = { resetDraft() },
                        modifier = Modifier.testTag(AdminProjectStatusCancelTag),
                    )
                    ttGlassButton(
                        label = stringResource(Res.string.admin_project_statuses_save),
                        onClick = {
                            if (!isBusy && refusal == null) {
                                onUpdate(
                                    editing.id,
                                    StatusRules.normaliseName(draftName),
                                    draftCategory,
                                    draftColor,
                                )
                                resetDraft()
                            } else {
                                attempted = true
                            }
                        },
                        modifier = Modifier.testTag(AdminProjectStatusSaveTag),
                        style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                    )
                } else {
                    ttGlassButton(
                        label = stringResource(Res.string.admin_project_statuses_add),
                        onClick = {
                            if (!isBusy && refusal == null && onAdd(draftName, draftCategory, draftColor)) {
                                resetDraft()
                            } else {
                                attempted = true
                            }
                        },
                        modifier = Modifier.testTag(AdminProjectStatusAddTag),
                        style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExistingStatusRow(
    status: StatusDefinition,
    isEditing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ttGlassCard()
            .padding(horizontal = ttSpacing.sm, vertical = ttSpacing.xs)
            .testTag(AdminProjectStatusRowTag(status)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(STATUS_COLOUR_DOT_SIZE)
                .clip(CircleShape)
                .background(ttTagTint(status.color.tint())),
        )
        Column(modifier = Modifier.weight(1f)) {
            ttText(
                text = status.name.ifEmpty { statusLabel(status.category) },
                style = ttTypography.body,
                color = if (isEditing) ttAccent() else ttLabel(),
            )
            ttText(
                text = statusLabel(status.category),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
        ttGlassIconButton(
            image = ttActionIcons.chevronDown,
            contentDescription = stringResource(Res.string.admin_project_statuses_move_up, status.name),
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier.rotate(180f).testTag(AdminProjectStatusMoveUpTag(status)),
        )
        ttGlassIconButton(
            image = ttActionIcons.chevronDown,
            contentDescription = stringResource(Res.string.admin_project_statuses_move_down, status.name),
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.testTag(AdminProjectStatusMoveDownTag(status)),
        )
        ttGlassIconButton(
            image = ttActionIcons.edit,
            contentDescription = stringResource(Res.string.admin_project_statuses_edit, status.name),
            onClick = onEdit,
            modifier = Modifier.testTag(AdminProjectStatusEditTag(status)),
        )
        ttGlassIconButton(
            image = ttActionIcons.trash,
            contentDescription = stringResource(Res.string.admin_project_statuses_remove, status.name),
            onClick = onRemove,
            modifier = Modifier.testTag(AdminProjectStatusRemoveTag(status)),
        )
    }
}

/**
 * Evaluates [StatusRules] and returns the localized refusal, or null when the draft can be
 * applied. [editingID] is excluded so a stage may keep its own name while it is edited.
 */
private fun refusalFor(
    name: String,
    statuses: List<StatusDefinition>,
    editingID: String?,
): StringResource? {
    val normalised = StatusRules.normaliseName(name)
    return when {
        !StatusRules.isNameValid(name) -> Res.string.admin_project_statuses_name_required
        editingID == null && statuses.size >= StatusRules.MAX_PER_PROJECT ->
            Res.string.admin_project_statuses_max
        statuses.any { it.id != editingID && it.name.equals(normalised, ignoreCase = true) } ->
            Res.string.admin_project_statuses_duplicate
        else -> null
    }
}

private val STATUS_COLOUR_DOT_SIZE = 12.dp
