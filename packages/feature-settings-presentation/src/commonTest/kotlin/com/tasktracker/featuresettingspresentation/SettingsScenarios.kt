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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.featuresettingsdomain.UserStatus
import org.jetbrains.compose.resources.getString
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_activity_idle
import com.tasktracker.resources.settings_activity_syncing
import com.tasktracker.resources.settings_activity_waiting
import com.tasktracker.resources.settings_connectivity_offline
import com.tasktracker.resources.settings_connectivity_online
import com.tasktracker.resources.settings_outcome_offline
import com.tasktracker.resources.settings_position_label
import com.tasktracker.resources.settings_sync_stuck
import com.tasktracker.resources.settings_sync_waiting
import com.tasktracker.resources.settings_user_details_section
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
        // Scrolled: the User details header plus the status rows above pushed the sync section
        // past the fold, and `assertIsDisplayed` is about pixels — a row below the fold has none.
        onNodeWithText(getString(Res.string.settings_sync_waiting)).performScrollTo().assertIsDisplayed()
        // The number, not just the label: "Waiting to sync" with nothing beside it would be a
        // row that promises an answer it does not give.
        onNodeWithText("7").assertIsDisplayed()
        // Scrolled because the User details header above pushed the sync section past the fold
        // — `assertIsDisplayed` is about pixels, and a row below the fold has none.
        onNodeWithTag(SettingsStuckRowTag).performScrollTo().assertIsDisplayed()
        onNodeWithText("2").assertIsDisplayed()
        onNodeWithTag(SettingsFailuresTag).performScrollTo().assertIsDisplayed()
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
                    themeScheme = ThemeScheme.System,
                    version = SettingsPreviewData.version,
                    commandOutcome = if (tapped) SyncOutcome.Offline else null,
                    onThemeChange = {},
                    onSyncNow = { tapped = true },
                    onSignOut = {},
                )
            }
        }

        // Live, even though a cycle could not start: the tap is what produces the answer.
        onNodeWithTag(SettingsSyncNowTag).performScrollTo().assertIsDisplayed()
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
        var chosen: UserDetails? = null
        setContent {
            Themed {
                SettingsScreen(
                    connectivity = ConnectivitySummary.Online,
                    activity = SyncActivity(phase = SyncActivity.Phase.UpToDate),
                    themeScheme = ThemeScheme.System,
                    version = SettingsPreviewData.version,
                    commandOutcome = null,
                    onThemeChange = {},
                    onSyncNow = { syncs++ },
                    onSignOut = { signsOut++ },
                    userDetails = UserDetails(),
                    onUserDetailsChange = { chosen = it },
                )
            }
        }

        // The list is scrolled *to* each control rather than the control into view: a
        // LazyColumn that has not reached an item leaves it out of the tree entirely, so
        // `performScrollTo` on the node itself has nothing to act on — `performScrollToNode`
        // walks the list until the row is composed. The User details header above pushed all
        // three rows past the fold on a short viewport.
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsSyncNowTag))
        onNodeWithTag(SettingsSyncNowTag).performClick()
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsSignOutTag))
        onNodeWithTag(SettingsSignOutTag).performClick()
        waitForIdle()

        assertEquals(1, syncs)
        assertEquals(1, signsOut)

        // The status picker sits in the User details section and opens its dropdown, where
        // choosing a state routes the whole UserDetails back through `onUserDetailsChange`.
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsUserStatusRowTag))
        onNodeWithTag(SettingsUserStatusRowTag).performClick()
        waitForIdle()
        onNodeWithText("Away").performClick()
        waitForIdle()
        assertEquals(UserDetails(status = UserStatus.Away), chosen)
    }

    /**
     * The User details section leads the Settings page: its heading, the identity row and the
     * position picker, all read **on mount, without scrolling**.
     *
     * The missing scroll is the assertion. A LazyColumn drops items below its composition
     * window from the tree entirely, so these nodes exist on first frame only when the section
     * is at the top — filed anywhere past the fold the same assertions go red with "could not
     * find any node". `assertIsDisplayed`, not `assertExists`: existence alone would pass for a
     * section composed one screen down.
     */
    val theUserDetailsSectionIsOnThePage: suspend ComposeUiTest.() -> Unit = {
        screenShowing()
        onNodeWithTag(SettingsUserDetailsTag).assertIsDisplayed()
        onNodeWithTag(SettingsPositionRowTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.settings_user_details_section)).assertIsDisplayed()
        // The position picker labels itself, which is the string the section ships with.
        onNodeWithText(getString(Res.string.settings_position_label)).assertIsDisplayed()
    }

    /**
     * Tapping the avatar opens the portrait picker, all ten bundled portraits answer, and the
     * choice reaches the callback as `avatarId` with the rest of the details untouched.
     *
     * The count matters as much as the click: a picker that rendered one swatch would pass a
     * test that only tapped the row it happened to show. And the expected value is a whole
     * [UserDetails], because `copy(avatarId = …)` that dropped the name would be a portrait
     * picker that quietly renames its user.
     */
    val choosingAnAvatarRoutesItsIdIntoTheDetails: suspend ComposeUiTest.() -> Unit = {
        var chosen: UserDetails? = null
        setContent {
            Themed {
                SettingsScreen(
                    connectivity = ConnectivitySummary.Online,
                    activity = SyncActivity(),
                    themeScheme = ThemeScheme.System,
                    version = SettingsPreviewData.version,
                    commandOutcome = null,
                    onThemeChange = {},
                    onSyncNow = {},
                    onSignOut = {},
                    userDetails = UserDetails(fullName = "Sam", avatarId = null),
                    onUserDetailsChange = { chosen = it },
                )
            }
        }

        // The placeholder is the button: the picker is closed until the disc is tapped.
        onNodeWithTag(SettingsAvatarTag).assertIsDisplayed()
        onNodeWithTag(AvatarPickerTag).assertDoesNotExist()

        onNodeWithTag(SettingsAvatarTag).performClick()
        waitForIdle()
        onNodeWithTag(AvatarPickerTag).assertIsDisplayed()
        (0..9).forEach { id -> onNodeWithTag(AvatarOptionTag(id)).assertExists() }

        onNodeWithTag(AvatarOptionTag(7)).performClick()
        waitForIdle()

        assertEquals(
            UserDetails(fullName = "Sam", avatarId = 7),
            chosen,
            "picking portrait 7 must route details.copy(avatarId = 7) with the name intact — " +
                "a null or partial value here is the picker silently not saving",
        )
        onNodeWithTag(AvatarPickerTag).assertDoesNotExist()
    }

    /** The About row is present when there is a version and absent when there is not. */
    val theVersionRowHidesItselfWithNoVersion: suspend ComposeUiTest.() -> Unit = {
        screenShowing(version = SettingsPreviewData.version)
        // 6: the User details section leads the list (index 1), pushing About to 6.
        onNodeWithTag(SettingsScreenTag).performScrollToIndex(6)
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
                themeScheme = ThemeScheme.System,
                version = version,
                commandOutcome = commandOutcome,
                onThemeChange = {},
                onSyncNow = {},
                onSignOut = {},
            )
        }
    }
}

/**
 * The theme every scenario here is drawn in, so previews and tests agree.
 *
 * `content =` is named rather than positional because `ttTheme` grew an `accentPalette`
 * parameter between `scheme` and `content`, so a positional call now binds the palette to a
 * composable. Same one-argument fix as `WorkItemCardScenarios`; the two modules failed their
 * test *compilation* together, so neither reported any executions at all.
 */
@Composable
internal fun Themed(content: @Composable () -> Unit) {
    ttTheme(ThemeScheme.Dark, content = content)
}