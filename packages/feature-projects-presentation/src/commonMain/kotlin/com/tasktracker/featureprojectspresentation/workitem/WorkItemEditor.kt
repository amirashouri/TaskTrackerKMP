package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.CommentID
import com.tasktracker.core.TagID
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.WorkItemKey
import com.tasktracker.designsystem.ConfirmPopup
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassFlowPicker
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Comment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.ProjectTag
import com.tasktracker.domain.Recurrence
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.PickedFile
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_discard
import com.tasktracker.resources.common_save
import com.tasktracker.resources.editor_discard_body
import com.tasktracker.resources.editor_discard_title
import com.tasktracker.resources.work_item_archive
import com.tasktracker.resources.work_item_back
import com.tasktracker.resources.work_item_details_label
import com.tasktracker.resources.work_item_due_clear
import com.tasktracker.resources.work_item_due_label
import com.tasktracker.resources.work_item_due_set
import com.tasktracker.resources.work_item_editor_edit_title
import com.tasktracker.resources.work_item_editor_new_title
import com.tasktracker.resources.work_item_key_read_only
import com.tasktracker.resources.work_item_priority_label
import com.tasktracker.resources.work_item_status_label
import com.tasktracker.resources.work_item_story_points_label
import com.tasktracker.resources.work_item_title_label
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Full-screen work item editor for creating or editing tasks.
 */
@Composable
fun WorkItemEditorScreen(
    title: String,
    details: String,
    storyPointsText: String,
    status: WorkItemStatus,
    priority: Priority,
    assigneeID: UserID?,
    currentUserID: UserID?,
    currentUserName: String?,
    otherAssigneeLabel: String?,
    hasDueDate: Boolean,
    recurrence: Recurrence?,
    repeatAnchor: Instant?,
    isCreating: Boolean,
    issuedKey: WorkItemKey?,
    selectableStatuses: List<WorkItemStatus>,
    isFiledAway: Boolean,
    canSave: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onTitleChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onStoryPointsChanged: (String) -> Unit,
    onStatusChanged: (WorkItemStatus) -> Unit,
    onPriorityChanged: (Priority) -> Unit,
    onAssigneeChanged: (UserID?) -> Unit,
    onToggleDueDate: (Boolean) -> Unit,
    onRecurrenceChanged: (Recurrence?) -> Unit,
    onFileAway: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    attachments: List<Attachment> = emptyList(),
    isAttaching: Boolean = false,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (AttachmentID) -> Unit = {},
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    projectTags: List<ProjectTag> = emptyList(),
    selectedTagIDs: List<TagID> = emptyList(),
    onTagToggled: (TagID) -> Unit = {},
) {
    WorkItemEditorForm(
        title = title,
        details = details,
        storyPointsText = storyPointsText,
        status = status,
        priority = priority,
        assigneeID = assigneeID,
        currentUserID = currentUserID,
        currentUserName = currentUserName,
        otherAssigneeLabel = otherAssigneeLabel,
        hasDueDate = hasDueDate,
        recurrence = recurrence,
        repeatAnchor = repeatAnchor,
        isCreating = isCreating,
        issuedKey = issuedKey,
        selectableStatuses = selectableStatuses,
        isFiledAway = isFiledAway,
        canSave = canSave,
        isBusy = isBusy,
        errorMessage = errorMessage,
        onTitleChanged = onTitleChanged,
        onDetailsChanged = onDetailsChanged,
        onStoryPointsChanged = onStoryPointsChanged,
        onStatusChanged = onStatusChanged,
        onPriorityChanged = onPriorityChanged,
        onAssigneeChanged = onAssigneeChanged,
        onToggleDueDate = onToggleDueDate,
        onRecurrenceChanged = onRecurrenceChanged,
        onFileAway = onFileAway,
        onSave = onSave,
        onCancel = onCancel,
        onDismissError = onDismissError,
        projectTags = projectTags,
        selectedTagIDs = selectedTagIDs,
        onTagToggled = onTagToggled,
        modifier = modifier
            .fillMaxSize()
            .padding(ttSpacing.screenGutterLarge)
            .verticalScroll(rememberScrollState())
            .padding(bottom = LocalTabBarScrollUnderInset.current)
            .testTag(WorkItemEditorScreenTag),
    )
}

