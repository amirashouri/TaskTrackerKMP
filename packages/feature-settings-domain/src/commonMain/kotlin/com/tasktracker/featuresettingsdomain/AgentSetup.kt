package com.tasktracker.featuresettingsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.ProjectID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * One project the setup wizard can pair a machine to.
 *
 * A flat copy rather than the projects feature's `AdminProject`, because this module may not
 * depend on `:packages:feature-projects-domain`: the shell maps one into the other, and the
 * sheet only ever needs the three fields that name a project and say whether it accepts agent
 * work.
 *
 * [agentEnabled] is carried so the wizard can warn before minting a code the project would
 * never dispatch to: the server accepts the pairing either way, and the admin would otherwise
 * pair a machine and watch it sit idle.
 */
data class AgentSetupProject(
    val id: ProjectID,
    val name: String,
    val keyPrefix: String,
    val agentEnabled: Boolean,
)

/**
 * The model backend the wizard writes into the command.
 *
 * The wire values are `tt agent connect`'s own `--provider` vocabulary, not a translation of
 * it, because the command is composed here and copied verbatim. [Echo] is the CLI's test
 * provider: it answers every run with a fixed line and never calls a model, which is the
 * honest way to check that pairing works before an API key is involved.
 */
enum class AgentSetupProvider(val wireValue: String) {
    OpenAiCompatible("openai-compatible"),
    Anthropic("anthropic"),
    Echo("echo"),
}

/**
 * A short-lived code that lets one machine join a project.
 *
 * The same server object the project editor's pairing sheet reads, named again here because
 * the two features cannot share a domain type. It is a one-time credential, not the machine's
 * token: the server stores only its hash, redeeming it mints the long-lived token, and it
 * expires. The wizard holds it in memory and never writes it to storage or a log.
 */
data class AgentSetupCode(
    val code: String,
    val projectID: ProjectID,
    val expiresAt: Instant,
)

/**
 * The pairing call behind the wizard, or `null` in a build with no API root.
 *
 * A gateway rather than more methods on a port for the reason [AgentSettingsGateway] exists:
 * this module names the shape and only the data module knows the shape is answered over HTTP.
 */
interface AgentPairingGateway {

    /**
     * Issues one single-use code for [projectID].
     *
     * [host] is the wizard's "where does this machine run" answer; the server stores it on the
     * runner the code eventually creates, so the Settings roster can label the row without
     * asking the machine. A lost response means asking for a new code, not recovering the old
     * one — the server keeps only the hash.
     */
    @Throws(AppError::class, CancellationException::class)
    suspend fun createCode(projectID: ProjectID, host: AgentHost): AgentSetupCode
}
