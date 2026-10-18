package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The two blob use cases, against a fake port and a fake file API.
 *
 * ## Why the "nothing happened" assertions carry this file
 *
 * Every result here is a decision *not* to talk to the server: already synced, nothing local
 * to send, nothing to fetch. Those are indistinguishable from a bug that simply forgot to
 * call the transport, so each one asserts the call count as well as the result. A use case
 * that uploaded anyway would pass a test that only checked the return value.
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
}
