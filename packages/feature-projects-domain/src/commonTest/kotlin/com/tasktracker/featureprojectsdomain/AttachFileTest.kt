package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.TestDateProvider
import com.tasktracker.core.WorkItemID
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The two rules only [AttachFile] can enforce, tested against a fake port.
 *
 * The guards are the point: each one must refuse *and leave the store untouched*, which
 * is what makes the assertions below distinguishable from a store that happened to
 * filter bad rows itself. A refusal the fake records as a write would be a use case that
 * half-worked, and only the empty-`attachments` check catches it.
 */
class AttachFileTest {

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    private suspend fun seeded(
        block: suspend (FakeProjectRepository, WorkItemID, AttachFile) -> Unit,
    ) {
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
        val attach = AttachFile(repository, TestDateProvider(now))
        block(repository, item.id, attach)
    }

    @Test
    fun `a valid file is stored and the stored attachment comes back`() = runTest {
        seeded { repository, itemID, attach ->
            val result = attach(
                workItemID = itemID,
                fileName = "crash-report.txt",
                mimeType = "text/plain",
                bytes = byteArrayOf(1, 2, 3),
            )

            assertIs<AttachFileResult.Success>(result)
            val stored = repository.attachments(itemID)
            assertTrue(stored.isNotEmpty(), "the attachment should have been stored")
            assertEquals("crash-report.txt", stored.first().fileName)
            assertEquals("text/plain", stored.first().mimeType)
            assertEquals(3L, stored.first().sizeBytes)
            assertEquals(result.attachment, stored.first())
        }
    }

    @Test
    fun `a blank name is refused and nothing is stored`() = runTest {
        seeded { repository, itemID, attach ->
            val result = attach(
                workItemID = itemID,
                fileName = "   ",
                mimeType = "text/plain",
                bytes = byteArrayOf(1, 2, 3),
            )

            assertIs<AttachFileResult.BlankName>(result)
            assertTrue(
                repository.attachments(itemID).isEmpty(),
                "a refused attach must leave the store untouched",
            )
        }
    }

    @Test
    fun `an empty file is refused and nothing is stored`() = runTest {
        seeded { repository, itemID, attach ->
            val result = attach(
                workItemID = itemID,
                fileName = "empty.txt",
                mimeType = "text/plain",
                bytes = byteArrayOf(),
            )

            assertIs<AttachFileResult.EmptyFile>(result)
            assertTrue(
                repository.attachments(itemID).isEmpty(),
                "a refused attach must leave the store untouched",
            )
        }
    }

    @Test
    fun `a file over the cap is refused naming the measured size and the limit`() = runTest {
        seeded { repository, itemID, attach ->
            val overCap = ByteArray((AttachFile.MAX_ATTACHMENT_BYTES + 1L).toInt())

            val result = attach(
                workItemID = itemID,
                fileName = "huge.bin",
                mimeType = "application/octet-stream",
                bytes = overCap,
            )

            assertIs<AttachFileResult.TooLarge>(result)
            assertEquals(
                AttachFile.MAX_ATTACHMENT_BYTES + 1L,
                result.sizeBytes,
                "the refusal must carry the measured size, not the limit",
            )
            assertEquals(AttachFile.MAX_ATTACHMENT_BYTES, result.limitBytes)
            assertTrue(
                repository.attachments(itemID).isEmpty(),
                "a refused attach must leave the store untouched",
            )
        }
    }

    @Test
    fun `a file exactly at the cap is accepted`() = runTest {
        seeded { repository, itemID, attach ->
            val atCap = ByteArray(AttachFile.MAX_ATTACHMENT_BYTES.toInt()) { 0x41 }

            val result = attach(
                workItemID = itemID,
                fileName = "at-cap.bin",
                mimeType = "application/octet-stream",
                bytes = atCap,
            )

            assertIs<AttachFileResult.Success>(result)
            assertEquals(AttachFile.MAX_ATTACHMENT_BYTES, result.attachment.sizeBytes)
        }
    }

    @Test
    fun `a name with surrounding whitespace is trimmed before it is stored`() = runTest {
        seeded { repository, itemID, attach ->
            val result = attach(
                workItemID = itemID,
                fileName = "  report.txt  ",
                mimeType = "text/plain",
                bytes = byteArrayOf(1),
            )

            assertIs<AttachFileResult.Success>(result)
            assertEquals("report.txt", result.attachment.fileName)
            assertEquals("report.txt", repository.attachments(itemID).first().fileName)
        }
    }

    @Test
    fun `attaching to a work item that no longer exists surfaces the store's refusal`() =
        runTest {
            seeded { repository, itemID, attach ->
                repository.deleteWorkItem(itemID)

                val failure = assertFailsWith<AppError.Validation> {
                    attach(
                        workItemID = itemID,
                        fileName = "report.txt",
                        mimeType = "text/plain",
                        bytes = byteArrayOf(1),
                    )
                }
                assertTrue(
                    failure.message?.contains("no longer exists") == true,
                    "the refusal should name why, not just refuse",
                )
            }
        }

}
