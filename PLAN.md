# TaskTrackerKMP — Port Plan

Resumable task list for porting `/Users/amirreza/Desktop/SampleProjects/TaskTracker`
(SwiftUI/SwiftData, 165 files, 13 SwiftPM packages, 395 unit + 10 UI tests) to
Kotlin Multiplatform.

**Every task below is a checkbox. Tick it when it is done. Start any session by
finding the first unticked box.**

## Index

Phases are listed **in the order they appear in this file**, not in phase order — Phase 13
(part 1) was pulled forward to unblock a running app, so the document is not monotonic. The
`file:line` column is the jump target if your viewer takes you to the raw file.

| phase | module / subject | file:line | state |
| --- | --- | --- | --- |
| [0](#phase-0--build-foundation) | build foundation, convention plugins | [PLAN.md:219](#phase-0--build-foundation) | done |
| [1](#phase-1--packagescore--packagesdomain) | `core` + `domain` | [PLAN.md:260](#phase-1--packagescore--packagesdomain) | done |
| [2](#phase-2--packagesrouting) | `routing` | [PLAN.md:400](#phase-2--packagesrouting) | done |
| [3](#phase-3--keychain--userdefaults) | keychain + user-defaults | [PLAN.md:449](#phase-3--keychain--userdefaults) | done |
| [4](#phase-4--packagesdatabase-room-kmp) | `database` (Room KMP) | [PLAN.md:511](#phase-4--packagesdatabase-room-kmp) | done |
| [5](#phase-5--packagesnetwork-ktor-360) | `network` (Ktor) | [PLAN.md:591](#phase-5--packagesnetwork-ktor-360) | done |
| [6](#phase-6--packagessync) | `sync` | [PLAN.md:707](#phase-6--packagessync) | done |
| [7](#phase-7--packagesfeature-projects-domain) | `feature-projects-domain` | [PLAN.md:799](#phase-7--packagesfeature-projects-domain) | done |
| [8](#phase-8--packagesfeature-projects-data) | `feature-projects-data` | [PLAN.md:868](#phase-8--packagesfeature-projects-data) | done |
| [9](#phase-9--packagesfeature-auth-domain--packagesfeature-auth-data) | auth domain + data | [PLAN.md:982](#phase-9--packagesfeature-auth-domain--packagesfeature-auth-data) | done |
| [10](#phase-10--design-system-completion) | design system | [PLAN.md:1103](#phase-10--design-system-completion) | done |
| [11](#phase-11--packagesfeature-auth-presentation) | `feature-auth-presentation` | [PLAN.md:1248](#phase-11--packagesfeature-auth-presentation) | done |
| [13 pt 1](#phase-13-part-1-pulled-forward--making-the-sign-in-screen-reachable) | **pulled forward** — sign-in reachable | [PLAN.md:1344](#phase-13-part-1-pulled-forward--making-the-sign-in-screen-reachable) | done |
| [13 pt 1 follow-ups](#phase-13-part-1-follow-up-2--the-projects-list-became-reachable-and-the-new-project-button-did-not) | sign-in button invisible; projects list + New Project | [PLAN.md:1421](#phase-13-part-1-follow-up--the-sign-in-button-was-invisible-and-it-was-three-bugs) | done |
| [12](#phase-12--packagesfeature-projects-presentation) | `feature-projects-presentation` | [PLAN.md:1692](#phase-12--packagesfeature-projects-presentation) | done |
| [13](#phase-13--settings--composition-root--shared-ui-shell) | settings + composition-root + shared-ui | [PLAN.md:1838](#phase-13--settings--composition-root--shared-ui-shell) | done |
| [14](#phase-14--ios-shell) | iOS shell | [PLAN.md:1915](#phase-14--ios-shell) | open |
| [15](#phase-15--verification-and-documentation) | verification + docs | [PLAN.md:1968](#phase-15--verification-and-documentation) | open |

**First unticked box: [14.1](#phase-14--ios-shell)**, at
[PLAN.md:1915](#phase-14--ios-shell). Phase 13 is closed, so Phase 14 is where work
resumes.

Reading aids, which are not phases:

- [Where things stand](#where-things-stand) — what is implemented, and the test baseline.
- [Decisions taken](#decisions-taken) — the calls that are not revisable per task.
- [Non-negotiable invariants](#non-negotiable-invariants-from-the-originals-design).
- [Rules that apply to every task](#rules-that-apply-to-every-task).
- [How to build](#how-to-build) — including [Kotlin/Native test constraints](#kotlinnative-constraints-hit-while-writing-tests),
  which is the rule that decides where an entry point file lives.
- [Test-count baseline](#test-count-baseline) — what "green" is supposed to mean.
- [Known product gaps](#known-product-gaps-carried-over-from-the-original) and
  [highest-risk items](#highest-risk-items) — the closing pair.

Per-phase notes live under their own phase heading, marked `### Phase N notes` or
`### What was written, and where it differs from the plan above`. Those carry the
reasoning a checkbox cannot: why a decision was reversed, what was measured, and what was
deliberately left out.

## Where things stand

Original: iOS 26, Swift 6 strict concurrency, SwiftData, Swift Testing, XcodeGen.

Port: 22 Gradle modules. **Implemented**: `:packages:core`, `:packages:domain`,
`:packages:di`, `:packages:design-system` (colour tokens, accent, strings),
`:packages:routing`, `:packages:keychain`, `:packages:user-defaults`,
`:packages:database`, `:packages:network`, `:packages:sync`,
`:packages:feature-projects-domain`, `build-logic`. **Empty**:
the eight remaining feature modules plus most of `:androidApp` and `:iosApp`.

**Progress: Phases 0-11 complete, `./gradlew check` green on all three targets.**
Phases 14-15 outstanding. Test baseline **969 unique in `commonTest`** (projects-data 154,
projects-presentation 117, network 88, projects-domain 81, sync 70, database 61, design-system
70, feature-auth-data 53, routing 52, domain 43, core 42, composition-root 35,
feature-auth-presentation 13, feature-auth-domain 13, keychain 12, settings-domain 12,
settings-presentation 11, di 19, user-defaults 19, settings-data 5) plus the **21 shell
scenarios** in `shared-ui`'s `commonTest`, which are `fun`s the target entry points call
rather than `@Test`s, and are counted per target below. The remainder is target-only:
`jvmTest` currently reports **1058 executions** in JUnit XML; the current iOS simulator XML
reports **1036**. These totals include the five new common tests, two of which are iOS-only
execution differences in this suite. This full run is not green; the existing failures are listed in the current
verification note under [test-count baseline](#test-count-baseline).
`testAndroidHostTest` is **not green** — see the Android-host note below.

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
- **A composable does not tag its own container; the caller does** —
  `NavigationHost` used to end its `modifier` chain with
  `.testTag(NavigationHostTag)`, and `RootScreen` passes
  `Modifier.testTag(SignedInShellTag)` in as that same `modifier`. `testTag`
  *overwrites* rather than merges, so whichever runs second wins and the loser is
  in no semantics tree at all — `NavigationHostTag` was present nowhere, and no
  test noticed for as long as it existed because nothing asked for it. `NavigationHost`
  now tags nothing of its own and the container is identified by `SignedInShellTag`.
  The alternative, keeping both by merging on the outer modifier, buys a second name
  for one node and a rule about modifier order that nothing in the type system
  enforces. Found by a test that asserted on the bar's anchoring and reported the
  wrong node missing, which is the only reason it was ever going to be found: a dead
  test hook is invisible to every test that does not use it.
- **Tests** — all 395 ported alongside their module, not deferred.
- **The database is an injected `DatabaseConfiguration`, not an injected host** —
  `:composition-root`'s `PlatformBindings` has three substitutable backends
  (`keychain`, `preferences`, `database`) and one honest `host: Any?`. The database
  is a *configuration* because a defaulted host would be evaluated during
  construction, and `applicationDefault(host)` throws without a `Context` — so every
  graph test, including the ones that only check whether a port is registered, would
  fail before asserting anything. It is not defaulted to the in-memory configuration
  because a default is what production receives, and production must not be able to
  reach an in-memory store by omission. A configuration also keeps the graph buildable
  on a target that cannot *open* a store, which is what the Android test host is.

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

Current test baseline: **969** `@Test`s in `commonTest`, plus **21** shell scenarios in
`:shared-ui` that are shared `fun`s rather than `@Test`s (see
[test-count baseline](#test-count-baseline) for the per-module split). Adding the
target-only entry points and platform suites gave **1058** executions on `jvmTest` and
**1036** on `iosSimulatorArm64Test` in the latest JUnit XML.

The focused new project member tests passed on JVM and iOS. The full run still reports
pre-existing failures: three project data seeding assertions, three project presentation
assertions, and two shared UI assertions on JVM; three project presentation assertions on iOS. The
Android host was not rerun; its last recorded baseline is 10 failures of 818.

`testAndroidHostTest` was **red for three** stacked reasons. Reason 1 is now fixed; reasons 2
and 3 remain, at **10 failures of 818** (was 66).

1. ~~The Compose resource-packaging failure below~~ — **fixed**, in
   `tasktracker.kmp.compose.gradle.kts`. It had taken every presentation suite that reads a
   string: 22 in `:shared-ui`, 32 in `:packages:feature-projects-presentation`, 7 in
   `:packages:feature-settings-presentation`. All 61 now pass.
2. Five `:composition-root` graph assertions cannot run on that target at all — `AppGraphTest`'s
   session-collaborator, looping-services, settings-port and sync-chain cases, and
   `SessionTeardownTest`'s outbox case: they resolve
   `TaskTrackerDatabase`, and Room's `inMemoryDatabaseBuilder` takes a `Context`, which is
   reachable only from a Robolectric sandbox, which only `@RunWith(RobolectricTestRunner::class)`
   creates — and those assertions live in `commonTest`, where a runner annotation is not
   expressible. The same assertions pass on JVM and iOS, so this is a gap in the target, not in
   the wiring. Fixing it means giving those five a source set that can carry a runner, not
   loosening what they assert.
3. **Five Compose UI assertions, and the prediction in the previous revision of this file was
   wrong about them.** The prediction was that fixing the packaging bug would move all 61 onto
   the `Build.FINGERPRINT` wall. It did not: Robolectric was already engaging, because those
   suites have had `androidHostTest` entry points carrying `@RunWith(RobolectricTestRunner::class)`
   all along, and the `MissingResourceException` was being thrown during composition setup —
   before the idling strategy ever dereferenced `Build.FINGERPRINT`. What is left is a layout
   difference, not a missing capability: Robolectric's default viewport is far smaller than the
   JVM's and iOS's, so a node inside a `LazyColumn` (`settings.versionRow`) or a
   `horizontalScroll` row (`board.column.in_review`) exists and reports
   `assertIsDisplayed()` failing. `@Config(qualifiers = "w411dp-h891dp-xxhdpi")` does clear
   that, and then exposes a genuinely separate problem underneath:
   `theVersionRowHidesItselfWithNoVersion` calls `setContent` twice, which Android's
   `ComponentActivity` rejects with *"has already set content"*. So these need the same
   decision as reason 2 — a change to shared scenarios that two green hosts currently pass —
   and they are recorded here as measured rather than guessed at a third time.

Those last two tests therefore ask `thisHostRasterisesText()` — an `expect` in `commonTest`
with `actual`s in `jvmTest`, `iosTest` and `androidHostTest` — and skip where the answer is no.
Two things to be honest about if that is ever relied on: the skip is recorded as a **pass** on
that target rather than as a skip, because `kotlin.test` has no portable skip and
`org.junit.Assume` does not exist on the iOS and JVM compilations, so a reader of
`testAndroidHostTest` results alone would conclude those assertions ran. And they do not need
that host to be worth having: the JVM and the iOS simulator both rasterise and both run every
assertion, and the pair is the entire reason the test is worth writing — the defect it exists
for only appears when the two disagree, which it did. Update this paragraph in the same change
as any test addition.

This number was wrong three times, in the same way, so it is worth saying why. It read 636
after Phase 9, because it had not been updated for the 66 auth tests Phase 9 added
(`feature-auth-domain` 13 + `feature-auth-data` 53) — so Phase 10's arithmetic did not
reconcile, and the gap looked like 3 tests when the additions were 50. It then read 636 in
the "Where things stand" paragraph *above* while the section here said 752, because Phase 10
updated this section and not that one: two copies of one number, one of which nobody was
reading. Both now say the same thing, and the Phase 11 addition is recorded in both.

The count is taken from the JUnit XML the build actually writes and cross-checked against
`@Test` annotations in the source tree: 1058 JVM executions and 1036 iOS executions. Note that a
naive `grep -c @Test` over-counts, because KDoc mentions the string in backticks — so the XML
is the source of truth and the grep is the cross-check, never the reverse. A second trap: the
modules at the repo root (`composition-root`, `shared-ui`) are one directory deep while the
rest are two, so a glob like `*/build/test-results/jvmTest/*.xml` matches those two *as well
as* a `**` glob does and silently doubles them. Measure per-module and check one module by
hand. Take it from the XML next time:

```bash
# Per-target totals, from the results the build just wrote.
for t in jvmTest iosSimulatorArm64Test testAndroidHostTest; do
  python3 -c "
import glob, re, sys
t = sys.argv[1]
total = failed = 0
for p in glob.glob(f'*/build/test-results/{t}/*.xml') + glob.glob(f'*/*/build/test-results/{t}/*.xml'):
    s = open(p).read()
    total += len(re.findall(r'<testcase ', s))
    failed += len(re.findall(r'<failure ', s))
print(f'{t}: {total} tests, {failed} failed')
" "$t"
done
```

The exit criteria in Phases 8-13 are forecasts off this baseline, and they were written
against a 222-test baseline that the port passed in Phase 4 — so they have been rebased
once, upward, by the same 182 tests the earlier phases actually added. They are counts to
check a phase against, not targets to hit exactly; the per-phase notes record what was
really written where it differs.

### Kotlin/Native constraints hit while writing tests

- **`.` and `,` are illegal inside backtick test names.** Kotlin/Native rejects them.
  `"clears 4.5 to 1 against white"` does not compile; write
  `"clears the text contrast bar against white"`. Hit again in Phase 10, and the second
  time it was subtler: a comma in a name is only rejected when the *common* test source is
  compiled for Native, so three of the six names in this phase looked fine under
  `:packages:design-system:jvmTest` and only failed under
  `:packages:design-system:iosSimulatorArm64Test`. A green JVM test run is not evidence
  that a test name is legal.
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

- [x] **9.1** `AuthSession.kt` — `UserIdentity` (id, email, optional displayName,
      `preferredGreeting`), `SessionState` (signedOut/restoring/signedIn),
      `AuthTokens`. `AuthSessionPort` is the seam: `signIn`, `signOut`, `restore`,
      `sessionUpdates`.
- [x] **9.2** `SignInUseCase.kt` — normalises through `AuthRules` **before** the
      port, so a malformed address never becomes a request.

### Data

- [x] **9.3** `AuthRepository.kt` — the ordering that makes a session trustworthy:
      **retain tokens before publishing `signedIn`**; a failed sign-in touches
      nothing; sign out **clears credentials then publishes `signedOut`**. Both
      orderings are load-bearing and coroutine dispatch makes the bug easy to write
      (a `launch` instead of an awaited call silently reorders).
- [x] **9.4** `TokenVault.kt` — owns the two tokens and every usability decision.
      Conforms to `AuthSessionDelegate`. Collapses concurrent refreshes. Keeps
      secrets separate from the profile, which lives in user-defaults and must be
      readable before the store opens.
- [x] **9.5** `HttpAuthRemote.kt` — private wire shapes (`access_token` /
      `refresh_token`). The `Endpoint` seam exists so a POST can set
      `isIdempotent: true` — that is what stops a dropped connection during refresh
      from reading as a failed sign-in.
- [x] **9.6** `MockAuthRemote.kt` — DEBUG stand-in, because `baseURL` is `.invalid`.
      Mints **genuine unsigned JWTs with a real `exp`** so expiry, forced refresh and
      restore are genuinely exercised.
- [x] **9.7** `SessionStateBus.kt` — the auth feature's own broadcaster.
- [x] **9.8** `commonTest` — **66 tests, against a forecast of 46** — see the note
      below. Token-vault expiry with a controlled clock, refresh collapsing,
      repository ordering, wire shape through a stub client, mock JWTs, sign-in use
      case normalisation, bus teardown. Every stream assertion starts its collector
      before it sends, and reads under a deadline; an unbounded `for (x in flow)` over
      a bus that has gone quiet is a test that never returns.

**Exit criteria**: met — **699** unique tests in `commonTest` green on `jvmTest` and
`iosSimulatorArm64Test` (was 636; the forecast was 595), `487` on
`testAndroidHostTest` (was 424), `./gradlew check verifyArchitectureGraph` green, and
`git diff --check` clean. The new 66 are `13` domain + `53` data.

### What was written, and where it differs from the plan above

**66 tests, against a forecast of 46** — `SignInUseCaseTest` 5, `UserIdentityTest` 5,
`SessionUseCasesTest` 3 in the domain; `TokenVaultTest` 14, `AuthRepositoryTest` 10,
`MockAuthRemoteTest` 14, `HttpAuthRemoteTest` 9, `SessionStateBusTest` 6 in the data.
The count is over because the plan named properties rather than cases, and the two
properties with the most independent failure modes were the ones the forecast gave
one line each: a session has to survive *concurrency* (a refresh that returns late, a
read that returns late, a sign-out that lands mid-request) and the bus has to survive
*several collectors at once*. Each of those is a separate way to get a wrong session,
so each is a separate test rather than an extra assertion on a happy path.

**9.1: the port shapes are KMP's, not the original's.** Three deliberate departures,
all because a Swift signature does not survive the crossing intact:
`AuthRemotePort.signIn` returns `AuthTokens` alone and the repository derives the
identity from the token's claims; `AuthSessionPort.restore` returns the `SessionState`
it settled on rather than `Void`, so a launch path can render it without a second
read; and `sessionUpdates` is a `Flow` property. Deriving identity from the token is
also a *behaviour* change with a reason: a cached profile in user-defaults can disagree
with the keychain, and the keychain is the authority — so a stale cache cannot
resurrect an account the user has since dropped.

**9.4: the vault is two locks, and the reason is a gesture.** The original was an
actor, which held its executor across `await` but *not* across its `Task.detached`
refresh, and the explicit `refreshInFlight` was what stopped eight concurrent misses
becoming eight refreshes. A naive single-`Mutex` port would hold one lock across the
network instead — correct, and wrong in a way users see: Sign Out would block for the
length of a slow refresh, on the one path where someone is waiting on a tap. So
`state` guards the tokens and a `revision`, and is never held across a request, while
`refresh` serialises the round-trip and collapses the misses. A sign-out therefore
proceeds *while* a refresh is in flight, and the refresh discards its own result on the
way back. Lock order is `refresh` then `state`, never nested — a `Mutex` is not
reentrant, so a nested acquisition deadlocks rather than fails.

**Three bugs this phase found in itself, all of which a JVM-only build accepts.**

| | JVM | Native |
| --- | --- | --- |
| `@Throws` on an override | compiles | rejected: the filter must match the interface's, and `AuthSessionDelegate` declares none |
| `return@withLock <value>` out of `Mutex.withLock` | works | returns `Unit`, which fails to cast to `AuthTokens` at runtime |
| `Long.shr` / `ULong.shr` shift counts | — | masked to 6 bits *everywhere*, so `120 down to 0` aliases onto `56 down to 0` |

The third is worth keeping: `MockAuthRemote` spread one 64-bit FNV hash over sixteen
bytes by shifting `120 down to 0`, and because the shift count is masked the last eight
bytes came out equal to the first eight — a well-formed, stable **64-bit** id wearing a
128-bit label. Every test that checked the whole string passed, because each id is
internally consistent. It now derives two independent words, and the test compares the
two *halves* (`take(16)` against `drop(16)`; the first attempt compared 4 bytes with 12
and could not fail against the very bug it was written for).

**A sign-out can lose a race with a keychain read, and the fix is the revision counter.**
The vault caches keychain values in memory to keep the interceptor off a platform call,
and the write-back is the dangerous moment: a sign-out landing just before it had its
own `removeAll()` undone by the value the read had already captured, and that value
went straight back out to the interceptor. The sign-out would appear to work and then
quietly not take. Writing the cache back is now conditional on `revision` being
unchanged, and the regression test holds a read open inside the keychain
(`PausableKeychain`) to reproduce the window — a test that cannot open the window
cannot catch the bug.

**9.6: the mock is in `commonMain`, and that is a decision rather than an oversight.**
The release rule is "a debug build must never be handed this instead of
`HttpAuthRemote`", and it is enforced at the composition root, which chooses between the
two — nothing else in the app constructs a remote. Its `commonTest` exercises it on all
three targets, so it has to sit in a source set `commonTest` can see, and the hierarchy
here is the default template with no debug/release split to hang a `debugMain` on.
Adding that split to every module, to keep one class out of a release binary, is new
build machinery for a problem a single `if` in the root already solves. **Deferred, and
deliberately so**: the phase that wires the root adds the build-mode switch, and this
becomes unreachable from a release root the same day.

**`AppError`'s singleton cases still carry a misleading stack trace.** An `object` case
such as `Unauthorized` is constructed once, so the trace points at whatever first threw
it for the life of the process and every later throw looks like the same failure site.
It cost real debugging time here. Left alone: it is a `core` change that affects every
feature's error handling, and it belongs in its own phase rather than inside auth.

---

## Phase 10 — Design system completion

- [x] **10.1** `ThemeScheme.kt` — three cases (`system`/`light`/`dark`) rather than a
      boolean, because "follow the system" is a distinct choice. Owns its own
      preferences key, deliberately **not** a `DefaultsKey` — appearance must survive
      sign-out. An unrecognised stored value falls back to `system`.
- [x] **10.2** Typography scale and spacing scale. The original has **neither**; both
      features currently take fills from shared tokens. Adding them here is the point
      of a design system.
- [x] **10.3** `AppMark.kt` and `AmbientBackdrop.kt` — shared so the splash and
      sign-in cross-fade cannot drift. The backdrop is accessibility-hidden.
- [x] **10.4** `Glass.kt` — `ttGlassCard`, `TTGlassSegmentedPicker`,
      `TTGlassButtonStyle`. **iOS 26 `glassEffect` / `GlassEffectContainer` have no
      Compose equivalent.** Use a translucent surface with a brush/blur equivalent and
      accept the visual difference. On the board, the glass bars must float over a
      single scrolling container as siblings, because a material can only sample what
      is drawn behind it.
- [x] **10.5** Add `compose-uiTooling` / `uiToolingPreview` for `@Preview`.
- [x] **10.6** `commonTest` — accent contrast against the real token value, theme
      fallback, `colorScheme` nil-ness for system, plus scale and constant invariants.

**Exit criteria**: met — **752** unique tests in `commonTest` green on `jvmTest` and
`iosSimulatorArm64Test` (was 702), **540** on `testAndroidHostTest` (was 490),
`./gradlew check verifyArchitectureGraph` green, `git diff --check` clean. **50 tests
written**, against a forecast of 9.

### What was written, and where it differs from the plan above

**50 tests, against a forecast of 9.** `:packages:design-system` 6 → 49 and
`:packages:user-defaults` 12 → 19. All common, all three targets, no target-specific
tests.

The forecast was 9 and the reason for writing 50 is that 9 would have tested the wrong
things. "Accent contrast, theme fallback, `colorScheme` nil-ness" are three assertions
about numbers that already existed, and `AccentContrastTest` was already the first of
them. A design system with no scale has nothing for a scale test to say, so 10.2 created
the two scales and 10.6 then had something to assert about them — and what is worth
asserting about a scale is not its values (a test of a constant against itself keeps
passing after somebody edits the constant) but its *properties*: that the ramp increases,
that the type scale never decreases, that the pairs that share a size differ in weight,
that the two named spacing values that alias the ramp still alias it.

| File | Tests | What it holds |
| --- | --- | --- |
| `ThemeSchemeTest` | 10 | wire round trip, six ways an unreadable value fails, `of` failing where `fromStoredValue` falls back |
| `SpacingScaleTest` | 9 | ramp ordering, the off-ramp values being off the ramp, the aliases staying on their step |
| `TypographyScaleTest` | 9 | sizes never decreasing, selected/emphasised pairs differing in weight, leading, whole-point sizes |
| `BrandConstantsTest` | 9 | the glyph fitting its tile, the shadow being a drop, the checkmark's tip above its vertex, the glass alphas' order |
| `PinnedAppearanceTest` | 6 | every pinned token differing per appearance, the pre-composited alphas, label legibility |
| `RawKeyTest` | 7 | the raw key used verbatim, `removeAll` leaving it alone, plain-string encoding |

**Two real bugs the tests found, in the code this phase wrote.**

- **`ttFixedColours.separatorLight` was `Color(0x3C000000)`.** `0x3C` is 60 read as the
  alpha byte, and 60 is UIKit's `separator` *red* component — the value the comment two
  lines above was describing. It also contradicted its own comment, which said 20% and
  0x3C is 23.5%. Corrected to UIKit's actual resolution, `(60, 60, 67)` at 0.29 light and
  `(84, 84, 88)` at 0.36 dark, and the KDoc now says where they came from.
- **The raw preference key was being namespaced twice.** `UserDefaultsStore.namespaced`
  prefixes every `DefaultsKey` with `com.tasktracker.`, which is right for them because
  their values are relative (`navigation.lastSeenProject`). But `ThemeScheme.defaultsKey`
  is `com.tasktracker.settings.themeScheme` — already absolute, because the original reads
  it through `@AppStorage`, which never applies a namespace. Prefixing produced
  `com.tasktracker.com.tasktracker.settings.themeScheme`. Nothing would have reported it:
  the read and the write agreed with each other, so the setting worked for anyone who set
  it in the new build and silently returned to `system` for anyone whose value the original
  had written. Raw keys are now used verbatim, and `RawKeyTest` asserts against the
  backend's key list rather than round-tripping, because a round trip cannot tell a right
  key from a consistently wrong one.

**23 mutations run, 23 caught** — across the design system and user defaults: a
case-insensitive theme parse, `opposite` returning itself, a dark label turned black
(white-on-white), the separator alpha reverted, a ramp reordered, a radius duplicated, a
control padding moved, the key retyped, the raw key re-namespaced, the raw value
JSON-encoded, `removeAll` extended to raw keys, a checkmark drawn upside down, a glyph too
big for its tile, a shadow offset to zero, a tint raised above the fill, an opaque
prominent tint, a press scale above 1, a hairline rounded to a point, the tab lens's wash
unclipped. Two mutations were
**missed** on the first pass and are worth recording because both were the *test's* fault
rather than the code's: a change to a control's horizontal padding passed a test whose
name said it pinned "the relationship between the two halves of a pair" and which only
checked one half, and a retype of `ThemeScheme.defaultsKey` had been skipped by a stale
mutation anchor. Both are now covered.

### Things decided, and why

**`System` resolves to `null`, and that is the load-bearing assertion.** A design system
that resolved `System` to light "as a sensible default" would make the third case
unreachable while passing every test about it — the enum would have three members and the
picker three options, and the app would ignore one. `colorScheme == null` for `System` is
what keeps it reachable, and the pinned pair comes from `ttFixedColours` on every platform
including the two where a dynamic colour is available, because a pinned appearance has to
be pinned.

**`ttLabel` and `ttLabelSecondary` were added, which the plan did not ask for.** The
original gets text colour from SwiftUI's `.primary` / `.secondary`. Compose has no such
thing outside Material 3, and the only token available was `ttCanvas()` — which in dark is
pure black on a pure black canvas, a 1:1 ratio that is invisible and compiles. The two new
tokens resolve the fixed label colours per appearance; `PinnedAppearanceTest` asserts the
label clears 4.5:1 on its own canvas so the regression cannot come back.

**`ttLabelSecondary` measures 3.44:1, and does not meet WCAG AA for body text.** That is
not a porting error: it is what UIKit's `secondaryLabel` measures, and the original used it
on footnotes and captions. 3:1 is WCAG's *large text* bar (18pt, or 14pt bold) and these
roles are 11–13sp, so the honest statement is that the token is correct for de-emphasised
supporting text and not for body copy. The test pins the measured ratio rather than
asserting a bar the design never claimed, and the consequence is recorded on the token
where a caller will read it. Raising the alpha to pass 4.5:1 would make the port a worse
match for the design; that is a design decision, not a token one.

**`uiToolingPreview` everywhere, `uiTooling` nowhere.** `ui-tooling` publishes no iOS
artifact in Compose Multiplatform 1.12.1 — adding it to `commonMain` fails
`kmpPartiallyResolvedDependenciesChecker` for `iosArm64` and `iosSimulatorArm64` — so only
`ui-tooling-preview`, which carries the `@Preview` annotation the previews pane and every
`#Preview` in the original map onto, is wired. The live inspector is therefore Android and
JVM only, which is a platform limit rather than a choice. This corrects the plan's
`compose-uiTooling`, which cannot be satisfied as written.

**`TTGlassButtonStyle` is a data class, not a `ButtonStyle`.** Material 3 is not a
dependency, so there is no `ButtonStyle` for it to implement and no `Button` to apply it
to. What is worth keeping from the original is that a style can be named once and applied
at many call sites, so the parameters became a value the caller passes. It also replaced a
`prominent: Boolean`, which could not express a destructive tint.

### Left for later, deliberately

**Nothing asserts that `ttTheme` reaches `ttFixedColours`.** The indirection between them
is `dynamicOrPinned`, and testing it needs a composer. `PinnedAppearanceTest` covers the
pure half and says in its own docstring that the composition half is uncovered; the first
presentation module (Phase 11) is where it belongs, since that is also the first thing in
the codebase that can decide how composition tests are written.

**The appearance is not loaded at the root, and the first frame is not settled.** The
original reads `@AppStorage` synchronously so the launch screen is already in the right
appearance. `UserDefaultsStore`'s raw API is suspend-only and there is no theme state
holder yet, so the first-frame guarantee is *not* met by anything written in this phase.
Closing it means a state holder and a synchronous read path, which is composition-root
work, not design-system work.

**The board's glass bars are documented, not built.** The rule — the bars must be siblings
over a single scrolling container, because a material can only sample what is drawn behind
it — is stated in `Glass.kt`'s docstring where a caller will meet it, but there is no board
yet to put them in. That is Phase 12.

---

## Phase 11 — `:packages:feature-auth-presentation`

- [x] **11.1** `AuthEnvironment.kt` — a nullable `CompositionLocal` holding the
      interface, plus `MissingAuthSessionView`. The gate is read by `SignInView` rather
      than offered as a `.previewSession()` modifier; see the note below.
- [x] **11.2** `SignInModel.kt` — two fields plus a `Phase`. **No router
      reference**, because the session bus drives the transition.
- [x] **11.3** `SignInView.kt` — the app's opening screen. Product name only, no
      navigation. Reads `currentAuthSession`; shows the gate when it is null.
- [x] **11.4** `LabelledField.kt`, `ErrorBanner.kt`, `PrimaryButton.kt` (not
      `PrimaryButtonStyle`), and **no `KeyboardHints.kt`** — see the note below.
- [x] **11.5** `AuthPreviewData.kt` fixtures.
- [x] **11.6** **7** Compose UI tests (the plan said 5), on all three targets.

**Exit criteria**: met — **14** new tests in this module (13 model + 7 UI scenarios run
per target) and **4** in `:packages:design-system` for `ttError`, taking the baseline from
752/766/540 to **766 common, 790 `jvmTest`, 773 `iosSimulatorArm64Test`, 561
`testAndroidHostTest`**. `./gradlew check verifyArchitectureGraph verifyModuleImports` green
on all three targets.

**Three of this phase's four file-level assumptions were wrong, and the code says so where a
caller will meet it.**

**`KeyboardHints.kt` does not exist, and the plan's reason for it is not true in Kotlin.**
The plan asks for platform-neutral enums "because the package also builds for Android and
the JVM host", which is the original's reason: `UIKeyboardType`, `UITextContentType` and
`SubmitLabel` are UIKit types, and its package also builds for macOS so naming one at a call
site does not compile there. In Compose Multiplatform the problem does not exist.
`KeyboardType` and `ImeAction` are `expect`/`actual` whose *Kotlin* declarations are common,
and autofill — which the plan assumed had no common type — has
`androidx.compose.ui.autofill.ContentType` with real `Username` / `Password` /
`EmailAddress` factories, applied by the `Modifier.contentType` extension. All three of the
plan's enums would have re-abstracted something already spelled; the two fields build
`KeyboardOptions` and pass `Modifier.contentType` directly. `PlatformImeOptions` *is* an
empty stub on the desktop artifact, but nothing in the common API needs it.

**`PrimaryButtonStyle.kt` is `PrimaryButton.kt`, and it is a composable rather than a
`ButtonStyle`.** There is no Material3 in this project, so there is no `Button` to style and
no `ButtonStyle` interface to implement. The three reasons the original builds its own —
height and radius, a press response, and a spinner that replaces the label — all survive, and
the composable also makes the enabled and pressed states parameters it controls rather than
ambient state it reads.

**The auth gate lives in `SignInView`, and the first version of the view ignored it.** The
plan puts `MissingAuthSessionView` in `AuthEnvironment.kt` and never says who shows it, and
the first implementation rendered the form unconditionally — so 11.1's gate was unreachable
code, and `SignInView` took a prebuilt `SignInModel` that nothing could supply. The test
`aMissingSessionShowsTheGate` failed on that and fixed it: the view now reads
`currentAuthSession`, shows the gate when it is null, and builds the model with
`remember(session)`. Reading the environment rather than accepting a model also means the
tests exercise the same composition path a running app takes. The plan's `.previewSession()`
became the function `previewSession()`, because a `Modifier` decorates a node and providing
a `CompositionLocal` wraps content; a first attempt kept the name as a `Modifier` that
threw, which is worse than not having it.

**The plan's 5 UI tests became 7, and they are split across three files rather than one.**
A Compose UI test in `commonTest` cannot reach the Android host: that target needs
Robolectric, Robolectric only engages when a JUnit 4 runner is attached, and `@RunWith` is
not expressible in `commonTest` — and a `commonTest` class runs on *every* target, so a
single set of wrappers there fails on the host even with the host's own file beside it. So
the assertions live in `SignInScenarios` (no test framework in it) and
`SignInViewJvmTest`, `SignInViewIosTest` and `SignInViewAndroidHostTest` contribute entry
points only. One copy of every expectation, all three targets. The extra two tests over the
plan's five are the ones that found real bugs: the gate, and the banner's text leaving the
semantics tree.

**Three pieces of test infrastructure were missing project-wide, and none of them was
missing because of anything in this phase.** They surfaced only because this is the first
module with a Compose UI test, and all three failed identically on every screen, with
messages that point nowhere near their cause:

- The JVM host had no skiko, so every `runComposeUiTest` died in its constructor with
  `LibraryLoadException: Cannot find libskiko-macos-arm64.dylib.sha256`. Fixed with
  `jvmTestImplementation(compose.desktop.currentOs)`.
- The Android host had no `ui-test-manifest` and no Robolectric, so every test died with
  `NullPointerException: Cannot invoke "String.toLowerCase()" because
  "android.os.Build.FINGERPRINT" is null`.
- The catalog's `compose-testManifest` entry pointed at
  `org.jetbrains.compose.ui:ui-test-manifest`, **which has never been published for any
  version** — it 404s on Maven Central. That is why it sat in the catalog unused. The
  artifact that exists is `androidx.compose.ui:ui-test-manifest`, and
  `ui-test-android` is already built on it.

**Two findings in the data layer, left visible rather than fixed here.** `AppError.Unauthorized`
is a `data object` whose message is "Your session has expired. Please sign in again.", which
is correct for a request made *with* a dead session and wrong on the sign-in screen, where
the honest text is "That email or password is not right." Rewording it in the model would
break the rule the model is built on, and adding a rejected-credentials case means changing
`:packages:core`, a finished phase. So the banner shows the text the app can actually
produce and the mismatch is documented in `AuthPreviewData.failingSession` and pinned by
`SignInModelTest.everyAppErrorReachesTheBannerVerbatim`. Separately, `AppError.message` is
`String?` because it shadows `Exception.message` even though the constructor takes a
non-null `String` — which is why two tests need `requireNotNull` around it.

---

## Phase 13 (part 1, pulled forward) — making the sign-in screen reachable

Phase 11 built the sign-in screen and proved it works. It was still **unreachable from the
running app**: `AppGraph` registered nothing, and `TaskTrackerApp` rendered a
`ShellPlaceholder`. Every test in the project was green and the emulator showed a blank
canvas. Items 13.7, 13.8 and 13.14 are done; 13.9–13.13 are not, and the routing, splash
and navigation-host work stays in Phase 13.

- [x] **13.7a** Register the auth graph: `DateProvider`, `SessionStateBus`,
      `AuthRemotePort`, `KeychainAccess`, `PreferencesBackend`, `KeychainStore`,
      `TokenVault`, `UserDefaultsStore`, `AuthSessionPort`, in dependency order.
      **`AuthRemotePort` is `MockAuthRemote`**, which is what its own KDoc anticipated.
- [x] **13.7b** `AppGraph.start(host: Any?)` takes the platform host. Android's
      `systemKeychain()` and `platformPreferences()` both need a `Context`.
- [x] **13.8a** `AppGraphTest` inverted: the `assertFalse(registeredNames.isNotEmpty())`
      assertion is replaced by 5 tests over the auth graph's shape.
- [x] **13.7c** `AuthFlowThroughGraphTest` — a real sign-in, restore, relaunch and
      rejection through the production graph.
- [x] **13.14a** `ShellPlaceholder` deleted; `TaskTrackerApp` resolves
      `AuthSessionPort`, wraps the tree in `ttTheme`, and branches on the session.
- [x] **13.14b** `ShellScenarios` + 5 shell UI tests per target.

**Exit criteria**: the Android app shows sign-in, signs in, survives a process death and
signs out — verified on an emulator, not only in tests. Test baseline **766 common, 806
`jvmTest`, 789 `iosSimulatorArm64Test`, 577 `testAndroidHostTest`** (the `+3` per target is
`GlassFillContrastTest`, added by the follow-up below).
`./gradlew check verifyArchitectureGraph verifyModuleImports` green on all three targets.

> `./gradlew check ... --rerun-tasks` currently fails in `:packages:database`, where
> `lintAnalyzeAndroidHostTest` runs before KSP has regenerated the iOS
> `OutboxDao_Impl.kt`. Pre-existing and unrelated to any of the work below; the check is green
> without `--rerun-tasks`. Worth fixing before the flag is ever added to a CI script.

**Six bugs lived here, and not one of them was reachable from a test.** Every one was
found by installing the APK and driving it, which is the argument for doing that before
Phase 13 rather than after.

**`AndroidKeychain` could not write at all.** Every `set` threw
`InvalidAlgorithmParameterException: Caller-provided IV not permitted`. `KeyGenParameterSpec`
defaults `randomizedEncryptionRequired` to true, which tells the platform to generate the
IV and forbids the caller from supplying one — and this class must supply its own, because
the IV is prepended to the stored blob so `data` can reverse the operation without the
keystore keeping per-write state. Fixed with `setRandomizedEncryptionRequired(false)`, which
is only sound because a fresh IV is generated per write. This is unreachable from tests: the
JVM has no `AndroidKeyStore`, Robolectric has no real keystore, and `:packages:keychain` has
no `androidHostTest` source set at all. On a device, every sign-in failed with
"Could not sign in."

**The shell rendered a permanently blank screen, and this one was a test-double defect as
much as a shell defect.** The port's `sessionUpdates` has no replay and drops a `send` when
nothing is collecting. The shell's first version subscribed with a plain `launch`, which is
*dispatched* — so `restore()` published `Restoring` and `SignedOut` before the collector had
reached its subscription point, both sends were dropped, and the collector then attached to
a stream with nothing left to say. Fixed with `CoroutineStart.UNDISPATCHED`, which is what
"subscribe, then restore" actually requires. The deeper problem is that the shell's test
double used a `StateFlow`, which *replays* and so could never fail this way. It now uses the
real `Broadcaster`, and a mutation check confirms it: reverting `UNDISPATCHED` to a plain
`launch` fails 4 of the 5 shell tests.

**`AppGraph.start()` needed a `Context` it had no way to pass.** Android's `systemKeychain()`
throws `IllegalArgumentException` naming the missing type, and `TaskTrackerApplication` now
passes `this`. The composition root also gained an `internal` seam for the two platform
backends: registering the auth graph made every `AppGraphTest` reach `systemKeychain()`,
which cannot be constructed on the Android host, so the tests broke for a reason unrelated to
the graph. `PlatformBindings` defaults both to the real backends, so no production call site
can substitute one.

**The shell's earlier read-once design was also wrong, in a way no test would have flagged.**
It read the session state at mount and never again, so a *successful* sign-in left the form
on screen — correct-looking and inert, because `onAuthenticated` had no state to move. The
screen is now driven entirely by `sessionUpdates`, which is also the path a token expiry
takes; `onAuthenticated` is empty on purpose, since a callback that also moved state would
be a second route to the same screen. Relatedly, `SessionState.Restoring` is named
explicitly in the branch rather than falling through to sign-in: the repository publishes it
before reading the keychain, so a shell that treats it as signed-out flashes the sign-in form
at a user who is already signed in.

### Phase 13 (part 1) follow-up — the sign-in button was invisible, and it was three bugs

Reported as "a white shadow on top of the Sign In button, and when both fields are filled the
button goes completely white and the text is hard to see". Both symptoms were real, and the
cause was three separate defects stacked on one component. All three were found by measuring
pixels off an emulator screenshot (`adb exec-out screencap`), because every one of them is
invisible to a test that checks a colour token — `AccentContrastTest` was green throughout,
since `#34537A` really does clear 7.88:1 against the label colour.

**The vertical gradient was collapsing onto its last stop.** `ttGlassSurface` filled with
`Brush.verticalGradient(colors)`, whose `endY` defaults to `Float.POSITIVE_INFINITY`. A modifier
is built before it is measured, so that end is meaningless for an element whose height is not
known yet, and the gradient resolved to the *final* stop everywhere: the accent stop was
computed and thrown away, and only the `canvas` stop was ever painted. It is now drawn through
`drawBehind` with an explicit `endY = size.height`, which is the size the element actually got.
This one was in the shared material, so it was wrong for every card and every glass control,
not just the button.

**A translucent fill cannot carry an opaque label.** The original's sign-in button was a solid
`Color.accentColor` with a `glassEffect` lens *over* it; the port had collapsed the lens into
the fill, so 62% accent landed on a near-white canvas and composited to `#F6F7F8` — measured,
with the white label at **1.07:1**. `ttGlassSurface` gained a `base` parameter for exactly
this: an opaque fill under the lens, which restores the original's structure. The lens is white
at `ttGlass.LENS_ALPHA = 0.08`, and that number is arithmetic rather than taste: the dark accent
starts at 5.65:1, and a lens matched to the hairline's 0.14 would drop it to 4.30:1 — a control
that passes the token suite and fails in dark mode.

**`Modifier.alpha(1f)` was suppressing the fill outright.** With `.alpha(if (enabled) 1f else
0.4f)` sitting *after* `.ttGlassSurface(...)`, the button painted a correct accent gradient when
disabled and painted nothing at all when enabled, and the 0.4 case did not actually dim
anything either. Hoisting `.alpha` above `.ttGlassSurface` — so it wraps the whole control,
next to the press scale, which is also what "the disabled control is dimmed" means — fixed both
states. The original ordering made the most common state the broken one.

Measured on `emulator-5554`, light appearance, white label on the composited fill:

| | before | after |
| --- | --- | --- |
| enabled | 1.18–1.21:1 | **6.67–7.54:1** |
| disabled | 1.18–1.35:1 | 2.08–2.15:1 (dimmed by design) |

The disabled figure is the original's `.opacity(isEnabled ? 1 : 0.4)` and is left there
deliberately; WCAG 1.4.3 exempts inactive controls, and lowering it further would cost the
enabled state's margin for no legibility gain.

**`GlassFillContrastTest` is new, and it exists because of this.** It asserts the ratio of the
fill a surface *composites to* rather than the token, for both accent variants, at the lens's
full strength. Reverting `LENS_ALPHA` to 0.14 fails it with the exact measured number. Its WCAG
maths is deliberately duplicated from `AccentContrastTest` rather than shared, so that an edit
to one helper cannot quietly relax both suites.

**On measuring this at all.** Two measurement mistakes cost more time than the fix. Compose's
`captureToImage` returns an all-black bitmap on the JVM skiko host, so a colour question cannot
be answered off-device at all. And the first contrast helper summed gamma-encoded sRGB channels
and then added the WCAG `0.05` offset, which is only defined against linear light — it reported
the fixed `#466286` fill at 2.69:1 when the correct answer is 6.23:1, and briefly made a passing
button look like a failing one. Pixel probes also have to be placed from the accessibility
bounds rather than by eye: the semantics node is 102px tall and the visible control is inside
it, so several early samples were reading the backdrop and reporting that a correctly-painted
button did not exist.

### The sign-in field-to-field gap: 28dp → 16dp

The sign-in form's two fields were the widest gap in the form, which is backwards. Measured on
`emulator-5554` at density 3.0 with both fields filled:

| gap | token | before |
| --- | --- | --- |
| title → Email field | `formStack` | 24dp |
| **Email → Password** | `fieldStack` | **28dp** |
| Password → banner slot | `formStack` | 24dp |
| banner → Sign In button | `formStack` | 24dp |

So the two fields sat further apart from *each other* than either sat from the rest of the form.
A group holds together by its members sitting closer together than the group sits to what
surrounds it, and at 28dp the two credential fields read as separate blocks rather than as one
form. It was 84px against a 191px (63.7dp) card — nearly half a card height of air between two
members of the same group.

`fieldStack` is now **16dp** (`xxl`), which puts the rhythm at 24 / **16** / 24 / 24 and drops
the gap-to-card ratio from 0.45 to 0.26.

**The value was wrong for a reason worth recording, not just because it looked wrong.** Every
other off-ramp value in `Spacing.kt` is chosen against something it has to agree with — a safe
area, a minimum column height, a whole-point grid. `fieldStack`'s entire justification was
"one step past `[xxxl]`", which is an argument about where a number sits in a progression
rather than about the relationship between two fields, and it is precisely that argument which
produced the value. So it moved out of the off-ramp values and became a named alias of a ramp
step, which is the treatment `controlPaddingH` and `formStack` already had, and
`the ramp aliases stay on the step they name` now asserts all three. Mutating `fieldStack` back
to 28dp fails with `expected:<16.0.dp> but was:<28.0.dp>`, so the claim is held rather than
merely stated.

The original's 28 stays in the KDoc's inventory of the original's outliers — that list records
what the port *was given*, not what it kept — and the class doc now says so explicitly, because
a reader who found an unbacked 28 in that list would otherwise be right to assume it was an
oversight and restore it.

**Verified on device after the change**: the card gap reads 48px (16.0dp) and the `Password`
label moved from y=850 to y=814 — 36px, exactly the 12dp the token lost. The button and
everything below it moved up the same 36px and still clear the fold. The error-banner state
(`MockAuthRemote.REJECTED_PASSWORD`) was checked too: the banner occupies the pre-existing slot
between the password field and the button, which is `formStack` and untouched, so the field gap
change shifts the whole lower half up without changing anything about the banner.

---

## Phase 13 (part 1) follow-up #2 — the projects list became reachable, and the New Project button did not

`ShellPlaceholder` was replaced with a real signed-in shell: the projects list, a board per
project via `Router.openProject`, the `NewProjectSheet` as local state, a header with sign-out,
and a `MissingProjectEnvironmentView` when the graph has no projects repository. The scope was
deliberately narrow — no tab bar, no `NavigationHost`, no Backlog/Archive routes, no routable
editor (13.9–13.13 remain open).

**Two gaps were closed in the presentation module because the shell exposed them.**
`NewProjectModel` had no `details` field, so the sheet's third field was discarded, and
`ProjectBoardModel` had no `fileAway` verb, so the board's **File Away** button — drawn since
Phase 12 — had nothing behind it. Neither is visible to a model test: a model cannot be asked
whether a button above it has a caller. Wiring the feature into a host is what surfaced both.

**The New Project button was measured at zero height, and never had any coverage.**
`ProjectsListScreen` put its `LazyColumn` in a `Column` with `Modifier.fillMaxSize`, so the list
took the whole height and the button below it was measured with none left — laid out past the
bottom of the screen, present in the tree, hittable by nothing. It only showed up with **at
least one project in the list**: the empty state draws no list, so every earlier scenario
rendered the button in the one configuration where it worked. With the app's own seeded sample
project the app opened on a screen whose only way to create a project could not be pressed.
`weight(1f)` on the list (and on the empty state) keeps the button measured and pins it while
the list scrolls. `theNewProjectButtonWorksWithRowsPresent` presses it with rows present, because
a zero-height node can pass a "is it in the tree" assertion and the bug is that it cannot be
reached.

**The signed-in identity and Sign out header were removed.** The shell drew the signed-in
email on the left and "Sign out" on the right above every signed-in screen, which put it on the
board as well as the list, where it cost the columns real height. It is gone from both.
`SignedInShell` keeps its wrapper and its `shell.signedIn` tag — a test still asserts on the
signed-in area as a whole — but it now hosts the content and nothing else. The board's topmost
node moved from y=96 to y=114 with the header's row gone from the tree entirely.

**The app can no longer be signed out by hand, and that is the deal.** `TaskTrackerApp.kt:174`
was the only `signOut()` call site in production, so removing the button removed the
affordance with it. Asked and confirmed rather than assumed. What survives is the machinery:
`AuthSessionPort.signOut` is untouched, and the `LaunchedEffect(current)` that wipes every
navigation stack on a non-signed-in state is untouched — so a session that ends by expiry still
takes exactly the same path, including `enterAuth`'s stack wipe. Phase 13's settings screen is
the intended home for the control; when it lands, this is a button to add rather than a session
path to build.

**What the scenarios cover changed, and deliberately so.** The three that clicked the button now
call `session.signOut()` on the fake port directly. The *behaviour* on a session ending is still
asserted — navigation cleared, observation stopped, signed-in tree torn down — because it still
happens. What is no longer asserted is that a button exists. In its place,
`theSignedInShellOffersNoSignOut` asserts the header's **absence**, so the removal is a pinned
contract rather than an untested edit: whoever adds sign-out to settings has to delete that
scenario in the same change, which is the only way the removal and the re-add stay in step.

**Nothing in the app consumed window insets, so every screen was laid out from y=0.** Removing
the header is what made it visible — with a row of its own above the content, the content's
position was an accident of the header's padding rather than something anyone had decided.

`MainActivity` calls `enableEdgeToEdge()`, which is only half of that promise: the tree draws
*under* the system bars, and something has to move the content back below them. Nothing did.
`grep` for `WindowInsets` across `packages`, `shared-ui`, `composition-root` and `androidApp`
returned zero hits. On the emulator the consequence was concrete — `dumpsys window displays`
reports a display cutout at `x=586..695, y=0..156`, and the board's project switcher sat at
y=114, inside the notch.

The fix is one `Box` at the root of `TaskTrackerApp`, wrapping all three session branches:

    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))

Three decisions in that one line:

- **`safeDrawing`, not `systemBars`.** A camera cutout is not a system bar, and on this device
  the cutout is the *taller* of the two — 156px against a 72px status bar. `statusBars` would
  have left the content under the cutout anyway, which is the bug being fixed.
- **Top only.** The bottom is a different question with a different right answer: a list should
  scroll *under* the gesture bar and give only its last row clearance, so that belongs in
  `contentPadding` on the scrolling container. Padding the root would shorten every list in the
  app on the strength of a guess.
- **The root, not per screen.** A per-screen inset is a per-screen bug waiting for the next
  screen. `windowInsetsPadding` also *consumes*, so children cannot double-apply it.

Measured after the change: the board switcher moved y=114 → y=270, exactly the 156px inset,
clearing the cutout with room to spare; the projects list's first row moved to y=291. The bottom
did not move — the New Project button still spans y=2653..2759, unchanged — which is the
confirmation that the inset is top-only rather than a general shrink.

The insets are zero under `runComposeUiTest`, so no scenario changed behaviour; `shared-ui`
stays 12/12 on JVM and iOS. The sign-in screen is fixed by construction — it is the third branch
of the same `Box` — but it can no longer be *reached* on the emulator, since the only sign-out
affordance was just removed. Verifying it visually needs `adb shell pm clear`, which would also
destroy the seeded projects.

**The board's lane row ran to neither edge, and it did not snap.** Three reports, all one cause
and one follow-on. The screen gutter was applied to `ProjectBoardScreen`'s whole `Column`:

    Column(modifier = …fillMaxSize().padding(ttSpacing.screenGutterLarge))

and the report was not "there is a border" but "there is a thick one on the left and the right"
— there is no line, there was a 32dp gutter, and at 3x that is 96px of canvas either side of the
lanes. It looked like a frame because the lanes *scroll*: a gutter on the row's container is a
gutter on the scroll **viewport**, so it was there at every scroll position rather than only at
rest. Measured, the row spanned x=96..1184 on a 1280px screen and a lane arriving from the right
stopped 96px short of the edge, with bare canvas beside it — which is why the page background
read as a panel lying *on top of* the incoming lanes rather than behind them.

The gutter now goes on each child that should be inset — the switcher, the error banner, the
empty state, via one `horizontalGutter()` — and the `Column` keeps vertical padding only. The
row's own gutters are `LaneRowSpacer`s *inside* the scrollable content, which is what a
`LazyRow`'s `contentPadding` does: they scroll away with the lanes, so the viewport is the full
width of the screen and no lane is ever inset from either edge. Both ends get one, for different
reasons: the near end so lane 0 rests with air at the left rather than against the bezel, the far
end because it is the only end that can be scrolled *away*. Reinstating `Arrangement.spacedBy`
would have put 8dp either side of the spacers and made the gutter 40dp rather than the 32dp
everything else uses, so the row spells its gaps out instead — which is also what keeps the
content adding up to `boardLaneStride`.

**Snapping is a custom `SnapLayoutInfoProvider`, and Compose ships the same idea.** A free fling
across four 264dp lanes lands anywhere, so the row now takes `rememberSnapFlingBehavior`. What
that needs is a provider, and both of Compose's own read their snap positions out of a *measured
lazy* layout — which this row deliberately is not, because a lane's height must not depend on how
far the user has scrolled (see `ProjectBoardScreen`). So `BoardLaneSnap.kt` derives the
positions from the stride instead: resting position of lane *i* is `i * stride`, and the leading
gutter cancels out of that arithmetic entirely.

Three decisions in that one file:

- **The arithmetic is a pure function**, `boardLaneSnapOffset`, taking a position, a velocity and
  a stride. It is the interesting behaviour and it is testable exactly; a UI test flinging a real
  row would assert the same rule plus Compose's fling animation, the frame clock and the device
  density, and would only run where a Compose harness exists.
- **One call moves one lane, not the whole decay.** Compose asks repeatedly as a fling runs with
  the velocity falling, so a hard flick covers several lanes and a gentle one covers one — travel
  proportional to the gesture, as a carousel's is, and always ending on a lane. Resolving the
  whole decay in one call would have meant owning a second decay model to duplicate the one
  `rememberSnapFlingBehavior` is already running.
- **The end of the row is clamped, and the clamp is not arithmetic.** Four lanes of 816px is a
  3432px row against a 1280px screen, so the last lane's own boundary at 2448px is past the end
  of the scroll range and it rests short of it. `floor(scroll / stride)` names lane 2 there, not
  lane 3, and trusting it cost the user a lane per back-fling at the end of the row — two
  back-flings to travel one row's worth. At the end of the range the index is the last lane
  outright.

Measured on the emulator after the change: the scroll container is x=0..1280, lane 0 rests at
x=96, and a lane arriving from the right now runs to x=1280 instead of stopping at 1184. Four
successive swipes settle at scroll 0 → 816 → 1632 → 2152 and then hold at 2152; four back-swipes
return 2152 → 1632 → 816 → 0. Every one of those is a lane boundary, and 2152 is the clamp.
`feature-projects-presentation` is 146/146 on JVM *and* iOS, the entry points on all three test
targets having been regenerated together — a `@Test` missing on one of them is exactly the kind
of thing that leaves a suite quietly weaker on one platform than the other.

**A board card's tap is still inert, on purpose.** `ProjectBoardScreen.onSelect` is wired to
`{}` in `ProjectsHost` because a work item's editor has no route in this host yet. It is left
explicit rather than left looking wired.

## Test-count baseline

| suite | target | tests |
| --- | --- | --- |
| `design-system` | JVM / iOS | 73 / 70 |
| `shared-ui` (shell scenarios) | JVM / iOS / Android host | 18 / 18 / 18 |
| `feature-projects-presentation` | JVM / iOS | 146 / 146 |
| `feature-projects-domain` | JVM | 80 |
| `composition-root` | JVM / iOS | 37 / 35 |

Android-host UI tests remain blocked on the known Compose resource-packaging failure recorded
below. Its composition-root model tests run, and five of them are additionally blocked on the
Robolectric-sandbox gap described in [Where things stand](#where-things-stand).

---

## Phase 12 — `:packages:feature-projects-presentation`

The largest presentation surface: 24 files.

- [x] **12.1** `ProjectEnvironment.kt` + `ViewPreviewData.kt` fixtures.
- [x] **12.2** ViewModels (7) — `ProjectsListModel` (holds summaries, re-reads on
      `ProjectChange`, exposes a `notice` for the archive-instead-of-delete outcome),
      `ProjectEditorModel` (draft with `canSave`/`hasChanges`, loaded as a snapshot so
      a concurrent remote rename loses), `ProjectBoardModel`,
      `ProjectBacklogModel`, `WorkItemEditorModel` (**a draft, never a stored
      record** — a form bound to a persisted row could save an edit with no outbox
      row), `ProjectArchiveModel`, `ProjectPickerModel`.
- [x] **12.3** Screens (8) — `ProjectsListScreen` (swipe-delete + confirmation naming
      the archive rule), `ProjectEditorScreen` (key prefix **read-only**),
      `ProjectBoardScreen`, `ProjectBacklogScreen`, `ProjectArchiveScreen`
      (month-grouped, newest first, terminal-only actions hidden),
      `ProjectPickerSheet`, `WorkItemEditorScreen`, `WorkItemEditorSheet`.
- [x] **12.4** Views (7) — `BoardColumn`, `WorkItemCard`, `WorkItemBacklogRow`,
      `ProjectRow`, `PriorityBadge`, `ProjectSwitcherButton`, `NewProjectSheet`.
- [x] **12.5** 4 Compose UI tests: card content rules, column behaviour, the archive
      round-trip from **both** places a task can be filed from (backlog row button
      and board card menu), because archiving is a status move and the two surfaces
      must agree.

**Exit criteria**: all green.

### Notes on 12.3–12.5

**A card's actions are a slot, not a menu.** 12.5 says "board card menu". The port
draws a visible button in the card's action slot instead, for the reason given in
`ProjectBoardScreen.kt`: filing away is offered as a button on the backlog row too,
and two visible buttons that do the same thing are easier to keep in step than two
menus. `BoardColumnView` takes a `cardTrailing` slot so the board's verbs are decided
in one place rather than per column.

**Swipe-delete is hand-written.** 12.3 requires it, Material3 is not on the
classpath, and Compose Multiplatform's swipe-to-dismiss lives in Material3 — so
`design-system`'s `TTSwipeToRevealRow` is built from `detectHorizontalDragGestures`
and `Animatable`. Revealing never *acts*: passing the threshold opens the same
confirmation the Delete button opens, because whether a project is archived or
deleted depends on its unfinished work and only the confirmation can say which.

**The Android host cannot read Compose resources — a build bug, not a test bug.**
`check` passes on the JVM and iOS targets but `testAndroidHostTest` fails every UI
entry point with

    MissingResourceException: Missing resource with path:
    composeResources/com.tasktracker.resources/values/strings.commonMain.cvr

**This stopped being a test-only problem: it crashed the app.** The first Android
application module is here now, so the same missing asset killed the process on the
first frame — the first `stringResource` in the first composition threw and the app
died during launch, with a stack trace pointing at Compose rather than at the build.
Confirmed on `emulator-5554`: `FATAL EXCEPTION: main … MissingResourceException`.

**Root cause.** Compose Multiplatform 1.12.1 registers
`copyAndroidMainComposeResourcesToAndroidAssets` and wires it with

```kotlin
componentSources.assets?.addGeneratedSourceDirectory(copyTask, CopyResourcesToAndroidAssetsTask::outputDirectory)
```

That `?.` is the bug. On AGP 9.4.1 the KMP library variant's assets source is **null**
(verified directly: `variant.sources.assets` prints `null` for `androidMain`), so the
wiring is silently skipped and the task is left with an unset `outputDirectory`. It never
appears in any task graph — `assembleAndroidMain` does not contain it — and no
`assembledResources/androidMain` directory is ever produced. `:packages:design-system` is
the only module that configures `compose.resources`, so it is the only one that produces
these files, and nothing packaged them.

**Fix, in `:androidApp`.** A `Sync` task copies
`preparedResources/commonMain/composeResources` (the one directory that *is* produced)
into `assets/composeResources/com.tasktracker.resources/`, re-rooted under the resource
package name, handed to AGP through `androidComponents.onVariants` +
`addStaticSourceDirectory`, with the `merge*Assets` → copy dependency declared by hand
because `addGeneratedSourceDirectory` — the variant that would infer it — wants a
`DirectoryProperty` and `Sync` exposes a plain `File`. Two things had to be discovered
the hard way: the legacy `android.sourceSets` API rejects a `Provider` outright on AGP 9
("You cannot add Provider instances to the Android SourceSet API"), and a path there
carries no task dependency, so a clean build silently packages an APK with no resources
in it. Both are commented at the call site.

**Verified on device after the fix**: `assets/composeResources/com.tasktracker.resources/
values/strings.commonMain.cvr` is in the APK; the app launches, restores its session,
lists Apollo and Gemini from Room, opens a board, returns to the list, and creates a
project through the New Project sheet. No exceptions in logcat.

**The Android-host UI tests are blocked too — and option 2 below was the answer, in a form
that turned out to be better than it looked when it was written.** They run in *library*
modules, and for AGP-KMP library components there is no assets source to add to at all:
`variant.sources.assets` is null (above), and `kotlin.android.sourceSets` is the *Kotlin*
source-set container with no `assets` property, so the legacy route is unavailable too. Both
were re-checked against `gradle-api-9.4.1` before choosing this, and two more were added:

3. `onVariants` on a KMP library yields **only** the `androidMain` variant. There is no
   `hostTestVariants` accessor on `KotlinMultiplatformAndroidComponentsExtension`, so the host
   test variant is unreachable through the variant API at all.
4. `variant.sources.resources` is *not* null, only `assets` is — which is a trap worth naming,
   because it looks like the escape hatch and is the wrong one: java resources reach the
   classpath, and the reader does not look there.

**What settles it is where the reader looks.** `DefaultAndroidResourceReader` resolves every
resource through `context.assets.open(path)` — no classpath fallback, confirmed by
disassembling the class — and Robolectric 4.16.1 has no `assets` option on `@Config` or in
`robolectric.properties`. Robolectric's asset directory therefore comes *only* from
`android_merged_assets` in the generated `test_config.properties`, which points at
`intermediates/assets/androidHostTest/mergeAndroidHostTestAssets`. So the files have to
physically land in that directory, and the convention plugin copies them there.

This is option 2, but pointing at a generated directory instead of a checked-in
`src/androidHostTest/assets/` tree, which is why it is no longer last on the list of
dislikes: no binary content is committed, and nothing about the resource format is hard-coded
in the repository. `Copy`, not `Sync`, because `mergeAndroidHostTestAssets` legitimately
produces `PublicSuffixDatabase.list` and a `Sync` would delete it. Writing into another
task's output directory is an implicit dependency that Gradle 9 rejects outright, so
`packageAndroidHostTestForUnitTest` gets an explicit `mustRunAfter`.

**Verified from a clean build** (`:shared-ui:clean` first, because a stale directory would
pass for the wrong reason): 22 tests, 21 pass, 1 failure — down from 22 failures. Across the
whole target, **10 failures of 818**, down from 66. The exit command
`jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports
:androidApp:compileDebugKotlin` is green.

The 10 that remain are not this bug, and are accounted for above: five `:composition-root`
assertions that need a `Context` from a runner `commonTest` cannot carry, and five Compose UI
assertions whose nodes fall outside Robolectric's much smaller default viewport.

### The glass fill was fading into the page — the white shadow, again

**The "white shadow" fix (projects list + board + New Project button).** The same
artifact reappeared on the projects list: a white smear along the bottom edge of
`ttGlassCard` rows and along the bottom of the `ttGlassButton`-based New Project
control. The root cause was identical.

**The ramp was fading the fill *into the page*.** The unbased glass fill's bottom
stop was `canvas.copy(alpha = 0.30f)`. Any stop that is a canvas colour at any alpha
composites to exactly the page over the page, so the last rows of every surface
were the background. On-device inspection: a project card ramped from `(195,204,216)`
at its top edge to `(251,251,252)` at its bottom, against a `(250,250,250)` page.
The fix separates two different things:

1. The **alpha** stays constant down the surface (no material fade-out), because
   glass is translucent top-to-bottom.
2. The **hue** travels from the tint toward the canvas by only [ttGlass.FILL_FADE]
   (0.40) at the bottom, never reaching it. A new pure function
   [ttGlass.fillRamp] computes the stops, so the arithmetic is testable in
   isolation — a `@Composable` could not have caught this.

The new `GlassFillRampTest` enforces: the bottom edge is never the page, alpha is
constant across both stops, the gradient stays lit at the top and calmer at the
bottom, caller tint alpha is respected, and a prominent control's stops remain
in-range. Two mutations were tried and both are caught — restoring
`canvas.copy(alpha = 0.30f)` as the bottom stop, and fading alpha along the ramp
without reaching zero — failing 2 of the 5 tests each, against a green suite on the
fixed code.

**Verified on device:** after reinstall, the New Project button ends at `(145,162,184)`
against the page `(250,250,250)` — visibly a blue control with a clear bottom edge;
the bottom-most Gemini card ends at `(217,223,230)` — the surface no longer
dissolves into the list background; the board switcher slab and work-item cards
show the same clean, non-white-bottom edge. No new crashes, and all JVM/iOS/unit
checks remain green.

(`design-system`: +5 tests, 61 on the JVM target, all passing.)

---

## Phase 13 — Settings + composition root + shared-ui shell

### `:packages:feature-settings-domain`

- [x] **13.1** `SettingsPort.kt` — connectivity read + fresh update stream,
      `setForcedOffline` phrased as **intent** rather than a value to install, sync
      activity + updates, `canSyncNow`, `syncNow`, `endSession` (**a closure the app
      injects** — the settings package does not name the auth feature or the session
      coordinator), `appVersion()`.
- [x] **13.2** `SettingsVocabulary.kt` — `ConnectivitySummary` and `SyncActivity`
      (7 phases) with user-facing titles, plus `SyncOutcome` and `AppVersion`.
      Deliberately **not** `NetworkMonitor.Connectivity` or `SyncStatus`.
- [x] **13.3** `SyncNow.kt` — owns the offline decision. A control that goes dead for
      an invisible reason leaves the user concluding the app is broken, so it answers
      `.offline` and the screen can say the edits are queued and safe.
- [x] **13.4** `commonTest` — 9 tests: `SyncNow` outcomes, port counters.

### `:packages:feature-settings-data`

- [x] **13.5** `LiveSettingsService.kt` — the only file that knows a settings screen
      is fed by the network monitor and the sync coordinator. Every `when` is
      deliberately **total, no `else`**, so a case added upstream becomes a build
      error here rather than silently disappearing.

### `:packages:feature-settings-presentation`

- [x] **13.6** `SettingsModel.kt`, `SettingsScreen.kt`, `SettingsEnvironment.kt`,
      `SettingsPreviewData.kt`. Rows appear only when their numbers are non-zero, so
      the quiet state cannot stand in for the alarming one. The About row hides itself
      when there is no version key.

### `:composition-root`

- [x] **13.7** *(auth only, done ahead of this phase — see "Phase 13 (part 1)" above.)*
      Register the object graph in 7 modules (platform, persistence,
      projects, auth, sync, session, settings), in dependency order. The three
      looping services (monitor, sync coordinator, timer) are **explicitly started**,
      never started during resolution — factories must stay a pure lookup. **Auth is
      registered; the other six are not.**
- [x] **13.8** **Invert `AppGraphTest`.** It asserted
      `assertFalse(resolver.registeredNames.isNotEmpty(), "expected an empty graph")`.
      Replaced, not deleted: 5 tests over the auth graph's registrations, scopes and
      sharing, plus `AuthFlowThroughGraphTest` for the flow itself.
- [x] **13.9** Add the missing `AppGraphAccessTest`. `AppGraph.kt`'s kdoc already
      claims it exists and that it fails if anything outside the composition root
      calls `KoinResolver` directly. It does not exist. Write it, or delete the claim.
- [x] **13.10** `SessionCoordinator.kt` — `SessionTeardown` (clears pull metadata and
      session cache, resets the container's session scope, stops the sync
      coordinator) and the coordinator that turns `SessionState` into `Router.root`.
      `hasLiveSession` guards a cold launch from treating "no credentials" as
      "session ended". **Outbox rows are kept** — they carry the queuing account's id
      and only that account can claim them.

### `:shared-ui`

- [x] **13.11** `RootScreen.kt` — holds the splash until **both** a minimum floor has
      elapsed **and** the session has answered. Either half alone is wrong. Restores
      the last visited project **twice** (`onAppear` and on `root` change), both
      idempotent, because the router is on the auth gate while the async restore runs.
- [x] **13.12** `SplashScreen.kt` — canvas + `AppMark` + name, drawn on the ambient
      backdrop so the hand-off from the launch screen is invisible and the
      splash→sign-in cross-fade has a static background.
- [x] **13.13** `NavigationHost.kt` — the Compose navigation host that renders the
      hand-rolled `Router`. `ProjectScopedTabScreen` is the root of both project tabs:
      the active project's screen, or a picker sheet when nothing is active. The
      scoped screens take a **non-optional** project id and rebuild on id change — a
      mid-view project switch must stop the old model or its collection keeps
      refreshing a board nobody shows. Tapping a project row routes through
      `openProject`, never a raw push.
- [x] **13.14** Delete `ShellPlaceholder()` from `TaskTrackerApp.kt`. `TaskTrackerApp`
      now resolves `AuthSessionPort`, wraps the tree in `ttTheme` and branches on the
      session. `SignedInShell` stands in for the content tree and is replaced by 13.13.

**Where the session boundary lives**: `SessionCoordinator.kt` is in `:composition-root`, not
`:shared-ui`. It is listed under `:composition-root` above rather than under `:shared-ui` where
it was originally filed, because it is the only thing allowed to call `Router.beginRestoringSession`,
`enterMain` and `enterAuth`; a file in `:shared-ui` that could route would make the one-owner
rule unenforceable rather than merely stated.

**Exit criteria**: `./gradlew jvmTest iosSimulatorArm64Test verifyArchitectureGraph
verifyModuleImports :androidApp:compileDebugKotlin` green — **1052 `jvmTest`, 1031
`iosSimulatorArm64Test`**. `testAndroidHostTest` is **not** green and is documented above and
in [test-count baseline](#test-count-baseline); it was already red before this phase, and this
phase's contribution to it is a real capability gap rather than a new fault.

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

## Follow-up — the tab lens drew its corners square

The bottom navigation's selection indicator is the lens: a translucent wash and a rim. A user
reported "a strange top-right and top-left squared shadow" on it.

It was not a shadow. The lens has never had one — `Modifier.shadow` appears twice in the whole
tree, on the bar and on the app mark. It was the lens's own fill, drawn as a plain `drawRect`
while only the *rim* was drawn with `CircleShape`. So the four corner wedges of the rectangle were
filled with white and nothing cut them back to the capsule, and the rim was drawn around the parts
that stuck out: a rectangle wearing a capsule's outline. `TAB_LENS_FADE` puts the gradient's bottom
stop at a quarter of the top one, so the top two wedges — the ones at `startY = 0`, where the
gradient is at full strength — are the ones you can see, and the bottom two had gone unnoticed.

The fix is `.clip(CircleShape)` immediately before the `drawBehind`, one modifier, chosen so the
fill and the rim are the *same shape object* and cannot disagree about where the lens ends.

### What it measured

`packages/design-system/src/commonTest/…/TabLensCornerTest.kt`, written against the *unfixed*
component first so that it could never be a test that has never failed. Unclipped, the wash
reached past the bar's glass into the top-left corner of its own bounding box by:

| appearance | JVM | iOS simulator |
| --- | --- | --- |
| light | `#09` | `#09` |
| dark | `#21` | `#21` |

**Both hosts agree to the byte, which is the opposite of every other measurement on this
component.** The captions differ between the JVM and iOS by most of a point of contrast because a
stroke's coverage is a rasteriser's business; this is a hard-edged rectangle overdrawing its own
silhouette, and both rasterisers agree exactly how much white lands in the corner. It is still
worth having run on two hosts rather than one — "the hosts agree" is a result, not an assumption.

### The part that took the work: a pixel diff can be green for the wrong reason

The test renders two bars in one frame with the lens on tab 1 in one and tab 3 in the other, and
everything else blank, and differences them. That construction has a vacuous-green regime, and
the `test-reviewer` subagent found it:

- The only structural guard was "some pixels differed". If anything *other* than the lens ever
  differs in the window, the bounding box becomes the whole window, the four corner probes land on
  bare glass three points outside the lens, **both bars agree there, and the test passes with the
  lens's corners as square as you like.** That is not hypothetical here: `TabBar.kt` already
  records an empty child darkening its backdrop by 0.14 of luminance as a defect that shipped,
  and the selection *does* rescale the tab cell, so the two bars really do differ in ways that
  have nothing to do with the lens.
  → Fixed by asserting the footprint does not touch an edge of the window. That also converts the
  KDoc's load-bearing premise from a comment into a checked property: a lens at rest is inset
  `tabBarLensInset` on every side of its segment, and at the peak of its stretch it is
  `TAB_LENS_WIDEN` wider and would overhang the window.
- The corner probe was a fixed 4 pixels, justified by a clearance figure that only holds at density
  3. At density 1 the arc is ~7.9 pixels from the corner and a 4×4 block reaches ~4.2, leaving
  ~2.7 pixels — so any future failure would have been a flake indistinguishable from the defect.
  → Fixed by deriving the block from the footprint's own implied radius, `sqrt(2) - 1` of it,
  capped.
- The threshold pair had a two-step dead band: an overhang of five colour steps entered neither the
  footprint (floor 5.1) nor the ceiling (6.4). `NOISE_FLOOR` came down to three steps — still 3×
  the compositing phase it rejects — which widens the visible band to six.
- The two bars' geometry was assumed equal rather than checked, so a one-row misalignment would
  have made the diff an edge detector of the glass ramp. Now asserted.
- The stated reason the empty `ImageVector` preserves the bar's height was wrong (`ttIcon` sizes the
  image from `ttIconSize.tab`, not from the vector's intrinsic size). The conclusion held; the
  reason did not.

**Exit criteria**: `TabLensCornerTest` green on `jvmTest` and `iosSimulatorArm64Test`; removing
`.clip(CircleShape)` fails both.

**On the baseline this moved**: `jvmTest` went 1051 → 1052 and `testAndroidHostTest` 817 → 818,
both exactly this one test. `iosSimulatorArm64Test` went 1029 → 1031, and the extra one was not
mine — the recorded 1029 was already a stale figure. Counted from the XML and cross-checked
against `@Test` declarations, the iOS total before this change was 1030. No stale or duplicate
result files were involved: every XML in the tree was written by the same run. Recorded here
because this is the third time these two numbers have drifted apart, and the drift was in the
baseline rather than in a count taken from the build.

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
