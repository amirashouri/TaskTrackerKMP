package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSeparatorWidth
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectsdomain.AgentRun
import com.tasktracker.featureprojectsdomain.AgentRunEvent
import com.tasktracker.featureprojectsdomain.AgentRunStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.agent_cancel
import com.tasktracker.resources.agent_cancelling
import com.tasktracker.resources.agent_empty
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
    onDispatch: () -> Unit,
    onCancel: () -> Unit,
    /**
     * Whether an agent is set up for this project. When false the section shows the
     * "no agent configured" message and the dispatch button is greyed out, so a run is
     * never queued on a project the admin has not enabled agents for.
     */
    agentConfigured: Boolean = true,
    /** Where the project's code lives, so the run's target is visible before dispatch. */
    repoUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val run = model.run

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
                    ttGlassButton(
                        label = stringResource(Res.string.agent_send),
                        onClick = { if (!model.isDispatching) onDispatch() },
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
                    ttGlassButton(
                        label = stringResource(Res.string.agent_send_again),
                        enabled = agentConfigured,
                        onClick = { if (!model.isDispatching) onDispatch() },
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
fun WorkItemAgentEventTag(seq: Long): String = "workItem.agent.event.$seq"

/** The log shows its tail: a long run's first lines are history, not status. */
private const val EVENT_LOG_LINES = 4
private val PILL_PADDING_V = 2.dp
private const val RESTING_FILL_ALPHA = 0.08f
private const val ACTIVE_FILL_ALPHA = 0.14f
private const val CRITICAL_FILL_ALPHA = 0.18f
