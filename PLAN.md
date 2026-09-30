# TaskTrackerKMP — Port Plan

Resumable task list for porting `/Users/amirreza/Desktop/SampleProjects/TaskTracker`
(SwiftUI/SwiftData, 165 files, 13 SwiftPM packages, 395 unit + 10 UI tests) to
Kotlin Multiplatform.

**Every task below is a checkbox. Tick it when it is done. Start any session by
finding the first unticked box.**

## Where things stand

Original: iOS 26, Swift 6 strict concurrency, SwiftData, Swift Testing, XcodeGen.

Port: 22 Gradle modules. **Implemented**: `:packages:core`, `:packages:domain`,
`:packages:di`, `:packages:design-system` (colour tokens, accent, strings),
`:packages:routing`, `:packages:keychain`, `:packages:user-defaults`,
`:packages:database`, `:packages:network`, `:packages:sync`,
`:packages:feature-projects-domain`, `build-logic`. **Empty**:
the eight remaining feature modules plus most of `:androidApp` and `:iosApp`.

**Progress: Phases 0-8 complete, `./gradlew check` green on all three targets.**
Phases 9-15 outstanding. Test baseline **636 unique in commonTest** (feature-projects-data
152, feature-projects-domain 80, sync 70, network 88, database 60, core 42, domain 43,
routing 49, di 19, keychain 12, user-defaults 12, design-system 6, composition-root 3)
plus **14 in `jvmTest`** (database 6: reflection and filesystem; sync 8: a real
SQLite-backed outbox, which has no common equivalent): **650 on `jvmTest`, 636 on
`iosSimulatorArm64Test`, and 424 on `testAndroidHostTest`** — see the Phase 4 note on
why the database's 60 do not run there, and the Phase 8 note on why the feature's 152 do
not.

## Decisions taken

- **Navigation** — hand-rolled `Router` ported verbatim as pure state in
  `:packages:routing` (a `library` module with no Compose dependency, so it cannot
  hold composables). `:shared-ui` owns a Compose navigation host that reads it. No
  third-party nav library. Mirrors `AGENTS.md:575-596` in the original.
- **Strings** — extracted to `composeResources/values/strings.xml` in
  `:packages:design-system`. It is the only module allowed to configure
  `compose.resources` (two modules doing it collide at dex merge). The original
  has **zero** localization; this is a deliberate fix of that gap.
- **iOS shell** — XcodeGen-generated `iosApp/project.yml`, iOS 26, `tasktracker://`
  scheme, mirroring the original's `project.yml`. Built late so Android gives fast
  feedback.
- **Backend** — none. `MockAuthRemote` is ported and a `MockSyncTransport` is
  written alongside it, so the full push/pull cycle is exercisable offline.
- **Tests** — all 395 ported alongside their module, not deferred.

## Non-negotiable invariants (from the original's design)

These are the four sync invariants, the transaction atomicity, and the ordering
rules. Each has already caused a real bug when broken in the original.

1. **One transaction writes both** the record and its outbox row. A two-step
   version leaves a hole where a crash strands an edit with nothing queued.
2. **Pulls never enqueue.** A `SyncApplySink` that queued what it applied would
   make every pull generate a push, forever.
3. **Push is ordered and oldest-first**, and a retryable verdict *stops the push
   loop* for the cycle. Claiming past it would break the ordering the queue exists
   for.
4. **Status history is local-only** and never enqueued.
5. **Verdicts are matched by entry id, never by position.** An unmentioned change
   is treated as retry, never accepted. A repeated id invalidates that change's
   verdict entirely.
6. **Session-ended mid-push releases, never fails.** One sign-out must not push a
   user's unsaved work over the dead-letter limit.
7. **`resolvedAt != null` exactly when `isTerminal`**, enforced on the *read* path
   as well as the write path.

## Rules that apply to every task

- `:packages:design-system` is the **only** module that configures `compose.resources`.
- One composable per file; file named after the composable. A `private` sub-composable
  in the file that renders it is the only exception.
- No `Material3`. The original is not a Material app.
- Every file opens with a multi-paragraph KDoc explaining *why* and naming the
  rejected alternative. This is the established convention in the four files that
  already exist.
- Test names are backtick full sentences. **No commas in test names** — Kotlin/Native
  rejects them.
- `verifyArchitectureGraph` uses **exact-set** matching: a new declared dependency
  without a matching edit to `allowedLibraryTable` in
  `build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts` fails
  the build. Missing dependencies are violations too.
- Never write an unbounded `for (x in flow)`. The original records an intermittent
  SIGSEGV at teardown from exactly this.
- Every `expect` needs a `jvm` actual, because the convention plugin adds a `jvm()`
  host target and runs `commonTest` there.
- **`@Throws` on a `suspend` declaration must list `CancellationException`.** Kotlin/Native
  rejects the annotation otherwise, and only Native rejects it — so this reaches
  `./gradlew check` and not the module you are working in, which is the expensive way to
  find it. The second class is also the honest contract: a suspended save that was
  cancelled is not a failed one, and a `catch (e: AppError)` that swallowed it would
  report a user-facing error for a screen the user already closed.

## How to build

```bash
# This machine has no system JDK. The project needs 25, which Android Studio bundles.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew check                        # 3 targets + both architecture guards
./gradlew verifyArchitectureGraph      # 22 modules, all edges allowed
./gradlew verifyModuleImports          # 22 modules, one task each
```

Current test baseline: **636** unique tests in `commonTest` (`:packages:feature-projects-data`
152, `:packages:feature-projects-domain` 80, `:packages:sync` 70, `:packages:network` 88,
`:packages:database` 60, `:packages:core` 42, `:packages:domain` 43,
`:packages:routing` 49, `:packages:di` 19, `:packages:keychain` 12,
`:packages:user-defaults` 12, `:packages:design-system` 6, `:composition-root` 3), plus 14
more in `jvmTest` only. That is **650** executions on `jvmTest`, **636** on
`iosSimulatorArm64Test` and **424** on `testAndroidHostTest`. Update this number in the
same change as any test addition.

The exit criteria in Phases 8-13 are forecasts off this baseline, and they were written
against a 222-test baseline that the port passed in Phase 4 — so they have been rebased
once, upward, by the same 182 tests the earlier phases actually added. They are counts to
check a phase against, not targets to hit exactly; the per-phase notes record what was
really written where it differs.

### Kotlin/Native constraints hit while writing tests

- **`.` and `,` are illegal inside backtick test names.** Kotlin/Native rejects them.
  `"clears 4.5 to 1 against white"` does not compile; write
  `"clears the text contrast bar against white"`.
- **`String.format` is JVM-only.** It is not in `commonTest` scope and does not compile
  for Kotlin/Native. Format numbers by hand.
- Truncating a `Color` channel with `.toInt()` yields 0 for anything under 1.0, because
  the channel is a 0-1 `Float`. Scale by 255 first.

---

## Phase 0 — Build foundation

- [x] **0.1** Apply `org.jetbrains.kotlin.plugin.serialization` in
      `build-logic/convention/src/main/kotlin/tasktracker.kmp.library.gradle.kts`.
      Now also required in `gradle/libs.versions.toml` as
      `kotlin-serialization-gradlePlugin` and on the convention's compile classpath.
- [x] **0.2** Add `kspJvm` to `tasktracker.kmp.room.gradle.kts`.
- [x] **0.3** Add `ktor-client-darwin` (iosMain) and `ktor-client-okhttp`
      (androidMain + jvmMain) to `packages/network/build.gradle.kts`. Uses
      `add("iosMainImplementation", ...)` because a platform source set with no
      directory yet gets no generated accessor.
- [x] **0.4** Created
      `packages/design-system/src/commonMain/composeResources/values/strings.xml` with
      the status / priority / tab / connectivity / sync / error vocabulary, extracted
      from the original's literals. Screen-level strings are added in Phases 11-13.
- [x] **0.5** Added `Accent.kt` with `ttAccent()` and `TaskTrackerAccent.Light` /
      `.Dark`, taking the values from the original's `AccentColor.colorset`
      (`#34537A` light, `#5F6873` dark).
- [x] **0.6** `AccentContrastTest` — 6 tests, asserting the token values directly
      instead of reading a colorset off disk. **Contrast is measured per appearance**:
      each accent is checked against the canvas it actually appears on. Checking the
      light accent against a dark canvas is not stricter, it measures a combination
      that cannot occur, and it fails a correct colour.
- [x] **0.7** Added `.gitignore`. The generated `iosApp/TaskTracker.xcodeproj` is
      ignored, as `project.yml` is the source of truth.
- [x] **0.8** `iosApp/project.yml`, `Info.plist`, `Assets.xcassets`, and the
      `AppUITestCase` base. `xcodegen generate` verified.
- [x] **0.9** *(not originally planned — found during 0.3)* **Call
      `applyDefaultHierarchyTemplate()` in `tasktracker.kmp.library`.** This was a real
      bug: without it the plugin registers only per-target source sets
      (`iosArm64Main`, `iosSimulatorArm64Main`), so **no `iosMain` existed at all** and
      `packages/design-system/src/iosMain/.../Colors.ios.kt` had never been compiled by
      anything. The module built clean for iOS with no implementation of its `expect`
      colour declarations. Now fixed, and the iOS target compiles and tests.

