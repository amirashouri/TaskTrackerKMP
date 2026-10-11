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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttAvatarSize
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttInitialsAvatar
import com.tasktracker.designsystem.ttInitialsFor
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.project_member_add_me
import com.tasktracker.resources.project_member_me
import com.tasktracker.resources.project_member_remove
import com.tasktracker.resources.project_members_invite_label
import com.tasktracker.resources.project_members_invite_placeholder
import com.tasktracker.resources.project_members_invite_queued
import com.tasktracker.resources.project_members_invite_send
import com.tasktracker.resources.project_members_label
import com.tasktracker.resources.project_members_none
import org.jetbrains.compose.resources.stringResource

/**
 * Who is on this project, as chips, plus the two ways to add somebody.
 *
 * ## Why chips and not full-width rows
 *
 * The first version drew one glass slab per member down the whole sheet width, which is the
 * shape a *list* uses — it reads as six separate panels to scan, and it spent most of the
 * sheet on three names. [WorkItemDependencyPills] already settled this shape for small
 * removable sets: a `FlowRow` of glass pills that wraps, so three members cost one row and
 * the form keeps its height for the fields. The pill is the same `clip` + [ttGlassCard]
 * binding, so this is the house answer rather than a second one.
 *
 * ## Why the disc, and why an unnamed member has none
 *
 * [ttInitialsAvatar] is the one way this app draws a person as a shape, and it is what makes
 * a pill read as a *person* rather than as a tag. A member the directory cannot name gets
 * the id and no disc: the disc's whole job is initials, and the first four characters of a
 * uuid are noise in a 24dp circle. The id stays visible and removable anyway — the same
 * honest fallback the assignee picker uses — because vanishing from the list is worse than
 * being ugly in it.
 *
 * ## Why "Add me" is a button on the label row
 *
 * It is one action, not a member, and [WorkItemDependencyPills] already puts its add affordance
 * on the label row for the same reason: a lone full-width accent row under the list looks
 * like a link somebody dropped into the form, and it is the only thing in the sheet that is
 * neither a field nor a chip. A button beside the caption reads as the section's action.
 *
 * ## Why the invite field lives here
 *
 * An invitation is how you add somebody who is not yet in the directory, so the field and
 * the member chips are one topic. Edit-mode only, because an invitation names a project id
 * and a project being created has none until save has run — [NewProjectSheet] hides the
 * whole section's invite half for that reason, and the model refuses it structurally too.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ProjectMembersSection(
    selectedMemberIDs: List<UserID>,
    onMemberIDsChanged: (List<UserID>) -> Unit,
    modifier: Modifier = Modifier,
    currentUserID: UserID? = null,
    currentUserName: String? = null,
    memberNames: Map<UserID, String> = emptyMap(),
    isCreating: Boolean = true,
    isBusy: Boolean = false,
    inviteEmail: String = "",
    onInviteEmailChanged: (String) -> Unit = {},
    onSendInvite: () -> Unit = {},
    inviteQueuedEmail: String? = null,
) {
    val meLabel = currentUserName ?: stringResource(Res.string.project_member_me)

    Row(
        modifier = Modifier.fillMaxWidth().testTag(ProjectMembersSectionTag),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = stringResource(Res.string.project_members_label),
            style = ttTypography.captionEmphasised,
            color = ttLabelSecondary(),
            modifier = Modifier.weight(1f),
        )
        if (currentUserID != null && currentUserID !in selectedMemberIDs) {
            ttGlassButton(
                label = stringResource(Res.string.project_member_add_me, meLabel),
                onClick = { onMemberIDsChanged(selectedMemberIDs + currentUserID) },
                modifier = Modifier.testTag(ProjectMemberAddMeTag),
                style = TTGlassButtonStyle(tint = ttAccent()),
            )
        }
    }
    if (selectedMemberIDs.isEmpty()) {
        ttText(
            text = stringResource(Res.string.project_members_none),
            style = ttTypography.caption,
            color = ttLabelSecondary(),
            modifier = Modifier.fillMaxWidth().testTag(ProjectMembersEmptyTag),
        )
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(ProjectMembersListTag),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            selectedMemberIDs.forEach { id ->
                MemberPill(
                    id = id,
                    name = memberNames[id],
                    onRemove = { onMemberIDsChanged(selectedMemberIDs - id) },
                )
            }
        }
    }
    // The invite row is edit-mode only, and the reason is structural rather than cosmetic:
    // an invitation names a project id, and a project being *created* has none until save
    // has run. Offering the field in create mode would promise an action the store cannot
    // yet take.
    if (!isCreating) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectsTextField(
                title = stringResource(Res.string.project_members_invite_label),
                value = inviteEmail,
                onValueChange = onInviteEmailChanged,
                placeholder = stringResource(Res.string.project_members_invite_placeholder),
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onImeAction = {
                    if (inviteEmail.isNotBlank() && !isBusy) onSendInvite()
                },
                modifier = Modifier.weight(1f).testTag(ProjectMemberInviteFieldTag),
            )
            val inviteEnabled = inviteEmail.isNotBlank() && !isBusy
            ttGlassButton(
                label = stringResource(Res.string.project_members_invite_send),
                onClick = { if (inviteEnabled) onSendInvite() },
                modifier = Modifier.testTag(ProjectMemberInviteSendTag),
                style = if (inviteEnabled) {
                    TTGlassButtonStyle(tint = ttAccent(), prominent = true)
                } else {
                    TTGlassButtonStyle()
                },
            )
        }
        inviteQueuedEmail?.let { queued ->
            ttText(
                text = stringResource(Res.string.project_members_invite_queued, queued),
                style = ttTypography.footnote,
                color = ttAccent(),
                modifier = Modifier.testTag(ProjectMemberInviteQueuedTag),
            )
        }
    }
}

/**
 * One member, or one pending identity: a disc, a name, and the same "X" the dependency
 * pills use to remove.
 *
 * The name is not clickable — unlike a dependency pill, a member opens nowhere yet — so the
 * only gesture on the chip is the remove, and it carries its own [Role.Button] semantics
 * and click label rather than hanging them off the whole pill.
 */
