package com.tasktracker.featureprojectspresentation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.UserID
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.AgentRunID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featureprojectsdomain.AgentPairingCode
import com.tasktracker.featureprojectsdomain.AgentPairingHost
import com.tasktracker.featureprojectsdomain.AgentPairingPort
import com.tasktracker.featureprojectsdomain.AgentRun
import com.tasktracker.featureprojectsdomain.AgentRunPage
import com.tasktracker.featureprojectsdomain.AgentRunsPort
import com.tasktracker.featureprojectsdomain.AgentRunStatus
import com.tasktracker.featureprojectsdomain.ProjectAgentMachine
import com.tasktracker.resources.Res
import com.tasktracker.resources.project_agent_host_desktop
import com.tasktracker.resources.project_agent_host_remote
import com.tasktracker.resources.project_agent_enabled_off
import com.tasktracker.resources.project_agent_enabled_on
import com.tasktracker.resources.settings_agent_offline
import com.tasktracker.resources.settings_agent_running
import kotlin.time.Instant
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.stringResource

/**
 * Structural Compose scenarios for the agent-management controls the previous slice added:
 * the project-editor machine roster (with the desktop-vs-VPS host label and derived health),
 * the pairing sheet's host picker, the per-project `agentEnabled` toggle, the work-item
 * dispatch target picker, and the run result diff sheet.
 *
 * All are structural - the assertions address nodes by their tags or by text the screen
 * already resolves, and never read a pixel. The model/serialization tests cover the data;
 * these pin that the controls actually appear and route their choices to the right place.
 */
@OptIn(ExperimentalTestApi::class)
object AgentManagementScenarios {

    /** The roster shows every paired machine with its host label and derived health. */
    val theRosterShowsEachMachineItsHostAndHealth: suspend ComposeUiTest.() -> Unit = {
        val now = Instant.parse("2026-11-03T12:00:30Z")
        val desktop = machine(
            id = "0195c3a4-0000-7000-8000-0000000000aa",
            name = "studio",
            host = AgentPairingHost.Desktop,
            lastSeenAt = Instant.parse("2026-11-03T12:00:20Z"),
        )
        val remote = machine(
            id = "0195c3a4-0000-7000-8000-0000000000bb",
            name = "vps",
            host = AgentPairingHost.Remote,
            lastSeenAt = Instant.parse("2026-11-03T11:20:00Z"),
        )
        val port = FakePairingPort(listOf(desktop, remote))

        var desktopLabel = ""
        var remoteLabel = ""
        var runningLabel = ""
        var offlineLabel = ""
        setContent {
            ttTheme(ThemeScheme.Light) {
                desktopLabel = stringResource(Res.string.project_agent_host_desktop)
                remoteLabel = stringResource(Res.string.project_agent_host_remote)
                runningLabel = stringResource(Res.string.settings_agent_running)
                offlineLabel = stringResource(Res.string.settings_agent_offline)
                ProjectAgentSection(port = port, projectID = ProjectID(PROJECT_ID), dateProvider = TestDateProvider(now))
            }
        }
        waitForIdle()

        onNodeWithTag(ProjectAgentMachineTag(desktop.id.stringValue)).assertIsDisplayed()
        onNodeWithTag(ProjectAgentMachineTag(remote.id.stringValue)).assertIsDisplayed()
        // The host label is the desktop-vs-VPS control: each machine's kind must be on the page.
        onNodeWithText(desktopLabel, substring = true).assertIsDisplayed()
        onNodeWithText(remoteLabel, substring = true).assertIsDisplayed()
        // Health is derived from the same observation; both states the roster can show appear.
        onNodeWithText(runningLabel).assertIsDisplayed()
        onNodeWithText(offlineLabel).assertIsDisplayed()
    }

    /** A project with no paired machines says so rather than drawing an empty list. */
    val anEmptyRosterSaysSo: suspend ComposeUiTest.() -> Unit = {
        val now = Instant.parse("2026-11-03T12:00:30Z")
        val port = FakePairingPort(emptyList())

        setContent {
            ttTheme(ThemeScheme.Light) {
                ProjectAgentSection(port = port, projectID = ProjectID(PROJECT_ID), dateProvider = TestDateProvider(now))
            }
        }
        waitForIdle()

        onNodeWithTag(ProjectAgentMachinesEmptyTag).assertIsDisplayed()
        onNodeWithTag(ProjectAgentMachineTag("0195c3a4-0000-7000-8000-0000000000aa")).assertDoesNotExist()
    }

