package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tasktracker.core.AppError
import com.tasktracker.designsystem.ConfirmPopup
import com.tasktracker.designsystem.LocalStatusBarScrollUnderInset
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.ServerAddressSettings
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_activity_failed
import com.tasktracker.resources.settings_activity_idle
import com.tasktracker.resources.settings_activity_retrying
import com.tasktracker.resources.settings_activity_signed_out
import com.tasktracker.resources.settings_activity_syncing
import com.tasktracker.resources.settings_activity_up_to_date
import com.tasktracker.resources.settings_activity_waiting
import com.tasktracker.resources.settings_change_password
import com.tasktracker.resources.settings_connectivity_needs_connection
import com.tasktracker.resources.settings_connectivity_offline
import com.tasktracker.resources.settings_connectivity_online
import com.tasktracker.resources.settings_connectivity_unknown
import com.tasktracker.resources.settings_manage_members
import com.tasktracker.resources.settings_manage_projects
import com.tasktracker.resources.settings_outcome_already_running
import com.tasktracker.resources.settings_email_label
import com.tasktracker.resources.settings_forgot_password
import com.tasktracker.resources.settings_outcome_offline
import com.tasktracker.resources.settings_profile_save_failed
import com.tasktracker.resources.settings_server_label
import com.tasktracker.resources.settings_server_none
import com.tasktracker.resources.settings_server_reset_notice
import com.tasktracker.resources.settings_server_saved_notice
import com.tasktracker.resources.settings_sign_out
import com.tasktracker.resources.settings_signout_blocked_body
import com.tasktracker.resources.settings_signout_blocked_title
import com.tasktracker.resources.settings_signout_force
import com.tasktracker.resources.settings_status_label
import com.tasktracker.resources.settings_sync_now
import com.tasktracker.resources.settings_sync_retry
import com.tasktracker.resources.settings_sync_stuck
import com.tasktracker.resources.settings_sync_waiting
import com.tasktracker.resources.settings_version
import org.jetbrains.compose.resources.stringResource

/**
 * Settings: appearance, the connection, the state of sync, and the way out.
 *
 * ## Every reading arrives as a parameter, and none of them are read from storage here
 *
 * This is a function of its arguments and owns no state, like every other screen in the app:
 * [SettingsModel] holds the readings and this composable turns them into rows. The one thing
 * that *is* passed in rather than reached for is the theme — [themeScheme] and [onThemeChange]
 * — and the reason is the original's own: an appearance preference is read synchronously and
 * has to be right on the first frame, and it is not something this feature owns. Routing it
 * through the settings port would make the theme a server-reachable concern, and reading it
 * from a preferences store here would give this module an edge to
 * `:packages:user-defaults` that the architecture table does not allow. So the shell owns it
 * and hands it down.
 *
 * ## Rows appear only when their numbers are non-zero
 *
 * The queue depth and the dead-letter count are each rendered only when
 * [SyncActivity.outstandingCount] and [SyncActivity.stuckCount] are non-null. The quiet state
 * cannot then be mistaken for the alarming one: a screen that always showed "Waiting to sync: 0"
 * reads as a stuck app, which is the opposite of what an empty queue means.
 *
 * ## The Sync Now button is disabled only while a cycle is running
 *
 * Not for an offline path, and not for a previous outcome. A control that goes dead for a
 * reason the user cannot see is how a dead button gets reported as a bug — so an offline tap
 * is *answered*, with [SyncOutcome.Offline] rendered as the sentence explaining that the edits
 * are queued and safe.
 *
 * ## Sign Out is the one control that can be answered with a question
 *
 * Every other command on this screen either works or reports what happened. Sign Out can fail
 * in a way the user has to decide about, because the app asks the server to revoke the refresh
 * token family before it forgets anything — deliberately, so a user is never signed out behind
 * their back while a possibly-live token sits on the server. When that call cannot be
 * completed, [signOutFailure] is non-null and the screen raises [ConfirmPopup] offering the
 * device-only sign-out instead. See `SettingsModel.signOut` for where that failure is caught,
 * and `SettingsPort.forceEndSession` for what the second button discards.
 *
 * ## The identity block edits optimistically and reverts on refusal
 *
 * A picker or the name sheet stages its value through [onUserDetailsChange] first, so the
 * header moves on the same frame; [onUserDetailsCommit] is the network step that follows and
 * the only one that can fail. A refused write is reverted by the adapter and reported in
 * [profileFailure], a dismissible banner above the block. The edit is deliberately not queued:
 * a profile write is server-owned identity, not offline work, and showing an unsaved value as
 * saved would be worse than asking the user to try again.
 */
