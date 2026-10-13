package com.tasktracker.sync

import com.tasktracker.core.AppError
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.database.OutboxStatus
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The wire contract: what shape each endpoint has, and what each server status means. */
class HttpSyncTransportTest {

    private fun entry(
        id: String = "change-1",
        entity: OutboxEntity = OutboxEntity.WorkItem,
        operation: OutboxOperation = OutboxOperation.Upsert,
    ) = newEntry(id, entity = entity, operation = operation).let {
        com.tasktracker.database.OutboxEntry(
            id = it.id,
            entity = it.entity,
            entityID = it.entityID,
            operation = it.operation,
            payload = it.payload,
            userID = it.userID,
            createdAt = it.createdAt,
            status = OutboxStatus.InFlight,
            attemptCount = 1,
            lastError = null,
        )
    }

    private fun body(json: String) = json.encodeToByteArray()

    // MARK: - Push

    @Test
    fun `a push is a POST to the versioned sync path and marked replayable`() = runTest {
        val client = StubHttpClient(body("""{"results":[{"id":"change-1","status":"accepted"}]}"""))
        val transport = HttpSyncTransport(client)

        transport.push(listOf(entry()))

        val request = client.requests.first()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/v1/sync/push", request.path)
        // Safe to replay only because each change carries a stable id the server can dedupe on;
        // without this the client's own retry could apply a rename twice.
        assertTrue(request.effectiveIdempotency)
    }

    @Test
    fun `the push body carries the change id and entity and payload`() = runTest {
        val client = StubHttpClient(body("""{"results":[]}"""))
        val transport = HttpSyncTransport(client)

        transport.push(listOf(entry(entity = OutboxEntity.Project, operation = OutboxOperation.Delete)))

        val change = Json.parseToJsonElement(client.requests.first().body!!.decodeToString())
            .jsonObject["changes"]!!.jsonArray.first().jsonObject
        assertEquals("change-1", change["id"]!!.jsonPrimitive.content)
        assertEquals("project", change["entity"]!!.jsonPrimitive.content)
        assertEquals("delete", change["operation"]!!.jsonPrimitive.content)
        // The server's spelling, not Kotlin's. Go decodes JSON keys case-insensitively, so the
        // push direction worked either way — which is exactly why the mismatch survived until
        // the pull direction was checked, and only that direction decodes exactly.
        assertEquals("change-1", change["entityId"]!!.jsonPrimitive.content)
        assertNull(change["entityID"])
        // Payloads go over the wire as readable JSON, not base64: correct either way, but
        // unreadable in a server log is a cost paid by whoever debugs it at 2am.
        assertEquals("""{"title":"task"}""", change["payload"]!!.jsonPrimitive.content)
    }

    @Test
    fun `server verdicts map onto the engine's outcomes`() = runTest {
        val client = StubHttpClient(
            body(
                """
                {"results":[
                  {"id":"a","status":"accepted"},
                  {"id":"b","status":"conflict","version":"v9"},
                  {"id":"c","status":"rejected","message":"title is required"},
                  {"id":"d","status":"retry_later","message":"503"}
                ]}
                """.trimIndent(),
            ),
        )
        val transport = HttpSyncTransport(client)

        val results = transport.push(listOf("a", "b", "c", "d").map { entry(it) })

        assertEquals(4, results.size)
        assertEquals(PushOutcome.Accepted, results.first { it.entryID == "a" }.outcome)
        assertEquals(PushOutcome.Conflict("v9"), results.first { it.entryID == "b" }.outcome)
        assertEquals(PushOutcome.Rejected("title is required"), results.first { it.entryID == "c" }.outcome)
        assertEquals(PushOutcome.RetryLater("503"), results.first { it.entryID == "d" }.outcome)
    }

    @Test
    fun `a status this build does not understand is retried never assumed to have landed`() = runTest {
        // Guessing "accepted" would drop a change against a newer server; guessing "rejected"
        // would dead-letter it. Retrying is the only safe reading of nonsense.
        val client = StubHttpClient(body("""{"results":[{"id":"change-1","status":"teleported"}]}"""))
        val transport = HttpSyncTransport(client)

        val results = transport.push(listOf(entry()))

        assertTrue(results.first().outcome is PushOutcome.RetryLater)
    }

    @Test
    fun `a status with no message still explains itself`() = runTest {
        val client = StubHttpClient(body("""{"results":[{"id":"change-1","status":"rejected"}]}"""))
        val transport = HttpSyncTransport(client)

        val outcome = transport.push(listOf(entry())).first().outcome

        assertEquals(PushOutcome.Rejected("The server reported rejected."), outcome)
    }

    @Test
    fun `pushing nothing does not call the server`() = runTest {
        val client = StubHttpClient()
        val transport = HttpSyncTransport(client)

        val results = transport.push(emptyList())

        assertTrue(results.isEmpty())
        assertTrue(client.requests.isEmpty())
    }

    // MARK: - Pull

