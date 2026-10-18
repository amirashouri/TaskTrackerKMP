package com.tasktracker.sync

import com.tasktracker.core.AppError
import com.tasktracker.database.OutboxEntity
import com.tasktracker.database.OutboxEntry
import com.tasktracker.database.OutboxOperation
import com.tasktracker.network.HttpClient
import com.tasktracker.network.HttpMethod
import com.tasktracker.network.HttpQueryItem
import com.tasktracker.network.HttpRequest
import com.tasktracker.network.jsonBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `HttpClient`-backed [SyncTransport].
 *
 * Every TaskTracker path is `/v1`-prefixed and defined here, which is the single place the
 * convention is enforced. The original codebase had two spellings in flight
 * (`/v1/projects` from the network tests, `/projects` from an early sketch); a sync
 * endpoint written either way would have been a 404 that cost an afternoon.
 */
class HttpSyncTransport(
    private val client: HttpClient,
) : SyncTransport {

    /** The server's change-log root. */
    private object Path {
        const val PUSH = "/v1/sync/push"
        fun changes(scope: SyncScope): String = "/v1/sync/changes/${scope.wireValue}"

        /**
         * The file API for one attachment's bytes.
         *
         * Scoped by project *and* work item as well as attachment id, because the server
         * re-checks all three against the stored row before serving a byte: an id that
         * exists under a project the caller is not in must answer `404`, and a path that
         * named only the attachment id could not be checked that way.
         */
        fun blob(projectID: String, workItemID: String, attachmentID: String): String =
            "/v1/projects/$projectID/work-items/$workItemID/attachments/$attachmentID/blob"
    }

    // MARK: - Wire types

    /**
     * The request body for one push. `id` is stable across retries, which is what lets the
     * server deduplicate a batch it already applied — the reason a `POST` here is safe to
     * mark idempotent.
     */
    @Serializable
    private data class PushRequestBody(val changes: List<Change>) {
        @Serializable
        data class Change(
            val id: String,
            val entity: String,
            // Sent in the server's spelling, so both directions agree on one name. The server
            // would have accepted either — Go matches JSON keys case-insensitively — and that
            // is precisely why the pull direction went unnoticed for so long.
            @SerialName("entityId") val entityID: String,
            val operation: String,
            val payload: String,
        )
    }

    @Serializable
    private data class PushResponseBody(val results: List<Result>) {
        @Serializable
        data class Result(
            val id: String,
            val status: String,
            val version: String? = null,
            val message: String? = null,
        )
    }

    private enum class ServerStatus(val wireValue: String) {
        Accepted("accepted"),
        Conflict("conflict"),
        Rejected("rejected"),
        RetryLater("retry_later"),
        ;

        companion object {
            /** `null` for a status this build does not know. */
            fun parse(raw: String): ServerStatus? = entries.firstOrNull { it.wireValue == raw }
        }
    }

    @Serializable
    private data class ChangesResponseBody(
        val changes: List<Change>,
        val nextCursor: String? = null,
        // No default: a server that omits it has not said whether the log continues, and
        // guessing `false` would silently end the pull with pages still unread. A missing
        // field is a decoding error, which is the honest reading.
        val hasMore: Boolean,
        // This one *does* default, and the asymmetry is deliberate. The server sends it with
        // `omitempty`, so absent means "false" rather than "unstated" — and a server old
        // enough not to know the field is also saying there is nothing to resync. Leaving it
        // undeclared is not an option: `ignoreUnknownKeys` is off, so the field would fail
        // decoding on exactly the page that needs a resync, which is the one moment the
        // client must not fall over.
        val resyncRequired: Boolean = false,
    ) {
        @Serializable
        data class Change(
            val entity: String,
            // The server spells this `entityId`, not `entityID`. Go decodes JSON keys
            // case-insensitively so the push direction worked by accident, but decoding is
            // exact on this side and `ignoreUnknownKeys` is off — so an unannotated
            // `entityID` fails the whole page, on the pull path, for every change.
            @SerialName("entityId") val entityID: String,
            val operation: String,
            val payload: String,
            val version: String? = null,
        )
    }

    // MARK: - SyncTransport

    override suspend fun push(entries: List<OutboxEntry>): List<PushResult> {
        if (entries.isEmpty()) return emptyList()

        // Payloads go over the wire as strings. They are JSON documents, and base64 — which
        // is what a `ByteArray` encodes to by default — would be correct but unreadable in
        // server logs and in a debugger. `decodeToString` replaces malformed sequences the
        // same way the original's `String(decoding:as:UTF8.self)` did, so a payload that is
        // not UTF-8 is not a decoding failure either.
        val body = jsonBody(
            PushRequestBody(
                changes = entries.map { entry ->
                    PushRequestBody.Change(
                        id = entry.id,
                        entity = entry.entity.wireValue,
                        entityID = entry.entityID,
                        operation = entry.operation.wireValue,
                        payload = entry.payload.decodeToString(),
                    )
                },
            ),
        )

        val response = client.send(
            HttpRequest(
                method = HttpMethod.Post,
                path = Path.PUSH,
                body = body,
                // `POST` is not idempotent by default, and correctly so. This one is: every
                // change carries a stable id, so a server that records the ids it has applied
                // will drop a duplicate instead of applying the same edit twice. Without this
                // the client's own retry on a transport error could double-apply a rename.
                isIdempotent = true,
            ),
            decoding = PushResponseBody.serializer(),
        )
        return response.results.map { result -> PushResult(result.id, outcome(result)) }
    }

    override suspend fun pull(scope: SyncScope, cursor: String?, limit: Int): PullPage {
        val queryItems = mutableListOf(HttpQueryItem("limit", limit.toString()))
        if (cursor != null) {
            // Omitted entirely rather than sent empty, which is what the original meant: a
            // server distinguishing "first page" from "resume from the empty cursor" would
            // answer the second one with nothing.
            queryItems.add(HttpQueryItem("cursor", cursor))
        }

        val response = client.send(
            HttpRequest(
                method = HttpMethod.Get,
                path = Path.changes(scope),
                queryItems = queryItems,
            ),
            decoding = ChangesResponseBody.serializer(),
        )

        val changes = response.changes.map { change ->
            val entity = OutboxEntity.parse(change.entity)
                ?: throw AppError.Decoding(
                    "Unknown entity \"${change.entity}\" in a ${scope.wireValue} change.",
                )
            val operation = OutboxOperation.parse(change.operation)
                ?: throw AppError.Decoding(
                    "Unknown operation \"${change.operation}\" in a ${scope.wireValue} change.",
                )
            RemoteChange(
                entity = entity,
                entityID = change.entityID,
                operation = operation,
                payload = change.payload.encodeToByteArray(),
                version = change.version,
            )
        }
        return PullPage(
            changes = changes,
            nextCursor = response.nextCursor,
            hasMore = response.hasMore,
            resyncRequired = response.resyncRequired,
        )
    }

    // MARK: - Attachment bytes

    /**
     * Pushes one attachment's bytes.
     *
     * The bytes go as the request body itself, not inside a multipart form: the server reads
     * `r.Body` directly and caps it with `http.MaxBytesReader`, so a `413` is the server's
     * own verdict on the size rather than something this client has to measure first. The
     * content type is declared honestly as octet-stream — the server ignores it, but a proxy
     * that inspects bodies should not be told a photo is JSON.
     *
     * Marked idempotent: the server overwrites an existing blob and re-records the same
     * `blob_ref`, so a replay after a transport error cannot corrupt anything, and without
     * the flag the retry policy would refuse to resend a `POST` that failed in flight.
     */
    override suspend fun uploadAttachmentBlob(
        projectID: String,
        workItemID: String,
        attachmentID: String,
        bytes: ByteArray,
    ) {
        // The reply (`{"status":"stored"}`) is not needed: a success is the absence of an
        // exception, and `send` already turns every non-2xx into an `AppError`.
        client.send(
            HttpRequest(
                method = HttpMethod.Post,
                path = Path.blob(projectID, workItemID, attachmentID),
                headers = mapOf("Content-Type" to "application/octet-stream"),
                body = bytes,
                isIdempotent = true,
            ),
        )
    }

    /**
     * Fetches one attachment's bytes.
     *
     * `send` returns the raw body and raises [com.tasktracker.core.AppError.NotFound] for a
     * `404`, which is exactly what "the metadata arrived, the bytes have not" looks like from
     * here — the caller decides whether that is worth retrying.
     */
    override suspend fun downloadAttachmentBlob(
        projectID: String,
        workItemID: String,
        attachmentID: String,
    ): ByteArray = client.send(
        HttpRequest(
            method = HttpMethod.Get,
            path = Path.blob(projectID, workItemID, attachmentID),
        ),
    )

    // MARK: - Mapping

    private fun outcome(result: PushResponseBody.Result): PushOutcome {
        val message = result.message ?: "The server reported ${result.status}."
        // An unrecognised status is treated as retryable. Guessing "accepted" would drop a
        // change on a server that is merely newer than this build; guessing "rejected" would
        // dead-letter it. Retrying is the only safe default.
        return when (ServerStatus.parse(result.status)) {
            ServerStatus.Accepted -> PushOutcome.Accepted
            ServerStatus.Conflict -> PushOutcome.Conflict(result.version)
            ServerStatus.Rejected -> PushOutcome.Rejected(message)
            ServerStatus.RetryLater, null -> PushOutcome.RetryLater(message)
        }
    }
}