@Composable
fun SettingsScreen(
    connectivity: ConnectivitySummary,
    activity: SyncActivity,
    themeScheme: ThemeScheme,
    version: AppVersion,
    commandOutcome: SyncOutcome?,
    onThemeChange: (ThemeScheme) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    /**
     * Puts the changes the server refused back in the queue and tries them again.
     *
     * Defaulted like every other optional command on this screen, so the previews and the
     * scenarios that do not exercise the stuck state do not have to name it.
     */
    onRetryStuckChanges: () -> Unit = {},
    modifier: Modifier = Modifier,
    accentPalette: AccentPalette = AccentPalette.Classic,
    onAccentPaletteChange: (AccentPalette) -> Unit = {},
    glassIntensity: Float = 1f,
    onGlassIntensityPreview: (Float) -> Unit = {},
    onGlassIntensityCommit: (Float) -> Unit = {},
    userDetails: UserDetails = UserDetails(),
    onUserDetailsChange: (UserDetails) -> Unit = {},
    /**
     * Writes the staged identity edit to the signed-in user's record.
     *
     * Separate from [onUserDetailsChange] because the two have different jobs: change is the
     * optimistic, synchronous update that keeps a picker responsive, and this is the network
     * write that either confirms it or makes the screen revert and show [profileFailure].
     * Defaulted so the previews and the scenarios that only read the header compile unchanged.
     */
    onUserDetailsCommit: () -> Unit = {},
    /**
     * The last profile write the server refused, or `null` when there is nothing to report.
     *
     * Rendered as a dismissible banner above the identity block. The model holds the
     * [AppError] and the screen owns the sentence, the same split as every other failure here.
     */
    profileFailure: AppError? = null,
    onDismissProfileFailure: () -> Unit = {},
    signOutFailure: AppError? = null,
    onForceSignOut: () -> Unit = {},
    onDismissSignOutFailure: () -> Unit = {},
    /**
     * Whether the signed-in person administers the account, and so sees the Admin section.
     *
     * The gate is *presence* rather than a disabled row: a control that is drawn but cannot be
     * used is how "the app is broken for me" gets reported, and a member has nothing to do on
     * either screen. Defaulted to `false`, so the previews and the scenarios that do not name it
     * read as a member — the honest default, and the safe one.
     */
    isAdmin: Boolean = false,
    onManageMembers: () -> Unit = {},
    onManageProjects: () -> Unit = {},
    /**
     * The change-password sheet's command and its model-held answer.
     *
     * The two row taps that open the sheets are *local* — the same decision
     * [nameEditorOpen] documents: the drafts never leave the sheet, so there is no callback
     * for opening one. What does cross the seam is the submit (new password + credential) and
     * the outcome the model records, which the sheet renders: `Started` disables submit,
     * `Failure` shows the server's sentence, `Success` shows the acknowledgment and closes.
     * The dismiss clears the outcome, so a reopened sheet cannot greet the user with the last
     * attempt's banner. All three defaulted, so previews and scenarios that never open the
     * sheet compile unchanged.
     */
    changePasswordOutcome: ChangePasswordOutcome? = null,
    onRequestChangePassword: (newPassword: String, oldPasswordOrCode: String) -> Unit = { _, _ -> },
    onDismissChangePasswordOutcome: () -> Unit = {},
    /**
     * Whether the administrator's generator can actually mint, which is not the same question
     * as [isAdmin].
     *
     * [isAdmin] is about the person; this is about the build. An administrator on a client with
     * no member directory bound — the offline demo — still sees the Admin section, whose two
     * rows lead to placeholders, but must not be offered a picker whose Generate button would
     * have no port to call. Two flags rather than one because they can disagree, and the sheet
     * is given their conjunction: an administrator, on a build that can mint.
     */
    canGenerateResetCode: Boolean = false,
    /**
     * The administrator's half of the same sheet: the member picker, the mint, and its answer.
     *
     * Read straight through to [ChangePasswordSheet], which draws them for the segment an
     * administrator has and for nobody else. Defaulted, like the other three commands here, so
     * a preview or a scenario that never opens the sheet names none of them.
     */
    resetCode: ResetCodePanel = ResetCodePanel(),
    onLoadResetMembers: () -> Unit = {},
    onSelectResetMember: (ResetCodeCandidate) -> Unit = {},
    onGenerateResetCode: () -> Unit = {},
    /**
     * The API root this launch is using and the one saved for the next, the open editor (or
     * `null` when the sheet is closed), and the last save/reset notice.
     *
     * All defaulted so the previews and the scenarios that do not exercise the Server field
     * compile unchanged, and so the screen stays a function of its arguments — the model owns
     * every one of these values and the shell binds them.
     */
    serverAddress: ServerAddressSettings = ServerAddressSettings(inUse = null, savedOverride = null),
    serverAddressEditor: ServerAddressEditor? = null,
    serverAddressNotice: ServerAddressNotice? = null,
    onEditServerAddress: () -> Unit = {},
    onServerAddressDraftChange: (String) -> Unit = {},
    onSaveServerAddress: () -> Unit = {},
    onResetServerAddress: () -> Unit = {},
    onDismissServerAddress: () -> Unit = {},
    isWide: Boolean = false,
) {
    // The name editor's visibility. Local rather than in the model: the draft never leaves the
    // sheet, and the model only ever sees the committed value. `false` is closed, which is the
    // state every other screen starts in.
    var nameEditorOpen by remember { mutableStateOf(false) }

    // The two password sheets, in the same local register as the name editor, for the same
    // reason: their drafts are theirs. `startChangeWithCode` is the one piece of navigation
    // between them — the forgot sheet's "I have a code" opens the change sheet with the
    // credential selector already on Admin Code, so the code the member just read out of band
    // lands in a field that is expecting it rather than one asking for a password they forgot.
    var changePasswordOpen by remember { mutableStateOf(false) }
    var forgotPasswordOpen by remember { mutableStateOf(false) }
    var startChangeWithCode by remember { mutableStateOf(false) }

    val identitySection: @Composable () -> Unit = {
        SettingsSection {
            UserDetailsRow(
                details = userDetails,
                onDetailsChange = { updated ->
                    onUserDetailsChange(updated)
                    onUserDetailsCommit()
                },
                onEditName = { nameEditorOpen = true },
                modifier = Modifier.testTag(SettingsUserDetailsTag),
            )
            if (userDetails.email.isNotBlank()) {
                SettingsDivider()
                SettingsIconRow(
                    label = stringResource(Res.string.settings_email_label),
                    value = userDetails.email,
                    icon = ttSettingsIcons.account,
                    modifier = Modifier.testTag(SettingsEmailRowTag),
                )
            }
            SettingsDivider()
            UserPositionPickerRow(
                selected = userDetails.position,
                onSelect = { position ->
                    onUserDetailsChange(userDetails.copy(position = position))
                    onUserDetailsCommit()
                },
                modifier = Modifier.testTag(SettingsPositionRowTag),
            )
            SettingsDivider()
            UserStatusPickerRow(
                selected = userDetails.status,
                onSelect = { status ->
                    onUserDetailsChange(userDetails.copy(status = status))
                    onUserDetailsCommit()
                },
                modifier = Modifier.testTag(SettingsUserStatusRowTag),
            )
            SettingsDivider()
            StatusTextEditor(
                value = userDetails.statusText,
                onValueChange = { statusText ->
                    onUserDetailsChange(userDetails.copy(statusText = statusText))
                },
                onCommit = onUserDetailsCommit,
                modifier = Modifier.testTag(SettingsStatusTextRowTag),
            )
            SettingsDivider()
            SettingsActionRow(
                label = stringResource(Res.string.settings_change_password),
                icon = ttSettingsIcons.lock,
                tint = ttAccent(),
                onClick = { changePasswordOpen = true },
                modifier = Modifier.testTag(SettingsChangePasswordRowTag),
            )
            SettingsDivider()
            SettingsActionRow(
                label = stringResource(Res.string.settings_forgot_password),
                icon = ttSettingsIcons.lock,
                tint = ttError(),
                onClick = { forgotPasswordOpen = true },
                modifier = Modifier.testTag(SettingsForgotPasswordRowTag),
            )
        }
    }

    val adminSection: @Composable () -> Unit = {
        if (isAdmin) {
            SettingsSection {
                SettingsActionRow(
                    label = stringResource(Res.string.settings_manage_members),
                    icon = ttSettingsIcons.account,
                    tint = ttAccent(),
                    onClick = onManageMembers,
                    modifier = Modifier.testTag(SettingsManageMembersTag),
                )
                SettingsDivider()
                SettingsActionRow(
                    label = stringResource(Res.string.settings_manage_projects),
                    icon = ttTabIcons.projects,
                    tint = ttAccent(),
                    onClick = onManageProjects,
                    modifier = Modifier.testTag(SettingsManageProjectsTag),
                )
            }
        }
    }

    val appearanceSection: @Composable () -> Unit = {
        SettingsSection {
            ThemePickerRow(
                selected = themeScheme,
                onSelect = onThemeChange,
            )
            SettingsDivider()
            AccentPalettePickerRow(
                selected = accentPalette,
                onSelect = onAccentPaletteChange,
            )
            SettingsDivider()
            GlassIntensityRow(
                intensity = glassIntensity,
                onPreview = onGlassIntensityPreview,
                onCommit = onGlassIntensityCommit,
            )
        }
    }

    val serverSection: @Composable () -> Unit = {
        SettingsSection {
            ServerAddressRow(
                label = stringResource(Res.string.settings_server_label),
                address = serverAddressLabel(serverAddress),
                icon = ttSettingsIcons.connectivity,
                onClick = onEditServerAddress,
                modifier = Modifier.testTag(SettingsServerRowTag),
            )
            serverAddressNotice?.let { notice ->
                SettingsDivider()
                ttText(
                    text = serverAddressNoticeLabel(notice),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ttSpacing.sm)
                        .testTag(SettingsServerNoticeTag),
                )
            }
        }
    }

    val connectivitySection: @Composable () -> Unit = {
        SettingsSection {
            SettingsIconRow(
                label = stringResource(Res.string.settings_status_label),
                value = connectivityLabel(connectivity),
                icon = ttSettingsIcons.connectivity,
            )
            SettingsDivider()
        }
    }

    val syncSection: @Composable () -> Unit = {
        SettingsSection {
            SettingsIconRow(
                label = stringResource(Res.string.settings_status_label),
                value = activityLabel(activity.phase),
                icon = ttSettingsIcons.sync,
            )
            activity.outstandingCount?.let { outstanding ->
                SettingsDivider()
                SettingsIconRow(
                    label = stringResource(Res.string.settings_sync_waiting),
                    value = outstanding.toString(),
                    icon = ttSettingsIcons.sync,
                    modifier = Modifier.testTag(SettingsWaitingRowTag),
                )
            }
            activity.stuckCount?.let { stuck ->
                SettingsDivider()
                SettingsIconRow(
                    label = stringResource(Res.string.settings_sync_stuck),
                    value = stuck.toString(),
                    icon = ttSettingsIcons.sync,
                    valueColor = ttError(),
                    modifier = Modifier.testTag(SettingsStuckRowTag),
                )
                SettingsDivider()
                SettingsActionRow(
                    label = stringResource(Res.string.settings_sync_retry),
                    icon = ttSettingsIcons.sync,
                    tint = ttAccent(),
                    onClick = onRetryStuckChanges,
                    enabled = !activity.isSyncing,
                    modifier = Modifier.testTag(SettingsRetryStuckTag),
                )
            }
            if (activity.failures.isNotEmpty()) {
                SettingsDivider()
                ttText(
                    text = activity.failures.joinToString(separator = "\n"),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ttSpacing.sm)
                        .testTag(SettingsFailuresTag),
                )
            }
            commandOutcome?.let { outcome ->
                outcomeMessage(outcome)?.let { message ->
                    SettingsDivider()
                    ttText(
                        text = message,
                        style = ttTypography.footnote,
                        color = ttError(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ttSpacing.sm)
                            .testTag(SettingsOutcomeTag),
                    )
                }
            }
            SettingsDivider()
            SettingsActionRow(
                label = stringResource(Res.string.settings_sync_now),
                icon = ttSettingsIcons.sync,
                tint = ttAccent(),
                onClick = onSyncNow,
                enabled = !activity.isSyncing,
                modifier = Modifier.testTag(SettingsSyncNowTag),
            )
        }
    }

    val signOutSection: @Composable () -> Unit = {
        SettingsSection {
            SettingsActionRow(
                label = stringResource(Res.string.settings_sign_out),
                icon = ttSettingsIcons.account,
                tint = ttError(),
                onClick = onSignOut,
                modifier = Modifier.testTag(SettingsSignOutTag),
            )
        }
    }

    val versionSection: @Composable () -> Unit = {
        version.display?.let { displayed ->
            SettingsSection {
                SettingsIconRow(
                    label = stringResource(Res.string.settings_version),
                    value = displayed,
                    icon = ttSettingsIcons.about,
                    modifier = Modifier.testTag(SettingsVersionRowTag),
                )
            }
        }
    }

    // The list and the confirmation popup are siblings in one box, so the popup floats over
    // the list rather than scrolling with it. `modifier` lands here rather than on the
    // `LazyColumn`, which is what lets the overlay cover the whole screen instead of only the
    // list's viewport — the same shape `SprintDetailScreen` uses for its own confirmation.
    Box(modifier = modifier.fillMaxSize()) {
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ttSpacing.xxl)
                    .padding(top = LocalStatusBarScrollUnderInset.current + ttSpacing.xl)
                    .testTag(SettingsScreenTag),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                // Left Pane
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xxl),
                ) {
                    profileFailure?.let {
                        item {
                            Box(modifier = Modifier.testTag(SettingsProfileFailureTag)) {
                                ttBanner(
                                    message = stringResource(Res.string.settings_profile_save_failed),
                                    onDismiss = onDismissProfileFailure,
                                )
                            }
                        }
                    }
                    item { identitySection() }
                    if (isAdmin) {
                        item { adminSection() }
                    }
                    item { serverSection() }
                    item { connectivitySection() }
                    item { syncSection() }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(ttSeparator()),
                )

                // Right Pane
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xxl),
                ) {
                    item { appearanceSection() }
                    item { signOutSection() }
                    version.display?.let {
                        item { versionSection() }
                    }
                }
            }
        } else {
            // The top gap is *content* padding, not viewport padding, and that is the whole
            // difference between a list that starts below the notch and one that scrolls under
            // it: `padding(top)` would shorten the viewport and keep every row out of the
            // status-bar strip forever. The shell stops applying the safe-area inset to this
            // destination for the same reason, and publishes the height for this line to spend
            // (`LocalStatusBarScrollUnderInset`). It is `0.dp` outside the shell, where there is
            // no notch to clear and this is the same layout as before.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ttSpacing.xxl)
                    .testTag(SettingsScreenTag),
                contentPadding = PaddingValues(
                    top = LocalStatusBarScrollUnderInset.current + ttSpacing.xl,
                    bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xxl),
            ) {
                profileFailure?.let {
                    item {
                        Box(modifier = Modifier.testTag(SettingsProfileFailureTag)) {
                            ttBanner(
                                message = stringResource(Res.string.settings_profile_save_failed),
                                onDismiss = onDismissProfileFailure,
                            )
                        }
                    }
                }
                item { identitySection() }
                if (isAdmin) {
                    item { adminSection() }
                }
                item { appearanceSection() }
                item { serverSection() }
                item { connectivitySection() }
                item { syncSection() }
                item { signOutSection() }
                version.display?.let {
                    item { versionSection() }
                }
            }
        }

        // The force sign-out, offered only when the clean one could not be completed.
        //
        // A popup rather than an inline row, and the reason is that this is the one thing on the
        // screen the user has to *answer* rather than read: the sign-out has already failed, and
        // what happens next is theirs to decide. The design system's own popup, for the contrast
        // reason `ConfirmPopup` documents — an inline confirmation on this glass was the defect
        // that component was written to fix.
        //
        // One body for every failure. The title already names what went wrong in the user's own
        // terms, and the choice in front of them — force it, or leave the session alone — is
        // identical whether the server timed out, refused, or was never reached at all.
        if (signOutFailure != null) {
            ConfirmPopup(
                title = stringResource(Res.string.settings_signout_blocked_title),
                body = stringResource(Res.string.settings_signout_blocked_body),
                confirmLabel = stringResource(Res.string.settings_signout_force),
                onCancel = onDismissSignOutFailure,
                onConfirm = onForceSignOut,
                // Red, like every other destructive confirm in the app. The button erases data,
                // and the tint is the only thing distinguishing it from the neutral button
                // beside it — which is the button that keeps everything.
                confirmTint = ttError(),
                tag = SettingsForceSignOutTag,
            )
        }

        // The editor is a second sibling of the list, for the same reason the popup is: it has
        // to float over the whole screen rather than scroll with the row that opened it. The
        // model's editor being non-null is both the visibility and the draft — see
        // `ServerAddressEditor` — so there is no flag here that could disagree with the field.
        serverAddressEditor?.let { editor ->
            ServerAddressSheet(
                draft = editor.draft,
                isChecking = editor.isChecking,
                failure = editor.failure,
                canReset = serverAddress.isCustom,
                onDraftChange = onServerAddressDraftChange,
                onSave = onSaveServerAddress,
                onReset = onResetServerAddress,
                onDismiss = onDismissServerAddress,
            )
        }

        // The name editor, in the same overlay layer as the Server sheet. Saving closes it
        // first and then dispatches, so a slow write cannot leave the sheet sitting over a
        // header that has already changed; the write's failure reverts the header *and* raises
        // the banner above it.
        if (nameEditorOpen) {
            UserNameSheet(
                initial = userDetails.fullName,
                onSave = { name ->
                    nameEditorOpen = false
                    onUserDetailsChange(userDetails.copy(fullName = name))
                    onUserDetailsCommit()
                },
                onDismiss = { nameEditorOpen = false },
            )
        }

        // The forgot sheet, in the same overlay layer. Its one way forward is the change sheet
        // with the credential selector preset: closing this one and opening that one in the
        // same tap is what makes the two read as one flow rather than two dialogs to hunt
        // between.
        if (forgotPasswordOpen) {
            ForgotPasswordSheet(
                onHaveCode = {
                    forgotPasswordOpen = false
                    startChangeWithCode = true
                    changePasswordOpen = true
                },
                onDismiss = { forgotPasswordOpen = false },
            )
        }

        // The change sheet draws last of the two, so a tap that crossed from the forgot sheet
        // lands on it rather than over it. Dismissing clears the model's outcome with the
        // sheet — see the parameter's KDoc — so every open starts from nothing to report.
        if (changePasswordOpen) {
            ChangePasswordSheet(
                outcome = changePasswordOutcome,
                startWithCode = startChangeWithCode,
                onSubmit = onRequestChangePassword,
                onDismiss = {
                    changePasswordOpen = false
                    startChangeWithCode = false
                    onDismissChangePasswordOutcome()
                },
                // An administrator, on a build that can mint. The conjunction rather than
                // `isAdmin` alone: the segment's only control is a call on the member
                // directory, so a client without one must show the member's form instead of a
                // picker that cannot produce anything.
                isAdmin = isAdmin && canGenerateResetCode,
                resetCode = resetCode,
                onLoadResetMembers = onLoadResetMembers,
                onSelectResetMember = onSelectResetMember,
                onGenerateResetCode = onGenerateResetCode,
            )
        }
    }
}




