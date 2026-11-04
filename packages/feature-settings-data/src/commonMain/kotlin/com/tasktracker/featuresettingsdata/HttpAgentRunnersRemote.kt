package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.parseAgentRunnerID
import com.tasktracker.featuresettingsdomain.AgentMachine
import com.tasktracker.featuresettingsdomain.AgentSettingsGateway
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The paired-machine API, over the authenticated client.
 *
 * `GET /v1/agent/runners` is scoped to the caller, so this needs no user id of
 * its own. Every optional key is declared with a default because the server omits
 * it when empty; a renamed or missing required key therefore fails to decode
 * rather than reading as a blank machine.
 *
 * `pausedAt` is the settings screen's stop state. It is additive on the runner
 * shape: the client that shipped before this section never called this route.
 */
class HttpAgentRunnersRemote(private val client: HttpClient) : AgentSettingsGateway {

    @Serializable
    internal data class RunnerDto(
        val id: String,
        val name: String,
        val provider: String = "",
        val modelLabel: String = "",
        val lastSeenAt: String? = null,
        val pausedAt: String? = null,
        val revokedAt: String? = null,
        val createdAt: String,
    )

    @Serializable
    internal data class RunnersEnvelope(val runners: List<RunnerDto>)

    @Serializable
    internal data class RunnerEnvelope(val runner: RunnerDto)

    override suspend fun loadMachines(): List<AgentMachine> = client.send(
        HttpRequest(
            method = HttpMethod.Get,
            path = "/v1/agent/runners",
        ),
        decoding = RunnersEnvelope.serializer(),
    ).runners.map { it.toDomain() }

    override suspend fun setPaused(machineID: AgentRunnerID, paused: Boolean): AgentMachine = client.send(
        HttpRequest(
            method = HttpMethod.Post,
            path = "/v1/agent/runners/${machineID.stringValue}/${if (paused) "stop" else "start"}",
        ),
        decoding = RunnerEnvelope.serializer(),
    ).runner.toDomain()

    private fun RunnerDto.toDomain(): AgentMachine = AgentMachine(
        id = parseAgentRunnerID(id)
            ?: throw AppError.Decoding("Agent runner id \"$id\" is not a UUID."),
        name = name,
        provider = provider,
        modelLabel = modelLabel,
        lastSeenAt = lastSeenAt.toInstant("lastSeenAt"),
        pausedAt = pausedAt.toInstant("pausedAt"),
        revokedAt = revokedAt.toInstant("revokedAt"),
    )

    private fun String?.toInstant(field: String): Instant? {
        if (this == null) return null
        return DateCoding.parse(this)
            ?: throw AppError.Decoding("Agent runner $field \"$this\" is not a valid date.")
    }
}
