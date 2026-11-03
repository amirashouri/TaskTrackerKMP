package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AgentRunID
import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.WorkItemID
import com.tasktracker.core.parseAgentRunID
import com.tasktracker.core.parseAgentRunnerID
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseUserID
import com.tasktracker.core.parseWorkItemID
import com.tasktracker.core.roundedToSecond
import com.tasktracker.featureprojectsdomain.AgentRun
import com.tasktracker.featureprojectsdomain.AgentRunEvent
import com.tasktracker.featureprojectsdomain.AgentRunPage
import com.tasktracker.featureprojectsdomain.AgentRunStatus
import com.tasktracker.featureprojectsdomain.AgentRunsPort
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import com.tasktracker.network.HttpRequest
import com.tasktracker.network.jsonBody
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The agent-runs API, over the authenticated client.
 *
 * Unlike the admin projects pair, there is no repository beneath this: a run is
 * server-owned, cannot progress offline and is never merged, so caching it would
 * create a second source of truth for something the server alone can advance.
 *
 * Timestamps arrive as ISO-8601 UTC strings and are parsed here rather than left
 * as strings, so every comparison above this layer is on a real instant. The
 * server may omit `runnerId`, `startedAt`, `finishedAt`, `summary` and `error`;
 * those DTO members carry defaults, and every other key is required — a rename
 * therefore fails to decode rather than silently reading nothing.
 */
class HttpAgentRunsRemote(private val client: HttpClient) : AgentRunsPort {

    @Serializable
    private data class DispatchBody(val workItemId: String)

    @Serializable
    internal data class AgentRunDto(
        val id: String,
        val workItemId: String,
        val projectId: String,
        val status: String,
        val requestedBy: String,
        val runnerId: String? = null,
        val cancelRequested: Boolean = false,
        val createdAt: String,
        val startedAt: String? = null,
        val finishedAt: String? = null,
        val summary: String = "",
        val error: String = "",
    )

    @Serializable
    internal data class AgentEventDto(
        val seq: Long,
        val kind: String,
        val message: String,
        val payload: String,
        val createdAt: String,
    )

    @Serializable
    internal data class RunEnvelope(val run: AgentRunDto)

    @Serializable
    internal data class RunsEnvelope(val runs: List<AgentRunDto>)

    @Serializable
    internal data class RunDetailEnvelope(
        val run: AgentRunDto,
        val events: List<AgentEventDto>,
        val hasMore: Boolean,
    )

    override suspend fun dispatch(workItemID: WorkItemID): AgentRun = client.send(
        HttpRequest(
            method = HttpMethod.Post,
            path = "/v1/agent/runs",
            body = jsonBody(DispatchBody(workItemID.stringValue)),
        ),
        decoding = RunEnvelope.serializer(),
    ).run.toDomain()

    override suspend fun runs(workItemID: WorkItemID, limit: Int): List<AgentRun> = client.send(
        HttpRequest(
            method = HttpMethod.Get,
            path = "/v1/agent/runs",
            queryItems = listOf(
                HttpQueryItem(name = "workItemId", value = workItemID.stringValue),
                HttpQueryItem(name = "limit", value = limit.toString()),
            ),
        ),
        decoding = RunsEnvelope.serializer(),
    ).runs.map { it.toDomain() }

    override suspend fun run(runID: AgentRunID, afterSeq: Long): AgentRunPage {
        val envelope = client.send(
            HttpRequest(
                method = HttpMethod.Get,
                path = "/v1/agent/runs/${runID.stringValue}",
                queryItems = listOf(HttpQueryItem(name = "afterSeq", value = afterSeq.toString())),
            ),
            decoding = RunDetailEnvelope.serializer(),
        )
        return AgentRunPage(
            run = envelope.run.toDomain(),
            events = envelope.events.map { it.toDomain() },
            hasMore = envelope.hasMore,
        )
    }

    override suspend fun cancel(runID: AgentRunID): AgentRun = client.send(
        HttpRequest(
            method = HttpMethod.Post,
            path = "/v1/agent/runs/${runID.stringValue}/cancel",
        ),
        decoding = RunEnvelope.serializer(),
    ).run.toDomain()

    private fun AgentRunDto.toDomain(): AgentRun = AgentRun(
        id = parseAgentRunID(id)
            ?: throw AppError.Decoding("Agent run id \"$id\" is not a UUID."),
        workItemID = parseWorkItemID(workItemId)
            ?: throw AppError.Decoding("Agent run workItemId \"$workItemId\" is not a UUID."),
        projectID = parseProjectID(projectId)
            ?: throw AppError.Decoding("Agent run projectId \"$projectId\" is not a UUID."),
        status = AgentRunStatus.fromWireValue(status)
            ?: throw AppError.Decoding("Agent run status \"$status\" is not one this app knows."),
        requestedBy = parseUserID(requestedBy)
            ?: throw AppError.Decoding("Agent run requestedBy \"$requestedBy\" is not a UUID."),
        runnerID = runnerId?.let { raw ->
            parseAgentRunnerID(raw)
                ?: throw AppError.Decoding("Agent run runnerId \"$raw\" is not a UUID.")
        },
        cancelRequested = cancelRequested,
        createdAt = createdAt.toInstant("createdAt"),
        startedAt = startedAt?.toInstant("startedAt"),
        finishedAt = finishedAt?.toInstant("finishedAt"),
        summary = summary,
        error = error,
    )

    private fun AgentEventDto.toDomain(): AgentRunEvent = AgentRunEvent(
        seq = seq,
        kind = kind,
        message = message,
        payload = payload,
        createdAt = createdAt.toInstant("createdAt"),
    )

    private fun String.toInstant(field: String): Instant =
        DateCoding.parse(this)?.roundedToSecond()
            ?: throw AppError.Decoding("Agent run $field \"$this\" is not a valid date.")
}