/** One connectivity state, as the word the Connection row shows. */
@Composable
private fun connectivityLabel(connectivity: ConnectivitySummary): String =
    stringResource(
        when (connectivity) {
            ConnectivitySummary.Online -> Res.string.settings_connectivity_online
            ConnectivitySummary.Offline -> Res.string.settings_connectivity_offline
            ConnectivitySummary.NeedsConnection -> Res.string.settings_connectivity_needs_connection
            ConnectivitySummary.Unknown -> Res.string.settings_connectivity_unknown
        },
    )

/** One sync phase, as the word the Sync row shows. Exhaustive with no `else`, on purpose. */
@Composable
private fun activityLabel(phase: SyncActivity.Phase): String =
    stringResource(
        when (phase) {
            SyncActivity.Phase.Idle -> Res.string.settings_activity_idle
            SyncActivity.Phase.WaitingForConnection -> Res.string.settings_activity_waiting
            SyncActivity.Phase.Syncing -> Res.string.settings_activity_syncing
            SyncActivity.Phase.UpToDate -> Res.string.settings_activity_up_to_date
            SyncActivity.Phase.Retrying -> Res.string.settings_activity_retrying
            SyncActivity.Phase.Failed -> Res.string.settings_activity_failed
            SyncActivity.Phase.SignedOut -> Res.string.settings_activity_signed_out
        },
    )

