# Decisions

The design record for this project — why things are the way they are. Curated from the
finished port log (removed from the tree; recoverable from git history) in October 2026; that
work is history and
this one is the contract. A rule lives here only if breaking it has already caused a
real bug. Everything operational — how to build, run, and test — lives in `Project.md`;
how to work here lives in `AGENTS.md`.

## Structural decisions

- **Navigation is a hand-rolled `Router`** — pure state in `:packages:routing`, a module
  with no Compose dependency so it cannot hold composables. `:shared-ui` owns the Compose
  host that reads it. No third-party nav library.
- **Strings live in `:packages:design-system` only.** It is the only module that
  configures `compose.resources` (two doing it collide at dex merge). The original had
  zero localization; this fixes that gap deliberately.
- **No backend.** `MockAuthRemote` and `MockSyncTransport` make the full push/pull cycle
  exercisable offline.
- **The database is an injected `DatabaseConfiguration`, not a host.**
  `:composition-root` has three substitutable backends (`keychain`, `preferences`,
  `database`) and one honest `host: Any?`. A defaulted host would throw without a
  `Context` during graph construction, and production must not reach an in-memory store
  by omission. A configuration also keeps the graph buildable on targets that cannot
  open a store (the Android test host).
- **A composable does not tag its own container; the caller does.** `testTag`
  overwrites rather than merges, so a self-tag plus a caller tag means one of them is in
  no semantics tree at all — invisible to every test that does not use it.
- **The shell's layout is chosen by width, never by platform.** `ShellLayoutClass` is
  derived from the `Dp` the shell was offered, and nothing anywhere branches on the
  platform to decide a layout. `isDesktopApp` already exists and is the tempting source;
  it answers which *process* this is, not how much *room* it has, so a Desktop window
  dragged to 700dp would be handed a two-pane layout that does not fit while a 1024dp
  tablet was handed the phone one. The bug is already measured in this repo:
  `TTGlassTabBar`'s KDoc records a 540px bar rendered in a 1024px window, because the
  bar's geometry was derived from a segment width that assumed a phone.
  One threshold, at `ShellExpandedMinimumWidth` = 840dp, pinned by an exact-value test.
  Material's medium class at 600dp is rejected rather than overlooked: between 600 and
  840 a side rail plus a single pane costs horizontal room and buys nothing, because the
  second pane that would spend it is the thing the class is gating — and a third class
  would make "which chrome" and "how many panes" two answers to one question.
  The class is read with `BoxWithConstraints` in `commonMain`, **not** an
  `expect`/`actual` size class: that needs three actuals, and the JVM test host has no
  real window for the `jvm` one to read, so it would need a test-only override anyway —
  while `ShellScenarios.setUpShell(width = …)` already supplies the width on all three
  hosts. `LocalShellLayoutClass` defaults to `Compact` for the reason
  `LocalTabBarScrollUnderInset` defaults to `0.dp`: a screen rendered outside the shell
  lays out exactly as it did before any of this existed.

## Sync and storage invariants

Each of these has already caused a real bug when broken, in the original or here:

1. **One transaction writes both** the record and its outbox row. A two-step version
   strands edits on a crash with nothing queued.
2. **Pulls never enqueue** — or every pull generates a push, forever.
3. **Push is ordered oldest-first**, and a retryable verdict stops the loop for the
   cycle. Claiming past it breaks the ordering the queue exists for.
4. **Status history is local-only**, never enqueued.
5. **Verdicts match by entry id, never position.** Unmentioned means retry, never
   accept; a repeated id invalidates that change's verdict.
6. **Session-ended mid-push releases, never fails** — one sign-out must not push
   unsaved work over the dead-letter limit.
7. **`resolvedAt != null` exactly when `isTerminal`**, enforced on the read path as
   well as the write path. Port the write path and miss the read path and a live task
   is filed in the archive forever, looking like nothing at all.

## Rules that apply to every task

- One composable per file, named after it; a `private` sub-composable in the rendering
  file is the only exception.
- No `Material3`. The original is not a Material app.
- Every file opens with KDoc explaining *why* and naming the rejected alternative.
- Test names are backtick full sentences — **no commas or periods**, Kotlin/Native
  rejects both, and only when common code compiles for Native.
