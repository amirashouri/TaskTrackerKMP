package com.tasktracker.featureprojectsdata

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.WorkItemID
import com.tasktracker.featureprojectsdomain.AgentRunStatus
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpRequest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class HttpAgentRunsRemoteTest {

    @Test
    fun dispatchPostsTheItemIdAndReadsTheRun() = runTest {
        val client = QueueHttpClient(listOf(RUN_ENVELOPE))
        val remote = HttpAgentRunsRemote(client)

        val run = remote.dispatch(WorkItemID(ITEM_ID))

        val request = client.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/agent/runs", request.path)
        assertTrue(request.requiresAuth)
        val body = JSONCoding.json.parseToJsonElement(request.body!!.decodeToString()).jsonObject
        assertEquals(ITEM_ID, body["workItemId"]!!.jsonPrimitive.content)

        assertEquals(AgentRunStatus.Running, run.status)
        assertEquals("0195c3a4-0000-7000-8000-0000000000aa", run.runnerID?.stringValue)
        assertEquals("Editing TT-12", run.summary)
        assertNull(run.finishedAt)
    }

    @Test
    fun theDetailCallCarriesTheCursorAndReturnsThePage() = runTest {
        val client = QueueHttpClient(listOf(DETAIL_ENVELOPE))
        val remote = HttpAgentRunsRemote(client)

        val page = remote.run(RUN_ID_OBJECT, afterSeq = 41)

        val request = client.requests.single()
        assertEquals("/v1/agent/runs/${RUN_ID_OBJECT.stringValue}", request.path)
        assertEquals("41", request.queryItems.single { it.name == "afterSeq" }.value)
        assertEquals(true, page.hasMore)
        assertEquals(1, page.events.size)
        assertEquals(42L, page.events.single().seq)
        assertEquals("tool", page.events.single().kind)
        assertEquals(AgentRunStatus.Running, page.run.status)
    }

    @Test
    fun theListCallAsksForTheItemAndLimit() = runTest {
        val client = QueueHttpClient(listOf("""{"runs":[$RUN_OBJECT]}"""))
        val remote = HttpAgentRunsRemote(client)

        val runs = remote.runs(WorkItemID(ITEM_ID), limit = 5)

        val request = client.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("$ITEM_ID", request.queryItems.single { it.name == "workItemId" }.value)
        assertEquals("5", request.queryItems.single { it.name == "limit" }.value)
        assertEquals(1, runs.size)
    }

    @Test
    fun cancelPostsToTheRunAndReadsTheUpdatedRow() = runTest {
        val client = QueueHttpClient(listOf(RUN_ENVELOPE))
        val remote = HttpAgentRunsRemote(client)

        remote.cancel(RUN_ID_OBJECT)

        val request = client.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/agent/runs/${RUN_ID_OBJECT.stringValue}/cancel", request.path)
    }

    /**
     * The DTOs must declare exactly what the server sends. A renamed key leaves the
     * old property missing, and with required members that is a decode failure — which
     * is what this pins, rather than a decode that quietly reads nothing.
     */
    @Test
    fun aRenamedServerKeyFailsToDecode() {
        JSONCoding.json.decodeFromString(
            HttpAgentRunsRemote.AgentRunDto.serializer(),
            RUN_OBJECT,
        )

        assertFailsWith<SerializationException> {
            JSONCoding.json.decodeFromString(
                HttpAgentRunsRemote.AgentRunDto.serializer(),
                RUN_OBJECT.replace("\"workItemId\"", "\"work_item_id\""),
            )
        }
        assertFailsWith<SerializationException> {
            JSONCoding.json.decodeFromString(
                HttpAgentRunsRemote.AgentRunDto.serializer(),
                RUN_OBJECT.replace("\"status\"", "\"runStatus\""),
            )
        }
    }

    /** A server that omits the nullable keys is the normal case for a queued run. */
    @Test
    fun theOptionalKeysMayBeOmitted() {
        val dto = JSONCoding.json.decodeFromString(
            HttpAgentRunsRemote.AgentRunDto.serializer(),
            MINIMAL_RUN_OBJECT,
        )

        assertNull(dto.runnerId)
        assertNull(dto.startedAt)
        assertNull(dto.finishedAt)
        assertEquals("", dto.summary)
        assertEquals("", dto.error)
        assertEquals(false, dto.cancelRequested)
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
            error("the agent remote never sends an encoded body")
        }
    }

    private companion object {
        const val RUN_ID = "0195c3a4-0000-7000-8000-000000000001"
        const val ITEM_ID = "0195c3a4-0000-7000-8000-000000000002"
        const val PROJECT_ID = "0195c3a4-0000-7000-8000-000000000003"
        const val USER_ID = "0195c3a4-0000-7000-8000-000000000004"

        val RUN_ID_OBJECT = com.tasktracker.core.AgentRunID(RUN_ID)

        val RUN_OBJECT = """
            {
              "id":"$RUN_ID",
              "workItemId":"$ITEM_ID",
              "projectId":"$PROJECT_ID",
              "status":"running",
              "requestedBy":"$USER_ID",
              "runnerId":"0195c3a4-0000-7000-8000-0000000000aa",
              "cancelRequested":false,
              "createdAt":"2026-11-03T12:00:00Z",
              "startedAt":"2026-11-03T12:00:07Z",
              "summary":"Editing TT-12",
              "error":""
            }
        """.trimIndent()

        val RUN_ENVELOPE = """{"run":$RUN_OBJECT}"""

        val MINIMAL_RUN_OBJECT = """
            {"id":"$RUN_ID","workItemId":"$ITEM_ID","projectId":"$PROJECT_ID",
             "status":"queued","requestedBy":"$USER_ID","createdAt":"2026-11-03T12:00:00Z"}
        """.trimIndent()

        val DETAIL_ENVELOPE = """
            {"run":$RUN_OBJECT,
             "events":[{"seq":42,"kind":"tool","message":"Editing TT-12","payload":"{}","createdAt":"2026-11-03T12:00:09Z"}],
             "hasMore":true}
        """.trimIndent()
    }
}
