# attachment-client-sync — Make WorkItem attachments server-synced

- **status:** pending
- **priority:** P1
- **plan:** proposed (no `docs/PLAN.md` entry yet). Companion server spec:
  `../TaskTrackerServer/specs/attachment-server-storage.md`. **Dual-repo wire change** —
  the `attachment` sync entity and `/v1/.../attachments/:id/blob` endpoints are shared
  contract and require the parent `AGENTS.md` §1 ACK before implementation.

## Scope

Client-side only. The goal: attachment **metadata** flows through the existing outbox/sync
engine (so every project member receives it), and the **bytes** upload to / download from
the server's file API on demand, while staying offline-first.

Current gap (confirmed by reading the code): `RoomProjectRepository.addAttachment` writes
the blob + the `attachments` row but **never enqueues an outbox entry**, and
`LocalDataSyncSink.apply` has no `attachment` case — so today an attachment is purely local
and invisible to teammates.

## Files

- `packages/database/src/commonMain/kotlin/.../database/Outbox.kt` — add
  `Attachment("attachment")` to `OutboxEntity` (wire value must equal the server's).
- `packages/database/src/commonMain/kotlin/.../database/AttachmentRecord.kt` — add a
  `projectID` column (denormalised; the sink needs it to announce the owning project, same
  reason `CommentRecord` carries `workItemID`) and a `blobRef: String?` (server key; null
  until uploaded) + an `uploadedAt: Instant?` flag. Keep `blobPath` as the **local cache**
  path. Migration for the Room schema bump.
- `packages/feature-projects-data/.../RoomProjectRepository.kt`:
  - `addAttachment` (line ~1059): after writing blob + row, **enqueue** an outbox upsert
    `OutboxEntity.Attachment` with the JSON metadata payload (id, workItemID, projectID,
    fileName, mimeType, sizeBytes, createdBy, version). This is the central fix.
    Must remain offline-first: enqueue + local write commit; the blob upload is a separate,
    non-blocking step (see `UploadAttachment`).
  - `deleteAttachment` (line ~1101): enqueue an `attachment` delete outbox entry after the
    local row + blob are removed (the server deletes its file when it applies the delete).
- `packages/feature-projects-data/.../LocalDataSyncSink.kt` — add `OutboxEntity.Attachment`
  to `apply()`:
  - upsert → insert/update the `attachmentDao` row (metadata only; **do not** write a blob
    from a pull — bytes come on demand). Announce as a `WorkItems` change (mirrors the
    comment-write choice).
  - delete → `attachmentDao().deleteByID(id)` (like comment). A pulled delete may orphan the
    local cache file; that costs disk, not correctness — leave it (matches the existing
    "pulled delete can orphan blobs" KDoc in `applyWorkItem`).
- `packages/sync/.../HttpSyncTransport.kt` — add `SyncScope.Attachments("attachments")` so the
  existing pull loop reads `GET /v1/sync/changes/attachments` (the server serves the **plural**;
  an earlier `.../attachment` guess here was wrong). Add `uploadAttachmentBlob(...)` and
  `downloadAttachmentBlob(...)` to the `SyncTransport` interface + impl. The bytes are the **raw
  request/response body** (the server reads `r.Body` directly, capped by `http.MaxBytesReader`) —
  **not** multipart. Keep metadata payloads-as-strings for the sync feed.
- `packages/feature-projects-domain/.../ProjectUseCases.kt`:
  - `AttachFile` (line ~526): keep the 25 MiB cap; after the local store succeeds, hand off
    to `UploadAttachment` (new use case) which uploads the bytes to the server file API and
    sets `blobRef`/`uploadedAt`. `AttachFile` itself must NOT block on the network.
  - `LoadAttachments` (line ~482): unchanged shape, but callers should treat the list as
    server-synced metadata.
  - New `FetchAttachment` use case: returns local bytes if cached, else downloads from the
    server (membership-enforced server-side) and caches them. Used by the viewer.
  - `DeleteAttachment` (line ~574): unchanged local behaviour; the outbox delete is now
    enqueued by the repository.
- `packages/feature-projects-presentation/.../workitem/AttachmentList.kt` — surface sync
  state: a "uploading…" / cloud indicator while `blobRef` is null, "synced / visible to
  teammates" once set; tapping opens via `FetchAttachment` (download-on-demand).
- `packages/network/.../HttpErrorMapping.kt` — already hardened; add a `413` →
  `AppError.Validation("That file is larger than the 25 MB limit.")` mapping reusing the
  existing `TooLarge` framing so the file API's `413` reads like the local cap.

## Contract

- [ ] After `addAttachment`, an `OutboxEntity.Attachment` upsert row exists in the outbox
  (assert via `OutboxQueue`/DAO test).
- [ ] `LocalDataSyncSink.apply` for an `attachment` upsert inserts/updates the metadata row
  and does **not** write a blob; for a delete it removes the row.
- [ ] A pulled `attachment` delete does not throw when the local blob is absent.
- [ ] `AttachFile` returns immediately after local commit without waiting for the upload;
  the upload is observable via `blobRef` becoming non-null.
- [ ] `FetchAttachment` serves cached bytes without network when present, else downloads
  and caches.
- [ ] The client enum wire value is exactly `"attachment"` and the cap is `25 MiB`, matching
  the server contract.
- [ ] Offline attach (no network) still stores locally and enqueues; on reconnect the
  metadata pushes and the blob uploads.

## Validation

- `./gradlew :packages:feature-projects-data:jvmTest :packages:sync:jvmTest` (no JDK in the
  agent sandbox — the host must run these; the change is verified by inspection + host CI).
- New tests: outbox enqueue on attach, sink applies pulled attachment metadata, blob
  download-on-demand cache hit/miss, pulled-delete removes row, Room migration carries
  `projectID` + `blobRef`.

## Handoff

- **Done:** server half merged & green (`attachment-server-storage.md` T1–T4): `attachment`
  sync entity, blob file API, config. Wire contract (`attachment` entity, `/v1/.../blob`
  endpoints, 25 MiB cap) is fixed.
- **Done (T5 + T6):** client data model + enqueue/sink.
  - `OutboxEntity.Attachment("attachment")` added (wire value equals the server's
    `OutboxEntityAttachment`).
  - `AttachmentRecord` gained `projectID: String`, `blobRef: String?`, `uploadedAt: Instant?`;
    `blobPath` kept as the local cache. `TaskTrackerDatabase` version bumped 4 → 5 (destructive
    fallback recreates the dev store, per the class KDoc — no hand-written SQL migration, same
    as the existing reset story).
  - Domain `Attachment` (`packages/domain/.../Entities.kt`) gained `projectID: ProjectID?`,
    `blobRef: String?`, `createdBy: UserID?`, `version: String`, `uploadedAt: Instant?`. The
    `createdBy`/`version` fields are REQUIRED by the wire contract even though the spec's T5 line
    only named `projectID`/`blobRef`: the server's `Attachment` JSON carries `createdBy` and
    `version`, and `JSONCoding` leaves `ignoreUnknownKeys` **off**, so a pulled payload would fail
    to decode without them. `projectID`/`createdBy` are nullable-with-default so existing test
    doubles compile untouched; every real path sets `projectID`.
  - `RecordMapping` `toRecord()`/`domainValue` updated to round-trip the new columns.
  - `RoomProjectRepository.addAttachment` now sets `projectID = owner` (the work item's project)
    on the local row.
  - All other `Attachment(...)` construction sites use named args, so the new defaulted fields
    compile without edits.
  - **T6 (enqueue + sink):** `RoomProjectRepository.addAttachment` enqueues `OutboxEntity.Attachment`
    upsert inside the same transaction as the row write — offline-first, and `blobPath` is
    `@Transient` so it never reaches the wire (the server keeps its own blob store keyed by
    `blobRef`). `createdBy` is set from the session stamp so the server's `created_by` column is
    satisfied. `deleteAttachment` enqueues an `attachment` delete. `LocalDataSyncSink.apply` gained
    the `Attachment` case: a metadata upsert inserts the row when absent (no blob is written), a
    delete removes the row; both announce as a work-item change, matching the comment treatment.
    A pulled delete orphans the local cache file by design (disk cost, not correctness).
- **Done (T7):** transport + scope wiring for attachments.
  - `SyncScope.Attachments("attachments")` added. `SyncConfiguration.scopes` defaults to
    `SyncScope.all` (= `entries`), so the engine's existing `for (scope in scopes) { transport.pull(...) }`
    loop now pulls attachments through the already-existing server route
    `GET /v1/sync/changes/attachments` — no new pull method was needed.
  - `SyncTransport` gained `uploadAttachmentBlob(projectID, workItemID, attachmentID, bytes)` and
    `downloadAttachmentBlob(projectID, workItemID, attachmentID): ByteArray`, with **default**
    implementations that throw `AppError.Transport` — so the offline `InMemorySyncTransport` and
    every test double compile unchanged, and a no-server build fails loudly instead of silently
    "succeeding".
  - `HttpSyncTransport` implements both against
    `POST|GET /v1/projects/{projectId}/work-items/{workItemId}/attachments/{attachmentId}/blob`.
    The bytes are the **raw request/response body** (the server reads `r.Body` directly and caps it
    with `http.MaxBytesReader`, answering `413`). Upload is marked idempotent (the server overwrites
    the blob and re-records the same `blob_ref`).
  - `InMemorySyncTransport.scopeFor` gained `OutboxEntity.Attachment -> SyncScope.Attachments`. This
    was also a latent compile break: `OutboxEntity.Attachment` was added to the enum in T5 and the
    `when` is exhaustive, so the offline push path would have thrown `NoWhenBranchMatchedException`.
  - `AppGraph` registers `OutboxEntity.Attachment to sink`, so a pulled attachment change reaches
    `LocalDataSyncSink` instead of hitting `NoSink` and stalling the attachments pull.
  - `RoomProjectRepository.rebuiltPayload` gained an `Attachment` branch (the second latent
    exhaustive-`when` break from T5), so a server-refused attachment can be re-sent from the stored
    row (with `blobPath` still `@Transient`).
  - `HttpErrorMapping` maps `413` to `AppError.Validation("That file is larger than the 25 MB limit.")`;
    413 is deliberately **not** in `SURFACE_SAFE_STATUSES`, so the server's wording cannot replace
    the product message.
  - No test asserted the exact scope set: the two `pullRequests.size` assertions in
    `SyncEngineTest` override `scopes = listOf(SyncScope.WorkItems)`, and `StubSyncTransport.pull`
    returns an empty page for an unscripted scope, so default-scope tests are unaffected.
- **Done (#13):** use cases + wiring for blob upload/download.
  - **New domain port** `AttachmentBlobTransport` (`feature-projects-domain`): `upload`/`download`
    scoped by project, work item and attachment id. Kept separate from `SyncTransport` because the
    domain module must not depend on `:packages:sync`; the composition root implements it with
    `SyncBackedAttachmentBlobTransport`, the one class that knows both interfaces.
  - **New use cases** (`ProjectUseCases.kt`): `UploadAttachment` returns
    `Uploaded | AlreadySynced | NoLocalBytes | NotYetOnServer`; a `404` is `NotYetOnServer` (the
    metadata push has not landed) rather than an error. `FetchAttachment` serves cached bytes
    first, then downloads and caches on first open; it returns `null` when there is nothing to
    open yet.
  - **Repository port** gained `attachment`, `attachmentBytes`, `cacheAttachmentBytes`,
    `markAttachmentUploaded` (the last two never touch the outbox). They have default
    "no blob cache" bodies so the five existing fakes compile unchanged; `RoomProjectRepository`
    overrides all four. `AttachmentDao` gained `@Update`, and `AttachmentBlobStore` gained
    `read` (with `readBlobFile` actuals on JVM/Android/iOS).
  - **Wiring:** `AppGraph` binds `AttachmentBlobTransport` **only when `apiBaseUrl != null`** — a
    demo graph has no file API, so the editor keeps attachments local instead of attempting an
    upload that always fails. `ProjectEnvironment` carries the optional port; `RootScreen`
    resolves it; `WorkItemEditorModel` builds the two use cases from it.
  - **Non-blocking handoff:** `AttachFile` still returns immediately after the local commit;
    `WorkItemEditorModel.attachFile` now returns the stored `Attachment?`, and `NavigationHost`
    launches `model.uploadAttachment(id)` in the composition scope after a successful attach.
    `refreshAttachments` retries every row with `blobRef == null`, which recovers the immediate
    `404` once the metadata push lands (a refresh follows a sync change announcement).
  - **Stale docs corrected:** the `Attachment` entity's "device-local, never queued" section, the
    `addAttachment` port KDoc's "no outbox row", and `AttachmentRecord`'s "no pull path / no
    `projectID` column" note all predated T5 and now describe the synced behaviour.
- **Remaining gap (follow-up):** the retry sweep lives in the editor model, so it only runs while
  an editor is open. A user who attaches and immediately leaves the editor before the metadata
  push lands keeps `blobRef == null` until they reopen it. A global sweep (e.g. a post-sync
  coordinator hook that drains all `blobRef == null` rows with local bytes) would close this;
  it needs an "all pending attachments" query on the port.
- **Pending:** #14 (UI sync state) + T12 (tests, both repos).
- **Next step:** #14 — `AttachmentList`: show "uploading…" while `blobRef == null`, "synced" once
  set, and wire a row tap to `WorkItemEditorModel.attachmentBytes` (download-on-demand) once a
  platform file opener exists; add the `413` banner path. Then T12 tests: `UploadAttachment`
  (uploaded / already-synced / no-local-bytes / not-yet-on-server), `FetchAttachment` (cache hit,
  miss→download→cache, no blobRef), repository `cacheAttachmentBytes`/`markAttachmentUploaded`,
  and the `SyncScope.Attachments` pull.
- **Gotchas:** do NOT put bytes in the sync payload — that is the server's file API job.
  Preserve the sink invariant ("never writes the outbox"); attachments pulled are metadata
  only. Keep `AttachFile` offline-first (no network block). Backfill: existing local-only
  attachments must be enqueued + uploaded on first sync after this ships. The `/v1/.../blob`
  route returns 404 until the metadata row exists (arrives via sync), so the client must
  upload only after its `attachment` change is accepted.

## Risks / notes

- The 25 MiB client cap and the server `AttachmentMaxBytes` must stay equal; drift breaks
  uploads silently.
- Existing local attachments have no server row; a one-time backfill enqueue is required so
  they become visible to teammates (don't leave them stranded as local-only).
- `blobPath` becomes a *cache*, not the source of truth; a re-install should re-download
  rather than expect the file to survive.
