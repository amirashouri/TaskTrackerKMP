package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The blob use cases — uploading, fetching, and the sweep that drains the upload queue —
 * against a fake port and a fake file API.
 *
 * ## Why the "nothing happened" assertions carry this file
 *
 * Every result here is a decision *not* to talk to the server: already synced, nothing local
 * to send, nothing to fetch. Those are indistinguishable from a bug that simply forgot to
 * call the transport, so each one asserts the call count as well as the result. A use case
 * that uploaded anyway would pass a test that only checked the return value.
 *
 * ## The sweep's two break rules, asserted separately
 *
 * One refused row must not stop the next one, and one failed path must not walk the rest of
 * the queue. Both are failures of the same loop, and the only way to tell them apart is a
 * file API that refuses *one* row and allows the rest — which is why the fake gained a
 * per-row failure.
 */
class AttachmentBlobUseCasesTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    // MARK: Uploading

    @Test
    fun `a row with local bytes is uploaded and marked with the server's key`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID)
        repository.seedBytes(attachment.id, byteArrayOf(1, 2, 3))
        val transport = FakeAttachmentBlobTransport()

        val result = UploadAttachment(repository, transport, TestDateProvider(now))(attachment.id)

        assertEquals(UploadAttachmentResult.Uploaded, result)
        assertEquals(listOf(attachment.id), transport.uploads)
        assertContentEquals(byteArrayOf(1, 2, 3), transport.lastUploadedBytes)
        assertNotNull(transport.lastProjectID, "the request must be scoped to a project")

        val stored = assertNotNull(repository.attachment(attachment.id))
        assertEquals(attachment.id.stringValue, stored.blobRef, "the ref names the blob the server holds")
        assertEquals(now, stored.uploadedAt, "the flag records when the bytes went up")
    }

    @Test
    fun `a row that already has a blob ref is not uploaded again`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID, blobRef = "already-there")
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val transport = FakeAttachmentBlobTransport()

        val result = UploadAttachment(repository, transport, TestDateProvider(now))(attachment.id)

        assertEquals(UploadAttachmentResult.AlreadySynced, result)
        assertEquals(0, transport.attempts, "a synced row must not spend a round trip")
    }

    /**
     * A row written before `projectID` existed cannot be scoped, and the file API refuses
     * anything it cannot scope. Uploading would spend a request to learn a 404, so the use
     * case answers "already synced" instead — the same bucket as "nothing to do".
     */
    @Test
    fun `a row with no project is not uploaded because it cannot be scoped`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID, scoped = false)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val transport = FakeAttachmentBlobTransport()

        val result = UploadAttachment(repository, transport, TestDateProvider(now))(attachment.id)

        assertEquals(UploadAttachmentResult.AlreadySynced, result)
        assertEquals(0, transport.attempts)
    }

    @Test
    fun `a row whose bytes are gone locally reports no local bytes`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID)
        val transport = FakeAttachmentBlobTransport()

        val result = UploadAttachment(repository, transport, TestDateProvider(now))(attachment.id)

        assertEquals(UploadAttachmentResult.NoLocalBytes, result)
        assertEquals(0, transport.attempts)
    }

    /**
     * The 404 that a client sees when it uploads before its metadata push has landed. It is
     * not an error: the row is queued, the next refresh retries, and the caller only needs to
     * know it should not treat the file as failed.
     */
    @Test
    fun `a not-found from the file api means the metadata has not landed yet`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val transport = FakeAttachmentBlobTransport(failure = AppError.NotFound("attachment not found"))

        val result = UploadAttachment(repository, transport, TestDateProvider(now))(attachment.id)

        assertEquals(UploadAttachmentResult.NotYetOnServer, result)
        assertNull(
            repository.attachment(attachment.id)?.blobRef,
            "a deferred upload must not claim the bytes are up",
        )
    }

    /**
     * Everything else propagates. The caller distinguishes a refusal from weather with
     * [AppError.isRetryable], and it can only do that if the use case hands the failure over
     * instead of flattening every error into a result.
     */
    @Test
    fun `a refusal propagates rather than being flattened into a result`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val refusal = AppError.Validation("That file is larger than the 25 MB limit.")
        val transport = FakeAttachmentBlobTransport(failure = refusal)

        val thrown = assertFailsWith<AppError> {
            UploadAttachment(repository, transport, TestDateProvider(now))(attachment.id)
        }

        assertEquals(refusal, thrown)
        assertNull(repository.attachment(attachment.id)?.blobRef)
    }

    // MARK: Fetching

    @Test
    fun `a cached file is served without touching the network`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID, blobRef = "on-the-server")
        repository.seedBytes(attachment.id, byteArrayOf(4, 5))
        val transport = FakeAttachmentBlobTransport()

        val bytes = FetchAttachment(repository, transport)(attachment.id)

        assertContentEquals(byteArrayOf(4, 5), bytes)
        assertEquals(0, transport.downloads, "a file already on disk must not be fetched again")
    }

    @Test
    fun `a file this device never had is downloaded once and then cached`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID, blobRef = "on-the-server", sizeBytes = 0)
        val transport = FakeAttachmentBlobTransport(downloadBytes = byteArrayOf(6, 7, 8))

        val bytes = FetchAttachment(repository, transport)(attachment.id)

        assertContentEquals(byteArrayOf(6, 7, 8), bytes)
        assertEquals(1, transport.downloads)

        val cached = assertNotNull(repository.attachment(attachment.id))
        assertTrue(
            cached.blobPath.isNotEmpty(),
            "the download must land in the cache, or every open would re-fetch it",
        )
        assertEquals(3L, cached.sizeBytes, "the cached row must describe the bytes it now holds")

        // The second open is a cache hit: the cache is the point, not a side effect.
        FetchAttachment(repository, transport)(attachment.id)
        assertEquals(1, transport.downloads, "a second open must not spend another download")
    }

    @Test
    fun `a row with no blob ref has nothing to fetch`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID)
        val transport = FakeAttachmentBlobTransport()

        val bytes = FetchAttachment(repository, transport)(attachment.id)

        assertNull(bytes)
        assertEquals(0, transport.downloads, "there is nothing on the server to ask for")
    }

    @Test
    fun `a row that no longer exists has nothing to fetch`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID, blobRef = "on-the-server")
        repository.deleteAttachment(attachment.id)
        val transport = FakeAttachmentBlobTransport()

        val bytes = FetchAttachment(repository, transport)(attachment.id)

        assertNull(bytes)
        assertEquals(0, transport.downloads)
    }

    /**
     * A build with no file API can still open the files it attached itself.
     *
     * This is the case the editor got wrong first: it built no `FetchAttachment` at all
     * without a transport, so a demo graph could not open a single one of its own
     * attachments — the local cache was never consulted. A `null` transport means "no server
     * to ask", not "this use case is unavailable", and the cache read comes first.
     */
    @Test
    fun `a cached file is served with no transport at all`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID)
        repository.seedBytes(attachment.id, byteArrayOf(4, 5))

        val bytes = FetchAttachment(repository)(attachment.id)

        assertContentEquals(byteArrayOf(4, 5), bytes)
    }

    /** ...and a row whose bytes are not here has nothing to answer with, rather than throwing. */
    @Test
    fun `a missing file with no transport has nothing to fetch`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID, blobRef = "on-the-server", sizeBytes = 0)

        assertNull(FetchAttachment(repository)(attachment.id))
    }

    // MARK: Sweeping

    /**
     * Every pending row, oldest first, and only the pending rows.
     *
     * The synced row is in the list to prove the filter is not decoration: a fake that
     * returned every attachment would still upload two rows here and the assertion would
     * pass, so the third one is what makes the test mean anything.
     */
    @Test
    fun `a sweep uploads every pending row and leaves a synced one alone`() = runTest {
        val (repository, itemID) = seeded()
        val first = repository.seedAttachment(itemID, fileName = "first.txt")
        val second = repository.seedAttachment(itemID, fileName = "second.txt")
        val done = repository.seedAttachment(itemID, fileName = "done.txt", blobRef = "already-there")
        repository.seedBytes(first.id, byteArrayOf(1))
        repository.seedBytes(second.id, byteArrayOf(2))
        repository.seedBytes(done.id, byteArrayOf(3))
        val transport = FakeAttachmentBlobTransport()

        val report = sweep(repository, transport)()

        assertEquals(listOf(first.id, second.id), transport.uploads, "oldest first, and only pending rows")
        assertEquals(listOf(first.id, second.id), report.uploaded)
        assertEquals(2, report.attempted)
        assertFalse(report.isEmpty)
        assertEquals(first.id.stringValue, repository.attachment(first.id)?.blobRef)
    }

    /** The overwhelmingly common cycle: nothing is waiting, so nothing is sent. */
    @Test
    fun `an empty queue is a sweep that sends nothing`() = runTest {
        val (repository, itemID) = seeded()
        val transport = FakeAttachmentBlobTransport()

        val report = sweep(repository, transport)()

        assertTrue(report.isEmpty)
        assertEquals(0, transport.attempts, "a pending query that matches nothing must not call the file API")
    }

    /**
     * A row with no local bytes cannot be uploaded from here, and retrying cannot change
     * that — so it is recorded, not thrown, and not spent a round trip on either. The sweep
     * continues past it: the next row is a different file with its own bytes.
     */
    @Test
    fun `a row with no local bytes is recorded and the sweep continues`() = runTest {
        val (repository, itemID) = seeded()
        val gone = repository.seedAttachment(itemID, fileName = "evicted.txt")
        val kept = repository.seedAttachment(itemID, fileName = "kept.txt")
        repository.seedBytes(kept.id, byteArrayOf(9))
        val transport = FakeAttachmentBlobTransport()

        val report = sweep(repository, transport)()

        assertEquals(listOf(gone.id), report.noLocalBytes)
        assertEquals(listOf(kept.id), report.uploaded)
        assertEquals(1, transport.attempts, "the row with no bytes must not be sent")
    }

    /** The 404 that means the metadata push has not landed: expected, and retried next time. */
    @Test
    fun `a not-yet-on-server row is left for the next sweep`() = runTest {
        val (repository, itemID) = seeded()
        val first = repository.seedAttachment(itemID, fileName = "first.txt")
        val second = repository.seedAttachment(itemID, fileName = "second.txt")
        repository.seedBytes(first.id, byteArrayOf(1))
        repository.seedBytes(second.id, byteArrayOf(2))
        val transport = FakeAttachmentBlobTransport(failure = AppError.NotFound("attachment not found"))

        val report = sweep(repository, transport)()

        assertEquals(listOf(first.id, second.id), report.notYetOnServer)
        assertTrue(transport.uploads.isEmpty(), "a refused upload must not claim the bytes are up")
        assertNull(repository.attachment(first.id)?.blobRef)
    }

    /**
     * The split the whole sweep turns on: a refusal is a verdict on *one* file, so stopping
     * would strand the rows behind it. Asserted through the transport's call list, because a
     * sweep that stopped at the refusal would leave the second row's bytes on this device
     * forever while reporting success.
     */
    @Test
    fun `a refused row does not stop the sweep from reaching the next one`() = runTest {
        val (repository, itemID) = seeded()
        val refused = repository.seedAttachment(itemID, fileName = "too-big.txt")
        val fine = repository.seedAttachment(itemID, fileName = "fine.txt")
        repository.seedBytes(refused.id, byteArrayOf(1))
        repository.seedBytes(fine.id, byteArrayOf(2))
        val verdict = AppError.Validation("That file is larger than the 25 MB limit.")
        val transport = FakeAttachmentBlobTransport(failureFor = { if (it == refused.id) verdict else null })

        val report = sweep(repository, transport)()

        assertEquals(2, transport.attempts)
        assertEquals(listOf(fine.id), transport.uploads)
        assertEquals(listOf(AttachmentSweepFailure(refused.id, verdict)), report.refused)
        assertNull(report.interruptedBy)
    }

    /**
     * And the other half: a transport failure is a fact about the path, so the remaining
     * rows would walk the same broken road. The sweep stops at the first one and the next
     * successful sync picks the queue back up — which is why the second row must still be
     * pending afterwards rather than silently forgotten.
     */
    @Test
    fun `a transport failure stops the sweep and leaves the rest pending`() = runTest {
        val (repository, itemID) = seeded()
        val first = repository.seedAttachment(itemID, fileName = "first.txt")
        val second = repository.seedAttachment(itemID, fileName = "second.txt")
        repository.seedBytes(first.id, byteArrayOf(1))
        repository.seedBytes(second.id, byteArrayOf(2))
        val weather = AppError.Transport("no route to host")
        val transport = FakeAttachmentBlobTransport(failureFor = { if (it == first.id) weather else null })

        val report = sweep(repository, transport)()

        assertEquals(1, transport.attempts, "the second row would have failed the same way")
        assertEquals(AttachmentSweepFailure(first.id, weather), report.interruptedBy)
        assertTrue(report.uploaded.isEmpty())
        assertNull(repository.attachment(second.id)?.blobRef, "an untried row must stay pending")
    }

    /**
     * The overlap with an open editor, which uploads the same rows for the same reason.
     *
     * The upload is idempotent, so two paths racing on one row is a timing fact rather than
     * an error — and a report that could not say so would have to call it a failure.
     */
    @Test
    fun `a row another path uploaded first is recorded as already synced`() = runTest {
        val (repository, itemID) = seeded()
        val first = repository.seedAttachment(itemID, fileName = "first.txt")
        val second = repository.seedAttachment(itemID, fileName = "second.txt")
        repository.seedBytes(first.id, byteArrayOf(1))
        repository.seedBytes(second.id, byteArrayOf(2))
        // An open editor finishing the second row's upload while the sweep is on the first:
        // the sweep's own attempt then finds `blobRef` already set.
        val transport = object : AttachmentBlobTransport {
            override suspend fun upload(
                projectID: ProjectID,
                workItemID: WorkItemID,
                attachmentID: AttachmentID,
                bytes: ByteArray,
            ) {
                repository.markAttachmentUploaded(second.id, "editor-did-this-one", now)
            }

            override suspend fun download(
                projectID: ProjectID,
                workItemID: WorkItemID,
                attachmentID: AttachmentID,
            ): ByteArray = byteArrayOf()
        }

        val report = sweep(repository, transport)()

        assertEquals(listOf(second.id), report.alreadySynced)
        assertEquals(listOf(first.id), report.uploaded)
        assertTrue(report.refused.isEmpty() && report.interruptedBy == null)
    }

    /** A cancelled sweep is a stopped sweep, not a failed one. */
    @Test
    fun `a cancelled sweep propagates rather than reporting a failure`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID)
        repository.seedBytes(attachment.id, byteArrayOf(1))
        val transport = object : AttachmentBlobTransport {
            override suspend fun upload(
                projectID: ProjectID,
                workItemID: WorkItemID,
                attachmentID: AttachmentID,
                bytes: ByteArray,
            ) = throw CancellationException("left the screen")

            override suspend fun download(
                projectID: ProjectID,
                workItemID: WorkItemID,
                attachmentID: AttachmentID,
            ): ByteArray = byteArrayOf()
        }

        assertFailsWith<CancellationException> { sweep(repository, transport)() }
    }

    // MARK: Backfilling

    /**
     * One change per pending row, oldest first — the set the sweep is about to drain.
     *
     * The two rows that are *not* queued are the assertion: a synced row is already on the
     * server and re-announcing it would be noise, and a pulled row has no bytes here to
     * upload even though it looks pending to anything that reads only `blobRef`.
     */
    @Test
    fun `the backfill queues one change per pending row`() = runTest {
        val (repository, itemID) = seeded()
        val first = repository.seedAttachment(itemID, fileName = "first.txt")
        val second = repository.seedAttachment(itemID, fileName = "second.txt")
        val synced = repository.seedAttachment(itemID, fileName = "synced.txt", blobRef = "on-the-server")
        repository.seedBytes(first.id, byteArrayOf(1))
        repository.seedBytes(second.id, byteArrayOf(2))

        val queued = BackfillUnqueuedAttachments(repository, TestDateProvider(now))()

        assertEquals(2, queued)
        assertEquals(
            listOf(first.id, second.id),
            repository.queuedAttachmentChanges(),
            "the change must be queued for every row the server cannot already hold",
        )
    }

    /** A store with nothing pending is a backfill that queues nothing, and says so. */
    @Test
    fun `a store with nothing pending queues nothing`() = runTest {
        val (repository, itemID) = seeded()
        val attachment = repository.seedAttachment(itemID, blobRef = "on-the-server")

        val queued = BackfillUnqueuedAttachments(repository, TestDateProvider(now))()

        assertEquals(0, queued)
        assertTrue(repository.queuedAttachmentChanges().isEmpty())
        assertNotNull(repository.attachment(attachment.id))
    }

    // MARK: Fixtures

    private suspend fun seeded(): Pair<FakeProjectRepository, WorkItemID> {
        val repository = FakeProjectRepository()
        val project = repository.createProject(
            name = "Apollo",
            keyPrefix = "AP",
            details = "",
            now = now,
        )
        val item = repository.createWorkItem(
            projectID = project.id,
            title = "Crash on launch",
            details = "",
            status = WorkItemStatus.Todo,
            priority = Priority.Medium,
            assigneeID = null,
            dueDate = null,
            now = now,
        )
        return repository to item.id
    }

    private fun sweep(repository: ProjectRepositoryPort, transport: AttachmentBlobTransport) =
        SweepPendingAttachments(repository, UploadAttachment(repository, transport, TestDateProvider(now)))
}
