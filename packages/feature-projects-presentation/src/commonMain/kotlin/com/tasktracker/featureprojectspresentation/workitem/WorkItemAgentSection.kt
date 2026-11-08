package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttMonospaced
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSeparatorWidth
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.core.AgentRunnerID
import com.tasktracker.featureprojectsdomain.AgentRun
import com.tasktracker.featureprojectsdomain.AgentPairingHost
import com.tasktracker.featureprojectsdomain.AgentRunEvent
import com.tasktracker.featureprojectsdomain.AgentRunStatus
import com.tasktracker.featureprojectsdomain.ProjectAgentMachine
import com.tasktracker.resources.Res
import com.tasktracker.resources.agent_cancel
import com.tasktracker.resources.agent_cancelling
import com.tasktracker.resources.agent_empty
import com.tasktracker.resources.agent_log_empty
import com.tasktracker.resources.agent_log_loading
import com.tasktracker.resources.agent_not_configured
import com.tasktracker.resources.agent_repository
import com.tasktracker.resources.agent_section_title
import com.tasktracker.resources.agent_send
import com.tasktracker.resources.agent_send_again
import com.tasktracker.resources.agent_status_cancelled
import com.tasktracker.resources.agent_status_claimed
import com.tasktracker.resources.agent_status_failed
import com.tasktracker.resources.agent_status_queued
import com.tasktracker.resources.agent_status_running
import com.tasktracker.resources.agent_status_succeeded
import com.tasktracker.resources.agent_unavailable
import com.tasktracker.resources.agent_waiting
import com.tasktracker.resources.agent_any_machine
import com.tasktracker.resources.agent_diff_title
import com.tasktracker.resources.agent_log_title
import com.tasktracker.resources.agent_run_on
import com.tasktracker.resources.agent_view_diff
import com.tasktracker.resources.agent_view_log
import com.tasktracker.resources.project_agent_host_desktop
import com.tasktracker.resources.project_agent_host_remote
import com.tasktracker.resources.common_done
import org.jetbrains.compose.resources.stringResource

/**
 * The agent block inside the work item editor: dispatch, live status, the latest
 * step, the event log, and a cooperative Cancel.
 *
 * The log is model text, so every line is rendered as plain [ttText] — never as a
 * link or markup the model chose. A run's summary gets the same treatment.
 */
