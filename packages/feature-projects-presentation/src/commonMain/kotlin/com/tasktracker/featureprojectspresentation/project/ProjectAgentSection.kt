package com.tasktracker.featureprojectspresentation

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import com.tasktracker.core.DateCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttMonospaced
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttToast
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectsdomain.AgentPairingPort
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_done
import com.tasktracker.resources.copied_to_clipboard
import com.tasktracker.resources.project_agent_body
import com.tasktracker.resources.project_agent_code_label
import com.tasktracker.resources.project_agent_command
import com.tasktracker.resources.project_agent_copy_code
import com.tasktracker.resources.project_agent_copy_command
import com.tasktracker.resources.project_agent_expires
import com.tasktracker.resources.project_agent_failure
import com.tasktracker.resources.project_agent_note
import com.tasktracker.resources.project_agent_pair
import com.tasktracker.resources.project_agent_retry
import com.tasktracker.resources.project_agent_title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The project-scoped agent block on the admin editor: the entry point to pairing, and
 * the sheet that carries the one-time code.
 *
 * A run is started from the work item, not here. This block only answers "how does a
 * machine join this project", and the sheet says plainly that the agent runs on the
 * machine holding the code and connects out to the server, so a VPS behind NAT is as
 * valid as the desktop in front of the admin.
 */
@Composable
fun ProjectAgentSection(
    port: AgentPairingPort,
    projectID: ProjectID,
    modifier: Modifier = Modifier,
) {
    var isOpen by remember(projectID) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassCard()
            .padding(ttSpacing.md)
            .testTag(ProjectAgentSectionTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = stringResource(Res.string.project_agent_title),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            ttGlassButton(
                label = stringResource(Res.string.project_agent_pair),
                onClick = { isOpen = true },
                modifier = Modifier.testTag(ProjectAgentPairTag),
                style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
            )
        }
        ttText(
            text = stringResource(Res.string.project_agent_body),
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
        )
    }

    if (isOpen) {
        AgentPairingSheet(
            port = port,
            projectID = projectID,
            onDismiss = { isOpen = false },
        )
    }
}

/**
 * The one-time code, the command that spends it, and a retry that asks for a new one.
 *
 * Nothing here is persisted. The code lives in [ProjectAgentSetupModel] until the sheet
 * closes; the command is composed in front of the admin and copied through the same
 * clipboard path the settings sheet uses.
 */
@Composable
private fun AgentPairingSheet(
    port: AgentPairingPort,
    projectID: ProjectID,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val model = remember(port, projectID) { ProjectAgentSetupModel(port, projectID) }
    var copies by remember { mutableIntStateOf(0) }

    LaunchedEffect(model) { model.request() }
    LaunchedEffect(copies) {
        if (copies != 0) {
            delay(COPIED_TOAST_MILLIS)
            copies = 0
        }
    }

    fun copy(value: String) {
        clipboard.setText(AnnotatedString(value))
        copies++
    }

    TTBottomSheetScaffold(
        visible = true,
        onDismissRequest = {
            model.clear()
            onDismiss()
        },
        modifier = Modifier.testTag(ProjectAgentSheetTag),
    ) {
        ttText(
            text = stringResource(Res.string.project_agent_pair),
            style = ttTypography.title,
            color = ttLabel(),
        )

        val code = model.pairingCode
        when {
            model.isRequesting -> ttText(
                text = stringResource(Res.string.project_agent_body),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )

            model.errorMessage != null -> ttText(
                text = stringResource(Res.string.project_agent_failure),
                style = ttTypography.footnote,
                color = ttError(),
                modifier = Modifier.testTag(ProjectAgentErrorTag),
            )

            code != null -> {
                ttText(
                    text = stringResource(Res.string.project_agent_code_label),
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
                        text = code.code,
                        style = ttTypography.footnote.ttMonospaced(),
                        color = ttLabel(),
                        modifier = Modifier.weight(1f).testTag(ProjectAgentCodeTag),
                    )
                    ttGlassIconButton(
                        image = ttActionIcons.copy,
                        contentDescription = stringResource(Res.string.project_agent_copy_code),
                        onClick = { copy(code.code) },
                        tint = ttAccent(),
                        modifier = Modifier.testTag(ProjectAgentCopyCodeTag),
                    )
                }
                ttText(
                    text = stringResource(Res.string.project_agent_expires, DateCoding.format(code.expiresAt)),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )

                val command = stringResource(Res.string.project_agent_command, code.code)
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
                        modifier = Modifier.weight(1f).testTag(ProjectAgentCommandTag),
                    )
                    ttGlassIconButton(
                        image = ttActionIcons.copy,
                        contentDescription = stringResource(Res.string.project_agent_copy_command),
                        onClick = { copy(command) },
                        tint = ttAccent(),
                        modifier = Modifier.testTag(ProjectAgentCopyCommandTag),
                    )
                }
                ttText(
                    text = stringResource(Res.string.project_agent_note),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.project_agent_retry),
                    onClick = { scope.launch { model.request() } },
                    enabled = !model.isRequesting,
                    modifier = Modifier.testTag(ProjectAgentRetryTag),
                )
                ttGlassButton(
                    label = stringResource(Res.string.common_done),
                    onClick = {
                        model.clear()
                        onDismiss()
                    },
                    modifier = Modifier.testTag(ProjectAgentDoneTag),
                    style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                )
            }

            if (copies > 0) {
                ttToast(
                    message = stringResource(Res.string.copied_to_clipboard),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

const val ProjectAgentSectionTag = "projects.agent.section"
const val ProjectAgentPairTag = "projects.agent.pair"
const val ProjectAgentSheetTag = "projects.agent.sheet"
const val ProjectAgentCodeTag = "projects.agent.code"
const val ProjectAgentCommandTag = "projects.agent.command"
const val ProjectAgentCopyCodeTag = "projects.agent.copy.code"
const val ProjectAgentCopyCommandTag = "projects.agent.copy.command"
const val ProjectAgentRetryTag = "projects.agent.retry"
const val ProjectAgentDoneTag = "projects.agent.done"
const val ProjectAgentErrorTag = "projects.agent.error"