**Exit criteria**: `./gradlew check` green; `ttAccent` resolvable on all four targets;
`Res.string.*` compiles; `xcodegen generate` succeeds. **All met — 37 tests green.**


---

## Phase 1 — `:packages:core` + `:packages:domain`

Pure Kotlin, no platform dependencies, everything else depends on them. Port
`Packages/TaskTrackerCore` (10 files, 813 lines) and
`Packages/TaskTrackerDomain` (7 files, 1,065 lines).

### `:packages:core` — `com.tasktracker.core`

- [x] **1.1** `AppError.kt` — 12 cases, as a `sealed class` **extending
      `Exception`**, not a returned value. The original throws; a value would change
      every signature and, worse, the failure mode — a thrown error is caught once at
      the boundary, a returned one must be checked by every caller. Detail-carrying
      cases name their property `detail`, not `message`, because `Throwable.message`
      is the *visible* text and a shadowing property would make
      `Server(500, "boom").message` return the fragment while the banner showed
      "Server error (500). boom". `isRetryable` / `requiresSignOut` /
      `isConnectivityFailure` are total `when`s with no `else`, so adding a case
      without classifying it is a compile error. `logText` is the compact form for
      logs, kept separate from the user-facing text.
- [x] **1.1a** Error text lives in `AppError`, **not** in `strings.xml`. `:packages:
      core` is allowed no project dependencies at all, so it cannot read `Res`;
      routing these through `:packages:design-system` would invert the layering to
      serve a string, and duplicating them would create a second source of truth
      that no module can consume. The `error_*` strings were deleted from
      `strings.xml` and a comment left in their place explaining why.
- [x] **1.2** `Identifiers.kt` — `@JvmInline value class` over `kotlin.uuid.Uuid`
      (needs `@file:OptIn(ExperimentalUuidApi::class)`, and an explicit
      `import kotlin.jvm.JvmInline` — it is only auto-imported on JVM, so without it
      the **Kotlin/Native** compile fails while JVM succeeds). Transparent
      serialization gives a bare JSON string, which is what the server expects.
      `parse*ID` returns `null` rather than throwing: IDs arrive from deep links and
      clipboard pastes, which are untrusted, and a `Result` around a non-`AppError`
      throwable would leak past the data layer's contract.
- [x] **1.3** `Broadcaster.kt` — replaces `AsyncBroadcaster`. A `Channel` per
      subscriber, exactly the original's continuation dictionary. **Not**
      `MutableSharedFlow`: it has no `complete()`, so [finish] is inexpressible and a
      view subscribing after a sign-out would wait forever on a stream that can only
      be silent. Two bugs found and fixed while porting, both of which the KDoc had
      claimed were already handled:
      - the unregister sat *after* the collect, so a cancelled collector leaked a
        queue that nothing drains — now in a `finally`;
      - a subscriber registering *after* `finish()` got a live channel that nothing
        would ever close — now gated on a `finished` flag and returns an empty flow.
      Uses `flow { }` + `consumeAsFlow`, not `callbackFlow`, which requires the block
      to end in `awaitClose` and so throws on the normal completion path.
- [x] **1.4** `DateCoding.kt` — ISO-8601 UTC at **whole-second** resolution, on
      `kotlin.time.Instant`. **Not** `kotlinx.datetime.Instant`: in kotlinx-datetime
      0.7+ it is a deprecated typealias for `kotlin.time.Instant`, so importing it
      would put a deprecation warning in every downstream module. Whole-second
      truncation is `Instant.fromEpochSeconds(epochSeconds)`, not `truncatedTo` —
      which takes a `DateTimeUnit` while `kotlin.time.seconds` is a `Duration`.
      `kotlinx-datetime` was dropped from this module as a result; nothing needs it.
- [x] **1.4a** `JSONCoding.kt` — the `Json` instances, plus `InstantWireSerializer`.
      Every `Instant` property in a DTO must carry
      `@Serializable(with = InstantWireSerializer::class)`: kotlinx ships a built-in
      `Instant` serializer that emits fractional seconds, and it is picked
      **silently**, so a forgotten annotation produces a build that succeeds and a
      wire format that quietly differs from the server's. There is no way to make it
      the project default — a `SerializersModule` entry only applies to `@Contextual`.
      `ignoreUnknownKeys` is **off**: a field renamed on the server has to fail at
      the boundary as `AppError.Decoding`, not decode as `null` on every device.
- [x] **1.5** `JWT.kt` — claims-only inspection, **no signature verification**; the
      client only needs `exp` to know when to refresh a token it did not mint.
      `Base64.decode` requires padding by default *and* rejects the URL-safe
      alphabet, so `decodeBase64URL` translates and restores padding arithmetically
      rather than switching to a globally-lenient config, which would also stop it
      rejecting a genuinely truncated payload. A fractional `exp` is rejected rather
      than truncated.
- [x] **1.6** `Log.kt` — replaces `TTLogger`'s `OSLogPrivacy` mirror with an
      explicit `logMessage { }` builder. Kotlin has no cross-platform
      `ExpressionByStringInterpolation`, and the alternative — a string template that
      cannot be redacted — makes "remember not to log the token" the only safeguard.
      Redaction happens before any sink sees the text, and `sink` is `internal` so
      nothing can hand a raw template to a platform logger and bypass it.
- [x] **1.7** `DateProvider.kt` — injectable wall clock so JWT expiry is testable
      without sleeping. `TestDateProvider` ships in `main`, not `test`, because the
      sync engine's suite lives in a different module from the clock it drives.
- [x] **1.8** `commonTest` — **42 tests** (was 9): date round-trips and lenient
      parsing, broadcaster fan-out/ordering/finish/cancellation, `AppError`
      classification and the visible-vs-log shape split, JWT claims and base64url,
      identifier wire format, coders' strictness.

### `:packages:domain` — `com.tasktracker.domain`

