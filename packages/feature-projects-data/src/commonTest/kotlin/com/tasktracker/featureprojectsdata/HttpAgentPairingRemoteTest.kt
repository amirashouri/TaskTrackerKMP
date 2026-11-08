package com.tasktracker.featureprojectsdata

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.ProjectID
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HttpAgentPairingRemoteTest {

    @Test
    fun requestingACodePostsTheProjectAndReadsTheExpiry() = runTest {
        val client = QueueHttpClient(
            """{"code":"ttpc_abc","projectId":"$PROJECT_ID","expiresAt":"2026-11-03T12:10:00Z"}""",
        )
        val remote = HttpAgentPairingRemote(client)

        val code = remote.createPairingCode(ProjectID(PROJECT_ID))

        val request = client.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/agent/runners/pairing-codes", request.path)
        assertEquals(true, request.requiresAuth)
        val body = JSONCoding.json.parseToJsonElement(request.body!!.decodeToString()).jsonObject
        assertEquals(PROJECT_ID, body["projectId"]!!.jsonPrimitive.content)

        assertEquals("ttpc_abc", code.code)
        assertEquals(ProjectID(PROJECT_ID), code.projectID)
        assertEquals(Instant.parse("2026-11-03T12:10:00Z"), code.expiresAt)
    }

    @Test
    fun theRosterKeepsOnlyThisProjectsLiveMachines() = runTest {
        val client = QueueHttpClient(
            """{"runners":[""" +
                """{"id":"0195c3a4-0000-7000-8000-0000000000aa","projectId":"$PROJECT_ID","name":"dev-machine","provider":"openai-compatible","modelLabel":"llama3.1","lastSeenAt":"2026-11-03T12:00:00Z","pausedAt":null,"revokedAt":null,"createdAt":"2026-11-01T09:00:00Z"},""" +
                """{"id":"0195c3a4-0000-7000-8000-0000000000bb","projectId":"0195c3a4-0000-7000-8000-000000000099","name":"other","provider":"","modelLabel":"","lastSeenAt":null,"pausedAt":null,"revokedAt":null,"createdAt":"2026-11-01T09:00:00Z"},""" +
                """{"id":"0195c3a4-0000-7000-8000-0000000000cc","projectId":"$PROJECT_ID","name":"retired","provider":"","modelLabel":"","lastSeenAt":null,"pausedAt":null,"revokedAt":"2026-11-02T09:00:00Z","createdAt":"2026-11-01T09:00:00Z"}""" +
                """]}""",
        )
        val remote = HttpAgentPairingRemote(client)

        val machines = remote.machines(ProjectID(PROJECT_ID))

        val request = client.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/agent/runners", request.path)
        assertEquals(1, machines.size, "another project's runner and a revoked one are not this roster")
        assertEquals("dev-machine", machines.single().name)
        assertEquals("openai-compatible · llama3.1", machines.single().modelSummary)
        assertEquals(Instant.parse("2026-11-03T12:00:00Z"), machines.single().lastSeenAt)
    }

    private class QueueHttpClient(private val body: String) : HttpClient {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun send(request: HttpRequest): ByteArray {
            requests += request
            error("the pairing remote always decodes a response")
        }

        override suspend fun <T> send(request: HttpRequest, decoding: KSerializer<T>): T {
            requests += request
            return JSONCoding.json.decodeFromString(decoding, body)
        }

        override suspend fun <T> send(request: HttpRequest, encoding: KSerializer<T>, body: T) {
            error("the pairing remote never sends an encoded body")
        }
    }

    private companion object {
        const val PROJECT_ID = "0195c3a4-0000-7000-8000-000000000003"
    }
}
