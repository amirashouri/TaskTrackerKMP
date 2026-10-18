package com.tasktracker.compositionroot

import com.tasktracker.core.AttachmentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.featureprojectsdomain.AttachmentBlobTransport
import com.tasktracker.sync.SyncTransport

/**
 * The one place the domain's blob port meets the sync module's file API.
 *
 * The two interfaces are deliberately separate: the domain layer must not depend on
 * `:packages:sync` (that would drag the database and the network into the layer written
 * against fakes), while `SyncTransport` is the type that already owns the base URL, the
 * auth interceptor and the retry policy. This adapter is the seam — and because it lives
 * in the composition root, it is also the only class that has to change if either side's
 * id representation ever differs.
 *
 * The IDs are value classes with a `stringValue`; the wire uses strings, so the conversion
 * happens here rather than in the domain, where a `String` would be a second spelling of
 * the same identity.
 */
internal class SyncBackedAttachmentBlobTransport(
    private val transport: SyncTransport,
) : AttachmentBlobTransport {

    override suspend fun upload(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
        bytes: ByteArray,
    ) {
        transport.uploadAttachmentBlob(
            projectID = projectID.stringValue,
            workItemID = workItemID.stringValue,
            attachmentID = attachmentID.stringValue,
            bytes = bytes,
        )
    }

    override suspend fun download(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
    ): ByteArray = transport.downloadAttachmentBlob(
        projectID = projectID.stringValue,
        workItemID = workItemID.stringValue,
        attachmentID = attachmentID.stringValue,
    )
}
