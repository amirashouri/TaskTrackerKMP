package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tasktracker.core.AgentRunnerID
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttMonospaced
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttToast
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featuresettingsdomain.AgentHost
import com.tasktracker.featuresettingsdomain.AgentMachine
import com.tasktracker.featuresettingsdomain.AgentOverview
import com.tasktracker.featuresettingsdomain.AgentState
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_done
import com.tasktracker.resources.copied_to_clipboard
import com.tasktracker.resources.settings_agent_copy_connect
import com.tasktracker.resources.settings_agent_copy_run
import com.tasktracker.resources.settings_agent_empty
import com.tasktracker.resources.settings_agent_empty_state
import com.tasktracker.resources.settings_agent_failure
import com.tasktracker.resources.settings_agent_host_desktop
import com.tasktracker.resources.settings_agent_host_remote
import com.tasktracker.resources.settings_agent_offline
import com.tasktracker.resources.settings_agent_running
import com.tasktracker.resources.settings_agent_section
import com.tasktracker.resources.settings_agent_setup
import com.tasktracker.resources.settings_agent_setup_body
import com.tasktracker.resources.settings_agent_setup_connect
import com.tasktracker.resources.settings_agent_setup_connect_step
import com.tasktracker.resources.settings_agent_setup_note
import com.tasktracker.resources.settings_agent_setup_run
import com.tasktracker.resources.settings_agent_setup_run_step
import com.tasktracker.resources.settings_agent_setup_title
import com.tasktracker.resources.settings_agent_start
import com.tasktracker.resources.settings_agent_stopped
import com.tasktracker.resources.settings_agent_stop
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The Agent block: the account's paired machines and, for an administrator, the
 * two controls that manage them.
 *
 * ## Everyone sees the status; only an administrator sees a command
 *
 * A member may need to know whether an agent is available before dispatching a
 * task, but pairing and stopping a machine changes what the whole account's fleet
 * will do. The gate is presence, like the Admin section: a drawn-but-dead row is
 * the shape this screen already refuses.
 *
 * ## Stop is a gate, not a kill
 *
 * The worker runs `tt agent run` on the customer's own machine. The server can
 * refuse it work and ask its current run to cancel; it cannot end a process it
 * does not own. [AgentSetupSheet] says that plainly, so a stopped row that keeps
 * showing a live process is not read as a bug.
 */
@Composable
internal fun AgentOverviewSection(
    overview: AgentOverview,
    serverRoot: String?,
    isAdmin: Boolean,
    onStop: (AgentRunnerID) -> Unit,
    onStart: (AgentRunnerID) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection {
        SettingsIconRow(
            label = stringResource(Res.string.settings_agent_section),
            value = agentStateLabel(overview.state),
            icon = ttSettingsIcons.sync,
            modifier = modifier.testTag(SettingsAgentRowTag),
        )

        overview.liveMachines.forEach { machine ->
            SettingsDivider()
            SettingsIconRow(
                label = machine.name,
                value = listOf(
                    machine.host?.let { agentHostLabel(it) },
                    agentStateLabel(machine.state(overview)),
                    machine.modelSummary,
                ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                icon = ttSettingsIcons.account,
            )
            if (isAdmin) {
                SettingsDivider()
                if (overview.canStop(machine)) {
                    SettingsActionRow(
                        label = stringResource(Res.string.settings_agent_stop),
                        icon = ttSettingsIcons.offline,
                        tint = ttError(),
                        onClick = { onStop(machine.id) },
                        modifier = Modifier.testTag(SettingsAgentStopTagPrefix + machine.id.stringValue),
                    )
                } else {
                    SettingsActionRow(
                        label = stringResource(Res.string.settings_agent_start),
                        icon = ttSettingsIcons.connectivity,
                        tint = ttAccent(),
                        onClick = { onStart(machine.id) },
                        modifier = Modifier.testTag(SettingsAgentStartTagPrefix + machine.id.stringValue),
                    )
                }
            }
        }

        if (overview.liveMachines.isEmpty()) {
            SettingsDivider()
            ttText(
                text = stringResource(Res.string.settings_agent_empty),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(SettingsAgentEmptyTag),
            )
        }

        // Pairing is a server operation — it mints the machine's credential — so
        // the row is absent rather than disabled in the offline demo.
        if (isAdmin && serverRoot != null) {
            SettingsDivider()
            SettingsActionRow(
                label = stringResource(Res.string.settings_agent_setup),
                icon = ttSettingsIcons.appearance,
                tint = ttAccent(),
                onClick = onOpenSetup,
                modifier = Modifier.testTag(SettingsAgentSetupRowTag),
            )
        }
    }
}

/**
 * What to run on the machine that holds the code.
 *
 * There is no token here on purpose: `tt agent connect` logs in and registers the
 * machine itself, and the server returns the runner token exactly once to that
 * process. The app can show the address and the two commands, but inventing a
 * token in the UI would be a credential the CLI has no route to adopt.
 *
 * ## Two commands, each one tap from the clipboard
 *
 * The commands are the only thing on this sheet a user has to retype, and a
 * mistyped URL is a failed pairing rather than a typo. So each one sits on its own
 * numbered card, in monospace, with a Copy button beside it — the same glyph and
 * the same two-second confirmation the reset-code sheet uses, because one app
 * should not have two "copied" behaviours.
 */
@Composable
internal fun AgentSetupSheet(
    serverRoot: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    // Counted rather than flagged: copying again while the first toast is still up
    // restarts the two seconds instead of leaving a stale one behind.
    var copies by remember { mutableIntStateOf(0) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        delay(AgentCopiedToastMillis)
        copies = 0
    }

    fun copy(command: String) {
        clipboard.setText(AnnotatedString(command))
        copies++
    }

    val connect = stringResource(Res.string.settings_agent_setup_connect, serverRoot)
    val run = stringResource(Res.string.settings_agent_setup_run)

    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(SettingsAgentSetupSheetTag),
    ) {
        ttText(
            text = stringResource(Res.string.settings_agent_setup_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        ttText(
            text = stringResource(Res.string.settings_agent_setup_body),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )

        // The toast is anchored to the commands, not to the sheet's bottom edge:
        // floating over the Done button would hide the way out for the two seconds
        // the confirmation is up.
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                AgentSetupCommand(
                    step = "1",
                    caption = stringResource(Res.string.settings_agent_setup_connect_step),
                    command = connect,
                    copyDescription = stringResource(Res.string.settings_agent_copy_connect),
                    onCopy = { copy(connect) },
                    commandTag = SettingsAgentSetupCommandTag,
                    copyTag = SettingsAgentSetupCopyConnectTag,
                )
                AgentSetupCommand(
                    step = "2",
                    caption = stringResource(Res.string.settings_agent_setup_run_step),
                    command = run,
                    copyDescription = stringResource(Res.string.settings_agent_copy_run),
                    onCopy = { copy(run) },
                    commandTag = SettingsAgentSetupRunCommandTag,
                    copyTag = SettingsAgentSetupCopyRunTag,
                )
                ttText(
                    text = stringResource(Res.string.settings_agent_setup_note),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )
            }

            if (copies > 0) {
                ttToast(
                    message = stringResource(Res.string.copied_to_clipboard),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_done),
                onClick = onDismiss,
                modifier = Modifier.testTag(SettingsAgentSetupCloseTag),
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
            )
        }
    }
}