- [x] **1.9** `Entities.kt` — `WorkItemStatus` with three deliberately distinct
      predicates: `appearsOnBoard` (todo/inProgress/inReview/**done**), `isTerminal`
      (done + closed), `isComplete` (**done only**). `Priority` orders by declaration
      so `compareTo` and `sortedBy` are plain ascending iteration. `Project` carries
      `keyPrefix` and `nextItemNumber`, floored at 1 in an `init` — which is why that
      one property is a `var`, since a `val` cannot be normalised there. `WorkItem`
      carries `key`, `rank`, `resolvedAt` and `reconcileResolutionAt()`.
      `WorkItemOrderingKey` is the single definition of item order, shared with the
      persistence layer.
- [x] **1.9a** **`title` and `systemImage` are deliberately gone from the status
      enum.** `:packages:domain` may only see `:packages:core`, so it cannot read
      `composeResources`; putting English labels in the business rules makes the app
      untranslatable and is the failure this port is supposed to avoid. The domain
      exposes `wireValue` instead — one stable string used as the JSON name, the Room
      column value and the deep-link segment — and presentation maps it with an
      **exhaustive** `when`, so a new status breaks every renderer at compile time
      instead of rendering blank. The mapping lands in
      `:packages:feature-projects-presentation` (Phase 12) with the rest of the strings.
- [x] **1.10** `ProjectRules.kt` — name ≤120, details ≤4000, key prefix 2-10
      uppercased alphanumerics. `WorkItemRules`: title ≤255, details ≤8000, assignee
      ≤120, blank assignee → null. **Every rule returns the normalised value**, and
      every length guard uses the existing `graphemeClusterCount`. `canTransition`
      permits all transitions including out of `done`. `WorkItemGrouping` derives
      board/backlog/archive membership as three separate pure functions. The ASCII
      guard in `normaliseKeyPrefix` is an explicit code-point range, not
      `Char.isAscii()` — the latter is stdlib-experimental and would cost this file
      an opt-in for one comparison. `BoardColumn` replaces the original's tuple so the
      column carries a name.
- [x] **1.11** `AuthRules.kt` — email ≤254, password ≤256, display name ≤120. Each
      returns the normalised value so `"  Ada@Example.com  "` cannot become a second
      account. Passwords are still **not** trimmed: a registration policy leaking into
      sign-in is how you tell a returning user their password is too short.
- [x] **1.12** `StatusChange` lives in `Entities.kt` alongside the other entities,
      not in its own file — it is a value type with no behaviour of its own, and a
      one-declaration file per type is noise at this size. Immutable, `from == null`
      means creation, never edited, local-only by design.
- [x] **1.13** `commonTest` — **43 tests**: every normalisation and limit boundary,
      key-prefix casing *and* non-ASCII rejection, the transition matrix, the four
      ordering keys, board/backlog/archive disjointness, the resolution invariant in
      both directions, and a serialisation round trip.
- [x] **1.14** `packages/domain/build.gradle.kts` declares
      `libs.kotlinx.serialization.json` itself. It is **not** inherited from
      `:packages:core`: a KMP `commonMainImplementation` dependency is not `api`, so
      core's own dependency is not on this module's compile classpath, and `@Serializable`
      on a domain entity does not resolve without it.

**Exit criteria**: met. **85 tests** in core + domain (42 + 43, against a planned 66),
`graphemeClusterCount` is the length primitive everywhere, and `./gradlew check` is
green on `jvmTest`, `testAndroidHostTest` and `iosSimulatorArm64Test` with both
architecture guards passing.

**Do not port**: `StreamObservation.swift`. It exists solely because a
`@MainActor deinit` is `nonisolated`. `ViewModel.onCleared()` is a real hook.

---

## Phase 2 — `:packages:routing`

Port `Packages/RoutingService` (4 files, 717 lines). Note: `Router.swift` is
already SwiftUI-free, so it ports directly with no UI dependency. `:shared-ui`
renders it.

- [x] **2.1** `Routes.kt` — `RootDestination` (auth/main), `AppTab`
      (projects/board/backlog/settings with `isProjectScoped`), `AppRoute` sealed
      hierarchy (board, backlog, archive, workItemEditor, settings), `DeepLinkScheme`.
      Two accessors on `AppRoute`, deliberately not one: `projectIDOrNull` (every
      project-scoped case, used by `currentProjectID`) and `selectsActiveProject`
      (board/backlog/archive only). The editor is project-scoped but must **not**
      become the active project — a deep link to an item in another project scopes the
      screen without moving the tabs out from under the user. `AppTab` keeps `wireValue`
      instead of `title`/`systemImage`, which belong in presentation.
- [x] **2.2** `DeepLinkParser.kt` — a **pure function** mapping `tasktracker://` to
      a `ParsedLink` (tab, optional modal route, stack to install first). Grammar:
      `projects`, `settings`, `board/<id>`, `backlog/<id>`, `archive/<id>`,
      `workitem/<pid>/<iid>`, `workitem/<pid>/new`. Archive and work-item links land
      on the **Board** tab, so an archive always opens within a project tab.
      Takes a `String`: common Kotlin has no URL type, so the iOS shell passes
      `URL.absoluteString` and Android `Intent.data.toString()`. The hand-rolled split
      has to do what Foundation's `host` + `pathComponents` did for free — drop an
      empty authority (`tasktracker:///board/x` parses like the two-slash form), strip
      query and fragment, and percent-decode.
- [x] **2.3** `Router.kt` — app-wide navigation state: `root`, `isRestoringSession`,
      `selectedTab`, four per-tab paths, `activeProjectID`. `openProject(id, in tab)`
      is the **only** way into a project-scoped tab, so tab bookkeeping and stack
      contents cannot drift. `restoreLastVisitedProject` takes a **tri-state
      `Boolean?`**: true restores, false drops a project deleted elsewhere, null
      (store read failed) keeps the id. Persists under `routing.lastVisitedProjectID`.
      All fields live in one immutable `RouterState` behind a single `StateFlow`, and
      every transition goes through a `compareAndSet` loop: `push` moves the tab, the
      stack and the active project at once, and a field-per-property port would let a
      reader observe a tab switched to a board that had not been installed yet. `root`
      has no setter at all, which is stronger than the original's `private(set)` +
      comment — a same-module caller could still reach that one.
- [x] **2.4** `commonTest` — 49 tests: the 30 from `RouterTests.swift` plus 19
      covering the areas the hand-rolled URL split introduces (query/fragment,
      percent-escapes, empty authority, case-folding), the two route accessors, and
      `openProject` rejection on Settings.

**Exit criteria**: met — 162 unique tests green (was 113). `enterAuth` keeps
`activeProjectID` deliberately: it belongs to the device, not the session. A redundant
`enterMain` keeps navigation and only re-asserts that the session read landed, matching
the original's early return.

---

## Phase 3 — Keychain + UserDefaults

### `:packages:keychain`

- [x] **3.1** `KeychainAccess.kt` — interface over raw bytes. Every operation suspends so
      the production implementation stays the single serialisation point. A plain
      `interface`, not `expect interface`: [InMemoryKeychain] has to be implementable
      outside this module. `KeychainError` keeps the raw platform `status` as an `Int`
      because neither `OSStatus` nor an Android exception name exists in common code,
      and the reason string is resolved by the platform actual before construction.
- [x] **3.2** Actuals: `iosMain` (`SystemKeychain`, Security framework),
      `androidMain` (`AndroidKeychain`, AES-GCM via `AndroidKeyStore`),
      `jvmMain` (in-memory — this is a test-only host target). Selected by
      `expect fun systemKeychain(host: Any? = null)` rather than an `expect interface`,
      so the compiler proves every target can actually produce a store. `host` is the
      `Context` Android needs and is `Any?` because common code cannot name it; the
      Android actual throws an `IllegalArgumentException` naming the fix rather than
      letting a bare `ClassCastException` out of the cast.
- [x] **3.3** `KeychainStore.kt` — `KeychainKey` enum plus a typed store with
      `Codable` get/set and `removeAll`. The `Codable` conveniences are `reified`
      extensions rather than members, which is why `data`/`setBytes` are public — an
      `inline` function cannot reach a private member.
- [x] **3.4** `InMemoryKeychain.kt` — test/preview stand-in that also supports
      **fault injection**, because the interesting auth paths are the ones where the
      keychain fails. Guarded by a `Mutex`: the `suspend` contract lets two coroutines be
      inside `data` at once, so a plain `MutableMap` would be a data race that only
      shows up on a device. Bytes are copied on the way in and out, so a caller mutating
      the array it passed cannot rewrite what the store holds.
- [x] **3.5** `commonTest` — 12 tests: round-trips, removal, fault injection.
- [x] **3.6** `DefaultsKey.kt` — `LastSeenProject`, `BoardShowsBacklog`,
      `HasSeenSyncHint`, `NetworkSimulation`, `CachedProfile`, with `com.tasktracker.`
      namespacing. `CachedProfile` is explicitly non-sensitive, and a test asserts it
      stays clear of the two credential key names.
- [x] **3.7** `UserDefaultsStore.kt` + a `PreferencesBackend` byte layer with four
      actuals (`NSUserDefaults` / `SharedPreferences` / `Properties` / in-memory). The
      backend split is not in the original: its tests ran on macOS against a throwaway
      `UserDefaults` suite, and there is no equivalent of that in `commonTest`. With a
      byte layer the typed store is testable on every target. `hasValue` asks the
      *backend*, not the decoder, so a value that no longer decodes still counts as
      present — settings needs to tell "chose the default" from "never touched it".
- [x] **3.8** `commonTest` — 12 tests: namespacing, `Codable` round-trips, `hasValue`,
      the corrupt-value path, and a `false` boolean surviving the round trip as a real
      value rather than reading back as an absence.

**Exit criteria**: met — 183 unique tests green (was 162).

Two asymmetries that are deliberate and each have a test pinning it:

- **A corrupt keychain payload throws; a corrupt preference returns `null`.** A keychain
  entry exists only because the app wrote it, so unreadable bytes are a real fault and
  the auth layer must see them — returning `null` would sign the user out silently and
  look like a session that merely expired. A UI preference, by contrast, is recoverable
  from its default.
- **The platform factories are not behaviourally testable from `commonTest`**, because
  the three actuals deliberately differ (JVM in-memory, iOS real, Android needs a
  `Context`). What is asserted everywhere is only that the wiring exists and that a
  missing `Context` is reported usefully. The iOS and Android backends are
  **compile-verified only**; their runtime behaviour is unproven until Phase 14 runs a
  real build.

---

## Phase 4 — `:packages:database` (Room KMP)

The largest single chunk. Room 2.8.4 + `sqlite-bundled` + KSP, per
`tasktracker.kmp.room`. `schemaDirectory` points at `$projectDir/schemas`, committed so a
migration diff shows in review.

- [x] **4.1** `schemas/` directory + Room entities. **There is no `ModelSchemaRegistry`**
      and there cannot be one: `@Database(entities = [...])` is read by the annotation
      processor at compile time. One `@Database` per feature would be worse than no
      registry — a repository has to write its entity *and* the outbox row in one
      transaction, which is only possible when both tables are in the same database, and
      the queue disagreeing with the store is the exact failure the outbox exists to
      prevent. The dependency still points one way; adding a feature table is a one-line
      edit to `entities` when phases 8 and 11 land. The three cross-cutting tables are
      `OutboxRecord`, `SyncMetadataRecord`, `SessionRecord`, as in the original.
- [x] **4.2** `OutboxRecord` — `OutboxOperation` (upsert/delete), `OutboxEntity`
      (project/workItem), `OutboxStatus` (pending/inFlight/failed/dead), plus `id`,
      `entityKind`, `entityID`, `operation`, `payload`, **`userID`**, `createdAt`, `status`,
      `attemptCount`, `lastAttemptAt`, `lastError`. The original's warning is kept as a
      comment on `entityKind`. The three state columns are `String`, not enum-typed, so a
      value from a newer build is *readable* and refused with an `OutboxError` at a point
      that can name the row, rather than blowing up inside a `TypeConverter`.
- [x] **4.3** `inTransaction { }` — mutate, commit once, **roll back on any throw**.
      Room's KMP `commonMain` has no `withTransaction`; the primitive is
      `useWriterConnection { it.immediateTransaction { … } }`, which is a real `BEGIN`/
      `ROLLBACK` and nests via savepoints. `IMMEDIATE` rather than `DEFERRED` because
      `claim` reads then writes, and a deferred transaction that upgrades can fail
      mid-flight. This is strictly better than `ModelContext.rollback()`: the rollback is
      the transaction's own behaviour and cannot be forgotten.
- [x] **4.4** `OutboxQueue.kt` — `claim(limit, maxAttempts, userID)` oldest-first with an
      id tie-break, then `complete` / `fail` / `reject` / `release` /
      `recoverStrandedEntries` / `entries` / `count(status:)` / `removeAll`. `release` and
      `fail` are different operations and the difference is load-bearing. `reject` is a
      default *method*, not an extension: Kotlin members shadow extensions, so the
      original's same-signature protocol extension would be dead code rather than a
      compile error. The scoped and unscoped claims are one `@Query` (`:userID IS NULL`),
      where the original needed two fetch descriptors.
- [x] **4.5** `SyncMetadataStore.kt` — **one row per scope string** (`"projects"`,
      `"workItems:<projectID>"`) so a partial sync of one project does not advance the
      whole app's position. Plus `SessionCache`, whose rows are stamped on outbox entries
      and on status changes. `Instant` is stored as epoch **milliseconds**, the granularity
      the original's "same millisecond" tie-break assumed.
- [x] **4.6** An in-memory store **must** use the in-memory builder. `DatabaseConfiguration.inMemory`
      carries an empty name, so there is no path a caller could pass by mistake; Room
      rejects a blank name and `:memory:` outright. The original pointed a file-backed
      store at `/dev/null` and lost a build to real SQLite sidecars.
- [x] **4.7** `commonTest` — **60 tests**: claim ordering and the id tie-break, attempt
      accounting, dead-lettering at `maxAttempts`, stranded recovery, user-scoped
      claiming, release-vs-fail semantics, per-scope cursor independence, `IN ()` guards,
      and transaction rollback leaving neither table's row behind. Plus 6 in `jvmTest`:
      the structural "no token in `SessionRecord`" guard (reflection) and on-disk location
      behaviour (a real file-backed store, directory creation, an unusable location).

### Phase 4 notes

- **`testAndroidHostTest` is disabled for this module** (see `build.gradle.kts`). Room's
  Android builder is `databaseBuilder(context, klass, name)` and needs a real `Context`;
  a host JVM test cannot produce one, because the `android.jar` on that classpath is a
  stub that throws `Stub!` from its constructors. Every test here opens a store, so leaving
  it on fails the whole suite for one wiring reason. The android target is still fully
  compiled, generated code included. Robolectric is the fix if this module ever wants
  host-side Android coverage — a new dependency, so not a call to make inside a phase
  about transaction semantics.
- **What Room's KMP API does not have**: `withTransaction` and the `Room` builders are not
  in `commonMain`, so the builder is an `expect`/`actual` (Android additionally needs a
  `Context`, threaded as `host: Any?`); `@Database` needs `@ConstructedBy` off Android,
  which means declaring an `expect object … : RoomDatabaseConstructor<T>` — and it must be
  declared *before* the `@Database` class, or KSP fails with `MissingType`. `Dispatchers.IO`
  is `internal` in coroutines' common source set, so the query dispatcher is an
  `expect`/`actual` too.
- **Runtime caveat**: the android and iOS builders, `createParentDirectory`, and the
  `NSUserDefaults`-free path resolution are compile-verified only, as with the keychain
  and user-defaults actuals. Nothing about the *transaction semantics* is unverified —
  those run on both the JVM and an iOS simulator.

**Exit criteria**: met — **246 common + 6 jvmTest** (was 186 measured, not the 183 the
plan had been carrying; Phase 3's own count was off by three). `packages/database/schemas/`
committed.
---

## Phase 5 — `:packages:network` (Ktor 3.6.0)

- [x] **5.1** `HttpRequest.kt` — transport-agnostic value type: method, path, query,
      headers, body, `requiresAuth`, `timeout`, `isIdempotent`. **Not a data class**,
      because the generated `equals` compares a `ByteArray` body by identity: two
      requests built separately from the same JSON would be unequal. Hand-written
      `equals`/`hashCode`/`copy`, the same trap `:packages:database` hit with outbox
      payloads. `toString` prints the body's *length* — a sign-in body is a POST, and this
      value is logged on every retry.
- [x] **5.2** `HttpResponseSummary.kt` — `isUnauthorized` / `isForbidden` /
      `isServerError`, and `isSuccess` as **2xx exactly** (a 3xx is not a success and must
      not reach a decoder expecting the final body).
- [x] **5.3** `HttpClient.kt` — the interface, so feature Data depends on a capability and
      not on Ktor. `KSerializer` is passed explicitly where the original relied on
      `Encodable`/`Decodable` inference; the reified seams are `jsonBody` and `post`.
- [x] **5.4** `KtorHttpClient.kt` — `expect` engine factory with `OkHttp` actuals for
      JVM/Android and `Darwin` for iOS (Foundation's `URLSession`, so the iOS transport is
      the original's unchanged). **No `HttpResponseValidator`**, contrary to the plan, and
      the reason is the auth design: validation throws on a non-2xx, and `AuthInterceptor`
      has to *see* the 401 to decide between "refresh once and replay" and "end the
      session". `expectSuccess = false`, and validation happens in `validated()` after the
      interceptor chain — which is also simply what `URLSession` did. Status→`AppError` and
      exception→`AppError` both live in `HttpErrorMapping`, with the `{message|error}`
      envelope and its 200-character cap. The `sleeper` is injected, so the retry tests
      assert the *delays asked for* (400ms, 800ms) instead of stopping a stopwatch.
- [x] **5.5** `RequestInterceptor.kt` — `adapt` / `retry` and `InterceptorDecision`.
      `AuthSessionDelegate` is declared **by its consumer**, which is why `TokenVault` will
      conform to it in `:packages:feature-auth-data` and `:packages:network` never depends
      on auth. `adapt`/`retry` are interface methods with defaults rather than protocol
      extensions with defaults, for the same reason `OutboxQueue.reject` is a default
      method in Phase 4: **a Kotlin member shadows a same-signature extension**, so the
      original's shape would have been silently dead code.
- [x] **5.6** `AuthInterceptor.kt` — on **403** the session **terminates** (a fresh token
      cannot help). On **401**, at most one refresh and one replay; a 401 *after* the
      replay ends the session rather than burning a second refresh. The refresh's `catch`
      rethrows `CancellationException` first — the original's bare `catch` covers that too
      only because Swift has no cancellation, and catching it would sign the user out on
      the way out of a cancelled screen.
- [x] **5.7** `NetworkMonitor.kt` — the plan sketched an `expect class`; the seam is
      instead `expect fun createConnectivityWatcher(host)` behind a two-method
      `ConnectivityWatcher`, because path classification, de-duplication, the override and
      the stream all belong in common code where they are testable, and each platform
      contributes only what it alone can know. Android:
      `registerDefaultNetworkCallback` + `NET_CAPABILITY_VALIDATED` (INTERNET alone is
      exactly what a captive portal advertises), with `ACCESS_NETWORK_STATE` declared in
      the *module's* manifest. iOS: `NWPathMonitor`, which is also the only path API that
      distinguishes `satisfiable` from `unsatisfied`. JVM: always `Satisfied`, because
      there is no portable way to ask a desktop JVM and guessing "offline" would park a
      queue that could have drained. `MutableStateFlow`, **not** core's `Broadcaster`:
      connectivity is a level, not a stream of events, so conflating is right and
      replay-on-subscribe is what lets a screen opened while offline render immediately.
      `setOverride` is what makes the Settings "Force Offline" switch honest on a
      simulator. `Connectivity.isKnownUnusable` is separate from `allowsSync` so a cold
      start (`Unknown`) does not fail fast and park every request.
- [x] **5.8** `RetryPolicy.kt` — exponential backoff with full jitter in
      `[exponential/2, exponential]`, and **method-aware**: a POST is never replayed on a
      transport error unless it opts in via `isIdempotent`. `2^(attempt-1)` is computed in
      integer arithmetic so a large `maxRetries` cannot overflow. The `Random` is injected
      so the jitter band is asserted against a seed rather than by luck.
- [x] **5.9** `commonTest` — **88 tests**, run on all three targets: the status table and
      its fallbacks, envelope and non-JSON message extraction, the 200-character cap, the
      idempotent-POST replay rules, exact backoff delays, the full 401/403/refresh matrix
      including the concurrent three-calls-each-401 case, URL building (base path, per-
      segment encoding, escaped query values), content-type handling, cancellation not
      being reported as a network failure, and the monitor's override/dedupe/replay rules.
      `ktor-client-mock` replaces `StubURLProtocol`, keeping the original's two load-
      bearing decisions: scripts keyed by path (a flat FIFO makes the concurrent test
      prove nothing) and a 500 for any call past the end of a script (so an accidental
      replay loop fails instead of passing).

### Phase 5 notes

- **`.offline` is not guessed from a connection failure.** The original mapped
  `URLError.notConnectedToInternet` to `.offline` and a DNS failure to `.transport`;
  those are distinguishable on Darwin (`NSURLErrorNotConnectedToInternet` vs
  `NSURLErrorCannotFindHost`) and *not* on OkHttp, which raises `UnknownHostException` for
  both. A shared mapping must pick one, and both options lie: guess `Offline` and a user
  with a DNS problem watches a banner that fixing their DNS will not clear; guess
  `Transport` and the honest offline state is lost everywhere. So `.offline` is claimed
  only where it is a **positive** statement — the monitor reporting an unusable path, which
  the client turns into a fast local failure with no round trip — and a failed connection
  stays `.transport`, which renders a "try again" affordance rather than a diagnosis.
  Cost: on iOS, a DNS failure shows "try again" where the original would have shown the
  offline banner. That is the wrong way round to be wrong.
- **No `ContentNegotiation`, no Ktor `Logging`** — both would be a *second* place doing a
  job `:packages:core` already owns: a second `Json` with its own leniency, one call away
  from the strict one that makes `AppError.Decoding` reachable, and a second path to the
  log that bypasses the redaction in `logMessage`. The two events that matter (replay
  after refresh, session end) go through `TTLogger`.
- **Architecture**: `:packages:network` depends on `:packages:core` only. The table said
  `core` + `domain`; the module uses no domain type, and the graph check *enforces* every
  declared edge, so the rule was widened to the truth rather than a dependency added to
  satisfy it. A transport that starts naming domain types is deciding what a request
  *means*, which is the repositories' job one layer up.
- **Ktor 3.6 API notes, all found by the compiler**: `HttpRequestBuilder.method` is Ktor's
  own `HttpMethod` enum, so this module's enum is spelled `io.ktor.http.HttpMethod.parse`;
  the mock engine's byte overload is `respond(content = …)`, not `respondBytes`; and
  `ContentType.parse` throws `BadContentTypeFormatException`, which extends `Exception`
  and not `IllegalArgumentException` — the one Ktor type that breaks the convention, on
  every platform.
- **`Content-Type` rides on the body, not on a header.** Ktor renders
  `OutgoingContent.contentType` into the request at send time, so a hand-set header is
  either dropped or one of two values. `MockEngine` stops short of that step, so the
  observable fact in a test is `request.body.contentType` — worth knowing before a test
  "fails" on a header that a real engine would have written.
- **Compile-verified only**: the Darwin and OkHttp engine factories, `NWPathMonitor`'s
  cinterop, and `ConnectivityManager`'s callback. Tests inject `MockEngine` and a fake
  watcher, so no test in this phase opens a socket. Same caveat as the keychain and
  database actuals.

**Exit criteria**: met — **88** (all common, all three targets; the plan's 32 was a
guess). The 401/403 rules, the idempotency rule and the retry ladder are verified against
a real Ktor client on the JVM and on an iOS simulator.

---

## Phase 6 — `:packages:sync`

- [x] **6.1** `SyncScope.kt` — a scope is **not** an entity type: a workItems change
      may reference a project. Plus `SyncTrigger` (appLaunch/foreground/localWrite/
      periodic/manual/connectivityRestored), `SyncPhase` and `SyncStage`.
- [x] **6.2** `SyncStatus.kt` — `SyncReport` (pushed, accepted, conflicts, rejected,
      retrying, applied, queueDepth, deadLetterCount, failures, requiresSignOut,
      `isSuccess`/`isDeferred`) and `SyncStatus`. **Dead-lettering is not a failure.**
- [x] **6.3** `SyncConfiguration.kt` — pushBatchSize 50, maxPushBatchesPerCycle 10,
      maxAttempts 5, pullPageSize 100, maxPullPagesPerScope 10, plus `SyncSchedule`
      (60s interval). Every bound exists so a month-long backlog trickles out
      instead of running until the app is killed.
- [x] **6.4** `SyncTransport.kt` — `PushOutcome` (`.accepted` / `.conflict` /
      `.rejected` / `.retryLater`; **conflict is a success for the queue**),
      `PushResult` (carries `entryID` so ordering cannot be assumed),
      `RemoteChange`, `PullPage`, `SyncTransport` (push **in the order given**),
      `SyncApplySink` (idempotent, server-wins).
- [x] **6.5** `SyncEngine.kt` — the core. Fixed order: **recover stranded → push
      oldest-first in order → pull each scope from its cursor → finalise**. Push-first
      because the pull only reflects what the server has. Serialised by a `Mutex`
      (two concurrent cycles would double-push). A retryable failure **stops the push
      loop** for the cycle. Entries claimed but already attempted this cycle are
      `fail`ed rather than released — the claim charged an attempt, or they strand
      invisible until next launch. Verdicts matched **by entry id, never position**.
      An unmentioned change is retry, never accepted. A repeated id invalidates that
      change's verdict entirely. Session-ended mid-push **releases**. Never throws —
      returns a `SyncReport`.
- [x] **6.6** `HttpSyncTransport.kt` — all paths `/v1`-prefixed and defined in one
      place: `POST /v1/sync/push`, `GET /v1/sync/changes/{scope}`.
- [x] **6.7** `MockSyncTransport.kt` — **new**, no counterpart in the original,
      because there is no backend. Backs the whole cycle offline. Shipped as
      `InMemorySyncTransport`, which echoes pushed changes into the change log so a
      round trip is observable without a server.
- [x] **6.8** `SyncCoordinator.kt` — decides **when** to sync and enforces
      single-flight across the timer, app lifecycle and the connectivity monitor.
      `synchronize(trigger)` returns null for both "already running" and "offline".
      `stop()` is called by teardown.
- [x] **6.9** `commonTest` — 41 tests: recover/push/pull order, oldest-first claiming,
      stop-on-retryable, per-cycle attempt dedup, id-matched verdicts,
      conflict-is-success, duplicate verdict invalidation, session-ended release,
      per-scope cursor advance, retry-whole-page, single-flight, transport paths.

**Notes**

- **`allowsSync` moved to `:packages:sync`, where the original has it.** The original
  asks `monitor.current.allowsSync` in `SyncCoordinator`; Phase 5 had put the predicate
  on the network value, which put a *sync policy* inside the transport layer. `:packages:
  network` now exposes `isKnownUnusable` (a fact: this path cannot carry traffic) and the
  coordinator owns `allowsSync = !isKnownUnusable` (a decision). The two differ in one
  case, and it matters: `NWPathMonitor` reports `Unknown` before its first callback, so
  reading `Unknown` as "offline" would leave a perfectly connected device sitting on a
  queue of unsynced work until the next launch. `Unknown` syncs; `Unsatisfied` and
  `RequiresConnection` do not.
- **`NetworkMonitor`'s constructor is public, with a `forWatcher` factory.** It was
  `internal`, which would have left a consumer able to *see* `ConnectivityWatcher` and
  never supply one. `:packages:sync`'s coordinator tests must not depend on the machine
  having a network, and cannot use `forHost` because on Android that demands a `Context`.
  The monitor is still started by the composition root, not the coordinator — the
  original's split, and the app shares that one monitor with `:packages:routing`.
- **The queue double is checked against the real queue.** The engine's `commonTest` runs
  against a hand-written `InMemoryOutboxQueue`, which is a bet: that it behaves like
  `RoomOutboxQueue` for every transition the engine drives. A double that is *almost*
  right makes the whole suite pass while asserting nothing. `RoomBackedSyncTest` (8
  tests, `jvmTest`) runs the same script through both and compares the rows. Writing it
  immediately corrected three documented beliefs: there is **no time-based backoff** in
  `claimable` (a row that failed a moment ago is handed straight back, which is why
  `SyncEngine` needs a seen-this-cycle guard), the order is `createdAt` then `id` even
  when the enqueue order is the reverse, and `pushed` counts what was *offered*.
- **`SyncCoordinator` is not an actor.** The original is a Swift `actor`, which forced
  every read into an `await` and made the phase callback a capture of `self`. The
  observable state is a `MutableStateFlow`, so `currentStatus` is a plain property and
  the callback is a lambda with nothing to retain. `statusUpdates()` returns the
  `StateFlow` itself: the original dropped a status identical to the current one
  (`guard next != status`), so conflating loses nothing, and replaying the current value
  is what lets a screen opened mid-cycle render the phase immediately.
- **A missing sink fails the cycle.** If a scope's `SyncApplySink` is absent, the engine
  reports a failure for that scope rather than skipping the page. The original had a
  silent `else`; skipping means the cursor advances over changes that were never
  applied, and they are only re-read if the cursor is rewound by something else.
- **Nothing throws out of `run`.** Every store, transport and clock call is caught and
  turned into a `SyncFailure` on the report, because a sync that throws takes the
  caller's coroutine with it — and the callers are a `launch` in a loop, a
  `foreground` event and a connectivity callback.

**Exit criteria**: met — **70** in `commonTest` (all three targets; the plan's 41 was a
guess) plus **8** in `jvmTest` for the real-queue parity described above. `:packages:sync`
depends on `:packages:core`, `:packages:database` and `:packages:network` — **not**
`:packages:domain`, which the original's `Package.swift` does not depend on either; the
architecture table was corrected to match rather than a dependency invented to satisfy it.

---

## Phase 7 — `:packages:feature-projects-domain`

- [x] **7.1** `ProjectRepositoryPort.kt` — the port presentation depends on. Every
      value crossing it is immutable. **Every write returns the value it produced.**
      `ProjectChange` is a **typed, scoped** invalidation event (`.projects` /
      `.workItems` + optional `ProjectID`), not `Unit`, so a view model re-reads only
      when it cares. `statusChanges` returns newest-first. `seedSampleDataIfNeeded` is
      idempotent and writes through the normal outbox path.
- [x] **7.2** `ProjectUseCases.kt` — six use cases, each owning a decision the data
      layer cannot:
      - `CreateProject`
      - `UpdateProject` — deliberately **cannot** change `keyPrefix` (that would
        orphan every issued key). A no-op edit reads instead of writing so
        `updatedAt` does not bump.
      - `CreateWorkItem`
      - `MoveWorkItem` — reads the current status first, so a stale screen gets
        "no longer exists" rather than a bare failure.
      - `SaveWorkItem` — one use case for create-or-update, because a status change
        is **also** a re-rank. The port's update takes no status, which is exactly
        what makes the move easy to forget.
      - `RemoveProject` — deletes only when nothing `isComplete`; otherwise
        **archives**, so a project's record of closed decisions survives.
- [x] **7.3** `Projections.kt` — `BoardProjection` (one column per board status, in
      `boardOrder`), `BacklogProjection` (flat, only `.backlog`, so it can never
      re-list a board card), `ArchiveProjection` (months keyed by month start, with an
      injectable `TimeZone`), `ProjectSummary`. Grouping lives here, not in a
      composable, so board/backlog/archive cannot disagree. `BoardColumn.isEmpty` and
      `.count` moved **onto** the domain's own `BoardColumn` rather than wrapping it in
      a presentation-local copy with the same two fields.
- [x] **7.4** `ArchiveWorkItem.kt` — the guarded move to `.closed`. Refuses an
      already-terminal item rather than no-op'ing, because `done` and `closed` are
      not interchangeable and rewriting one into the other would lie in the history.
- [x] **7.5** `WorkItemPreview.kt` — "is there anything worth expanding to on this
      card", trimming first so a whitespace-only description cannot produce a
      disclosure that opens onto nothing.
- [x] **7.6** `FakeProjectRepository` + `commonTest` — **80** of the 95, in five
      suites: `ProjectUseCasesTest` (26), `ProjectionsTest` (23), `WorkItemPreviewTest`
      (7), `ArchiveWorkItemTest` (7) and `FakeProjectRepositoryTest` (17). The last two
      are the ones that were missing when this phase was picked back up: grouping had no
      tests at all, and the fake's own contract was assumed by the use-case suites rather
      than asserted.

### Phase 7 notes

Three things surfaced here that the original never had to answer.

1. **`@Throws` on a `suspend` declaration is a Kotlin/Native error unless
   `CancellationException` is listed** — and only on Native. `jvmTest` and
   `testAndroidHostTest` compiled all seven use cases without complaint, so the
   annotation looked fine until `iosSimulatorArm64Test` rejected the module. Now a rule
   under **Rules that apply to every task**; every suspend declaration in the module
   carries both classes.
2. **"Newest first" history is not a total order until the tie is broken.** A card
   created and triaged inside one clock tick produces two `StatusChange` rows with the
   same `changedAt`, and a stable sort on the timestamp alone hands those back in
   insertion order — the *oldest* order. The fake breaks the tie on insertion order, and
   the port's KDoc now says so, because Phase 8's Room query has to break it the same way
   (rowid). A test names the case rather than leaving it to whichever sort an
   implementation reached for.
3. **The backlog and the archive are disjoint; the board and the archive are not.**
   `done` is a column *and* archived, deliberately. The first test written for the three
   projections together asserted they partitioned the items, which is false and would have
   been a wrong test that passed nothing useful. The overlap is now asserted instead.

**Exit criteria**: met — **484** unique tests in `commonTest` green on all three targets
(498 on `jvmTest`, 424 on `testAndroidHostTest`), both architecture guards passing.

---

## Phase 8 — `:packages:feature-projects-data`

- [x] **8.1** `RoomProjectRepository.kt` — the only way to change a project or work
      item. Every mutation writes the record **and** its outbox row inside a single
      `inTransaction`. Re-reads affected rows after commit and publishes the matching
      `ProjectChange`. Includes `seedSampleDataIfNeeded` with **relative** due dates,
      so the sample board does not become a wall of overdue cards.
- [x] **8.2** `LocalDataSyncSink.kt` — `SyncApplySink` over the same container.
      **Never writes to the outbox.** Idempotent by construction. A project **delete**
      publishes **both** `.projects` and `.workItems`, because it takes its items with
      it and a board left open would show a column of items that no longer exist — the
      one case where an event being *nearly* right shows something stale rather than
      merely costing a re-read. Every other write announces one kind; see the table
      below.
- [x] **8.3** `WorkItemRecord.kt` — `projectID` is a plain string, not a
      relationship: the cascade has to be written explicitly anyway, and a
      relationship could make a deleted project's items vanish before their delete
      rows are written. `domainValue` reconciles `resolvedAt` on the **read** path, so
      a row written before the column existed heals on first read. `isOverdue`
      excludes terminal items.
- [x] **8.4** `ProjectRecord.kt` — carries `nextItemNumber`, which is **never
      derived by counting rows**: counting is wrong the moment an item is deleted,
      and two offline devices need a counter to merge.
- [x] **8.5** `StatusChangeRecord.kt` — append-only, deliberately **not** related to
      the work item: cascade deletes must be explicit, and a history row must never
      reattach itself to a reused id.
- [x] **8.6** `ProjectChangeBus.kt` — the one invalidation channel the repository and
      the sink publish on. Fresh stream per subscriber; `send` is a no-op when nobody
      listens (a screen opened later re-reads on start).
- [x] **8.7** `SampleData.kt` — the first-launch board.
- [x] **8.8** **Converter field-completeness test.** Both record↔domain converters in
      the original silently dropped a field (`resolvedAt`, `nextItemNumber`) because
      the compiler fills in a default. Write a test that reflects over each entity's
      fields and asserts each one appears in **both** converters. This is the single
      highest-value new test in the port. Written with explicit constructors instead,
      because reflection is unavailable on Kotlin/Native; see below.
- [x] **8.9** `commonTest` — ~65 of the 95: outbox atomicity, sink idempotency and
      no-echo, pull invalidation reaching the same channel a local edit does, a sink
      throw leaving the cursor put, status history surviving its item, a sync round
      trip. **152 written** — see the note below.

**Exit criteria**: met — **636** unique tests in `commonTest` green on `jvmTest` and
`iosSimulatorArm64Test` (was 484; the forecast was 549), `424` on `testAndroidHostTest`
unchanged, `./gradlew check verifyArchitectureGraph` green, and `:composition-root` still
compiles with an empty graph.

### What was written, and where it differs from the plan above

**152 tests, against a forecast of 65** — `RoomProjectRepositoryTest` 51,
`RecordMappingTest` 27, `LocalDataSyncSinkTest` 24, `SeedingTest` 20,
`StatusChangeHistoryTest` 14, `RecordCompletenessTest` 8, `ProjectChangeBusTest` 8. The count is well over the forecast because the two suites the forecast named
outright — history and seeding — turned out to carry the properties with the most
independent failure modes, and because every port method got its own normalisation,
outbox, announcement and error cases rather than one happy path each. Recorded as the
baseline is 636 common / 650 `jvmTest`; the later phases' forecasts are left as written
and are read against the new baseline.

**8.2 and 8.6: announcements are per-write-kind, not per-entity.** As written above
8.2 says a project change publishes both kinds. That was followed for a *delete* and
narrowed for everything else, in both the sink and the repository, because the original
announced both kinds from all seven of its write paths — which is precisely the
over-broadcast `ProjectChange`'s `kind` exists to prevent: a board woken by a project
rename re-reads every card in it for nothing, and a test cannot distinguish a board that
woke for the right reason from one that woke by accident. The rule now, and the one
`ProjectChangeBus`'s helpers encode:

| write | announces |
| --- | --- |
| create work item | `.projects` + `.workItems` (the counter moved) |
| delete project | `.projects` + `.workItems` (the items went with it) |
| create / update / archive project | `.projects` |
| edit / move / delete work item | `.workItems` |
| seed | both, unscoped — several projects at once, so there is no one to name |

**8.8: explicit constructors, not reflection.** `KClass.constructors` is unavailable on
Kotlin/Native, so the completeness test cannot reflect and still run on iOS. It instead
constructs each record through its full constructor with a non-default value per column
and asserts the domain value reads all of them back, which is stronger: a *missing*
parameter is a compile error rather than something a test has to notice. `RecordMappingTest`
(27 tests) covers the same ground from the other direction. The original's
`nextItemNumber` went missing because every sample value it was tested with had the
counter at 1, so the fixture here is deliberately unlike the defaults in every column.

**8.3 and 8.5, two corrections rather than ports.** A pulled work item cannot change
its `projectID` — a payload naming a different parent is a server bug, and applying it
would move a card between projects with no delete and nothing in the history. And a pulled
project cannot lower `nextItemNumber`, because the server's copy is whatever was true when
an *earlier* offline edit was pushed and honouring a lower counter reissues a key that is
already in use. Both are asserted.

**History ordering needed a column the original did not have.** `changedAt DESC` alone is
not a total order: a card created and triaged inside one clock tick gives two rows with the
same instant, and a stable sort then returns them in *insertion* order — so the history
reads backwards. `StatusChangeRecord.sequence` (the database-assigned insertion
tie-breaker) is the fix, and the query decides the whole order in one place rather than
being re-sorted in Kotlin.

**A bad payload and a bad column are handled in opposite directions, deliberately.** A
column this build cannot parse is dropped, so one unreadable row does not empty a history
or hide a project. A *payload* it cannot parse throws `AppError.Decoding`, aborting the page
and leaving the cursor where it was: a bad payload is the only copy of somebody's edit, and
dropping it loses that permanently, whereas version skew is better served by a later
release that understands the shape.

**Outbox ordering has a known gap.** The queue is `ORDER BY createdAt ASC, id ASC` and ids
are random, so two rows written at the same instant order arbitrarily. A sequence column
here, as in the history, would close it; the tests that would trip over it give their
writes distinct instants rather than asserting on `Uuid.random()`. Also deferred:
`kotlin.time.Instant` is stored with the serializer's built-in format rather than a
whole-second wire format of this project's own, and the v1→v2 Room migration is
Android-only and has no runtime test.

---

## Phase 9 — `:packages:feature-auth-domain` + `:packages:feature-auth-data`

### Domain

- [ ] **9.1** `AuthSession.kt` — `UserIdentity` (id, email, optional displayName,
      `preferredGreeting`), `SessionState` (signedOut/restoring/signedIn),
      `AuthTokens`. `AuthSessionPort` is the seam: `signIn`, `signOut`, `restore`,
      `sessionUpdates`.
- [ ] **9.2** `SignInUseCase.kt` — normalises through `AuthRules` **before** the
      port, so a malformed address never becomes a request.

### Data

- [ ] **9.3** `AuthRepository.kt` — the ordering that makes a session trustworthy:
      **retain tokens before publishing `signedIn`**; a failed sign-in touches
      nothing; sign out **clears credentials then publishes `signedOut`**. Both
      orderings are load-bearing and coroutine dispatch makes the bug easy to write
      (a `launch` instead of an awaited call silently reorders).
- [ ] **9.4** `TokenVault.kt` — owns the two tokens and every usability decision.
      Conforms to `AuthSessionDelegate`. Collapses concurrent refreshes. Keeps
      secrets separate from the profile, which lives in user-defaults and must be
      readable before the store opens.
- [ ] **9.5** `HttpAuthRemote.kt` — private wire shapes (`access_token` /
      `refresh_token`). The `Endpoint` seam exists so a POST can set
      `isIdempotent: true` — that is what stops a dropped connection during refresh
      from reading as a failed sign-in.
- [ ] **9.6** `MockAuthRemote.kt` — DEBUG stand-in, because `baseURL` is `.invalid`.
      Mints **genuine unsigned JWTs with a real `exp`** so expiry, forced refresh and
      restore are genuinely exercised.
- [ ] **9.7** `SessionStateBus.kt` — the auth feature's own broadcaster.
- [ ] **9.8** `commonTest` — 46 tests: token-vault expiry with a controlled clock,
      refresh collapsing, repository ordering, wire shape through a stub client, mock
      JWTs, sign-in use case normalisation, bus teardown. Use a `BoundedStream` helper;
      never an unbounded `for (x in flow)`.

**Exit criteria**: 595 tests green.

---

## Phase 10 — Design system completion

- [ ] **10.1** `ThemeScheme.kt` — three cases (`system`/`light`/`dark`) rather than a
      boolean, because "follow the system" is a distinct choice. Owns its own
      preferences key, deliberately **not** a `DefaultsKey` — appearance must survive
      sign-out. An unrecognised stored value falls back to `system`.
- [ ] **10.2** Typography scale and spacing scale. The original has **neither**; both
      features currently take fills from shared tokens. Adding them here is the point
      of a design system.
- [ ] **10.3** `AppMark.kt` and `AmbientBackdrop.kt` — shared so the splash and
      sign-in cross-fade cannot drift. The backdrop is accessibility-hidden.
- [ ] **10.4** `Glass.kt` — `ttGlassCard`, `TTGlassSegmentedPicker`,
      `TTGlassButtonStyle`. **iOS 26 `glassEffect` / `GlassEffectContainer` have no
      Compose equivalent.** Use a translucent surface with a brush/blur equivalent and
      accept the visual difference. On the board, the glass bars must float over a
      single scrolling container as siblings, because a material can only sample what
      is drawn behind it.
- [ ] **10.5** Add `compose-uiTooling` / `compose-uiToolingPreview` for `@Preview`.
      They are in the catalog and currently unreferenced, so there is no preview
      support at all despite every Swift view having one.
- [ ] **10.6** `commonTest` — 9 tests: accent contrast against the real token value,
      theme fallback, `colorScheme` nil-ness for system.

**Exit criteria**: 604 tests green.

---

## Phase 11 — `:packages:feature-auth-presentation`

- [ ] **11.1** `AuthEnvironment.kt` — a nullable `CompositionLocal` holding the
      interface, plus `MissingAuthSessionView` and a `.previewSession()` modifier.
- [ ] **11.2** `SignInModel.kt` — two fields plus a `Phase`. **No router
      reference**, because the session bus drives the transition.
- [ ] **11.3** `SignInView.kt` — the app's opening screen. Product name only, no
      navigation.
- [ ] **11.4** `LabelledField.kt`, `ErrorBanner.kt`, `PrimaryButtonStyle.kt`,
      `KeyboardHints.kt` (platform-neutral enums, because the package also builds for
      Android and the JVM host).
- [ ] **11.5** `AuthPreviewData.kt` fixtures.
- [ ] **11.6** 5 Compose UI tests: sign-in, session restore, sign-out, auth-gate
      return.

**Exit criteria**: 604 tests + 5 UI tests green.

---

## Phase 12 — `:packages:feature-projects-presentation`

The largest presentation surface: 24 files.

- [ ] **12.1** `ProjectEnvironment.kt` + `ViewPreviewData.kt` fixtures.
- [ ] **12.2** ViewModels (7) — `ProjectsListModel` (holds summaries, re-reads on
      `ProjectChange`, exposes a `notice` for the archive-instead-of-delete outcome),
      `ProjectEditorModel` (draft with `canSave`/`hasChanges`, loaded as a snapshot so
      a concurrent remote rename loses), `ProjectBoardModel`,
      `ProjectBacklogModel`, `WorkItemEditorModel` (**a draft, never a stored
      record** — a form bound to a persisted row could save an edit with no outbox
      row), `ProjectArchiveModel`, `ProjectPickerModel`.
- [ ] **12.3** Screens (8) — `ProjectsListScreen` (swipe-delete + confirmation naming
      the archive rule), `ProjectEditorScreen` (key prefix **read-only**),
      `ProjectBoardScreen`, `ProjectBacklogScreen`, `ProjectArchiveScreen`
      (month-grouped, newest first, terminal-only actions hidden),
      `ProjectPickerSheet`, `WorkItemEditorScreen`, `WorkItemEditorSheet`.
- [ ] **12.4** Views (7) — `BoardColumn`, `WorkItemCard`, `WorkItemBacklogRow`,
      `ProjectRow`, `PriorityBadge`, `ProjectSwitcherButton`, `NewProjectSheet`.
- [ ] **12.5** 4 Compose UI tests: card content rules, column behaviour, the archive
      round-trip from **both** places a task can be filed from (backlog row button
      and board card menu), because archiving is a status move and the two surfaces
      must agree.

**Exit criteria**: all green.

---

## Phase 13 — Settings + composition root + shared-ui shell

### `:packages:feature-settings-domain`

- [ ] **13.1** `SettingsPort.kt` — connectivity read + fresh update stream,
      `setForcedOffline` phrased as **intent** rather than a value to install, sync
      activity + updates, `canSyncNow`, `syncNow`, `endSession` (**a closure the app
      injects** — the settings package does not name the auth feature or the session
      coordinator), `appVersion()`.
- [ ] **13.2** `SettingsVocabulary.kt` — `ConnectivitySummary` and `SyncActivity`
      (7 phases) with user-facing titles, plus `SyncOutcome` and `AppVersion`.
      Deliberately **not** `NetworkMonitor.Connectivity` or `SyncStatus`.
- [ ] **13.3** `SyncNow.kt` — owns the offline decision. A control that goes dead for
      an invisible reason leaves the user concluding the app is broken, so it answers
      `.offline` and the screen can say the edits are queued and safe.
- [ ] **13.4** `commonTest` — 9 tests: `SyncNow` outcomes, port counters.

### `:packages:feature-settings-data`

- [ ] **13.5** `LiveSettingsService.kt` — the only file that knows a settings screen
      is fed by the network monitor and the sync coordinator. Every `when` is
      deliberately **total, no `else`**, so a case added upstream becomes a build
      error here rather than silently disappearing.

### `:packages:feature-settings-presentation`

- [ ] **13.6** `SettingsModel.kt`, `SettingsScreen.kt`, `SettingsEnvironment.kt`,
      `SettingsPreviewData.kt`. Rows appear only when their numbers are non-zero, so
      the quiet state cannot stand in for the alarming one. The About row hides itself
      when there is no version key.

### `:composition-root`

- [ ] **13.7** Register the object graph in 7 modules (platform, persistence,
      projects, auth, sync, session, settings), in dependency order. The three
      looping services (monitor, sync coordinator, timer) are **explicitly started**,
      never started during resolution — factories must stay a pure lookup.
- [ ] **13.8** **Invert `AppGraphTest`.** It currently asserts
      `assertFalse(resolver.registeredNames.isNotEmpty(), "expected an empty graph")`.
      That assertion is the first thing to fail and must be replaced, not deleted.
- [ ] **13.9** Add the missing `AppGraphAccessTest`. `AppGraph.kt`'s kdoc already
      claims it exists and that it fails if anything outside the composition root
      calls `KoinResolver` directly. It does not exist. Write it, or delete the claim.

### `:shared-ui`

- [ ] **13.10** `SessionCoordinator.kt` — `SessionTeardown` (clears pull metadata and
      session cache, resets the container's session scope, stops the sync
      coordinator) and the coordinator that turns `SessionState` into `Router.root`.
      `hasLiveSession` guards a cold launch from treating "no credentials" as
      "session ended". **Outbox rows are kept** — they carry the queuing account's id
      and only that account can claim them.
- [ ] **13.11** `RootScreen.kt` — holds the splash until **both** a minimum floor has
      elapsed **and** the session has answered. Either half alone is wrong. Restores
      the last visited project **twice** (`onAppear` and on `root` change), both
      idempotent, because the router is on the auth gate while the async restore runs.
- [ ] **13.12** `SplashScreen.kt` — canvas + `AppMark` + name, drawn on the ambient
      backdrop so the hand-off from the launch screen is invisible and the
      splash→sign-in cross-fade has a static background.
- [ ] **13.13** `NavigationHost.kt` — the Compose navigation host that renders the
      hand-rolled `Router`. `ProjectScopedTabScreen` is the root of both project tabs:
      the active project's screen, or a picker sheet when nothing is active. The
      scoped screens take a **non-optional** project id and rebuild on id change — a
      mid-view project switch must stop the old model or its collection keeps
      refreshing a board nobody shows. Tapping a project row routes through
      `openProject`, never a raw push.
- [ ] **13.14** Delete `ShellPlaceholder()` from `TaskTrackerApp.kt`.

**Exit criteria**: ~613 tests green, Android app runs end to end.

---

## Phase 14 — iOS shell

Much of the scaffolding landed in Phase 0 so the Xcode project generates from day one.
What remains is verifying the link and filling in the behaviours.

- [x] **14.1** `iosApp/project.yml` — XcodeGen manifest, iOS 26 deployment target,
      bundle id `com.tasktracker.app`, `tasktracker://` URL scheme, `UILaunchScreen`
      → `CanvasBackground`, portrait + both landscapes,
      `ITSAppUsesNonExemptEncryption: false`. `fileGroups` lists `../PLAN.md` and
      `project.yml` — without it the manifest is invisible in the navigator and the first
      sign of trouble is a hand-edit to `project.pbxproj` that the next
      `xcodegen generate` silently discards. The original records the same lesson at
      `project.yml:10-15`.
- [x] **14.2** `iosApp/TaskTracker/Info.plist`, written by hand with
      `GENERATE_INFOPLIST_FILE: NO` on the target.
- [x] **14.3** `iosApp/TaskTracker/Assets.xcassets` — `CanvasBackground.colorset`
      (white / black) and `AppIcon.appiconset`. **No `AccentColor` colorset**, on
      purpose: `ttAccent()` is a token in `commonMain`, so an iOS colorset would be a
      second source of truth for a value nothing reads. If system chrome ever needs the
      accent, derive the colorset from the token rather than hand-writing it.
- [x] **14.4** `shared-ui/src/iosMain/.../MainViewController.kt` — exports
      `mainViewController()` to Swift and **owns the graph**, mirroring
      `TaskTrackerApplication` on Android. It takes no arguments precisely so
      `shared-ui` does not have to `export(project(":packages:di"))` and publish the DI
      port into the framework's public surface for the platform shell to construct the
      thing it should be *given*.
- [x] **14.5** `iosApp/TaskTracker/TaskTrackerApp.swift` — a `WindowGroup` and nothing
      else. No iOS navigation or view state, matching `MainActivity`.
- [x] **14.6** `iosApp/TaskTrackerUITests/AppUITestCase.swift` — base class documenting
      the two traps: the system "Save Password?" sheet (it covers the app while leaving
      the underlying screen visible **and** in the accessibility tree, so element
      assertions keep passing while every tap is silently discarded) and the simulator
      keeping its container between launches (sign-out is a one-way door through the
      keychain, and uninstalling to reset would discard the seeded-data store).
- [ ] **14.7** Build script phase running
      `./gradlew :shared-ui:linkDebugFrameworkIosSimulatorArm64`. Written in
      `project.yml` as a `preBuildScript` with `basedOnDependencyAnalysis: false` —
      without that flag Xcode decides the script has nothing to do and skips it, leaving
      a stale or missing framework for the build to fail on in a way that points at the
      linker rather than at Gradle. **Not yet verified end to end.**
- [ ] **14.8** Verify the link on an iPhone 17 Pro simulator: `xcodegen generate`,
      then `xcodebuild -project TaskTracker.xcodeproj -scheme TaskTracker -destination
      'platform=iOS Simulator,name=iPhone 17 Pro' build`. Expect a first failure here —
      the framework output path, the `xcode-frameworks` copy task, and the `Info.plist`
      are all unproven.
- [ ] **14.9** Deep link handling on iOS via `scene(_:openURLContexts:)` → `Router`,
      and an `<intent-filter>` for `tasktracker://` on the Android launcher activity.
- [ ] **14.10** Port the 10 XCUITests once there are screens to drive.

**Exit criteria**: `xcodegen generate` then an iPhone 17 Pro simulator build and run.

---

## Phase 15 — Verification and documentation

- [ ] **15.1** `./gradlew check` green on all three targets (`jvmTest`,
      `testAndroidHostTest`, `iosSimulatorArm64Test`), with `verifyArchitectureGraph`
      and every `verifyModuleImports` passing.
- [ ] **15.2** iOS simulator build + XCUITest run.
- [ ] **15.3** Write `AGENTS.md` for the KMP project, recording the Kotlin-specific
      boundaries and every decision that differs from the original: the accent is now
      a real token (so the contrast test no longer reads a file off disk), glass has no
      Compose equivalent, the hand-rolled router is pure state with no Compose types,
      and `StreamObservation` was dropped.
- [ ] **15.4** Update the test-count baseline in `AGENTS.md` in the same change as any
      test addition. The baseline is the tripwire that notices a module whose tests
      no longer compile — do not let it go stale.
- [ ] **15.5** Delete the four empty scaffolds (`FeatureBacklog`, `FeatureBoard`,
      `FeatureTaskEditor`, `FeatureWorkItems`) rather than carrying them over. They
      were already listed as known gaps in the original's `AGENTS.md:538-539`.

---

## Known product gaps carried over from the original

Do not treat these as regressions.

1. The API host is `https://api.tasktracker.invalid`, so server sync cannot succeed.
   Mitigated by `MockSyncTransport` (task 6.7), which is an addition, not a port.
2. **The local store is not user-partitioned.** Outbox rows carry a `userID` and the
   engine claims only the signed-in account's, but synced entities remain on disk, so
   a different account signing in sees the previous account's projects. Full
   multi-user isolation is a schema decision, not a teardown fix. Flagged, not fixed.
3. No typography or spacing scale in the original; added in Phase 10.
4. Dark mode recolours the accent only; surfaces stay semantic by decision, so there
   is no charcoal surface treatment.

## Highest-risk items

1. **Sync engine `settle` logic** (6.5) — id-matched verdicts, duplicate
   invalidation, unmentioned-as-retry. Short code that looks like an optimisation and
   encodes several hard-won lessons.
2. **Converter field completeness** (8.8) — has already silently dropped two fields
   in the original.
3. **`resolvedAt` read-path reconciliation** (8.3) — port the write path, miss the
   read path, and a live task is filed in the archive forever. The symptom looks like
   nothing at all.
4. **Compiler-enforced boundaries become convention.** Kotlin cannot enforce
   Domain-has-no-Data. `tasktracker.architecture` is the substitute and must be kept
   green, or the layering erodes the first time someone is in a hurry.
5. **Session ordering** (9.3) — a `launch` instead of an awaited call silently
   reorders, and the resulting bug is a sign-in that appears to work.
