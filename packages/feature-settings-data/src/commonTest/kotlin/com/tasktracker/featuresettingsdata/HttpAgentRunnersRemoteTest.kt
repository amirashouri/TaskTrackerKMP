package com.tasktracker.featuresettingsdata

import com.tasktracker.core.AgentRunnerID
import com.tasktracker.core.JSONCoding
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpAgentRunnersRemoteTest {

    @Test
    fun theListReadsTheAccountsMachinesAndTheirPauseState() = runTest {
        val client = QueueHttpClient(listOf(RUNNERS_ENVELOPE))
        val remote = HttpAgentRunnersRemote(client)

        val machines = remote.loadMachines()

        val request = client.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/agent/runners", request.path)
        assertTrue(request.requiresAuth)

        assertEquals(2, machines.size)
        val running = machines.first()
        assertEquals(MACHINE_ID, running.id.stringValue)
        assertEquals("dev-machine", running.name)
        assertEquals("openai-compatible · llama3.1", running.modelSummary)
        assertEquals(AgentHost.Desktop, running.host)
        assertEquals("2026-11-03T12:00:00Z", running.lastSeenAt.toString())
        assertNull(running.pausedAt)
        assertTrue(running.isLive)
        assertTrue(machines.last().isPaused)
        assertTrue(machines.last().revokedAt != null)
    }

    @Test
    fun stopAndStartPostToTheTwoPathsAndReadTheConfirmedRow() = runTest {
        val client = QueueHttpClient(listOf(PAUSED_ENVELOPE, RUNNING_ENVELOPE))
        val remote = HttpAgentRunnersRemote(client)
        val id = AgentRunnerID(MACHINE_ID)

        val stopped = remote.setPaused(id, paused = true)
        val started = remote.setPaused(id, paused = false)

        assertEquals("/v1/agent/runners/$MACHINE_ID/stop", client.requests[0].path)
        assertEquals("/v1/agent/runners/$MACHINE_ID/start", client.requests[1].path)
        assertEquals(HttpMethod.Post, client.requests[0].method)
        assertTrue(stopped.isPaused)
        assertTrue(!started.isPaused)
    }

    /** The server omits an empty provider, and every optional key needs a default. */
    @Test
    fun theOptionalKeysMayBeOmitted() = runTest {
        val client = QueueHttpClient(listOf(MINIMAL_RUNNERS_ENVELOPE))
        val machine = HttpAgentRunnersRemote(client).loadMachines().single()

        assertEquals("", machine.provider)
        assertEquals("", machine.modelLabel)
        assertEquals("", machine.modelSummary)
        assertNull(machine.lastSeenAt)
        assertNull(machine.pausedAt)
        assertNull(machine.revokedAt)
        assertNull(machine.host)
    }

    /**
     * The client declares the exact keys it reads. A rename must fail the decode
     * rather than silently leave a machine nameless, and `createdAt` is required
     * even though this layer does not model it — its presence is what the server
     * contract guarantees.
     */
    @Test
    fun aRenamedOrMissingRequiredKeyFailsToDecode() {
        JSONCoding.json.decodeFromString(
            HttpAgentRunnersRemote.RunnerDto.serializer(),
            MINIMAL_RUNNER_OBJECT,
        )

        assertFailsWith<SerializationException> {
            JSONCoding.json.decodeFromString(
                HttpAgentRunnersRemote.RunnerDto.serializer(),
                MINIMAL_RUNNER_OBJECT.replace("\"name\"", "\"runner_name\""),
            )
        }
        assertFailsWith<SerializationException> {
            JSONCoding.json.decodeFromString(
                HttpAgentRunnersRemote.RunnerDto.serializer(),
                MINIMAL_RUNNER_OBJECT.replace(",\"createdAt\":\"2026-11-03T12:00:00Z\"", ""),
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
            error("the agent settings remote never sends an encoded body")
        }
    }

    private companion object {
        const val MACHINE_ID = "0195c3a4-0000-7000-8000-0000000000aa"

        const val MINIMAL_RUNNER_OBJECT =
            """{"id":"$MACHINE_ID","name":"dev-machine","createdAt":"2026-11-03T12:00:00Z"}"""

        const val MINIMAL_RUNNERS_ENVELOPE = """{"runners":[$MINIMAL_RUNNER_OBJECT]}"""

        val RUNNING_OBJECT = """
            {"id":"$MACHINE_ID","name":"dev-machine","provider":"openai-compatible",
             "modelLabel":"llama3.1","host":"desktop","lastSeenAt":"2026-11-03T12:00:00Z",
             "createdAt":"2026-11-03T11:00:00Z"}
        """.trimIndent()

        val PAUSED_OBJECT = """
            {"id":"$MACHINE_ID","name":"dev-machine","provider":"openai-compatible",
             "modelLabel":"llama3.1","lastSeenAt":"2026-11-03T12:00:00Z",
             "pausedAt":"2026-11-03T12:05:00Z","createdAt":"2026-11-03T11:00:00Z"}
        """.trimIndent()

        val REVOKED_OBJECT = """
            {"id":"0195c3a4-0000-7000-8000-0000000000bb","name":"old-machine",
             "pausedAt":"2026-11-03T12:05:00Z","revokedAt":"2026-11-03T12:06:00Z",
             "createdAt":"2026-11-03T11:00:00Z"}
        """.trimIndent()

        val RUNNING_ENVELOPE = """{"runner":$RUNNING_OBJECT}"""
        val PAUSED_ENVELOPE = """{"runner":$PAUSED_OBJECT}"""
        val RUNNERS_ENVELOPE = """{"runners":[$RUNNING_OBJECT,$REVOKED_OBJECT]}"""
    }
}
