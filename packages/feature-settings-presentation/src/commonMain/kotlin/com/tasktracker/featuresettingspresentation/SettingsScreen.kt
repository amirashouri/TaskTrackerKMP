package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttMonospacedDigits
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
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
import com.tasktracker.resources.settings_force_offline
import com.tasktracker.resources.settings_appearance_section
import com.tasktracker.resources.settings_outcome_already_running
import com.tasktracker.resources.settings_outcome_offline
import com.tasktracker.resources.settings_sign_out
import com.tasktracker.resources.settings_status_label
import com.tasktracker.resources.settings_sync_now
import com.tasktracker.resources.settings_sync_section
import com.tasktracker.resources.settings_sync_stuck
import com.tasktracker.resources.settings_sync_waiting
import com.tasktracker.resources.settings_theme_label
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
    isForceOffline: Boolean,
    themeScheme: ThemeScheme,
    version: AppVersion,
    commandOutcome: SyncOutcome?,
    onThemeChange: (ThemeScheme) -> Unit,
    onForceOfflineChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(ttSpacing.screenGutterLarge)
            .testTag(SettingsScreenTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        item {
            SettingsSection(title = stringResource(Res.string.settings_appearance_section)) {
                ThemePickerRow(
                    selected = themeScheme,
                    onSelect = onThemeChange,
                )
            }
        }
        item {
            SettingsSection(title = stringResource(Res.string.settings_connection_section)) {
                SettingsLabeledRow(
                    label = stringResource(Res.string.settings_status_label),
                    value = connectivityLabel(connectivity),
                )
                SettingsToggleRow(
                    label = stringResource(Res.string.settings_force_offline),
                    isOn = isForceOffline,
                    onToggle = onForceOfflineChange,
                )
            }
        }
        item {
            SettingsSection(title = stringResource(Res.string.settings_sync_section)) {
                SettingsLabeledRow(
                    label = stringResource(Res.string.settings_status_label),
                    value = activityLabel(activity.phase),
                )
                // The queue depth is the number a user actually cares about: it is how many of
                // their edits are still only on this device.
                activity.outstandingCount?.let { outstanding ->
                    SettingsLabeledRow(
                        label = stringResource(Res.string.settings_sync_waiting),
                        value = outstanding.toString(),
                        modifier = Modifier.testTag(SettingsWaitingRowTag),
                    )
                }
                // Dead rows are kept rather than dropped, so this can be non-zero, and it is
                // then the only sign that something is stuck.
                activity.stuckCount?.let { stuck ->
                    SettingsLabeledRow(
                        label = stringResource(Res.string.settings_sync_stuck),
                        value = stuck.toString(),
                        valueColor = ttError(),
                        modifier = Modifier.testTag(SettingsStuckRowTag),
                    )
                }
                if (activity.failures.isNotEmpty()) {
                    ttText(
                        text = activity.failures.joinToString(separator = "\n"),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                        modifier = Modifier.testTag(SettingsFailuresTag),
                    )
                }
                // Only a request that could not start lands here. The button below is
                // deliberately *not* disabled for it: greying a control out for a reason the
                // user cannot see is how a dead button gets reported as a bug.
                commandOutcome?.let { outcome ->
                    outcomeMessage(outcome)?.let { message ->
                        ttText(
                            text = message,
                            style = ttTypography.footnote,
                            color = ttError(),
                            modifier = Modifier.testTag(SettingsOutcomeTag),
                        )
                    }
                }
                ttGlassButton(
                    label = stringResource(Res.string.settings_sync_now),
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth().testTag(SettingsSyncNowTag),
                    style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                    // The one state that disables it: a cycle already running, which the status
                    // row above is already saying out loud. Offline is *not* a reason — that
                    // tap has to be answerable, and the answer is what lands above.
                    enabled = !activity.isSyncing,
                )
            }
        }
        item {
            SettingsSection(title = stringResource(Res.string.settings_account_section)) {
                // Through the port, which the app backs with the session coordinator: that is
                // what forgets the credentials, ends the session-scoped state and routes back
                // to sign-in. Tearing down from here would leave the tokens behind.
                ttGlassButton(
                    label = stringResource(Res.string.settings_sign_out),
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().testTag(SettingsSignOutTag),
                    style = TTGlassButtonStyle(tint = ttError(), prominent = true),
                )
            }
        }
        // The About section hides itself entirely rather than rendering an empty one: a
        // version row with nothing beside it is a row that says the app does not know its own
        // version, which is only true for a build with no version keys at all.
        version.display?.let { displayed ->
            item {
                SettingsSection(title = stringResource(Res.string.settings_about_section)) {
                    SettingsLabeledRow(
                        label = stringResource(Res.string.settings_version),
                        value = displayed,
                        modifier = Modifier.testTag(SettingsVersionRowTag),
                    )
                }
            }
        }
    }
}