/**
 * The sentence for an outcome, or `null` for [SyncOutcome.Started].
 *
 * `null` for the happy path rather than an empty string, so the row is not rendered at all:
 * saying anything about a sync that started would be noise, and an empty text node in the
 * tree is a thing a test can accidentally match.
 */
@Composable
private fun outcomeMessage(outcome: SyncOutcome): String? = when (outcome) {
    SyncOutcome.Started -> null
    SyncOutcome.AlreadyRunning -> stringResource(Res.string.settings_outcome_already_running)
    SyncOutcome.Offline -> stringResource(Res.string.settings_outcome_offline)
}

/**
 * The Server row's value: the address this launch is using, or the no-server label.
 *
 * Deliberately the *in-use* root and not the saved override. A save cannot rebind the running
 * clients, so showing the saved value here would claim the change is live; the notice below the
 * row is what carries "next launch".
 */
@Composable
private fun serverAddressLabel(address: ServerAddressSettings): String =
    address.inUse ?: stringResource(Res.string.settings_server_none)

/** The notice under the Server row, as the sentence for the command that raised it. */
@Composable
private fun serverAddressNoticeLabel(notice: ServerAddressNotice): String = stringResource(
    when (notice) {
        ServerAddressNotice.Saved -> Res.string.settings_server_saved_notice
        ServerAddressNotice.Reset -> Res.string.settings_server_reset_notice
    },
)

