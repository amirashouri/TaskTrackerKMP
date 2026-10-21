package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.featuresettingsdomain.UserPosition
import com.tasktracker.featuresettingsdomain.UserProfileChanges
import com.tasktracker.featuresettingsdomain.UserStatus
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The self-profile transport's wire shape.
 *
 * The properties worth pinning are the ones invisible in a decoded value: the method, the path,
 * `requiresAuth` (the route rejects an anonymous call), and — for the write — the *set of keys*
 * in the body. A PATCH that sent every field would still decode to the right profile while
 * quietly clearing a position an admin had set, so "only what changed" is asserted on the JSON,
 * not on the result.
 */
class HttpUserProfileGatewayTest {

    private fun gateway(
        response: String,
        failure: AppError? = null,
    ): Pair<HttpUserProfileGateway, RecordingHttpClient> {
        val client = RecordingHttpClient(response.encodeToByteArray(), failure)
        return HttpUserProfileGateway(client) to client
    }

    @Test
    fun loadReadsTheCallersProfileFromMe() = runTest {
        val (gateway, client) = gateway(
            """
            {
              "id": "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
              "email": "ada@example.com",
              "display_name": "Ada Lovelace",
              "position": "Product owner",
              "avatar_id": "7",
              "presence": "be_right_back",
              "presence_note": "Back in ten minutes"
            }
            """.trimIndent(),
        )

        val details = gateway.load()

        val request = client.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/me", request.path)
        assertTrue(request.requiresAuth)
        assertNull(request.body)

        assertEquals(
            UserDetails(
                fullName = "Ada Lovelace",
                position = UserPosition.ProductOwner,
                avatarId = 7,
                email = "ada@example.com",
                status = UserStatus.BeRightBack,
                statusText = "Back in ten minutes",
            ),
            details,
        )
    }

    @Test
    fun aProfileWithOnlyTheRequiredKeysStillDecodes() = runTest {
        // The server omits empty optionals (`omitempty`). Every field has a default precisely so
        // an installed client keeps decoding when one is missing.
        val (gateway, _) = gateway(
            """{"id":"u1","email":"ada@example.com","presence":"active"}""",
        )

        assertEquals(
            UserDetails(email = "ada@example.com", status = UserStatus.Active),
            gateway.load(),
        )
    }

    @Test
    fun anAdminFreeTextPositionLeavesThePickerUnselected() = runTest {
        val (gateway, _) = gateway(
            """{"id":"u1","email":"ada@example.com","position":"Senior Designer","presence":"busy"}""",
        )

        val details = gateway.load()
        assertNull(details.position)
        assertEquals(UserStatus.Busy, details.status)
    }

    @Test
    fun updateSendsOnlyTheFieldsThatChanged() = runTest {
        val (gateway, client) = gateway(
            """{"id":"u1","email":"ada@example.com","display_name":"Ada Lovelace","presence":"away","presence_note":"At the dentist"}""",
        )

        gateway.update(
            UserProfileChanges(
                fullName = "Ada Lovelace",
                status = UserStatus.Away,
                statusText = "At the dentist",
            ),
        )

        val request = client.requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/v1/me", request.path)
        assertTrue(request.requiresAuth)
        assertTrue(request.effectiveIdempotency, "an absolute-value PATCH is safe to replay")

        val body = Json.parseToJsonElement(request.body!!.decodeToString()).jsonObject
        assertEquals(setOf("display_name", "presence", "presence_note"), body.keys)
        assertEquals(JsonPrimitive("Ada Lovelace"), body["display_name"])
        assertEquals(JsonPrimitive("away"), body["presence"])
        assertEquals(JsonPrimitive("At the dentist"), body["presence_note"])
        assertFalse("position" in body)
        assertFalse("avatar_id" in body)
    }

    @Test
    fun updateSpellsPositionAsALabelAndAvatarAsADigitString() = runTest {
        val (gateway, client) = gateway(
            """{"id":"u1","email":"ada@example.com","position":"Product owner","avatar_id":"7","presence":"active"}""",
        )

        gateway.update(
            UserProfileChanges(
                position = UserPosition.ProductOwner,
                avatarId = 7,
            ),
        )

        val body = Json.parseToJsonElement(client.requests.single().body!!.decodeToString()).jsonObject
        assertEquals(setOf("position", "avatar_id"), body.keys)
        assertEquals(JsonPrimitive("Product owner"), body["position"])
        assertEquals(JsonPrimitive("7"), body["avatar_id"])
    }

    @Test
    fun aTransportFailureIsReportedRatherThanSwallowed() = runTest {
        val failure = AppError.Transport("the connection dropped")
        val (gateway, _) = gateway("", failure)

        val thrown = assertFailsWith<AppError.Transport> { gateway.load() }
        assertEquals(failure, thrown)
    }
}

/**
 * A client that records the request and decodes the scripted response with the shared codec.
 *
 * Decoding through [JSONCoding] rather than returning a pre-built object is what keeps the DTO's
 * `@SerialName` list under test: a renamed key fails here the same way it would on a device.
 */
private class RecordingHttpClient(
    private val response: ByteArray,
    private val failure: AppError?,
) : HttpClient {

    val requests = mutableListOf<HttpRequest>()

    override suspend fun send(request: HttpRequest): ByteArray {
        requests += request
        failure?.let { throw it }
        return response
    }

    override suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T {
        requests += request
        failure?.let { throw it }
        return JSONCoding.json.decodeFromString(decoding, response.decodeToString())
    }

    override suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T) {
        error("the profile gateway never sends a typed body; it builds a JsonObject")
    }
}
