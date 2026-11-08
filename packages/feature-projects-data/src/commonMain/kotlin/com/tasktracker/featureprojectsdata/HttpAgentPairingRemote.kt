package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.roundedToSecond
import com.tasktracker.featureprojectsdomain.AgentPairingCode
import com.tasktracker.featureprojectsdomain.AgentPairingPort
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import com.tasktracker.network.jsonBody
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `POST /v1/agent/runners/pairing-codes`, over the authenticated client.
 *
 * The route is project-scoped: the body names the project, the server checks the
 * caller's membership and binds the code to it. The response carries the plaintext
 * code exactly once, which is why this value is returned straight to the sheet and
 * never stored.
 */
class HttpAgentPairingRemote(private val client: HttpClient) : AgentPairingPort {

    @Serializable
    private data class CreatePairingCodeBody(val projectId: String)

    @Serializable
    internal data class PairingCodeDto(
        val code: String,
        val projectId: String,
        val expiresAt: String,
    )

    override suspend fun createPairingCode(projectID: ProjectID): AgentPairingCode {
        val response = client.send(
            HttpRequest(
                method = HttpMethod.Post,
                path = "/v1/agent/runners/pairing-codes",
                body = jsonBody(CreatePairingCodeBody(projectID.stringValue)),
            ),
            decoding = PairingCodeDto.serializer(),
        )
        return AgentPairingCode(
            code = response.code,
            projectID = parseProjectID(response.projectId)
                ?: throw AppError.Decoding("Pairing code projectId \"${response.projectId}\" is not a UUID."),
            expiresAt = response.expiresAt.toInstant(),
        )
    }

    private fun String.toInstant(): Instant =
        DateCoding.parse(this)?.roundedToSecond()
            ?: throw AppError.Decoding("Pairing code expiresAt \"$this\" is not a valid date.")
}