    /** The refresh control re-reads the roster instead of leaving a stale list. */
    val tappingRefreshReReadsTheRoster: suspend ComposeUiTest.() -> Unit = {
        val now = Instant.parse("2026-11-03T12:00:30Z")
        val port = FakePairingPort(
            listOf(
                machine(
                    id = "0195c3a4-0000-7000-8000-0000000000aa",
                    name = "studio",
                    lastSeenAt = now,
                ),
            ),
        )

        setContent {
            ttTheme(ThemeScheme.Light) {
                ProjectAgentSection(port = port, projectID = ProjectID(PROJECT_ID), dateProvider = TestDateProvider(now))
            }
        }
        waitForIdle()
        // The section reads once on mount.
        val afterMount = port.machinesCalls

        onNodeWithTag(ProjectAgentRefreshTag).performClick()
        waitForIdle()

        assertEquals(afterMount + 1, port.machinesCalls, "the refresh button re-reads the roster")
        onNodeWithTag(ProjectAgentMachineTag("0195c3a4-0000-7000-8000-0000000000aa")).assertIsDisplayed()
    }

    /**
     * The pairing sheet's host picker chooses where the machine runs, and that choice is the
     * host the server is asked to bind - not just a label on the screen. The sheet opens from
     * the project editor's "pair" action; the picker is private to that file, so the scenario
     * drives it the way an admin does.
     */
    val thePairingHostChoiceReachesTheServer: suspend ComposeUiTest.() -> Unit = {
        val now = Instant.parse("2026-11-03T12:00:30Z")
        val port = FakePairingPort(emptyList())

        setContent {
            ttTheme(ThemeScheme.Light) {
                ProjectAgentSection(port = port, projectID = ProjectID(PROJECT_ID), dateProvider = TestDateProvider(now))
            }
        }
        waitForIdle()
        onNodeWithTag(ProjectAgentPairTag).performClick()
        waitForIdle()
        // The sheet requests a code on open with the default (Desktop) host.
        assertEquals(AgentPairingHost.Desktop, port.lastHost)

        onNodeWithTag(ProjectAgentCodeTag).assertIsDisplayed()
        onNodeWithTag(ProjectAgentHostDesktopTag).assertIsDisplayed()
        onNodeWithTag(ProjectAgentHostRemoteTag).assertIsDisplayed()

        onNodeWithTag(ProjectAgentHostRemoteTag).performClick()
        onNodeWithTag(ProjectAgentRetryTag).performClick()
        waitForIdle()

        assertEquals(AgentPairingHost.Remote, port.lastHost, "the chosen host is sent with the re-issued code")
    }

    /**
     * The per-project `agentEnabled` switch is the gate that lets a work item dispatch to a
     * paired agent. Toggling it must report the new value, not leave the model and the label apart.
     */
    val theAgentEnabledToggleReportsItsState: suspend ComposeUiTest.() -> Unit = {
        var enabled by mutableStateOf(false)
        var offLabel = ""
        var onLabel = ""
        setContent {
            ttTheme(ThemeScheme.Light) {
                offLabel = stringResource(Res.string.project_agent_enabled_off)
                onLabel = stringResource(Res.string.project_agent_enabled_on)
                NewProjectForm(
                    name = "Apollo Programme",
                    keyPrefix = "AP",
                    details = "",
                    canSave = true,
                    onNameChanged = {},
                    onKeyPrefixChanged = {},
                    onDetailsChanged = {},
                    repoUrl = "",
                    onRepoUrlChanged = {},
                    agentEnabled = enabled,
                    onAgentEnabledChanged = { enabled = it },
                    onSave = {},
                    onDismiss = null,
                )
            }
        }

        onNodeWithTag(NewProjectAgentEnabledTag).assertIsDisplayed()
        onNodeWithText(offLabel).assertIsDisplayed()
        assertEquals(false, enabled)

        onNodeWithTag(NewProjectAgentEnabledTag).performClick()
        waitForIdle()

        onNodeWithText(onLabel).assertIsDisplayed()
        assertEquals(true, enabled, "the toggle reports its new state to the form")
    }

    /**
     * Before a dispatch the work item offers the project's machines as targets, and choosing one
     * carries that runner into the dispatch - the per-run targeting the roster alone cannot.
     */
    val theDispatchTargetPickerRoutesTheChosenMachine: suspend ComposeUiTest.() -> Unit = {
        val target = machine(
            id = "0195c3a4-0000-7000-8000-0000000000aa",
            name = "vps",
            host = AgentPairingHost.Remote,
            lastSeenAt = Instant.parse("2026-11-03T12:00:00Z"),
        )
        val model = AgentRunModel(WorkItemID(ITEM_ID), FakeRunsPort())
        var dispatched: AgentRunnerID? = null

        setContent {
            ttTheme(ThemeScheme.Light) {
                WorkItemAgentSection(
                    model = model,
                    onDispatch = { dispatched = it },
                    onCancel = {},
                    agents = listOf(target),
                )
            }
        }
        waitForIdle()

        onNodeWithTag(WorkItemAgentAnyRunnerTag).assertIsDisplayed()
        val runnerTag = WorkItemAgentRunnerTag(target.id.stringValue)
        onNodeWithTag(runnerTag).assertIsDisplayed()

        onNodeWithTag(runnerTag).performClick()
        onNodeWithTag(WorkItemAgentSendTag).performClick()
        waitForIdle()

        assertEquals(target.id, dispatched, "the selected machine is sent as the dispatch target")
    }

