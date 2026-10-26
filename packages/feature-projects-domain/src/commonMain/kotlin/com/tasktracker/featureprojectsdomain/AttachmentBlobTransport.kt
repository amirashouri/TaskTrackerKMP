package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.AttachmentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID

/**
 * Transport port for pushing and downloading attachment binary blobs.
 */
interface AttachmentBlobTransport {

    suspend fun upload(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
        bytes: ByteArray,
    )

    suspend fun download(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
    ): ByteArray
}
