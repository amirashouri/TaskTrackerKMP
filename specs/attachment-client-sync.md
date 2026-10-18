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
- **Done (#20):** the global sweep that drains attachments a closed editor left behind.
  - **The gap it closes.** The upload was fired by the editor after an attach, so the queue
    was only drained while an editor was open. A user who attached a file and left the card
    before the upload landed kept `blobRef == null` — bytes safe on this device, file
    invisible to teammates — until they came back. The spec's own follow-up asked for a
    post-sync hook; this is it.
  - **The seam is a closure, not an import.** `SyncCoordinator` gained
    `onCycleSucceeded: suspend (SyncReport) -> Unit = {}`, invoked after a cycle whose
    report `isSuccess` and launched in the coordinator's own scope. `:packages:sync` may not
    name a feature, so the dependency is handed in the same way the engine takes its sink
    map. Launched rather than awaited because the gate is held for the length of a cycle,
    and a callback that uploads a 25 MiB body would hold it for as long as the body takes to
    leave the device — a periodic trigger arriving in that window would be dropped for a
    reason that has nothing to do with a cycle already running.
  - **Every failure is contained there.** The hook is a child of a scope whose job is a
    `SupervisorJob` with no exception handler, so a throwing hook would reach the platform's
    default handler — on iOS, the process. `runHook` logs and returns; only
    `CancellationException` propagates. A test in `SyncCoordinatorTest` does the assertion
    by throwing: `runTest` reports an uncaught child exception as a failure, so that test
    failing *is* the proof of containment.
  - **The port query the spec asked for.** `AttachmentDao.pendingUploads`
    (`blobRef IS NULL AND blobPath != ''`, ordered `createdAt, sequence`), surfaced as
    `pendingAttachmentIDs()` on the port with a `mapNotNull` parse in the repository. Both
    conditions are one question: a row with a ref has nothing to send, and a row with a
    blank path has nothing to send *with*. A row whose file was evicted keeps a non-blank
    path — SQL cannot see the filesystem — so it stays in the list and costs one local read
    per sweep, landing in the report rather than being confused with a row never sent.
  - **`SweepPendingAttachments`** runs the same `UploadAttachment` the editor runs, one row
    at a time, and the failure split is the editor's own: a **refusal** (`!isRetryable`) is
    a verdict on one file, so it is recorded and the sweep continues — stopping would strand
    the rows behind it; a **transport failure** is a fact about the path, so the sweep stops
    and the next successful sync picks the queue back up. The report carries `uploaded`,
    `alreadySynced`, `noLocalBytes`, `notYetOnServer`, `refused` and `interruptedBy` rather
    than a count, because "3 uploaded" cannot say what happened to the other seven.
  - **Overlap with the editor is a fact, not an error.** The upload is idempotent end to
    end, so the same row uploaded by two paths overwrites the same blob and lands on the
    same `blobRef`; the `alreadySynced` bucket exists so that race reads honestly.
  - **Wiring** follows the blob transport's switch: the sweep and its `UploadAttachment` are
    registered only when `apiBaseUrl != null`, read by the coordinator through `getOrNull`,
    and resolved once at coordinator construction — a hook that resolved per cycle would be
    a second resolution path per cycle and would throw after `AppGraph.stop` while a sweep
    is still finishing its last upload. A demo graph gets no hook and keeps its attachments
    local, exactly as before.
  - **Tests:** 5 in `SyncCoordinatorTest` (a success runs the hook with the cycle's own
    report; a deferred cycle, a dead session and a cycle that never started do not; a hook
    that throws does not disturb the cycle), 8 in `AttachmentBlobUseCasesTest` (every pending
    row oldest first with a synced row skipped, an empty queue, a row with no bytes recorded
    while the sweep continues, `NotYetOnServer` left for the next sweep, a refusal continuing,
    a transport failure stopping with the rest still pending, another path's upload read as
    `alreadySynced`, a cancellation propagating), 2 in `AttachmentBlobRepositoryTest`
    (only this device's unsent rows are pending; a row stops being pending once its upload
    is recorded) and 1 in `AppGraphTest` (the sweep is registered only with a backend, and a
    demo graph still builds its coordinator). Both `FakeProjectRepository`s gained
    `pendingAttachmentIDs`, mirroring the SQL rather than returning everything, so the
    sweep cannot be tested against a store that filters nothing.
  - **Baseline:** regenerated after a green three-host run — **1802 jvm / 1751 iOS /
    1460 Android-host, 0 failures** (the Android host's 62 skips are the store-opening
    scenarios `thisHostOpensRoomStores` guards, including the resolution half of the graph
    test above).
- **Remaining gap (follow-up):** ~~a one-time backfill enqueue for attachments that predate
  sync~~ — done in #21 below.
- **Done (#21):** the one-time backfill enqueue for attachments that predate sync.
  - **The shape of the hole.** A row written before the change feed carried attachments has a
    row, a blob and **no outbox entry**, so no cycle pushes it and no teammate ever sees it —
    however many times the app syncs. The bytes are on this device and the sweep (#20) now
    uploads any row with no `blobRef`, so what was missing was only the metadata change.
  - **The set is structural, which is what makes it converge.** The backfill queues a change
    for every row in `pendingAttachmentIDs` — `blobRef IS NULL AND blobPath != ''` — and that
    is exactly the set the server cannot already hold as metadata: a row reaches
    `blobRef != null` only through an upload, and an upload is answered `404` unless the
    metadata row is already there. So a still-pending row is either pre-sync or queued and
    not yet pushed, and queueing its change is correct in both cases. Rows the server has
    fall out of the set by themselves, which is why no durable "which rows did I backfill"
    marker exists to get wrong: run it once, the set empties as the uploads land.
  - **`ProjectRepositoryPort.queueAttachmentChange(id, now)`** is the write, and it is the
    same enqueue `addAttachment` performs — one `OutboxEntity.Attachment` upsert, one payload,
    re-read from the stored row so the server learns what this device actually holds. It
    deliberately does **not** deduplicate: the outbox is a log, an entry disappears the
    moment the server accepts it, so "already queued" is not a question the store can answer,
    and the server's upsert is idempotent. A repeated id costs a payload, not correctness.
  - **Once per install, and the flag is raw.** `AttachmentUpgradeBackfill` (composition root)
    holds the bound, because the domain module has no preferences by design. The key is a
    plain string rather than a `DefaultsKey` for the reason the appearance scheme's is:
    `removeAll` walks the enum on sign-out, so an enum member would let the backfill replay
    after the first sign-out as though it had never run. The value stored is how many changes
    were queued, so a later "why did this device re-announce everything" names a number.
    A failure leaves the flag unset, so the next successful cycle picks the queue back up.
  - **Why the bound exists even though the work converges.** An attachment whose metadata is
    already on the server but whose bytes keep failing to upload stays in the pending set
    forever, so an unbounded backfill would re-announce that one file on every cycle, to
    teammates who can already see it. The work is idempotent by construction; the bound is
    what keeps the idempotence from becoming noise.
  - **Order in the hook, and why it is sweep-first.** Both halves converge on the cycle after
    the backfill either way, so the order is not about correctness — it is about what a
    failing backfill costs. The sweep is the recurring work and the backfill is a one-time
    catch-up, so the sweep runs first: a store that throws while queueing the pre-sync rows
    still uploads everything that was already queued. Neither waits.
  - **Tests:** 2 in `AttachmentBlobUseCasesTest` (one change per pending row, oldest first,
    with a synced row and a byte-less row skipped; an empty store queues nothing and says
    so), 2 in `AttachmentBlobRepositoryTest` (the queued payload is the stored row's own, with
    `blobPath` excluded and the bytes untouched; a missing row queues nothing and leaves the
    queue alone), 5 in `AttachmentUpgradeBackfillTest` (first run queues and records the
    count, a second run does nothing, sign-out does not clear the flag, a failed run leaves
    it unset and retries, an empty store still records that it ran) and 2 more assertions in
    `AppGraphTest` (the backfill rides the same backend switch as the sweep).
- **Next step:** none outstanding for attachments. Remaining spec-wide work is outside this
  file.
- **Done (#14):** per-row sync state, and the refusal/transient split behind it.
  - **Five states, not two.** `AttachmentSyncState` (`AttachmentList.kt`) is `Synced`,
    `Uploading`, `Waiting`, `Failed`, `LocalOnly`. The original plan's "`blobRef == null` means
    uploading…" was wrong in three ways at once: a queued file, a file in flight and a file the
    server refused all have no ref, so one label would have lied about two of them. `LocalOnly`
    covers the demo graph, where there is no server to wait for and a spinner could never resolve.
  - **The model owns the states** (`WorkItemEditorModel.syncStateOf`), because only it knows what
    is in flight. Two id sets back it — `uploadingIDs` and `refusedIDs` — and the check order is
    the contract: `blobRef` first, then "no transport", then in-flight, then refused, else waiting.
  - **Refusal vs transient is `AppError.isRetryable`**, the same property the sync engine retries
    on, so the two cannot drift apart. A non-retryable failure (a `413`, a `400`) sets `Failed`,
    raises the banner and is **skipped by the refresh sweep** — re-sending a verdict on every
    refresh would re-raise the banner forever. A retryable one stays `Waiting` and stays quiet:
    painting an error over an attach the user watched succeed, for a problem that fixes itself,
    is how a button loses its trustworthiness.
  - `NoLocalBytes` also lands on `Failed` (retrying cannot put the bytes back) but with no banner,
    since no user action asked for it.
  - **`AttachmentList` renders the state** as a caption beside the size, `ttError()` for `Failed`
    only. The `syncStateOf` parameter is nullable: null means "this caller has no sync
    information", matching the editor's existing "empty list means no file list" convention. A
    default of `Synced` was rejected — it would put a checkmark on a file that never left the
    device.
  - **Threading:** only `WorkItemEditorSheet` → `ProjectsEditorFullScreen` carries it, because that
    is the one live path. `WorkItemEditorScreen` is preview-only (it passes no attachment params at
    all today), so it was deliberately left alone rather than given a sixth dead parameter.
  - **Fake upgraded to make this testable:** `FakeProjectRepository` now implements `attachment`,
    `attachmentBytes`, `cacheAttachmentBytes`, `markAttachmentUploaded`, keeps bytes in a separate
    `blobBytes` map (the real store separates metadata from bytes too), and — a real bug in the
    double — now sets `projectID` on `addAttachment`. Without that every upload read as
    `AlreadySynced`, because the use case refuses a row it cannot scope.
  - **Tests:** four in `WorkItemEditorModelTest` — `LocalOnly` with no transport, `Synced` after a
    successful sweep, `Failed` + banner + no retry on a `Validation`, `Waiting` + silence + retry
    on a `Transport`. `FakeAttachmentBlobTransport` counts `attempts` separately from `uploads` so
    "was retried" and "succeeded" cannot be confused.
- **Done (T12):** tests across all four layers. 25 new tests, both repos green.
  - **Domain** (`AttachmentBlobUseCasesTest`, 10): `UploadAttachment`'s four results plus the
    case where a refusal must propagate rather than be flattened into one — the model's
    retry/refuse split depends on that, so a use case that swallowed a `413` would break the
    feature without failing any other test. `FetchAttachment` covers cache hit (0 downloads),
    miss → download → cache, and that a second open is a cache hit.
  - **Data** (`AttachmentBlobRepositoryTest`, 5): `cacheAttachmentBytes` and
    `markAttachmentUploaded` write the blob / set the ref **and queue nothing**. That is the
    assertion that matters: either one enqueuing a change would make every pulled file echo
    back to the server as a new push. The harness now injects an in-memory `FakeBlobStore`
    instead of the production `FileBlobStore()`, which was writing into the real support
    directory during tests.
  - **Sync** (2): `SyncScope.Attachments` is in `SyncScope.all` (a scope defined but omitted
    from `all` would be pulled by nobody, silently), and an attachment change is routed to the
    `Attachment` sink.
  - **Server** (`internal/attachments/handler_test.go`, 8): upload stores bytes and links
    `blobRef`; oversize → 413 with no file left behind; upload before the metadata row → 404
    (the client's `NotYetOnServer`); non-member → 404 on both verbs; download serves the bytes
    with the stored mime type and filename; a member using a mismatched work-item id → 404.
  - **Two bugs in the doubles, found by writing these:** both `FakeProjectRepository`s never set
    `projectID` on `addAttachment`, so every upload read as `AlreadySynced`; and the data-module
    `Harness` was constructing the real `FileBlobStore()`, so attachment tests wrote files into
    the device's support directory.
- **Not done, deliberately:** the row tap that would call `attachmentBytes`. There is still no
  platform file opener (a `Desktop.open` / `Intent` / `UIDocumentInteractionController` seam), and a
  tappable row that opens nothing is the mock this repo refuses. The use case is ready and tested
  at the domain level; the opener is its own slice.
- **Done (#19):** the file opener — the row is now a doorway.
  - **New port** `AttachmentOpener` (`feature-projects-domain/commonMain`): `open(fileName,
    mimeType, bytes)`. It takes **bytes, not the cached path**, which is the mirror of
    `FilePicker`: there the platform owns the file and the domain wants the bytes, here the
    domain owns the bytes and the platform wants a file. A path-shaped port would force the
    domain to know the blob store's layout, and the store refuses to hand out absolute paths
    for the same reason. Each side hands over a copy and keeps its own storage rules; the copy
    is bounded by `AttachFile.MAX_ATTACHMENT_BYTES`.
  - **`handOffFileName`** (shared, `internal`) is the one place a picked name becomes a file
    name. A picked name is display text and can carry a separator or `..`; three platform
    implementations of that guard is three chances to get one wrong.
  - **Three actuals**, each writing a scratch copy and then handing it off:
    - JVM: `java.awt.Desktop.open` on a file in `java.io.tmpdir`. `isDesktopSupported` is
      checked first because `getDesktop()` throws `HeadlessException` on a server or in CI, and
      both halves run on `Dispatchers.IO` because both block.
    - Android: `ACTION_VIEW` on a `content://` URI from a `FileProvider`. Since API 24 a
      `file://` URI throws `FileUriExposedException`, so the provider is not optional. The
      provider is declared in **this module's** manifest with `${applicationId}.fileprovider`
      and a single narrow `<cache-path path="attachments/">` — the code and its requirement
      travel together, the same way `:packages:network` ships the permission its connectivity
      callback needs. `FLAG_ACTIVITY_NEW_TASK` is required because the launch comes from the
      application context.
    - iOS: `UIDocumentInteractionController.presentOpenInMenuFromRect`, on the main queue, with
      the controller held on the opener — UIKit does not retain it, and a released controller is
      the silent "nothing happened" the port's KDoc refuses. `presentOpenInMenu` rather than
      `presentOptionsMenu` because the press means "open", and a Quick Look preview is a
      different feature.
  - **A real bug fixed on the way:** `FetchAttachment` now takes a **nullable** transport, and
    `WorkItemEditorModel` builds it unconditionally. It used to be built only when a blob
    transport existed, which meant a demo graph — no server — could not open the files it had
    attached itself, because `attachmentBytes` returned `null` before ever looking at the
    cache. `null` now means "no file API", so a cache hit still answers and a cache miss
    returns `null` instead of attempting a download that has nowhere to go.
  - **The model** (`openAttachment`) fetches through `attachmentBytes` and hands the result to
    the opener. Three cases stay silent — no opener, no such row, nothing to open — because
    none of them is a failure of the press and the row's own caption already says which
    applies. A failed download and a platform that cannot open the type both reach the banner,
    because the user asked for this one. `openingIDs` is the re-entrancy guard, cleared in a
    `finally` so a cancelled open cannot leave a row saying "opening" for the model's lifetime.
  - **The row** is pressable **only when there is an opener**: `onOpen` is nullable, and `null`
    renders no press target at all rather than a tap that does nothing. The press target is the
    **name**, not the row — a full-width tap area would sit under Remove and turn a near-miss
    into a file opening instead of a file being removed.
  - **Opening is not a sync state.** `isOpening` is a separate nullable callback and the
    caption slot shows "Opening…" while it is true, then the sync state comes back. Folding a
    transient activity into `AttachmentSyncState` would put it in a slot that otherwise never
    lies about where the file is; two captions per row would be worse.
  - **Two pre-existing iOS breaks found and fixed**, both invisible while only `jvmTest` ran:
    - `BlobFiles.ios.readBlobFile` called `NSData.getBytes`, which is **absent** from this
      Kotlin version's `NSData` projection — the exact gap `FilePicker.ios` documents. The
      module's iOS target had never been compiled, so the blob store did not build for iOS at
      all. Fixed with the `bytes` + `memcpy` route the other two iOS seams use.
    - `WorkItemEditorModelTest` had a test named `a refusal is remembered, explained, and not
      retried`. The Kotlin/Native backend rejects a comma in a function name outright, and it
      rejects the **whole module**, so that one comma made every iOS test in
      `feature-projects-presentation` uncompilable. Renamed, and the rule is now written at the
      top of the class so the next one is not added.
  - **Tests:** 6 in `WorkItemEditorModelTest` (bytes/name/type handed over; a remote row
    downloads first; nothing to open is silent; no opener is silent; a platform refusal is
    reported and clears the flag; a second press mid-open does not open twice — the last one
    observed *from inside* the fake's `open`, which is the only window where the guard does
    anything), 2 in `AttachmentBlobUseCasesTest` (a cache hit answers with no transport at all;
    a missing file with no transport returns `null`), and 4 UI scenarios in
    `AttachmentRowScenarios`, each wired into all three test targets: no opener means no press
    target, pressing names the file, and the caption slot swaps in both directions.
  - **The test baseline was stale and is now regenerated.** `test-baseline.json` still held the
    pre-T12 counts (1749 / 1698 / 1414), so `scripts/check-baseline.sh` failed on all three
    hosts. It is generated, not hand-edited: `scripts/check.sh full` → `scripts/test-baseline.py`
    now records **1786 jvm / 1735 iOS / 1446 Android-host, 0 failures**. That full run is also
    the first time `iosSimulatorArm64Test` has executed since the attachment work began, which
    is why the two iOS breaks above went unnoticed — `jvmTest` alone never sees them.
- **Next step:** none outstanding for attachments. Both follow-ups the spec carried — the
  global sweep (#20) and the backfill enqueue (#21) — are shipped and green.
- **Done — outbox ordering (follow-up bug found after #21):** a create-time attach was
  dead-lettered before it was ever pushed.
  - **The defect.** `OutboxDao.claimable` ordered `createdAt ASC, id ASC`, and an outbox id is
    `entity:entityID:operation:uuid`. On a shared instant the id leads with the *entity name*,
    so `attachment` (and `comment`) sorted ahead of the `workItem` they name — the exact wedge
    the query's own KDoc claims it prevents. A create enqueues `workItem` and `project` at one
    `now`, then the flushed attachment at a second; when both calls land on the same
    millisecond the attachment was claimed first, the server's `resolveProject` found no work
    item through it and answered `rejected` (`change does not name a project`), and `settle`
    dead-letters a rejection on its first attempt. The task reached the database and the file
    never did, silently. Reproduced deterministically under the fixed clock — claimed order
    `[attachment, project, workItem]`. The edit path was never affected: the work item is
    already on the server by the time the file is attached.
  - **The fix.** A rank tie-break between `createdAt` and `id`, in both `claimable` and
    `deadLetters`: project, sprint, work item, then its comments and files — the dependency
    order the server applies. Insertion order would not have worked: `createWorkItem` enqueues
    the work item *before* the project it must follow, so only the alphabetical accident keeps
    that pair right today.
  - **Tests:** 1 in `OutboxQueueTest` (project/workItem/comment/attachment at one instant,
    parents first), mutation-checked red without the ORDER BY change — it reported
    `[attachment, comment, project, workItem]`.
  - **Baseline:** regenerated after a green three-host run — **1820 jvm / 1769 iOS / 1475
    Android-host**. The Android host is unchanged because it runs no `:packages:database` tests.
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