const val SettingsScreenTag = "settings.screen"
const val SettingsSyncNowTag = "settings.syncNow"
const val SettingsSignOutTag = "settings.signOut"
const val SettingsForceSignOutTag = "settings.forceSignOut"
const val SettingsWaitingRowTag = "settings.waitingRow"
const val SettingsStuckRowTag = "settings.stuckRow"

/**
 * The control that sends the refused changes again.
 *
 * A tag of its own rather than reusing the stuck row's, because the two are asserted
 * separately: a scenario has to be able to say both "the count is there" and "the way out of
 * it is there", and a single tag would let a build that dropped the control still pass.
 */
const val SettingsRetryStuckTag = "settings.retryStuck"
const val SettingsFailuresTag = "settings.failures"
const val SettingsOutcomeTag = "settings.outcome"
const val SettingsVersionRowTag = "settings.versionRow"

/** The failed-profile-write banner above the identity block. */
const val SettingsProfileFailureTag = "settings.profileFailure"

/** The read-only account address under the identity header. */
const val SettingsEmailRowTag = "settings.email"

/** The Server row and the restart notice it can raise. Both tags, because a scenario has to be
 * able to say "the row is there" and "the notice says so" as two separate claims. */
const val SettingsServerRowTag = "settings.serverRow"
const val SettingsServerNoticeTag = "settings.serverNotice"

/**
 * The two admin-only entries. Tags of their own rather than one shared tag, because the gate and
 * the two dispatch assertions are separate claims: a build that dropped one row, or wired both
 * to the same callback, has to fail rather than pass on a single match.
 */
const val SettingsManageMembersTag = "settings.manageMembers"
const val SettingsManageProjectsTag = "settings.manageProjects"

/** Tags for change password and forgot password rows. */
const val SettingsChangePasswordRowTag = "settings.changePassword"
const val SettingsForgotPasswordRowTag = "settings.forgotPassword"


