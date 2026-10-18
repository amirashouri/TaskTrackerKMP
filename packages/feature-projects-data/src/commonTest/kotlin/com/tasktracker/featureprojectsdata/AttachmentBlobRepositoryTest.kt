package com.tasktracker.featureprojectsdata

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.randomAttachmentID
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxOperation
import com.tasktracker.domain.Attachment
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.featureprojectsdomain.ProjectChange
import com.tasktracker.sync.RemoteChange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The attachment bytes, as the repository holds them.
 *
 * ## The queue assertions are the point
 *
 * [RoomProjectRepository.cacheAttachmentBytes] and [RoomProjectRepository.markAttachmentUploaded]
 * are bookkeeping about bytes, not edits to the file's metadata: caching a download must not
 * re-broadcast the row to every device, and marking an upload must not enqueue a second push.
 * A repository that queued from either would produce an endless echo of `attachment` changes
 * — every pull of a file causing another push of it — so the "queue depth is unchanged" lines
 * below are testing the thing that would actually break.
 *
 * The bytes are asserted through the store as well as the row, because a row that says it has
 * a file and a store that has no file is the desync these two methods exist to avoid.
 */
class AttachmentBlobRepositoryTest {

    @Test
    fun `adding a file stores its bytes and queues the metadata`() = runTest {
        val harness = Harness()
        val item = aWorkItemIn(harness)

        val attachment = harness.repository.addAttachment(
            workItemID = item.id,
            fileName = "crash-report.txt",
            mimeType = "text/plain",
            bytes = byteArrayOf(1, 2, 3),
            now = Moments.start,
        )

        // The project and the work item queued their own rows, so the attachment is found by
        // what it is rather than by position — the queue's order ties on a shared clock.
        val queued = harness.queued()
            .single { it.entityKind == OutboxEntity.Attachment.wireValue }
        assertEquals(OutboxOperation.Upsert.wireValue, queued.operation)
        assertEquals(attachment.id.stringValue, queued.entityID)

        assertContentEquals(
            byteArrayOf(1, 2, 3),
            harness.repository.attachmentBytes(attachment.id),
            "the row and the file must agree about what was attached",
        )
        assertNull(attachment.blobRef, "a fresh attach has not reached the server yet")
    }

    @Test
    fun `a row whose bytes are not on this device reads as null`() = runTest {
        val harness = Harness()
        val item = aWorkItemIn(harness)
        val attachment = harness.repository.addAttachment(
            workItemID = item.id,
            fileName = "crash-report.txt",
            mimeType = "text/plain",
            bytes = byteArrayOf(1),
            now = Moments.start,
        )

        // The file is gone but the row still names it: a re-install, or a cache evicted under
        // pressure. That is a "download it again", not a failure.
        harness.blobStore.delete(harness.blobStore.writes.single())

        assertNull(harness.repository.attachmentBytes(attachment.id))
        assertNotNull(harness.repository.attachment(attachment.id), "the metadata survives the bytes")
    }

    @Test
    fun `a pulled row arrives with metadata and no bytes`() = runTest {
        val harness = Harness()
        val item = aWorkItemIn(harness)
        val pulled = Attachment(
            id = randomAttachmentID(),
            workItemID = item.id,
            projectID = item.projectID,
            fileName = "from-a-teammate.png",
            mimeType = "image/png",
            sizeBytes = 12L,
            blobRef = "blob-on-the-server",
            createdAt = Moments.start,
        )

        // The fixture queued the project and the work item; the pull must add nothing to it.
        val queuedByFixture = harness.queueDepth()
        harness.sink.apply(upsertOf(pulled))

        assertEquals(pulled, harness.repository.attachment(pulled.id))
        assertNull(
            harness.repository.attachmentBytes(pulled.id),
            "a pull carries metadata only — the bytes are a separate request",
        )
        assertEquals(
            queuedByFixture,
            harness.queueDepth(),
            "a pulled row must never be pushed back",
        )
    }

    @Test
    fun `caching a download writes the file and does not queue anything`() = runTest {
        val harness = Harness()
        val item = aWorkItemIn(harness)
        val pulled = Attachment(
            id = randomAttachmentID(),
            workItemID = item.id,
            projectID = item.projectID,
            fileName = "from-a-teammate.png",
            mimeType = "image/png",
            sizeBytes = 12L,
            blobRef = "blob-on-the-server",
            createdAt = Moments.start,
        )
        harness.sink.apply(upsertOf(pulled))
        val queuedByFixture = harness.queueDepth()
        val changes = mutableListOf<ProjectChange>()
        val job = collecting(harness.bus.changes(), changes)

        val cached = harness.repository.cacheAttachmentBytes(pulled.id, byteArrayOf(1, 2, 3, 4))

        job.cancel()

        val row = assertNotNull(cached)
        assertTrue(row.blobPath.isNotEmpty(), "the row must name the file it now has")
        assertEquals(4L, row.sizeBytes, "the row must describe the bytes it holds, not the old count")
        assertContentEquals(byteArrayOf(1, 2, 3, 4), harness.repository.attachmentBytes(pulled.id))
        assertEquals(
            queuedByFixture,
            harness.queueDepth(),
            "a cache write is local bookkeeping, not an edit",
        )

        assertTrue(
            changes.any { it.kind == ProjectChange.Kind.WorkItems && it.projectID == item.projectID },
            "a screen showing this list must hear that the file is now openable",
        )
    }

    @Test
    fun `marking an upload records the ref and does not queue a second push`() = runTest {
        val harness = Harness()
        val item = aWorkItemIn(harness)
        val attachment = harness.repository.addAttachment(
            workItemID = item.id,
            fileName = "crash-report.txt",
            mimeType = "text/plain",
            bytes = byteArrayOf(1, 2, 3),
            now = Moments.start,
        )
        // The attach itself queued one row; the upload that follows must not queue another.
        val queuedByAttach = harness.queueDepth()
        val changes = mutableListOf<ProjectChange>()
        val job = collecting(harness.bus.changes(), changes)

        harness.repository.markAttachmentUploaded(
            id = attachment.id,
            blobRef = attachment.id.stringValue,
            uploadedAt = Moments.later,
        )

        job.cancel()

        val row = assertNotNull(harness.repository.attachment(attachment.id))
        assertEquals(attachment.id.stringValue, row.blobRef)
        assertEquals(Moments.later, row.uploadedAt)
        assertEquals(queuedByAttach, harness.queueDepth(), "the bytes are not a metadata edit")
        assertTrue(
            changes.any { it.kind == ProjectChange.Kind.WorkItems },
            "the list has to stop saying 'waiting' on this device too",
        )
    }

    // MARK: Fixtures

    /** A project with one work item, which is the least an attachment can hang off. */
    private suspend fun aWorkItemIn(harness: Harness) = run {
        val project = harness.repository.createProject(
            name = "Apollo",
            keyPrefix = "AP",
            details = "",
            now = Moments.start,
        )
        harness.repository.createWorkItem(
            projectID = project.id,
            title = "Crash on launch",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = Moments.start,
        )
    }

    private fun upsertOf(attachment: Attachment) = RemoteChange(
        entity = OutboxEntity.Attachment,
        entityID = attachment.id.stringValue,
        operation = OutboxOperation.Upsert,
        payload = JSONCoding.json.encodeToString(Attachment.serializer(), attachment)
            .encodeToByteArray(),
        version = null,
    )
}
