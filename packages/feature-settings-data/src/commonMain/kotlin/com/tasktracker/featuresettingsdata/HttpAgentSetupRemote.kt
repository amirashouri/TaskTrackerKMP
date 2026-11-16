package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.DateCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.core.parseProjectID
import com.tasktracker.featuresettingsdomain.AgentHost
import com.tasktracker.featuresettingsdomain.AgentPairingGateway
import com.tasktracker.featuresettingsdomain.AgentSetupCode
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import com.tasktracker.network.jsonBody
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `POST /v1/agent/runners/pairing-codes`, over the authenticated client.
 *
 * The same route the project editor's pairing sheet calls, reached through this module's own
 * thin client because the two features may not import each other's data. Deliberately only
 * the mint: the wizard confirms the pairing by watching [HttpAgentRunnersRemote]'s list, which
 * is the read the Settings section already performs, rather than by inventing a second way to
 * ask whether a machine arrived.
 *
 * Every key is required, matching the server's response: `code`, `projectId` and `expiresAt`
 * are always present, so a renamed one fails to decode instead of reporting a blank code.
 *
 * Named for the setup wizard rather than for the route, because `feature-projects-data` already
 * has an `HttpAgentPairingRemote` for the same endpoint and the composition root imports both:
 * two classes with one simple name would be an import collision in the one file that builds
 * them, and an import alias there would hide which pairing sheet a line belongs to.
 */
class HttpAgentSetupRemote(private val client: HttpClient) : AgentPairingGateway {

    @Serializable
    private data class CreatePairingCodeBody(
        val projectId: String,
        val host: String,
    )

    @Serializable
    internal data class PairingCodeDto(
        val code: String,
        val projectId: String,
        val expiresAt: String,
    )

    override suspend fun createCode(projectID: ProjectID, host: AgentHost): AgentSetupCode {
        val response = client.send(
            HttpRequest(
                method = HttpMethod.Post,
                path = "/v1/agent/runners/pairing-codes",
                body = jsonBody(CreatePairingCodeBody(projectID.stringValue, host.wireValue)),
            ),
            decoding = PairingCodeDto.serializer(),
        )
        return AgentSetupCode(
            code = response.code,
            projectID = parseProjectID(response.projectId)
                ?: throw AppError.Decoding("Pairing code projectId \"${response.projectId}\" is not a UUID."),
            expiresAt = DateCoding.parse(response.expiresAt)
                ?: throw AppError.Decoding("Pairing code expiresAt \"${response.expiresAt}\" is not a valid date."),
        )
    }
}
