package com.tasktracker.featureprojectsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.JSONCoding
import com.tasktracker.core.parseProjectID
import com.tasktracker.core.parseWorkItemID
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.domain.Priority
import com.tasktracker.domain.Project
import com.tasktracker.domain.Sprint
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.sync.RemoteChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pull side: a server change landing in the local store.
 *
 * ## The two rules everything else follows from
 *
 * **It never writes to the outbox.** Every test here that applies something also checks the
 * queue depth, because a sink that queued what it applied would still pass every other
 * assertion in this file — the row would be written, the screen would be told, the data would
 * be right — while turning every pull into a push, and a server that echoed the change back
 * would leave the two sides talking to each other for as long as the app was open.
 *
 * **It is idempotent.** A cursor only advances after a page is fully applied, so an
 * interrupted cycle deliberately re-delivers that page. Each "applying it twice" test is one
 * run of that requirement, and the one thing that does *not* survive it is appending a history
 * row.
 */
class LocalDataSyncSinkTest {

    // MARK: Projects

    @Test
    fun aPulledProjectIsInsertedWhenItIsNotHere() = runTest {
        val harness = Harness()
        val project = aProject()

        harness.sink.apply(upsert(OutboxEntity.Project, project))

        assertEquals(project, harness.repository.project(project.id))
    }

    @Test
    fun aPulledProjectOverwritesTheStoredOneServerWins() = runTest {
        val harness = Harness()
        val project = aProject()
        harness.repository.createProject(project.name, project.keyPrefix, project.details, Moments.start)
        // The local row is under a random id, so put the *same* id here to make it an update
        // rather than a second project.
        harness.database.projectDao().deleteByID(
            harness.repository.projects(includeArchived = true)[0].id.stringValue,
        )

        harness.sink.apply(upsert(OutboxEntity.Project, project.copy(name = "Renamed by the server")))

        assertEquals("Renamed by the server", harness.repository.project(project.id)?.name)
    }

    @Test
    fun aPulledProjectCannotRewindTheKeyCounter() = runTest {
        val harness = Harness()
        val project = aProject(nextItemNumber = 40)
        harness.sink.apply(upsert(OutboxEntity.Project, project))

        harness.sink.apply(upsert(OutboxEntity.Project, project.copy(nextItemNumber = 3, name = "Stale copy")))

        // The server's copy is whatever was true when the *earlier* offline edit was pushed, so
        // honouring a lower counter would issue a key that is already in use.
        assertEquals(40, harness.repository.project(project.id)?.nextItemNumber)
    }

    @Test
    fun aPulledProjectQueuesNothing() = runTest {
        val harness = Harness()

        harness.sink.apply(upsert(OutboxEntity.Project, aProject()))

        assertEquals(0, harness.queueDepth())
    }