@Composable
private fun MemberPill(
    id: UserID,
    name: String?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = name ?: id.stringValue
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ttRadius.control))
            .ttGlassCard()
            .padding(start = ttSpacing.sm, end = ttSpacing.xs, top = ttSpacing.xs, bottom = ttSpacing.xs)
            .testTag(ProjectMemberPillTag(id)),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (name != null) {
            // The design system sanctions one disc size ("one size for every disc, so a
            // stack is made of discs rather than of circles"), so a pill's disc is the
            // stack's 24dp rather than a second invented diameter.
            ttInitialsAvatar(
                initials = ttInitialsFor(name),
                size = ttAvatarSize.avatar,
            )
        }
        ttText(
            text = label,
            style = ttTypography.callout,
            color = ttLabel(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(ProjectMemberNameTag(id)),
        )
        ttText(
            text = "X",
            style = ttTypography.callout,
            color = ttLabelSecondary(),
            modifier = Modifier
                .clip(RoundedCornerShape(ttRadius.control))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(Res.string.project_member_remove),
                    onClick = onRemove,
                )
                .testTag(ProjectMemberRemoveTag(id)),
        )
    }
}

@Preview(name = "Members, dark", showBackground = true, widthDp = 360)
@Composable
private fun ProjectMembersSectionDarkPreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectMembersSection(
        selectedMemberIDs = ViewPreviewData.previewMemberIDs,
        onMemberIDsChanged = {},
        currentUserID = ViewPreviewData.previewUserID,
        currentUserName = ViewPreviewData.previewUserNames[ViewPreviewData.previewUserID],
        memberNames = ViewPreviewData.previewUserNames,
    )
}

@Preview(name = "Members, light, edit mode", showBackground = true, widthDp = 360)
@Composable
private fun ProjectMembersSectionLightPreview() = ProjectsPreview(ThemeScheme.Light) {
    ProjectMembersSection(
        selectedMemberIDs = ViewPreviewData.previewMemberIDs,
        onMemberIDsChanged = {},
        currentUserID = ViewPreviewData.previewUserID,
        currentUserName = ViewPreviewData.previewUserNames[ViewPreviewData.previewUserID],
        memberNames = ViewPreviewData.previewUserNames,
        isCreating = false,
        inviteEmail = "grace@example.com",
    )
}

const val ProjectMembersSectionTag = "projects.editor.members"
const val ProjectMembersListTag = "projects.editor.members.list"
const val ProjectMembersEmptyTag = "projects.editor.members.empty"
const val ProjectMemberAddMeTag = "projects.editor.members.addMe"
const val ProjectMemberInviteFieldTag = "projects.editor.members.inviteField"
const val ProjectMemberInviteSendTag = "projects.editor.members.inviteSend"
const val ProjectMemberInviteQueuedTag = "projects.editor.members.inviteQueued"

fun ProjectMemberPillTag(id: UserID): String = "projects.editor.members.pill.$id"
fun ProjectMemberNameTag(id: UserID): String = "projects.editor.members.name.$id"
fun ProjectMemberRemoveTag(id: UserID): String = "projects.editor.members.remove.$id"