- `verifyArchitectureGraph` is **exact-set**: a new edge without a matching
  `allowedLibraryTable` edit fails the build, and so does a missing one.
- Never write an unbounded `for (x in flow)` — the original records an intermittent
  SIGSEGV at teardown from exactly this.
- Every `expect` needs a `jvm` actual; the convention plugin adds a `jvm()` target and
  runs `commonTest` there.
- **`@Throws` on `suspend` must list `CancellationException`** — only Native rejects
  the omission, so it surfaces at `./gradlew check`, the expensive way. A cancelled
  save is not a failed one; swallowing it reports an error for a screen already closed.

## Highest-risk areas

New work touching these deserves a second pair of eyes:

1. **Sync `settle` logic** — id-matched verdicts, duplicate invalidation,
   unmentioned-as-retry. Short code that looks like an optimisation.
2. **Converter field completeness** — has silently dropped fields twice. Every new
   column needs both directions covered.
3. **`resolvedAt` read-path reconciliation** — see invariant 7 above.
4. **Architecture boundaries are convention, not compiler-enforced.** Kotlin cannot
   enforce domain-has-no-data; the `tasktracker.architecture` plugin is the substitute
   and must stay green.
5. **Session ordering** — a `launch` instead of an awaited call silently reorders, and
   the bug is a sign-in that appears to work.

## Known product gaps (not regressions)

Do not file these as bugs you introduced:

1. The API host is `https://api.tasktracker.invalid` — server sync cannot succeed.
   `MockSyncTransport` is the mitigation, and an addition, not a port.
2. **The local store is not user-partitioned.** Outbox rows carry a `userID` and the
   engine claims only the signed-in account's, but entities stay on disk — a different
   account sees the previous account's projects. A schema decision, flagged not fixed.
3. No typography or spacing scale in the original; added here.
4. Dark mode recolours the accent only; surfaces stay semantic by decision.

## Measurement lore

- **23 mutations run, 23 caught** — two missed on the first pass, both because the
  *test* was at fault. A new test that has never failed is not evidence; see
  `AGENTS.md` rule 1.
- JVM and iOS rasterisers **disagree** (same token measured 5.37:1 vs 3.98:1 — iOS
  strokes small glyphs at ~85% coverage). Set ink values from the worse host; hold
  both to the real threshold.
- Test counts come from the JUnit XML the build writes, cross-checked against `@Test`
  — never `grep -c`, which over-counts KDoc mentions. `test-baseline.json` holds the
  current totals and is regenerated from a release-gate run, not from a focused one.

## Continuous integration

`tests.yml` runs on `workflow_dispatch` and a nightly schedule, **not on every push**.
PRs are verified with focused JVM unit tests for the touched modules; the three-host
matrix and `scripts/check.sh` are the release gate and a drift backstop for `main`.

- The gating was never real: the repository is public, so Actions minutes are free, and
  `main` carries no branch protection, so these checks never blocked a merge. The cost
  was wall clock in front of every commit.
- No configuration reaches two minutes. The floor is 4787 tests (1723 JVM, 1672 iOS,
  1392 Android) plus ~1.7 GB of cache restore, before Kotlin/Native links anything on
  the iOS host. Warm, the legs measured 5m22s / 6m12s / ~15m (run 39045556483 and
  39042728812); cold, iOS was 35m04s.
- `~/.konan` is **not** covered by `gradle/actions/setup-gradle`, which caches
  `GRADLE_USER_HOME` only — so it was re-fetched and re-compiled every run until the
  workflow cached it explicitly. The saved entry measured **436.72 MiB**
  (`konan-macOS-…`, saved by run 39045556483): the "1.9 GB" first recorded here was a
  developer machine's `~/.konan`, which holds several Kotlin/Native versions. Cache it;
  do not reintroduce a per-push iOS leg.
- `main` was red on 2026-10-17 by choice, and it was infrastructure, not a regression:
  `java.net.SocketException: Connection reset` while `gradlew` fetched the 143 MB
  `gradle-9.8.0-bin.zip`, because no macOS job had ever cached the Gradle distribution.
  Do not go looking for a defect in that commit.