/**
 * One numbered command: what it does, the exact text to run, and Copy.
 *
 * A glass card rather than a bare `Text` because the command is an object the
 * user acts on, not a sentence — the border says where the copyable string starts
 * and stops, which a wrapped line on a sheet background cannot.
 */
@Composable
private fun AgentSetupCommand(
    step: String,
    caption: String,
    command: String,
    copyDescription: String,
    onCopy: () -> Unit,
    commandTag: String,
    copyTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
            .padding(start = ttSpacing.md, end = ttSpacing.sm, top = ttSpacing.sm, bottom = ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        // The step number is decoration; the caption next to it carries the meaning
        // for a screen reader, so the badge itself is not announced.
        Box(
            modifier = Modifier
                .size(AgentStepBadgeSize)
                .background(ttAccent(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ttText(
                text = step,
                style = ttTypography.caption,
                color = ttOnAccent,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        ) {
            ttText(
                text = caption,
                style = ttTypography.caption,
                color = ttLabelSecondary(),
            )
            ttText(
                text = command,
                style = ttTypography.footnote.ttMonospaced(),
                color = ttLabel(),
                modifier = Modifier.testTag(commandTag),
            )
        }

        ttGlassIconButton(
            image = ttActionIcons.copy,
            contentDescription = copyDescription,
            onClick = onCopy,
            tint = ttAccent(),
            modifier = Modifier.testTag(copyTag),
        )
    }
}

/** The reset-code sheet's own confirmation duration, kept in step with it. */
private const val AgentCopiedToastMillis = 2_000L

/** 22dp, the tab glyph size: large enough for a digit, small enough not to lead. */
private val AgentStepBadgeSize = 22.dp

/** One machine's own state, from the overview's observation time. */
private fun AgentMachine.state(overview: AgentOverview): AgentState = when {
    isPaused -> AgentState.Stopped
    overview.state == AgentState.Running -> AgentState.Running
    else -> AgentState.Offline
}

/** The Agent row's value. Total with no `else`, like every vocabulary mapping here. */
@Composable
private fun agentHostLabel(host: AgentHost): String = stringResource(
    when (host) {
        AgentHost.Desktop -> Res.string.settings_agent_host_desktop
        AgentHost.Remote -> Res.string.settings_agent_host_remote
    },
)

@Composable
private fun agentStateLabel(state: AgentState): String = stringResource(
    when (state) {
        AgentState.NotConfigured -> Res.string.settings_agent_empty_state
        AgentState.Running -> Res.string.settings_agent_running
        AgentState.Offline -> Res.string.settings_agent_offline
        AgentState.Stopped -> Res.string.settings_agent_stopped
    },
)

/** The banner's sentence, shown by the screen when a stop/start is refused. */
@Composable
internal fun agentFailureLabel(): String = stringResource(Res.string.settings_agent_failure)

const val SettingsAgentRowTag = "settings.agent.row"
const val SettingsAgentEmptyTag = "settings.agent.empty"
const val SettingsAgentSetupRowTag = "settings.agent.setup"
const val SettingsAgentSetupSheetTag = "settings.agent.setup.sheet"
const val SettingsAgentSetupCommandTag = "settings.agent.setup.command"
const val SettingsAgentSetupRunCommandTag = "settings.agent.setup.run.command"
const val SettingsAgentSetupCopyConnectTag = "settings.agent.setup.copy.connect"
const val SettingsAgentSetupCopyRunTag = "settings.agent.setup.copy.run"
const val SettingsAgentSetupCloseTag = "settings.agent.setup.close"
const val SettingsAgentFailureTag = "settings.agent.failure"

/** Per-machine tags, so a scenario can name the row it expects to move. */
const val SettingsAgentStopTagPrefix = "settings.agent.stop."
const val SettingsAgentStartTagPrefix = "settings.agent.start."
