# TaskTrackerKMP — Port Plan

Resumable task list for porting `/Users/amirreza/Desktop/SampleProjects/TaskTracker`
(SwiftUI/SwiftData, 165 files, 13 SwiftPM packages, 395 unit + 10 UI tests) to
Kotlin Multiplatform.

**Every task below is a checkbox. Tick it when it is done. Start any session by
finding the first unticked box.**

<a id="index"></a>
<details open>
<summary><b>Index</b></summary>

## Index

Phases are listed **in the order they appear in this file**, not in phase order — Phase 13
(part 1) was pulled forward to unblock a running app, so the document is not monotonic. The
`file:line` column is the jump target if your viewer takes you to the raw file.

**Every section below is collapsible, and the finished ones start closed.** The open ones are
exactly the unfinished work — Phases 14, 15, 22, 23 and 24 — because "find the first unticked box"
is the first thing a new session has to do, and a list of collapsed bars is the fastest way to
see it. The `state` on each collapsed bar says the same thing without expanding anything.

**Do not hand-edit the `file:line` numbers.** A line number in a link is a claim about this file
that the file itself invalidates: any edit above a row moves every row below it. This table had
drifted on **25 of its 26 rows**, by −4 to −323 lines, and one row's anchor matched no heading
at all and had never resolved. `tools/plan_index.py` recomputes the numbers from the real
headings and owns the anchors:

```bash
python3 tools/plan_index.py --check   # exit 1 on any drift; run before committing
python3 tools/plan_index.py --fix     # recompute the numbers, re-collapse, re-anchor
```

Each section also carries an explicit `<a id>` **outside** its `<details>`, so a jump still lands
when the section is closed instead of depending on the renderer to derive an id from a heading
nobody can see.