@Composable
fun WorkItemAgentSection(
    model: AgentRunModel,
    onDispatch: (AgentRunnerID?) -> Unit,
    onCancel: () -> Unit,
    /**
     * Whether an agent is set up for this project. When false the section shows the
     * "no agent configured" message and the dispatch button is greyed out, so a run is
     * never queued on a project the admin has not enabled agents for.
     */
    agentConfigured: Boolean = true,
    /** Where the project's code lives, so the run's target is visible before dispatch. */
    repoUrl: String? = null,
    /** The caller's project-scoped machines; empty means the server chooses. */
    agents: List<ProjectAgentMachine> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val run = model.run
    var showLog by remember { mutableStateOf(false) }
    var showDiff by remember { mutableStateOf(false) }
    var selectedRunnerID by remember { mutableStateOf<AgentRunnerID?>(null) }
    LaunchedEffect(agents) {
        if (selectedRunnerID != null && agents.none { it.id == selectedRunnerID }) {
            selectedRunnerID = null
        }
    }
    LaunchedEffect(showLog) {
        if (showLog) model.loadFullLog()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassCard()
            .padding(ttSpacing.md)
            .testTag(WorkItemAgentSectionTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = stringResource(Res.string.agent_section_title),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            run?.let { AgentStatusPill(it.status) }
        }

        if (!repoUrl.isNullOrBlank()) {
            ttText(
                text = stringResource(Res.string.agent_repository, repoUrl),
                style = ttTypography.captionSmall,
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(WorkItemAgentRepositoryTag),
            )
        }

        when {
            model.isUnavailable -> ttText(
                text = stringResource(Res.string.agent_unavailable),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )

            run == null -> {
                if (agentConfigured) {
                    ttText(
                        text = stringResource(Res.string.agent_empty),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                    )
                    AgentTargetPicker(
                        agents = agents,
                        selectedRunnerID = selectedRunnerID,
                        onSelect = { selectedRunnerID = it },
                    )
                    ttGlassButton(
                        label = stringResource(Res.string.agent_send),
                        onClick = { if (!model.isDispatching) onDispatch(selectedRunnerID) },
                        modifier = Modifier.testTag(WorkItemAgentSendTag),
                    )
                } else {
                    ttText(
                        text = stringResource(Res.string.agent_not_configured),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                    )
                    ttGlassButton(
                        label = stringResource(Res.string.agent_send),
                        enabled = false,
                        onClick = {},
                        modifier = Modifier.testTag(WorkItemAgentSendTag),
                    )
                }
            }

            else -> {
                val step = model.latestEvent?.message.orEmpty().ifBlank {
                    if (run.status == AgentRunStatus.Queued) {
                        stringResource(Res.string.agent_waiting)
                    } else {
                        ""
                    }
                }
                if (step.isNotEmpty()) {
                    ttText(
                        text = step,
                        style = ttTypography.footnote,
                        color = ttLabel(),
                        modifier = Modifier.testTag(WorkItemAgentStepTag),
                    )
                }

                if (model.events.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs)) {
                        model.events.takeLast(EVENT_LOG_LINES).forEach { event ->
                            AgentEventLine(event)
                        }
                    }
                    ttGlassButton(
                        label = stringResource(Res.string.agent_view_log),
                        onClick = { showLog = true },
                        modifier = Modifier.testTag(WorkItemAgentLogButtonTag),
                    )
                }

                if (run.status.isTerminal) {
                    val outcome = run.error.ifBlank { run.summary }
                    if (outcome.isNotBlank()) {
                        ttText(
                            text = outcome,
                            style = ttTypography.footnote,
                            color = if (run.status == AgentRunStatus.Failed) ttError() else ttLabelSecondary(),
                            modifier = Modifier.testTag(WorkItemAgentOutcomeTag),
                        )
                    }
                    if (run.diff.isNotBlank()) {
                        ttGlassButton(
                            label = stringResource(Res.string.agent_view_diff),
                            onClick = { showDiff = true },
                            modifier = Modifier.testTag(WorkItemAgentDiffButtonTag),
                        )
                    }
                    AgentTargetPicker(
                        agents = agents,
                        selectedRunnerID = selectedRunnerID,
                        onSelect = { selectedRunnerID = it },
                    )
                    ttGlassButton(
                        label = stringResource(Res.string.agent_send_again),
                        enabled = agentConfigured,
                        onClick = { if (!model.isDispatching) onDispatch(selectedRunnerID) },
                        modifier = Modifier.testTag(WorkItemAgentSendTag),
                    )
                } else if (model.canCancel) {
                    ttGlassButton(
                        label = if (model.isCancelling) {
                            stringResource(Res.string.agent_cancelling)
                        } else {
                            stringResource(Res.string.agent_cancel)
                        },
                        onClick = { if (!model.isCancelling) onCancel() },
                        modifier = Modifier.testTag(WorkItemAgentCancelTag),
                    )
                } else if (run.cancelRequested) {
                    // Cancel is cooperative: the runner observes the flag at its next
                    // heartbeat, so the run stays live for up to one interval.
                    ttText(
                        text = stringResource(Res.string.agent_cancelling),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                        modifier = Modifier.testTag(WorkItemAgentCancelTag),
                    )
                }
            }
        }
    }

    if (showLog) {
        AgentLogSheet(
            events = model.events,
            isLoading = model.isLoadingLog,
            onDismiss = { showLog = false },
        )
    }

    if (showDiff) {
        AgentDiffSheet(diff = run?.diff.orEmpty(), onDismiss = { showDiff = false })
    }
}

@Composable
private fun AgentTargetPicker(
    agents: List<ProjectAgentMachine>,
    selectedRunnerID: AgentRunnerID?,
    onSelect: (AgentRunnerID?) -> Unit,
) {
    if (agents.isEmpty()) return

    ttText(
        text = stringResource(Res.string.agent_run_on),
        style = ttTypography.captionEmphasised,
        color = ttLabelSecondary(),
    )
    ttGlassButton(
        label = stringResource(Res.string.agent_any_machine),
        onClick = { onSelect(null) },
        modifier = Modifier.testTag(WorkItemAgentAnyRunnerTag),
        style = if (selectedRunnerID == null) {
            TTGlassButtonStyle(tint = ttAccent(), prominent = true)
        } else {
            TTGlassButtonStyle()
        },
    )
    agents.forEach { machine ->
        val host = machine.host?.let { agentHostLabel(it) }
        ttGlassButton(
            label = listOfNotNull(machine.name, host).joinToString(" · "),
            onClick = { onSelect(machine.id) },
            modifier = Modifier.testTag(WorkItemAgentRunnerTag(machine.id.stringValue)),
            style = if (selectedRunnerID == machine.id) {
                TTGlassButtonStyle(tint = ttAccent(), prominent = true)
            } else {
                TTGlassButtonStyle()
            },
        )
    }
}

@Composable
private fun agentHostLabel(host: AgentPairingHost): String = stringResource(
    when (host) {
        AgentPairingHost.Desktop -> Res.string.project_agent_host_desktop
        AgentPairingHost.Remote -> Res.string.project_agent_host_remote
    },
)

@Composable
private fun AgentDiffSheet(diff: String, onDismiss: () -> Unit) {
    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(WorkItemAgentDiffTag),
    ) {
        ttText(
            text = stringResource(Res.string.agent_diff_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LOG_SHEET_MAX_HEIGHT)
                .verticalScroll(rememberScrollState()),
        ) {
            ttText(
                text = diff,
                style = ttTypography.captionSmall.ttMonospaced(),
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(WorkItemAgentDiffTextTag),
            )
        }
        ttGlassButton(
            label = stringResource(Res.string.common_done),
            onClick = onDismiss,
            modifier = Modifier.testTag(WorkItemAgentDiffDoneTag),
        )
    }
}

