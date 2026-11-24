package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTDateField
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.core.UserID
import com.tasktracker.domain.deriveEpicStatus
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_back
import com.tasktracker.resources.common_save
import com.tasktracker.resources.epic_archive
import com.tasktracker.resources.epic_create
import com.tasktracker.resources.epic_details_label
import com.tasktracker.resources.epic_due_clear
import com.tasktracker.resources.epic_due_label
import com.tasktracker.resources.epic_editor_edit_title
import com.tasktracker.resources.epic_editor_new_title
import com.tasktracker.resources.epic_link_items
import com.tasktracker.resources.epic_subtasks_empty
import com.tasktracker.resources.epic_subtasks_label
import com.tasktracker.resources.epic_title_label
import com.tasktracker.resources.epic_unarchive
import com.tasktracker.resources.epic_unlink_item
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The epic editor: create or edit one story.
 *
 * ## Create and edit differ by one section, and that is the point
 *
 * A create has no saved epic, so it has no subtasks and therefore no progress bar and no
 * derived status. Those are not shown empty — they are absent until the epic exists, because
 * a zero-of-zero bar on a thing that does not exist yet claims a fact about it.
 *
 * The form is a page rather than a sheet: it is a routed destination, it carries a list of
 * tasks and two pickers, and a sheet tall enough to hold all of that is a page with a handle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpicEditorScreen(
    model: EpicEditorModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /** Directory callbacks for the owner picker; defaulted for previews and isolated tests. */
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
) {
    val scope = rememberCoroutineScope()
    var isItemPickerOpen by remember { mutableStateOf(false) }
    var isArchiveSheetOpen by remember { mutableStateOf(false) }

    val projectNames = remember(model.availableProjects) {
        model.availableProjects.associate { it.id to it.name }
    }
    val subtasks = model.subtasks

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ttSpacing.xxl)
            .padding(top = ttSpacing.screenGutterLarge, bottom = ttSpacing.xxxl)
            .testTag(EpicEditorScreenTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttGlassIconButton(
                image = ttActionIcons.chevronLeft,
                contentDescription = stringResource(Res.string.common_back),
                onClick = onBack,
                modifier = Modifier.testTag(EpicEditorBackTag),
            )
            ttText(
                text = if (model.isCreating) {
                    stringResource(Res.string.epic_editor_new_title)
                } else {
                    stringResource(Res.string.epic_editor_edit_title, model.title)
                },
                style = ttTypography.titleSmall,
                color = ttLabel(),
                modifier = Modifier.weight(1f),
            )
            if (model.canSave) {
                ttGlassButton(
                    label = stringResource(
                        if (model.isCreating) Res.string.epic_create else Res.string.common_save,
                    ),
                    onClick = onSave,
                    style = TTGlassButtonStyle(prominent = true),
                    modifier = Modifier.testTag(EpicEditorSaveTag),
                )
            }
        }

        model.errorMessage?.let { message ->
            ttBanner(message = message, onDismiss = model::dismissError)
        }

        ProjectsTextField(
            title = stringResource(Res.string.epic_title_label),
            value = model.title,
            onValueChange = model::onTitleChanged,
            fieldTag = EpicEditorTitleTag,
            imeAction = ImeAction.Next,
        )

        ProjectsTextField(
            title = stringResource(Res.string.epic_details_label),
            value = model.details,
            onValueChange = model::onDetailsChanged,
            fieldTag = EpicEditorDetailsTag,
            singleLine = false,
            minLines = 3,
            imeAction = ImeAction.Default,
        )

        Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
            TTDateField(
                label = stringResource(Res.string.epic_due_label),
                value = model.dueDate,
                onValueChange = { model.onDueDateChanged(it) },
                modifier = Modifier.testTag(EpicEditorDueTag),
            )
            if (model.dueDate != null) {
                ttGlassButton(
                    label = stringResource(Res.string.epic_due_clear),
                    onClick = { model.onDueDateChanged(null) },
                    modifier = Modifier.testTag(EpicEditorClearDueTag),
                )
            }
        }

        EpicOwnerPicker(
            ownerID = model.ownerID,
            onOwnerChanged = model::onOwnerChanged,
            searchMembers = searchMembers,
            resolveSelected = resolveSelected,
        )

        EpicProjectDropdown(
            projects = model.availableProjects,
            selected = model.projectIDs,
            onToggle = model::toggleProject,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(EpicEditorProjectChipsTag),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        ) {
            model.projectIDs.forEach { id ->
                SelectedProjectChip(name = projectNames[id] ?: id.stringValue.take(SHORT_ID_LENGTH))
            }
        }

        if (!model.isCreating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DIVIDER_HEIGHT)
                    .background(ttSeparator()),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ttText(
                    text = epicStatusLabel(deriveEpicStatus(subtasks)),
                    style = ttTypography.captionEmphasised,
                    color = ttAccent(),
                    modifier = Modifier.testTag(EpicEditorStatusTag),
                )
                ttText(
                    text = stringResource(Res.string.epic_subtasks_label),
                    style = ttTypography.captionEmphasised,
                    color = ttLabelSecondary(),
                )
                Box(modifier = Modifier.weight(1f))
                ttGlassButton(
                    label = stringResource(Res.string.epic_link_items),
                    onClick = {
                        scope.launch { model.loadCandidates() }
                        isItemPickerOpen = true
                    },
                    modifier = Modifier.testTag(EpicEditorLinkItemsTag),
                )
            }
            EpicProgressBar(
                done = subtasks.count { it.status.isTerminal },
                total = subtasks.size,
                modifier = Modifier.testTag(EpicEditorProgressTag),
            )
            if (subtasks.isEmpty()) {
                ttText(
                    text = stringResource(Res.string.epic_subtasks_empty),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().testTag(EpicEditorSubtasksTag),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                ) {
                    subtasks.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ttText(
                                text = item.key.rawValue,
                                style = ttTypography.captionEmphasised,
                                color = ttLabelSecondary(),
                            )
                            ttText(
                                text = item.title,
                                style = ttTypography.callout,
                                color = ttLabel(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.status.isTerminal) {
                                ttText(
                                    text = "\u2713",
                                    style = ttTypography.callout,
                                    color = ttAccent(),
                                )
                            }
                            // An annotation on the row, not the row's action: the inline close
                            // glyph keeps the unlink well from dominating the task title.
                            ttGlassIconButton(
                                image = ttActionIcons.close,
                                contentDescription = stringResource(Res.string.epic_unlink_item),
                                onClick = { scope.launch { model.unlink(item.id) } },
                                modifier = Modifier.testTag(EpicEditorUnlinkTag(item.id.stringValue)),
                                size = ttSpacing.xxxl,
                                iconSize = ttIconSize.close,
                            )
                        }
                        // Between links only: the line pairs each unlink with the row above it,
                        // without leaving a stray rule under the last item.
                        if (index < subtasks.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(DIVIDER_HEIGHT)
                                    .background(ttSeparator()),
                            )
                        }
                    }
                }
            }

            ttGlassButton(
                label = stringResource(
                    if (model.isArchived) Res.string.epic_unarchive else Res.string.epic_archive,
                ),
                onClick = {
                    if (model.isArchived) {
                        scope.launch { model.setArchived(isArchived = false, archiveSubtasks = true) }
                    } else {
                        isArchiveSheetOpen = true
                    }
                },
                modifier = Modifier.testTag(EpicEditorArchiveTag),
            )
        }
    }

    EpicItemPickerSheet(
        items = model.candidateItems,
        selectedEpicID = model.savedEpicID,
        visible = isItemPickerOpen,
        onToggle = { id, isSelected ->
            scope.launch {
                if (isSelected) model.unlink(id) else model.link(id)
            }
        },
        onDismissRequest = { isItemPickerOpen = false },
        onDismissed = { isItemPickerOpen = false },
    )

    EpicArchiveSheet(
        epicTitle = model.title,
        unfinishedCount = subtasks.count { !it.status.isTerminal },
        visible = isArchiveSheetOpen,
        onArchiveOnly = {
            isArchiveSheetOpen = false
            scope.launch { model.setArchived(isArchived = true, archiveSubtasks = false) }
        },
        onArchiveAll = {
            isArchiveSheetOpen = false
            scope.launch { model.setArchived(isArchived = true, archiveSubtasks = true) }
        },
        onDismissRequest = { isArchiveSheetOpen = false },
        onDismissed = { isArchiveSheetOpen = false },
    )
}

