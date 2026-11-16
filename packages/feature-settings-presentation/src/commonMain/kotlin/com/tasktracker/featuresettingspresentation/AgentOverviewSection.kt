package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttMonospaced
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTextField
import com.tasktracker.designsystem.ttToast
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featuresettingsdomain.AgentHost
import com.tasktracker.featuresettingsdomain.AgentMachine
import com.tasktracker.featuresettingsdomain.AgentOverview
import com.tasktracker.featuresettingsdomain.AgentSetupProject
import com.tasktracker.featuresettingsdomain.AgentSetupProvider
import com.tasktracker.featuresettingsdomain.AgentState
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_done
import com.tasktracker.resources.copied_to_clipboard
import com.tasktracker.resources.settings_agent_copy_code
import com.tasktracker.resources.settings_agent_copy_command
import com.tasktracker.resources.settings_agent_empty
import com.tasktracker.resources.settings_agent_empty_state
import com.tasktracker.resources.settings_agent_failure
import com.tasktracker.resources.settings_agent_host_desktop
import com.tasktracker.resources.settings_agent_host_remote
import com.tasktracker.resources.settings_agent_offline
import com.tasktracker.resources.settings_agent_running
import com.tasktracker.resources.settings_agent_section
import com.tasktracker.resources.settings_agent_setup
import com.tasktracker.resources.settings_agent_setup_agent_off
import com.tasktracker.resources.settings_agent_setup_base_url_hint
import com.tasktracker.resources.settings_agent_setup_base_url_label
import com.tasktracker.resources.settings_agent_setup_body
import com.tasktracker.resources.settings_agent_setup_code_label
import com.tasktracker.resources.settings_agent_setup_command_base_url
import com.tasktracker.resources.settings_agent_setup_command_connect
import com.tasktracker.resources.settings_agent_setup_command_label
import com.tasktracker.resources.settings_agent_setup_command_model
import com.tasktracker.resources.settings_agent_setup_command_run
import com.tasktracker.resources.settings_agent_setup_command_workspace
import com.tasktracker.resources.settings_agent_setup_expired
import com.tasktracker.resources.settings_agent_setup_expires
import com.tasktracker.resources.settings_agent_setup_failure
import com.tasktracker.resources.settings_agent_setup_get_code
import com.tasktracker.resources.settings_agent_setup_host_label
import com.tasktracker.resources.settings_agent_setup_model_hint
import com.tasktracker.resources.settings_agent_setup_model_label
import com.tasktracker.resources.settings_agent_setup_new_code
import com.tasktracker.resources.settings_agent_setup_note
import com.tasktracker.resources.settings_agent_setup_online
import com.tasktracker.resources.settings_agent_setup_paired
import com.tasktracker.resources.settings_agent_setup_project_empty
import com.tasktracker.resources.settings_agent_setup_project_failure
import com.tasktracker.resources.settings_agent_setup_project_label
import com.tasktracker.resources.settings_agent_setup_project_loading
import com.tasktracker.resources.settings_agent_setup_provider_anthropic
import com.tasktracker.resources.settings_agent_setup_provider_echo
import com.tasktracker.resources.settings_agent_setup_provider_label
import com.tasktracker.resources.settings_agent_setup_provider_openai
import com.tasktracker.resources.settings_agent_setup_requesting
import com.tasktracker.resources.settings_agent_setup_secret_note
import com.tasktracker.resources.settings_agent_setup_server_hint
import com.tasktracker.resources.settings_agent_setup_server_label
import com.tasktracker.resources.settings_agent_setup_title
import com.tasktracker.resources.settings_agent_setup_waiting
import com.tasktracker.resources.settings_agent_setup_workspace_hint
import com.tasktracker.resources.settings_agent_setup_workspace_label
import com.tasktracker.resources.settings_agent_start
import com.tasktracker.resources.settings_agent_stopped
import com.tasktracker.resources.settings_agent_stop
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The Agent block: the account's paired machines and, for an administrator, the
 * controls that manage them.
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
 * The setup wizard: pick a project and a model, mint one code, copy one command, and
 * watch the machine arrive.
 *
 * ## Why there is still a command
 *
 * The app mints the credential, but it cannot start a process on a machine it does not
 * own — the server never dials in, and `tt agent connect` is what redeems a code. So the
 * wizard's job is to make that one paste exact, and then to prove it worked: it polls the
 * fleet the section already renders and moves itself from "waiting" to "paired" to
 * "online" without the admin leaving Settings.
 *
 * ## Nothing secret is typed here
 *
 * A provider API key stays on the machine (`TT_AGENT_API_KEY`). The sheet says so rather
 * than offering a field, because a key entered on a phone that has no route to the worker
 * would be a credential the app could not deliver.
 */
