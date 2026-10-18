package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.AppError
import com.tasktracker.core.AttachmentID
import com.tasktracker.core.ProjectID
import com.tasktracker.core.WorkItemID
import com.tasktracker.featureprojectsdomain.AttachmentBlobTransport

/**
 * A file API that records every attempt and can be told to refuse.
 *
 * [attempts] counts calls that were *made*, [uploads] the ones that were allowed through.
 * The pair is what lets a test tell "the model retried" from "the model succeeded": a
 * refusal must not be retried, so `attempts` staying at one is the assertion, and a count
 * of successful uploads cannot make it.
 *
 * [failure] is thrown from every upload, which is how the model's two failure classes are
 * separated — a `Validation` is a verdict a row must remember, a `Transport` is weather.
 */
class FakeAttachmentBlobTransport(
    var failure: AppError? = null,
    private val downloadBytes: ByteArray = byteArrayOf(7, 7, 7),
) : AttachmentBlobTransport {

    /** Every upload call, whether it was refused or not. */
    var attempts: Int = 0
        private set

    /** The attachments whose bytes were actually accepted, in order. */
    val uploads = mutableListOf<AttachmentID>()

    override suspend fun upload(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
        bytes: ByteArray,
    ) {
        attempts += 1
        failure?.let { throw it }
        uploads += attachmentID
    }

    override suspend fun download(
        projectID: ProjectID,
        workItemID: WorkItemID,
        attachmentID: AttachmentID,
    ): ByteArray = downloadBytes
}