@Composable
private fun AgentLogSheet(
    events: List<AgentRunEvent>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(WorkItemAgentLogTag),
    ) {
        ttText(
            text = stringResource(Res.string.agent_log_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        if (isLoading) {
            ttText(
                text = stringResource(Res.string.agent_log_loading),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
        if (events.isEmpty()) {
            ttText(
                text = stringResource(Res.string.agent_log_empty),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LOG_SHEET_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
            ) {
                events.forEach { event ->
                    ttText(
                        text = event.message,
                        style = ttTypography.captionSmall,
                        color = ttLabelSecondary(),
                        modifier = Modifier.testTag(WorkItemAgentLogLineTag(event.seq)),
                    )
                }
            }
        }
        ttGlassButton(
            label = stringResource(Res.string.common_done),
            onClick = onDismiss,
            modifier = Modifier.testTag(WorkItemAgentLogDoneTag),
        )
    }
}

@Composable
private fun AgentStatusPill(status: AgentRunStatus) {
    val shape = RoundedCornerShape(ttRadius.row)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(statusFill(status))
            .border(ttSeparatorWidth, statusRim(status), shape)
            .padding(horizontal = ttSpacing.sm, vertical = PILL_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = statusLabel(status),
            style = ttTypography.captionSmall,
            color = ttLabel(),
        )
    }
}

@Composable
private fun AgentEventLine(event: AgentRunEvent) {
    ttText(
        text = event.message,
        style = ttTypography.captionSmall,
        color = ttLabelSecondary(),
        modifier = Modifier.testTag(WorkItemAgentEventTag(event.seq)),
    )
}

@Composable
private fun statusLabel(status: AgentRunStatus): String = stringResource(
    when (status) {
        AgentRunStatus.Queued -> Res.string.agent_status_queued
        AgentRunStatus.Claimed -> Res.string.agent_status_claimed
        AgentRunStatus.Running -> Res.string.agent_status_running
        AgentRunStatus.Succeeded -> Res.string.agent_status_succeeded
        AgentRunStatus.Failed -> Res.string.agent_status_failed
        AgentRunStatus.Cancelled -> Res.string.agent_status_cancelled
    },
)

@Composable
private fun statusFill(status: AgentRunStatus): Color = when (status) {
    AgentRunStatus.Queued -> ttLabelSecondary().copy(alpha = RESTING_FILL_ALPHA)
    AgentRunStatus.Claimed, AgentRunStatus.Running -> ttAccent().copy(alpha = ACTIVE_FILL_ALPHA)
    AgentRunStatus.Succeeded -> ttAccent().copy(alpha = ACTIVE_FILL_ALPHA)
    AgentRunStatus.Failed -> ttError().copy(alpha = CRITICAL_FILL_ALPHA)
    AgentRunStatus.Cancelled -> ttLabelSecondary().copy(alpha = RESTING_FILL_ALPHA)
}

@Composable
private fun statusRim(status: AgentRunStatus): Color = when (status) {
    AgentRunStatus.Queued, AgentRunStatus.Cancelled -> ttSeparator()
    AgentRunStatus.Claimed, AgentRunStatus.Running, AgentRunStatus.Succeeded -> ttAccent()
    AgentRunStatus.Failed -> ttError()
}

const val WorkItemAgentSectionTag = "workItem.agent.section"
const val WorkItemAgentSendTag = "workItem.agent.send"
const val WorkItemAgentCancelTag = "workItem.agent.cancel"
const val WorkItemAgentStepTag = "workItem.agent.step"
const val WorkItemAgentOutcomeTag = "workItem.agent.outcome"
const val WorkItemAgentRepositoryTag = "workItem.agent.repository"
const val WorkItemAgentLogButtonTag = "workItem.agent.log.open"
const val WorkItemAgentLogTag = "workItem.agent.log"
const val WorkItemAgentLogDoneTag = "workItem.agent.log.done"
const val WorkItemAgentDiffButtonTag = "workItem.agent.diff.open"
const val WorkItemAgentDiffTag = "workItem.agent.diff"
const val WorkItemAgentDiffTextTag = "workItem.agent.diff.text"
const val WorkItemAgentDiffDoneTag = "workItem.agent.diff.done"
const val WorkItemAgentAnyRunnerTag = "workItem.agent.runner.any"
fun WorkItemAgentLogLineTag(seq: Long): String = "workItem.agent.log.line.$seq"
fun WorkItemAgentRunnerTag(id: String): String = "workItem.agent.runner.$id"
fun WorkItemAgentEventTag(seq: Long): String = "workItem.agent.event.$seq"

/** The log shows its tail: a long run's first lines are history, not status. */
private const val EVENT_LOG_LINES = 4
private val LOG_SHEET_MAX_HEIGHT = 420.dp
private val PILL_PADDING_V = 2.dp
private const val RESTING_FILL_ALPHA = 0.08f
private const val ACTIVE_FILL_ALPHA = 0.14f
private const val CRITICAL_FILL_ALPHA = 0.18f