/**
 * The section frame, so all five read as the same kind of group.
 *
 * A labelled glass surface rather than a list `Section`, because this build has no Material3
 * and because the sections are not a `Form`: the rows inside are individually interactive and
 * the glass is what separates them from the ambient backdrop, which is the app's whole visual
 * language on every other surface.
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
        ttText(
            text = title,
            style = ttTypography.caption,
            color = ttLabelSecondary(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.control))
                .padding(ttSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            content = { content() },
        )
    }
}

/** A label and a value on one line, the platform's own `LabeledContent` in this app's idiom. */
@Composable
private fun SettingsLabeledRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = ttLabel(),
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(text = label, style = ttTypography.body, color = ttLabelSecondary())
        ttText(
            text = value,
            style = ttTypography.body.ttMonospacedDigits(),
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A switch, drawn rather than imported.
 *
 * There is no Material3 `Switch` in this build, and the alternative — a `Checkbox` from the
 * same library — would bring the dependency the design decision rejected. So the indicator is
 * a glass surface that fills with the accent when on, and the whole row is the hit target
 * rather than the 20dp box: a 20dp target is below the platform minimum, and this is a
 * setting a user reaches for with one hand.
 */
@Composable
private fun SettingsToggleRow(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .clickable { onToggle(!isOn) }
            .testTag(SettingsForceOfflineTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(text = label, style = ttTypography.body, color = ttLabel())
        Box(
            modifier = Modifier
                .size(20.dp)
                .ttGlassSurface(
                    shape = RoundedCornerShape(ttRadius.row),
                    tint = if (isOn) ttAccent() else null,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isOn) {
                // A plain fill, not a second glass surface: a lens over nothing is glass again,
                // so the indicator would read as *unfilled* at exactly the moment the user needs
                // to see that it is on. One dot, one fill, one meaning.
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(ttOnAccent, CircleShape),
                )
            }
        }
    }
}

/**
 * The three schemes, as one row of segments.
 *
 * `ThemeScheme.title` rather than three strings in `Res`: the design system already owns these
 * three words as part of the type it defines, and a second copy in `strings.xml` would be two
 * sources for one sentence.
 */
@Composable
private fun ThemePickerRow(
    selected: ThemeScheme,
    onSelect: (ThemeScheme) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
        ttText(
            text = stringResource(Res.string.settings_theme_label),
            style = ttTypography.body,
            color = ttLabelSecondary(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ThemeScheme.all.forEach { scheme ->
                val isSelected = scheme == selected
                ttGlassButton(
                    label = scheme.title,
                    onClick = { onSelect(scheme) },
                    // `alpha` rather than swapping the whole style for the unselected case:
                    // the unselected segment stays glass and the selected one is filled, so
                    // "which is on" is answered by the fill rather than by the text weight.
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (isSelected) 1f else 0.6f)
                        .testTag(ThemeOptionTag(scheme)),
                    style = if (isSelected) {
                        TTGlassButtonStyle(tint = ttAccent(), prominent = true)
                    } else {
                        TTGlassButtonStyle()
                    },
                )
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
const val SettingsForceOfflineTag = "settings.forceOffline"
const val SettingsWaitingRowTag = "settings.waitingRow"
const val SettingsStuckRowTag = "settings.stuckRow"
const val SettingsFailuresTag = "settings.failures"
const val SettingsOutcomeTag = "settings.outcome"
const val SettingsVersionRowTag = "settings.versionRow"

/** Per-scheme test tag, so a scenario can tap "Dark" without a positional matcher. */
fun ThemeOptionTag(scheme: ThemeScheme): String = "settings.theme.${scheme.wireValue}"