| phase | module / subject | file:line | state |
| --- | --- | --- | --- |
| [0](#phase-0--build-foundation) | build foundation, convention plugins | [PLAN.md:395](#phase-0--build-foundation) | done |
| [1](#phase-1--packagescore--packagesdomain) | `core` + `domain` | [PLAN.md:446](#phase-1--packagescore--packagesdomain) | done |
| [2](#phase-2--packagesrouting) | `routing` | [PLAN.md:592](#phase-2--packagesrouting) | done |
| [3](#phase-3--keychain--userdefaults) | keychain + user-defaults | [PLAN.md:649](#phase-3--keychain--userdefaults) | done |
| [4](#phase-4--packagesdatabase-room-kmp) | `database` (Room KMP) | [PLAN.md:719](#phase-4--packagesdatabase-room-kmp) | done |
| [5](#phase-5--packagesnetwork-ktor-360) | `network` (Ktor) | [PLAN.md:808](#phase-5--packagesnetwork-ktor-360) | done |
| [6](#phase-6--packagessync) | `sync` | [PLAN.md:932](#phase-6--packagessync) | done |
| [7](#phase-7--packagesfeature-projects-domain) | `feature-projects-domain` | [PLAN.md:1033](#phase-7--packagesfeature-projects-domain) | done |
| [8](#phase-8--packagesfeature-projects-data) | `feature-projects-data` | [PLAN.md:1110](#phase-8--packagesfeature-projects-data) | done |
| [9](#phase-9--packagesfeature-auth-domain--packagesfeature-auth-data) | auth domain + data | [PLAN.md:1233](#phase-9--packagesfeature-auth-domain--packagesfeature-auth-data) | done |
| [10](#phase-10--design-system-completion) | design system | [PLAN.md:1363](#phase-10--design-system-completion) | done |
| [11](#phase-11--packagesfeature-auth-presentation) | `feature-auth-presentation` | [PLAN.md:1519](#phase-11--packagesfeature-auth-presentation) | done |
| [13 pt 1](#phase-13-part-1-pulled-forward--making-the-sign-in-screen-reachable) | **pulled forward** — sign-in reachable | [PLAN.md:1623](#phase-13-part-1-pulled-forward--making-the-sign-in-screen-reachable) | done |
| [13 pt 1 follow-ups](#phase-13-part-1-follow-up-2--the-projects-list-became-reachable-and-the-new-project-button-did-not) | sign-in button invisible; projects list + New Project | [PLAN.md:1815](#phase-13-part-1-follow-up-2--the-projects-list-became-reachable-and-the-new-project-button-did-not) | done |
| [12](#phase-12--packagesfeature-projects-presentation) | `feature-projects-presentation` | [PLAN.md:2013](#phase-12--packagesfeature-projects-presentation) | done |
| [13](#phase-13--settings--composition-root--shared-ui-shell) | settings + composition-root + shared-ui | [PLAN.md:2191](#phase-13--settings--composition-root--shared-ui-shell) | done |
| [14](#phase-14--ios-shell) | iOS shell | [PLAN.md:2288](#phase-14--ios-shell) | open |
| [15](#phase-15--verification-and-documentation) | verification + docs | [PLAN.md:2462](#phase-15--verification-and-documentation) | open |
| [16](#phase-16--the-glass-slider-ttglasssurface-learns-an-intensity) | glass slider intensity | [PLAN.md:2711](#phase-16--the-glass-slider-ttglasssurface-learns-an-intensity) | done |
| [17](#phase-17--the-project-picker-was-not-using-the-scaffold) | project picker scaffold | [PLAN.md:2903](#phase-17--the-project-picker-was-not-using-the-scaffold) | done |
| [18](#phase-18--the-app-had-no-icon) | launcher icon, iOS + Android | [PLAN.md:3016](#phase-18--the-app-had-no-icon) | done |
| [19](#phase-19--the-palette-audit-the-error-red-was-never-held-to-the-accents-bar) | palette + glass audit, dark/light | [PLAN.md:3162](#phase-19--the-palette-audit-the-error-red-was-never-held-to-the-accents-bar) | done |
| [20](#phase-20--the-design-system-review-and-four-things-it-was-missing) | design system review, banner/spinner/tokens/previews | [PLAN.md:3315](#phase-20--the-design-system-review-and-four-things-it-was-missing) | done |
| [21](#phase-21--ttbodylabel-adopted-and-shared-ui-back) | `ttBodyLabel` adoption, `:shared-ui` verified | [PLAN.md:3504](#phase-21--ttbodylabel-adopted-and-shared-ui-back) | done |
| [22](#phase-22--the-error-red-meets-its-bar-and-the-glass-hairline-is-measured) | error red pinned; glass hairline + caption measured in pixels | [PLAN.md:3582](#phase-22--the-error-red-meets-its-bar-and-the-glass-hairline-is-measured) | 2 findings open |
| [23](#phase-23--sprints-a-time-boxed-goal-a-set-of-tasks-and-a-verdict) | **sprints** — goal, dates, owner, finish, story-point summary | [PLAN.md:3760](#phase-23--sprints-a-time-boxed-goal-a-set-of-tasks-and-a-verdict) | domain + persistence + port + design system + models and screens + the Sprints tab (23.5.1–23.5.4, 23.6.1–23.6.6) done |
| [24](#phase-24--board-identity-and-the-backlogs-third-segment) | **board identity** — switcher initials, sprint/Kanban title, Kanban backlog segment | [PLAN.md:5375](#phase-24--board-identity-and-the-backlogs-third-segment) | open |

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

</details>

<a id="where-things-stand"></a>
<details open>
<summary><b>Where things stand</b></summary>

## Where things stand

Original: iOS 26, Swift 6 strict concurrency, SwiftData, Swift Testing, XcodeGen.

Port: 22 Gradle modules. **Implemented**: `:packages:core`, `:packages:domain`,
`:packages:di`, `:packages:design-system` (colour tokens, accent, strings),
`:packages:routing`, `:packages:keychain`, `:packages:user-defaults`,
`:packages:database`, `:packages:network`, `:packages:sync`,
`:packages:feature-projects-domain`, `build-logic`. **Empty**:
the eight remaining feature modules plus most of `:androidApp` and `:iosApp`.

**Progress: Phases 0-13 complete; Phase 14 (iOS shell) and Phase 15 outstanding, and
`./gradlew check` is red only on `testAndroidHostTest`.** Test baseline **980 in `commonTest`**
(projects-data 153, projects-presentation 121, network 88, projects-domain 81, design-system 80,
sync 70, database 61, feature-auth-data 53, routing 52, domain 43, core 42, composition-root 30,
feature-auth-presentation 13, feature-auth-domain 13, keychain 12, settings-domain 12,
settings-presentation 11, di 19, user-defaults 19, settings-data 5, shared-ui 2) plus the **25 shell
scenarios** in `shared-ui`'s `commonTest`, which are `fun`s the target entry points call
rather than `@Test`s, and are counted per target below. The remainder is target-only:
`jvmTest` reported **1058 executions** and the iOS simulator XML **1036** as of Phase 13.2.
Phase 14.9 adds **2 shell scenarios, so 24 per host** (`TaskTrackerAppJvmTest` 24,
`TaskTrackerAppIosTest` 24, `TaskTrackerAppAndroidHostTest` 24, all measured).

**Re-measured in Phase 15.0, and the two modules that reported nothing now report again.**
`WorkItemCardScenarios.kt:142` and `SettingsScenarios.kt:234` called `ttTheme(ThemeScheme.Dark,
content)` positionally, and `ttTheme` had grown an `accentPalette` parameter between `scheme`
and `content` — so both `commonTest` compilations failed and `:feature-projects-presentation`
and `:feature-settings-presentation` reported no executions at all. Naming the argument fixed
both. Measured from the JUnit XML, per-module and de-duplicated across the one-deep and
two-deep globs:

| Target | Executions | Failures |
| --- | --- | --- |
| `jvmTest` | **1088** | 5 |
| `iosSimulatorArm64Test` | **1067** | 5 |
| `testAndroidHostTest` | **853** | 17 |

The 5 JVM failures are the documented pre-existing set: `SeedingTest` 3 and `shared-ui` 2
(`aChosenThemeOutlivesTheProcess`, `theBoardReturnsToTheList`). iOS reports the same 5. So the
`ttTheme` fix did **not** change the failure count on either host — it changed the *execution*
count, which is what had been hiding the two modules. The `commonTest` `@Test` total is **980**,
with `shared-ui`'s 25 shell scenarios counted separately per host.

`testAndroidHostTest` is **still not green**, but its shape changed and reason 2 is now fixed —
see the Android-host note below.

</details>

<a id="decisions-taken"></a>
<details>
<summary><b>Decisions taken</b></summary>

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

</details>

<a id="non-negotiable-invariants-from-the-originals-design"></a>
<details>
<summary><b>Non-negotiable invariants (from the original's design)</b></summary>

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

</details>

<a id="rules-that-apply-to-every-task"></a>
<details>
<summary><b>Rules that apply to every task</b></summary>

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

</details>

<a id="how-to-build"></a>
<details>
<summary><b>How to build</b></summary>

## How to build

```bash
# This machine has no system JDK. The project needs 25, which Android Studio bundles.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew check                        # 3 targets + both architecture guards
./gradlew verifyArchitectureGraph      # 22 modules, all edges allowed
./gradlew verifyModuleImports          # 22 modules, one task each
```

Current test baseline, **re-measured in Phase 15.0**: **980** `@Test`s in `commonTest`, plus
**25** shell scenarios in `:shared-ui` that are shared `fun`s rather than `@Test`s (see
[test-count baseline](#test-count-baseline) for the per-module split). From the JUnit XML:
`jvmTest` **1088 executions / 5 failures**, `iosSimulatorArm64Test` **1067 / 5**,
`testAndroidHostTest` **853 / 17**.

The 5 failures on each of JVM and iOS are the documented pre-existing set: `SeedingTest` 3 and
`shared-ui` 2 (`aChosenThemeOutlivesTheProcess`, `theBoardReturnsToTheList`).

`testAndroidHostTest` was **red for three** stacked reasons. Reasons 1 and 2 are now fixed;
reason 3 remains, and grew from 10 to 17 only because the two presentation modules began
executing tests again.

1. ~~The Compose resource-packaging failure below~~ — **fixed**, in
   `tasktracker.kmp.compose.gradle.kts`. It had taken every presentation suite that reads a
   string: 22 in `:shared-ui`, 32 in `:packages:feature-projects-presentation`, 7 in
   `:packages:feature-settings-presentation`. All 61 now pass.
2. ~~Five `:composition-root` graph assertions cannot run on that target at all~~ — **fixed**.
   `AppGraphTest`'s session-collaborator, looping-services, settings-port and sync-chain cases,
   and `SessionTeardownTest`'s outbox case, all resolve `TaskTrackerDatabase` or `NetworkMonitor`,
   and both need a `Context` on Android. The fix is the split `:shared-ui` already used: the
   bodies moved to `HostedGraphScenarios` in `commonTest`, and each host contributes entry
   points — `HostedGraphScenariosTest` on JVM and iOS, and a Robolectric-annotated
   `HostedGraphAndroidHostTest` on the Android host. Two things had to be true for it to work,
   and both are worth recording because neither is obvious:
   - **`testBindings()` had to thread the host into the database configuration.** It passed
     `DatabaseConfiguration.inMemory`, whose `host` is `null` by design, so even once a real
     `Context` existed Room still refused to open. The `Context` now reaches the in-memory
     configuration as well as `PlatformBindings`.
   - **One of the five still cannot run there, for a different reason.** `the outbox is left
     alone` opens a `RoomOutboxQueue`, and the Android host has no `sqliteJni` on its
     `java.library.path` — the same limit that makes `:packages:database` disable
     `testAndroidHostTest` outright. It asks `thisHostOpensRoomStores()` and skips where the
     answer is no, so it runs on JVM and iOS and registers as a pass on Android. The other four
     need only a `Context` and now pass on all three hosts.
3. **Seventeen Compose UI assertions fail on layout, not behaviour, and the number was five
   before the `ttTheme` fix because two modules were not executing at all.** The prediction in
   an earlier revision of this file was that fixing the packaging bug would move all 61 onto the
   `Build.FINGERPRINT` wall. It did not: Robolectric was already engaging, because those suites
   have had `androidHostTest` entry points carrying `@RunWith(RobolectricTestRunner::class)`
   all along, and the `MissingResourceException` was thrown during composition setup — before
   the idling strategy ever dereferenced `Build.FINGERPRINT`. What is left is a layout
   difference, not a missing capability: Robolectric's default viewport is far smaller than the
   JVM's and iOS's, so a node inside a `LazyColumn` (`settings.versionRow`) or a
   `horizontalScroll` row (`board.column.in_review`) exists and reports
   `assertIsDisplayed()` failing. `@Config(qualifiers = "w411dp-h891dp-xxhdpi")` does clear
   that, and then exposes a genuinely separate problem underneath:
   `theVersionRowHidesItselfWithNoVersion` calls `setContent` twice, which Android's
   `ComponentActivity` rejects with *"has already set content"*. So these need the same kind of
   decision as reason 2 — a change to shared scenarios that two green hosts currently pass —
   and they are recorded here as measured rather than guessed at a third time. The 17 are
   `:feature-settings-presentation` 7, `:shared-ui` 7, `:feature-projects-presentation` 3.

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
`@Test` annotations in the source tree: **1088 JVM executions, 1067 iOS, 853 Android host**
(re-measured in Phase 15.0; the previous figures of 1058 / 1036 predate the `ttTheme` compile
fix that brought `:feature-projects-presentation` and `:feature-settings-presentation` back).
Note that a
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

<a id="kotlinnative-constraints-hit-while-writing-tests"></a>
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

</details>

<a id="phase-0--build-foundation"></a>
<details>
<summary><b>Phase 0 — Build foundation</b> &mdash; <i>done</i></summary>

## Phase 0 — Build foundation

> **See also:** [`Project.md` — The packages, one by one](Project.md#the-packages-one-by-one) and
> [How the packages depend on each other](Project.md#how-the-packages-depend-on-each-other) for the
> module map, the layer diagram, and the five deliberately-absent edges.

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

</details>

<a id="phase-1--packagescore--packagesdomain"></a>
<details>
<summary><b>Phase 1 — <code>:packages:core</code> + <code>:packages:domain</code></b> &mdash; <i>done</i></summary>

## Phase 1 — `:packages:core` + `:packages:domain`

> **See also:** [`Project.md` — Cross-cutting, the bottom of the stack](Project.md#cross-cutting--the-bottom-of-the-stack).

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
      board/backlog/archive membership as three separate pure functions. The ASCIIguard in `normaliseKeyPrefix` is an explicit code-point range, not `Char.isAscii()` — the latter is stdlib-experimental and would cost this file
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

</details>

<a id="phase-2--packagesrouting"></a>
<details>
<summary><b>Phase 2 — <code>:packages:routing</code></b> &mdash; <i>done</i></summary>

## Phase 2 — `:packages:routing`

> **See also:** [`Project.md` — Cross-cutting, the bottom of the stack](Project.md#cross-cutting--the-bottom-of-the-stack).

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

</details>

<a id="phase-3--keychain--userdefaults"></a>
<details>
<summary><b>Phase 3 — Keychain + UserDefaults</b> &mdash; <i>done</i></summary>

## Phase 3 — Keychain + UserDefaults

> **See also:** [`Project.md` — Cross-cutting, the bottom of the stack](Project.md#cross-cutting--the-bottom-of-the-stack).

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

</details>

<a id="phase-4--packagesdatabase-room-kmp"></a>
<details>
<summary><b>Phase 4 — <code>:packages:database</code> (Room KMP)</b> &mdash; <i>done</i></summary>

## Phase 4 — `:packages:database` (Room KMP)

> **See also:** [`Project.md` — Infrastructure, one layer above core](Project.md#infrastructure--one-layer-above-core)
> for the platform `expect`/`actual` seams.

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

</details>

<a id="phase-5--packagesnetwork-ktor-360"></a>
<details>
<summary><b>Phase 5 — <code>:packages:network</code> (Ktor 3.6.0)</b> &mdash; <i>done</i></summary>

## Phase 5 — `:packages:network` (Ktor 3.6.0)

> **See also:** [`Project.md` — Infrastructure, one layer above core](Project.md#infrastructure--one-layer-above-core).

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

</details>

<a id="phase-6--packagessync"></a>
<details>
<summary><b>Phase 6 — <code>:packages:sync</code></b> &mdash; <i>done</i></summary>

## Phase 6 — `:packages:sync`

> **See also:** [`Project.md` — Infrastructure, one layer above core](Project.md#infrastructure--one-layer-above-core)
> and [Moving a work item](Project.md#moving-a-work-item) for the push side of the cycle.

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

</details>

<a id="phase-7--packagesfeature-projects-domain"></a>
<details>
<summary><b>Phase 7 — <code>:packages:feature-projects-domain</code></b> &mdash; <i>done</i></summary>

## Phase 7 — `:packages:feature-projects-domain`

> **See also:** [`Project.md` — Feature modules](Project.md#feature-modules--each-is-a-domain--data--presentation-triplet).

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

</details>

<a id="phase-8--packagesfeature-projects-data"></a>
<details>
<summary><b>Phase 8 — <code>:packages:feature-projects-data</code></b> &mdash; <i>done</i></summary>

## Phase 8 — `:packages:feature-projects-data`

> **See also:** [`Project.md` — Moving a work item](Project.md#moving-a-work-item), which walks this
> module's single transaction and the outbox row it writes with it.

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

</details>

<a id="phase-9--packagesfeature-auth-domain--packagesfeature-auth-data"></a>
<details>
<summary><b>Phase 9 — <code>:packages:feature-auth-domain</code> + <code>:packages:feature-auth-data</code></b> &mdash; <i>done</i></summary>

## Phase 9 — `:packages:feature-auth-domain` + `:packages:feature-auth-data`

> **See also:** [`Project.md` — Signing in](Project.md#signing-in) and
> [Ending a session](Project.md#ending-a-session), which walk both directions through these modules.

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

</details>

<a id="phase-10--design-system-completion"></a>
<details>
<summary><b>Phase 10 — Design system completion</b> &mdash; <i>done</i></summary>

## Phase 10 — Design system completion

> **See also:** [`Project.md` — Cross-cutting, the bottom of the stack](Project.md#cross-cutting--the-bottom-of-the-stack),
> and [Where a test goes](Project.md#where-a-test-goes-and-why-the-scenarios-are-not-tests) for the
> `thisHostRasterisesText` rasterisation skip.

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

</details>

<a id="phase-11--packagesfeature-auth-presentation"></a>
<details>
<summary><b>Phase 11 — <code>:packages:feature-auth-presentation</code></b> &mdash; <i>done</i></summary>

## Phase 11 — `:packages:feature-auth-presentation`

> **See also:** [`Project.md` — Signing in](Project.md#signing-in).

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

</details>

<a id="phase-13-part-1-pulled-forward--making-the-sign-in-screen-reachable"></a>
<details>
<summary><b>Phase 13 (part 1, pulled forward) — making the sign-in screen reachable</b> &mdash; <i>done</i></summary>

## Phase 13 (part 1, pulled forward) — making the sign-in screen reachable

> **See also:** [`Project.md` — Signing in](Project.md#signing-in).

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

</details>

<a id="phase-13-part-1-follow-up-2--the-projects-list-became-reachable-and-the-new-project-button-did-not"></a>
<details>
<summary><b>Phase 13 (part 1) follow-up #2 — the projects list became reachable, and the New Project button did not</b> &mdash; <i>done</i></summary>

## Phase 13 (part 1) follow-up #2 — the projects list became reachable, and the New Project button did not

> **See also:** [`Project.md` — Application shell](Project.md#application-shell--above-the-architecture).

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

</details>

<a id="test-count-baseline"></a>
<details>
<summary><b>Test-count baseline</b></summary>

## Test-count baseline

| suite | target | tests |
| --- | --- | --- |
| `design-system` | JVM / iOS | 83 / 80 |
| `shared-ui` (shell scenarios) | JVM / iOS / Android host | 24 / 24 / 24 |
| `feature-projects-presentation` | JVM / iOS / Android host | 159 / 159 / 159 |
| `feature-settings-presentation` | JVM / iOS | 24 / 24 |
| `feature-projects-domain` | JVM | 80 |
| `composition-root` | JVM / iOS | 37 / 35 |

Android-host UI tests remain blocked on the known Compose resource-packaging failure recorded
below. Its composition-root model tests run, and five of them are additionally blocked on the
Robolectric-sandbox gap described in [Where things stand](#where-things-stand).

`design-system` and `feature-settings-presentation` were re-measured the same way for the glass
slider (Phase 16): 83/80 and 24/24, both green, with the `ttTheme` signature workaround applied
locally to `SettingsScenarios.kt` and reverted. The 8 new design-system tests are the 8 unit
tests in `GlassIntensityTest` and the 1 rendered test in `GlassIntensityRenderTest`; the delta
from the 73/70 previously recorded here is 10 per host, so two pre-existing tests had been added
to that module since this table was written without the table being corrected.

`GlassIntensityTest` and `GlassIntensityRenderTest` were both **rewritten** during Phase 16 after
the slider shipped, because both were asserting the defect described in [16.7](#167-the-defect-the-feature-reported-on-itself).
The count went up because the replacement assertions are two in the unit test and one rendered
test measuring three panels rather than two.

`feature-projects-presentation` is 159 on all three hosts, re-measured from the JUnit XML with the
`ttTheme` signature workaround applied locally and reverted — see the floating-button follow-up
above. It runs with one pre-existing failure on JVM and iOS (`BoardLaneSnapTest`) and four on the
Android host (that one plus three lane scenarios that do not fit Robolectric's window).

---

</details>

<a id="phase-12--packagesfeature-projects-presentation"></a>
<details>
<summary><b>Phase 12 — <code>:packages:feature-projects-presentation</code></b> &mdash; <i>done</i></summary>

## Phase 12 — `:packages:feature-projects-presentation`

> **See also:** [`Project.md` — Feature modules](Project.md#feature-modules--each-is-a-domain--data--presentation-triplet)
> and [Moving a work item](Project.md#moving-a-work-item).

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
pass for the wrong reason): 22 tests, 21 pass, 1 failure — down from 22 failures. That build
measured **10 failures of 818** across the target, down from 66. The exit command
`jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports
:androidApp:compileDebugKotlin` is green.

**Superseded in Phase 15.0: the target is now 853 tests and 17 failures.** The five
`:composition-root` assertions this paragraph counted have been fixed (reason 2 below), and the
count rose because two presentation modules that had not been executing at all began to again.
So the remaining 17 are *all* of the second class named here: Compose UI
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

</details>

<a id="phase-13--settings--composition-root--shared-ui-shell"></a>
<details>
<summary><b>Phase 13 — Settings + composition root + shared-ui shell</b> &mdash; <i>done</i></summary>

## Phase 13 — Settings + composition root + shared-ui shell

> **See also:** [`Project.md` — Application shell](Project.md#application-shell--above-the-architecture),
> [Ending a session](Project.md#ending-a-session), and
> [How the packages depend on each other](Project.md#how-the-packages-depend-on-each-other).

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

</details>

<a id="phase-14--ios-shell"></a>
<details open>
<summary><b>Phase 14 — iOS shell</b></summary>

## Phase 14 — iOS shell

> **See also:** [`Project.md` — Running on iOS](Project.md#running-on-ios), the artifact-by-path
> rule and the freshness check.

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
      **Correction, Phase 18:** the `AppIcon.appiconset` this box claims existed was a
      `Contents.json` with no `filename` on any image, so it compiled to nothing and the
      app shipped with **no icon at all**. The slot is now populated — see
      [Phase 18](#phase-18--the-app-had-no-icon).
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
- [x] **14.7** Build script phase running
      `./gradlew :shared-ui:linkDebugFrameworkIosSimulatorArm64 :shared-ui:syncComposeResourcesForIos`.
      Written in `project.yml` as a `preBuildScript` with `basedOnDependencyAnalysis: false` —
      without that flag Xcode decides the script has nothing to do and skips it, leaving
      a stale or missing framework for the build to fail on in a way that points at the
      linker rather than at Gradle. Verified end to end in 14.8.
- [x] **14.8** Verified on an **iPhone 18 Pro** simulator: `xcodegen generate`, then
      `xcodebuild -project TaskTracker.xcodeproj -scheme TaskTracker -destination
      'platform=iOS Simulator,name=iPhone 18 Pro' build` → `** BUILD SUCCEEDED **`, and the
      app launches to `routing -> main` with no exception. The three unproven things all held:
      the framework output path, the linker against it, and the `Info.plist`.

      ### The one thing the phase was missing: the resources

      The first launch **crashed**, every time, on the first frame:

      ```
      org.jetbrains.compose.resources.MissingResourceException: Missing resource with path:
      <app>/compose-resources/composeResources/com.tasktracker.resources/values/strings.commonMain.cvr
      ```

      A `stringResource` in composition threw, on the main thread, inside `setContent` — so the
      crash log's faulting stack is Compose and the message names only an absent path. Nothing in
      either points at a build phase, which is why it reads as a resource bug rather than the
      packaging gap it is. `ls` on the built `.app` confirmed it: no `compose-resources`
      directory in the bundle at all.

      `linkDebugFrameworkIosSimulatorArm64` builds a framework and copies no assets. Compose
      Multiplatform puts the `.cvr` blobs in the bundle through a *separate* task,
      `syncComposeResourcesForIos`, and the only thing that wires it up on the Xcode path is the
      plugin making `embedAndSignAppleFrameworkForXcode` depend on it. This project never calls
      that task — it calls `linkDebugFrameworkIosSimulatorArm64` — so the sync was simply never
      in the graph.

      The destination is not configurable: the task reads `BUILT_PRODUCTS_DIR` and
      `UNLOCALIZED_RESOURCES_FOLDER_PATH` from the environment and writes
      `$BUILT_PRODUCTS_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/compose-resources`, and it picks
      the Kotlin target from `PLATFORM_NAME`/`ARCHS`. So the fix has to live in a phase Xcode
      runs — there is no Gradle-side alternative. That is also why the Android Studio run
      configuration is unaffected: it supplies the same values as the
      `compose.ios.resources.platform` / `compose.ios.resources.archs` Gradle properties, which is
      what the `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED` early exit above exists to let it do.

      This is the same class of defect as Phase 12's on Android and as
      `copyComposeResourcesIntoAndroidHostTestAssets` on the Android *test* host: three hosts,
      three wirings, one missing each until something rendered a string.
- [x] **14.9** The four top-level views scroll **under** the floating tab bar, and the
      last row of each is still reachable. Recorded in full under
      [Scroll-under: the two assertions](#scroll-under-the-two-assertions).
- [ ] **14.10** Deep link handling on iOS via `scene(_:openURLContexts:)` → `Router`,
      and an `<intent-filter>` for `tasktracker://` on the Android launcher activity.
- [ ] **14.11** Port the 10 XCUITests once there are screens to drive.

<a id="scroll-under-the-two-assertions"></a>
### Scroll-under: the two assertions

The bar was the second child of a `Column`, so the content box ended where the bar began. A
floating capsule with an opaque band of canvas under it reads as a band with a capsule on top of
it, not as glass — and no list could put an item behind the glass, because there was nowhere
behind it. `NavigationHost` now overlays the bar (`align(Alignment.BottomCenter)` over a
full-height content box) and publishes the bar's laid-out height as
`LocalTabBarScrollUnderInset` in `:packages:design-system`.

**Measured, not a token.** The bar carries `navigationBarsPadding()` and its labels scale with
the font setting, so its height is not derivable from the spacing scale: on the JVM host
(1024x768, density 1) it is **60px** tall, top edge at y=708. The shell reads that with
`onSizeChanged`, and the cost is one recomposition per consumer, once — the first composition
reads `0.dp` because the bar has not been measured yet.

**Content inset, never viewport inset.** Eight consumers read it: the scroll ranges of Projects,
Backlog, Settings, Archive and each board lane, the Projects and Backlog add buttons, the
project picker, and `TTBottomSheetScaffold`. Applying it to a screen's *root* would stop the
screen short of the glass, which is the layout being replaced; it belongs in `contentPadding`,
which moves the end of the content under the bar while the viewport still runs to the screen
edge. Projects' add button had to be moved out of its `Column` and floated over the list for the
same reason — as a `Column` child it sat *on* the list's last item.

**The overlay has one sharp edge, and it cost a test to find.** Content that used to stop above
the bar now reaches the bottom of the screen, so anything drawn there is under the glass and the
bar takes the touch. `TTBottomSheetScaffold`'s action row sat exactly on the bar: clicking
**Create** in the new-project sheet dismissed the form instead of submitting, and the sheet's
own tag disappearing is the only trace. `creatingAProjectAddsItToTheList` went red on this and
is the regression test — it is not a new test, it is an existing one that stopped being about the
sheet. `TTBottomSheetScaffold` now reads the same inset, so every sheet clears the bar on all
three hosts with no per-caller change.

**Two assertions, because they fail in opposite directions.** Extending the scroll region is the
ask; reserving room at the end of the scroll is what stops the extension from costing
reachability. Deleting the reserving `Modifier` takes nothing away from the first and cannot be
seen by it.

| scenario | asserts | mutation that must redden it | measured on red |
| --- | --- | --- | --- |
| `everyTabScrollsBehindTheTabBar` | each tab's scroll region ends **below** the bar's top edge | pad the content box's bottom (the old `Column`) | region ends 664.0, bar top 708.0 |
| `theLastRowOfEachListScrollsClearOfTheTabBar` | each list's last row scrolls **above** the bar's top edge | `contentPadding = PaddingValues(bottom = 0.dp)` on Projects | row bottom 736.0, bar top 708.0 — 28.0px overlap |

Both mutations were run and both caught. Green on all three hosts: `jvmTest`,
`iosSimulatorArm64Test`, `testAndroidHostTest` (24 shell scenarios, 24 on iOS, 24 on the Android
host; the Android host's four failures are the documented `LazyColumn`-viewport gap and are
unchanged by this phase).

**Two shell scenarios were already red** — `theBoardReturnsToTheList` and
`aChosenThemeOutlivesTheProcess`, on every host — and are unrelated: confirmed by stashing this
phase and re-running. Two `commonTest` files also did not compile against `ttTheme`'s current
signature (`WorkItemCardScenarios.kt:142`, `SettingsScenarios.kt:234`, both a positional
`content` argument that `ttTheme`'s new `accentPalette` parameter had displaced), which is why
`:feature-projects-presentation` and `:feature-settings-presentation` reported no executions at
all. **Both were fixed in Phase 15.0** by naming the argument, so the two modules execute again;
the failure counts on JVM and iOS were unchanged by the fix, which is how it is known that it
only restored execution and did not change behaviour.

**Looked at, not just measured.** The bounds assertions are structural, so a pixel check was run
once on the JVM host to confirm the *composite*: mid-scroll, the row fill reads through the bar's
translucent lens — `rgb(50,53,57)` at y=735 against `rgb(22,24,26)` for the same pixel with the
list scrolled to its end, where the reserved gutter leaves only canvas behind the glass. Those two
frames are what "content passes under the bar" looks like, and neither number is reachable from
the modifier chain.

**Not asserted:** the first frame, before the bar has been measured, lays out with a `0.dp`
inset, and the board lane's end padding has no test — its scrolling `Column` carries no tag and
adding one to assert a padding would test the padding through a proxy. The lane's extent under
the bar *is* covered, by the first scenario.

**Exit criteria**: `xcodegen generate` then an iPhone 17 Pro simulator build and run.

---

</details>

<a id="phase-15--verification-and-documentation"></a>
<details open>
<summary><b>Phase 15 — Verification and documentation</b></summary>

## Phase 15 — Verification and documentation

> **See also:** [`Project.md` — Tests](Project.md#tests) and
> [Where a test goes](Project.md#where-a-test-goes-and-why-the-scenarios-are-not-tests).

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

</details>

<a id="follow-up--the-tab-lens-drew-its-corners-square"></a>
<details>
<summary><b>Follow-up — the tab lens drew its corners square</b></summary>

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

</details>

<a id="follow-up--the-floating-create-buttons-covered-the-rows-underneath-them"></a>
<details>
<summary><b>Follow-up — the floating create buttons covered the rows underneath them</b></summary>

## Follow-up — the floating create buttons covered the rows underneath them

A user reported that the `+` on the projects screen sat on top of the delete button on the last
card, making it impossible to remove a project.

It was true, and it was arithmetic rather than a rendering accident. `ProjectsListScreen` and
`ProjectBacklogScreen` each drew a 56dp `ttGlassAddButton` at `align(BottomEnd)`, padded by
`boardBottomInset`/`screenGutterLarge` + `tabBarInset`, while the `LazyColumn` under them declared
`contentPadding = PaddingValues(bottom = tabBarInset)` and nothing else. The circle therefore sat
in the band the list never reserved, and a floating control is drawn *after* its content, so it
also won the touch.

`ProjectRow` puts its own actions bottom trailing (`Arrangement.spacedBy(ttSpacing.sm,
Alignment.End)`), which makes the collision exact: on the projects list the measured rectangles
were the button at `(952, 680)–(1008, 736)` and the last row's delete action at
`(952, 713)–(996, 736)` — the delete button **entirely** inside the circle.

Both create actions moved into the flow: the projects one into a header `Row` trailing
`tab_projects`, the backlog one trailing the project switcher. Neither screen needed a top app bar
— there is none in the shell, and `LocalTabBarScrollUnderInset` has no top counterpart to mirror.

### What it cost, stated plainly

The action now scrolls away with the title. On a long list you scroll up to add. That is the iOS
large-title behaviour rather than the pinned `navigationBar`, and it was chosen deliberately over a
pinned bar, which would have needed a top inset threaded through every scrolling screen's
`contentPadding`. The alternative was a permanent overlay over the list.

### The backlog was not the same defect, and the first test said so

The backlog carried a button in the same place over a list with the same missing reservation, and
the obvious assumption is that it covered the same thing. It did not. `WorkItemBacklogRow` lays its
action row out with `Arrangement.spacedBy(ttSpacing.sm)` and **no** `Alignment.End`, so "Put on
board" and "File away" sit at the *leading* edge — measured at x 42..237 on a 1008px window, while
a bottom trailing control sits at x 964..1008. The first version of the backlog test asserted the
add action missed those two buttons and passed against a button that was genuinely floating over
the list.

What the floating button actually intercepted there was the **row**, which is
`Modifier.clickable(onClick = onPutOnBoard)` across its full width: tapping the bottom right of a
backlog did nothing instead of filing the task to the board. The test was rewritten against the
row, and its KDoc records the measurement so the wrong version cannot be re-derived.

### Two tests that could not have caught it

`theNewProjectButtonWorksWithRowsPresent` already existed and passed throughout. It asks whether
the button can be *pressed*; the defect was that pressing *it* pressed something else. Both nodes
were present, displayed and hittable at the same time.

Neither screen had any assertion about *where* its action was. The new ones sweep every composed
row rather than naming the last one, because which rows a `LazyColumn` composes is the viewport's
decision and Robolectric's window is far smaller than the other two hosts — a test that named "the
ninth row" would either fail on the smallest host or pass against the bug on the others.

The backlog test scrolls to the end first. At scroll position zero the final rows are clipped away
by the bottom of the window and a corner control overlaps nothing.

Both were mutation-checked by restoring the floating button in place of the header action:
`theNewProjectActionDoesNotCoverTheLastRowsDelete` fails with `create=Rect.fromLTRB(952.0, 680.0,
1008.0, 736.0) remove=Rect.fromLTRB(952.0, 713.0, 996.0, 736.0)`, and
`theAddActionDoesNotCoverTheLastRow` with `add=Rect.fromLTRB(964.0, 676.0, 1008.0, 720.0)
row=Rect.fromLTRB(16.0, 630.0, 1008.0, 736.0)`.

The backlog's `+` also has a second scenario, `theAddActionTrailsTheSwitcher`, because the overlap
assertion alone would pass for a button placed *anywhere* clear of the rows — a top bar, a bottom
bar, anywhere — and so does not pin down what was asked for.

### Left standing, deliberately

`ttGlassAddButton` has lost both of its callers and is now unused, and
`ttSpacing.boardBottomInset` has lost its last production caller — `SpacingScaleTest` still reads
it. Neither was removed here: deleting a shared primitive and retuning a named token are separate
decisions from moving two buttons.

`ttGlassAddButton` was not restyled either. `ttGlassIconButton` at `ttSpacing.iconButtonSize`
(44dp) is already the compact circular glass action, and `ttActionIcons.add` already exists as the
plus — drawn as a single twelve-point contour precisely because two overlapping bars would fill as
a hole under this file's even-odd rule. So the header actions are the existing component with the
existing glyph.

That leaves them on a 44dp touch target. There is no documented 48dp floor in this repository: the
token is documented as a circle's "visible and tappable diameter" and every icon action in the app
already ships 44dp. Raising the floor is a change to that shared convention affecting every icon
button and its layout, and it is worth doing deliberately rather than as a side effect.

---

</details>

<a id="known-product-gaps-carried-over-from-the-original"></a>
<details>
<summary><b>Known product gaps carried over from the original</b></summary>

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

</details>

<a id="highest-risk-items"></a>
<details>
<summary><b>Highest-risk items</b></summary>

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

---

</details>

<a id="phase-16--the-glass-slider-ttglasssurface-learns-an-intensity"></a>
<details>
<summary><b>Phase 16 — the glass slider (<code>ttGlassSurface</code> learns an intensity)</b> &mdash; <i>done</i></summary>

## Phase 16 — the glass slider (`ttGlassSurface` learns an intensity)

The user asked for a slider in Settings that sets how much glass effect the app shows, like the
iOS option. Two decisions were asked about rather than guessed, because neither is recoverable
from the repository: **continuous 0-100%** over discrete stops, and the **bottom of the travel
cross-fades toward Reduce Transparency** rather than being a plain alpha reduction.

### 16.1 Why it lives in `ttTheme` and not in a settings port

`ShellPreferences` already holds `themeScheme` and `accentPalette`, persists them through
`UserDefaultsStore`, and `NavigationHost` passes them plus callbacks into `SettingsScreen`. The
glass amount follows that path exactly, so **no module and no dependency edge changed** and
`verifyArchitectureGraph` / `verifyModuleImports` are untouched. It is deliberately not a
settings-*domain* port: it is not a business rule, nothing in the domain needs it, and every
surface in the app resolves an alpha. `ttTheme` grew a defaulted `glassIntensity` parameter and
provides `LocalGlassIntensity`, which keeps the splash, the previews and every test host that
does not ask for an amount painting the glass that shipped.

### 16.2 The mapping, and the direction that is easy to get backwards

**More glass means a lower fill alpha**, because glass is see-through and a solid fill is what
Reduce Transparency means. At `0f` the fill is `alpha = 1f`; at `1f` it is exactly the
measured value. The lens, the hairline and the fill's ramp all scale to nothing at `0f` — a lens
exists to lighten a translucent fill, and a ramp over an opaque one is a gradient over nothing.

### 16.3 The defect the rendered test found, and the unit test could not

The cross-fade was first written as a fade of the fill's **floor**, with `ttGlassSurface`'s
`fillAlpha` multiplying the result. Those look equivalent and are not: `ttGlassSurface` defaults
`fillAlpha = ttGlass.FILL_ALPHA`, so an untinted surface's shipped alpha is
`FILL_ALPHA * FILL_ALPHA` = **0.3025**, and fading its floor toward 1 before that multiplication
left the bottom of the slider at **0.55** — translucent, which is the one thing it must not be.
Measured off a rendered frame: the 0-intensity panel painted `#90a1b6` where the accent is
`#34537a`.

`GlassIntensityTest` was green throughout, because it calls `ttGlass.fillRamp` with
`fillAlpha = 1f` — not the value the shipping caller passes. **The test was measuring the
arithmetic rather than the arrangement**, which is the trap `GlassFillRampTest`'s own header
warns about. The fix interpolates the *finished* alpha instead:

```
shipped = ((tint?.alpha ?: FILL_ALPHA) * fillAlpha).coerceIn(0f, 1f)
alpha   = lerp(1f, shipped, intensity)
```

which reaches a true opaque `1.0` and still puts intensity `1f` on the previous arithmetic to
the bit. Confirmed rendered, both appearances, both rasterisers: the 0-intensity panel now
paints **exactly** the accent (`#34537a` light, `#5f6873` dark) and the full-intensity panel is
unchanged from the pixels that shipped (`#cdd5df`, `#15171a`).

### 16.4 The slider control, and the two things it got wrong first

`ttSlider` is a hand-written glass slider: `Material` is not used in this package and a feature
may not hand-roll fill, rim and thumb out of glass tokens. Two real defects were found by the
scenarios rather than by reading the code:

- **A tap did nothing.** The gesture was first `detectHorizontalDragGestures`, whose
  `onDragStart` fires only once the touch slop is exceeded — so the control needed a *drag* to
  respond at all. It is now an explicit `awaitEachGesture` that acts on the press.
- **One drag wrote three times.** The commit fired from the press, from the pointer-up branch
  *and* from a `finally`, which the code comment had excused as "harmless". Caught by counting
  commits rather than by reading a value. There is now exactly one, in the `finally`, so a
  cancelled gesture still persists what the thumb did.
- `onValueChangeFinished` **takes the value** rather than closing over the caller's. The no-arg
  shape makes the write depend on the caller having already recomposed, which a `StateFlow`
  caller does and a plain-parameter caller does not; the slider knows the last value it emitted.
- `preview` and `commit` are separate `ShellPreferences` methods because the slider is
  continuous: the appearance follows the thumb every frame and the store sees one write.

### 16.5 Measurements and mutations

`GlassIntensityTest` (8 tests, unit — the last two added by [16.7](#167-the-defect-the-feature-reported-on-itself),
which is also where this list stops being the full story): full intensity is the fill that shipped;
a caller's tint keeps its own floor; zero intensity is opaque with no ramp; zero intensity is the
neutral and *not* the accent; a fifth of the way down is still nearer the neutral; alpha falls
monotonically across 21 steps; out-of-range clamps; the ramp fades by the measured amount.

`GlassIntensityRenderTest` (1 test, rendered on both rasterisers, three panels on one frame): the
bottom of the travel is an opaque neutral rather than absent, the 20% mark has not crossed to the
accent, and full glass sits between the accent and the page.

| mutation | caught by |
| --- | --- |
| floor ignores `intensity` | `no intensity is opaque`, `clamped`, `monotonic` |
| fade ignores `intensity` | `no intensity is opaque` |
| commit fires on every move | `a drag previews every step and persists once` |
| commit uses the gesture's first value | `a tap…`, `a drag…` |
| floor-fade (the 16.3 defect) | `the bottom of the slider is an opaque neutral rather than absent` |

5 mutations, 5 caught. One was **missed on the first pass**: `alpha falls monotonically` passed
against the floor-ignoring mutation, because a *constant* alpha also satisfies non-increasing.
The test was at fault and was strengthened with a strict end-to-end decrease; it then caught it.

### 16.6 What is deliberately unverified

The slider is continuous, so there are 101 values. **The ends and the arithmetic between them are
measured; the intermediate positions are not.** That caveat was written before the user reported
[16.7](#167-the-defect-the-feature-reported-on-itself), and it is worth noting that the value the
user actually tried — 20% — was among the unmeasured ones. [16.9](#169-still-unverified) states
where that stands after the fix.

<a id="167-the-defect-the-feature-reported-on-itself"></a>
### 16.7 The defect the feature reported on itself

The user set the slider to 20% and reported that the app "gets too dark" and that it "changes the
colour saturation" — asking whether it should only change the glass properties. It should, and it
did not. Both halves of the report were correct and the cause was a single line.

**What the arithmetic did.** `fillRamp` cross-faded only the fill's **alpha** toward 1. At 20%
intensity in Light appearance the fill became an 0.86-alpha `#34537a`, compositing to about
`#506b8c` over the page — and the ramp fade had already dropped to 0.08, so it was a flat slab.
A large flat area of the accent navy reads as *both* darker and more saturated, which is exactly
the two symptoms reported. The full travel was 30% alpha; 20% was 86%. The mapping was
monotone and smooth and completely wrong about what it was interpolating.

**What Reduce Transparency actually is.** On iOS, reducing transparency makes materials opaque
and drops vibrancy — the accent tint is part of the *glass* vocabulary, so it is what has to go.
The destination is a neutral material colour. The app already had one and was already using it:
`ttSurface()` (`#F2F2F7` / `#1C1C1E`), which is what the sheet and switcher capsule pass as their
solid `base`. The cross-fade was aiming at the accent because "increase opacity" and "become the
accent" felt like the same direction. They are opposite.

**The fix** is that the fill's colour crosses over as well as its alpha: `lerp(reducedSurface,
top, intensity)`. At `1f` both interpolations are the identity, so the top of the slider is still
the arrangement that shipped, bit for bit.

**Measured, both appearances, both rasterisers, identical to the channel:**

| intensity | Light | Dark |
| --- | --- | --- |
| 1f (unchanged) | `#cdd5df` | `#15171a` |
| 0.2f (was `#506b8c`) | `#d2d8e3` | `#202125` |
| 0f | `#f2f2f7` = `ttSurface()`, 0 channels | `#1c1c1e` = `ttSurface()`, 0 channels |
| distance from neutral at 0.2f | 31 channels (was 158) | 7 channels (was 77) |

**Why it shipped, which is the part worth keeping.** Every test was green, because every test
asserted the accent:

- `GlassIntensityTest` checked alpha, direction, monotonicity, clamping and the ramp. Alpha-only,
  so structurally blind to the colour. It passed with `reducedSurface` deleted.
- `GlassIntensityRenderTest` asserted `distance(zero, accent) <= 3/255` — "an opaque fill lands on
  its colour" — where "its colour" had been written as the accent. It was **passing on the
  premise that produced the bug**, and read that agreement as confirmation.
- Two further rendered assertions (`zeroToCanvas > zeroToAccent * 4`, `fullToCanvas <
  zeroToCanvas`) inferred *opacity* from a colour's distance to the page. That only works while the
  fill is the accent; against an opaque neutral it fails by arithmetic, because a solid neutral is
  *supposed* to sit near the page it came from. Discarded rather than re-tuned.

So the replacement assertions had to establish two different things by two different means.
Opacity is now **direct** — an opaque fill of a solid colour lands on it, so the 0-intensity pixel
must equal `ttSurface()` within 3 channels, and a translucent fill or a hole cannot. See-through
is now an **ordering** — a translucent fill must land strictly between the accent and the page and
nearer the page, which avoids predicting an alpha and modelling the composite in a test whose
subject is the composite. The colour claim is its own assertion, at 0f and at the reported 0.2f,
in both files.

`reducedSurface` is a **required** parameter with no default. A default would have been silently
wrong for every caller that omitted it, which is the exact shape of the bug; `fillRamp`'s five
call sites in `GlassFillRampTest` now thread an appearance-matched neutral rather than relying on a
default they do not care about.

### 16.8 Two more mutations, and what the numbers would have been

Total for the phase: **7 mutations run, 7 caught.** The colour crossing was mutated back to the
defect twice, once against each file, and both went red:

| mutation | caught by | reported |
| --- | --- | --- |
| `crossed = top` (the defect) | `no intensity is the neutral surface and not the accent` | `expected <[242, 242, 247]> but was <[82, 108, 142]>` |
| `crossed = top` (the defect) | `a fifth of the way down is still nearer the neutral than the accent` | `160 channels from the neutral and 0 from the accent` |
| `crossed = top` (the defect) | `the bottom of the slider is an opaque neutral rather than absent` | `painted #34537a but the neutral is #f2f2f7, which is 190 channels away` |

The alpha-only assertions in `GlassIntensityTest` **stayed green** through all three, which is the
demonstration that the pre-existing suite could not have caught this and the reason the new
assertions had to be about colour rather than a stronger version of the same arithmetic.

<a id="169-still-unverified"></a>
### 16.9 Still unverified

The three panels are the travel's ends and the reported 20%. The other 98 positions are an
unmeasured blend of the two interpolations, which is the accepted cost of a continuous control
and is unchanged by this fix — but it now interpolates *two* quantities rather than one, so the
"unverified intermediate" caveat covers a surface with two moving parts instead of one.

---

</details>

<a id="phase-17--the-project-picker-was-not-using-the-scaffold"></a>
<details>
<summary><b>Phase 17 — the project picker was not using the scaffold</b> &mdash; <i>done</i></summary>

## Phase 17 — the project picker was not using the scaffold

### 17.1 The defect, and why it survived review

The picker was reported as having a background that is "too translucent". It was, by about a
factor of three, and the cause was that it drew its own surface:

```kotlin
.ttGlassSurface(shape = shape)   // no `base`
```

`ttGlassSurface` has two washes. With a `base` it is a lens *over* something solid — white fading
to nothing, with the base showing through underneath — and the scaffold passes
`ttSurface()` at `1f - LENS_ALPHA`. Without one the fill ramp runs instead: shipped alpha
`FILL_ALPHA * FILL_ALPHA` = 0.3025, tinted toward the accent. So the sheet listing the projects
the user is choosing between was 0.3025-alpha accent, while the sheet that *creates* a project
was a 0.92 opaque neutral.

What made it survive review is that the two are the same rounded rect, with the same hairline,
from the same design system, at the same radius. They read as one vocabulary in a screenshot.
Only compositing told them apart.

The other half of the defect was structural: because the picker had its own frame, it also had a
hand-rolled scrim and its own copy of the tab bar's scroll-under inset arithmetic in
`NavigationHost`, which is a second spelling of reasoning the scaffold already owns and does not
get updated when that reasoning changes.

### 17.2 The one thing that made this unsafe to fix directly

`TTBottomSheetScaffold` applies `.verticalScroll` unconditionally, and the picker's body is a
`LazyColumn`. Compose refuses to measure a scrollable inside a scrollable of the same axis, and
it refuses at *measure* time, not compile time — so wrapping the picker in the scaffold as it
stood would have compiled, run in previews, and crashed on the first frame that overflowed.

So the scaffold grew a `scrollable: Boolean = true`. It is defaulted rather than named because a
`Column` body is the common case and a body that forgets should land on the arrangement that
scrolls rather than on one that can crash. Defaulting it also means the two existing callers are
untouched, and the design-system suite stayed at 83/80 with no new failures.

### 17.3 Why this needed pixels, and what it measured

"Is the background the scaffold's" is readable off the modifier chain. "The sheet is opaque enough
to read a list through" is not — a surface can be handed the right base and still composite to
something translucent, and computing `0.92 * neutral + 0.08 * backdrop` in the test would be
asserting the arithmetic rather than the frame. That is the modelling trap, and this repo has
already shipped two of them.

The measurement is a **difference between two backdrops**. The same sheet is rendered over pure
black and over pure white and the assertion is on how far apart the two frames are inside the
sheet. That needs no token, no predicted alpha, and no knowledge of which pixel is background:
an opaque sheet transmits the lens and little else, a translucent one lets most of the page
through.

| | over black | over white | apart |
| --- | --- | --- | --- |
| Dark, fixed | `#1b1b1d` | `#2f2f31` | **20** channels |
| Light, fixed | `#dfdfe4` | `#f3f3f8` | **19** channels |
| Light, defect | `#262c34` | `#d8dee6` | **178** channels |

Light and Dark were measured separately and the iOS simulator returned **19 and 20 — identical to
the JVM**. They agree because the probe point is a flat padding strip rather than a glyph, which
is the whole reason it is a padding strip. The bar is 51 channels: 31 above the correct reading
for compositing rounding, 127 below the defect's.

<a id="174-where-it-samples-and-the-mutation-that-cost-a-cycle"></a>
### 17.4 Where it samples, and the mutation that cost a cycle

The measurement is a horizontal strip just below the sheet's content, inside the scaffold's own
`xxl` bottom padding. Sampling the content would have been wrong in a way worth recording: each
row is a `ttGlassCard`, which is *itself* an untinted fill ramp, and those rows sit on the sheet
and so do not move with the page however translucent the sheet is. A median over the content area
would be dominated by rows and would report an opaque sheet while the sheet was the most
translucent thing in the frame.

The mutation that cost the cycle is the same trap from the other side. The natural first attempt —
put the untinted `ttGlassSurface` back on the *content* — **left the reading at 19 channels**. It
should: that surface stacks on the scaffold's frame, which is already opaque, so the page still
cannot reach the list. It is not the defect. The mutation that reproduces the defect is removing
the solid `base` from `TTBottomSheetScaffold`; bypassing the scaffold altogether reads 255.

Both tests are mutation-proven:

| mutation | caught by |
| --- | --- |
| scaffold's `base` removed | `the sheet does not transmit the page behind it` (178 > 51) |
| scaffold bypassed entirely | `the sheet does not transmit the page behind it` (255) **and** `the sheet is framed by the shared scaffold` (`assertExists` on the scrim tag) |
| untinted surface added to the content | **nothing — correctly**, see above |

The third row is in the table because a mutation that *should* stay green is only worth recording
once you have checked it does.

### 17.5 Baseline

`:shared-ui` 24 → **26** tests, still **2** failures on both hosts
(`aChosenThemeOutlivesTheProcess`, `theBoardReturnsToTheList` — pre-existing, untouched).
`:packages:design-system` unchanged at 83 JVM / 80 iOS. The two new tests live in `:shared-ui`
rather than beside the picker because `:packages:feature-projects-presentation`'s `commonTest`
does not currently compile — the two `ttTheme(ThemeScheme.Dark, content)` call sites whose second
positional argument is now `accentPalette`. That is pre-existing and deliberately not folded in.

### 17.6 What is still unverified

The exit transition's *timing* is not asserted anywhere, only that the sheet stays composed long
enough for one to run. And nothing here proves the picker looks right — that is a person opening
the app. The numbers above prove the sheet stopped transmitting the page; they cannot say whether
the result is *pleasant*.

</details>

<a id="phase-18--the-app-had-no-icon"></a>
<details>
<summary><b>Phase 18 — the app had no icon</b> &mdash; <i>done</i></summary>

## Phase 18 — the app had no icon

### 18.1 What was actually wrong

Two different failures, and only one of them looked like a failure.

**iOS had no icon at all.** `AppIcon.appiconset/Contents.json` declared the 1024×1024
universal slot and gave it no `filename`, so `actool` compiled the catalog happily and the
bundle shipped without a primary icon. Phase 14.3 ticked this box as done, which is the
part worth recording: the checkbox was satisfied by a file existing, not by the icon
existing.

**Android's icon existed and was unrelated to the product.** `ic_launcher_foreground.xml`
was a white box-and-bar glyph on `#1C1C1E` — a placeholder, not the brand accent, and drawn
in the 108dp viewport with no regard for the mask. It was the one screen in the app with
`#1C1C1E` on it, which is the same colour `Colors.android.kt` uses as its
*missing-theme fallback* (`fallbackColor()`). An icon is the one thing that cannot fall
back.

### 18.2 The composition

Three board lanes at 18% white behind a bold checkmark, on a diagonal ramp from `#40659A`
to `#22354F`.

The ramp's two stops average to `#314C74`, within 3/255 of `TaskTrackerAccent.Light`
(`#34537A`) on every channel. So the icon is the accent token lifted and deepened rather
than a second colour that happens to sit near it, and the two stay recognisably the same
colour. The ramp runs along the anti-diagonal to match `AppMark`'s `.topLeading` →
`.bottomTrailing`.

The lanes are at 18% and are meant to be texture, not a second element. At the size a
launcher actually draws an icon — around 40px before the mask takes its third — three
separate bars *in front of* a checkmark are the first thing to turn to mush. Behind it,
they read as depth at a large icon size and are simply absent at a small one, which is the
right failure mode: the mark loses a texture rather than losing a silhouette. `_assert_lanes_stay_behind_the_mark`
fails the generator above 0.25 rather than leaving it to taste.

The stroke is **0.24 of the glyph box**. `AppMarkDefaults` is `0.085 / (30/68)` = **0.193**
of its own. Same reasoning as the geometry below: heavier because it has to survive a
downscale.

### 18.3 Why `AppMark` was not changed to match

`AppMark`'s proportions were not reused, and they were not adjusted. Its geometry is tuned
for a 68dp tile drawn at 1x, inside a screen the user is already looking at. A launcher
icon is seen at roughly 40px and then masked down to about half that, so this file uses a
short arm reaching further left (`0.04` against `0.10`) with the vertex moved to match
(`0.34` against `0.38`), widening the corner a small render is most likely to round off,
and a long arm climbing further (`0.78→0.14` against `0.80→0.18`) so the diagonal cannot
flatten into a horizontal dash.

Two different numbers for two different sizes is correct. Pretending otherwise is how a
logo ends up with a stroke that looks heavy in the splash and flimsy on the home screen.

### 18.4 Platform differences, and why there are fewer than usual

- **`minSdk` is 26**, so adaptive icons cover *every* device this app targets. The legacy
  `mipmap-*/ic_launcher.png` rasters that the other option would have added are dead
  weight — no device that can install this APK can reach the branch that would read them.
- **Android content is fitted to the 33dp safe circle**, not the 72dp safe square. The
  square would let the lanes' round ends sit outside the circular mask, and the circle is
  the one every launcher can be relied on to show. Measured: the farthest drawn point is
  **405.1 canvas px** from the centre, giving **0.08146 dp/px** and a lane span of
  **53.4dp of the 108dp viewport (49%)**.
- The bound is the **capsule endpoints**, not the geometry's bounding box. Every shape here
  is a union of circles and capsules, so the farthest point of a cap at `p` is exactly
  `|p| + r`. The bounding box would be simpler and would shrink the icon for nothing: this
  composition is wide and flat, so its box has empty top corners. Worth about 3dp of width.
- **Monochrome is the checkmark alone.** Android 13+ tints that layer with the user's
  wallpaper palette and picks the contrasting shade itself, so anything low-alpha in there
  becomes a muddy second tone at best.
- **iOS gets a dark-appearance variant** (`luminosity: dark`): same white mark, field
  dropped to `#232F3F`→`#121820`. Apple darkens the icon rather than shipping a second
  mark, which is also what the monochrome layer does.
- **No alpha channel.** `actool` rejects one. `_write_png` emits colour type 2 (RGB)
  directly so the file is correct before it reaches Xcode, rather than relying on a tool to
  flatten it.

### 18.5 Why a generator, and why not Pillow

The PNG and the vector have to be the same picture, and they are the two files in this repo
most likely to drift: nobody compares a rendered `ic_launcher_foreground.xml` against a
1024px PNG by looking at them. `tools/generate_app_icon.py` emits both, plus the dark PNG,
from one geometry.

It is not a Pillow script because there is no Pillow and no numpy on this machine, and a
build tool that needs a `pip install` is one that stops working the next time the
virtualenv is rebuilt. `zlib` is standard library, a PNG is a zlib stream in four chunks,
and anti-aliasing comes from analytic signed-distance coverage rather than supersampling —
faster at 1024px, and exact at the round caps.

### 18.6 The bug the generator shipped with, and how it presented

The first run rendered the lanes as solid white bars running edge to edge, and an ASCII
preview of a *later* build still showed them as a dashed flicker. Two different defects,
worth separating because only one was real:

**The real one.** `_render` carried the gradient in `r/g/b` and composited into those same
variables. Composite operations are additive, so the first pixel the check painted white
raised the accumulator to white and every pixel to its right inherited that. The mask was
consulted correctly at every single pixel; the colour it produced simply was not reset. It
looks like a mask bug and is not one — the fix is to carry the gradient in `gr/gg/gb` and
composite on copies. The signature that identifies it is that the *leading* edge of every
shape is correct and everything to its right is solid: a mask bug would be wrong at the
edge too.

**The other one was the diagnostic, not the icon.** The preview sampled one pixel per
character cell at a 16px stride, so it landed on a lane about a third of the time and
missed it the rest. Averaging the cell is what makes the shape legible. Same discipline as
[17.4](#174-where-it-samples-and-the-mutation-that-cost-a-cycle): a diagnostic that cannot
distinguish "this is wrong" from "I am looking in the wrong place" is not a diagnostic.

### 18.7 Verified

| check | result |
| --- | --- |
| white on light ramp top (`#40659A`) | **5.92:1** |
| white on light ramp bottom (`#22354F`) | **12.42:1** |
| white on dark ramp top (`#232F3F`) | **13.55:1** |
| white on dark ramp bottom (`#121820`) | **17.84:1** |
| `actool` on the whole catalog | exit 0; emitted `AppIcon60x60@2x.png`, `AppIcon76x76@2x~ipad.png`, `Assets.car` |
| `:androidApp:processDebugResources` | **BUILD SUCCESSFUL** (AAPT2 links all three vectors) |
| PNG format | `1024 x 1024, 8-bit/color RGB, non-interlaced` — no alpha, as `actool` requires |

The contrast gate runs in the generator and **fails the build**, not the launch, if white
drops below 4.5:1 on any of the four stops. It checks the *lightest* stop, not the darkest:
a mark that clears at `#22354F` can still fail at `#40659A`.

### 18.8 What is not verified

No test was added and the baseline is unchanged — there is no source set that can see a
PNG or a `res/drawable`, and a test that asserts a hex is a test that cannot fail for a
reason anyone would act on. The contrast gate in the generator is the honest equivalent:
it fails for a specific, actionable reason, and it fails at build time.

Nothing here proves the icon looks *good*. It proves the mark clears the text contrast bar
everywhere it is drawn, that it sits inside the tightest adaptive mask, and that both
platforms compile the same geometry. Whether three lanes at 18% read as a board or as noise
is a person putting the app on a home screen.

</details>

<a id="phase-19--the-palette-audit-the-error-red-was-never-held-to-the-accents-bar"></a>
<details>
<summary><b>Phase 19 — the palette audit: the error red was never held to the accent's bar</b> &mdash; <i>done</i></summary>

## Phase 19 — the palette audit: the error red was never held to the accent's bar

### 19.1 What was asked, and what the answer was

A sweep of every pairing the app actually paints, in both appearances, against the bars in
`AGENTS.md`. The palette is sound. The accent clears both bars in both appearances across all
six palettes; `label`/`bodyLabel` sit between 15.25:1 and 21:1 everywhere; the glass fill is
visible on its own canvas in both (1.64:1 light, 1.28:1 dark); and the `LENS_ALPHA` tuning from
the sign-in-button work still holds (6.39:1 light, 4.73:1 dark).

The defect is narrower and worse than "some numbers are low": **one colour was exempt from the
suite that exists to police the others.**

### 19.2 The root cause: `AccentContrastTest` iterates the accent, and only the accent

`AccentContrastTest` holds every `TaskTrackerAccent` variant to two bars — 4.5:1 against
`ttOnAccent` for the label drawn on it, 3:1 against its own canvas for the control to be visible.
`ttError` is not in that list, and `ErrorTokenTest` covers only `ttErrorFill`/`ttErrorRim`'s
*alphas* (26 and 64 of 255), never their contrast.

So the red was never measured against a label. Measured now:

| pairing | light | dark | bar |
| --- | --- | --- | --- |
| `ttError()` vs `ttOnAccent` (prominent destructive button) | **3.55:1** | **3.41:1** | 4.5:1 |
| `ttError()` vs canvas (the `WarningGlyph`) | 3.55:1 | 6.16:1 | 3:1 non-text — passes |

**Dark is the worse of the two**, which is the opposite of every other pairing in the package and
is what makes this look like rounding error rather than a gap. It is not: it is a white label on
a red button. Three call sites paint it, all `TTGlassButtonStyle(tint = ttError(), prominent = true)`
— `ProjectsListScreen.kt:340`, `WorkItemEditor.kt:453`, `WorkItemEditor.kt:625`.

The repository already knows this bug class by name. `LENS_ALPHA` was cut from 0.16 to 0.08 for
the stated reason that the wash "lands on the *only* fill in the app that carries a label, so it
spends the accent's contrast margin", and `GlassFillContrastTest` holds the result. The error red
simply never had that treatment.

### 19.3 The error banner's message was drawn in its own rim token

`ProjectsComponents.kt` drew the banner's message *and* its dismiss glyph in `ttErrorRim()` — the
**rim** token, error at 25% alpha — composited over the banner's own `ttErrorFill()` at 10%, on
the canvas.

| | banner fill | message | ratio |
| --- | --- | --- | --- |
| light | `#FFEBEA` | `#FFBFBC` | **1.37:1** |
| dark | `#1A0706` | `#531613` | **1.39:1** |

Pale pink on pale pink, in **both** appearances, on the one component whose entire job is to be
read. Seven screens render it (`WorkItemEditor.kt:347,535`, `ProjectArchiveScreen.kt:93`,
`ProjectBacklogScreen.kt:115`, `ProjectsListScreen.kt:126,129`, `ProjectBoardScreen.kt:184`).

**The fix, and the fix that was tried first and rejected.** The obvious repair is `ttError()`. It
is not sufficient: `#FF3B30` on the light fill measures **3.10:1** — a different bug rather than
the absence of one. What ships is `ttLabel()`, at **18.37:1** light and **19.53:1** dark. The
banner's error-ness is already carried three times over (the fill, the rim, and `WarningGlyph`),
so the message does not also have to be red, and an unreadable failure notice is worse than a
legible one that is not coloured.

### 19.4 `ttLabelSecondary` is *worse* over glass than over the canvas

Undocumented, and the direction is the opposite of intuition. An untinted glass surface composites
to `#C2CBD7` in light — *darker* than a white canvas, and therefore closer to this token's own
dark grey. So the same 60% alpha that measures 3.44:1 on the canvas measures:

| | over canvas | over a glass card |
| --- | --- | --- |
| light | 3.44:1 | **2.81:1** — below even the 3:1 large-text bar |
| dark | 6.36:1 | 5.85:1 |

Eleven files in `:packages:feature-projects-presentation` use `ttLabelSecondary` and **all eleven**
also build glass surfaces, so this is not hypothetical. It is recorded and *not* fixed here: it is
a design decision about how much weight a caption carries on glass, not an arithmetic slip.

### 19.5 The glass hairline does not exist in light appearance

`ttGlassSurface`'s rim is `Color.White` at `HAIRLINE_ALPHA` on **both** appearances — `Glass.kt`
says so, and `GlassIntensity.kt` confirms there is no per-appearance factor. White at 14% over the
light glass fill measures **1.08:1**: against its own fill. In dark it is 1.55:1, and 1.98:1
against the canvas, so the rim is doing real work there.

`Glass.kt` asserts the rim "is what makes the surface read as raised". That is true in dark and
measurably false in light, where light-mode glass has no lit edge at all. Also recorded, not fixed.

### 19.6 A KDoc figure that was wrong by a factor of 1.75

`ttFixedColours.labelSecondary`'s comment claimed the token measures "3.44:1 on light and 3.64:1 on
dark". Light is right. **Dark is 6.36:1** — which means the comment's conclusion, "does not clear
the 4.5:1 body-text bar", is true of light and false of dark, and the guidance it gives is wrong
for half the users of the app.

Nothing caught it because `PinnedAppearanceTest` asserts only `secondary >= 3f`. That is now
stated as deliberate in the KDoc: this is the one token whose guidance is per-appearance, so a
single threshold is the actual invariant and pinning a ratio would pin the wrong thing.

### 19.7 Tests

Two, with the second expected to be red.

**`ProjectPaletteTest`** (`jvmTest`, one test) — no `color = ttErrorRim()` / `color = ttErrorFill()`
anywhere in this module's `commonMain`. It is a *source* scan on purpose: the defect was an
argument that was passed, and read the pixels back the message is legible while read the source
it still says `color = ttErrorRim()`. Every route to a Compose text node's resolved colour goes
through rasterising the node, which costs a `thisHostRasterisesText()` skip on the Android host —
so the assertion that matters most would be the one silently not running on a third of the
platforms. It checks `color =` only, so the banner's own `.border(1.dp, ttErrorRim(), …)` — the
correct use, in the same function — is not flagged, and comment lines are skipped because the
explanatory comment quotes the offending call verbatim.

Limitation, stated rather than papered over: it needs a filesystem, so it runs on the **JVM host
only**. The module root comes from the test class's own code source walked up to a directory
containing `src/commonMain`, not from `user.dir` or a relative path.

**`AccentContrastTest.the error token carries a white label as legibly as the accent does`**
(`commonTest`, one test) — **red on both hosts by design**, landing at 3.55:1 light / 3.41:1 dark.
It exists to make the decision unavoidable rather than to describe a passing state. Fixing it means
either darkening the red until white clears 4.5:1 on it in both appearances, or moving the
destructive label off `ttOnAccent`; both are design calls, and neither should be made silently
while tidying a different file.

| mutation | caught by |
| --- | --- |
| `ProjectsErrorBanner` message reverted to `ttErrorRim()` | `ProjectPaletteTest` — names `ProjectsComponents.kt:228` and `:230` with both source lines |

### 19.8 Baseline

`:packages:design-system` JVM **81** tests, **1 failing by design** (the error-token bar).
`:packages:feature-projects-presentation` JVM **165** tests, 0 failing.

Both figures are from the JUnit XML and both cross-check against `@Test` declarations exactly
(78 `commonTest` + 3 `jvmTest` = 81). `:packages:design-system`'s commonTest additions compile for
`iosSimulatorArm64`; the iOS **run** was not repeated this session.

**A bookkeeping correction.** Phase 17.5 recorded this module at "83 JVM / 80 iOS". The declared
count is 81 on the JVM host, so that figure was already wrong by 2 before this phase and was not
caught, which is the same failure `AGENTS.md` warns about when it says counts must come from the
XML rather than from a running total. iOS is left unrecorded rather than guessed: this phase
measured one host and did not invent the other.

### 19.9 What is still unverified

Findings 19.4 and 19.5 are **sRGB models**, and `AGENTS.md` rule 2 says a model is not the
compositor. 1.37:1 and 3.55:1 are far enough below their bars that the verdict is not in doubt;
2.81:1 and 1.08:1 are not, and both need rendered pixels on **both** the JVM and the iOS
rasteriser before either is called a defect. The design-system test hosts can do this and are
unaffected by the `ShellPreferences.kt` break.

</details>

<a id="phase-20--the-design-system-review-and-four-things-it-was-missing"></a>
<details>
<summary><b>Phase 20 — the design system review, and four things it was missing</b> &mdash; <i>done</i></summary>

## Phase 20 — the design system review, and four things it was missing

A pass over all 16 files of `:packages:design-system` (4,663 lines) plus every call site in the
repo. The package's *documentation and test discipline* is the best in the codebase and nothing
here criticises it. The gaps were coverage, ownership and duplication.

### 20.1 Two banners, and the drift between them

`ErrorBanner.kt` (auth) and `ProjectsErrorBanner` (projects) were the same component twice. They
had diverged on six axes, and the one that mattered was the message colour: the auth copy used
`ttLabel()`, the projects copy used `ttErrorRim()` — the **1.37:1** defect from Phase 19, on seven
screens. Nothing could catch it, because each module's tests could only see its own copy.

**One `ttBanner` now lives in the design system.** `Banner.kt`. Both feature copies are deleted
and all eight call sites point at it.

`ttBannerTone` is a parameter rather than a second component because the projects screens were
*already* passing non-failures through their error banner — `RemovalNotice.ArchivedInstead`
("3 items were archived instead") was painted in the failure red. That is a **deliberate
behaviour change**: those notices are now accent-toned. It is the one user-visible change in
this phase and it should be looked at.

### 20.2 The design system was citing a feature for one of its own constants

`ttGlass.PRESSED_SCALE` existed, with a KDoc explaining the press response — and **no component
applied it**. `PrimaryButton` carried a private `0.97f` of its own. `DISABLED_ALPHA`'s doc
literally said "the same value `PrimaryButton` uses", describing a coincidence between two
constants with nothing to do with each other.

- `ttGlassButton` now applies `ttGlass.PRESSED_SCALE`, with `indication = null` so the scale is
  the only reaction.
- `ttGlassButton(loading = true)` added: the label and the spinner cross-fade, and the button
  stops answering taps. Deliberately **not** `enabled = false` — that carries `DISABLED_ALPHA`
  with it, and a busy control is not a dimmed control.
- `PrimaryButton`'s private `Spinner` became `ttSpinner` in the design system.

### 20.3 Six copies of `1.dp`

`ttSeparatorWidth` now exists. Six call sites were spelling one number: three chip/badge outlines
and the hairline rule in `SettingsScreen`. It is deliberately **not** `ttGlass.hairlineWidth`
(0.5dp) — a glass rim is a highlight, an outline has to be visible against its own fill.

**A correction to the review that prompted this.** That review claimed "callers hand-roll
dividers". On checking, only **one** of those sites was a divider; the rest are chip outlines,
field-focus borders and badge tints. A `ttDivider` component would have had one call site, so
what shipped is a token instead.

### 20.4 Two pieces of dead public API, one resolution each

- **`ttGlassAddButton`** — zero call sites, zero tests. Kept, and its KDoc now says so plainly,
  with a rule for deleting it: if the next screen wanting a floating create does not reach for
  it, it goes. Dead API reads as *available*, which is worse than absent API.
- **`ttBodyLabel`** — an `expect` plus three `actual`s and a KDoc arguing it is "the fourth
  semantic role the app actually has", with **zero call sites** and no contrast assertion
  anywhere. Still unresolved and deliberately left alone: the right move is to adopt it over the
  11 files reaching for `ttLabelSecondary` on glass, and that depends on 20.6.

### 20.5 Six previews for the components that had none

`ttSlider`, a slider in its busy state, `ttInitialsStack`, `ttBanner` (both tones), glass
intensity at 1.0 / 0.5 / 0.0, and `TTBottomSheetScaffold`. The gallery covered ten components
and these were the ones nobody could *look at* — including `ttSlider`, which has the most
intricate logic in the package.

### 20.6 Not done: the rim and the on-glass caption

Both are Phase 19's findings 19.4 and 19.5, both belong in `ttGlassSurface` so one edit fixes
every surface, and **neither is touched** — they need rendered pixels on both rasterisers first
(19.9). Shipping them on an sRGB model would repeat the exact mistake `AGENTS.md` rule 2 exists
to prevent.

### 20.7 Tests, and a test that could not fail

| test | host | what it holds |
| --- | --- | --- |
| `BannerContrastTest` (3) | common | the token-level relationship: ink over a 10% error fill clears 4.5:1 |
| `BannerSourceTest` (2) | jvm | the banner's text nodes are `ttLabel()`; the two tones differ |
| `ProjectPaletteTest` (1) | jvm | no rim/fill token used as a text colour (Phase 19) |

**`BannerContrastTest` stayed green through both mutations of its own defect.** The message
colour moved to `ttErrorRim()` — the original bug — and it passed. The fill alpha moved 0.10 →
0.22, and it passed. Neither is a rounding error: `ttLabel()` is black on light and white on
dark and a banner's fill is always a pale tint near the canvas, so *black on pale pink clears
4.5:1*. The test computed its own fill instead of reading the drawn one, and a contrast ratio
held a constant about a component it did not look at.

That is what `BannerSourceTest` is for, and it asserts the *argument* rather than a ratio. Its
KDoc says so, so the next person does not "fix" it by deleting the weaker one.

Two false reds along the way, both worth recording because a false red teaches everyone that an
assertion is noise: the guard's first regex swept every `color =` in the file and so flagged the
border's `color = rim` and the glyph's `color = base` — both correct. Scoping it to `ttText(`
calls by balanced-paren parse fixed it.

| mutation | caught by | not caught by |
| --- | --- | --- |
| message → `base.copy(alpha = RIM_ALPHA)` | `BannerSourceTest`, names `base.copy()` | `BannerContrastTest` |
| fill alpha 0.10 → 0.22 | — | `BannerContrastTest` |

### 20.8 Unblocking, and one test that is not mine

`:packages:feature-projects-presentation` did not compile, so nothing in this phase could be
verified until it did. `ProjectBoardModel.kt:72-73` read `currentUserName?.trim().takeIf { … }`,
where the `?.` makes the receiver `String?`. Two characters (`?.takeIf`) — the author's evident
intent was already in the `?: return false`, and the file had not moved for ten minutes.

That fix exposed a **stale test**, not a regression:
`ProjectBoardModelTest.toggling showOnlyMyTasks filters items by assignee presence` expected 1
item and got 0. The in-flight `currentUserName` work changed the filter from "has an assignee"
to "is the current user" and had not updated the test. **Left red on purpose** — deciding whether
a stale test should be updated or the filter reverted is the author's call, not this phase's.
(It has since been updated by the author and is green.)

`:shared-ui` remained uncompilable (`ShellPreferences.kt:138-139`, the same `currentUserName`
work) and was untouched by this phase. Fixed later; see 20.10.

### 20.10 Uncompiling `:shared-ui` — `ShellPreferences.currentUserName`

The remaining compile break was one line and one mistake: `currentUserName` was a plain `val`
whose getter called `UserDefaultsStore.value(key: DefaultsKey)`, which is `suspend`
(`UserDefaultsStore.kt:33` — it may cross a platform boundary, so the whole facade is). The
reader is a `remember` block in a composition (`NavigationHost.kt:571`), which is not a
coroutine, so a property cannot be the shape the call needs. `ShellPreferences.kt:138-139`,
suspend-in-a-non-suspend-getter, both hosts.

**The one-line fix is the wrong one, and it is worth recording why.** Making the getter `suspend`,
or snapshotting the read into `restore()`, both compile — and the snapshot version is behaviourally
broken. `restore()` runs once per process from `RootScreen.kt:158`, *before* anyone has signed in,
and `DefaultsKey.CachedProfile` is only written by `AuthRepository.signIn`
(`AuthRepository.kt:132`). So a cold launch + sign in would hand `ProjectBoardModel`'s
"show only my tasks" filter a `null`, and `assigneeMatchesCurrentUser` returns `false` for a
`null` current user — the filter would silently match nothing, with no error and no red test.

Resolved as a `StateFlow` + an explicit `refreshCurrentUser()`:

| piece | file | why |
| --- | --- | --- |
| `currentUserName: StateFlow<String?>` | `ShellPreferences.kt` | the file's own convention for all four of its other preferences; gives a later board a name that arrives after it composed |
| `suspend fun refreshCurrentUser()` | `ShellPreferences.kt` | reads `CachedProfile` when the root is `Main`, which is the first moment a cached profile *can* be the current session's |
| one `LaunchedEffect` on `(isPreferencesRestored, navigation.root)` | `RootScreen.kt` | sign-in writes the profile *before* the coordinator moves the root, so arriving at `Main` is the signal, and re-reading on the next `Main` overwrites rather than accumulates |
| `collectAsState` + `remember` key | `NavigationHost.kt` | the board freezes the name into its model, so a late-arriving name has to build a new model |

Two things settled rather than left open. `preferredGreeting` (`AuthSession.kt:54`) is reused
rather than re-spelling display-name-else-email here — it is the one definition of what to call
this person, and a second spelling is how the shell's greeting and the sign-in screen's drift
apart. And `refreshCurrentUser` has **no** `runCatching`, unlike the raw-key reads in
`restore()`: those have a documented fallback, and this one cannot invent one. Keeping the
previous name on a throw would leave one account's name on the board after another signs in, and
inventing one would filter a real person's own work out of their own board. `null` is correct for
both cases the store can actually report.

**Cost, stated rather than hidden:** on a cold launch the board model is built twice — once with
`null`, once with the name. That is the rebuild-and-cancel-the-previous-observation
`ProjectsHost.kt:220` already documents, and the `DisposableEffect` below it cancels the old
observation, so this cannot leave two boards watching the store.

**A mutation that proved the path is dead to the suite.** Deleting the `refreshCurrentUser()`
call left `:shared-ui:jvmTest` byte-identical: 28 tests, the same 2 pre-existing failures. Nothing
in the suite renders `RootScreen` with a seeded `CachedProfile`, so the whole feature could be
deleted and no test would notice. This is a real coverage gap, not a passing verification — it
needs a scenario that seeds a cached profile, drives the root to `Main`, and asserts the board
received the name. Not written here: it is a new test, and this phase fixed a compile break rather
than adding behaviour tests. Recorded so the next person does not read the green suite as
evidence the filter works.

### 20.11 Baseline

`:packages:design-system` **86** JVM tests (81 `commonTest` + 5 `jvmTest`), **1 failing by
design** — the error-token bar from Phase 19.

`:packages:feature-projects-presentation` **165**, 0 failing — the stale `ProjectBoardModelTest`
is green.
`:packages:feature-auth-presentation` **20**, 0 failing.
`:packages:feature-projects-data` **153**, 3 failing (`SeedingTest`, documented pre-existing).
`:shared-ui` **28** JVM / **26** iOS, 2 failing on both hosts (`aChosenThemeOutlivesTheProcess`,
`theBoardReturnsToTheList`, documented pre-existing) — the module now compiles and runs, which it
did not in 20.9.

Whole JVM host: **1097 tests, 6 failures**; whole iOS simulator host: **1072 tests, 6 failures**.
Identical set on both: 3 `SeedingTest`, 2 `:shared-ui`, 1 deliberate error-token bar.
`verifyArchitectureGraph` and `verifyModuleImports` pass. All figures from the JUnit XML the
build writes, cross-checked against `@Test` declarations.

</details>

<a id="phase-21--ttbodylabel-adopted-and-shared-ui-back"></a>
<details>
<summary><b>Phase 21 — <code>ttBodyLabel</code> adopted, and <code>:shared-ui</code> back</b> &mdash; <i>done</i></summary>

## Phase 21 — `ttBodyLabel` adopted, and `:shared-ui` back

### 21.1 `:shared-ui` needed nothing from this phase

Phase 20 recorded it uncompilable (`ShellPreferences.kt:138-139`). By the time this phase
started it had been fixed by the same `currentUserName` workstream that broke it — the file's
mtime moved at **18:16:10**, and the fix was the author's, not this phase's. Nothing was
re-done.

What this phase did do is **verify** it, because "no longer compiling" and "working" are
different claims. `:shared-ui:jvmTest` now runs: **28 tests, 2 failures** — `aChosenThemeOutlivesTheProcess`
and `theBoardReturnsToTheList`, which are the two `AGENTS.md` records for this module and were
failing before any of this work began. Back to its documented baseline.

`ProjectBoardModelTest.toggling showOnlyMyTasks filters items by assignee presence` also went
green in the meantime, so the stale test Phase 20 left red on purpose has been resolved by the
workstream that caused it.

### 21.2 `ttBodyLabel`: adopted at two sites, and proven first

The token existed with an `expect`, three `actual`s, a long argument for why the design system
needed a fourth semantic role — and **no call sites and no assertions anywhere**. Its KDoc
claimed "17:1 in both appearances", which was a number nobody was checking, and
`AccentContrastTest` does not reach it because that suite is about the accent.

`BodyLabelContrastTest` (4 tests, `commonTest`) now holds that claim, and it was written before
the adoption so the token could be adopted against a measurement rather than a comment. Unlike
`BannerContrastTest` — which could not fail for its own defect — this one goes red: mutating
`bodyLabelLight` to a mid grey fails two of its four.

**Adopted**, both where the text is load-bearing rather than decorative:

| site | was | now | was measured |
| --- | --- | --- | --- |
| `LabelledField` field title, on the field's opaque `ttSurface()` | `ttLabelSecondary()` | `ttBodyLabel()` | **3.30:1** on that surface in light |
| `ProjectsEmptyState` body — the only copy on an empty screen | `ttLabelSecondary()` | `ttBodyLabel()` | **3.44:1** on the light canvas |

Both were below the 4.5:1 body-text bar. A word naming an input, and the single message an
empty screen exists to deliver, were each the hardest text on their screen to read.

### 21.3 What was deliberately *not* adopted, and why that is the discipline

`ttBodyLabel` appears at **two** call sites out of 55 `ttLabelSecondary` uses. A blanket
replacement would have been easy, wrong, and would have looked like thoroughness:

- **The field's placeholder** stays on `ttLabelSecondary()`. A placeholder is a hint about what
  could be typed; at full ink an empty field reads as a filled one. The *title* and the
  *placeholder* are different roles and Phase 21 is the reason the design system has two tokens.
- **The ~50 `ttLabelSecondary` calls on glass** are untouched. Over a glass card that token
  measures **2.81:1** in light — a real defect — but the fix needs rendered pixels on both
  rasterisers (19.9), and swapping full ink into every caption would flatten a deliberate
  hierarchy rather than fix a number.

Adoption was per-site and reasoned, not a find-and-replace. Two sites is the honest answer to
"where is this token actually correct", and it is much smaller than the 55 that were available.

### 21.4 Baseline

Whole JVM host: **1064 tests, 6 failures**, all six accounted for:

| count | what |
| --- | --- |
| 3 | `SeedingTest` in `:packages:feature-projects-data` — documented pre-existing |
| 2 | `:shared-ui` `aChosenThemeOutlivesTheProcess`, `theBoardReturnsToTheList` — documented pre-existing |
| 1 | `AccentContrastTest` error-token bar — **red by design** since Phase 19 |

`:packages:design-system` **90** (85 `commonTest` + 5 `jvmTest`), cross-checked against `@Test`
declarations exactly. `:packages:feature-auth-presentation` and
`:packages:feature-projects-presentation` both fully green.

---

</details>

<a id="phase-22--the-error-red-meets-its-bar-and-the-glass-hairline-is-measured"></a>
<details open>
<summary><b>Phase 22 — the error red meets its bar, and the glass hairline is measured</b> &mdash; <i>2 findings open</i></summary>

## Phase 22 — the error red meets its bar, and the glass hairline is measured

Two suggestions from the Phase 20 review, taken together: one is a fix and the other is a
measurement. They are in one phase because they are the same kind of claim — *"this token
passes the bar its KDoc claims"* — and one of them was false in a way arithmetic could not
see.

### 22.1 The error red: `dynamicOrPinned` deleted, and why that is the fix

Phase 19 left `AccentContrastTest`'s error-token bar red by design, because `ttError()` was
`UIColor.systemRed` on iOS and `android.R.attr.colorError` on Android, and a white label on
either of those does not clear 4.5:1. Phase 22 pins the colour instead:

| | before | after |
| --- | --- | --- |
| `errorLight` | `UIColor.systemRed` / `colorError` | **`#D3291F`** |
| `errorDark` | `UIColor.systemRed` / `colorError` | **`#DC2B21`** |

Measured on the tokens actually in the file, everything against the pairing it is drawn in:

| pairing | before (`#FF3B30`) | after | bar |
| --- | --- | --- | --- |
| a white label on the error fill, light | 3.55:1 | **5.11:1** | 4.5:1 |
| a white label on the error fill, dark | 3.55:1 | **4.76:1** | 4.5:1 |
| an error glyph on the canvas, light | 3.55:1 | **5.11:1** | 3:1 non-text |
| an error glyph on the canvas, dark | 5.92:1 | **4.41:1** | 3:1 non-text |
| `ttLabel` on a 10%-alpha error fill, light | 18.37:1 | **17.98:1** | 4.5:1 |
| `ttLabel` on a 10%-alpha error fill, dark | 19.62:1 | **19.92:1** | 4.5:1 |

**One figure went the wrong way and is left that way rather than hidden.** The dark error
glyph on the dark canvas drops from 5.92:1 to 4.41:1, because the red got *darker* and a
dark red has less luminance to stand out from a black page with. It clears the 3:1 non-text
bar by a wide margin and the light glyph — the one on a white page, which is where a warning
icon is actually read — improves from 3.55:1 to 5.11:1. A single red cannot be right for both
pages, and the pairing that had to hold was the label on the fill.

(The Phase 19 dark glyph figure was 6.16:1; the 5.92:1 above is `#FF3B30` against
`ttCanvas()`, which is what this table states. The label-on-fill figures are the ones Phase
19 left red, and they are the ones this phase closes.)

**How the two reds were chosen.** Both keep the systemRed hue (3.2°) and a saturation of
0.74, and both sit inside the window that keeps a white label above 4.5:1 — which for a hue
at 3.2° and that saturation means L* ≤ 0.18333. They are not the same colour, because a
single red cannot serve two appearances: dark needs more luminance than light to hold the
same ratio against a different surface pairing, and holding one value for both would mean
either a dark red that reads as muddy on white or a bright red that glows on black. The hue
being held is the deliberate part. A system red that is redder than `#D3291F` is redder than
the thing being replaced.

**The platform path is gone, not just bypassed.** `Colors.ios.kt` and `Colors.android.kt`
both now read `ttFixedColours.error(ttIsDarkTheme())`. Keeping `dynamicOrPinned` and only
changing `pinned` would have looked identical in every test on this host and would have left
a following-system appearance picking up `#FF3B30` — a red measured at 3.55:1 under a white
label, which is the
defect this phase exists to close. `dynamicOrPinned` is for colours the *system* owns
(backgrounds, separators, label tiers); a brand red that has to clear a contrast bar is not
one of them, and leaving the branch in place would be a comment promising something the code
does not do.

### 22.2 The glass hairline and the caption over it, rendered

`GlassSurfacePixelTest` reads the pixels a Compose frame actually produced. Both rasterising
hosts report the same figures **to the digit**:

| | modelled | rendered, JVM | rendered, iOS |
| --- | --- | --- | --- |
| `ttLabelSecondary` on the glass fill, light | 2.81:1 | **2.88:1** | **2.88:1** |
| the same caption, dark | 5.85:1 | **6.01:1** | **6.01:1** |
| the hairline against its own fill, light | 1.08:1 | **1.07:1** | **1.07:1** |
| the hairline against its own fill, dark | 1.55:1 | **1.54:1** | **1.54:1** |

The rendered light fill is **#c8d0db**, the caption composites to **#747780**, and the same
token on the bare canvas measures **3.44:1**. So the finding from Phase 19 is real, and it
is worse than "a low number": **glass is costing this caption contrast rather than adding
any.** The accent-tinted fill darkens toward the token's own grey, so putting a surface
behind a secondary label makes it *less* legible. A secondary caption placed on glass today
is a regression against the same caption placed on the page.

Both of these are now **red by design** and are meant to stay red until someone changes the
tokens they name. They are detectors, not gates.

### 22.3 How the rim was measured, and the three things that had to be fixed first

The hairline is 0.5dp. Three separate measurements had to be got right before it produced a
number at all, and each of them had failed silently rather than loudly:

1. **A `?: return` on a host that rasterises.** A null reading on the JVM or iOS means the
   measurement broke; returning made that a green test that measured nothing — the exact
   shape of the `BannerContrastTest` failure in Phase 20. Each test now checks
   `thisHostRasterisesText()` itself and *then* insists on a reading.
2. **The dp→px scale was taken from the card instead of the root**, giving ≈3.66 on a 1×
   host, sampling the canvas and reporting the fill as `#ffffff`. A *wrong* reading, which
   passes every assertion written against it.
3. **`getBoundsInRoot()` ignores `Modifier.offset`.** The card was offset 40dp and reported
   bounds of `(0, 0)` anyway, because bounds come from layout and `offset` is a placement
   offset. The card is no longer offset; the first version's fill sample happened to land
   inside the card regardless, which is the kind of luck that hides a bug rather than
   preventing one.

**The rim is found by differencing two frames, not two cards.** One frame renders the card
with the hairline, the second the identical card with `borderWidth = 0.dp`. The fill's wash
is a function of `y` alone, so the two frames must agree everywhere except where the rim
painted; the strongest disagreement along the top edge is the rim as the compositor mixed
it, and the control frame's colour at that coordinate is the fill it sits on. Two cards side
by side cannot work: the rim's colour depends on where the edge falls inside a device pixel,
so a card at `x = 0` and one at `x = 120` sample the same 0.5dp band at different subpixel
phases and blend it differently. That version returned no reading at all.

**And it has to be rendered at 3×.** `runComposeUiTest` renders at density 1, and at density
1 the hairline is **not drawn at all**: the two frames were compared across every pixel of a
1024×768 frame and the maximum channel difference was **0.0**, while the same comparison
against an 8dp border showed 0.13. A 0.5dp stroke is half a pixel and Skia drops it rather
than splitting it. `LocalDensity` is overridden to 3 for the rim measurement — an iPhone and
an Android phone are both at least 2×, so this is the case that ships rather than a
contrivance. **The finding is therefore specific to devices at 2× and above, which is every
device this app targets.**

The strongest row is used, not the first: the rim spans 1.5 device rows at this density and
one of them is a partial blend, so the first differing row is a *third* of the rim.

### 22.4 Mutation evidence

`HAIRLINE_ALPHA` was raised and the rim readings moved with it, which is what makes them
measurements rather than two constants that happen to be near each other:

| `HAIRLINE_ALPHA` | light rim as rendered | the fill under it | against that fill |
| --- | --- | --- | --- |
| **0.14** (shipped) | `#cad2dc` | `#c2cbd7` | **1.07:1** |
| 0.5 | `#e1e5eb` | `#c2cbd7` | **1.30:1** |
| 1.0 (an opaque white line, no longer a hairline) | `#ffffff` | `#c2cbd7` | **1.64:1** |

### 22.5 What 22.3 and 22.4 mean: the light rim cannot be tuned into existence

The dark rim reaches **1.54:1** against its own fill with the token exactly as it ships,
because dark fill is dark enough for white to be lighter than it. The light rim reaches
**1.07:1**, and the table above is the finding: even an **opaque white line** — no longer a
hairline in any sense — manages only 1.64:1 against the light fill.

So the light rim is **not** a tuning problem, and no value of `HAIRLINE_ALPHA` fixes it. The
light fill is already at `#c2cbd7` and there is no white above it worth having. A rim that
reads as a lit edge in light has to be *darker* than its fill — a shadow rather than a
highlight — which is a different design grammar from the one `ttGlassSurface` claims to port,
and a decision rather than a number. That is why the test is red by design and left red:
it is waiting on a decision, not on a constant.

The caption has no such excuse. Light glass captions sit at 2.88:1 against a 3:1 large-text
bar and 4.5:1 body-text bar, and the same token reaches 3.44:1 on the bare canvas, so the
fix is either a caption token chosen against glass, or a fill light enough that the existing
one works — and the first is the cheaper of the two.

### 22.6 Baseline

Whole JVM host: **1105 tests, 7 failures**, all seven accounted for:

| count | what |
| --- | --- |
| 3 | `SeedingTest` in `:packages:feature-projects-data` — documented pre-existing |
| 2 | `:shared-ui` `aChosenThemeOutlivesTheProcess`, `theBoardReturnsToTheList` — documented pre-existing |
| 2 | `GlassSurfacePixelTest` light caption (2.88:1) and light rim (1.07:1) — **red by design** |

`:packages:design-system` **94** (89 `commonTest` + 5 `jvmTest`), cross-checked against
`@Test` declarations exactly. `AccentContrastTest`'s error bar, red by design since Phase 19,
is **green** as of 22.1. `verifyArchitectureGraph`, `verifyModuleImports` and
`:androidApp:compileDebugKotlin` all pass.

`BodyLabelContrastTest`'s "body ink also clears the text bar on the canvas, which is where
empty states sit" lost its comma: a backtick test name may not contain one, and
`compileTestKotlinIosSimulatorArm64` rejected it. Caught by compiling the iOS host, which is
the reason the deciding command includes it.

---

</details>

<a id="phase-23--sprints-a-time-boxed-goal-a-set-of-tasks-and-a-verdict"></a>
<details open>
<summary><b>Phase 23 — Sprints: a time-boxed goal, a set of tasks, and a verdict</b> &mdash; <i>23.5.5-23.5.7, 23.6 partial</i></summary>

## Phase 23 — Sprints: a time-boxed goal, a set of tasks, and a verdict

**Nothing in this phase is built yet.** This is the breakdown, written down before any of it
so that the order is a decision rather than a sequence discovered halfway through.

### 23.0 What was asked, and the four decisions it turns on

A user can create a sprint board and add tasks to it. Every sprint has a goal, a start and
end date, and an owner; the owner can finish it; finishing it produces a summary of what was
done, what was not, how many story points were achieved, and whether the goal was hit. Work
items gain a story-points attribute.

Four things had to be decided before the work could be broken down, because each of them
changes what the tasks *are*:

| decision | choice | why this and not the other |
| --- | --- | --- |
| entry point | **a 5th tab, `Sprints`** | Adding a tab forces `tabTitle`, `tabIcon` and `isProjectScoped` to be updated, and those are exhaustive `when`s — so the tab bar cannot gain an unlabelled hole by accident. A push off the board would have been less code and a second-class destination. |
| where it lives | **the existing `feature-projects-*` triple** | A sprint is the same aggregate as the work items it holds. A new `feature-sprints-*` triple would duplicate the port, the Room wiring and the Compose screen tree for one more project-scoped concept. |
| story points | **`Int?`, `null` = unestimated** | Keeps "not estimated" distinct from "estimated as zero". A non-null `0` makes the summary silently wrong, and a summary that lies is worse than a summary that shows a blank. |
| the goal | **`goalStoryPoints: Int`**, a number | This is the one place the brief is ambiguous. A free-text goal statement reads well in a header but cannot answer "did it hit the goal", which is half of what the summary has to say. So the goal is a **target in story points**, and "hit" is `achieved >= target`. A sentence is welcome as `name`; it is not the goal. |

Two consequences worth stating before the list, because they are the parts most likely to be
discovered late:

- **A sprint cannot be closed until it has items, and the summary is computed from the items'
  final state — not from a snapshot taken when Finish was pressed.** A snapshot goes stale the
  moment a task is edited afterwards, and "the summary said we were done and then we edited
  a task" is a bug nobody notices until a stakeholder asks.
- **The app has no date-entry UI at all.** `WorkItem.dueDate` is a toggle, not a picker
  (`WorkItemCard.kt` formats the instant it is given; nothing ever chooses one). Sprint start
  and end dates are the first real date inputs in this codebase, so there is nothing to reuse
  and 23.4.1 is new work rather than a wiring exercise.

### 23.1 Domain — the entity and the two work-item attributes

**All six done.** `:packages:domain` **66** tests (cross-checked against `@Test`), `:packages:core`
**43**. Every assertion below is mutation-proven; the table is in 23.1.7.

- [x] **23.1.1 `SprintID`.** A `value class` over `String` plus a `randomSprintID()` factory in
  `:packages:core`, beside `ProjectID` and `WorkItemID`. Test alongside the existing id tests
  — a distinct id per call is the whole contract.
- [x] **23.1.2 `Sprint`.** In `Entities.kt` beside `Project`: `id`, `projectID`, `name`,
  `goalStoryPoints`, `startDate`, `endDate`, `owner`, `isFinished`, `finishedAt`, `createdAt`,
  `updatedAt`. `id`/`createdAt` are `val`, as on `Project`, because an entity that can change
  its own identity is a row the sync layer cannot match. `init` enforces `endDate >= startDate`,
  floors `goalStoryPoints` at 0, and keeps `finishedAt` consistent with `isFinished` in **both**
  directions — the same invariant shape as `WorkItem.reconcileResolutionAt`, which is where the
  two-directions rule is already written down. Test: an end before a start is refused; a
  finished sprint is dated; **reopening clears the date**, which is the case that gets
  forgotten.
- [x] **23.1.3 `WorkItem.storyPoints: Int?`.** `null` is unestimated and must survive a round
  trip unchanged. A negative arriving from a server is floored at 0 in `init`. Test: `null`
  survives; `0` is not `null`; negative is floored.
- [x] **23.1.4 `WorkItem.sprintID: SprintID?`.** `null` is "in the backlog", which is the state
  almost every item starts in. Test: an item with no sprint is still fully usable.
- [x] **23.1.5 The hit/miss predicate, as a pure function.** `achieved >= goal`, taking
  `achieved` rather than reading items itself, so it is callable from a test without a store.
  Test: under the goal misses, **exactly at the goal hits**, over hits. "Exactly at" is the
  case a `>` gets wrong.
- [x] **23.1.6 `SprintSummary`.** The value the end-of-sprint screen renders, built by a pure
  function from a `Sprint` and a `List<WorkItem>`: committed points, achieved points, done
  items, incomplete items, `didHitGoal`. The done/incomplete split **uses `status.isTerminal`**,
  the same predicate the archive uses, so the summary and the archive cannot disagree about
  which tasks shipped. Unestimated items count as tasks and as zero points — and that is stated
  in the KDoc, because "does an unestimated task count" is the question a reader will have.
  Test: the split agrees with `WorkItemGrouping`; a task with no estimate lands in the right
  list and adds no points.

#### 23.1.7 Four decisions that changed while building it

None of these were in 23.0 and all four were forced by a question the brief does not answer.

**A window that ends before it starts is constructible, not refused.** 23.1.2 said `init`
enforces `endDate >= startDate`. It does not, and the plan was wrong to say so. A sprint
pulled back in time arrives that way from a server, and a throwing constructor in a `data
class` would take down the read of the *whole* list rather than quarantining the one bad row.
`init` only **floors** the goal; the window is a question, and [Sprint.isActiveOn] is what
answers it.

**A sprint with no goal has no verdict, so the answer is `Boolean?`.** `didHitGoal` returns
`null` for a goal of zero rather than `false`. A `false` would read as *we missed* — as much
an invention as a `true`, which is the same over-crediting defect Phase 22 found in the light
glass rim. Forcing the answer into a `Boolean` is what produces a sprint that always reports
success. The third state is the honest one, and `SprintSummary.didHitGoal` carries it so every
renderer has to handle it.

**Achieved points counts only *delivered* items, while the done/incomplete split counts
everything that left the flow.** `WorkItemStatus` has two different predicates and they are
not the same question: `isComplete` is `Done` alone, `isTerminal` is `Done` or `Closed`. A
closed item is reported under done — it is no longer outstanding work — but contributes
**nothing** to `achievedStoryPoints`, because nothing was delivered. Counting it would let a
sprint claim points for work that was decided against. Both choices are asserted separately
(M8 below caught the merge of them).

**The builder filters to the sprint's own items rather than trusting its caller.** Every
caller here holds a project's whole work list, because that is what the board and the backlog
have, so `sprintSummary` takes the list it is given and filters on `sprintID`. There is no
way to produce a summary that counted another sprint's work.

#### 23.1.8 Mutation evidence — 13 run, 13 caught

Driven by `tools/mutate_23_1.py`, which applies one defect, runs the host, records what went
red, and restores the file.

| # | the deliberate defect | caught by |
| --- | --- | --- |
| M1 | `Sprint`'s goal floor removed | `aNegativeGoalIsFlooredRatherThanStored` |
| M2 | the floor applied to `null` instead of to negatives | `aNegativeEstimateArrivingFromAServerIsFlooredAtZero`, `anUnestimatedItemKeepsItsNull` |
| M3 | `reconcileFinishedAt` no longer clears on reopen | `reopeningClearsTheDate` |
| M4 | `reconcileFinishedAt` restamps with the local clock | `anExistingFinishDateIsNotRestampedWithTheLocalClock` |
| M5 | `isActiveOn`'s end made exclusive | `theWindowIncludesBothEnds` |
| M6 | `sprintID`'s **default** changed to a random sprint | `aNewItemLandsInTheBacklogWithoutBeingToldTo` |
| M7 | the sprint filter removed from `sprintSummary` | `anItemInAnotherSprintIsNotCounted` |
| M8 | `isComplete` merged into `isTerminal` | `onlyDeliveredItemsCountTowardsTheAchievedPoints` |
| M9 | `>=` written as `>` | `landingExactlyOnTheGoalIsAHit`, `theSummaryCarriesTheVerdictRatherThanLeavingItToTheView` |
| M10 | no-goal answered `false` instead of `null` | `aSprintWithNoGoalHasNoVerdictRatherThanAVictory` |
| M11 | the incomplete list left in input order | `bothListsComeBackInBoardOrderRatherThanTheOrderTheyWereHandedOver` |
| M12 | `randomSprintID` made deterministic | `generatedIdentifiersAreDistinct` |
| M13 | `parseSprintID` always returning `null` | `aSprintIdParsesAndRejectsLikeEveryOtherOne` |

**M6 is the one that found a hole in a test, and it is why this table exists.** The first
`WorkItemEstimationTest` passed `sprintID = null` *explicitly*, so it never exercised the
default at all — and a default handing every new work item a random sprint passed the whole
suite. That defect scatters new work into sprints nobody created, and nothing else in 23.1
would have noticed. The fix is `aNewItemLandsInTheBacklogWithoutBeingToldTo`, which
constructs a `WorkItem` and asserts on the default rather than on a value it supplied.

One assertion was written and then **deleted** as non-load-bearing:
`theOwnerIsFreeTextLikeAnAssigneeRatherThanAUserId` asserted that a string is stored
unchanged, which no implementation could plausibly get wrong. The `String?` choice for
`owner` is stated in the property's KDoc, which is where a reader looking for the reasoning
will find it.

#### 23.1.9 Baseline after 23.1

Whole JVM host: **1129 tests, 7 failures** — the same seven Phase 22 recorded (3 `SeedingTest`,
2 `:shared-ui`, 2 `GlassSurfacePixelTest` red by design). **24 new tests, no new failures.**
iOS simulator: **1104 tests, same 7 failures**. `verifyArchitectureGraph`, `verifyModuleImports`
and `:androidApp:compileDebugKotlin` all pass, which is the check that the new entity stayed in
`:packages:domain` and the new identifier in `:packages:core`.

### 23.2 Persistence

**All three done.** `:packages:database` **75** JVM tests (66 `commonTest` + 9 `jvmTest`),
cross-checked against `@Test`. Eight new, no new failures.

- [x] **23.2.1 `SprintRecord` + DAO + `version = 4`.** `TaskTrackerDatabase.kt` is at `version = 3`
  with `AutoMigration(1 → 2)` and `(2 → 3)` only, so the new table arrives as
  `AutoMigration(3 → 4)`. Two columns on `work_items`: `story_points` (nullable integer) and
  `sprint_id` (nullable, no foreign key — see 23.3.5).
- [x] **23.2.2 Record ↔ entity mapping.** Both nullable pairs in both directions. Test: a
  stored `null` comes back `null` rather than as 0 or an empty string, for both columns.
- [x] **23.2.3 The migration test.** Open a v3 database that holds real rows at v4 and assert
  the rows survived and the two new columns are `NULL`. **This is the task that catches a
  destructive migration**, and it is the one that must be written before the migration is
  trusted, not after — the failure it prevents is silent and only appears on a device that
  already has the user's data.

#### 23.2.4 Three corrections to the plan, from building it

**The column names are `storyPoints` and `sprintID`, not `story_points` and `sprint_id`.**
Every existing column in this store is camelCase — `projectID`, `statusRaw`, `resolvedAt` —
so the snake_case written above would have been the only two columns in the schema that did
not match the module they sit in.

**23.2.2's test lives here rather than in `:packages:feature-projects-data`.** The
record ↔ domain mapper is in the feature module and grows in 23.3, but the *store* round trip
is answerable here and belongs here: write a `WorkItemRecord` with both columns set, read it
back, assert. Testing it in the feature module would have needed the feature module to
exist first, and this is exactly the assertion that has to hold before the mapper is
written against these columns.

**Both new columns default to `null`, which is why nothing else needed editing.** Every
existing `WorkItemRecord(...)` in `:packages:feature-projects-data` compiles untouched, so
23.2 is additive rather than a 23.3-shaped change made in the wrong order. It also means the
`AutoMigration(3, 4)` brings every existing row across unestimated and in the backlog — which
is what an item created before sprints existed actually was.

#### 23.2.5 Why the migration test does not use Room's helper, and what it does instead

Room's `MigrationTestHelper` is Android only, and **this module's tests cannot run on the
Android host at all** — `build.gradle.kts` disables `testAndroidHostTest` because Room's
Android builder needs a real `Context` that a host JVM test cannot produce. So the helper
would have put the one test that catches a destructive migration behind a platform this
project cannot run Room tests on.

Instead `SprintMigrationTest` builds a genuine version-3 file — all six tables and all four
indices, transcribed from the committed `3.json`, executed through the same bundled SQLite
driver the app uses, `PRAGMA user_version = 3` — seeds a project and two work items, closes
it, and opens it through `TaskTrackerDatabase.open` so that **Room runs its own generated
`AutoMigration(3, 4)`**. Nothing about the migration is stubbed.

Three things about writing it that cost time and are worth keeping:

- **`BundledSQLiteConnection` is not the obvious constructor it looks like.** Its parameter
  is a raw native pointer on the JVM and Android, and only the native variant has a no-arg
  form, so a test written against it compiles on one host and is uncompilable on the other.
  The portable entry is the driver's own `open(path)`.
- **`PRAGMA index_info` cannot be run while a `PRAGMA index_list` statement is still
  stepping.** Both need the same connection, and doing so reuses the connection underneath
  the open statement — which on the bundled driver threw rather than returning wrong rows.
  The list is drained first and the columns read afterwards.
- **A migration test that leaves its file behind stops testing anything**, because the next
  run starts from whatever the last one wrote. `@AfterTest` deletes it, and `deleteStoreFile`
  is `internal` in `PlatformPaths.kt` rather than a public API nobody calls: nothing in the
  app deletes the store, and the `-wal`/`-shm` sidecars have to go with it or the next file
  that reuses the path replays them.

#### 23.2.6 The drift guard, and what each layer actually catches

`SprintSchemaDriftTest` asks the only question that makes a hand-written DDL trustworthy:
**does it describe the version 3 that actually shipped?** It executes the same
`VersionThreeSchema` fixture and compares what SQLite *made* — read back through
`PRAGMA table_info` and `PRAGMA index_list` — against the committed `3.json`. Comparing the
DDL text with itself would find nothing.

The guard is not decoration, but it is **not the primary defence**, and saying so is more
useful than overclaiming. Room validates the whole schema after a migration, so a v3 literal
with a wrong column or table usually fails the *open* first. What the guard adds is the
readable failure and the two cases Room cannot see:

- **an index that v3 had and v4 also has.** A missing index leaves a store that *works* —
  slower, but correct — and there is nothing in the app that would notice. N7 below is that
  case, and it is caught by both layers.
- **a future column added at v5.** The v3 literal without it migrates to v4 and then to v5
  perfectly happily, because neither v4 nor v5 requires it. Only the committed JSON says
  version 3 had it.

It is `jvmTest` for one reason: reading `3.json` off disk. `SprintMigrationTest` stays in
`commonTest` and runs on both hosts that can run Room at all. Adding
`kotlinx-serialization-json` to `:packages:database`'s **jvmTest** classpath is the only new
dependency edge in this phase, and it is there because the artefact the guard has to read is
a JSON file the module itself owns.

#### 23.2.7 Mutation evidence — 8 run, 5 caught, 3 rejected at compile time

Driven by `tools/mutate_23_2.py`.

| # | the deliberate defect | outcome |
| --- | --- | --- |
| N1 | `storyPoints` made `Int = 0` — every pre-sprint item becomes "estimated as zero" | **rejected at compile time** |
| N2 | `sprintID` made `String = ""` — every item arrives in a sprint named `""` | **rejected at compile time** |
| N3 | `releaseFromSprint`'s `WHERE 0` | **rejected at compile time** |
| N3b | `releaseFromSprint` narrowed to one status — compiles, and orphans items | caught by `deletingASprintReturnsItsItemsToTheBacklogRatherThanOrphaningThem` |
| N4 | `assignToSprint` also setting `rank` | caught by `movingAnItemIntoASprintTouchesItsUpdatedAtAndNeverItsRank` |
| N5 | a column dropped from the v3 literal | caught by the migration test **and** `everyTableInTheHandWrittenSchemaMatchesTheCommittedOneColumnForColumn` |
| N6 | a table added to the literal's table list | caught by `everyTableInTheHandWrittenSchemaMatchesTheCommittedOneColumnForColumn` |
| N7 | an index dropped from the v3 literal | caught by `everyIndexInTheHandWrittenSchemaMatchesTheCommittedOne` **and** both migration assertions |

**"Rejected at compile time" is a real outcome and not a gap, but it is not the same evidence
as a red test, so both are reported.** Room's auto-migration processor refuses a `NOT NULL`
column with no default value outright, which moves N1 and N2 from "ships and eats every
existing row" to "does not build" — which is the better outcome and is not one this phase
earned. N3 is rejected because `WHERE 0` leaves the `:sprintID` parameter unused, and that
left the release assertion with only a build failure as evidence; **N3b was added for exactly
that reason**, as a mutation Room will compile so the assertion can be shown red at runtime.

An earlier version of the script reported N1, N2 and N3 as **not caught**, because it read
the *previous* run's XML after a run that produced none. Deleting the results directory
before each mutation is what makes "caught", "rejected" and "nothing ran" three separable
answers; the same class of bug made twelve mutations look uncaught in 23.1.

#### 23.2.8 Baseline after 23.2

Whole JVM host: **1137 tests, 7 failures** — the same seven, unchanged. iOS simulator:
**1109 tests, same 7**. `verifyArchitectureGraph`, `verifyModuleImports` and
`:androidApp:compileDebugKotlin` all pass; the new `SprintRecord` is in `:packages:database`
where `ProjectRecord`'s KDoc says the schema has to live, and nothing outside that module
names it.

### 23.3 Port and data

**176 tests in `:packages:feature-projects-data`, 3 failing** — the pre-existing `SeedingTest`
trio, unchanged. 23 new assertions; the mutation table is in 23.3.7.

- [x] **23.3.1 Port reads.** `sprints(projectID)`, `sprint(id)`, `sprintWorkItems(id)`, on
  `ProjectRepositoryPort`. Reads return domain values, never records, as the interface's own
  KDoc already insists.
- [x] **23.3.2 Port writes.** `createSprint`, `updateSprint`, `finishSprint`, `reopenSprint`,
  `setWorkItemSprint`, `deleteSprint`. `now` is a parameter on every one of them, per the
  interface's stated rule — a port method that reads the clock is a method whose tests can only
  run on a particular day.
- [x] **23.3.3 The implementations**, writing through the same outbox path as every other write
  so sprint changes are queued for sync rather than becoming a side door. While here, confirm
  the outbox payload carries the two new work-item columns: a record that serialises without
  them would silently drop a user's estimate on the next push.
- [x] **23.3.4 `setWorkItemSprint`.** One item, one sprint, one write. Moving an item between
  sprints must **not** touch `rank` — the rank is the item's position in its *status column*
  on the board, and a sprint is not a column, so letting the two interact is how a board ends
  up reordering itself. Test: assigning, reassigning and clearing all leave `rank` alone.
- [x] **23.3.5 At most one **open** sprint per project.** Two concurrent open sprints make
  "the sprint's items" ambiguous for the board and double the "what is in progress" question.
  Enforced in the domain, so the data layer cannot create the state by accident. Test: a second
  `createSprint` on a project with an open one is rejected; it is allowed once the first is
  finished. *(Decide here whether a project may have several **future** sprints — the reading
  is yes, and only the *open* one is exclusive.)*

#### 23.3.6 Five decisions that changed while building it

**The one-open rule moved from the domain to the transaction.** 23.3.5 says "enforced in the
domain", and the first cut put it there. That is wrong for a reason this codebase already
knows: this store is local-first against a server that also merges, so a check made *outside*
the write is a race — two devices each creating a sprint offline both pass it and both store
theirs, and the invariant this phase exists to establish does not hold. It is now inside
`inTransaction`, next to the `openByProject` query, so the count and the insert cannot
interleave. A use-case check is also not enough on its own: anyone holding the port bypasses
it. Refusals are `AppError.Validation` and the message **names the offending sprint**, so the
caller can offer "finish that one first" instead of a dead end.

**`isFinished` is absent from `updateSprint`.** A partial edit that could set it would have to
maintain the date that goes with it *and* queue the outbox row that tells the server, and a
setter cannot do either. Finishing is `finishSprint`, which dates it and refuses a second
call; reopening is `reopenSprint`, which clears the date and refuses when another sprint has
opened since. Both refusals exist because each has an invariant the other does not — see
`reopeningClearsTheDateAndIsRefusedWhileAnotherSprintIsOpen`, which is the one place that can
see both sprints and therefore the only place the rule can be stated.

**`setWorkItemSprint` takes `Patch<SprintID?>`, not a nullable argument.** Same reason as every
other patch in this file: "the caller does not care about sprint membership" and "the caller is
emptying this sprint" are different instructions, and a nullable parameter collapses them into
one where `null` means both. `sendingItemsBackToTheBacklogIsDistinctFromNotMentioningTheirSprint`
is the assertion that would fail if it were collapsed.

**`deleteProject` cascades to sprints; `deleteSprint` releases its items.** Two different
directions, and both were missing. The cascade exists because leaving sprints behind would
show a sprint on a Sprints tab whose project no longer exists. The release exists because a
card pointing at a sprint that does not exist is the state the next pull would find and could
not explain.

**`Sprint` rides the `WorkItems` sync scope.** `SyncScope.Sprints` would be the honest name, but
adding it is a protocol change — a server that scopes its log per entity would need to answer
the new scope — so `InMemorySyncTransport.scopeFor` maps `OutboxEntity.Sprint` to
`SyncScope.WorkItems` and the real transport is untouched. Flagged for 23.6, where the tab
that makes the scope observable arrives.

#### 23.3.7 Mutation evidence — 13 run, 13 caught

Driven by `tools/mutate_23_3.py`.

| # | the deliberate defect | outcome |
| --- | --- | --- |
| M1 | `storyPoints` written as `?: 0` — every unestimated item becomes "estimated as zero" | caught by `anUnestimatedItemStaysUnestimatedThroughTheStore` |
| M2 | `sprintID` dropped on the way to the record | caught by `aWorkItemSurvivesTheRoundTripThroughItsRecord` |
| M3 | `SprintRecord.apply` refusing a closed sprint | caught by `applyingASprintCopiesItsFinishedFlagRatherThanRefusingIt` |
| M4 | `createSprint`'s guard asking the dao for a sprint that can never be open | caught by `aSecondOpenSprintIsRefusedRatherThanSilentlyReplacingTheFirst` |
| M5 | `reopenSprint`'s guard inverted, so reopening beside a live sprint goes through | caught by `reopeningClearsTheDateAndIsRefusedWhileAnotherSprintIsOpen` |
| M6 | `finishSprint`'s double-finish guard disabled | caught by `finishingASprintDatesItAndRefusesToFinishItTwice` |
| M7 | `setWorkItemSprint` also setting `rank = 0` | caught by `movingItemsIntoASprintQueuesEachOneAndLeavesTheirRankAlone` |
| M8 | `setWorkItemSprint` dropping the outbox enqueue | caught by `movingItemsIntoASprintQueuesEachOneAndLeavesTheirRankAlone` |
| M9 | `deleteProject`'s sprint cascade emptied | caught by `deletingAProjectTakesItsSprintsWithItAndQueuesThem` |
| M10 | `deleteSprint` leaving its items pointing at it | caught by `deletingASprintReturnsItsItemsToTheBacklogRatherThanOrphaningThem` |
| M11 | `announceSprints` sending only the `Sprints` half | caught by `aSprintWriteAnnouncesSprintsAndWorkItems` |
| M12 | the sink's pulled-sprint delete not releasing items | caught by `aPulledSprintDeleteReturnsItsItemsToTheBacklog` |
| M13 | the sink's pulled-sprint upsert forcing `isFinished = false` | caught by `aPulledSprintCanArriveClosed` |

**The first run of this table reported M12 and M13 as caught, by the wrong assertion.** They
were caught only by the three pre-existing `SeedingTest` failures, which fail on every run of
this module regardless of what was mutated — so "caught" there means nothing at all, and the
sync sink's sprint path had *no test*. That is the same shape as the 23.1 M6 hole and the 23.2
N3 one: a mutation runner reporting red for a reason unrelated to the defect. Five tests in
`LocalDataSyncSinkTest` were added for exactly this, and the table above is the second run,
where each is caught by its own name. **M4, M5 and M6 were then rewritten**, because their
first forms did not compile — `val open = null` and `if (false)` are rejected by the type
system, and a mutation the compiler refuses is not evidence about a test. Each is now a
*wrong-but-well-typed* defect: M4 asks the dao for a sprint that can never be open, M5 inverts
the guard, M6 disables the branch with `&& false`. The lesson is the 23.2 N3b one and it is
worth stating plainly: **an assertion that has only ever been shown red by a build failure has
not been shown red.**

#### 23.3.8 Baseline after 23.3

Whole JVM host: **1160 tests, 7 failures** — the same seven, unchanged. iOS simulator:
**1132 tests, same 7**. `verifyArchitectureGraph`, `verifyModuleImports` and
`:androidApp:compileDebugKotlin` all pass. Counts are from the JUnit XML, cross-checked against
`@Test`: 5 in `LocalDataSyncSinkTest`, 12 in `RoomSprintRepositoryTest`, 6 in
`RecordCompletenessTest` — 23 new, and both hosts moved by 23 (1137 → 1160, 1109 → 1132).

Two fixtures outside this phase had to change, and both are worth recording because the second
one was a real trap:

- **Five `ProjectRepositoryPort` implementations** needed the nine new members:
  `RoomProjectRepository`, `ViewPreviewData`'s `PreviewRepository`, and the four test fakes.
  The three in-memory fakes implement sprints for real rather than stubbing them — a fake that
  threw would be a landmine for the first test written against it, and one that silently did
  nothing would be worse, because it would pass. `shared-ui`'s fake follows its own file's
  existing convention (`unused("createSprint")`), since the shell has no path that calls these.
- **`TransportTest` picked the wrong example.** Its
  `a change naming an entity this build does not know is a decoding error` used `"sprint"` as
  the name no `OutboxEntity` carries — and 23.2 made `sprint` one. The test went red for a
  reason that had nothing to do with decoding. It now uses `"milestone"`. This is the general
  hazard in an exhaustive-decode test: **the unknown value is a claim about the present, and
  adding to the enum invalidates it.**

### 23.4 Design system

**26 new tests, 14 mutations run and 14 caught.** `:packages:design-system` is 119 tests on
the JVM with the same 2 red-by-design `GlassSurfacePixelTest` failures; the table is in 23.4.7
and the measurements are in 23.4.6.

- [x] **23.4.1 `TTDateField`.** The first date input in the app. A field that opens a day grid,
  built from `ttGlassSurface` and existing spacing/radius/typography tokens — **no new styling
  primitives**, and if the grid needs a colour that does not exist, it is added as a token with
  its measurement rather than inlined at the call site. Ships with the light/dark contrast
  test the other new surfaces get.
- [x] **23.4.2 A story-points presentation.** A numeral in `ttTypography.caption`, deliberately
  not a pill: fifty pills on a board is a wall of badges, and the number is a quantity rather
  than a status. Test: it renders at the caption style and does not take the card's tint.

#### 23.4.3 Three decisions taken before writing, and one that was not free

**`LocalDate`, and `kotlinx-datetime` enters `:design-system`.** A day grid is a calendar, and
a calendar needs calendar arithmetic: which weekday a month starts on, how long the month is,
what the previous month is. `Sprint.startDate` is an `Instant`, and an `Instant` cannot answer
"the 4th" without a time zone to interpret it in — and the answer changes with the device, so a
sprint stored as an instant begins on a different day for a user who flies. The conversion
belongs at the edge in 23.5, where the zone is a decision somebody actually made.

The dependency is a **library**, not a module edge: `verifyArchitectureGraph` filters
`filterIsInstance<ProjectDependency>()`, so `allowedLibraryTable` and `packageOwnersTable` are
unchanged and neither architecture check reads it. It is declared in the module's own build
file for the reason `:packages:domain` declares its own `kotlinx.serialization` — a KMP
`implementation` dependency is not `api`.

**Field, popup and grid are one component.** 23.4.1 asks for "a field that opens a day grid",
and splitting the grid out as the real component leaves the popup wiring bespoke and re-decided
by every caller. 23.5 gets one call site per sprint date.

**`TTStoryPoints(points: Int?)` owns the unestimated reading.** `null` means nobody has said;
`0` means somebody said zero, which is a real answer — a one-line chore worth nothing is worth
nothing. Rendering the absent case as "0" would throw that decision away *and* sum wrong. A card
and a backlog row disagreeing about what a dash means is the failure the component prevents.

**The one that cost something: the weekday header is `ttBodyLabel`, not `ttLabelSecondary`.**
`ttLabelSecondary` measures **3.30:1** on this popup's base in light appearance, and the header
is `captionSmall`, so it needs the 4.5:1 that text below 18sp gets. The obvious choice — the
secondary token, because the header is a label rather than content — would have shipped a
failing number on the one row that says what each column *is*. `ttBodyLabel` is documented for
the opaque `ttSurface()` the popup's base is built from, so it is the token that matches the
surface rather than the one that matches the role. Measured, both appearances, in 23.4.6.

#### 23.4.4 The calendar arithmetic is pure, and that is what made it testable

`ttMonthGrid`, `ttShiftMonth`, `ttWeekdayOrder` are functions over `LocalDate` with no frame in
them. Every interesting thing about a date grid is a question about which day lands in which
cell, and reading that off a rendered frame means comparing a screenshot to a picture of a
calendar — which is how a grid that is off by one cell survives review, because "the 1st is
under a T" is not a sentence anybody says out loud.

Four things this bought, each of which is a test that could not otherwise exist:

- **`ttMonthGrid` is always 6x7.** 42 cells, padded with `null`. A month that needs five rows
  still gets six. The alternative makes the popup **change height** between months, so stepping
  from a 28-day February to a 31-day March moves the control under the user's finger.
- **`null` means "not this month", not "greyed out".** Those cells are exactly how a user picks
  the 1st of March while looking at February, and grey reads as *disabled*, which is a different
  promise from "belongs to another month".
- **The grid is always Monday-first *relative to a parameter*.** Both `MONDAY` and `SUNDAY` are
  exercised by every test, and the header row and the cells below it come from one
  `firstDayOfWeek`, so they cannot disagree about which way round the week runs.
- **Month length is asked of the calendar, never counted.** kotlinx-datetime 0.8.0 has no
  `daysInMonth`, so it is "the day before the first of the next month". A hard-coded
  `31, 28, 31, 30…` array is a leap-year bug waiting for February — the one month where the two
  answers differ. The tests pin 2024 (29), 2026 (28), 2000 (29, the 400 rule) and **1900 (28,
  the century rule a naive rule gets wrong)**.

#### 23.4.5 The three measurement bugs this phase found, and what each one was

All three produced a **green or plausible-looking number that was measuring the wrong thing**,
which is the failure mode `AGENTS.md` rule 2 exists to prevent. They are recorded because the
first two would have shipped had the assertions been looser.

**A `LocalDensity` override silently halves every node's reported bounds.** `getBoundsInRoot()`
reports each node in *that node's own* density. Overriding `LocalDensity` to 2 leaves the root
reporting density-1 coordinates and the card and the text reporting half of theirs — and the
scale derived from the root then maps every sample onto the wrong half of the frame. The scan
came back reading the card's own fill gradient as if it were the numeral's ink, and reported
**1.56:1** for black-on-white text. `GlassSurfacePixelTest` overrides density legitimately, to
measure a 0.5dp rim that is genuinely not drawn at density 1; copying that idiom here was the
error. A `caption` numeral at density 1 is 14 device pixels tall and does have fully-covered
interior pixels, so the override bought nothing.

**Modifier order decided what "the card" was.** `padding` *before* `ttGlassCard` puts the
padding outside the glass, so the card shrink-wraps to the numeral, and a background sample
taken 4dp from the card's top-left lands in the padding rather than on the fill. The card then
measured the canvas and the ink measured the canvas. Glass first, padding inside.

**Ink on a filled cell needs the *cell* as its background.** With the popup's fill as the
reference, the strongest pixel in a selected day's bounds is the **accent disc**, not the white
numeral — white `#ffffff` is nearly this popup's `#f4f4f9` while the disc is not. So the
selected-day assertion measured accent-against-popup and reported 7.19:1, which is about the
right number for entirely the wrong pair.

Two further measurement notes, both now in the code: the fill is sampled from the surface's
**horizontal centre**, because a sample 4dp in from a corner lands outside a 16dp rounded rect
and reads the page behind it — invisible in light, where the glass composites to the same white,
and a 0% ink coverage in dark. And the pill test measures **ink coverage of the component's own
box**: a numeral covers 26% and a capsule covers very nearly 100%, so the threshold sits between
them by a wide margin and depends on neither the font, the density nor the rasteriser. There is
no `Background` semantics property in Compose Multiplatform 1.12.1 to assert against, so this
had to come out of the frame.

#### 23.4.6 What the components measure, on both rasterisers

`DateFieldContrastTest` and `StoryPointsTest` print every figure. Measured on the JVM, on the
popup's own lens-over-`ttSurface()` frame (`#f4f4f9` light, `#2c2c2e` dark) and on a real glass
card:

| | light | dark |
| --- | --- | --- |
| an unselected day (`ttLabel`) on the popup | `#000000` on `#f4f4f9` — **19.15:1** | `#ffffff` on `#2c2c2e` — **13.94:1** |
| the weekday header (`ttBodyLabel`) on the popup | `#1c1c1e` on `#f4f4f9` — **15.52:1** | `#f2f2f7` on `#2c2c2e` — **12.49:1** |
| the selected day (`ttOnAccent`) on the accent disc | `#ffffff` on `#39577d` — **7.41:1** | `#ffffff` on `#636c76` — **5.33:1** |
| an estimate (`ttLabel`) on a glass card | `#000000` on `#c2ccd7` — **12.91:1** | `#ffffff` on `#1c1f22` — **16.56:1** |
| the accent disc against its surround | **0.73** apart | **0.33** apart |
| the estimate's glyphs' share of its own box | **26%** | **31%** |

Every one of these is the **composite the app ships**, not a token on a token: a day numeral on
a translucent lens over an opaque surface is a value composited under another translucent layer,
which `AGENTS.md`'s scope table sends to pixels unconditionally. The last two rows are not
contrast at all and are the ones a ratio cannot produce: a disc painted the same colour as its
surround satisfies every ratio above and is still a calendar with no visible selection, and a
pill is still a pill at 21:1.

**Two of the four contrast figures are also what makes 23.4.2's "no tint" claim structural.**
`TTStoryPoints` has no `tint` parameter, so no call site *can* pass one — but a parameter can
be added, and a component can pick up a tint without one being passed. So the estimate is
rendered twice over the *same* card, once plain and once tinted hard with the accent, and the
assertion is that **the ink does not move**: `#000000` both times in light, `#ffffff` both times
in dark, and 0.48 and 0.63 away from the accent itself.

#### 23.4.7 Mutation evidence — 14 run, 14 caught

Driven by `tools/mutate_23_4.py`, which excludes the two red-by-design `GlassSurfacePixelTest`
tests **by name** — they are red on every run, so counting them would make every mutation look
caught and the table meaningless. That is the same trap 23.1 M6 and 23.3 M12 fell into.

| # | the deliberate defect | outcome |
| --- | --- | --- |
| D1 | the grid drops its last week | caught by `a month grid is six rows of seven and never anything else` |
| D2 | the leading-blank count off by one | caught by `the leading blanks are exactly the days before the first of the month` |
| D3 | February a day short, so a leap 29th falls off the grid | caught by `February is 29 days in a leap year and 28 in a common one` |
| D4 | stepping a month steps 30 *days* | caught by `stepping a month lands on the first and crosses a year boundary` |
| D5 | the weekday header ignoring the grid's week start | caught by `every day sits in the column whose header names its own weekday` |
| D6 | the header truncated to one character — two `W`s | caught by `no two weekday columns read the same` |
| D7 | a day cell sized to its numeral, ~20dp | caught by `a day cell is at least the platform minimum tap target` |
| D8 | an unselected day on the secondary ink | caught by `an unselected day clears the text bar on the popup's own fill` |
| D9 | a selected day in `ttLabel` rather than `ttOnAccent` | caught by `the selected day's ink is the same in both appearances` — **see below** |
| D10 | the weekday header back on `ttLabelSecondary` | caught by `the weekday header clears the text bar on the popup's own fill` |
| D11 | an unestimated card drawn at the estimate's size | caught by `an unestimated card reads a step below an estimated one` |
| D12 | proportional digits, so 9 → 10 reflows the row | caught by `an estimate uses monospaced digits` |
| D13 | an estimate painted in the card's accent | caught by `an estimate is never painted in the accent over a tinted card` |
| D14 | the pill 23.4.2 rejected | caught by `the estimate is text over nothing rather than text inside a pill` |

**D9 is the one worth reading.** It was **not caught** on the first run, and the reason is the
most interesting result in this phase. Replacing `ttOnAccent` with `ttLabel` on the selected day
left *every* contrast assertion green: black on the light accent is **7.5:1** and white on the
dark accent **5.33:1**, both clear of 4.5:1. The defect was real — the token whose entire job
is to name the one fill in this app that carries text had been dropped — and **no ratio could see
it**. What it actually breaks is that `ttLabel` *follows the appearance* (black in light, white
in dark) while `ttOnAccent` does not, so the pairing is right by luck rather than by
construction, and it stops being legible the moment the dark accent is lightened. So
`the selected day's ink is the same in both appearances` was added, and it is that assertion —
not any contrast figure — which now holds the token in place. **A measurement passing and the
pixels still being wrong is a real state, and it is invisible unless somebody looks for it.**

D14 also took three attempts, and the first two **did not compile**: one wrapped the numeral in
a `Box` without closing the brace, the next passed a second `modifier =` to a function that
already takes one. Neither is evidence about anything — 23.2's N3 and 23.3's M4 are the same
lesson, and it is now four phases running. The third edits the modifier chain that is already
there, which is also how the pill would actually be added.

#### 23.4.8 A build constraint that only the iOS host found

Three test names carried a comma, and the JVM compiled them without complaint:

```
e: Name contains illegal characters: ",".
> Task :packages:design-system:compileTestKotlinIosSimulatorArm64 FAILED
```

Kotlin/Native mangles test function names into exported symbols, and a backticked name may not
carry a comma there. The JVM is permissive, so `jvmTest` alone would have been green and the
iOS build broken. This is the counterpart to the rule already in this file about iOS test entry
points, and it is now recorded here because it is the same species of problem: **a test name is
source, and the Native target's rules for it are stricter than the JVM's.**

#### 23.4.9 The contrast maths, folded in — and what 23.4.9 got wrong when it said "follow up"

The note originally written here said the WCAG relative-luminance formula existed **four** times,
and left the folding in as a follow-up. Both halves were wrong. Counting it properly found
**nine** files carrying it, and the suite was not one implementation copied nine times — it was
**two implementations disagreeing**:

| file | sRGB breakpoint |
| --- | --- |
| `AccentContrastTest`, `AvatarContrastTest`, `BannerContrastTest`, `BodyLabelContrastTest`, `GlassSurfacePixelTest`, `PinnedAppearanceTest` | **`0.03928`** |
| `GlassFillContrastTest`, `TabLensContrastTest` | **`0.04045`** |

So a token sitting in the narrow band between the two breakpoints would have measured
differently depending on which suite asked. Both values are in circulation in accessibility
references, which is exactly how that happens. `0.03928` is kept, on the majority's side, and
changing it moved **no** test — which is recorded as the reason the choice is not load-bearing
today, not as a reason it does not matter.

They also disagreed on `over`'s **parameter order**, in three shapes:
`over(foreground, alpha, background)` in three files, `over(bottom, top, alpha)` in
`GlassFillContrastTest`, and a two-argument `over(top, bottom)` that read the alpha off `top` in
`PinnedAppearanceTest`. One signature now: `over(top, alpha, bottom)`, plus a two-argument
overload for the "the top carries its own alpha" case, which is a genuinely different operation
rather than a different spelling.

**And there was a ninth `Color.hex()`** — a *file-private top-level* extension in
`GlassIntensityRenderTest`, which the survey missed because it carries no `relativeLuminance`.
A file-private extension and an `internal` one with the same receiver and arguments are a
redeclaration conflict, not a preference, so the compiler found it.

The refactor is a **pure deletion**: the shared helpers are top-level internal functions with the
names the call sites already used, so no call site moved. The diff is nine deleted helper blocks
and one rewritten file, and there is no opportunity to change an argument at the same time as
the implementation underneath it.

**The conversion went wrong exactly where the old signatures were ambiguous, and the test that
already existed caught it.** `GlassFillContrastTest`'s two call sites were rewritten as
`over(variant.color, LENS_ALPHA, Color.White)` — which reads correctly against the *old*
spelling and puts the lens where the base belongs, so the composite came out `#eff1f4` and the
lens measured **1.13:1**. That is the argument-order hazard made real rather than theoretical,
and it is the strongest argument for unifying the signature instead of overloading it.

Two process notes, because both cost time and both are the same lesson as the 23.2 N3 and 23.3
M4 ones:

- **The first attempt at this was a scripted brace-matching deletion, and it ate declarations.**
  `AccentContrastTest` declares a `data class Variant` *between* two of its helper functions, so
  removing `linearise` also removed the class and four `CANVAS_*` constants. The build caught it
  immediately and `git checkout` restored all eight files, which were clean and tracked —
  but `AGENTS.md` warns about exactly this ("do not use broad scripted search-and-replace in
  this tree") for a reason that is now measured rather than assumed. The rest was done by hand.
- **`PinnedAppearanceTest`'s pair was `Float`, not `Double`,** and became `Double` with it. Four
  call sites moved from `4.5f` to `4.5`. Nothing else changed, and the thresholds sit far enough
  from the measured values that `Float` precision was never load-bearing.

Baseline is unchanged: **1186 JVM / 1158 iOS, the same 7 failures**, and `:packages:design-system`
is **120 tests with the same 2 red-by-design `GlassSurfacePixelTest` failures**.

### 23.5 Presentation

The first half of 23.5: the sprint list, the editor, the sprint's own screen, and its summary.
**23.5.1–23.5.4 landed; 23.5.5–23.5.7 are still open** and are listed as such below.

- [x] **23.5.1 `SprintListModel` + `SprintListScreen`.** Each row: name, date range, progress
  as `achieved / goal`, and an open/finished badge. Model is `remember`-keyed and observed with
  `DisposableEffect` like every other model in `NavigationHost` — that pair is what stops a
  second live observer being left watching the store.
- [x] **23.5.2 `SprintEditorSheet`.** Create and edit in one sheet, mirroring
  `WorkItemEditorSheet`'s nullable-id shape: name, goal points, start and end through
  `TTDateField`, owner. Validates end ≥ start before it will save.
- [x] **23.5.3 `SprintDetailModel` + `SprintDetailScreen`.** Goal, dates, owner, the committed
  and achieved totals, the sprint's items with their points, and a **Finish** button enabled
  only for the owner.
- [x] **23.5.4 `SprintSummaryScreen`.** Rendered from `SprintSummary`, so there is no arithmetic
  in the view: done items, incomplete items, achieved points, and whether the goal was hit. The
  hit/miss is **read from the value**, never recomputed here.
- [ ] **23.5.5 Story points in the work item editor**, plus `WorkItemEditorModel` carrying the
  field through to `SaveWorkItem`. That use case takes every field **explicitly and by name**
  (`title`, `details`, `status`, `priority`, `assignee`, `dueDate`, `now`) and forwards them to
  `repository.createWorkItem` / `updateWorkItem`, so a new field that is not added to its
  signature and to both call sites is a field that is silently dropped on save — and the editor
  will look like it worked. Test: a value entered in the editor comes back out of the store.
- [ ] **23.5.6 Assigning an item to a sprint**, from the editor and from the sprint screen. One
  place, two callers.
- [ ] **23.5.7 Story points on the board card and the backlog row**, through 23.4.2.

#### 23.5.1–23.5.4 What landed, and the four decisions that were not free

**Sprint use cases went into the domain, not into the models.** 23.3 gave the port nine sprint
writes and nothing above it had a caller, so the presentation models were the only thing that
could reach them. That is the shape every other write in this feature was moved *away* from, and
the deciding question was where a decision a view makes belongs: `SprintUseCases.kt` holds
`CreateSprint`, `UpdateSprint`, `FinishSprint`, `ReopenSprint`, `DeleteSprint`, following
`ProjectUseCases`. The validation split is deliberate and is **not** "the same checks twice" —
the use case answers "is this edit well-formed" and the store answers "may this project hold it",
which only the transaction can know. The one-open rule stays in `RoomProjectRepository` where 23.3
put it.

**`UpdateSprint` reads the sprint before it patches it, and that is the whole reason it is more
than a forward.** `end < start` is a relation between *two* fields, and a partial edit may name
only one of them. Refusing only when both arguments are present accepts exactly the edit easiest to
make by accident — moving the start past an end the caller never mentioned. The first version of
this was the argument-only check and mutation **N2** killed it; the test that does it is
`an untouched start is not dragged along by a moved end`. The read costs one call and is still
cheaper than a write that fails inside a transaction after the outbox has been touched.

**The `Instant ↔ LocalDate` conversion is one pair of functions at the model edge, and the
rounding direction is a decision that was argued rather than defaulted.** 23.4 deferred the
conversion here precisely because "which day is this instant" is a real answer that depends on
the zone. Both directions round **toward the start of the day**, so a sprint stored at 09:00 on
the 4th reads back as the 4th, and a user who picks 18 October is never shown the 19th. Rounding
the end *up* to the end of its day is the other defensible choice and it is the wrong one for a
form. The consequence is stated rather than hidden: `Sprint.isActiveOn` compares instants, so the
final day belongs to the window for display and to the day-after for `isActiveOn`. That asymmetry
is in the entity and is not this function's to fix.

Every other date test in 23.5 uses UTC, so a suite of them would never notice a conversion that
ignored the zone it was handed — which is why `a row's dates are read in the model's zone and not
in UTC's` uses a fixed UTC+11 offset and the same instant that is the 16th in UTC. That test is
what makes mutation **P6** (`TimeZone.UTC` hardcoded inside `sprintInstant`) caught rather than
green.

**`ttDateLabel` became public, and `ttDateRange` was added next to it.** A sprint's window is
rendered *outside* the field that picked it, so a list row saying "4 Oct – 18 Oct 2026" has to
format a `LocalDate` and there was no way to do it that reused this file's month-name lookup.
Formatting it again at the call site is how a date grid and the row above it end up disagreeing
about what a month is called. The range prints the year **once** when both ends share one:
"4 Oct 2026 – 18 Oct 2026" is two facts where the reader wanted one, and the repetition makes a
single-year sprint look as though it spans a year boundary — which is precisely what a reader of
a sprint window is checking for. The rule is same-*year*, not same-*month*, because a window
crossing months is common while one crossing years is not.

#### 23.5.4 Why the verdict is a function and not a `when` inside the screen

23.5.4's promise is that the hit/miss is **read from `SprintSummary.didHitGoal`** and never
recomputed in the view. A `when` written inline in a composable states that only a rendered frame
could check, and a rendered frame can check the *wording* but not the *source* of the verdict.
`sprintVerdict(didHitGoal: Boolean?)` is therefore a pure function over the flag alone — no
`achievedStoryPoints`, no `goalStoryPoints` — and `SprintVerdictTest` asserts that for all 169
goal/achieved pairs the flag and the comparison a screen would have made **never disagree**. If a
summary could carry numbers and a flag that disagree, there would be something to recompute, and a
view recomputing from the numbers would be right some of the time and wrong the rest with no test
noticing. Mutations **P15** (the `null` state collapsed into `Miss`) and **P16** (the `true` state
collapsed into `Miss`, which is the `>` that gets "exactly at the goal" wrong) are both caught.

#### 23.5.7 deferred, and `ProjectBoardScreen.kt` untouched

`ProjectBoardScreen.kt` and `ProjectBoardModel.kt` belong to the concurrent workstream, so the
board-card half of 23.5.7 is deferred rather than contended. Nothing in 23.5.1–23.5.4 needed
either file: `SprintListModel` filters by the `sprintID` the store hands it rather than reaching
into the board's own grouping, which is what made that possible.

**Preview fixtures only.** 23.8 forbids extending the store's first-launch sample data while
`SeedingTest` has three documented failures, so `ViewPreviewData.sprints` is preview-only and
`PreviewRepository` still defaults to no sprints — the board previews assert on `workItems` having
nothing in a sprint, and a fixture that moved a card into one would take the board's empty column
with it.

#### 23.5.5 Verification

**24 mutations, 24 caught** (`tools/mutate_23_5.py`), and **three of the twenty-four were wrong
mutations on the first pass** — which is the number worth recording, because each was a way of
reporting a green that meant nothing:

- **N4** was originally "drop the `normaliseName` call", and recorded NOT CAUGHT. The fake's
  `createSprint` normalises the name too, exactly as `RoomProjectRepository` does — so deleting
  the use case's copy of a check the layer below already performs is invisible through this port.
  That is the fake doing its job, and it means "delete the redundant call" is not a mutation when
  something below you already does the same thing. It was replaced with a name-shouting defect,
  which is caught.
- **P9** was originally `isCreating || false || (…)`, and `false || (x)` is `x`. A runner that
  cannot tell an equivalent expression from a real defect will happily report its own arithmetic
  mistake as a hole in the tests. The second attempt (`get() = isCreating`) was REJECTED AT COMPILE
  TIME because it orphaned the four `original*` vals this build treats as errors. The third
  short-circuits the condition, which compiles and is a real defect.
- **P11** originally compared the draft's dates as **instants**, on the theory that the stored
  instant might not be at midnight and the `LocalDate` comparison might be hiding something. It
  recorded NOT CAUGHT because `sprintInstant` round-trips exactly, so the two comparisons agree
  for every input: an equivalent mutation, not a defect. The `LocalDate` choice is still the right
  one and is still asserted directly in `an unchanged edit is not a change` — but it cannot be
  mutated into failing, because it makes no observable difference. That is now said in the runner
  rather than left as a surprise.
- **P6** was first written as `date.atTime(12, 0).toInstant(timeZone)` and was REJECTED AT COMPILE
  TIME — those are `expect`-declared extensions, so the call needs imports the file does not have.
  A mutation that does not compile proves nothing, which is now a third reported outcome rather
  than a silent "nothing ran". This is the fifth such case across the project (23.2 N3, 23.3 M4,
  23.4 D14 ×2, 23.5 P6/P9).

**68 new tests**: `SprintUseCasesTest` 17, `SprintDetailModelTest` 15, `SprintListModelTest` 14,
`SprintEditorModelTest` 13, `SprintVerdictTest` 5, plus 4 added to `DateFieldTest`. Measured from
the JUnit XML and cross-checked against `@Test`, never from `grep -c`.

Baseline: **1254 JVM / 1226 iOS, the same 7 failures** — the 3 `SeedingTest` in
`:packages:feature-projects-data`, the 2 red-by-design `GlassSurfacePixelTest` in
`:packages:design-system`, and the 2 `:shared-ui` ones. Up from 1186 / 1158, which is `+68` on each
host and matches the new-test count exactly.

### 23.6 The entry point

This is the part that makes any of it reachable, and it is listed on its own because a feature
with no entry point is a feature nobody can find.

- [x] **23.6.1 `AppTab.Sprints`.** The enum entry, `wireValue = "sprints"`, and
  **`isProjectScoped = true`** — the switch over `AppTab` in `Routes.kt` is exhaustive, so
  leaving it out is a compile error rather than a silently unscoped tab.
- [x] **23.6.2 `tabTitle` and `tabIcon`.** Both are exhaustive `when`s in `NavigationHost.kt`;
  add `Res.string.tab_sprints` and `ttTabIcons.sprints`. The mapping back to the native app's
  SF Symbols is recorded in `ttTabIcons.mapping`, and this one has no counterpart there — say so
  in that table rather than leaving a blank.
- [x] **23.6.3 `AppRoute.SprintList` / `AppRoute.SprintDetail`**, and both route accessors:
  `projectIDOrNull` returns the project, and `selectsActiveProject` returns it too, because a
  sprint tab that did not become the active project would show nothing.
- [x] **23.6.4 `SprintsDestination` in `NavigationHost`**, plus the `TabRoot` branch, the
  project picker and the empty state — mirroring `ProjectScopedTabScreen`, including
  `seedSampleDataIfNeeded`, so landing on the tab first does not show an empty store.
- [x] **23.6.5 Deep links.** `tasktracker://sprints/<pid>` and `tasktracker://sprint/<pid>/<sid>`
  in the link grammar, so the new tab does not become the one destination a shared link cannot
  reach.
- [x] **23.6.6 Check five tabs at phone width.** `TTGlassTabBar` has laid out four. A fifth
  segment is the first thing in this app to have been looked at at exactly one size, and the
  Android host's viewport is already known to be smaller than the other two — so the bar is
  checked in a preview and the tab-width assertion is written against a **fixed** expectation
  rather than whatever the host happens to produce.

#### 23.6.6 The measurement, and why the width is fixed

`TTGlassTabBar` has never laid out a fifth segment, and 23.6.6 is the first look at one. The
numbers below are **JVM, 390dp, density 1**, and they are frozen into the test's KDoc and its
failure messages rather than recomputed per host:

```
bar            x 16.0..374.0 dp, 358px   (= 390 − 2 × ttSpacing.tabBarEdgeInset 16)
segments       71, 71, 70, 70, 70 px     (= (358 − 2 × hairlineInset 3) / 5 = 70.4)
captions       projects 47, board 34, backlog 46, sprints 41, settings 47 px
caption boxes  71, 71, 70, 70, 70 px
```

**Five tabs fit, with headroom**: the narrowest segment is 70px against the 44px minimum hit
target both platforms' accessibility guidance names, read from `ttSpacing.iconButtonSize` so
the assertion tracks the token. The 71/71/70/70/70 spread is the integer division of 70.4
across five segments, and the test allows a 1px spread rather than demanding five equal
numbers the layout cannot produce.

Three assertions, and each is **structural** — none of them reads a pixel, because nothing
here survives being read off the modifier chain:

- the tab count is the **literal 5**, not `AppTab.entries.size`, so a sixth tab is a design
  decision that has to change the number on purpose;
- the segment spread is at most 1px;
- each caption's drawn width is at most its own box.

The third is the one that carries the finding. The caption is `maxLines = 1` with **no**
`overflow`, so a label too wide for its segment is **clipped, not ellipsised** — a defect that
reads as a slightly wrong word rather than as an error, and which no assertion about
`hasText` would ever catch, because the text is still in the semantics tree. `sprints` at 41px
in a 70px box has 29px to spare, and the two labels that were already tightest (`board` 34px,
`projects`/`settings` 47px) are unchanged by this phase.

**The shell is constrained, not the window resized.** `setUpShell` takes a `width` and lays
the app inside a `Box` of it. The alternative — a density override — was rejected on purpose:
it leaves the root at the host's density and everything below it at the forced one, so
`getBoundsInRoot()` reports **two coordinate systems on one frame**, and a test written
against it measures nothing. Relatedly, `setContent` can only be called once per test, so a
scenario needing two widths has to be two tests rather than one test with two layouts; the
width became a parameter of the setup helper instead.

#### 23.6.7 One definition for a sprint, because two of them disagreed

The first run of `aSprintOpensAndALinkLandsOnIt` failed with

```
expected:<[SprintList(...), SprintDetail(...)]> but was:<[SprintDetail(...)]>
```

and the cause was not the test. **The deep link and the row tap each had their own idea of
the stack a sprint is opened on.** The link installed `[SprintList, SprintDetail]`; the tap
called `push(SprintDetail)` onto whatever the tab had, which after a plain tab switch is
nothing, so it installed `[SprintDetail]`.

What makes this worth writing down is that **the two render identically**. `NavigationHost`
draws `TabRoot` only when the stack is empty (`if (pushed == null || editorOverProjectRoot)`),
and `SprintDetailScreen` is opaque, so a stack with the list beneath it and a stack without
produce the same pixels. A screenshot could never have found this, and neither could any
assertion about what is on screen. It was found only because the test asserted the **router's
state**.

The fix is one definition used by both entry points — `sprintListStack` for the root and
`sprintStack` for the open sprint, both in `Router.kt`, called by `Router.openProject`,
`Router.openSprint` and `DeepLinkParser` — plus one atomic transition, `Router.openSprint`.
Not two `push`es: `RouterState` publishes through a single `StateFlow` precisely so a
transition moving several fields at once is never observable half-applied, and two pushes
would publish a state in which the list is a *pushed* route for one frame.

**The KDoc written for this was wrong and was corrected.** It claimed "a detail with no list
beneath it is a screen with no back". There is no `BackHandler` anywhere in `NavigationHost`
and `SprintDetailScreen` has no back button — the tab bar is how every project-scoped screen
in this app is left, board and backlog included. The real reason is state consistency, and the
comment now says that.

The alternative shapes were weighed rather than skipped. Two `push`es at the call site is a
smaller diff and leaves the link and the tap as two statements that can drift again. Mirroring
the board's `editorOverProjectRoot` builds and discards a sprint-list model behind an opaque
screen and adds a second tab-specific carve-out to the shell's render condition. Making the
root *implied* — no `SprintList` in any stack, back via `popToRoot` — matches only the board
tab's comment and contradicts what `board/<pid>` and `backlog/<pid>` both do, since each
installs its root route.

#### 23.6.8 18 tests, and the one that was not measuring anything

**18 new tests**, all green on all three hosts:

| Module | Count | What |
| --- | --- | --- |
| `:packages:routing` | 11 | the tab and its `wireValue`, `isProjectScoped`, both link forms, both refusal cases, `openProject`, `openSprint`, and the tap/link agreement |
| `:shared-ui` | 5 | two sprint scenarios, five-tabs-at-390dp, plus 2 in `TabPresentationTest` |
| `:packages:design-system` | 2 | the glyph's rising bars and its non-frame shape |

**13 mutations, 13 caught** (`tools/mutate_23_6.py`, 0 compile-rejected). **Two were wrong on
the first pass**, which is the number that matters:

- **U1 went green.** Handing the Sprints tab the **Projects glyph** failed nothing: the design
  system asserts its own drawings and never asks which tab a glyph belongs to, and the shell
  scenarios assert labels and roles but not drawings. Two 22dp glyphs differing by one bar are
  the same blob at review size, so no screenshot test would have caught it either. The fix was
  a new test, not a new assertion: `tabIcon` is now `internal` rather than `private`, and
  `TabPresentationTest` asserts the two properties that pin the mapping **without restating
  it** — that no two tabs share a glyph, and that the set handed out is `ttTabIcons.all`. A
  table of `AppTab.Sprints to ttTabIcons.sprints` in a test would be a second copy of the
  thing being checked and would keep passing after the production mapping changed.
- **S6 was rejected at compile time** on the first attempt: `parseSprintID` returns
  `SprintID?`, so reading the project id out of the wrong slot produced a nullable where a
  non-null was required. Rejecting the defect proves nothing, and the runner reports that
  outcome separately for exactly this reason — the sixth such case in the project after 23.2
  N3, 23.3 M4, 23.4 D14 ×2 and 23.5 P6/P9.

S1–S3 are the three ways the sprint stack can be wrong, and they are the reason the runner
leads with them. **Only S1 was caught before the runner existed, and it was caught by a Compose
test rather than by the module that owns the stack.**

One honest limit: **`openProject` on the Sprints tab is covered only by a routing test.** The
Compose scenario reaches Sprints by tab tap, not by `openProject`, so mutation S3 is caught in
`:packages:routing` and nowhere else. That is the right module for it — the shell has no way
to reach that branch — but it means the assertion is a pure unit test with no rendered
counterpart.

#### 23.6.9 Baseline

`./gradlew jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports :androidApp:compileDebugKotlin --continue`

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1254 | **1272** | 7, unchanged |
| iOS simulator | 1226 | **1244** | 7, unchanged |

+18 on both hosts, from the JUnit XML the build writes, cross-checked against `@Test`:
`RoutingTest.kt` 63 `@Test` / 63 results, `TabIconsGeometryTest.kt` 9 / 9, and shared-ui's 33
as 27 (`TaskTrackerAppJvmTest`) + 2 (`ProjectPickerSheetRenderTest`) + 2 (`TabBarScreenshotTest`)
+ 2 (`TabPresentationTest`).

The 7 failures are the documented ones, identical by name on both hosts: 3 × `SeedingTest`
in `:packages:feature-projects-data`, 2 × `GlassSurfacePixelTest` in `:packages:design-system`
(red by design, Phase 22), and 2 × `:shared-ui` (`aChosenThemeOutlivesTheProcess`,
`theBoardReturnsToTheList`).

**Android host** (`:shared-ui`, documented-red): 31 tests, the same 7 failures as before this
phase — 2 × `ProjectPickerSheetRenderTest` (`Build.FINGERPRINT` is null under Robolectric) and
5 × `TaskTrackerAppAndroidHostTest` in the viewport/layout class ("Failed to inject touch
input… could not find node", "is not displayed"). **All five sprint and tab tests pass there**,
including the three new scenarios; the sprint reads in `ProjectFakes.kt` were made real rather
than left stubbed, which is what lets them.

Two counts in the tree were stale after the fifth glyph and were corrected in this change: the
`TabIcons` class docs ("four glyphs", "four names") and the test name
`every glyph fills the grid so the four read as one set`. Two comments in `NavigationHost` said
a **fifth** tab would be a compile error; with five tabs present they now say **sixth**.

#### 23.6.10 What 23.6.6 did not change

`TTGlassTabBar`'s geometry is **untouched** — no segment count, no inset, no hairline. 23.8
said not to, and the measurement is the reason that was the right instruction: five segments
fit at 390dp with 26px of headroom over the 44px minimum, so there was no pressure to change
anything. `setUpShell`'s `width` parameter constrains the **test**, not the bar.

`ProjectBoardScreen.kt` and `ProjectBoardModel.kt` belong to the concurrent workstream and were
not touched. No sprint rows were added to the sample-data fixture (23.8), so `SeedingTest`'s
three documented failures are exactly as readable as they were.

### 23.7 Verification

- [x] **23.7.1 Mutation-check every new test** before it is believed, per `AGENTS.md` rule 1 —
  including the pure ones, which are the ones most likely to pass for the wrong reason.
  23.6's run is `tools/mutate_23_6.py`: **13 mutations, 13 caught, 0 compile-rejected**, with
  two wrong on the first pass (U1 green, S6 compile-rejected) — see §23.6.8.
- [x] **23.7.2 The deciding command**, unchanged:

  ```bash
  ./gradlew jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports :androidApp:compileDebugKotlin
  ```

  `verifyArchitectureGraph` and `verifyModuleImports` are the check that the new entity stayed
  in `:domain`, the new port stayed in `feature-projects-domain`, and no record leaked into
  presentation. If a sprint needs a new dependency edge, `allowedLibraryTable` and
  `packageOwnersTable` are updated **deliberately**, in the same change, with the reason.
  23.6 added one library edge — `shared-ui` → `kotlinx-datetime` — which is a library and so
  leaves both tables untouched. All four tasks passed; see §23.6.9.
- [x] **23.7.3 Baseline from the JUnit XML**, cross-checked against `@Test`, in the same change
  as each test — never from `grep -c`. After 23.6: **JVM 1272 / iOS 1244, 7 failures each**,
  up +18 from 1254 / 1226. See §23.6.9 for the per-module cross-check.

### 23.8 Two things not to do while this is in flight

- **Do not extend the sample-data fixture with sprints.** `SeedingTest` has three documented
  pre-existing failures on both hosts; adding rows to the fixture they assert against makes the
  baseline worse and the existing failures harder to read. Sprints ship with no sample data
  until those are resolved.
- **Do not touch the tab bar's geometry.** Five tabs is a layout fact, not an invitation to
  redesign the capsule. If it does not fit at phone width, 23.6.6 is where that is discovered
  and answered, on its own.

### 23.9 Build order

`23.1 → 23.2 → 23.3` is a strictly layered chain: no presentation compiles without the port,
and no port without the entity. `23.4.1` is independent and can be built in parallel, because
nothing else blocks on it and it is the one piece with no precedent in this codebase.
`23.5` follows `23.3`. `23.6.6` is last because five tabs cannot be measured until there are
five.

### 23.10 The date field could not be used to choose a year

Found by using the app rather than by reading it, which is the argument for doing that.

#### Three defects, one cause

A sprint's start and end dates are the only dates 23.5 added, and **all three of these are on
that path**:

| Symptom | Cause |
| --- | --- |
| The month arrows did nothing | `shownMonth = shownMonth?.let { ttShiftMonth(it, step) }` |
| The picker opened on January 1970 | `shownMonth ?: today ?: value ?: LocalDate(1970, 1, 1)` |
| A year could not be chosen at all | there was no year control; only two month arrows |

The first two are **one line of code**. `shownMonth` was seeded from `value ?: today`, and for a
sprint being *created* both are null — `SprintEditorSheet` declares `today: LocalDate? = null` and
`NavigationHost` never passes it, despite the model already holding a `dateProvider`. A null seed
made `shownMonth?.let { … }` assign null to null, so the arrows were inert **permanently**, not
just on the first press, and the last fallback standing was a literal 1970.

**Nothing about this is visible in a test.** The old and new grids are identical whenever `today`
is non-null, which is the case every existing test used. The defect only appeared when a caller
omitted `today`, and the only such caller was the one screen the feature shipped with.

#### What it is fixed with

- `anchorToday = today ?: ttSystemToday()`, resolved once, so the seed and the year window cannot
  disagree about which day it is. `today` stays the test seam and every test still passes it.
- `shownMonth` seeded and clamped, and `onStepMonth` shifting the **displayed** month rather than
  a nullable one — which makes the handler total instead of conditional.
- The **month-and-year label is the control** that opens a year grid, carrying a `chevronDown`
  rather than a new glyph because it is the same affordance the closed field already uses. It
  swaps the grid's *contents* rather than opening a second popup: the field's popup already has a
  position provider that flips above the field for the end date at the bottom of a form, and a
  popup of a popup would need that logic twice.
- A window of **[ttYearWindow](today) = today ± [TT_YEAR_WINDOW_SPAN] (5)**, enforced on month
  stepping too. Clamping to the *nearest reachable month in the direction pressed* — forward off
  the last December lands on December of the last year, not January of the first — because
  landing on January would move the user backwards from the arrow they pressed.

#### 15 tests, 10 mutations, 10 caught

`tools/mutate_datefield_year.py`. Seven are arithmetic ([ttYearWindow], [ttClampToYearWindow],
[ttStepMonthInWindow], [ttYearRows], [ttSystemToday]) and eight operate the composed calendar.

**Every interaction assertion is about a day cell, never about text.** "Is March 2026 showing" is
answered by the presence of `TTDayCellTag(LocalDate(2026, 3, 1))` — one unambiguous node — rather
than by parsing a label whose text comes from `ttMonthName`, a resource lookup that would fail
these tests on a locale change for a reason that has nothing to do with the calendar.

**Y1 and Y2 were first written as the literal old expressions and were compile-rejected.** The fix
changed `shownMonth`'s *inferred type* from `LocalDate?` to `LocalDate`, so
`shownMonth?.let { … }` no longer assigns. That is the better outcome of the two — the compiler
now refuses the bug's original form — but it is **not** evidence that anything is tested, so both
were rewritten to reproduce the same two observable faults in a form that does compile. This is
the seventh compile-rejected case in the project, and the first where the rejection was the fix
working as intended rather than the mutation being malformed.

#### Baseline

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1272 | **1287** | 7, unchanged |
| iOS simulator | 1244 | **1259** | 7, unchanged |

+15 on both, from the JUnit XML, cross-checked against `@Test`: `DateFieldTest` 18 → 25 and
`DateFieldInteractionTest` 0 → 8.

**Android host: measured, not assumed.** `:packages:design-system:testAndroidHostTest` is
**136 tests / 14 failed** after the change and was **121 / 14** before it — the original files
restored from `HEAD` to measure that, then restored again. Identical failures, +15 tests. The eight
interaction tests report **8 passed, 0 failed** there: they are behind `thisHostRasterisesText()`,
which registers the skip as a pass on that host, and which is the honest outcome for a `Popup`.

`TTDateMonthStepTag` was removed rather than kept: it built a tag out of a *localised string*, so a
test asserting "the next-month arrow" would fail on a translation instead of on a defect.It is replaced by [TTMonthStepTag], addressed by the step it performs.

### 23.11 The month arrows pointed the wrong way, and the year screen opened elsewhere

Reported as one message and three defects, none of which shares a cause with 23.10.

#### Three defects, three causes

| Symptom | Cause |
| --- | --- |
| The left arrow stepped forward | `chevronLeft` and `chevronRight` had each other's bodies |
| The arrows were slabs, not chevrons | `MonthStepGlyph` sized the `Image` to `ttSpacing.iconButtonSize` |
| The year screen opened somewhere else | a popup's y offset is a function of its height, and the year grid is half as tall |

#### The glyphs were on each other's names

`chevronLeft`'s contour is the exact quarter turn of `chevronDown` with its apex at **x = 17** —
which is the *maximum* x, so it points right. Both bodies were correct rotations of
`chevronDown`; they were simply bound to each other's names, so the arrow left of "March 2026"
stepped forward and the one on the right stepped back.

**Every existing assertion passed on a backwards arrow.** A chevron does not have to point
anywhere for `every glyph has path nodes`, `every glyph is drawn inside the viewport` or
`every glyph fills the grid` to be true of it, and both bodies are non-empty, inside the
viewport and 18x18 units. A review diff of two hexagons does not show that only the *binding*
is wrong either. So the direction is now read off the geometry: a chevron's **apex** is the
vertex at the extreme of its own bounds along the axis it points, it must be that extreme
alone, and it must sit on the other axis's centre line — plus the relational claim that the
left-facing apex is left of the right-facing one, which is what a swap cannot satisfy.

#### The binding to the slots was separately unguarded

Swapping the two `glyph =` lines at the `TTDayGrid` call site is a **different defect** from the
one above, and nothing in the suite could see it: [TTMonthStepTag] is built from the `step`
argument, not from the glyph, so both tags, both click handlers and all nine
`DateFieldInteractionTest` cases stay green while the calendar's arrows read backwards.

There is no way to read "which vector did this node draw" off the semantics tree — the painter
is not in the node — and the slots do not move, so a position assertion does not close the gap
either. Per `AGENTS.md`'s table a direction claim is a **silhouette claim, which is a pixel
claim**, so [MonthArrowRenderTest] renders the real header and measures ink.

**The centroid, not the bounding box.** A chevron is symmetric about its own axis, so its ink
*extent* says nothing about direction — the left-facing and right-facing glyphs have identical
bounds. The *centroid* does say: the apex is the thick end of the mark and the terminals are cut
square, so the ink leans towards the point. Measured on the JVM and on the iOS simulator, which
agree to the pixel: **19.5 against a box centred on 22.0** for the previous arrow, **1003.5
against 1002.0** for the next. Margins of 2.5px and 1.5px, with a backwards chevron measuring
about 5px the other way, so the truth sits in the middle of a 5px separation. Thin, and written
down rather than widened — and the numbers are in the failure message so a false red is
diagnosable.

#### The arrows were 44dp of chevron in a 44dp target

`MonthStepGlyph` sized the `Image` to `ttSpacing.iconButtonSize`, so the glyph spanning 18 of its
vector's 24 units rendered 33dp across inside a 44dp box — three quarters of it, flanking a row
whose other content is a word. The 44dp is now the `Box` and the chevron is
[dateFieldChevronSize], the same "half the target, reads as an affordance" figure the closed
field's own chevron already used.

Measured, and **identical on both rasterisers**: the arrow's ink spans **11px of its 44px box**
and covers **72 pixels**, against **22px** and **270 pixels** at the old size — 0.25 against
0.50 of the box. Those are the numbers the test bounds. The ink threshold stops short of the
antialiased edge, so the drawn extent is larger than 11px; the *ratio* is what the assertion
uses, not the absolute.

#### The year screen opened on the other side of the field

A popup's offset is computed from its own measured size:
[DateFieldPopupPositionProvider] opens **below** the field when the popup fits and **above** it
when it does not. The day grid is six rows and the year grid is three, which is enough to cross
that line for a field low in a sheet — and the end date of a sprint is exactly that field. So
the calendar opened above and the years opened below, and tapping the month label appeared to
throw the picker across the screen.

Fixed where the height varies, not in the provider: `TTDateField` learns the popup's content
height from the day grid with `onSizeChanged` and imposes it on both grids. **Measured rather
than computed**, because the height is a sum of tokens and a typed line height and a constant
would silently stop matching the day grid the first time the typography scale changed.

The assertion is about the popup's **size**, not its offset — the offset is a function of the
size, and a popup is its own composition root, so `size` needs no mapping into any captured
image's coordinate space.

**The width pin was written, measured, and deleted.** The provider also clamps `x` to
`windowWidth - contentWidth`, so a narrower grid would in principle shift sideways, and the
first version pinned both axes. Unpinning the width went **green**: every row of both grids is
`fillMaxWidth()`, so both resolve to the popup's own maximum width and the popup is always
window-width — **1000px on the JVM host, in both states**. The constraint was dead code, so it
went, and with it a comment defending a case that cannot occur. The width half of the test
stayed, because it is what would say so if that ever stopped being true.

#### Three things the mutation ritual caught that reading would not have

- **C3 went green on the first pass, and the test was at fault.** The size bound was 0.55 of the
  box, from a geometric estimate of 0.36 for the draw mark and 0.75 for the oversized one. The
  estimate was wrong in the one direction that mattered: the ink threshold truncates the span,
  so the measured fractions are **0.25 and 0.50**, and the bound sat *above* the oversized
  arrow. It is 0.35 now, between the two measured numbers.
- **A red baseline produced four green ticks.** Removing the width pin also removed the `then`
  branch that guarded `popupContentHeightPx == 0`. Without that guard the first layout is
  `height(0.dp)`, `onSizeChanged` reports zero, the value stays zero for the life of the field,
  and the popup **never opens** — seven of the nine `DateFieldInteractionTest` cases failed with
  "could not find any node". Every one of the four mutations then reported itself *caught*, on
  the strength of those seven. That is the whole argument for measuring the baseline instead of
  assuming it: [tools/mutate_datefield_chevrons.py] now runs the unmutated tree first and
  refuses to report any verdict unless it is green. A mutation runner that cannot tell a caught
  mutation from a pre-existing failure is worse than no runner.
- **`String.format` is JVM-only.** A failure message built with `"%.2f".format(x)` compiles for
  `jvmTest` and breaks `iosSimulatorArm64Test`, which has no equivalent. Only the second host
  finds it.

#### Mutations

`tools/mutate_datefield_chevrons.py`: **5 mutations, 5 caught, 0 compile-rejected**, and each by
exactly the assertion it should be — C1 the four geometry assertions, C2 (the gap) the ink
centroid alone, C3 the ink span alone, C4 and C6 the popup size alone. C6 is the zero guard,
recorded because it is a defect that actually shipped into this working tree.

#### Baseline

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1287 | **1293** | 7, unchanged |
| iOS simulator | 1259 | **1265** | 7, unchanged |

+6 on both, from the JUnit XML, cross-checked against `@Test`:
[TabIconsGeometryTest] 9 → 12, [DateFieldInteractionTest] 8 → 9, and a new
[MonthArrowRenderTest] 0 → 2.

### 23.12 The calendar filled the screen and had no way out

> *"The calendar opens as a full screen page and there is no way to close it. Make the calendar
> popup open in the middle of the screen and the popup should be the size of the calendar itself
> not whole page size."*

One sentence, three defects, and **the first one had already been measured in 23.11 and written
off.**

#### The number was in the record and was read as a curiosity

23.11 measured the popup's contents and reported: **1000px wide inside a 1024px window**, and
"every row of both grids is `fillMaxWidth()`, so both resolve to the popup's own maximum width
and the popup is always window-width". That was correct, and it was treated as an inert
observation — the width was pinned, found to make no difference, unpinned, and a comment was
written defending the x-clamp case that could not occur. The number was the defect, sitting in
the paragraph that described it.

The mechanism is one modifier. `TTDayGrid`'s header, weekday and six day rows are all
`fillMaxWidth()` and its cells are `weight(1f)`, so given nothing to bound them they resolve to
the popup's **maximum** width, which is the window's. Anchoring the popup to the field could not
have fixed it, because the width is settled before any offset is asked for — `popupContentSize`
arrives already measured.

#### Why "no way to close it" is the same defect

`onDismissRequest` is reached by an outside tap or by back. **A popup that fills the window has
no outside left to tap.** So the picker read as a page with no exit for the same reason it read
as full-screen, and the two halves of the report are one bug.

#### The fix

- **Width**, from [dateFieldPopupWidth]: seven 44dp cells and the surface's own padding, computed
  from the two tokens the grid already uses rather than written as a number — **332dp**. Applied
  before `.padding`, so it is the popup's whole width, and Compose coerces it into the popup
  window's constraints, so a narrow screen shrinks it rather than letting it overhang.
- **Placement**: [DateFieldPopupPositionProvider] is now a plain centring provider,
  `ttCentredPopupOffset`. The anchor is ignored *because* centring makes it irrelevant. A popup
  taller than its window starts at the top rather than at a negative offset, which is the
  `coerceAtLeast(0)` on both axes.
- **A Done button**, in a footer below the grid, calling the same `dismissPicker` lambda as
  `onDismissRequest`. It does **not** assign: choosing a day already closes the picker, so a Done
  that also assigned would be a second, invisible way to answer the question.

**A footer, not the header.** The header row is where the month arrows live, at sizes and
positions `MonthArrowRenderTest` measures; a third control there would move both of them for no
gain. `End` alignment and the `resting` style, not `fillMaxWidth` and `prominent`: closing a
picker is not the sheet's primary action, and a full-width accent button would claim to be the
most important thing on screen when what the user came for is a date.

#### This reverses 23.4, deliberately

23.4 anchored the popup beside the field so the field stayed in view as the reference for what
was being changed. That reference is given up on purpose and is recorded here as a decision
rather than a drift: a picker the user can see all of, read at its own size, and dismiss three
ways is worth more than one beside a field it covers. The height pin from 23.11 is **kept**, but
its reason has changed — centring removed its dependency on position, and what remains is that
the popup should not visibly resize under the user's finger when the calendar becomes the year
list.

#### Half of this cannot be asserted on a frame, and the split is not arbitrary

**A popup's rendered position cannot be read back.** A `Popup` is its own composition root —
once one is open, `onRoot()` finds *two* of them — and the content's bounds are reported in the
popup's own coordinate space, before the window offset is applied. So the **width** is measured
on a rendered frame (`the popup is the width of the calendar and not the width of the window`,
which was 308 and should have been 332, see below) and the **placement** is asserted on
[ttCentredPopupOffset] as a plain function. Which is the right way round: the width was the
defect, and the placement is two subtractions that cannot silently change behaviour on one host.

#### Two of the new tests were wrong on their first run, and both were informative

- **[TTDateFieldPopupTag] sat after `.padding`**, so it marked the 308dp content rather than the
  332dp surface — and had done so since 23.4. The tag now goes outside the padding, so it means
  the popup's own size. A tag in the wrong place is a measurement that is quietly 24dp short of
  the thing it names.
- **The centring test asserted exactly equal margins**, which is asserting that a remainder does
  not exist: 768 − 349 = 419 is odd, so one margin is 209 and the other 210 and the popup *is*
  centred. The bound is now "equal to within a pixel", with the arithmetic written down.

#### A hole in the Done test, found by writing its mutation

The first version of `Done closes the picker without choosing a date` asserted only that the
popup closed, under a KDoc claiming Done must not change the date. P5 — Done also assigns — went
green against it. The test was fixed rather than the mutation dropped: `withField` now collects
every date the field reports, so the test asserts the field reported **none**.

#### Mutations

- [tools/mutate_datefield_popup.py]: **5 mutations, 5 caught, 0 compile-rejected**. P1 (no width
  constraint) caught by the width assertion *and* by the 44dp cell width, P2 (anchor instead of
  centre) and P3 (no `coerceAtLeast`) by the placement assertions, P4 and P5 by Done.
- [tools/mutate_datefield_chevrons.py] re-run against the changed file: **5 mutations, 5 caught**,
  baseline green.

The new runner's first act is to run the **unmutated** tree and refuse to report anything unless
it is green. That guard earned its place immediately: on its first run it reported
`BASELINE DID NOT RUN` because a missing test import had stopped the task compiling, and without
the check that would have read as "caught" for every mutation in the file.

#### Baseline

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1293 | **1299** | 7, unchanged |
| iOS simulator | 1265 | **1271** | 7, unchanged |
| Android host (`:packages:design-system`) | 136 | **148** | 14, unchanged |

+6 on both deciding hosts, from the JUnit XML and cross-checked against `@Test`:
[DateFieldInteractionTest] 9 → 13 and [DateFieldTest] 25 → 27. The Android host figure spans
23.11 and 23.12 together — 136 + 6 + 6 — and its 14 failures are unchanged across all three.

### 23.13 The calendar was still stuck to the top — a pixel count read as a dp count

> *"Its horizontally centered now but vertically its not, still a long top to buttom background
> and also the calendar is sticked to the top of the page."*

The width half of 23.12 worked. This is the height half, and the cause is one line:

```kotlin
.onSizeChanged { popupContentHeightPx = it.height }   // pixels
.height(popupContentHeightPx.dp)                       // ...applied as dp
```

**At the tests' density of 1 a pixel count *is* a dp count**, so this is arithmetically
invisible on every host this project runs and wrong by exactly the density factor everywhere
else. A 419dp calendar measures 1257px on a density-3 phone, and reading that back as `1257.dp`
asks for a card three times too tall. A card taller than the window has nowhere to be centred:
`ttCentredPopupOffset`'s `coerceAtLeast(0)` puts `y` at **0**, the card sits at the top, and its
background runs the full height of the window — all three halves of the report, from one unit
error.

Reproduced before fixing, by rendering at density 3: **a popup 996x768px inside a 768px window,
its grid clamped from 696px, `y = 0`.** That is the reported symptom with the numbers attached.

#### Two phases of measurement, all of it at density 1

23.11 and 23.12 both measured the popup's size and both were correct — 1000px wide, then 332dp
— and neither could see this, because a test host's density of 1 makes the two units identical.
The lesson is not "measure more" but that **a unit error is invisible to every measurement you
take in the unit you happen to be in**, and the assertion has to be about the *relationship*
between densities rather than about any one card.

#### The fix

The pinned height is a `Dp`, converted at the point of measurement:

- `popupContentHeight` is `Dp`, set from `with(density) { size.height.toDp() }`, so the unit
  travels with the value and the same mistake cannot be made twice. `BoardColumn`'s
  `headerHeight` is the same idiom and was in the tree already.
- `Dp.Unspecified` replaces `0` as the "not measured yet" sentinel. `0.dp` is a value this must
  never hold — applying it collapses the grid, `onSizeChanged` reports zero back, and the popup
  never opens at all. That is not hypothetical: it is what happened in 23.11 when a tidy `then`
  branch took the guard with it.
- The density is read in the composable scope, **not** inside the `onSizeChanged` lambda, which
  is not a composable and cannot read a `CompositionLocal` at all. The compiler says so.

#### One instance of the shape, not a class

`grep` for a px-valued name applied as `.dp` across the tree returns **one** hit — the line
above. The three sibling measurements are all correct: `BoardColumn` converts with
`LocalDensity`, `ProjectSwitcherButton` converts inside a `Density` scope, and `Slider` only
does px arithmetic and never converts.

#### The second cause of the same symptom: a window shorter than the calendar

The unit fix is not sufficient on a **landscape phone**, whose window is about 360dp against a
card that wants 419dp. Clipped at the top again, from the other direction. So:

- The popup takes at most [TT_DATE_FIELD_MAX_HEIGHT_FRACTION] of the **window's** height, read
  from `BoxWithConstraints`'s `maxHeight` — there is no other way to ask, because the popup is
  its own composition root and `LocalWindowInfo` is not the app's window from inside it.
- The grid takes what is left after the footer (`weight(1f, fill = false)`, with the pin applied
  only if it fits inside that budget), and the overflow **scrolls**.
- **The footer is outside the scroller**, so `Done` stays reachable when space runs out. That is
  the one that matters: a footer that scrolls away is a picker with no way out, which is the
  defect this whole phase was opened for.

#### Two things the mutations caught that reading would not have

- **P7 went green until the test density was chosen properly.** At density 2.5 the card is 136%
  of the window, so the *window* clamps it whether or not the cap exists, and deleting the cap
  changed nothing observable. The cap only binds when the card is **between 90% and 100%** of the
  window — a narrow band, and one that has to be aimed at deliberately. Density **1.75** puts a
  419dp card into a 439dp window and P7 goes red immediately.
- **The host guard was in the wrong place, and it hid a pass.** The `thisHostRasterisesText()`
  check sat inside `probePopup`, which returned a zeroed measurement, so on the Android host the
  two probes were compared against each other: the density test passed **vacuously** (0 == 0) and
  the short-window test failed on `0px` — a new failure on a host that was previously at 14. A
  skip that is allowed to reach an assertion is not a skip; the guard belongs at the top of the
  test.

Also: the scroll semantics live on the scrolling `Box`, not on the grid inside it, so
`TTDayGridTag` cannot answer "does the calendar scroll" — hence [TTDateScrollTag]. And `Done` is
asserted **displayed**, not merely present: P8, which deletes the `weight`, leaves the footer in
the tree at zero height, and an existence assertion cannot see that.

#### Mutations

- [tools/mutate_datefield_popup.py]: **8 mutations, 8 caught, 0 compile-rejected**. P6 is the
  unit error, caught by the density-invariance assertion *and* the short-window one; P7 and P8
  by the short-window test.
- [tools/mutate_datefield_chevrons.py]: **5 mutations, 5 caught**, after updating C4 and C6 for
  the `Px` → `Dp` rename. Both of those had gone stale — `PATTERN NOT FOUND` rather than a
  silent pass, which is the failure mode worth having from a mutation runner.

#### Baseline

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1299 | **1301** | 7, unchanged |
| iOS simulator | 1271 | **1273** | 7, unchanged |
| Android host (`:packages:design-system`) | 148 | **150** | 14, unchanged |

+2 on both deciding hosts, from the JUnit XML and cross-checked against `@Test`: two tests added
to [DateFieldInteractionTest] 13 → 15. The Android host went to 150/15 on the first run and back
to 150/14 once the guard moved — which is the second time this phase that measuring the baseline
rather than assuming it has been what caught the mistake.

### 23.15 Project switcher on the sprints tab

The sprints tab had no way to switch projects without leaving it: it showed the current project's
sprint list but offered no control to move to another. The board tab already had one, so the
sprints tab now mirrors the `BoardDestination` pattern — a `ProjectPickerModel` observed against
the repository, a `produceState` read of the current project, and a `ProjectSwitcherButton` handed
four params (`project`, `onOpenSwitcher = { router.popToRoot() }`, `projects`, `onSelectProject =
{ router.openProject(it, AppTab.Sprints) }`). The `ProjectSwitcherButton` itself is reused as-is;
only its wiring in `SprintsDestination` changed.

The `project` param on `SprintListScreen` got a `= null` default so the two existing previews
keep compiling without it; production call sites always supply it.

#### Test

[`switchingProjectsFromTheSprintsTabCallsOpenProject`] in `ShellScenarios.kt`, registered in
`TaskTrackerAppJvmTest`, `TaskTrackerAppIosTest`, and `TaskTrackerAppAndroidHostTest`. Two
seeded projects (Apollo/AP, Viking/VI so their `ProjectRowTag`s don't collide), open the first,
switch to the sprints tab, open the switcher, select the second — then assert
`router.state.value.activeProjectID == other.id` and `router.state.value.selectedTab == AppTab.Sprints`.

The two-project setup matters: `shellTestProject` defaults both to `keyPrefix = "AP"`, and
the first attempt failed with "Expected exactly '1' node but found '2' nodes that satisfy:
TestTag = 'projects.row.AP'".

#### Mutation

One mutation, one caught: inverting `AppTab.Sprints` → `AppTab.Board` in `onSelectProject`
makes the test go red on the `selectedTab` assertion. Restored and green.

#### Baseline

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1301 | **1302** | 7, unchanged |
| iOS simulator | 1273 | **1274** | 7, unchanged |
| Android host (`:shared-ui`) | 31 | **32** | 7, unchanged |

+1 on both deciding hosts, from the JUnit XML and cross-checked against `@Test`: one scenario
added and registered on all three entry points. The Android host count is up by one but its
17 pre-existing Compose-layout failures are unchanged; that host is outside the deciding command.

### 23.16 System back pops one route

On Android the system back button closed the whole app from the sprint detail: nothing in the
signed-in tree consumed the back event, so the press reached the activity default. `NavigationHost`
now installs a `BackHandler` that pops exactly one route — `router.setPath(currentPath.dropLast(1),
tab)` — guarded by `enabled = path.size > 1`. The guard is the stack *size*, not `pushed != null`,
because the sprints tab's root stack is `[SprintList]` (size 1): a `pushed`-based guard would
wrongly enable back at the tab root and let the press fall through to the activity default there.
The lambda re-reads `router.path(tab)` instead of closing over `path`, the same discipline the
editor's `onDismiss` carries: the lambda runs after the gesture, on a stack that may have moved.

#### Test

[`systemBackPopsTheSprintDetail`] in `ShellScenarios.kt`, registered in `TaskTrackerAppJvmTest`,
`TaskTrackerAppIosTest`, and `TaskTrackerAppAndroidHostTest`. It opens a project, taps to the
sprints tab, taps a sprint row, asserts the stack is `[SprintList, SprintDetail]`, presses system
back, and asserts the stack is `[SprintList]` with the list screen displayed — then presses back
again at the root and asserts the stack is unchanged.

The press goes through `pressSystemBack`, an `expect`/`actual` split in `SystemBackSupport`, and
the split is measured rather than stylistic. On the Android host an explicit
`LocalNavigationEventDispatcherOwner` provide *is* the one the composition local resolves (a
diagnostic printed `resolved isOurOwner=true dispatcherIsOurs=true`), yet the androidx `BackHandler`
that the platform delegate installs registers with the *activity's* dispatcher: the provided
dispatcher's history stayed empty, a synthetic `dispatchOnBackCompleted()` on it fired no handler,
and `activity.onBackPressed()` did. So the Android actual presses the real activity chain, and the
JVM and iOS actuals — whose `BackHandler` registers with the provided dispatcher — dispatch on it.
The scenario itself stays common; only the press is per-host.

#### Mutation

Two mutations, two caught, one on each press path: replacing `dropLast(1)` with the unmodified
`currentPath` in the `BackHandler` makes the test red on the JVM (`expected:<[SprintList…]> but
was:<[SprintList…, SprintDetail…]>`) and red on the Android host through the real
`activity.onBackPressed()` chain with the same assertion message. Restored and green on both.

#### Baseline

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1302 | **1303** | 7, unchanged |
| iOS simulator | 1274 | **1275** | 7, unchanged |
| Android host (`:shared-ui`) | 32 | **33** | 7, unchanged |

+1 on all three hosts, from the JUnit XML and cross-checked against `@Test`: one scenario added
and registered on all three entry points. The failure sets are the documented pre-existing ones —
`SeedingTest` ×3 in `:packages:feature-projects-data`, the two glass-contrast assertions in
`:packages:design-system`, and `aChosenThemeOutlivesTheProcess` plus `theBoardReturnsToTheList`
in `:shared-ui` on the deciding hosts; the seven Robolectric-viewport layout failures in
`:shared-ui` on the Android host. None moved.

### 23.17 The light glass caption and rim, repaired at the token

Phase 19 recorded two findings (§19.4, §19.5) and deliberately left them unfixed as design
questions. `GlassSurfacePixelTest` was written to settle them by rendering rather than by
arithmetic, and both rasterisers reported the same figures to the digit — **2.88:1** for a
`ttLabelSecondary` caption over the rendered light glass fill (`#747780` over `#c8d0db`),
below the 3:1 large-text bar *and* below the 3.44:1 the same token manages on the bare
canvas, so glass was *costing* the caption contrast rather than adding it; and **1.07:1** for
the light hairline (`#cad2dc` over `#c2cbd7` — white at `HAIRLINE_ALPHA`, blended over 1
device row at 0.5dp on a 3× display), so light-mode glass had no lit edge at all. Both were
real defects, and both are repaired at the token they named, not by re-tuning the measurement.

**The caption.** The fill cannot be lightened — `ttGlass.FILL_ALPHA` is every card's contrast
budget, and spending it to carry one token is the wrong direction. So `labelSecondaryLight`
was darkened eight channels, `(60, 60, 67)` → `(52, 52, 58)`, at the unchanged 0.60 alpha.
It renders **#6f727a over #c8d0db = 3.09:1** (3.68:1 on the bare canvas, against the
platform `secondaryLabel`'s own 3.44:1). It deliberately still does not meet the 4.5:1 body
bar — the token resolves UIKit's `secondaryLabel`, which does not either — and
`PinnedAppearanceTest`'s ordering (3:1 ≤ secondary < label over the canvas) still holds at
3.68:1. Dark is untouched: `#97989f` over `#191b1e` = 6.01:1, and 6.36:1 on canvas.

**The rim.** No value of `HAIRLINE_ALPHA` fixes the light edge, and that is measured, not
assumed: white at 0.14 was 1.07:1, at 0.5 it was 1.30:1, and a fully opaque white line —
no longer a hairline — reached only **1.64:1**, because the light fill (`#c2cbd7`) is already
near the top of what white can reach. So light gets the opposite rim grammar: a **shadow
line**, black at the new `ttGlass.LIGHT_HAIRLINE_ALPHA = 0.22f`, which renders **#979ea7 =
1.65:1** over the same fill — darker than its fill, the same raised edge arriving from the
other direction. Dark keeps the white rim at `HAIRLINE_ALPHA` and its 1.54:1 with no change
at all, because dark fill is dark enough for white to be lighter. The resolver
`ttGlassHairlineAlpha(): Float` became `@Composable internal fun ttGlassHairline(): Color` in
`GlassIntensity.kt` — white at `HAIRLINE_ALPHA × intensity` in dark, black at
`LIGHT_HAIRLINE_ALPHA × intensity` in light — so the rim still scales with
`LocalGlassIntensity` and still paints nothing at zero intensity, and `ttGlassSurface`'s
border binds to it. The Phase-19 model was within 0.01 on both rim figures and 0.16 on the
captions; close, but the rendered reading decided which number to change.

Both hosts agree to the digit on every figure in this entry. These are flat fills, so they
are expected to, and the agreement is itself the check that each reading measured the
composite and not something rasteriser-specific.

#### Test

No test was added; the two gates in `GlassSurfacePixelTest` already existed and now hold the
fixed numbers, with the repair and its measurements in their failure messages so a regression
says what drifted. Green-compatibility of the neighbours was checked, not assumed:
`PinnedAppearanceTest` (alpha 0.60 ± 0.005, light ≠ dark as full RGBA, 3.68:1 inside its
3:1-to-label band), `BrandConstantsTest` (`HAIRLINE_ALPHA < 1f` still true),
`GlassIntensityRenderTest` (samples panel centres, never the hairline), and `Avatar` /
`TabBar`, which carry their own rim alphas by design and are untouched.

#### Mutation

Two mutations, both caught, one per repair:

- Reverting `labelSecondaryLight` to `0x993C3C43` made `a caption on the rendered glass fill
  still clears the large-text bar` fail at **2.88:1** (`#747780` over `#c8d0db`), the
  message naming the old composite.
- Reverting the rim to white on both appearances made `the hairline reads as a lit edge in
  light` fail at **1.07:1** (`#cad2dc` over `#c2cbd7`).

Both fixes restored; both hosts green.

#### Baseline

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1303 | **1303** | 7 → **5** |
| iOS simulator | 1275 | **1275** | 7 → **5** |

No test was added or removed — the totals are unchanged and only the failure sets shrank, to
the documented pre-existing set: `SeedingTest` ×3 in `:packages:feature-projects-data` and
`aChosenThemeOutlivesTheProcess` plus `theBoardReturnsToTheList` in `:shared-ui`. The
deciding command (`jvmTest iosSimulatorArm64Test verifyArchitectureGraph
verifyModuleImports :androidApp:compileDebugKotlin`) now reports exactly those five per host.
`AGENTS.md`'s test-host table was re-measured to the same figures; it had stood at the
Phase-15 counts.

</details>

<a id="phase-24--board-identity-and-the-backlogs-third-segment"></a>
<details open>
<summary><b>Phase 24 — Board identity and the backlog's third segment</b> &mdash; <i>open</i></summary>

## Phase 24 — Board identity and the backlog's third segment

**Nothing in this phase is built yet.** This is the breakdown, written down before any of it so
that the order is a decision rather than a sequence discovered halfway through. Each `### 24.N`
is small enough to start and finish in one session, and each appends its own
`#### Test` / `#### Mutation` / `#### Baseline` when executed, exactly like Phase 23's tasks.

### 24.0 What was asked, and the four decisions it turns on

The board's project switcher repeats the full project name on a header line whose remaining
space is empty; the sprint title, or the word the board goes by when there is no sprint, belongs
there. The backlog tab has two sections (Backlog, Archived) but no home for a task that is
already on a board.

| decision | choice | why this and not the other |
| --- | --- | --- |
| switcher pill | **initials avatar + chevron only, Board only** | The monogram already carries the project's key prefix; dropping the name frees the header space the title needs. The other three screens (backlog, sprints, archive) keep the full name — their headers give the switcher `weight(1f)` and have no title row to make room for. |
| the title row | **sprint name, or "Kanban" when there is no active sprint** | The sprint is the board's identity while one is open; the board's identity otherwise. The old full-width `Active Sprint:` banner (`ProjectBoardScreen.kt:194-201`) is redundant and removed — its tag `board.activeSprint` is asserted by no test. |
| a task added with no active sprint | **keeps its lane status and `sprintID = null`, and stays visible** | Already the runtime contract (`initialSprintID = model.activeSprint?.id`, `NavigationHost.kt:699-703`): with `activeSprint == null` the model filters nothing, so the new task is on the Kanban board. 24.3 pins it, because a filter change that silently drops it is exactly the defect this repo keeps meeting. |
| the backlog's third segment | **Kanban = everything with `status.appearsOnBoard`; verbs = open + file away** | The board is already a pure partition of `WorkItemStatus`; no new field exists or is needed. Archive is `Closed`-only (`Projections.kt:119-121`), so Kanban sits between Backlog and Archive and overlaps nothing. |

### 24.1 Switcher shows initials on the board

**All three done.** `ProjectSwitcherButton` gained `showName: Boolean = true`; when false, the
pill renders the monogram, the chevron and nothing else. A null project still shows the "No
project" words even in compact mode, because "a project is selected" is the one state the pill
identifies and two bare marks would misname the screen. The dropdown is untouched and still
lists every project's full name. `ProjectBoardScreen` is the only caller passing `showName =
false`; backlog, sprints and archive keep their defaults.

- [x] **24.1.1** `ProjectSwitcherButton.kt`: a board-only flag (default keeps today's behaviour)
      renders the pill as monogram + chevron with no name text; the dropdown is unchanged.
- [x] **24.1.2** Wire it from `ProjectBoardScreen.kt:164-171`; backlog/sprints/archive keep full names.
- [x] **24.1.3** Adjust the bounds assertions in `BoardLaneScenarios.kt`
      (`theSwitcherIsNoWiderThanItsContentAllows`, :144-158, and friends); mutate by flipping the
      flag and expect the assertion to go red.

#### Test

[`theSwitcherShowsTheMonogramAndNotTheName`] in `BoardLaneScenarios.kt`, registered in
`ProjectsUiJvmTest`, `ProjectsUiIosTest` and `ProjectsUiAndroidHostTest`. It asserts the
monogram and chevron exist (on the unmerged tree — the pill is a clickable `Row`, which merges
its descendants' semantics, the same reason the stack discs read `useUnmergedTree`) and that the
project's name text node is absent: `onAllNodesWithText("Task Tracker").assertCountEquals(0)`.
The `theSwitcherIsNoWiderThanItsContentAllows` scenario survived unchanged — the compact pill is
still content-width and still capped.

#### Mutation

One mutation, caught, on the production flag: flipping `showName = false` back to `true` at the
board call site makes the name text reappear, and the test goes red with the defect named:
`Did not expect any node but found '1' node that satisfies: (Text ... contains 'Task Tracker')`,
the node being `board.switcher`. Restored, green again.

#### Baseline

The two hosts that can run without `:shared-ui` (see the phase note below):

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM (`:packages:feature-projects-presentation`) | 215 | **216** | 0, unchanged |
| iOS simulator (same module) | 215 | **216** | 0, unchanged |

+1 per host from the JUnit XML, cross-checked against `@Test` (`ProjectsUiJvmTest` 42 → 43,
`ProjectsUiIosTest` 41 → 42, `ProjectsUiAndroidHostTest` 42 → 43).

**Pre-existing blocker, not this change:** `:shared-ui` does not compile in the current tree —
`ShellScenarios.kt` references `BacklogSectionScenarios` (its `projectBacklogScenarios` list,
lines 1654-1662) but no mechanism exposes `<feature-projects-presentation>:commonTest` to
`:shared-ui`'s test compile, so `Unresolved reference 'BacklogSectionScenarios'` fails
`compileTestKotlinJvm`. Confirmed by stashing this change and rebuilding. That is 24.4's surface
(its "add the open callback through `BacklogDestination`" and the three-host registration), and
it is why the whole-command baseline cannot be re-measured yet; the AGENTS.md global numbers
(1303/1275) wait for 24.5 when `:shared-ui` compiles again.

### 24.2 Header title: sprint name, or "Kanban"

**All three done.** The header's left group now fills (`weight(1f)` instead of `fill = false`) and
a `ttText` in `ttTypography.titleSmall` takes the leftover width, ellipsising long sprint names:
`activeSprint?.name` when a sprint is running, otherwise the new string `board_kanban` =
"Kanban". The full-width **`Active Sprint:` banner is deleted** (with its `ttBannerTone` import and
the `board.activeSprint` tag, which no test asserted), replacing two stripped text lines with the
name in the line the compact switcher freed — that, not the banner, is what 24.1 made room for.

- [x] **24.2.1** `ProjectBoardScreen.kt` header row: add a headline in the free space (left weight
      group, :159-177) reading `activeSprint?.name` when one exists, else a new string resource
      "Kanban"; remove the `Active Sprint:` banner (:194-201).
- [x] **24.2.2** Pass `activeSprint = model.activeSprint` into `NavigationHost.kt`'s
      `BoardDestination` (:682-715) — the only board host that currently drops it.
- [x] **24.2.3** Structural tests for the title (present, pinned to sprint name / "Kanban") and
      the banner's absence; mutate by deleting the wiring.

#### Test

Two scenarios in `BoardLaneScenarios.kt`:

- [`aBoardWithoutAnActiveSprintIsNamedKanban`] — `onNodeWithTag(ProjectBoardTitleTag)` equals
  the `board_kanban` resource. The wire in `boardShowing` gained an `activeSprint` parameter.
- [`aBoardWithAnActiveSprintNamesItInTheHeader`] — the same node equals `ViewPreviewData.openSprint.name`
  ("Sprint 7"), and `onAllNodesWithText("Active Sprint", substring = true)` is empty, so the
  banner cannot silently come back in disguise.

Both registered in `ProjectsUiJvmTest`, `ProjectsUiIosTest`, `ProjectsUiAndroidHostTest`.

#### Mutation

Two mutations, both caught, both red on the sprint-name scenario:

1. Dropping `activeSprint?.name` from the title made it fail with the node's real text named —
   `Text = '[Kanban]'` on `board.title` where `['Sprint 7']` was expected.
2. Rewriting the title as `"Active Sprint: ${name}"` failed the same assertion (and the
   substring-absence check it precedes), the message naming `[Active Sprint: Sprint 7]`.

Restored, both green on all three hosts.

#### Baseline

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM (`:packages:feature-projects-presentation`) | 216 | **218** | 0, unchanged |
| iOS simulator (same module) | 216 | **218** | 0, unchanged |

+2 per host from the JUnit XML, cross-checked against `@Test` (`ProjectsUiJvmTest` 43 → 45,
`ProjectsUiIosTest` 42 → 44, `ProjectsUiAndroidHostTest` 43 → 45). The iOS and Android counts are
source-checked; the full host suites re-run at 24.5 behind the `:shared-ui` blocker described
under 24.1. The `NavigationHost` `activeSprint` wire lands on the Board tab but is unassertable
until `:shared-ui` compiles — same blocker; the wire is two lines.

### 24.3 A task added without an active sprint lands on the Kanban board

**Both done.** With no sprint running, the board host seeds the editor with
`initialSprintID = activeSprint?.id` (null) and the lane's status; `SaveWorkItem` writes both
through. Nothing needed fixing — §19 pins the sprint-carrying side (`WorkItemEditorModelTest` and
`ProjectUseCasesTest`), and `the board falls back to all project work items when no active sprint
exists` pins the filter's open side. What no test held was the **round trip**: the composition the
hosts actually perform, which is where a regression would most likely be introduced (scissoring
the sprint seed, as §19's bug did, or over-applying the filter).

- [x] **24.3.1** Write the failing test first: board with `activeSprint == null` → add task in a
      lane → item keeps its lane status, `sprintID == null`, and appears on the board, unchanged
      by the board screen's sprint filter.
- [x] **24.3.2** If already green, the deliverable is the mutation-checked pin; if red, fix the
      create path (`SaveWorkItem`, `WorkItemEditorModel.save`) until the invariant holds.

#### Test

`a task added from a sprintless board keeps its lane status and shows up on the board` in
`ProjectBoardModelTest` composes `ProjectBoardModel` and `WorkItemEditorModel` exactly as the two
hosts do — `initialStatus = WorkItemStatus.Todo`, `initialSprintID = board.activeSprint?.id` —
saves, advances the board's reload, and asserts all three halves: the item keeps its lane status,
keeps `sprintID == null`, and sits in the board's Todo column. It runs on all three hosts via
`commonTest` (the UI scenarios on the Android host — the unrelated 3 below — are unaffected).

#### Mutation

Two mutations, both caught, both red on no other test:

1. Dropping `initialStatus` from the editor's status init (`existing?.status ?: initialStatus
   ?: Backlog`) → the new task is created Backlog → fails `expected:<Todo> but was:<Backlog>`
   and the board column. Prior coverage missed this because every existing editor test creates
   with the default status.
2. Making the no-sprint filter hide sprintless items (`rawItems.filter { it.sprintID != null }`)
   → the Todo column is empty (silently, the save having "succeeded") → fails the board
   assertion naming all four empty columns. (The inverse filter — show only sprintless items —
   turns out to *preserve* the invariant and was rejected as a mutation for that reason.)

Restored, green on JVM, iOS simulator, and Android host. JVM module: 218 → **219**, 0 failures.

### 24.4 The backlog gains a Kanban segment

**All four done.** `Projections.kt` grew `BacklogSection.Kanban` and the `kanban` partition —
`WorkItemGrouping.boardColumns(items).flatMap { it.items }`, so the section reads the whole board
as rows in board order, then rank, and can never drift from the board tab's own grouping — with
`kanbanItemCount` / `kanbanIsEmpty` mirroring the existing two. `ProjectBacklogScreen` now shows a
three-option picker (Backlog / Kanban / Archive), an exhaustive `when` renders
`KanbanSectionList` and `EmptyKanbanSection`, and a Kanban row passes `onOpen` into
`WorkItemBacklogRow` so its tap opens the task and its secondary verb stays File away. No
`BacklogDestination` (`NavigationHost.kt:719-764`) change was needed: that destination already
wires `onSelect` straight to the editor, and the row hands it `onSelect(item.id)`.

The 24.4 blocker named in 24.1's baseline — `:shared-ui` could not compile because
`ShellScenarios.kt` referenced the presentation module's `BacklogSectionScenarios` across a
source-set boundary Gradle cannot see — was resolved the way 24.1 said 24.4 would: the six section
scenarios moved into `:shared-ui`'s `commonTest` (new `BacklogSectionScenarios.kt`), where all
three hosts can run them.

- [x] **24.4.1** `Projections.kt`: `BacklogSection.Kanban`; partition = `status.appearsOnBoard`
      (board order, then rank); counts/emptiness mirror the existing two.
- [x] **24.4.2** `ProjectBacklogScreen.kt`: options, titles, tags and the render branch — the
      exhaustive `when` made the compiler find every site; `KanbanSectionList` + empty state,
      rows = tap-to-open + File away (`WorkItemBacklogRow`'s new `onOpen`). `BacklogDestination`
      needed no change (its `onSelect` already reaches the editor).
- [x] **24.4.3** Strings: `backlog_section_kanban` = "Kanban", and `backlog_section_archived` = "Archive".
- [x] **24.4.4** Tests: partition assertions already in `ProjectionsTest.kt:154-240`; the six
      section scenarios moved into `:shared-ui`'s `commonTest` plus five new Kanban ones and the
      backlog-row-tap regression guard (thirteen in `ShellScenarios.projectBacklogScenarios`), and
      the three host files re-registered to the canonical thirteen-method mapping below.

#### Test

`BacklogSectionScenarios.kt` (shared-ui `commonTest`) holds the thirteen scenarios: the six
moved ones (archived switch, switch-back, closed-list order, section persist across the switch,
and the backlog's and archived's own empty states), five new Kanban ones (switch to the board
rows, board-order-then-rank, tap-to-open, file-away, Kanban's own empty state), and
`tappingABacklogRowOpensTheTask`, the regression guard for the row-tap contract described under
Mutation. Fixtures are
`shellWorkItem(number, status, rank)` on a fixed project (`keyPrefix = "AP"`, explicit ids via a
common `padStart` — `"%012d".format` is JVM-only), `backlogItems(count)`, `closedItems()` and
`boardItems()` (two Todo-lane items so rank earns its say). The board-order scenario reads each
row's top edge off the tree and requires a strict rise *in the projection's expected order*, so a
shuffled fixture (deliberate) never leaks its own order into the assertion.

The three host files now register the canonical thirteen methods `[0..12]` by index, fixing a
**pre-existing off-by-one**: the original list had seven entries while every host registered
`[0..7]`, so host `[4]`..`[7]` had run the *previous* lambda under the *next* name since the
scenarios were added — invisible while passing, and the reason the empty-state scenarios had
never actually exercised their own bodies. The old method
`theBacklogTabHasTwoSectionsAndTheArchivedSectionShowsClosedWork` merged into
`switchingToTheArchivedSectionShowsClosedWorkAndHidesBacklogRows`; its lambda still runs under
the new index 0.

#### Mutation

- **The moved tests caught a real production defect on their first green chase.** All three
  empty-state assertions failed with "could not find any node that satisfies: (TestTag =
  'backlog.emptyBacklog' / 'backlog.emptyKanban' / 'backlog.emptyArchived')" against the tree I
  dumped, which showed the empty state carrying `backlog.rows`. Root cause: on this Compose
  version a chained `Modifier.testTag(a).testTag(b)` keeps **a** — the callers tagged the shared
  list container and each empty branch re-tagged it, so the empty tag never won. Fixed by moving
  each list's tag onto the node that renders it and applying the empty tag to the empty state's
  own modifier. This was the ritual's red-first: the assertions failed against a live defect and
  went green only after the production fix.
- **The board-order scenario caught its own test-side bug first.** Reading tops in the *shuffled
  fixture order* failed "194.0 !< 80.0" even though the list was correct; iterating the
  projection's `kanban` order fixed the assertion. Then a deliberate production mutation —
  `items(projection.kanban.asReversed())` — went red with "536.0 !< 422.0"; restored, green.
- **The last-row scroll scenario was a second `setContent` too far.** It ran
  `everyTabScrollsBehindTheTabBar()` (itself a registered test) mid-body, whose `setUpShell`
  called `setContent` a second time — wiping the shell out from under its Settings step on every
  host, and throwing Android's `ComponentActivity` "has already set content". Removed the call;
  the scenario is one `setContent`, green on all three hosts, and the per-tab region geometry is
  still covered by the standalone scenario.
- **The backlog-row tap was a real user-facing bug, and its regression guard is mutation-clean.**
  A backlog row's body used to be `clickable { onOpen ?: onPutOnBoard }`, so tapping *anywhere*
  on an untriaged row silently put the item on the board — it vanished from the list. The row
  body no longer triages: `BacklogSectionList` passes `onOpen = { onSelect(item.id) }` so a
  backlog row's tap opens the task (the same contract the Kanban rows carry), archived rows get
  the inert `onOpen ?: {}` fallback, and "Put on board" is its own explicit button shown only
  when `item.status == WorkItemStatus.Backlog`. The new `tappingABacklogRowOpensTheTask` asserts
  both halves: the tap answers with the id *and* the put-on-board action is still displayed.
  Two mutations, both red with the defect named: removing the `onOpen` wiring from
  `BacklogSectionList` failed "expected the tapped backlog row to open the task ... but
  was:<[]>" (the tap fell through to a no-op), and reverting the button rule to `if (onOpen ==
  null)` hid the action — red on "putOnBoard ... could not find any node". Mutating the
  `onOpen ?: onPutOnBoard` fallback alone is no longer a reachable defect — backlog rows always
  pass `onOpen`, so the fallback is inert there, which is why the section wiring is the mutation
  that must be checked.

#### Baseline

The deciding command (`jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports
:androidApp:compileDebugKotlin`) now runs with `:shared-ui` compiling, so the whole-command
baseline is measured here rather than deferred to 24.5. Counts from the JUnit XML, cross-checked
against `@Test`.

| Host | Before | After | Failures |
| --- | --- | --- | --- |
| JVM | 1303 | **1332** | 5, unchanged set |
| iOS simulator | 1275 | **1304** | 5, unchanged set |
| Android host | red, ~17 documented | **788 tests, 31 failures** | viewport / Context / Build-mediated set, unchanged classes |

The five per host are the same documented pre-existing set (`SeedingTest` ×3 in
`:packages:feature-projects-data`, `aChosenThemeOutlivesTheProcess` and `theBoardReturnsToTheList`
in `:shared-ui`). The Android host's 31 are all `testAndroidHostTest` layout/environment failures
— design-system 14 rasterised-pixel gates, feature-settings-presentation 7, feature-projects-
presentation 3, shared-ui 7 — every one in a class `AGENTS.md` already records as red for
documented reasons; this change took shared-ui's count from 9 to 7 (two new-portable scenarios
fixed) and no failure added.

The first iOS read of these totals came off 1294 because only part of the iOS simulator matrix
had re-executed at that moment (the same stale-XML trap the last-row fix hit). Re-measured clean
with `--rerun-tasks`, iOS read **1303** (before 24.6 added its one scenario, which is why the
table's After column says 1304); 1294 was the undercount, not a regression.

### 24.5 Baseline and full matrix

**Folded into 24.4.** Once `:shared-ui` compiled, there was no reason to defer the deciding
command, so 24.4's `#### Baseline` measured it: JVM 1303 → **1332**, iOS 1275 → **1304**, both
with the same 5 pre-existing failures; Android host 788 / 31, all documented classes. `AGENTS.md`'s
test-host table and the shared-ui counts above were updated in the same change as the test
additions, per the "baseline in the same change" rule.

### 24.6 File Away in the editor closes the sheet

**Defect.** `WorkItemEditorDestination`'s `onFileAway` ran `model.fileAway()` and never
dismissed the sheet, while `onSave` and `onCancel` both did — so tapping File Away archived the
item and left a modal standing over a board that no longer showed it, still offering edits.

**Fix.** `shared-ui/src/commonMain/.../NavigationHost.kt:1111` now mirrors `onSave`'s close:
on a successful `fileAway()` the destination sets `isSheetVisible = false` (or `onDismiss()` when
expanded); on `false` it stays open, so an archive the store refused keeps its error banner
readable.

**Test.** `filingAwayInTheEditorClosesTheSheet` in `ShellScenarios` (registered on all three
hosts) pushes the editor the way a user does — project row, then card (`WorkItemCardTag`) — and
asserts both halves: the store saw the `Closed` write, and the `WorkItemEditorDestinationTag` is
gone with the board back on screen.

**Mutation.** Reverting the dismiss only went red with "expected file-away in the editor to close
the sheet, not leave it standing over the board"; restored, green. A second mutation this test
would not have caught had the button been off-screen: the sheet's action row is its last child,
and Robolectric's short viewport sat it under the fold, where the click landed on nothing — the
scenario `performScrollTo()`s the button first, and the Android host stays at its documented 7.

**Baseline.** shared-ui is now 49 JVM / 47 iOS / 47 Android tests, and the whole-command `After`
column in 24.4's table already reads the post-24.6 numbers (**1332 / 1304 / 788**) because the
"baseline in the same change" rule updates that table too — the one-scenario-per-host growth from
24.4 is this change's, not the backlog work's.
</details>
