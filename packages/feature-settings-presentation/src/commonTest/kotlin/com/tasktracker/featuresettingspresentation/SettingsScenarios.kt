package com.tasktracker.featuresettingspresentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import org.jetbrains.compose.resources.getString
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_activity_idle
import com.tasktracker.resources.settings_activity_syncing
import com.tasktracker.resources.settings_activity_waiting
import com.tasktracker.resources.settings_connectivity_offline
import com.tasktracker.resources.settings_connectivity_online
import com.tasktracker.resources.settings_outcome_offline
import com.tasktracker.resources.settings_sync_stuck
import com.tasktracker.resources.settings_sync_waiting
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Settings screen, as scenarios.
 *
 * ## The rule these are really about
 *
 * **A row appears only when it has something to say.**
 *
 * The queue depth and the dead-letter count render only when
 * [SyncActivity.outstandingCount] and [SyncActivity.stuckCount] are non-null, so a screen that
 * always showed "Waiting to sync: 0" would read as a stuck app rather than a quiet one. The
 * scenario that matters is therefore the negative one — an idle screen with no counts shows
 * neither row — because that is the state a user sees most and the one where a stray zero would
 * do the most damage.
 *
 * ## The Sync Now control is disabled for exactly one reason
 *
 * While a cycle is running. Not when offline, and not after a previous outcome: a control that
 * goes dead for a reason the user cannot see is how a dead button gets reported as a bug. An
 * offline tap is *answered* with the queued-and-safe sentence instead.
 */
@OptIn(ExperimentalTestApi::class)
object SettingsScenarios {

