package com.tasktracker.featuresettingspresentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.tasktracker.core.AppError
import com.tasktracker.designsystem.TTBannerDismissTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.ServerAddressSettings
import com.tasktracker.featuresettingsdomain.ServerAddressValidation
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.featuresettingsdomain.UserStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_activity_idle
import com.tasktracker.resources.settings_activity_syncing
import com.tasktracker.resources.settings_activity_waiting
import com.tasktracker.resources.settings_connectivity_offline
import com.tasktracker.resources.settings_connectivity_online
import com.tasktracker.resources.settings_outcome_offline
import com.tasktracker.resources.settings_position_label
import com.tasktracker.resources.settings_profile_save_failed
import com.tasktracker.resources.settings_server_error_cleartext_not_permitted
import com.tasktracker.resources.settings_server_saved_notice
import com.tasktracker.resources.settings_sync_waiting
import org.jetbrains.compose.resources.getString
import kotlin.math.abs
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

        val idleText = getString(Res.string.settings_activity_idle)

        // Scroll the LazyColumn to the idle row so it actually gets composed!
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasText(idleText))

        onNodeWithText(getString(Res.string.settings_connectivity_online)).assertIsDisplayed()
        onNodeWithText(idleText).assertIsDisplayed()
        onNodeWithTag(SettingsStuckRowTag).assertDoesNotExist()
        onNodeWithTag(SettingsWaitingRowTag).assertDoesNotExist()
    }

    /**
     * Offline with a queue is the state the screen exists to explain, and all of it shows.
     *
     * Seven rows are in the outbox and two of them are dead, so the two count rows are
     * **disjoint**: five waiting, two stuck. They used to overlap — the waiting row read the
     * full depth — which told the user about nine items where there were seven, and left the
     * waiting row unclearable once the only rows left were the dead ones, because a dead row
     * is never claimed again.
     */
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

        val waitingText = getString(Res.string.settings_activity_waiting)
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasText(waitingText))

        onNodeWithText(getString(Res.string.settings_connectivity_offline)).assertIsDisplayed()
        onNodeWithText(waitingText).assertIsDisplayed()
        // Scrolled: the User details header plus the status rows above pushed the sync section
        // past the fold, and `assertIsDisplayed` is about pixels — a row below the fold has none.
        onNodeWithText(getString(Res.string.settings_sync_waiting)).performScrollTo().assertIsDisplayed()
        // The number, not just the label: "Waiting to sync" with nothing beside it would be a
        // row that promises an answer it does not give. Five rather than the seven in the queue,
        // because two of the seven are dead — those are the row below.
        onNodeWithText("5").assertIsDisplayed()
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

        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsSyncNowTag))

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
        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsSyncNowTag))
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
     * The User details section leads the Settings page: the identity row and the position
     * picker, both read **on mount, without scrolling**.
     *
     * The missing scroll is the assertion. A LazyColumn drops items below its composition
     * window from the tree entirely, so these nodes exist on first frame only when the section
     * is at the top — filed anywhere past the fold the same assertions go red with "could not
     * find any node". `assertIsDisplayed`, not `assertExists`: existence alone would pass for a
     * section composed one screen down.
     *
     * There is deliberately no heading assertion: section titles were removed from Settings in
     * `61fb776`, and the identity row is the section's first visible content. The row tags are
     * the contract now; the old `settings_user_details_section` string is unused.
     */
    val theUserDetailsSectionIsOnThePage: suspend ComposeUiTest.() -> Unit = {
        screenShowing()
        onNodeWithTag(SettingsUserDetailsTag).assertIsDisplayed()
        onNodeWithTag(SettingsPositionRowTag).assertIsDisplayed()
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
        // One composition whose version argument changes, rather than two `setContent` calls.
        // The property under test is the row's *presence*, which is decided by a `?.let` at
        // composition rather than by a null check at draw, so recomposing with the versionless
        // value is the same measurement — and Android's `ComponentActivity` rejects a second
        // `setContent` outright, which two calls made this scenario a host-specific failure.
        var version by mutableStateOf(SettingsPreviewData.version)
        setContent {
            Themed {
                SettingsScreen(
                    connectivity = ConnectivitySummary.Online,
                    activity = SyncActivity(),
                    themeScheme = ThemeScheme.System,
                    version = version,
                    commandOutcome = null,
                    onThemeChange = {},
                    onSyncNow = {},
                    onSignOut = {},
                )
            }
        }
        // 5: the User details section leads the list (index 0), pushing About to 5. The title
        // used to sit at 0 and the number was 6; it was 6 because of the title, not because
        // the list is long.
        onNodeWithTag(SettingsScreenTag).performScrollToIndex(5)
        onNodeWithTag(SettingsVersionRowTag).assertIsDisplayed()

        version = SettingsPreviewData.versionless
        waitForIdle()
        onNodeWithTag(SettingsVersionRowTag).assertDoesNotExist()
    }

    /**
     * The status field's text sits on the vertical centre of its glass pill.
     *
     * Composed directly rather than through the screen's `LazyColumn`, because a lazy list
     * drops items below its composition window and the three hosts disagree about the viewport
     * — a row measured through the list would be laid out on the JVM and iOS and uncomposed on
     * the short Android Robolectric screen. The pill is the tagged outer `Box`; the field is the
     * `BasicTextField`'s own semantics node, found by its text. The assertion is the two centres
     * agreeing within 1dp — a `Box` that still defaulted to `TopStart` pins the field to the top
     * of the 36dp content area and lands it 4dp high (measured 20.0dp against the pill's 24.0dp),
     * which is what this catches.
     */
    val theStatusFieldSitsCentredInItsPill: suspend ComposeUiTest.() -> Unit = {
        val text = "Back on Monday"
        setContent {
            Themed {
                StatusTextEditor(
                    value = text,
                    onValueChange = {},
                    modifier = Modifier.testTag(SettingsStatusTextRowTag),
                )
            }
        }

        val pill = onNodeWithTag(SettingsStatusTextRowTag).getBoundsInRoot()
        val field = onNodeWithText(text).getBoundsInRoot()
        val pillCentreY = (pill.top.value + pill.bottom.value) / 2f
        val fieldCentreY = (field.top.value + field.bottom.value) / 2f
        val offset = abs(pillCentreY - fieldCentreY)

        assertTrue(
            offset < 1f,
            "the status field's centre is ${fieldCentreY}dp against the pill's ${pillCentreY}dp — " +
                "${offset}dp apart, so the text rides high in the glass pill instead of sitting on " +
                "its centre (a top-aligned Box leaves it 4dp off)",
        )
    }

    /**
     * A member is not an administrator, so the Admin section is not in the tree at all.
     *
     * The absence is the assertion, and it is only meaningful because the section sits high on
     * the page: a row below the fold is not composed, and `assertDoesNotExist` would then pass
     * whether or not the gate held — the false green this scenario exists to avoid.
     */
    val theAdminSectionIsHiddenFromAMember: suspend ComposeUiTest.() -> Unit = {
        screenShowing(isAdmin = false)

        onNodeWithTag(SettingsManageMembersTag).assertDoesNotExist()
        onNodeWithTag(SettingsManageProjectsTag).assertDoesNotExist()
    }

    /** An administrator sees both entries, and each dispatches its own command exactly once. */
    val anAdminSeesTheManagementEntriesAndTheyDispatch: suspend ComposeUiTest.() -> Unit = {
        var membersTaps = 0
        var projectsTaps = 0
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
                    isAdmin = true,
                    onManageMembers = { membersTaps++ },
                    onManageProjects = { projectsTaps++ },
                )
            }
        }

        onNodeWithTag(SettingsManageMembersTag).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SettingsManageProjectsTag).performScrollTo().assertIsDisplayed()

        onNodeWithTag(SettingsManageMembersTag).performClick()
        onNodeWithTag(SettingsManageProjectsTag).performClick()

        assertEquals(1, membersTaps, "the Manage Members row must dispatch its own command")
        assertEquals(1, projectsTaps, "the Manage Projects row must dispatch its own command")
    }

    /**
     * The Server row shows the address the running clients are bound to.
     *
     * Scrolled to rather than asserted on mount: the row sits below User details and
     * Appearance, and a `LazyColumn` does not compose what is past the fold — an
     * `assertIsDisplayed` without the scroll would be a host-dependent pass.
     */
    val theServerRowShowsTheAddressInUse: suspend ComposeUiTest.() -> Unit = {
        screenShowing(
            serverAddress = ServerAddressSettings(
                inUse = "https://tasks.example.com",
                savedOverride = null,
            ),
        )

        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsServerRowTag))
        onNodeWithTag(SettingsServerRowTag).assertIsDisplayed()
        onNodeWithText("https://tasks.example.com").assertIsDisplayed()
    }

    /**
     * Tapping the row opens the editor, prefilled with the address the row showed.
     *
     * The editor state is local to the scenario because visibility is the model's in
     * production; what this pins is the screen's half — the row dispatches, and a non-null
     * editor renders the sheet with the draft in it.
     */
    val tappingTheServerRowOpensTheEditor: suspend ComposeUiTest.() -> Unit = {
        var editor by mutableStateOf<ServerAddressEditor?>(null)
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
                    serverAddress = ServerAddressSettings(
                        inUse = "https://tasks.example.com",
                        savedOverride = null,
                    ),
                    serverAddressEditor = editor,
                    onEditServerAddress = {
                        editor = ServerAddressEditor(draft = "https://tasks.example.com")
                    },
                    onDismissServerAddress = { editor = null },
                )
            }
        }

        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsServerRowTag))
        onNodeWithTag(SettingsServerRowTag).performClick()
        waitForIdle()

        onNodeWithTag(ServerAddressSheetTag).assertIsDisplayed()
        onNodeWithTag(ServerAddressFieldTag).assertIsDisplayed()
    }

    /** A refused address keeps the sheet open and says which part was wrong. */
    val aRefusedAddressShowsItsReason: suspend ComposeUiTest.() -> Unit = {
        screenShowing(
            serverAddress = ServerAddressSettings(
                inUse = "https://tasks.example.com",
                savedOverride = null,
            ),
            serverAddressEditor = ServerAddressEditor(
                draft = "http://example.com",
                failure = ServerAddressValidation.Invalid(
                    ServerAddressValidation.Reason.CleartextNotPermitted,
                ),
            ),
        )
        waitForIdle()

        onNodeWithTag(ServerAddressSheetTag).assertIsDisplayed()
        onNodeWithTag(ServerAddressErrorTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.settings_server_error_cleartext_not_permitted))
            .assertIsDisplayed()
    }

    /**
     * Reset is present only when an override exists.
     *
     * One composition whose state changes, not two `setContent` calls, for the reason the
     * version scenario gives: a `LazyColumn` and Android's `ComponentActivity` both make the
     * second call the wrong shape. The negative case is the assertion that matters — a Reset
     * on a build with no override is a control that claims to have changed something.
     */
    val resetIsOfferedOnlyWhenAnOverrideExists: suspend ComposeUiTest.() -> Unit = {
        var override by mutableStateOf<String?>(null)
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
                    serverAddress = ServerAddressSettings(
                        inUse = "https://tasks.example.com",
                        savedOverride = override,
                    ),
                    serverAddressEditor = ServerAddressEditor(draft = "https://tasks.example.com"),
                )
            }
        }
        waitForIdle()

        onNodeWithTag(ServerAddressResetTag).assertDoesNotExist()

        override = "https://old.example.com"
        waitForIdle()

        onNodeWithTag(ServerAddressResetTag).assertIsDisplayed()
    }

    /**
     * While the check runs, the only available action is waiting.
     *
     * The save is already dispatched to the shell's scope, so neither Cancel nor Reset can
     * cancel it: Cancel would hide a save that then lands, and Reset would race it. The dimmed
     * controls are the honest signal that the check owns the sheet until it answers.
     */
    val aCheckInFlightDisablesTheOtherActions: suspend ComposeUiTest.() -> Unit = {
        screenShowing(
            serverAddress = ServerAddressSettings(
                inUse = "https://tasks.example.com",
                savedOverride = "https://old.example.com",
            ),
            serverAddressEditor = ServerAddressEditor(
                draft = "https://new.example.com",
                isChecking = true,
            ),
        )
        waitForIdle()

        onNodeWithTag(ServerAddressResetTag).assertIsNotEnabled()
        onNodeWithTag(ServerAddressCancelTag).assertIsNotEnabled()
    }

    /**
     * A save raises the restart notice, and the notice says *when* the change applies.
     *
     * The row still reads the in-use address in this state, which is the honest pairing: the
     * notice is the only thing that can explain why the address the user just saved is not the
     * address on screen.
     */
    val aSavedChangeRaisesTheRestartNotice: suspend ComposeUiTest.() -> Unit = {
        screenShowing(
            serverAddress = ServerAddressSettings(
                inUse = "https://tasks.example.com",
                savedOverride = "https://new.example.com",
            ),
            serverAddressNotice = ServerAddressNotice.Saved,
        )

        onNodeWithTag(SettingsScreenTag).performScrollToNode(hasTestTag(SettingsServerNoticeTag))
        onNodeWithTag(SettingsServerNoticeTag).assertIsDisplayed()
        val notice = getString(Res.string.settings_server_saved_notice)
        onNodeWithText(notice).assertIsDisplayed()
        assertTrue(
            notice.contains("next time"),
            "\"$notice\" does not say when the saved address takes effect",
        )
    }

    /**
     * The name has an editor, and saving it stages the new value and asks for one write.
     *
     * Two callbacks rather than one because the screen's job is split: the staged value is what
     * the header shows immediately, and the commit is the network step that can fail. A test
     * that only saw the staged value would pass for a screen that never asked the server.
     */
    val theNameSheetStagesAndCommitsANewName: suspend ComposeUiTest.() -> Unit = {
        var staged: UserDetails? = null
        var commits = 0
        screenShowing(
            userDetails = UserDetails(fullName = "Sam", email = "sam@example.com"),
            onUserDetailsChange = { staged = it },
            onUserDetailsCommit = { commits++ },
        )

        onNodeWithTag(SettingsEditNameTag).performClick()
        waitForIdle()
        onNodeWithTag(UserNameSheetTag).assertIsDisplayed()

        onNodeWithTag(UserNameFieldTag).performTextClearance()
        onNodeWithTag(UserNameFieldTag).performTextInput("Samantha")
        waitForIdle()
        onNodeWithTag(UserNameSaveTag).assertIsEnabled()
        onNodeWithTag(UserNameSaveTag).performClick()
        waitForIdle()

        assertEquals(
            UserDetails(fullName = "Samantha", email = "sam@example.com"),
            staged,
            "the sheet must route the whole profile with only the name changed",
        )
        assertEquals(1, commits, "saving the name must ask for exactly one write")
        onNodeWithTag(UserNameSheetTag).assertDoesNotExist()
    }

    /**
     * Save is unavailable for a blank or an unchanged name.
     *
     * The unchanged case is the one a blank guard alone misses: pressing Save on the value that
     * is already there would be a PATCH with an empty delta, and the closed sheet would read as
     * evidence that something was saved.
     */
    val theNameSaveIsDisabledForABlankOrUnchangedName: suspend ComposeUiTest.() -> Unit = {
        screenShowing(userDetails = UserDetails(fullName = "Sam", email = "sam@example.com"))

        onNodeWithTag(SettingsEditNameTag).performClick()
        waitForIdle()

        onNodeWithTag(UserNameSaveTag).assertIsNotEnabled()

        onNodeWithTag(UserNameFieldTag).performTextClearance()
        waitForIdle()
        onNodeWithTag(UserNameSaveTag).assertIsNotEnabled()

        onNodeWithTag(UserNameFieldTag).performTextInput("Samantha")
        waitForIdle()
        onNodeWithTag(UserNameSaveTag).assertIsEnabled()
    }

    /**
     * The note writes when the user finishes, and never past the server's limit.
     *
     * The zero after typing is the assertion that matters: a field that committed per keystroke
     * would still pass the final `1`, while having sent eight PATCHes and raced seven of them.
     * `performImeAction` is the Done path the keyboard's return key takes.
     */
    val theStatusNoteCommitsOnDoneAndStopsAtTheLimit: suspend ComposeUiTest.() -> Unit = {
        var value by mutableStateOf("")
        var commits = 0
        setContent {
            Themed {
                StatusTextEditor(
                    value = value,
                    onValueChange = { value = it },
                    onCommit = { commits++ },
                )
            }
        }

        val field = onNode(hasSetTextAction())
        field.performTextInput("On vacation")
        waitForIdle()

        assertEquals("On vacation", value)
        assertEquals(0, commits, "typing must not write; only Done and focus loss do")

        field.performImeAction()
        waitForIdle()
        assertEquals(1, commits)

        // A paste is cut to 280 rather than refused outright, so the field never holds a value
        // the server would answer with a 400.
        field.performTextClearance()
        field.performTextInput("x".repeat(300))
        waitForIdle()
        assertEquals(280, value.length)
    }

    /**
     * A refused write is reported above the identity block, and dismissing the report leaves
     * the reverted profile untouched.
     *
     * The banner is a report, not a question: the edit was reverted before it appeared, so there
     * is nothing to retry from here. The name below is still the confirmed one, which is the
     * other half of that promise.
     */
    val aFailedProfileWriteRaisesADismissibleBanner: suspend ComposeUiTest.() -> Unit = {
        var failure by mutableStateOf<AppError?>(AppError.Transport("the connection dropped"))
        var dismissed = 0
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
                    userDetails = UserDetails(fullName = "Sam", email = "sam@example.com"),
                    profileFailure = failure,
                    onDismissProfileFailure = {
                        dismissed++
                        failure = null
                    },
                )
            }
        }

        onNodeWithTag(SettingsProfileFailureTag).assertIsDisplayed()
        onNodeWithText(getString(Res.string.settings_profile_save_failed)).assertIsDisplayed()

        onNodeWithTag(TTBannerDismissTag).performClick()
        waitForIdle()

        assertEquals(1, dismissed)
        onNodeWithTag(SettingsProfileFailureTag).assertDoesNotExist()
        onNodeWithText("Sam").assertIsDisplayed()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.screenShowing(
    connectivity: ConnectivitySummary = ConnectivitySummary.Online,
    activity: SyncActivity = SyncActivity(),
    version: AppVersion = SettingsPreviewData.version,
    commandOutcome: SyncOutcome? = null,
    isAdmin: Boolean = false,
    serverAddress: ServerAddressSettings = ServerAddressSettings(inUse = null, savedOverride = null),
    serverAddressEditor: ServerAddressEditor? = null,
    serverAddressNotice: ServerAddressNotice? = null,
    userDetails: UserDetails = UserDetails(),
    profileFailure: AppError? = null,
    onUserDetailsChange: (UserDetails) -> Unit = {},
    onUserDetailsCommit: () -> Unit = {},
    onDismissProfileFailure: () -> Unit = {},
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
                isAdmin = isAdmin,
                serverAddress = serverAddress,
                serverAddressEditor = serverAddressEditor,
                serverAddressNotice = serverAddressNotice,
                userDetails = userDetails,
                onUserDetailsChange = onUserDetailsChange,
                onUserDetailsCommit = onUserDetailsCommit,
                profileFailure = profileFailure,
                onDismissProfileFailure = onDismissProfileFailure,
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