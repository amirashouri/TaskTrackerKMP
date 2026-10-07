package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_about_section
import com.tasktracker.resources.settings_account_section
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
import com.tasktracker.resources.settings_connection_section
import com.tasktracker.resources.settings_appearance_section
import com.tasktracker.resources.settings_outcome_already_running
import com.tasktracker.resources.settings_outcome_offline
import com.tasktracker.resources.settings_sign_out
import com.tasktracker.resources.settings_status_label
import com.tasktracker.resources.settings_sync_now
import com.tasktracker.resources.settings_sync_section
import com.tasktracker.resources.settings_sync_stuck
import com.tasktracker.resources.settings_sync_waiting
import com.tasktracker.resources.settings_title
import com.tasktracker.resources.settings_user_details_section
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
    modifier: Modifier = Modifier,
    accentPalette: AccentPalette = AccentPalette.Classic,
    onAccentPaletteChange: (AccentPalette) -> Unit = {},
    glassIntensity: Float = 1f,
    onGlassIntensityPreview: (Float) -> Unit = {},
    onGlassIntensityCommit: (Float) -> Unit = {},
    userDetails: UserDetails = UserDetails(),
    onUserDetailsChange: (UserDetails) -> Unit = {},
)
 {
    LazyColumn(
        modifier = modifier
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
            ttText(
                text = stringResource(Res.string.settings_title),
                style = ttTypography.largeTitle,
                color = ttLabel(),
                modifier = Modifier.padding(bottom = ttSpacing.xs),
            )
        }
        // First section on the page, directly under the title: it is the identity the rest of
        // the screen belongs to, not a preference filed with the others. It renders even when
        // empty — the row that reads "—" is the honest answer, whereas a section that hides
        // itself would leave the user with no idea where their profile went. The position picker
        // dispatches through the same callback as every other edit here; the model persists it.
        item {
            SettingsSection(title = stringResource(Res.string.settings_user_details_section)) {
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
        item {
            SettingsSection(title = stringResource(Res.string.settings_appearance_section)) {
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
        item {
            SettingsSection(title = stringResource(Res.string.settings_connection_section)) {
                SettingsIconRow(
                    label = stringResource(Res.string.settings_status_label),
                    value = connectivityLabel(connectivity),
                    icon = ttSettingsIcons.connectivity,
                )
                SettingsDivider()
            }
        }
        item {
            SettingsSection(title = stringResource(Res.string.settings_sync_section)) {
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
            SettingsSection(title = stringResource(Res.string.settings_account_section)) {
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
                SettingsSection(title = stringResource(Res.string.settings_about_section)) {
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

const val SettingsScreenTag = "settings.screen"
const val SettingsSyncNowTag = "settings.syncNow"
const val SettingsSignOutTag = "settings.signOut"
const val SettingsWaitingRowTag = "settings.waitingRow"
const val SettingsStuckRowTag = "settings.stuckRow"
const val SettingsFailuresTag = "settings.failures"
const val SettingsOutcomeTag = "settings.outcome"
const val SettingsVersionRowTag = "settings.versionRow"


