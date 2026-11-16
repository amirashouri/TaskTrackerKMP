package com.tasktracker.featuresettingsdata

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.featuresettingsdomain.AgentHost
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpAgentSetupRemoteTest {

    /**
     * The mint is a `POST` with the project and the host in the body.
     *
     * Both halves matter: the route is project-scoped, so a body without `projectId` is a
     * request the server refuses, and `host` is what the Settings roster later labels the row
     * with. Asserting the exact JSON keys is the same contract check the runner remote's tests
     * make — the keys are the server's, not this layer's.
     */
    @Test
    fun theMintPostsTheProjectAndHostAndReadsTheCode() = runTest {
        val client = QueueHttpClient(listOf(CODE_JSON))
        val remote = HttpAgentSetupRemote(client)

        val code = remote.createCode(ProjectID(PROJECT_ID), AgentHost.Remote)

        val request = client.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/agent/runners/pairing-codes", request.path)
        assertTrue(request.requiresAuth, "the route is scoped to the signed-in account")

        val body = request.body?.decodeToString().orEmpty()
        assertTrue(body.contains("\"projectId\":\"$PROJECT_ID\""), "the body must name the project: $body")
        assertTrue(body.contains("\"host\":\"remote\""), "the body must carry the host: $body")

        assertEquals("ttpc_abc", code.code)
        assertEquals(PROJECT_ID, code.projectID.stringValue)
        assertEquals("2026-11-03T12:10:00Z", code.expiresAt.toString())
    }

    /**
     * The code arrives once and is the only thing that authorises a redemption: a renamed
     * `code` key must fail the decode rather than leave the sheet showing an empty string that
     * suggests pairing succeeded.
     */
    @Test
    fun aRenamedRequiredKeyFailsToDecode() {
        JSONCoding.json.decodeFromString(
            HttpAgentSetupRemote.PairingCodeDto.serializer(),
            CODE_JSON,
        )

        assertFailsWith<SerializationException> {
            JSONCoding.json.decodeFromString(
                HttpAgentSetupRemote.PairingCodeDto.serializer(),
                CODE_JSON.replace("\"code\"", "\"pairingCode\""),
            )
        }
    }

    private class QueueHttpClient(bodies: List<String>) : HttpClient {
        private val bodies = bodies.toMutableList()
        val requests = mutableListOf<HttpRequest>()

        override suspend fun send(request: HttpRequest): ByteArray {
            requests += request
            return bodies.removeAt(0).encodeToByteArray()
        }

        override suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T {
            requests += request
            return JSONCoding.json.decodeFromString(decoding, bodies.removeAt(0))
        }

        override suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T) {
            error("the setup remote sends its body on the request, not through an encoded call")
        }
    }

    private companion object {
        const val PROJECT_ID = "0195c3a4-0000-7000-8000-0000000000bb"

        const val CODE_JSON =
            """{"code":"ttpc_abc","projectId":"$PROJECT_ID","expiresAt":"2026-11-03T12:10:00Z"}"""
    }
}