    /** The quiet screen shows both status rows and neither count row. */
    val aQuietScreenSaysNothingAboutCounts: suspend ComposeUiTest.() -> Unit = {
        screenShowing(connectivity = ConnectivitySummary.Online, activity = SyncActivity())

        onNodeWithTag(SettingsScreenTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.settings_connectivity_online)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.settings_activity_idle)).assertIsDisplayed()
        // The absence is the assertion. Both rows would be *displayed* if a zero were rendered.
        onNodeWithTag(SettingsStuckRowTag).assertDoesNotExist()
        onNodeWithTag(SettingsWaitingRowTag).assertDoesNotExist()
    }

    /** Offline with a queue is the state the screen exists to explain, and all of it shows. */
    val offlineWorkIsExplainedRatherThanHidden: suspend ComposeUiTest.() -> Unit = {
        screenShowing(
            connectivity = ConnectivitySummary.Offline,
            activity = SyncActivity(
                phase = SyncActivity.Phase.WaitingForConnection,
                queueDepth = 7,
                deadLetterCount = 2,
                failures = listOf("TT-3: the server rejected the priority value."),
            ),
        )

        onNodeWithText(getString(Res.string.settings_connectivity_offline)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.settings_activity_waiting)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.settings_sync_waiting)).assertIsDisplayed()
        // The number, not just the label: "Waiting to sync" with nothing beside it would be a
        // row that promises an answer it does not give.
        onNodeWithText("7").assertIsDisplayed()
        onNodeWithTag(SettingsStuckRowTag).assertIsDisplayed()
        onNodeWithText("2").assertIsDisplayed()
        onNodeWithTag(SettingsFailuresTag).assertIsDisplayed()
        onNodeWithText("TT-3: the server rejected the priority value.").assertIsDisplayed()
    }

    /** A cycle in flight disables Sync Now, and is the only thing that does. */
    val onlyARunningCycleDisablesSyncNow: suspend ComposeUiTest.() -> Unit = {
        screenShowing(
            connectivity = ConnectivitySummary.Online,
            activity = SyncActivity(phase = SyncActivity.Phase.Syncing, queueDepth = 3),
        )

        onNodeWithText(getString(Res.string.settings_activity_syncing)).assertIsDisplayed()
        onNodeWithTag(SettingsSyncNowTag).assertIsNotEnabled()
    }

    /**
     * Offline with nothing running leaves the button live, and tapping it reports the reason.
     *
     * The sentence is the whole point: it says the edits are *queued*, so the user concludes
     * their work is safe rather than that the app dropped it.
     */
    val anOfflineTapIsAnsweredRatherThanIgnored: suspend ComposeUiTest.() -> Unit = {
        // Snapshot state, not a plain `var`: the tap has to *recompose* the screen for the
        // outcome row to appear, and a plain local would leave the tree holding the pre-tap
        // arguments forever. That is the failure mode this scenario exists to catch in reverse —
        // a model whose outcome never reaches the screen looks exactly like a screen that
        // ignores its command.
        var tapped by mutableStateOf(false)
        setContent {
            Themed {
                SettingsScreen(
                    connectivity = ConnectivitySummary.Offline,
                    activity = SyncActivity(phase = SyncActivity.Phase.WaitingForConnection),
                    isForceOffline = false,
                    themeScheme = ThemeScheme.System,
                    version = SettingsPreviewData.version,
                    commandOutcome = if (tapped) SyncOutcome.Offline else null,
                    onThemeChange = {},
                    onForceOfflineChange = {},
                    onSyncNow = { tapped = true },
                    onSignOut = {},
                )
            }
        }

        // Live, even though a cycle could not start: the tap is what produces the answer.
        onNodeWithTag(SettingsSyncNowTag).assertIsDisplayed()
        onNodeWithTag(SettingsSyncNowTag).assertIsEnabled()
        onNodeWithTag(SettingsOutcomeTag).assertDoesNotExist()

        onNodeWithTag(SettingsSyncNowTag).performClick()
        waitForIdle()

        onNodeWithTag(SettingsOutcomeTag).assertIsDisplayed()
        val reassurance = getString(Res.string.settings_outcome_offline)
        onNodeWithText(reassurance).assertIsDisplayed()
        // The sentence has to promise the work is safe, not merely that nothing happened.
        assertTrue(reassurance.contains("queued"), "\"$reassurance\" does not say the edits are queued")
    }

    /** A started cycle has nothing to say, so no outcome row is rendered at all. */
    val aStartedCycleSaysNothing: suspend ComposeUiTest.() -> Unit = {
        screenShowing(
            connectivity = ConnectivitySummary.Online,
            activity = SyncActivity(phase = SyncActivity.Phase.UpToDate),
            commandOutcome = SyncOutcome.Started,
        )

        onNodeWithTag(SettingsOutcomeTag).assertDoesNotExist()
        onNodeWithTag(SettingsScreenTag).assertIsDisplayed()
    }

    /** Both commands are dispatched, which is the screen's entire job beyond rendering. */
    val theControlsDispatchTheirCommands: suspend ComposeUiTest.() -> Unit = {
        var syncs = 0
        var signsOut = 0
        var forced: Boolean? = null
        setContent {
            Themed {
                SettingsScreen(
                    connectivity = ConnectivitySummary.Online,
                    activity = SyncActivity(phase = SyncActivity.Phase.UpToDate),
                    isForceOffline = false,
                    themeScheme = ThemeScheme.System,
                    version = SettingsPreviewData.version,
                    commandOutcome = null,
                    onThemeChange = {},
                    onForceOfflineChange = { forced = it },
                    onSyncNow = { syncs++ },
                    onSignOut = { signsOut++ },
                )
            }
        }

        onNodeWithTag(SettingsSyncNowTag).performClick()
        onNodeWithTag(SettingsForceOfflineTag).performClick()
        onNodeWithTag(SettingsSignOutTag).performClick()
        waitForIdle()

        assertEquals(1, syncs)
        assertEquals(true, forced)
        assertEquals(1, signsOut)
    }

    /** The About row is present when there is a version and absent when there is not. */
    val theVersionRowHidesItselfWithNoVersion: suspend ComposeUiTest.() -> Unit = {
        screenShowing(version = SettingsPreviewData.version)
        onNodeWithTag(SettingsVersionRowTag).assertIsDisplayed()

        // Remounted rather than re-composed with a new argument: `AppVersion.display` is a
        // computed property, and the point of the scenario is the row's *presence*, which is
        // decided by a `?.let` at composition rather than by a null check at draw.
        screenShowing(version = SettingsPreviewData.versionless)
        onNodeWithTag(SettingsVersionRowTag).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.screenShowing(
    connectivity: ConnectivitySummary = ConnectivitySummary.Online,
    activity: SyncActivity = SyncActivity(),
    version: AppVersion = SettingsPreviewData.version,
    commandOutcome: SyncOutcome? = null,
) {
    setContent {
        Themed {
            SettingsScreen(
                connectivity = connectivity,
                activity = activity,
                isForceOffline = false,
                themeScheme = ThemeScheme.System,
                version = version,
                commandOutcome = commandOutcome,
                onThemeChange = {},
                onForceOfflineChange = {},
                onSyncNow = {},
                onSignOut = {},
            )
        }
    }
}

/** The theme every scenario here is drawn in, so previews and tests agree. */
@Composable
internal fun Themed(content: @Composable () -> Unit) {
    ttTheme(ThemeScheme.Dark, content)
}