@Composable
internal fun AgentSetupSheet(
    draft: AgentSetupDraft,
    projects: List<AgentSetupProject>,
    projectsLoading: Boolean,
    projectsFailure: AppError?,
    phase: AgentSetupPhase,
    onDismiss: () -> Unit,
    onSelectProject: (ProjectID) -> Unit,
    onSelectHost: (AgentHost) -> Unit,
    onSelectProvider: (AgentSetupProvider) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onWorkspaceChange: (String) -> Unit,
    onServerChange: (String) -> Unit,
    onRequestCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(SettingsAgentSetupSheetTag),
    ) {
        AgentSetupForm(
            draft = draft,
            projects = projects,
            projectsLoading = projectsLoading,
            projectsFailure = projectsFailure,
            phase = phase,
            onDismiss = onDismiss,
            onSelectProject = onSelectProject,
            onSelectHost = onSelectHost,
            onSelectProvider = onSelectProvider,
            onModelChange = onModelChange,
            onBaseUrlChange = onBaseUrlChange,
            onWorkspaceChange = onWorkspaceChange,
            onServerChange = onServerChange,
            onRequestCode = onRequestCode,
        )
    }
}

/**
 * The wizard's fields and its status, with no frame of its own.
 *
 * It emits into the caller's column, so the sheet keeps the frame — scrim, spring, scroll and
 * the two safe-area insets — in one place and the form stays a sequence of fields. Settings is
 * already a two-column screen when wide, so this form is never given a pane of its own; it has
 * one caller and is private to make that explicit.
 */
