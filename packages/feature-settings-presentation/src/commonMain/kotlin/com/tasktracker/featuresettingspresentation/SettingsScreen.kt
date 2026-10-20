package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.AppError
import com.tasktracker.designsystem.ConfirmPopup
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ttAccent
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
import com.tasktracker.resources.settings_connectivity_needs_connection
import com.tasktracker.resources.settings_connectivity_offline
import com.tasktracker.resources.settings_connectivity_online
import com.tasktracker.resources.settings_connectivity_unknown
import com.tasktracker.resources.settings_manage_members
import com.tasktracker.resources.settings_manage_projects
import com.tasktracker.resources.settings_outcome_already_running
import com.tasktracker.resources.settings_outcome_offline
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
) {
    // The list and the confirmation popup are siblings in one box, so the popup floats over
    // the list rather than scrolling with it. `modifier` lands here rather than on the
    // `LazyColumn`, which is what lets the overlay cover the whole screen instead of only the
    // list's viewport — the same shape `SprintDetailScreen` uses for its own confirmation.
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Top only: bottom padding here would shrink the *list's viewport*, and the sign-out row
                // would then never reach the screen's bottom edge — which is the bound the scroll is
                // supposed to have. Its clearance from the glass is `contentPadding`, below.
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.xl)
                .testTag(SettingsScreenTag),
            // Scroll range under the floating tab bar, not viewport inset — see
            // `LocalTabBarScrollUnderInset`. This is the list whose last row is the sign-out button,
            // so an inset that is a few dp short is a sign-out button parked under the glass. The
            // `ttSpacing.xl` is clearance rather than decoration: it is the same air the board gives
            // its lanes, and without it the last row comes to rest flush against the capsule's top
            // edge instead of above it.
            contentPadding = PaddingValues(bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            item {
                // First section on the page: it is the identity the rest of the screen belongs to,
                // not a preference filed with the others. It renders even when empty — the row that
                // reads "—" is the honest answer, whereas a section that hides itself would leave
                // the user with no idea where their profile went. The position picker dispatches
                // through the same callback as every other edit here; the model persists it.
                SettingsSection {
                    UserDetailsRow(
                        details = userDetails,
                        onDetailsChange = onUserDetailsChange,
                        modifier = Modifier.testTag(SettingsUserDetailsTag),
                    )
                    SettingsDivider()
                    UserPositionPickerRow(
                        selected = userDetails.position,
                        onSelect = { position ->
                            onUserDetailsChange(userDetails.copy(position = position))
                        },
                        modifier = Modifier.testTag(SettingsPositionRowTag),
                    )
                    SettingsDivider()
                    UserStatusPickerRow(
                        selected = userDetails.status,
                        onSelect = { status ->
                            onUserDetailsChange(userDetails.copy(status = status))
                        },
                        modifier = Modifier.testTag(SettingsUserStatusRowTag),
                    )
                    SettingsDivider()
                    StatusTextEditor(
                        value = userDetails.statusText,
                        onValueChange = { statusText ->
                            onUserDetailsChange(userDetails.copy(statusText = statusText))
                        },
                        modifier = Modifier.testTag(SettingsStatusTextRowTag),
                    )
                }
            }
            // The Admin section is drawn only for an administrator. It sits high on the page
            // because for the one person who can see it, member and project management are the
            // highest-value destinations here — and because a row below the fold would be a row
            // a test could not tell from an absent one.
            if (isAdmin) {
                item {
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
            item {
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
            // The Server section leads the connection group. Its row shows the address the
            // running clients are bound to — the honest reading, since a save cannot take
            // effect until the next launch — and the sheet edits the address the next launch
            // will read. A save or reset leaves a notice here saying so, which is the whole of
            // the restart promise surfaced where the user makes the choice.
            item {
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
            item {
                SettingsSection {
                    SettingsIconRow(
                        label = stringResource(Res.string.settings_status_label),
                        value = connectivityLabel(connectivity),
                        icon = ttSettingsIcons.connectivity,
                    )
                    SettingsDivider()
                }
            }
            item {
                SettingsSection {
                    SettingsIconRow(
                        label = stringResource(Res.string.settings_status_label),
                        value = activityLabel(activity.phase),
                        icon = ttSettingsIcons.sync,
                    )
                    // The queue depth is the number a user actually cares about: it is how many of
                    // their edits are still only on this device.
                    activity.outstandingCount?.let { outstanding ->
                        SettingsDivider()
                        SettingsIconRow(
                            label = stringResource(Res.string.settings_sync_waiting),
                            value = outstanding.toString(),
                            icon = ttSettingsIcons.sync,
                            modifier = Modifier.testTag(SettingsWaitingRowTag),
                        )
                    }
                    // Dead rows are kept rather than dropped, so this can be non-zero, and it is
                    // then the only sign that something is stuck.
                    activity.stuckCount?.let { stuck ->
                        SettingsDivider()
                        SettingsIconRow(
                            label = stringResource(Res.string.settings_sync_stuck),
                            value = stuck.toString(),
                            icon = ttSettingsIcons.sync,
                            valueColor = ttError(),
                            modifier = Modifier.testTag(SettingsStuckRowTag),
                        )
                        // The way out, and it sits here rather than beside Sync Now because it
                        // only means anything while the row above has a number on it. Without
                        // it that number is a dead end: a refused change is never claimed again,
                        // so Sync Now cannot move it and the only other answer was to delete
                        // the work and make it again.
                        SettingsDivider()
                        SettingsActionRow(
                            label = stringResource(Res.string.settings_sync_retry),
                            icon = ttSettingsIcons.sync,
                            tint = ttAccent(),
                            onClick = onRetryStuckChanges,
                            // Disabled on the same rule as Sync Now, and for the same reason:
                            // the retry ends by asking for a cycle, and that request would be
                            // dropped. Offline is not a reason here either — putting the rows
                            // back needs no network, and the cycle that follows says so.
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
                    // Only a request that could not start lands here. The button below is
                    // deliberately *not* disabled for it: greying a control out for a reason the
                    // user cannot see is how a dead button gets reported as a bug.
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
                        // The one state that disables it: a cycle already running, which the status
                        // row above is already saying out loud. Offline is *not* a reason — that
                        // tap has to be answerable, and the answer is what lands above.
                    )
                }
            }
            item {
                SettingsSection {
                    // Through the port, which the app backs with the session coordinator: that is
                    // what forgets the credentials, ends the session-scoped state and routes back
                    // to sign-in. Tearing down from here would leave the tokens behind.
                    SettingsActionRow(
                        label = stringResource(Res.string.settings_sign_out),
                        icon = ttSettingsIcons.account,
                        tint = ttError(),
                        onClick = onSignOut,
                        modifier = Modifier.testTag(SettingsSignOutTag),
                    )
                }
            }
            // The About section hides itself entirely rather than rendering an empty one: a
            // version row with nothing beside it is a row that says the app does not know its own
            // version, which is only true for a build with no version keys at all.
            version.display?.let { displayed ->
                item {
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