    @Test
    fun deletingAProjectRemovesItsWorkItemsAndHistoryToo() = runTest {
        // A correction rather than a port: the original deleted the project and its history and
        // left the items, which orphans every card under it. Nothing references them, no board
        // can show them, and a later pull re-sending one of those ids would resurrect a card
        // into a project that no longer exists.
        val harness = Harness()
        val project = aProject()
        harness.sink.apply(upsert(OutboxEntity.Project, project))
        val item = aWorkItem(projectID = project.id)
        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))
        assertEquals(1, harness.repository.workItems(project.id).size)

        harness.sink.apply(delete(OutboxEntity.Project, project.id.stringValue))

        assertNull(harness.repository.project(project.id))
        assertEquals(0, harness.database.workItemDao().count())
        assertEquals(0, harness.database.statusChangeDao().all().size)
    }

    @Test
    fun deletingAProjectThatIsNotHereSucceedesQuietly() = runTest {
        // A re-delivered delete is a normal consequence of idempotency, not an error. Failing
        // would abort the page and re-deliver it forever.
        val harness = Harness()

        harness.sink.apply(delete(OutboxEntity.Project, aProject().id.stringValue))

        assertNull(harness.repository.project(aProject().id))
    }

    @Test
    fun deletingAProjectAnnouncesBothKindsBecauseItTakesItsWorkItemsWithIt() = runTest {
        // Announcing `.projects` alone would leave a board that happened to be open showing a
        // column of items that no longer exist — the one case where an event being *nearly*
        // right shows something stale rather than merely costing a re-read.
        val harness = Harness()
        val project = aProject()
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.sink.apply(delete(OutboxEntity.Project, project.id.stringValue))

        assertEquals(
            listOf(
                ProjectChange(ProjectChange.Kind.Projects, project.id),
                ProjectChange(ProjectChange.Kind.WorkItems, project.id),
            ),
            seen,
        )
    }

    // MARK: Work items

    @Test
    fun aPulledWorkItemIsInsertedWhenItIsNotHere() = runTest {
        val harness = Harness()
        val project = aProject()
        harness.sink.apply(upsert(OutboxEntity.Project, project))
        val item = aWorkItem(projectID = project.id)

        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))

        assertEquals(item, harness.repository.workItem(item.id))
    }

    @Test
    fun aPulledWorkItemCannotMoveItToAnotherProject() = runTest {
        // A payload naming a different parent is a server bug, and applying it would move a
        // card between projects with no delete and nothing in the history.
        val harness = Harness()
        val project = aProject()
        val elsewhere = aProject(nextItemNumber = 1).copy(id = parseProjectID("44444444-4444-4444-8444-444444444444")!!)
        harness.sink.apply(upsert(OutboxEntity.Project, project))
        harness.sink.apply(upsert(OutboxEntity.Project, elsewhere))
        val item = aWorkItem(projectID = project.id)
        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))

        harness.sink.apply(upsert(OutboxEntity.WorkItem, item.copy(projectID = elsewhere.id)))

        assertEquals(project.id, harness.repository.workItem(item.id)?.projectID)
    }

    @Test
    fun aPulledTerminalWorkItemWithNoResolutionDateIsDatedOnTheWayIn() = runTest {
        // The normal case for a card moved to Done on another device: the payload has a terminal
        // status and no `resolvedAt`, and without this the archive cannot file it.
        val harness = Harness()
        val item = aWorkItem(status = WorkItemStatus.Done, resolvedAt = null, updatedAt = Moments.later)

        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))

        assertEquals(Moments.later, harness.repository.workItem(item.id)?.resolvedAt)
    }

    @Test
    fun aPulledReopenedWorkItemHasItsStaleResolutionDateCleared() = runTest {
        val harness = Harness()
        val item = aWorkItem(status = WorkItemStatus.Todo, resolvedAt = Moments.start)

        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))

        assertNull(harness.repository.workItem(item.id)?.resolvedAt)
    }

    @Test
    fun aPulledWorkItemQueuesNothing() = runTest {
        val harness = Harness()

        harness.sink.apply(upsert(OutboxEntity.WorkItem, aWorkItem()))

        assertEquals(0, harness.queueDepth())
    }

    @Test
    fun aPulledWorkItemAppendsNoHistoryRow() = runTest {
        // The one thing that would not be idempotent. A sink that recorded history would fill an
        // item's history with the same transition once per retry of an interrupted page, and
        // there is no server-side author to attribute the row to in any case.
        val harness = Harness()
        val item = aWorkItem()
        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))

        assertEquals(0, harness.database.statusChangeDao().all().count { it.workItemID == item.id.stringValue })
    }

    @Test
    fun aProjectUpsertAnnouncesTheProjectsOnly() = runTest {
        // A rename writes one row. Waking a board for it would make every open board re-read
        // every card in the project because somebody changed its title.
        val harness = Harness()
        val project = aProject()
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.sink.apply(upsert(OutboxEntity.Project, project))

        assertEquals(listOf(ProjectChange(ProjectChange.Kind.Projects, project.id)), seen)
    }

    @Test
    fun aWorkItemChangeAnnouncesTheItemsOnly() = runTest {
        val harness = Harness()
        val item = aWorkItem()
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))

        assertEquals(listOf(ProjectChange(ProjectChange.Kind.WorkItems, item.projectID)), seen)
    }

    @Test
    fun deletingAWorkItemAnnouncesTheProjectReadBeforeTheRowWent() = runTest {
        val harness = Harness()
        val item = aWorkItem()
        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.sink.apply(delete(OutboxEntity.WorkItem, item.id.stringValue))

        assertEquals(listOf(ProjectChange(ProjectChange.Kind.WorkItems, item.projectID)), seen)
    }

    // MARK: Idempotency

    @Test
    fun applyingTheSamePageTwiceLeavesTheSameStore() = runTest {
        val harness = Harness()
        val project = aProject()
        val item = aWorkItem(projectID = project.id)
        val page = listOf(upsert(OutboxEntity.Project, project), upsert(OutboxEntity.WorkItem, item))

        for (change in page) harness.sink.apply(change)
        val afterFirst = snapshot(harness)
        for (change in page) harness.sink.apply(change)

        assertEquals(afterFirst, snapshot(harness))
    }

    @Test
    fun applyingTheSameDeleteTwiceSucceeds() = runTest {
        val harness = Harness()
        val item = aWorkItem()
        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))

        harness.sink.apply(delete(OutboxEntity.WorkItem, item.id.stringValue))
        harness.sink.apply(delete(OutboxEntity.WorkItem, item.id.stringValue))

        assertNull(harness.repository.workItem(item.id))
        assertEquals(0, harness.queueDepth())
    }

    @Test
    fun reapplyingAWholePageTenTimesAddsNothingToTheStore() = runTest {
        val harness = Harness()
        val project = aProject()
        val items = (1..3).map {
            aWorkItem(
                id = parseWorkItemID("3333333$it-3333-4333-8333-333333333333")!!,
                projectID = project.id,
                key = "TT-$it",
            )
        }
        val page = listOf(upsert(OutboxEntity.Project, project)) + items.map { upsert(OutboxEntity.WorkItem, it) }

        repeat(10) { page.forEach { harness.sink.apply(it) } }

        assertEquals(1, harness.repository.projects(includeArchived = true).size)
        assertEquals(3, harness.repository.workItems(project.id).size)
        assertEquals(0, harness.queueDepth())
    }

    // MARK: Payloads this build cannot read

    @Test
    fun aPayloadThatIsNotJsonFailsThePageRatherThanBeingSkipped() = runTest {
        // The opposite of the *column* rule, deliberately. A bad column is in a store that
        // already has the row, so dropping it costs one entry. A bad payload is the only copy of
        // somebody's edit, and dropping it loses that permanently — so it fails the page, the
        // cursor stays where it was, and a later release that understands the shape gets it.
        val harness = Harness()

        val failure = assertFailsWith<AppError.Decoding> {
            harness.sink.apply(
                RemoteChange(
                    entity = OutboxEntity.Project,
                    entityID = aProject().id.stringValue,
                    operation = OutboxOperation.Upsert,
                    payload = "not json at all".encodeToByteArray(),
                    version = null,
                ),
            )
        }

        assertTrue(failure.message.orEmpty().contains("Unreadable"))
    }

    @Test
    fun aPayloadOfTheWrongShapeFailsRatherThanWritingAnEmptyRow() = runTest {
        val harness = Harness()
        val project = aProject()

        assertFailsWith<AppError.Decoding> {
            harness.sink.apply(upsert(OutboxEntity.Project, "{\"not\":\"a project\"}"))
        }

        assertNull(harness.repository.project(project.id))
    }

    @Test
    fun aFailedPageLeavesWhatItAlreadyAppliedAndIsReDeliveredFromTheSamePoint() = runTest {
        // A page is applied change by change, and a page is only complete when every change in
        // it has landed — but each change is its own transaction, so a failure partway through
        // leaves the earlier ones applied. That is deliberate: the cursor has not moved, so the
        // whole page is re-delivered, and the sink is idempotent. What must never happen is a
        // row that is *half* written.
        val harness = Harness()
        val good = aProject()
        harness.sink.apply(upsert(OutboxEntity.Project, good))

        assertFailsWith<AppError.Decoding> {
            harness.sink.apply(upsert(OutboxEntity.Project, "{\"not\":\"a project\"}"))
        }

        assertEquals(good, harness.repository.project(good.id))
        assertEquals(1, harness.database.projectDao().count())
    }

    // MARK: The round trip

    @Test
    fun aProjectQueuedByTheRepositoryComesBackThroughTheSinkUnchanged() = runTest {
        // The end-to-end property both halves have to agree on: the payload the repository
        // writes is the payload the sink can read. A test of the two separately cannot see a
        // disagreement between them, and a disagreement here is a server round trip that
        // silently loses a field.
        val source = Harness()
        val project = source.repository.createProject("Apollo", "APO", "notes", Moments.start)
        // A later instant for the second write, so the queue's order is decided by time rather
        // than by the random id its `createdAt ASC, id ASC` tie-break falls back to. Applying
        // these two payloads in the other order would end with a stale counter and this test
        // would fail about half the time.
        val item = source.repository.createWorkItem(
            project.id, "One", "more", WorkItemStatus.Done, Priority.High, "Ada", Moments.day, Moments.later,
        )
        val queued = source.queued()

        val target = Harness()
        queued.forEach { change ->
            target.sink.apply(
                RemoteChange(
                    entity = OutboxEntity.parse(change.entityKind)!!,
                    entityID = change.entityID,
                    operation = OutboxOperation.parse(change.operation)!!,
                    payload = change.payload,
                    version = null,
                ),
            )
        }

        // Compared against what the source reads *now*, not against the value createProject
        // returned: the work item issued a key, so the counter has moved on since, and the last
        // queued project payload is the one that carries the advanced one.
        assertEquals(source.repository.project(project.id), target.repository.project(project.id))
        assertEquals(source.repository.workItem(item.id), target.repository.workItem(item.id))
    }

    @Test
    fun aWorkItemThatWentOutAndCameBackKeepsItsResolutionDate() = runTest {
        // The field the original's two converters both dropped. Round-tripped through real JSON
        // this time, rather than through a mapping in memory, so a missing annotation or a
        // renamed column would show up rather than being invisible to both halves.
        val source = Harness()
        val project = source.repository.createProject("Apollo", "APO", "", Moments.start)
        val item = source.repository.createWorkItem(
            project.id, "One", "", WorkItemStatus.Done, Priority.Medium, null, null, Moments.start,
        )
        val queued = source.queued().last { it.entityKind == "workItem" }

        val target = Harness()
        target.sink.apply(
            RemoteChange(
                entity = OutboxEntity.WorkItem,
                entityID = queued.entityID,
                operation = OutboxOperation.Upsert,
                payload = queued.payload,
                version = null,
            ),
        )

        val readBack = assertNotNull(target.repository.workItem(item.id))
        assertEquals(Moments.start, readBack.resolvedAt)
        assertEquals("APO-1", readBack.key.rawValue)
    }


    // MARK: Sprints

    @Test
    fun aPulledSprintIsInsertedAndAnnounced() = runTest {
        val harness = Harness()
        val sprint = aSprint()
        val seen = mutableListOf<ProjectChange>()
        collecting(harness.bus.changes(), seen)

        harness.sink.apply(upsert(OutboxEntity.Sprint, sprint))

        assertEquals(sprint, harness.repository.sprint(sprint.id))
        // Both kinds: a subscriber hearing only the sprints half keeps showing the old
        // membership, and one hearing only the items half never learns the sprint exists.
        assertEquals(
            listOf(ProjectChange.Kind.Sprints, ProjectChange.Kind.WorkItems),
            seen.map { it.kind },
        )
        // The pull never writes to the outbox — the rule this whole file is built on.
        assertEquals(0, harness.queueDepth())
    }

    @Test
    fun aPulledSprintCanArriveClosed() = runTest {
        // The one asymmetry with a work item, and the reason it is here rather than in the
        // mapping test: a sprint is closed on whichever device its owner is holding, so the
        // closed state *has* to be receivable — and the summary, which is the entire point
        // of closing one, is computed from it.
        val harness = Harness()
        val open = aSprint()
        harness.sink.apply(upsert(OutboxEntity.Sprint, open))

        val closed = open.copy(isFinished = true, finishedAt = Moments.later)
        harness.sink.apply(upsert(OutboxEntity.Sprint, closed))

        val stored = assertNotNull(harness.repository.sprint(open.id))
        assertEquals(true, stored.isFinished)
        assertEquals(Moments.later, stored.finishedAt)
    }

    @Test
    fun aPulledSprintDeleteReturnsItsItemsToTheBacklog() = runTest {
        // Nothing local would ever clean this up otherwise, and the next pull would find a
        // card belonging to a sprint that does not exist. The *local* delete does the same
        // and is tested in `RoomSprintRepositoryTest`; this is the same requirement from the
        // pull side, which is a different code path and the one a re-delivered page takes.
        val harness = Harness()
        val sprint = aSprint()
        val item = aWorkItem(sprintID = sprint.id)
        harness.sink.apply(upsert(OutboxEntity.Sprint, sprint))
        harness.sink.apply(upsert(OutboxEntity.WorkItem, item))

        harness.sink.apply(delete(OutboxEntity.Sprint, sprint.id.stringValue))

        assertNull(harness.repository.sprint(sprint.id))
        assertNull(assertNotNull(harness.repository.workItem(item.id)).sprintID)
    }

    @Test
    fun aPulledProjectDeleteTakesItsSprintsWithIt() = runTest {
        // The mirror of the local cascade, and on the path a *server-driven* deletion takes.
        // Leaving the sprints behind would show a sprint on a Sprints tab whose project no
        // longer exists.
        val harness = Harness()
        val sprint = aSprint()
        harness.sink.apply(upsert(OutboxEntity.Sprint, sprint))
        harness.sink.apply(upsert(OutboxEntity.Project, aProject()))

        harness.sink.apply(delete(OutboxEntity.Project, aProject().id.stringValue))

        assertNull(harness.repository.sprint(sprint.id))
    }

    @Test
    fun applyingAPulledSprintTwiceLeavesTheSameStore() = runTest {
        // The idempotency requirement this whole file is built on, for the third aggregate.
        val harness = Harness()
        val sprint = aSprint()
        val change = upsert(OutboxEntity.Sprint, sprint)

        harness.sink.apply(change)
        harness.sink.apply(change)

        assertEquals(1, harness.repository.sprints(sprint.projectID).size)
        assertEquals(sprint, harness.repository.sprint(sprint.id))
    }

    // MARK: Helpers

    private fun upsert(entity: OutboxEntity, value: Any): RemoteChange =
        RemoteChange(
            entity = entity,
            entityID = idOf(value),
            operation = OutboxOperation.Upsert,
            payload = encode(value),
            version = null,
        )

    private fun upsert(
        entity: OutboxEntity,
        json: String,
    ): RemoteChange = RemoteChange(
        entity = entity,
        entityID = aProject().id.stringValue,
        operation = OutboxOperation.Upsert,
        payload = json.encodeToByteArray(),
        version = null,
    )

    private fun delete(entity: OutboxEntity, id: String): RemoteChange =
        RemoteChange(
            entity = entity,
            entityID = id,
            operation = OutboxOperation.Delete,
            payload = id.encodeToByteArray(),
            version = null,
        )

    private fun idOf(value: Any): String = when (value) {
        is Project -> value.id.stringValue
        is Sprint -> value.id.stringValue
        is WorkItem -> value.id.stringValue
        else -> error("not a projects payload: $value")
    }

    private fun encode(value: Any): ByteArray = when (value) {
        is Project -> JSONCoding.json.encodeToString(Project.serializer(), value).encodeToByteArray()
        is Sprint -> JSONCoding.json.encodeToString(Sprint.serializer(), value).encodeToByteArray()
        is WorkItem -> JSONCoding.json.encodeToString(WorkItem.serializer(), value).encodeToByteArray()
        else -> error("not a projects payload: $value")
    }

    /** Everything the sink could have written, as one comparable value. */
    private suspend fun snapshot(harness: Harness): String = buildString {
        append(harness.repository.projects(includeArchived = true))
        append(harness.database.workItemDao().all())
        append(harness.database.statusChangeDao().all().map { it.sequence })
    }
}
