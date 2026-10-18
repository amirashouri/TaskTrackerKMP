package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AttachmentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID

/**
 * Moves an attachment's **bytes** between this device and the server's file API.
 *
 * Metadata is a different road: it rides the change feed, so the row that names a file
 * reaches every member through the same outbox every other entity uses. This port exists
 * for the half that cannot ride that feed — a 25 MiB photo is not a JSON document, and
 * putting bytes in a change payload would make every sync page carry the file.
 *
 * ## Why a port rather than the transport itself
 *
 * `:packages:feature-projects-domain` deliberately depends only on `:packages:core` and
 * `:packages:domain`. Naming `SyncTransport` here would drag the sync module — and through
 * it the database and the network — into the layer that is supposed to be testable with
 * fakes. The composition root implements this port by delegating to `SyncTransport`, which
 * is the one place that knows both halves.
 *
 * ## The three checks are the server's, and this port does not repeat them
 *
 * Upload and download are both scoped by project, work item and attachment id, and the
 * server re-checks all three against membership before serving a byte. A caller cannot
 * widen access by passing ids it guessed: a mismatch answers `404`, never a file.
 */
interface AttachmentBlobTransport {

    /**
     * Pushes one attachment's bytes.
     *
     * The metadata row must already exist on the server — it arrives through the change
     * feed — so a `404` here means "not accepted yet", not "no such attachment". See
     * [UploadAttachment] for why that is a retryable state rather than a failure.
     */
    suspend fun upload(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
        bytes: ByteArray,
    )

    /**
     * Fetches one attachment's bytes.
     *
     * Throws `AppError.NotFound` when the server has no blob for the attachment yet, which
     * is a legitimate state while the upload is still in flight.
     */
    suspend fun download(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
    ): ByteArray
}
