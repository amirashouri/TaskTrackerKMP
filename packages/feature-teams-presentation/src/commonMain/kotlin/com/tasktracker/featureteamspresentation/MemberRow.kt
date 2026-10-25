package com.tasktracker.featureteamspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_members_delete
import com.tasktracker.resources.admin_members_reactivate
import com.tasktracker.resources.admin_members_reset_code_action
import com.tasktracker.resources.admin_members_role_admin
import com.tasktracker.resources.admin_members_role_member
import org.jetbrains.compose.resources.stringResource

/**
 * What a member row offers in its trailing slot.
 *
 * Two values, never a `null` or a hidden control: every row is either deletable or restorable,
 * and which one it is follows from the member's own status rather than from the bucket the
 * screen happens to be showing. That is deliberate — reading it off the member keeps the rule
 * true for a row that arrives in the wrong bucket, and it makes the rule testable without
 * rendering anything.
 */
enum class MemberRowAction { Delete, Reactivate }

/**
 * Which action [user] offers.
 *
 * Exhaustive over [UserStatus], so a fourth status is a compile error here rather than a row
 * that silently offers the wrong thing. The two live values both map to delete: an account the
 * server merely marked away is still one an admin can remove.
 */
fun memberRowAction(user: User): MemberRowAction = when (user.status) {
    UserStatus.Deleted -> MemberRowAction.Reactivate
    UserStatus.Active, UserStatus.Inactive -> MemberRowAction.Delete
}

/**
 * One person in the members list, with the one action their status allows.
 *
 * ## The caption carries role and position, and drops either when it is empty
 *
 * The row's two fixed readings are the name and the address; the role is what decides whether
 * this row may itself administer, and the position is the job title the server stores as free
 * text. They share a caption line because neither is worth a line of its own, and a position
 * that is absent or blank leaves the role reading alone rather than an empty trailing separator.
 */
@Composable
fun MemberRow(
    user: User,
    action: MemberRowAction,
    onDelete: () -> Unit,
    onReactivate: () -> Unit,
    modifier: Modifier = Modifier,
    onGenerateResetCode: ((User) -> Unit)? = null,
    isGeneratingResetCode: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(memberRowTag(user.id))
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
            .padding(
                horizontal = ttSpacing.xl,
                vertical = ttSpacing.xl,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xl),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        ) {
            ttText(
                text = user.preferredGreeting,
                style = ttTypography.body,
                maxLines = 1,
            )
            ttText(
                text = user.email,
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                maxLines = 1,
            )
            ttText(
                text = memberCaption(user),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                maxLines = 1,
            )
        }

        when (action) {
            MemberRowAction.Delete -> Row(
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Live rows only: a deleted member cannot sign in, so a code for them would be
                // a credential for an account the server still refuses. The padlock is the
                // settings screen's own glyph — the same mark that leads to the flow this ends
                // in — so the two halves of "password" wear one shape.
                if (onGenerateResetCode != null) {
                    ttGlassIconButton(
                        image = ttSettingsIcons.lock,
                        contentDescription = stringResource(Res.string.admin_members_reset_code_action),
                        onClick = { onGenerateResetCode(user) },
                        // Disabled while a mint is in flight: a second tap would mint a second
                        // code and leave the admin holding two live credentials for one hand-off.
                        enabled = !isGeneratingResetCode,
                        modifier = Modifier.testTag(memberResetCodeButtonTag(user.id)),
                    )
                }
                ttGlassIconButton(
                    image = ttActionIcons.trash,
                    contentDescription = stringResource(Res.string.admin_members_delete),
                    // The one destructive control in the app that is a single tap away from a row
                    // action, so it is tinted rather than left neutral: the tint is what says the
                    // tap leads to a confirmation and then to a lost session.
                    tint = ttError(),
                    onClick = onDelete,
                    modifier = Modifier.testTag(memberDeleteButtonTag(user.id)),
                )
            }

            MemberRowAction.Reactivate -> ttGlassButton(
                label = stringResource(Res.string.admin_members_reactivate),
                onClick = onReactivate,
                modifier = Modifier.testTag(memberReactivateButtonTag(user.id)),
            )
        }
    }
}

/**
 * The role, and the position when there is one.
 *
 * Not a resource with two placeholders, because the position is server free text and a
 * translated format string would have to be trusted to place it; a `when` on the role keeps the
 * role half translated and the position half verbatim.
 */
@Composable
private fun memberCaption(user: User): String {
    val role = when (user.role) {
        MemberRole.Admin -> stringResource(Res.string.admin_members_role_admin)
        MemberRole.Member -> stringResource(Res.string.admin_members_role_member)
    }
    val position = user.position?.takeIf { it.isNotBlank() } ?: return role
    return "$role · $position"
}

/** The row's tag, so a test finds the member it means rather than the nth row. */
fun memberRowTag(id: UserID): String = "adminMembers.row.$id"

/** The delete button's tag on [id]'s row. */
fun memberDeleteButtonTag(id: UserID): String = "adminMembers.row.$id.delete"

/** The reset-code button's tag on [id]'s row. */
fun memberResetCodeButtonTag(id: UserID): String = "adminMembers.row.$id.resetCode"

/** The reactivate button's tag on [id]'s row. */
fun memberReactivateButtonTag(id: UserID): String = "adminMembers.row.$id.reactivate"
