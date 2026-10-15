package com.tasktracker.featuresettingspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.AppError
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.featuresettingsdomain.SyncActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Fixtures for previews, and a [SettingsPort] small enough to stand in for the real service.
 *
 * ## Why this lives in `commonMain`
 *
 * The same reason `ViewPreviewData` and `AuthPreviewData` do: the KMP convention gives this
 * module no `debug` source set, and a fixture in `commonTest` is not reachable from a
 * `commonMain` preview. It is also what lets a Compose UI test in `commonTest` reuse the same
 * shapes the previews show.
 *
 * ## This is not the test fake
 *
 * `FakeSettingsPort` in `commonTest` counts calls, because what its tests assert is which port
 * method the model reached. This one answers and forgets, because a preview should not need a
 * scenario arranged around it.
 */
object SettingsPreviewData {

    /** The version a shipped build reports. */
    val version: AppVersion = AppVersion(marketing = "1.0", build = "42")

    /** A build with no version keys, so the About row has to hide itself. */
    val versionless: AppVersion = AppVersion(marketing = "", build = "")

    /** The quiet case: connected, synced, nothing queued. */
    val quiet: SettingsPort = PreviewSettingsPort(
        connectivity = ConnectivitySummary.Online,
        activity = SyncActivity(phase = SyncActivity.Phase.UpToDate),
        version = version,
    )

    /**
     * The state this screen exists to explain: a device genuinely offline, holding edits that
     * have gone nowhere, with some the server refused outright.
     */
    val offlineWithQueuedWork: SettingsPort = PreviewSettingsPort(
        connectivity = ConnectivitySummary.Offline,
        activity = SyncActivity(
            phase = SyncActivity.Phase.WaitingForConnection,
            queueDepth = 7,
            deadLetterCount = 2,
            failures = listOf("TT-3: the server rejected the priority value."),
        ),
        version = version,
    )

    /** Mid-cycle, so the Sync Now control is disabled and the status says Syncing. */
    val syncing: SettingsPort = PreviewSettingsPort(
        connectivity = ConnectivitySummary.NeedsConnection,
        activity = SyncActivity(phase = SyncActivity.Phase.Syncing, queueDepth = 3),
        version = version,
    )

    /** No version to report, so the About row renders nothing at all. */
    val signedOutWithoutVersion: SettingsPort = PreviewSettingsPort(
        activity = SyncActivity(phase = SyncActivity.Phase.SignedOut),
        version = versionless,
    )
}

/**
 * A port that answers from fixed values.
 *
 * The streams are [flowOf] of a single element rather than a replaying flow, which is enough
 * for a preview and honest about what it is: a preview mounts the *screen*, and the screen is a
 * function of its arguments, so anything that arrives on a stream here would arrive twice.
 */
class PreviewSettingsPort(
    private val connectivity: ConnectivitySummary = ConnectivitySummary.Online,
    private val activity: SyncActivity = SyncActivity(),
    private val version: AppVersion = SettingsPreviewData.version,
) : SettingsPort {

    override suspend fun connectivity(): ConnectivitySummary = connectivity

    override fun connectivityUpdates(): Flow<ConnectivitySummary> = flowOf(connectivity)

    override suspend fun setForcedOffline(forced: Boolean) = Unit

    override suspend fun syncActivity(): SyncActivity = activity

    override fun syncUpdates(): Flow<SyncActivity> = flowOf(activity)

    override suspend fun canSyncNow(): Boolean = connectivity.allowsSync

    override suspend fun syncNow() = com.tasktracker.featuresettingsdomain.SyncOutcome.Started

    override suspend fun endSession() = Unit

    /** A preview has no session to end, so both ways out are the same no-op here. */
    override suspend fun forceEndSession() = Unit

    override fun appVersion(): AppVersion = version

    override suspend fun userDetails(): UserDetails = UserDetails()

    override fun userDetailsUpdates(): kotlinx.coroutines.flow.Flow<UserDetails> = kotlinx.coroutines.flow.flowOf(UserDetails())

    override suspend fun setUserDetails(details: UserDetails) = Unit
}


@Preview(name = "Settings, quiet", showBackground = true)
@Composable
private fun SettingsQuietPreview() {
    SettingsPreview(connectivity = ConnectivitySummary.Online, activity = SyncActivity(phase = SyncActivity.Phase.UpToDate))
}

@Preview(name = "Settings, offline with queued work", showBackground = true)
@Composable
private fun SettingsOfflinePreview() {
    SettingsPreview(
        connectivity = ConnectivitySummary.Offline,
        activity = SyncActivity(
            phase = SyncActivity.Phase.WaitingForConnection,
            queueDepth = 7,
            deadLetterCount = 2,
            failures = listOf("TT-3: the server rejected the priority value."),
        ),
    )
}

@Preview(name = "Settings, sync running", showBackground = true)
@Composable
private fun SettingsSyncingPreview() {
    SettingsPreview(
        connectivity = ConnectivitySummary.NeedsConnection,
        activity = SyncActivity(phase = SyncActivity.Phase.Syncing, queueDepth = 3),
    )
}

/** No version keys, so the About row hides itself rather than inventing a number. */
@Preview(name = "Settings, no version to report", showBackground = true)
@Composable
private fun SettingsVersionlessPreview() {
    SettingsPreview(
        connectivity = ConnectivitySummary.Online,
        activity = SyncActivity(phase = SyncActivity.Phase.SignedOut),
        version = SettingsPreviewData.versionless,
    )
}

/** The screen with inert controls, so a preview cannot accidentally do anything. */
@Composable
private fun SettingsPreview(
    connectivity: ConnectivitySummary,
    activity: SyncActivity,
    version: AppVersion = SettingsPreviewData.version,
    signOutFailure: AppError? = null,
) {
    ttTheme(ThemeScheme.System) {
        SettingsScreen(
            connectivity = connectivity,
            activity = activity,
            themeScheme = ThemeScheme.System,
            version = version,
            commandOutcome = null,
            onThemeChange = {},
            onSyncNow = {},
            onSignOut = {},
            signOutFailure = signOutFailure,
        )
    }
}

/**
 * The popup, which only exists once a clean sign-out has failed.
 *
 * Worth its own preview rather than a note on the happy one: it is the only state on this
 * screen with two buttons and a destructive one, and it is unreachable by tapping around in a
 * preview — nothing there can make a real sign-out fail.
 */
@Preview(name = "Settings, sign-out blocked", showBackground = true)
@Composable
private fun SettingsSignOutBlockedPreview() {
    SettingsPreview(
        connectivity = ConnectivitySummary.Offline,
        activity = SyncActivity(phase = SyncActivity.Phase.WaitingForConnection, queueDepth = 2),
        signOutFailure = AppError.Timeout,
    )
}