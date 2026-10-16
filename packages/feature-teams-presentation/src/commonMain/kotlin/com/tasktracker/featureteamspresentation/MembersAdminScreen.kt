package com.tasktracker.featureteamspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.designsystem.ConfirmPopup
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSpinner
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_members_add
import com.tasktracker.resources.admin_members_delete_body
import com.tasktracker.resources.admin_members_delete_title
import com.tasktracker.resources.admin_members_empty_active
import com.tasktracker.resources.admin_members_empty_deleted
import com.tasktracker.resources.admin_members_segment_active
import com.tasktracker.resources.admin_members_segment_deleted
import com.tasktracker.resources.admin_members_title
import com.tasktracker.resources.common_delete
import org.jetbrains.compose.resources.stringResource

/**
 * The members screen: one bucket at a time, with the action each row allows and a way to add.
 *
 * ## A function of its arguments, like every other screen here
 *
 * It owns no state and reads no port. The list, the chosen bucket, the failure and the two
 * overlays all arrive as parameters, and every command leaves as a callback — which is what
 * lets the whole screen be previewed and exercised without a network, and what keeps the rules
 * (which rows offer which action, what a delete does to the list) in [MemberAdminModel] where a
 * test can read them without rendering anything.
 *
 * ## Why the two overlays are drawn inline
 *
 * The confirmation is the design system's [ConfirmPopup], and the add form is [AddMemberDialog];
 * both are part of this tree rather than a platform `Dialog`. That is the same decision the
 * projects list records: a platform-backed dialog does not render under `runComposeUiTest` on
 * every target, so an overlay drawn here is the one a UI test can find.
 *
 * ## Why the empty state is two sentences rather than one
 *
 * The control above already says which bucket is on screen, so the body only has to say whether
 * the bucket is empty because nothing has been added yet or because nothing has been deleted —
 * and those are different situations for the admin reading them, not the same one twice.
 */
@Composable
fun MembersAdminScreen(
    bucket: MembersBucket,
    members: List<User>,
    isLoading: Boolean,
    errorMessage: String?,
    pendingDeletion: User?,
    isAddFormOpen: Boolean,
    isCreating: Boolean,
    createErrorMessage: String?,
    onSelectBucket: (MembersBucket) -> Unit,
    onRequestDelete: (User) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onReactivate: (User) -> Unit,
    onOpenAddForm: () -> Unit,
    onCloseAddForm: () -> Unit,
    onCreate: (email: String, password: String, role: MemberRole, position: String?) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // How much of the bottom of this screen the floating tab bar covers, published by the shell
    // in `shared-ui`. It goes into the list's `contentPadding` so the last row clears the glass,
    // and it is *not* a root padding: padding on the screen would stop the scroll short of the
    // bottom edge instead of letting the list run under the bar.
    val tabBarInset = LocalTabBarScrollUnderInset.current

    Box(
        modifier = modifier.fillMaxSize().testTag(MembersAdminScreenTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            MembersHeader(onOpenAddForm = onOpenAddForm)

            if (errorMessage != null) {
                ttBanner(message = errorMessage, onDismiss = onDismissError)
            }

            TTGlassSegmentedPicker(
                options = MembersBucket.entries,
                selection = bucket,
                onSelect = onSelectBucket,
                title = { option ->
                    when (option) {
                        MembersBucket.Active -> stringResource(Res.string.admin_members_segment_active)
                        MembersBucket.Deleted -> stringResource(Res.string.admin_members_segment_deleted)
                    }
                },
                testTag = { membersBucketTag(it) },
            )

            when {
                isLoading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag(MembersLoadingTag),
                    contentAlignment = Alignment.Center,
                ) {
                    ttSpinner(color = ttLabel())
                }

                members.isEmpty() -> MembersEmptyState(
                    bucket = bucket,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                else -> LazyColumn(
                    // `weight(1f)` so the list takes the column's remaining height and its own
                    // `contentPadding` is measured against that — padding is laid out inside the
                    // viewport, so a viewport that stopped at the bar would reserve nothing useful.
                    modifier = Modifier.weight(1f).testTag(MembersListTag),
                    contentPadding = PaddingValues(bottom = ttSpacing.xl + tabBarInset),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                ) {
                    items(members, key = { it.id.stringValue }) { user ->
                        val action = memberRowAction(user)
                        MemberRow(
                            user = user,
                            action = action,
                            onDelete = { onRequestDelete(user) },
                            onReactivate = { onReactivate(user) },
                        )
                    }
                }
            }
        }

        if (isAddFormOpen) {
            AddMemberDialog(
                isCreating = isCreating,
                errorMessage = createErrorMessage,
                onCreate = onCreate,
                onDismiss = onCloseAddForm,
            )
        }
    }

    // Outside the `Box` and after the list, so the confirmation is the last thing in the tree
    // and therefore over everything the screen drew. It is keyed on the *member*, so a row that
    // leaves the list while the popup is open still confirms against the account it named rather
    // than against a row that no longer exists.
    pendingDeletion?.let { user ->
        ConfirmPopup(
            title = stringResource(Res.string.admin_members_delete_title, user.preferredGreeting),
            body = stringResource(Res.string.admin_members_delete_body),
            confirmLabel = stringResource(Res.string.common_delete),
            // The destructive tint, so the button that ends a session does not look like the one
            // that would have kept it.
            confirmTint = ttError(),
            onCancel = onCancelDelete,
            onConfirm = onConfirmDelete,
        )
    }
}

/**
 * The page title, with the create action in its trailing section.
 *
 * An action in the header rather than a floating button, for the reason the projects list
 * records: a floating circle has to be paid for twice — once in layout that reserves space for
 * it and once in the touch target that has to aim past it — and the reservation is the half that
 * gets forgotten. There is nothing under a header action to cover.
 */
@Composable
private fun MembersHeader(
    onOpenAddForm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        ttText(
            text = stringResource(Res.string.admin_members_title),
            style = ttTypography.titleSmall,
            color = ttLabel(),
            modifier = Modifier.weight(1f),
        )
        ttGlassIconButton(
            image = ttActionIcons.add,
            contentDescription = stringResource(Res.string.admin_members_add),
            onClick = onOpenAddForm,
            modifier = Modifier.testTag(MembersAddTag),
        )
    }
}

/** What an empty bucket says, which is the one thing the two buckets disagree about. */
@Composable
private fun MembersEmptyState(
    bucket: MembersBucket,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.testTag(MembersEmptyTag),
        contentAlignment = Alignment.Center,
    ) {
        ttText(
            text = when (bucket) {
                MembersBucket.Active -> stringResource(Res.string.admin_members_empty_active)
                MembersBucket.Deleted -> stringResource(Res.string.admin_members_empty_deleted)
            },
            style = ttTypography.callout,
            color = ttLabelSecondary(),
        )
    }
}

/** The screen's tag. */
const val MembersAdminScreenTag = "adminMembers.screen"

/** The header's add button. */
const val MembersAddTag = "adminMembers.add"

/** The list of member rows. */
const val MembersListTag = "adminMembers.list"

/** The empty-bucket state, in place of the list. */
const val MembersEmptyTag = "adminMembers.empty"

/** The spinner shown while a bucket loads. */
const val MembersLoadingTag = "adminMembers.loading"

/** The tag on the segment for [bucket]. */
fun membersBucketTag(bucket: MembersBucket): String = "adminMembers.bucket.${bucket.name.lowercase()}"