    @Test
    fun `a pull is a GET on the scope's path with the cursor and limit`() = runTest {
        val client = StubHttpClient(body("""{"changes":[],"hasMore":false}"""))
        val transport = HttpSyncTransport(client)

        transport.pull(SyncScope.WorkItems, cursor = "c7", limit = 50)

        val request = client.requests.first()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/v1/sync/changes/workItems", request.path)
        assertTrue(request.queryItems.contains(HttpQueryItem("cursor", "c7")))
        assertTrue(request.queryItems.contains(HttpQueryItem("limit", "50")))
    }

    @Test
    fun `a first pull sends no cursor at all rather than an empty one`() = runTest {
        val client = StubHttpClient(body("""{"changes":[],"hasMore":false}"""))
        val transport = HttpSyncTransport(client)

        transport.pull(SyncScope.Projects, cursor = null, limit = 10)

        val request = client.requests.first()
        assertTrue(request.queryItems.none { it.name == "cursor" })
        assertEquals("/v1/sync/changes/projects", request.path)
    }

    @Test
    fun `a change page is decoded and the cursor is passed through uninterpreted`() = runTest {
        val client = StubHttpClient(
            body(
                """
                {"changes":[{"entity":"workItem","entityId":"task-9","operation":"upsert",
                "payload":"{\"title\":\"from server\"}","version":"v3"}],
                "nextCursor":"c2","hasMore":true}
                """.trimIndent(),
            ),
        )
        val transport = HttpSyncTransport(client)

        val page = transport.pull(SyncScope.WorkItems, cursor = "c1", limit = 10)

        assertEquals(1, page.changes.size)
        val change = page.changes.first()
        assertEquals(OutboxEntity.WorkItem, change.entity)
        assertEquals("task-9", change.entityID)
        assertEquals(OutboxOperation.Upsert, change.operation)
        assertEquals("v3", change.version)
        assertEquals("""{"title":"from server"}""", change.payload.decodeToString())
        // The cursor is the server's business, not ours.
        assertEquals("c2", page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun `a change naming an entity this build does not know is a decoding error`() = runTest {
        // `milestone`, not `sprint`: the point is a name no `OutboxEntity` carries, and
        // `sprint` stopped being one when the sprint work added it.
        val client = StubHttpClient(
            body(
                """{"changes":[{"entity":"milestone","entityId":"m-1","operation":"upsert","payload":"{}"}],"hasMore":false}""",
            ),
        )
        val transport = HttpSyncTransport(client)

        val failure = assertFailsWith<AppError.Decoding> {
            transport.pull(SyncScope.WorkItems, cursor = null, limit = 10)
        }
        assertTrue(failure.detail.contains("milestone"))
    }

    @Test
    fun `a change naming an operation this build does not know is a decoding error`() = runTest {
        val client = StubHttpClient(
            body("""{"changes":[{"entity":"workItem","entityId":"t-1","operation":"teleport","payload":"{}"}],"hasMore":false}"""),
        )
        val transport = HttpSyncTransport(client)

        assertFailsWith<AppError.Decoding> {
            transport.pull(SyncScope.WorkItems, cursor = null, limit = 10)
        }
    }

    @Test
    fun `a page that does not say whether the log continues is a decoding error`() = runTest {
        // Guessing `hasMore = false` would end the pull with pages still unread, silently.
        val client = StubHttpClient(body("""{"changes":[]}"""))
        val transport = HttpSyncTransport(client)

        assertFailsWith<AppError.Decoding> {
            transport.pull(SyncScope.WorkItems, cursor = null, limit = 10)
        }
    }

    @Test
    fun `a page asking for a resync says so rather than failing to decode`() = runTest {
        // The server sends this with `omitempty`, so it only ever arrives when it is true —
        // and it arrives on exactly the page the client must not fall over on. Leaving the
        // field undeclared turned "your cursor is too old" into a decoding error.
        val client = StubHttpClient(
            body("""{"changes":[],"hasMore":true,"resyncRequired":true}"""),
        )
        val transport = HttpSyncTransport(client)

        val page = transport.pull(SyncScope.WorkItems, cursor = "stale", limit = 10)

        assertTrue(page.resyncRequired)
    }

    @Test
    fun `a page that says nothing about a resync is not one`() = runTest {
        // Absent means false: the field is omitted when it is false, and a server that
        // predates it is saying the same thing.
        val client = StubHttpClient(body("""{"changes":[],"hasMore":false}"""))
        val transport = HttpSyncTransport(client)

        val page = transport.pull(SyncScope.WorkItems, cursor = null, limit = 10)

        assertTrue(!page.resyncRequired)
    }

    @Test
    fun `a transport error reaches the engine as an AppError`() = runTest {
        val client = StubHttpClient(error = AppError.Unauthorized)
        val transport = HttpSyncTransport(client)

        assertFailsWith<AppError.Unauthorized> { transport.push(listOf(entry())) }
    }

    @Test
    fun `an unparseable reply is a decoding error and not a crash`() = runTest {
        val client = StubHttpClient(body("""{"results":"not a list"}"""))
        val transport = HttpSyncTransport(client)

        assertFailsWith<AppError.Decoding> { transport.push(listOf(entry())) }
    }

    @Test
    fun `a page with no changes still carries the cursor the server sent`() = runTest {
        // A server that has nothing new can still have moved its cursor, and a pull that
        // dropped that would re-read the same empty page for ever.
        val client = StubHttpClient(body("""{"changes":[],"nextCursor":"c9","hasMore":false}"""))
        val transport = HttpSyncTransport(client)

        val page = transport.pull(SyncScope.Projects, cursor = "c8", limit = 10)

        assertTrue(page.changes.isEmpty())
        assertEquals("c9", page.nextCursor)
        assertNull(page.changes.firstOrNull())
    }
}

/** The stand-in server that ships in `main`, so a simulator can run a whole cycle offline. */
class InMemorySyncTransportTest {

    @Test
    fun `a push is accepted and the next pull brings the same change back`() = runTest {
        val transport = InMemorySyncTransport()

        val results = transport.push(listOf(newEntry("change-1").let { it.toEntryForTest() }))
        val page = transport.pull(SyncScope.WorkItems, cursor = null, limit = 10)

        assertEquals(listOf("change-1"), results.map { it.entryID })
        assertEquals(PushOutcome.Accepted, results.first().outcome)
        // A round trip, not a one-way trip: what this device pushed comes back down the
        // change log, which is what makes a whole cycle observable without a server.
        assertEquals(1, page.changes.size)
        assertEquals("change-1", page.changes.first().entityID)
        assertEquals("1", page.nextCursor)
        assertTrue(!page.hasMore)
    }

    @Test
    fun `a change is filed on the stream its entity belongs to`() = runTest {
        val transport = InMemorySyncTransport()

        transport.push(
            listOf(
                newEntry("p-1", entity = OutboxEntity.Project).toEntryForTest(),
                newEntry("w-1", entity = OutboxEntity.WorkItem).toEntryForTest(),
            ),
        )

        assertEquals(1, transport.logSize(SyncScope.Projects))
        assertEquals(1, transport.logSize(SyncScope.WorkItems))
    }

    @Test
    fun `a rejected change does not enter the log`() = runTest {
        val transport = InMemorySyncTransport(
            scripted = mapOf("change-1" to PushOutcome.Rejected("nope")),
        )

        val results = transport.push(listOf(newEntry("change-1").toEntryForTest()))

        assertEquals(PushOutcome.Rejected("nope"), results.first().outcome)
        assertEquals(0, transport.logSize(SyncScope.WorkItems))
    }

    @Test
    fun `a conflict is still filed because the server won with its own version`() = runTest {
        val transport = InMemorySyncTransport(conflicts = true)

        val results = transport.push(listOf(newEntry("change-1").toEntryForTest()))

        assertEquals(PushOutcome.Conflict("in-memory-1"), results.first().outcome)
        assertEquals(1, transport.logSize(SyncScope.WorkItems))
    }

    @Test
    fun `a seeded log pages and hands out a cursor per page`() = runTest {
        val transport = InMemorySyncTransport()
        transport.seed(
            SyncScope.WorkItems,
            listOf(remoteChange("task-1"), remoteChange("task-2"), remoteChange("task-3")),
        )

        val first = transport.pull(SyncScope.WorkItems, cursor = null, limit = 2)
        val second = transport.pull(SyncScope.WorkItems, cursor = first.nextCursor, limit = 2)

        assertEquals(listOf("task-1", "task-2"), first.changes.map { it.entityID })
        assertEquals("2", first.nextCursor)
        assertTrue(first.hasMore)
        assertEquals(listOf("task-3"), second.changes.map { it.entityID })
        assertTrue(!second.hasMore)
    }

    @Test
    fun `a cursor at the end returns an empty page rather than failing`() = runTest {
        val transport = InMemorySyncTransport()

        assertEquals(PullPage.empty, transport.pull(SyncScope.Projects, cursor = "99", limit = 10))
    }

    @Test
    fun `push batches are recorded so a test can see batch boundaries`() = runTest {
        val transport = InMemorySyncTransport()

        transport.push(listOf(newEntry("a").toEntryForTest()))
        transport.push(
            listOf(newEntry("b").toEntryForTest(), newEntry("c").toEntryForTest()),
        )

        assertEquals(listOf(listOf("a"), listOf("b", "c")), transport.batches)
        assertEquals(listOf("a", "b", "c"), transport.pushed)
    }
}

/** The engine-facing view of a queued change, as the store would hand it over mid-push. */
private fun com.tasktracker.database.NewOutboxEntry.toEntryForTest() =
    com.tasktracker.database.OutboxEntry(
        id = id,
        entity = entity,
        entityID = entityID,
        operation = operation,
        payload = payload,
        userID = userID,
        createdAt = createdAt,
        status = OutboxStatus.InFlight,
        attemptCount = 1,
        lastError = null,
    )