    /** A finished run that reported a diff offers it in a sheet rather than only a longer log. */
    val aTerminalRunWithADiffOpensTheDiffSheet: suspend ComposeUiTest.() -> Unit = {
        val terminal = AgentRun(
            id = AgentRunID(RUN_ID),
            workItemID = WorkItemID(ITEM_ID),
            projectID = ProjectID(PROJECT_ID),
            status = AgentRunStatus.Succeeded,
            requestedBy = UserID(USER_ID),
            runnerID = AgentRunnerID(RUNNER_ID),
            cancelRequested = false,
            createdAt = Instant.parse("2026-11-03T12:00:00Z"),
            startedAt = null,
            finishedAt = null,
            summary = "Done.",
            error = "",
            diff = SAMPLE_DIFF,
        )
        val port = object : AgentRunsPort {
            override suspend fun dispatch(
                workItemID: WorkItemID,
                idempotencyKey: String?,
                runnerID: AgentRunnerID?,
            ): AgentRun = terminal

            override suspend fun runs(workItemID: WorkItemID, limit: Int): List<AgentRun> = listOf(terminal)
            override suspend fun run(runID: AgentRunID, afterSeq: Long): AgentRunPage =
                AgentRunPage(terminal, emptyList(), false)

            override suspend fun cancel(runID: AgentRunID): AgentRun = terminal
        }
        val model = AgentRunModel(WorkItemID(ITEM_ID), port)

        setContent {
            ttTheme(ThemeScheme.Light) {
                LaunchedEffect(Unit) { model.refresh() }
                WorkItemAgentSection(model = model, onDispatch = {}, onCancel = {}, agents = emptyList())
            }
        }
        waitForIdle()

        onNodeWithTag(WorkItemAgentDiffButtonTag).assertIsDisplayed()
        onNodeWithTag(WorkItemAgentDiffButtonTag).performClick()
        waitForIdle()

        onNodeWithTag(WorkItemAgentDiffTag).assertIsDisplayed()
        onNodeWithTag(WorkItemAgentDiffTextTag).assertIsDisplayed()
        onNodeWithTag(WorkItemAgentDiffTextTag).assertTextContains(SAMPLE_DIFF, substring = true)
    }

    private fun machine(
        id: String,
        name: String,
        host: AgentPairingHost? = null,
        lastSeenAt: Instant? = null,
        pausedAt: Instant? = null,
    ) = ProjectAgentMachine(
        id = AgentRunnerID(id),
        name = name,
        provider = "openai-compatible",
        modelLabel = "llama3.1",
        lastSeenAt = lastSeenAt,
        pausedAt = pausedAt,
        host = host,
    )

    /** A pairing port that records its roster reads and the host of each code it mints. */
    private class FakePairingPort(
        private val roster: List<ProjectAgentMachine>,
    ) : AgentPairingPort {
        var machinesCalls = 0
        var lastHost: AgentPairingHost? = null

        override suspend fun createPairingCode(
            projectID: ProjectID,
            host: AgentPairingHost,
        ): AgentPairingCode {
            lastHost = host
            return AgentPairingCode(
                code = "TTPC-EXAMPLE",
                projectID = projectID,
                expiresAt = Instant.parse("2026-11-03T12:05:00Z"),
            )
        }

        override suspend fun machines(projectID: ProjectID): List<ProjectAgentMachine> {
            machinesCalls += 1
            return roster
        }
    }

    /** A runs port whose `runs` returns the one terminal run; enough to populate the section. */
    private class FakeRunsPort : AgentRunsPort {
        override suspend fun dispatch(
            workItemID: WorkItemID,
            idempotencyKey: String?,
            runnerID: AgentRunnerID?,
        ): AgentRun = error("the target picker does not dispatch through the model")

        override suspend fun runs(workItemID: WorkItemID, limit: Int): List<AgentRun> = emptyList()
        override suspend fun run(runID: AgentRunID, afterSeq: Long): AgentRunPage =
            error("the target picker does not read detail")

        override suspend fun cancel(runID: AgentRunID): AgentRun =
            error("the target picker does not cancel")
    }

    private const val PROJECT_ID = "0195c3a4-0000-7000-8000-000000000003"
    private const val ITEM_ID = "0195c3a4-0000-7000-8000-000000000002"
    private const val RUN_ID = "0195c3a4-0000-7000-8000-000000000001"
    private const val USER_ID = "0195c3a4-0000-7000-8000-000000000004"
    private const val RUNNER_ID = "0195c3a4-0000-7000-8000-0000000000aa"
    private const val SAMPLE_DIFF = "diff --git a/README.md b/README.md\n+ship the agent run\n"
}