/** One selected project in the editor's chip row. */
@Composable
private fun SelectedProjectChip(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ttRadius.control))
            .background(ttSeparator().copy(alpha = CHIP_FILL_ALPHA))
            .padding(horizontal = ttSpacing.sm, vertical = ttSpacing.xxs)
            .testTag(EpicEditorProjectChipTag(name)),
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

const val EpicEditorScreenTag = "projects.epic.editor"
const val EpicEditorBackTag = "projects.epic.editor.back"
const val EpicEditorSaveTag = "projects.epic.editor.save"
const val EpicEditorTitleTag = "projects.epic.editor.title"
const val EpicEditorDetailsTag = "projects.epic.editor.details"
const val EpicEditorDueTag = "projects.epic.editor.due"
const val EpicEditorClearDueTag = "projects.epic.editor.due.clear"
const val EpicEditorPickProjectsTag = "projects.epic.editor.projects.pick"
const val EpicEditorProjectChipsTag = "projects.epic.editor.projects.chips"
const val EpicEditorStatusTag = "projects.epic.editor.status"
const val EpicEditorProgressTag = "projects.epic.editor.progress"
const val EpicEditorSubtasksTag = "projects.epic.editor.subtasks"
const val EpicEditorLinkItemsTag = "projects.epic.editor.subtasks.link"
const val EpicEditorArchiveTag = "projects.epic.editor.archive"

fun EpicEditorProjectChipTag(name: String): String = "projects.epic.editor.projects.chip.$name"
fun EpicEditorUnlinkTag(id: String): String = "projects.epic.editor.subtasks.unlink.$id"

private const val SHORT_ID_LENGTH = 8
private const val CHIP_FILL_ALPHA = 0.10f
private val DIVIDER_HEIGHT = 1.dp