/** Modal bottom sheet wrapper for work item editing. */
@Composable
fun WorkItemEditorSheet(
    title: String,
    details: String,
    storyPointsText: String,
    status: WorkItemStatus,
    priority: Priority,
    assigneeID: UserID?,
    currentUserID: UserID?,
    currentUserName: String?,
    otherAssigneeLabel: String?,
    hasDueDate: Boolean,
    recurrence: Recurrence?,
    repeatAnchor: Instant?,
    isCreating: Boolean,
    issuedKey: WorkItemKey?,
    selectableStatuses: List<WorkItemStatus>,
    isFiledAway: Boolean,
    canSave: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onTitleChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onStoryPointsChanged: (String) -> Unit,
    onStatusChanged: (WorkItemStatus) -> Unit,
    onPriorityChanged: (Priority) -> Unit,
    onAssigneeChanged: (UserID?) -> Unit,
    onToggleDueDate: (Boolean) -> Unit,
    onRecurrenceChanged: (Recurrence?) -> Unit,
    onFileAway: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    blockers: List<WorkItem> = emptyList(),
    onOpenBlocker: (WorkItemID) -> Unit = {},
    onRemoveBlocker: (WorkItemID) -> Unit = {},
    onAddBlockerClick: () -> Unit = {},
    comments: List<Comment>? = null,
    commentAuthorNames: Map<UserID, String> = emptyMap(),
    commentDraft: String = "",
    onCommentDraftChanged: (String) -> Unit = {},
    commentReplyTo: CommentID? = null,
    onCommentReply: (CommentID) -> Unit = {},
    onCommentCancelReply: () -> Unit = {},
    onCommentSubmit: () -> Unit = {},
    attachments: List<Attachment> = emptyList(),
    isAttaching: Boolean = false,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (AttachmentID) -> Unit = {},
    attachmentSyncState: ((Attachment) -> AttachmentSyncState)? = null,
    onOpenAttachment: ((AttachmentID) -> Unit)? = null,
    attachmentOpening: ((Attachment) -> Boolean)? = null,
    pendingAttachments: List<PickedFile> = emptyList(),
    onRemovePending: (Int) -> Unit = {},
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    projectTags: List<ProjectTag> = emptyList(),
    selectedTagIDs: List<TagID> = emptyList(),
    onTagToggled: (TagID) -> Unit = {},
    isAdmin: Boolean = false,
    onManageTags: (() -> Unit)? = null,
) {
    ProjectsEditorFullScreen(
        title = title,
        details = details,
        storyPointsText = storyPointsText,
        status = status,
        priority = priority,
        assigneeID = assigneeID,
        currentUserID = currentUserID,
        currentUserName = currentUserName,
        otherAssigneeLabel = otherAssigneeLabel,
        hasDueDate = hasDueDate,
        recurrence = recurrence,
        repeatAnchor = repeatAnchor,
        isCreating = isCreating,
        issuedKey = issuedKey,
        selectableStatuses = selectableStatuses,
        isFiledAway = isFiledAway,
        canSave = canSave,
        isBusy = isBusy,
        errorMessage = errorMessage,
        onTitleChanged = onTitleChanged,
        onDetailsChanged = onDetailsChanged,
        onStoryPointsChanged = onStoryPointsChanged,
        onStatusChanged = onStatusChanged,
        onPriorityChanged = onPriorityChanged,
        onAssigneeChanged = onAssigneeChanged,
        onToggleDueDate = onToggleDueDate,
        onRecurrenceChanged = onRecurrenceChanged,
        onFileAway = onFileAway,
        onSave = onSave,
        onCancel = onCancel,
        onDismissError = onDismissError,
        hasUnsavedChanges = hasUnsavedChanges,
        onBack = onBack,
        blockers = blockers,
        onOpenBlocker = onOpenBlocker,
        onRemoveBlocker = onRemoveBlocker,
        onAddBlockerClick = onAddBlockerClick,
        comments = comments,
        commentAuthorNames = commentAuthorNames,
        commentDraft = commentDraft,
        onCommentDraftChanged = onCommentDraftChanged,
        commentReplyTo = commentReplyTo,
        onCommentReply = onCommentReply,
        onCommentCancelReply = onCommentCancelReply,
        onCommentSubmit = onCommentSubmit,
        attachments = attachments,
        isAttaching = isAttaching,
        onAttachClick = onAttachClick,
        onRemoveAttachment = onRemoveAttachment,
        attachmentSyncState = attachmentSyncState,
        onOpenAttachment = onOpenAttachment,
        attachmentOpening = attachmentOpening,
        pendingAttachments = pendingAttachments,
        onRemovePending = onRemovePending,
        projectTags = projectTags,
        selectedTagIDs = selectedTagIDs,
        onTagToggled = onTagToggled,
        isAdmin = isAdmin,
        onManageTags = onManageTags,
        searchMembers = searchMembers,
        resolveSelected = resolveSelected,
        modifier = modifier
            .fillMaxSize()
            .testTag(WorkItemEditorFullScreenTag),
    )
}

