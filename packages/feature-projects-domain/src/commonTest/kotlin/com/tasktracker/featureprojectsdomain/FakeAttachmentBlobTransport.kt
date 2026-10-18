package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID

/**
 * A file API that records what was asked of it and can be told to refuse.
 *
 * Counts `attempts` and `downloads` rather than only successes, because most of what these
 * use cases promise is about calls that *should not happen*: an already-synced row must not
 * upload, a row with no bytes must not upload, and a cached file must not download. A
 * recorder that only kept successes would let every one of those pass by accident.
 *
 * [failureFor] is the per-row override, and it exists for the sweep specifically: a sweep is
 * tested on what it does about one refused row *and the next one*, which a single blanket
 * failure cannot express.
 */
class FakeAttachmentBlobTransport(
    var failure: AppError? = null,
    var downloadBytes: ByteArray = byteArrayOf(9, 9, 9),
    private var failureFor: ((AttachmentID) -> AppError?)? = null,
) : AttachmentBlobTransport {

    var attempts: Int = 0
        private set

    var downloads: Int = 0
        private set

    /** The ids whose bytes were accepted, in order. */
    val uploads = mutableListOf<AttachmentID>()

    /** The last payload the use case handed over, so a test can prove the bytes were read. */
    var lastUploadedBytes: ByteArray? = null
        private set

    /** The project the last upload was scoped to — the membership the server will check. */
    var lastProjectID: ProjectID? = null
        private set

    override suspend fun upload(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
        bytes: ByteArray,
    ) {
        attempts += 1
        (failureFor?.invoke(attachmentID) ?: failure)?.let { throw it }
        uploads += attachmentID
        lastUploadedBytes = bytes
        lastProjectID = projectID
    }

    override suspend fun download(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
    ): ByteArray {
        downloads += 1
        failure?.let { throw it }
        return downloadBytes
    }
}