@Composable
private fun AgentSetupForm(
    draft: AgentSetupDraft,
    projects: List<AgentSetupProject>,
    projectsLoading: Boolean,
    projectsFailure: AppError?,
    phase: AgentSetupPhase,
    onDismiss: () -> Unit,
    onSelectProject: (ProjectID) -> Unit,
    onSelectHost: (AgentHost) -> Unit,
    onSelectProvider: (AgentSetupProvider) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onWorkspaceChange: (String) -> Unit,
    onServerChange: (String) -> Unit,
    onRequestCode: () -> Unit,
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

    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        copies++
    }

    // A code exists only while the machine has not redeemed it. Once the runner appears,
    // the code is spent and the command beside it would redeem nothing.
    val code = (phase as? AgentSetupPhase.Ready)?.code?.code
    val hasCode = code != null
    val isWaiting = phase is AgentSetupPhase.Ready || phase is AgentSetupPhase.Paired
    // An expired code is a code that was minted: the button's job is to replace it, not to
    // offer a first one.
    val hasMinted = hasCode || phase is AgentSetupPhase.Expired

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

        AgentSetupFieldLabel(stringResource(Res.string.settings_agent_setup_project_label))
        when {
            projectsLoading -> AgentSetupNote(
                text = stringResource(Res.string.settings_agent_setup_project_loading),
            )

            projectsFailure != null -> AgentSetupNote(
                text = stringResource(Res.string.settings_agent_setup_project_failure),
                isError = true,
            )

            projects.isEmpty() -> AgentSetupNote(
                text = stringResource(Res.string.settings_agent_setup_project_empty),
            )

            else -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                projects.forEach { project ->
                    val selected = draft.project?.id == project.id
                    ttGlassButton(
                        label = project.name,
                        onClick = { onSelectProject(project.id) },
                        enabled = !hasCode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SettingsAgentSetupProjectTagPrefix + project.id.stringValue),
                        style = if (selected) {
                            TTGlassButtonStyle(tint = ttAccent(), prominent = true)
                        } else {
                            TTGlassButtonStyle()
                        },
                    )
                }
                // Pairing a machine to a project that does not dispatch is a machine that
                // sits idle; the server accepts the code either way.
                if (draft.project?.agentEnabled == false) {
                    AgentSetupNote(text = stringResource(Res.string.settings_agent_setup_agent_off))
                }
            }
        }

        AgentSetupFieldLabel(stringResource(Res.string.settings_agent_setup_host_label))
        // The same control the backlog page switches sections with: two exclusive choices that
        // are one question, drawn on one slab with the selection sliding between them, rather
        // than two buttons whose "prominent" style had to carry the selection by itself.
        TTGlassSegmentedPicker(
            options = AgentHost.entries,
            selection = draft.host,
            // The picker has no `enabled`: a live code freezes the draft, so the gate is here.
            onSelect = { host -> if (!hasCode) onSelectHost(host) },
            modifier = Modifier.fillMaxWidth(),
            title = { host -> agentHostLabel(host) },
            testTag = { host ->
                when (host) {
                    AgentHost.Desktop -> SettingsAgentSetupHostDesktopTag
                    AgentHost.Remote -> SettingsAgentSetupHostRemoteTag
                }
            },
        )

        AgentSetupFieldLabel(stringResource(Res.string.settings_agent_setup_provider_label))
        // Segmented rather than the chip flow it was: the three providers are one question with
        // one answer, and the chip row drew its glass across the full width while the three
        // names filled a fraction of it — a background longer than its content. Three equal
        // segments put the slab's edges where the content's edges are.
        TTGlassSegmentedPicker(
            options = AgentSetupProvider.entries,
            selection = draft.provider,
            onSelect = onSelectProvider,
            modifier = Modifier.fillMaxWidth(),
            title = { provider -> agentSetupProviderLabel(provider) },
        )

        if (draft.provider != AgentSetupProvider.Echo) {
            AgentSetupFieldLabel(stringResource(Res.string.settings_agent_setup_model_label))
            ttTextField(
                value = draft.model,
                onValueChange = onModelChange,
                hint = stringResource(Res.string.settings_agent_setup_model_hint),
                modifier = Modifier.testTag(SettingsAgentSetupModelTag),
            )
        }

        if (draft.provider == AgentSetupProvider.OpenAiCompatible) {
            AgentSetupFieldLabel(stringResource(Res.string.settings_agent_setup_base_url_label))
            ttTextField(
                value = draft.baseUrl,
                onValueChange = onBaseUrlChange,
                hint = stringResource(Res.string.settings_agent_setup_base_url_hint),
                modifier = Modifier.testTag(SettingsAgentSetupBaseUrlTag),
            )
        }

        AgentSetupFieldLabel(stringResource(Res.string.settings_agent_setup_workspace_label))
        ttTextField(
            value = draft.workspace,
            onValueChange = onWorkspaceChange,
            hint = stringResource(Res.string.settings_agent_setup_workspace_hint),
            modifier = Modifier.testTag(SettingsAgentSetupWorkspaceTag),
        )

        AgentSetupFieldLabel(stringResource(Res.string.settings_agent_setup_server_label))
        ttTextField(
            value = draft.serverUrl,
            onValueChange = onServerChange,
            hint = stringResource(Res.string.settings_agent_setup_server_hint),
            modifier = Modifier.testTag(SettingsAgentSetupServerTag),
        )

        // The one action that changes server state. Disabled while a project is missing,
        // because the code is bound to a project and there would be nothing to bind it to.
        ttGlassButton(
            label = stringResource(
                if (hasMinted) {
                    Res.string.settings_agent_setup_new_code
                } else {
                    Res.string.settings_agent_setup_get_code
                },
            ),
            onClick = onRequestCode,
            enabled = draft.project != null && phase !is AgentSetupPhase.Requesting,
            loading = phase is AgentSetupPhase.Requesting,
            modifier = Modifier.testTag(SettingsAgentSetupGetCodeTag),
            style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                when (phase) {
                    AgentSetupPhase.Editing -> Unit

                    AgentSetupPhase.Requesting -> AgentSetupNote(
                        text = stringResource(Res.string.settings_agent_setup_requesting),
                    )

                    is AgentSetupPhase.Failed -> AgentSetupNote(
                        text = stringResource(Res.string.settings_agent_setup_failure),
                        isError = true,
                        tag = SettingsAgentSetupErrorTag,
                    )

                    AgentSetupPhase.Expired -> AgentSetupNote(
                        text = stringResource(Res.string.settings_agent_setup_expired),
                        isError = true,
                        tag = SettingsAgentSetupStatusTag,
                    )

                    is AgentSetupPhase.Paired -> AgentSetupNote(
                        text = stringResource(Res.string.settings_agent_setup_paired),
                        tag = SettingsAgentSetupStatusTag,
                    )

                    is AgentSetupPhase.Online -> AgentSetupNote(
                        text = stringResource(Res.string.settings_agent_setup_online, phase.machine.name),
                        tag = SettingsAgentSetupStatusTag,
                    )

                    is AgentSetupPhase.Ready -> {
                        ttText(
                            text = stringResource(Res.string.settings_agent_setup_waiting),
                            style = ttTypography.footnote,
                            color = ttLabelSecondary(),
                            modifier = Modifier.testTag(SettingsAgentSetupStatusTag),
                        )
                        ttText(
                            text = stringResource(Res.string.settings_agent_setup_code_label),
                            style = ttTypography.caption,
                            color = ttLabelSecondary(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
                                .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                        ) {
                            ttText(
                                text = phase.code.code,
                                style = ttTypography.footnote.ttMonospaced(),
                                color = ttLabel(),
                                modifier = Modifier.weight(1f).testTag(SettingsAgentSetupCodeTag),
                            )
                            ttGlassIconButton(
                                image = ttActionIcons.copy,
                                contentDescription = stringResource(Res.string.settings_agent_copy_code),
                                onClick = { copy(phase.code.code) },
                                tint = ttAccent(),
                                modifier = Modifier.testTag(SettingsAgentSetupCopyCodeTag),
                            )
                        }
                        ttText(
                            text = stringResource(
                                Res.string.settings_agent_setup_expires,
                                DateCoding.format(phase.code.expiresAt),
                            ),
                            style = ttTypography.footnote,
                            color = ttLabelSecondary(),
                        )

                        val command = agentSetupCommand(draft, phase.code.code)
                        ttText(
                            text = stringResource(Res.string.settings_agent_setup_command_label),
                            style = ttTypography.caption,
                            color = ttLabelSecondary(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row))
                                .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                        ) {
                            ttText(
                                text = command,
                                style = ttTypography.footnote.ttMonospaced(),
                                color = ttLabel(),
                                modifier = Modifier.weight(1f).testTag(SettingsAgentSetupCommandTag),
                            )
                            ttGlassIconButton(
                                image = ttActionIcons.copy,
                                contentDescription = stringResource(Res.string.settings_agent_copy_command),
                                onClick = { copy(command) },
                                tint = ttAccent(),
                                modifier = Modifier.testTag(SettingsAgentSetupCopyCommandTag),
                            )
                        }
                        ttText(
                            text = stringResource(Res.string.settings_agent_setup_secret_note),
                            style = ttTypography.footnote,
                            color = ttLabelSecondary(),
                        )
                    }
                }

                if (isWaiting) {
                    ttText(
                        text = stringResource(Res.string.settings_agent_setup_note),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                    )
                }
            }

            // Anchored to the status block rather than the sheet's bottom edge: floating
            // over Done would hide the way out for the two seconds the toast is up.
            if (copies > 0) {
                ttToast(
                    message = stringResource(Res.string.copied_to_clipboard),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = ttSpacing.sm),
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

/** A small caption above one question in the wizard. */
@Composable
private fun AgentSetupFieldLabel(text: String) {
    ttText(
        text = text,
        style = ttTypography.captionEmphasised,
        color = ttLabelSecondary(),
    )
}

/** One plain sentence in the wizard, in the error colour when it reports a failure. */
@Composable
private fun AgentSetupNote(
    text: String,
    isError: Boolean = false,
    tag: String? = null,
) {
    ttText(
        text = text,
        style = ttTypography.footnote,
        color = if (isError) ttError() else ttLabelSecondary(),
        modifier = if (tag == null) Modifier else Modifier.testTag(tag),
    )
}

/**
 * The one line to paste on the machine.
 *
 * Assembled from resource pieces rather than one template so the optional flags can be
 * omitted entirely — `--model` on an `echo` provider, or `--base-url` against a provider's own
 * default, are flags the CLI would reject as empty strings. `tt agent run` needs no provider
 * flags of its own: `connect` writes them into the machine's config.
 */
@Composable
private fun agentSetupCommand(draft: AgentSetupDraft, code: String): String {
    val connect = stringResource(
        Res.string.settings_agent_setup_command_connect,
        draft.serverUrl,
        code,
        draft.provider.wireValue,
    )
    val model = draft.model.trim().takeIf { it.isNotEmpty() }
        ?.let { stringResource(Res.string.settings_agent_setup_command_model, it) }
        .orEmpty()
    val baseUrl = draft.baseUrl.trim().takeIf { it.isNotEmpty() }
        ?.let { stringResource(Res.string.settings_agent_setup_command_base_url, it) }
        .orEmpty()
    val run = stringResource(Res.string.settings_agent_setup_command_run)
    val workspace = draft.workspace.trim().takeIf { it.isNotEmpty() }
        ?.let { stringResource(Res.string.settings_agent_setup_command_workspace, it) }
        .orEmpty()
    return connect + model + baseUrl + run + workspace
}

@Composable
private fun agentSetupProviderLabel(provider: AgentSetupProvider): String = stringResource(
    when (provider) {
        AgentSetupProvider.OpenAiCompatible -> Res.string.settings_agent_setup_provider_openai
        AgentSetupProvider.Anthropic -> Res.string.settings_agent_setup_provider_anthropic
        AgentSetupProvider.Echo -> Res.string.settings_agent_setup_provider_echo
    },
)

/** The reset-code sheet's own confirmation duration, kept in step with it. */
private const val AgentCopiedToastMillis = 2_000L

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
const val SettingsAgentSetupProjectTagPrefix = "settings.agent.setup.project."
const val SettingsAgentSetupHostDesktopTag = "settings.agent.setup.host.desktop"
const val SettingsAgentSetupHostRemoteTag = "settings.agent.setup.host.remote"
const val SettingsAgentSetupModelTag = "settings.agent.setup.model"
const val SettingsAgentSetupBaseUrlTag = "settings.agent.setup.baseUrl"
const val SettingsAgentSetupWorkspaceTag = "settings.agent.setup.workspace"
const val SettingsAgentSetupServerTag = "settings.agent.setup.server"
const val SettingsAgentSetupGetCodeTag = "settings.agent.setup.getCode"
const val SettingsAgentSetupCodeTag = "settings.agent.setup.code"
const val SettingsAgentSetupCommandTag = "settings.agent.setup.command"
const val SettingsAgentSetupCopyCodeTag = "settings.agent.setup.copy.code"
const val SettingsAgentSetupCopyCommandTag = "settings.agent.setup.copy.command"
const val SettingsAgentSetupStatusTag = "settings.agent.setup.status"
const val SettingsAgentSetupErrorTag = "settings.agent.setup.error"
const val SettingsAgentSetupCloseTag = "settings.agent.setup.close"
const val SettingsAgentFailureTag = "settings.agent.failure"

/** Per-machine tags, so a scenario can name the row it expects to move. */
const val SettingsAgentStopTagPrefix = "settings.agent.stop."
const val SettingsAgentStartTagPrefix = "settings.agent.start."