/** Scrollable work item form with pinned bottom action bar. */
@Composable
private fun ProjectsEditorFullScreen(
    title: String,
    details: String,
    storyPointsText: String,
    status: WorkItemStatus,
    priority: Priority,
    assigneeID: UserID?,
    currentUserID: UserID?,
    currentUserName: String?,
    otherAssigneeLabel: String?,
    hasDueDate: Boolean,
    recurrence: Recurrence?,
    repeatAnchor: Instant?,
    isCreating: Boolean,
    issuedKey: WorkItemKey?,
    selectableStatuses: List<WorkItemStatus>,
    isFiledAway: Boolean,
    canSave: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onTitleChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onStoryPointsChanged: (String) -> Unit,
    onStatusChanged: (WorkItemStatus) -> Unit,
    onPriorityChanged: (Priority) -> Unit,
    onAssigneeChanged: (UserID?) -> Unit,
    onToggleDueDate: (Boolean) -> Unit,
    onRecurrenceChanged: (Recurrence?) -> Unit,
    onFileAway: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    blockers: List<WorkItem> = emptyList(),
    onOpenBlocker: (WorkItemID) -> Unit = {},
    onRemoveBlocker: (WorkItemID) -> Unit = {},
    onAddBlockerClick: () -> Unit = {},
    comments: List<Comment>? = null,
    commentAuthorNames: Map<UserID, String> = emptyMap(),
    commentDraft: String = "",
    onCommentDraftChanged: (String) -> Unit = {},
    commentReplyTo: CommentID? = null,
    onCommentReply: (CommentID) -> Unit = {},
    onCommentCancelReply: () -> Unit = {},
    onCommentSubmit: () -> Unit = {},
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    attachments: List<Attachment> = emptyList(),
    isAttaching: Boolean = false,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (AttachmentID) -> Unit = {},
    attachmentSyncState: ((Attachment) -> AttachmentSyncState)? = null,
    onOpenAttachment: ((AttachmentID) -> Unit)? = null,
    attachmentOpening: ((Attachment) -> Boolean)? = null,
    pendingAttachments: List<PickedFile> = emptyList(),
    onRemovePending: (Int) -> Unit = {},
    projectTags: List<ProjectTag> = emptyList(),
    selectedTagIDs: List<TagID> = emptyList(),
    onTagToggled: (TagID) -> Unit = {},
    isAdmin: Boolean = false,
    onManageTags: (() -> Unit)? = null,
) {
    val tabBarInset = LocalTabBarScrollUnderInset.current
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ttCanvas().copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ttSpacing.screenGutterLarge, vertical = ttSpacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.fieldStack),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    ttGlassIconButton(
                        image = ttActionIcons.chevronLeft,
                        contentDescription = stringResource(Res.string.work_item_back),
                        onClick = onBack,
                        modifier = Modifier.testTag(WorkItemBackTag),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
                ) {
                    ttText(
                        text = if (isCreating) {
                            stringResource(Res.string.work_item_editor_new_title)
                        } else {
                            stringResource(
                                Res.string.work_item_editor_edit_title,
                                issuedKey?.toString().orEmpty(),
                            )
                        },
                        style = ttTypography.title,
                        color = ttLabel(),
                    )
                    errorMessage?.let { message ->
                        ttBanner(message = message, onDismiss = onDismissError)
                    }
                    issuedKey?.let { key ->
                        ttText(
                            text = stringResource(Res.string.work_item_key_read_only, key.toString()),
                            style = ttTypography.footnote,
                            color = ttLabelSecondary(),
                        )
                    }
                }
            }
            ProjectsTextField(
                title = stringResource(Res.string.work_item_title_label),
                value = title,
                onValueChange = onTitleChanged,
                imeAction = ImeAction.Next,
            )
            ProjectsTextField(
                title = stringResource(Res.string.work_item_details_label),
                value = details,
                onValueChange = onDetailsChanged,
                singleLine = false,
                minLines = DETAILS_MIN_LINES,
                imeAction = ImeAction.Next,
            )
            ProjectsTextField(
                title = stringResource(Res.string.work_item_story_points_label),
                value = storyPointsText,
                onValueChange = onStoryPointsChanged,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            )
            if (selectableStatuses.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
                    ttText(
                        text = stringResource(Res.string.work_item_status_label),
                        style = ttTypography.captionEmphasised,
                        color = ttLabelSecondary(),
                    )
                    TTGlassFlowPicker(
                        options = selectableStatuses,
                        selection = status,
                        onSelect = onStatusChanged,
                        title = { statusLabel(it) },
                        modifier = Modifier.testTag(WorkItemStatusPickerTag),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
                ttText(
                    text = stringResource(Res.string.work_item_priority_label),
                    style = ttTypography.captionEmphasised,
                    color = ttLabelSecondary(),
                )
                TTGlassFlowPicker(
                    options = Priority.entries,
                    selection = priority,
                    onSelect = onPriorityChanged,
                    title = { priorityLabel(it) },
                    modifier = Modifier.testTag(WorkItemPriorityPickerTag),
                )
            }
            WorkItemAssigneeRow(
                assigneeID = assigneeID,
                onAssigneeChanged = onAssigneeChanged,
                searchMembers = searchMembers,
                resolveSelected = resolveSelected,
            )
            WorkItemTagPicker(
                tags = projectTags,
                selectedIDs = selectedTagIDs,
                onToggle = onTagToggled,
                isAdmin = isAdmin,
                onManageTags = onManageTags,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                ttText(
                    text = stringResource(Res.string.work_item_due_label),
                    style = ttTypography.captionEmphasised,
                    color = ttLabelSecondary(),
                    modifier = Modifier.weight(1f),
                )
                ttGlassButton(
                    label = if (hasDueDate) {
                        stringResource(Res.string.work_item_due_clear)
                    } else {
                        stringResource(Res.string.work_item_due_set)
                    },
                    onClick = { onToggleDueDate(!hasDueDate) },
                    modifier = Modifier.testTag(WorkItemDueToggleTag),
                )
            }
            WorkItemRecurrenceRow(
                recurrence = recurrence,
                onRecurrenceChanged = onRecurrenceChanged,
                anchorDate = repeatAnchor,
            )
            WorkItemDependencyPills(
                blockers = blockers,
                onOpenBlocker = onOpenBlocker,
                onRemoveBlocker = onRemoveBlocker,
                onAddClick = onAddBlockerClick,
            )
            comments?.let { thread ->
                if (!isCreating) {
                    CommentThread(
                        comments = thread,
                        authorNames = commentAuthorNames,
                        canComment = currentUserID != null,
                        draft = commentDraft,
                        replyTo = commentReplyTo,
                        onDraftChanged = onCommentDraftChanged,
                        onReply = onCommentReply,
                        onCancelReply = onCommentCancelReply,
                        onSubmit = onCommentSubmit,
                    )
                }
            }
            AttachmentList(
                attachments = attachments,
                onAttachClick = onAttachClick,
                onRemove = onRemoveAttachment,
                syncStateOf = attachmentSyncState,
                onOpen = onOpenAttachment,
                isOpening = attachmentOpening,
                pendingAttachments = pendingAttachments,
                onRemovePending = onRemovePending,
            )
            Spacer(Modifier.height(40.dp))
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = ttSpacing.screenGutterLarge)
                .padding(bottom = tabBarInset + ttSpacing.md)
                .ttGlassCard()
                .background(Color.Transparent)
                .pointerInput(Unit) { }
                .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isFiledAway) {
                ttGlassButton(
                    label = stringResource(Res.string.work_item_archive),
                    onClick = { if (!isBusy) onFileAway() },
                    modifier = Modifier.testTag(WorkItemFileAwayTag),
                    style = TTGlassButtonStyle(tint = ttError(), prominent = true),
                )
            }
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = {
                    if (hasUnsavedChanges) showDiscardConfirmation = true else onCancel()
                },
                modifier = Modifier.testTag(WorkItemCancelTag),
            )
            ttGlassButton(
                label = stringResource(Res.string.common_save),
                onClick = { if (canSave && !isBusy) onSave() },
                modifier = Modifier.testTag(WorkItemSaveTag),
                style = if (canSave && !isBusy) {
                    TTGlassButtonStyle(tint = ttAccent(), prominent = true)
                } else {
                    TTGlassButtonStyle()
                },
            )
        }

        if (showDiscardConfirmation) {
            ConfirmPopup(
                title = stringResource(Res.string.editor_discard_title),
                body = stringResource(Res.string.editor_discard_body),
                cancelLabel = stringResource(Res.string.common_cancel),
                confirmLabel = stringResource(Res.string.common_discard),
                onCancel = { showDiscardConfirmation = false },
                onConfirm = {
                    showDiscardConfirmation = false
                    onCancel()
                },
                confirmTint = ttError(),
                tag = WorkItemEditorDiscardTag,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun WorkItemEditorForm(
    title: String,
    details: String,
    storyPointsText: String,
    status: WorkItemStatus,
    priority: Priority,
    assigneeID: UserID?,
    currentUserID: UserID?,
    currentUserName: String?,
    otherAssigneeLabel: String?,
    hasDueDate: Boolean,
    recurrence: Recurrence?,
    repeatAnchor: Instant?,
    isCreating: Boolean,
    issuedKey: WorkItemKey?,
    selectableStatuses: List<WorkItemStatus>,
    isFiledAway: Boolean,
    canSave: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    onTitleChanged: (String) -> Unit,
    onDetailsChanged: (String) -> Unit,
    onStoryPointsChanged: (String) -> Unit,
    onStatusChanged: (WorkItemStatus) -> Unit,
    attachments: List<Attachment> = emptyList(),
    isAttaching: Boolean = false,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (AttachmentID) -> Unit = {},
    onPriorityChanged: (Priority) -> Unit,
    onAssigneeChanged: (UserID?) -> Unit,
    onToggleDueDate: (Boolean) -> Unit,
    onRecurrenceChanged: (Recurrence?) -> Unit,
    onFileAway: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    blockers: List<WorkItem> = emptyList(),
    onOpenBlocker: (WorkItemID) -> Unit = {},
    onRemoveBlocker: (WorkItemID) -> Unit = {},
    onAddBlockerClick: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    comments: List<Comment>? = null,
    commentAuthorNames: Map<UserID, String> = emptyMap(),
    commentDraft: String = "",
    onCommentDraftChanged: (String) -> Unit = {},
    commentReplyTo: CommentID? = null,
    onCommentReply: (CommentID) -> Unit = {},
    onCommentCancelReply: () -> Unit = {},
    onCommentSubmit: () -> Unit = {},
    projectTags: List<ProjectTag> = emptyList(),
    selectedTagIDs: List<TagID> = emptyList(),
    onTagToggled: (TagID) -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                ttGlassIconButton(
                    image = ttActionIcons.chevronLeft,
                    contentDescription = stringResource(Res.string.work_item_back),
                    onClick = onBack,
                    modifier = Modifier.testTag(WorkItemBackTag),
                )
            }
            ttText(
                text = if (isCreating) {
                    stringResource(Res.string.work_item_editor_new_title)
                } else {
                    stringResource(Res.string.work_item_editor_edit_title, issuedKey?.toString().orEmpty())
                },
                style = ttTypography.title,
                color = ttLabel(),
                modifier = Modifier.weight(1f),
            )
        }
        errorMessage?.let { message ->
            ttBanner(message = message, onDismiss = onDismissError)
        }
        issuedKey?.let { key ->
            ttText(
                text = stringResource(Res.string.work_item_key_read_only, key.toString()),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
        ProjectsTextField(
            title = stringResource(Res.string.work_item_title_label),
            value = title,
            onValueChange = onTitleChanged,
            imeAction = ImeAction.Next,
        )
        ProjectsTextField(
            title = stringResource(Res.string.work_item_details_label),
            value = details,
            onValueChange = onDetailsChanged,
            singleLine = false,
            minLines = DETAILS_MIN_LINES,
            imeAction = ImeAction.Next,
        )
        ProjectsTextField(
            title = stringResource(Res.string.work_item_story_points_label),
            value = storyPointsText,
            onValueChange = onStoryPointsChanged,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        )
        if (selectableStatuses.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
                ttText(
                    text = stringResource(Res.string.work_item_status_label),
                    style = ttTypography.captionEmphasised,
                    color = ttLabelSecondary(),
                )
                TTGlassFlowPicker(
                    options = selectableStatuses,
                    selection = status,
                    onSelect = onStatusChanged,
                    title = { statusLabel(it) },
                    modifier = Modifier.testTag(WorkItemStatusPickerTag),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
            ttText(
                text = stringResource(Res.string.work_item_priority_label),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            TTGlassFlowPicker(
                options = Priority.entries,
                selection = priority,
                onSelect = onPriorityChanged,
                title = { priorityLabel(it) },
                modifier = Modifier.testTag(WorkItemPriorityPickerTag),
            )
        }
        WorkItemAssigneeRow(
            assigneeID = assigneeID,
            onAssigneeChanged = onAssigneeChanged,
        )
        WorkItemTagPicker(
            tags = projectTags,
            selectedIDs = selectedTagIDs,
            onToggle = onTagToggled,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            ttText(
                text = stringResource(Res.string.work_item_due_label),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
                modifier = Modifier.weight(1f),
            )
            ttGlassButton(
                label = if (hasDueDate) {
                    stringResource(Res.string.work_item_due_clear)
                } else {
                    stringResource(Res.string.work_item_due_set)
                },
                onClick = { onToggleDueDate(!hasDueDate) },
                modifier = Modifier.testTag(WorkItemDueToggleTag),
            )
        }
        WorkItemRecurrenceRow(
            recurrence = recurrence,
            onRecurrenceChanged = onRecurrenceChanged,
            anchorDate = repeatAnchor,
        )
        WorkItemDependencyPills(
            blockers = blockers,
            onOpenBlocker = onOpenBlocker,
            onRemoveBlocker = onRemoveBlocker,
            onAddClick = onAddBlockerClick,
        )
        comments?.let { thread ->
            if (!isCreating) {
                CommentThread(
                    comments = thread,
                    authorNames = commentAuthorNames,
                    canComment = currentUserID != null,
                    draft = commentDraft,
                    replyTo = commentReplyTo,
                    onDraftChanged = onCommentDraftChanged,
                    onReply = onCommentReply,
                    onCancelReply = onCommentCancelReply,
                    onSubmit = onCommentSubmit,
                )
            }
        }
        AttachmentList(
            attachments = attachments,
            onAttachClick = onAttachClick,
            onRemove = onRemoveAttachment,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md, Alignment.End),
        ) {
            if (!isFiledAway) {
                ttGlassButton(
                    label = stringResource(Res.string.work_item_archive),
                    onClick = { if (!isBusy) onFileAway() },
                    modifier = Modifier.testTag(WorkItemFileAwayTag),
                    style = TTGlassButtonStyle(tint = ttError(), prominent = true),
                )
            }
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onCancel,
                modifier = Modifier.testTag(WorkItemCancelTag),
            )
            ttGlassButton(
                label = stringResource(Res.string.common_save),
                onClick = { if (canSave && !isBusy) onSave() },
                modifier = Modifier.testTag(WorkItemSaveTag),
                style = if (canSave && !isBusy) {
                    TTGlassButtonStyle(tint = ttAccent(), prominent = true)
                } else {
                    TTGlassButtonStyle()
                },
            )
        }
    }
}

const val WorkItemEditorScreenTag = "workItem.editor.screen"
const val WorkItemEditorSheetTag = "workItem.editor.sheet"
const val WorkItemEditorFullScreenTag = "workItem.editor.fullScreen"
const val WorkItemBackTag = "workItem.editor.back"
const val WorkItemStatusPickerTag = "workItem.editor.status"
const val WorkItemPriorityPickerTag = "workItem.editor.priority"
const val WorkItemDueToggleTag = "workItem.editor.dueToggle"
const val WorkItemFileAwayTag = "workItem.editor.fileAway"
const val WorkItemSaveTag = "workItem.editor.save"
const val WorkItemCancelTag = "workItem.editor.cancel"
const val WorkItemEditorDiscardTag = "workItem.editor.discard"

private const val DETAILS_MIN_LINES = 3