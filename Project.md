# Project.md

How this project is built, run, and verified. Companion to [`docs/DECISIONS.md`](docs/DECISIONS.md), which is the
design record — why each decision was made. This file is the short operational answer to "how do
I build and run this, and what will bite me". Host test details live in
[`docs/TEST-HOSTS.md`](docs/TEST-HOSTS.md). The finished port log was removed from the tree
(it was frozen archaeology); it is recoverable from git history.

---

## Requirements

JDK 25. This machine has exactly one JDK and `java_home` cannot find it, so **every** Gradle
invocation needs:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Without it the build dies immediately with `Unable to locate a Java Runtime`. The scripts under
`scripts/` export it for you, so prefer them over a bare `./gradlew`.

---

## The command that decides whether you are done

```bash
scripts/check.sh          # fast: JVM + graph + baseline + summary
scripts/check.sh full     # all three hosts + baseline; required for UI work
```

The script exports the JDK, runs Gradle with `--continue`, summarizes failures, and accepts only
tests named in `test-known-failures.json`. Counts and identities are generated into
`test-baseline.json`. Policy and host details: [`docs/TESTING.md`](docs/TESTING.md); rollout:
[`docs/TEST-HARNESS-PLAN.md`](docs/TEST-HARNESS-PLAN.md).

---

## Running on iOS

Two build systems meet here, and they do not know about each other. Gradle compiles the Kotlin
and links a framework; Xcode compiles a four-line SwiftUI shell and links that framework **by
path**.

### The one rule

The iOS app runs against whatever is in:

```
shared-ui/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework
```

Xcode does not ask Gradle for it. It opens that path and links whatever is there. If the file is
out of date, **nothing fails** — Xcode resolves `import ComposeApp`, the app launches, and the
only symptom is behaviour that contradicts the source you are reading.

### So the link runs in the Xcode build phase

`iosApp/project.yml` has a `preBuildScripts` phase named "Build ComposeApp" that runs:

```sh
./gradlew :shared-ui:linkDebugFrameworkIosSimulatorArm64 \
          :shared-ui:syncComposeResourcesForIos --console=plain
```

It runs on **every** path — Xcode, `xcodebuild`, and Android Studio alike. Do not reintroduce a
branch that skips part of it. See [The bug that was here](#the-bug-that-was-here) for what that
cost.

### Checking the framework is actually fresh

```bash
./gradlew :shared-ui:verifyIosSimulatorArm64FrameworkIsFresh
```

Fails when the linked binary is older than the newest `.kt` under `shared-ui/src`, and names the
file. Takes milliseconds; run it whenever the simulator's behaviour disagrees with your source and
you have no other explanation.

### Manual fallback

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared-ui:linkDebugFrameworkIosSimulatorArm64
```

Then build from Xcode. If you have changed `project.yml`, run `xcodegen generate` from `iosApp/`
first — the `.xcodeproj` is generated and never hand-edited.

### The bug that was here

`project.yml` used to skip the link when `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES`, on a comment
that asserted "Android Studio's KMP run configuration builds the framework before invoking Xcode,
so the link below is redundant on that path."

**That was false.** `.idea/runConfigurations/TaskTracker.xml` declared no Gradle before-run task at
all — only Xcode's own `BuildBeforeRunTask`. So on the IDE path the link never ran and only the
resource sync did. Two build systems each assumed the other was doing the work, and nothing
complained.

The result was an app built against a framework binary hours older than its sources, containing
none of the day's work. Symptom: behaviour that contradicted the code, and a crash that
reproduced identically every single time.

Why the obvious fixes did not work, and neither of them was wrong on its own:

| Attempt | Why it changed nothing |
| --- | --- |
| Delete the installed app | Acted on the *installed copy*, not the *built artifact*. The stale binary was still on disk being relinked into a fresh install. |
| Re-run `xcodegen generate` | Regenerates the project file. The project file was never stale — it pointed correctly at a framework that was. |

Neither operation touches the artifact that was actually wrong. That is the general shape of this
bug class: **fixing a symptom at a layer below the cause**. When a build artifact is consumed by
path, verify the artifact.

### Why `.idea/` is not the fix

`.idea/` is gitignored (`.gitignore:21`) and nothing in it is tracked. A run-configuration edit
there would have fixed one machine and vanished for the next person, unreviewable. The fix had to
be in `project.yml`, which is tracked, which is why the comment explaining the false assumption
was rewritten rather than deleted — so the next person reading it can see it was checked.

---

## Running on Android

Standard: open `androidApp` as a run configuration, or `./gradlew :androidApp:installDebug`. The
Gradle build compiles the Kotlin directly, so there is no artifact-by-path problem and none of the
above applies.

---

## The backend URL

All three shells reach the Go backend, and each reads its address from its own platform's
configuration surface through the single resolver `platformApiBaseUrl` in `:composition-root`.
When nothing overrides it, each falls back to the local server — `http://localhost:8080` where the
host network is shared, `http://10.0.2.2:8080` on Android, where that alias is how an emulator
reaches the machine running the server.

| Shell | Configuration surface | Override |
| --- | --- | --- |
| Desktop (JVM) | environment variable | `TASKTRACKER_API_URL=https://… ./gradlew :shared-ui:runDesktop` |
| iOS | `Info.plist` key `TaskTrackerApiBaseUrl` | the `TASKTRACKER_API_BASE_URL` build setting, set in `iosApp/project.yml` |
| Android | manifest `<meta-data android:name="com.tasktracker.API_BASE_URL">` | `-Ptasktracker.apiBaseUrl=https://…` (or `gradle.properties`) |

Two consequences worth knowing. First, naming a URL binds the real `HttpAuthRemote` /
`HttpSyncTransport` and disables the offline sample seed — that is the point, but it means a
launched app now expects a reachable server rather than working standalone. Second, Android blocks
cleartext HTTP by default, so `androidApp/src/main/res/xml/network_security_config.xml` permits it
for the two local hosts only; a deployment whose server is plain HTTP must add its host there.

---

## Tests

| Host | Task | State |
| --- | --- | --- |
| JVM | `jvmTest` | fast feedback; known failures named in `test-known-failures.json` |
| iOS simulator | `iosSimulatorArm64Test` | slower; needed for anything touching `expect`/`actual` or rendered pixels |
| Android host | `testAndroidHostTest` | Robolectric; known failures named in `test-known-failures.json` |
| Desktop (the JVM app) | `:shared-ui:runDesktop` | **not a test host** — Desktop *is* the `jvm` target, so it is the row above; see the note below |

**Counts and failure identities are generated.** The paragraphs below are historical measurement
lore and predate the named-baseline gate; the current numbers live only in `test-baseline.json` and
the current allowlist in `test-known-failures.json`. Phase 4 of `docs/TEST-HARNESS-PLAN.md` moves
this lore to `docs/DECISIONS.md`.

Those totals are now **generated**: `test-baseline.json` is written by `scripts/test-baseline.py`
from the JUnit XML, and `scripts/check-baseline.sh` fails if the file and the build disagree. The
count is per module and de-duplicated across the one-deep (`composition-root`, `shared-ui`) and
two-deep globs, which do not overlap, and cross-checked against the `@Test` declarations in the
source rather than a `grep -c` (which over-counts the backticked mentions in KDoc). The last hand
measurement, 2026-10-15 in one whole `./gradlew check`, read JVM **1588**, iOS **1539**, Android
**1283**, with 12 / 12 / 34 failures, name for name — the figures the generated file was seeded
from. The three are one run's output, not an accumulation: 5.6 added **nine JVM-only** tests
(`:packages:user-defaults` and `:shared-ui` `jvmTest`, because the code under test is
`jvmMain`), and the **+4 per host** over the 1575 / 1535 / 1279 that 5.4 recorded are 5.5's
modal scenarios, which landed without a re-measurement here. 5.7 is that re-measurement.

**Every one of the three figures recorded before those runs was stale, in the same direction.** The
recorded 1534 / 1494 / 1238 had drifted from 1571 / 1531 / 1275 — JVM +37, iOS +37, Android +37.
None of that was section 5: the desktop-layout work accounts for exactly **+10 per host**
(6 in `:packages:design-system`'s `commonTest`, 4 in `:shared-ui`'s), and the remaining +27 came
from the sync-transport work that landed on this branch first. The Android host's *failure* count
did not move at all: `HEAD` measures 1265 tests / 34 failed and this branch measures 1275 / 34,
so all ten new tests pass there and the nine `shared-ui` failures are the same nine either way
(verified by re-running with `setUpShell`'s width default reverted, which produces an identical
list). A count that is wrong in the same direction on three hosts at once is a baseline that
stopped being re-measured, not thirty-seven tests that appeared quietly.

### Desktop now has a *layout* dimension the JVM host can drive

Section 5 did not add a test host. It added the ability to ask the shell for a width, and
`ShellScenarios.setUpShell(width = …)` already had the hook — what changed is that the shell's
chrome now follows it. `aNarrowShellDrawsTheBarAndNoRail`, `aWideShellDrawsTheRailAndNoBar`,
`theRailIsALeadingColumnRatherThanAStretchedOne` and `exactlyOneRailDestinationIsMarkedSelected`
are `commonTest` bodies with three entry points each, so all three totals move.

**One width and one consequence worth carrying.** `setUpShell`'s `width` now defaults to
`PHONE_WIDTH` rather than `null`. It used to mean "whatever viewport this host gives the root",
which was invisible while the shell drew one chrome at every width and became twelve failing
scenarios the moment it did not. The Android host is the other half of that: a fixed width is a
request, clamped by the parent's constraints, and Robolectric clamps 1200dp below the expanded
threshold, so the three wide-width scenarios skip there (they print, and register as passes) while
the JVM and iOS hosts run them.

The Desktop work added 16 tests, and where they land is worth knowing when the counts move:
7 in `:packages:user-defaults`'s `jvmTest`, 5 in `:packages:keychain`'s `jvmTest` and 3 in
`:shared-ui`'s `jvmTest` are **JVM-only**, because the backends and the entry point they cover
exist only on that target; the 1 resource test is
per-host (a scenario body plus a `jvmTest`, an `iosTest` and an `androidHostTest` entry point),
so it moves all three totals. `:packages:keychain`'s 3 keychain round-trip tests are **opt-in** — they write to the real
login keychain, so they run only under `TASKTRACKER_KEYCHAIN_INTEGRATION=1`, and they register
as passes in a default run without having run. That run is the one that matters for the JNA
binding; the default suite says nothing about it. The `commonTest` source sets hold **1389**
`@Test` annotations (counted as line-leading `@Test` across `**/src/commonTest/**`, which
excludes the backticked mentions in KDoc that make a `grep -c` over-count), and `:shared-ui`'s
shell scenarios are **58**, counted per host: the `@Test` entry points in
`TaskTrackerAppJvmTest` / `TaskTrackerAppIosTest` / `TaskTrackerAppAndroidHostTest`, each one
wrapping a shared `fun` body that carries no `@Test` itself. Both were re-counted in 5.7; the
figures they replace (1729 / 42) could not be reproduced from the source, and 1729 cannot even
be a `commonTest` count — the whole JVM host runs **1588** tests and every `commonTest` body
runs on it, so the `commonTest` figure is capped below that.

### Desktop is not a fourth test host

Desktop and the JVM test host are one Gradle target and one set of source sets: the app is
`shared-ui/src/jvmMain` and its code is tested by `shared-ui/src/jvmTest`, which is the `jvm`
target's test source set. So there is no Desktop twin of `iosSimulatorArm64Test` to run — a fourth
column would execute the same tasks over the same code and report the same numbers twice — and
`:shared-ui:runDesktop` is a launch task (`JavaExec`), not an assertion.

What the Desktop *app* does differently from the test host is a single flag.
`startDesktopProcess` marks the process as the Desktop app before the shell opens, and both JVM
storage seams choose their backend from that flag when they construct it; on the test host the
flag stays clear, so no scenario writes to the developer's real preferences file or login
keychain. Deleting the assignment used to leave the suite byte-identical while the shipped app
kept its session and its appearance in memory alone — the same shape as the `CachedProfile` gap
above, and the reason `DesktopEntryPointJvmTest` (3 tests, `jvmTest`) enters the seam and asserts
the flag is already set at the moment the shell opens, and that the preferences router therefore
hands the shell the file-backed backend rather than the test host's stub.

The 12 failures on JVM and iOS are `SeedingTest` ×3 (`:packages:feature-projects-data`),
`theLongAccentCaptionWrapsInsteadOfClipping` + `theUserDetailsSectionIsOnThePage`
(`:packages:feature-settings-presentation`), and seven `:shared-ui` shell scenarios
(`theSettingsTabEndsTheSession`, `aLockedSprintOffersNoTaskEdits`,
`aChosenThemeOutlivesTheProcess`, `theLastRowOfEachListScrollsClearOfTheTabBar`,
`theBoardReturnsToTheList`, `filingAwayInTheEditorClosesTheSheet`,
`tappingASprintTaskRowOpensTheEditor`). All twelve were re-verified pre-existing in October
2026: the shared-ui seven were reproduced at commit `acaef2a` in a clean worktree with none of
the working tree's uncommitted work, so neither the recurrence feature nor the user-directory
work caused them. See `docs/DECISIONS.md`, measurement lore.

The 34 on the Android host are Compose layout and rasterisation failures, not behaviour:
Robolectric's default viewport is far smaller, so content inside a `LazyColumn` or a
`horizontalScroll` row exists but is not displayed, and its rasteriser disagrees with the
other two hosts on the pixel gates. 14 are in `:packages:design-system` (the `DateField*` and
`StoryPoints` contrast/raster gates), 8 in `:packages:feature-settings-presentation`,
3 in `:packages:feature-projects-presentation`, and 9 in `:shared-ui`. Measured 2026-10-12:
the `:shared-ui` failures were re-run with the comments feature's editor wiring reverted and
the identical set failed, so none of the 34 belongs to that feature. The rise from the previously
recorded 854/17 is tests that landed on `main` after that measurement, not new breakage.

A test that renders Compose and reads pixels must call `thisHostRasterisesText()` — an `expect` in
`:packages:design-system`'s `commonTest` with actuals in `jvmTest`, `iosTest`, `androidHostTest`.
Without it the Android host fails on layout, not behaviour. Be aware the skip registers as a
*pass* on that host, not a skip. The same pattern has a second instance, `thisHostOpensRoomStores()`
in `:composition-root`, which is the same fact for Room rather than for text: `sqliteJni` is not on
the Android host's library path, so a scenario that needs a store asks first.

### Compose resources

`:packages:design-system` is the only module that configures `compose.resources`, and every string
lives there. A new `stringResource` key needs `./gradlew :packages:design-system:generateComposeRes`
if the generated accessors do not appear.

### Where a test goes, and why the scenarios are not `@Test`s

The source-set shape follows from one constraint: an Android-host Compose UI test only runs under
`@RunWith(RobolectricTestRunner::class)`, and a runner annotation is not expressible in
`commonTest` without taking the JVM and iOS compilations down with it. So the **assertions** live in
`commonTest` and each host contributes only entry points.

For **behaviour** scenarios — used by all ten scenario objects: `ShellScenarios` (25),
`SignInScenarios` (7), `SettingsScenarios` (7), `ProjectsListScenarios` (13),
`WorkItemCardScenarios` (8), `BoardLaneScenarios` (7), `BoardColumnScenarios` (5),
`ArchiveRoundTripScenarios` (4), `ProjectBacklogScenarios` (2) and `GlassIntensityScenarios` (6):

- `commonTest` holds an `object` of scenarios, each a `suspend ComposeUiTest.() -> Unit` value —
  a value rather than a function because `setContent` and `waitForIdle` are suspend, so a
  non-suspend function type is not assignable to a suspend one.
- `jvmTest` / `iosTest` / `androidHostTest` each hold a thin class of `@Test fun name() =
  runComposeUiTest(block = Scenarios.name)`. The Android one is the only one that carries
  `@RunWith(RobolectricTestRunner::class)` (plus `@Config(sdk = [34])` and
  `@GraphicsMode(NATIVE)`).

One copy of the behaviour, three platforms, no duplicated expectations.

**Pixel tests in `:packages:design-system` are the exception, and they are simpler.** They are
plain `@Test`s in `commonTest` with no host entry points at all:

```kotlin
@Test
fun theBottomOfTheSliderIsAnOpaqueNeutralRatherThanAbsent() = rasterising {
    runComposeUiTest { /* read pixels */ }
}
```

`rasterising { }` is the guard *and* the composition — it asks `thisHostRasterisesText()` before
calling `runComposeUiTest`, because on the Android host the failure happens in `runComposeUiTest`'s
own environment setup, so a test that called it first would die instead of reaching the branch that
explains why. There are five such files (`GlassIntensityRenderTest`, `TabIconsRenderTest`,
`TabLensContrastTest`, `TabLensCornerTest`, `AvatarContrastTest`). The scenario-object split is not
needed here because these assert on pixels rather than on a shell, and the guard already handles the
host difference.

Which source sets each module actually has:

| Modules | Source sets |
| --- | --- |
| most (`core`, `domain`, `routing`, `di`, `keychain`, `user-defaults`, `network`, and the domain/data modules) | `commonTest` only |
| `database`, `sync` | `commonTest` + `jvmTest` — the host-only suites (on-disk store location, the reflection guard that `SessionRecord` holds no token, the Room-backed sync integration) |
| `design-system`, the three `*-presentation` modules, `composition-root`, `shared-ui` | `commonTest` + `jvmTest` + `iosTest` + `androidHostTest` |

`composition-root` is the odd one: its `jvmTest`/`iosTest`/`androidHostTest` carry `TestPlatformHost`
*and* a scenario list, because five of its graph assertions need a real `Context` on Android. Those
now live in `HostedGraphScenarios` with per-host entry points (`HostedGraphScenariosTest` on JVM and
iOS, `HostedGraphAndroidHostTest` under Robolectric), so the module passes on all three hosts. One
of the five — `the outbox is left alone` — still cannot open its Room store on the Android host
(`sqliteJni` is not on that target's `java.library.path`, the same limit that disables
`:packages:database`'s Android host tests), so it asks `thisHostOpensRoomStores()` and skips there
while running on JVM and iOS.

**Deciding where a new test belongs:**

1. Pure logic with no composition — `commonTest`, plain `@Test`.
2. A **pixel** measurement in `:packages:design-system` — `commonTest`, plain `@Test fun … =
   rasterising { runComposeUiTest { … } }`. No host entry points; the guard handles the host split.
3. Compose **behaviour** asserted through the shell or a screen (a control reaches its model, a node
   is shown or hidden, a modifier is present) — a `commonTest` scenario object plus per-host entry
   points, because the Android host needs a runner annotation `commonTest` cannot carry. No
   `rasterising`, since nothing reads a pixel.
4. Needs a real `Context` (a Room store, a `ConnectivityManager`) — the same scenario-object split
   as 3, with the Android entry point carrying `@RunWith(RobolectricTestRunner::class)`, which is
   what creates the sandbox `testPlatformHost()` reads. `:composition-root`'s `HostedGraphScenarios`
   is the worked example.
5. Needs a real file, reflection, or a JVM-only API — `jvmTest` or `androidHostTest`.

Rule 1 also decides the *whether*: per `AGENTS.md`, prefer a structural assertion over a rendered
one unless the property cannot survive being read off the modifier chain — a fill composited under
another translucent layer, a silhouette, a blur, or a hairline needs pixels; a token's value or a
modifier's presence does not.

Two rules that bite at step 1 and are not visible from a green JVM run:

- **Test names are backtick full sentences with no commas or periods** — Kotlin/Native rejects
  both, and only when the *common* source is compiled for Native, so a name can look fine under
  `:module:jvmTest` and fail under `:module:iosSimulatorArm64Test`.
- **Every `expect` needs a `jvm` actual**, because the convention plugin adds a `jvm()` host target
  and runs `commonTest` there.

---

## When something renders differently on iOS than on the JVM

Measure it on both. They disagree: the same token on the same backdrop measured 5.37:1 on the JVM
and 3.98:1 on iOS, because iOS rasterises a small glyph's stroke at roughly 85% coverage. Tuning
to one host ships a defect to the other. `tools/inspect_frame.py` reads a PNG with no third-party
dependencies and prints geometry, contrast, and an ASCII map.

Rendered pixel assertions belong in a test whose name says what is being measured. See
`docs/DECISIONS.md` measurement lore for a case where the assertion looked reasonable and encoded the defect as correct.

---

## Where things are

Gradle modules are the architecture boundary; see
`build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts` for the checked
dependency table. `:composition-root` binds implementations, `:packages:design-system` owns all
shared visual tokens and Compose resources, and shared behaviour lives in `commonMain`.

---

## The packages, one by one

Every entry below is one Gradle module and one Kotlin package. The package prefix and the module
it belongs to are declared together in `packageOwnersTable` in
`tasktracker.architecture.gradle.kts`, and `verifyModuleImports` fails the build if a file imports
a package its module is not allowed to see. "Structure" describes the source sets the module
actually uses; a module that names no platform seam has only `commonMain`/`commonTest` plus the
`jvm` host target every module gets from the convention plugin.

### How the packages depend on each other

Every arrow below is one entry in `allowedLibraryTable`, and `verifyArchitectureGraph` fails the
build on an edge that is not here. Read it bottom-up: a module may only depend on modules in the
layers beneath it.

```
layer 0   :packages:core                          (no project dependencies at all)
          :packages:domain                        ← core

layer 1   :packages:design-system                 ← core
          :packages:keychain                      ← core
          :packages:user-defaults                 ← core
          :packages:di                            ← core
          :packages:routing                       ← core, domain
          :packages:database                      ← core, domain
          :packages:network                       ← core
          :packages:sync                          ← core, database, network

layer 2   :packages:feature-projects-domain       ← core, domain
          :packages:feature-auth-domain           ← core, domain
          :packages:feature-settings-domain       ← core, domain
          :packages:feature-projects-data         ← core, domain, database, network, sync,
                                                    keychain, feature-projects-domain
          :packages:feature-auth-data             ← core, domain, database, network, sync,
                                                    keychain, user-defaults, feature-auth-domain
          :packages:feature-settings-data         ← core, domain, database, network, sync,
                                                    feature-settings-domain

layer 3   :packages:feature-projects-presentation ← core, domain, design-system, routing, di,
                                                    feature-projects-domain
          :packages:feature-auth-presentation     ← core, design-system, feature-auth-domain
          :packages:feature-settings-presentation ← core, domain, design-system, routing, di,
                                                    feature-settings-domain

layer 4   :composition-root                       ← every :packages module
          :shared-ui                              ← all of layer 3, the three feature domains,
                                                    core, domain, design-system, routing, di,
                                                    user-defaults, composition-root
          :androidApp                             ← composition-root, shared-ui, di
```

Five edges are **deliberately absent**, and each one is a bug the table exists to prevent:

- **presentation → its own data** — this is the edge that would let a screen reach Room directly
  instead of going through a use case. It is the reason `AuthEnvironment` types the port as
  `AuthSessionPort` and says so.
- **`:shared-ui` → any data module** — the shell resolves ports, so it can ask for projects but
  cannot construct a repository.
- **`:composition-root` → `:shared-ui` / `:androidApp`** — it stops at the libraries; the shell
  sits above it.
- **`:packages:network` → `:packages:domain`** — a transport that names a domain type starts
  deciding what a request *means*.
- **`:packages:sync` → `:packages:domain`** — the engine routes opaque payloads and never learns
  what a `WorkItem` is.

`verifyModuleImports` is the second check and catches the other route: a dependency added to a
module *between* this one and the target, then re-exported. Both are exact-set, so a declared
dependency with no matching table edit fails just as loudly as a missing one.

### Cross-cutting — the bottom of the stack

**`:packages:core`** (`com.tasktracker.core`) — the shared primitives everything else is built
on: `AppError` (a thrown sealed class, not a returned value), `Identifiers` (`value class` over
`Uuid`), `Broadcaster`, `DateCoding`/`JSONCoding`, `JWT` claims inspection, `Log`, `DateProvider`
and `TextLength` (the grapheme-count primitive used by every length rule).
*Structure:* pure Kotlin, **no project dependencies at all** — not even on the design system, which
is why error text lives here rather than in `strings.xml`.

**`:packages:domain`** (`com.tasktracker.domain`) — the business vocabulary and rules that are
not specific to one feature: `Entities` (`WorkItemStatus`, `Priority`, `Project`, `WorkItem`,
`StatusChange`), `ProjectRules`, `AuthRules`. Statuses expose `wireValue`, never a display label.
*Structure:* `commonMain` only, depends on `:packages:core` alone. It declares its own
`kotlinx-serialization-json` dependency because a `commonMainImplementation` edge is not `api`.

**`:packages:design-system`** (`com.tasktracker.designsystem`) — every shared visual token and all
Compose resources: `Colors` (an `expect`/`actual` per platform), `Accent`, `ThemeScheme`, the
Liquid Glass primitives (`Glass`, `GlassIntensity`), `Typography`, `Spacing`, `TabBar`/`TabIcons`,
`SettingsIcons`, `Avatar`, `Slider`, `Swipe`, `Insets`, `Brand`, `BottomSheetScaffold`, `Previews`.
*Structure:* it is the **only** module that configures `compose.resources`, so all strings live in
`commonMain/composeResources`. Platform colour actuals sit in `androidMain`/`iosMain`/`jvmMain`,
and `commonTest` carries the raster-support `expect` (`thisHostRasterisesText`) with actuals in
`jvmTest`, `iosTest` and `androidHostTest`. Its tests are the pixel/contrast measurements.

**`:packages:routing`** (`com.tasktracker.routing`) — app-wide navigation as pure state:
`Routes` (`RootDestination`, `AppTab`, `AppRoute`), the hand-rolled `Router` behind one immutable
`RouterState`, and the pure-function `DeepLinkParser` for the `tasktracker://` scheme.
*Structure:* `commonMain` only, **no Compose dependency** — it cannot hold composables, so
`:shared-ui` renders it.

**`:packages:keychain`** (`com.tasktracker.keychain`) — secure byte storage: the `KeychainAccess`
interface, the typed `KeychainStore` over `KeychainKey`, `InMemoryKeychain` (with fault injection
for tests), and the `systemKeychain(host)` factory.
*Structure:* a genuine platform seam — `SystemKeychain` in `iosMain` (Security framework),
`AndroidKeychain` in `androidMain` (AES-GCM via `AndroidKeyStore`), an in-memory `jvmMain` actual.
The platform backends are compile-verified only.

**`:packages:user-defaults`** (`com.tasktracker.userdefaults`) — non-sensitive preferences:
the `PreferencesBackend` byte layer, `DefaultsKey`, and the typed `UserDefaultsStore` with its
`hasValue` distinction between "chose the default" and "never touched it".
*Structure:* same shape as the keychain — four `PreferencesBackend` actuals (`NSUserDefaults`,
`SharedPreferences`, JVM `Properties`, in-memory) with the typed store shared in `commonMain`.

**`:packages:di`** (`com.tasktracker.di`) — a thin dependency-injection seam: the `Resolver`
interface, `KoinResolver`, and `DiException`.
*Structure:* `commonMain` only, depends on `:packages:core`. It exists so features depend on a
capability rather than on Koin directly.

### Infrastructure — one layer above core

**`:packages:database`** (`com.tasktracker.database`) — the Room KMP store and the outbox: entity
and DAO declarations, `Transactions` (`useWriterConnection` + `IMMEDIATE`, with rollback on any
throw), `OutboxQueue`/`RoomOutboxQueue`, `SyncMetadataStore`, `DatabaseConfiguration`.
*Structure:* the largest module and the one with the most platform seams. `PlatformBuilders`,
`PlatformPaths` and `PlatformDispatch` are `expect`/`actual` across `androidMain`/`iosMain`/`jvmMain`
(Android needs a `Context`; `Dispatchers.IO` is `internal` in common). `schemas/` is committed so a
migration diff shows in review. Android host tests are disabled here — the builder needs a real
`Context`, which a host JVM cannot produce.

**`:packages:network`** (`com.tasktracker.network`) — the transport: the `HttpClient` interface
(so features never see Ktor), `KtorHttpClient`, `HttpRequest`/`HttpResponseSummary`,
`HttpErrorMapping`, the `AuthInterceptor` refresh-and-replay chain, `RequestInterceptor`,
`RetryPolicy`, and `NetworkMonitor`.
*Structure:* depends on `:packages:core` only — it must never name a domain type. Platform seams
are the HTTP engine (`OkHttp` on JVM/Android, `Darwin` on iOS) and the connectivity watcher
(`NetworkCallback`, `NWPathMonitor`, a JVM stub).

**`:packages:sync`** (`com.tasktracker.sync`) — the offline-first engine: `SyncEngine`,
`SyncCoordinator`, the `SyncTransport` port with `HttpSyncTransport` and `InMemorySyncTransport`,
plus `SyncScope`, `SyncStatus`, `SyncConfiguration`. It routes opaque payloads and never learns
what a `WorkItem` is.
*Structure:* `commonMain` on `:packages:core`, `:packages:database`, `:packages:network`;
`jvmTest` holds the Room-backed integration suite.

### Feature modules — each is a domain / data / presentation triplet

The three feature families all follow the same split, and the split is the architecture: a domain
module holds ports and use cases, a data module implements those ports, and a presentation module
renders UI against its **own domain** and never against data.

**`:packages:feature-projects-domain`** (`com.tasktracker.featureprojectsdomain`) —
`ProjectRepositoryPort`, `ProjectUseCases`, `Projections`, `WorkItemPreview`, `ArchiveWorkItem`.
*Structure:* `commonMain` on `core` + `domain`; the port is what every other layer sees.

**`:packages:feature-projects-data`** (`com.tasktracker.featureprojectsdata`) —
`RoomProjectRepository`, `RecordMapping`, `LocalDataSyncSink`, `ProjectChangeBus`, `SampleData`
(the offline seed).
*Structure:* the widest dependency set of the triplet — core, domain, database, network, sync,
keychain and its own domain. It is the only layer that knows both the port and Room.

**`:packages:feature-projects-presentation`** (`com.tasktracker.featureprojectspresentation`) —
the screens and their models: `ProjectsListScreen`, `ProjectBoardScreen`, `ProjectBacklogScreen`,
`ProjectArchiveScreen`, `ProjectEditorScreen`, `WorkItemEditor`, `ProjectPickerSheet`,
`NewProjectSheet`, the row/card components, and a `*Model` per screen.
*Structure:* `commonMain` on core, domain, design-system, routing, di and its own domain module.
Tests live in `commonTest` as shared scenarios plus per-host entry points in
`jvmTest`/`iosTest`/`androidHostTest`.

**`:packages:feature-auth-domain`** (`com.tasktracker.featureauthdomain`) — `AuthSession` (the
port), `SignInUseCase`.
*Structure:* `commonMain` on `core` + `domain`.

**`:packages:feature-auth-data`** (`com.tasktracker.featureauthdata`) — `AuthRepository`,
`TokenVault` (the `AuthSessionDelegate` the network layer declared), `SessionStateBus`,
`HttpAuthRemote` and `MockAuthRemote` (so the whole flow runs offline).
*Structure:* `commonMain`; the mock remote is production code, not test code, deliberately.

**`:packages:feature-auth-presentation`** (`com.tasktracker.featureauthpresentation`) —
`SignInView`, `SignInModel`, `AuthEnvironment`, `PrimaryButton`, `LabelledField`, `ErrorBanner`,
`AuthPreviewData`.
*Structure:* `commonMain` on core, design-system and its own domain; host entry points in
`jvmTest`/`iosTest`/`androidHostTest`.

**`:packages:feature-settings-domain`** (`com.tasktracker.featuresettingsdomain`) — `SettingsPort`,
`SettingsVocabulary`, `SyncNow`. `SyncActivity`'s two count rows are **disjoint**: `outstandingCount`
is the live rows still waiting, `stuckCount` is the dead ones the server refused permanently. Both
derive from `SyncReport`'s `queueDepth`/`deadLetterCount`, and `queueDepth` deliberately includes the
dead rows because it mirrors the outbox — so the *waiting* row subtracts them. Showing the raw depth
there made one dead row render as both "Waiting to sync: 1" and "Could not be sent: 1", and the first
could never clear, because a dead row is never claimed again and every subsequent cycle correctly
found nothing to do.
*Structure:* `commonMain` on `core` + `domain`, plus a `FakeSettingsPort` in `commonTest` so other
modules can drive the screen.

**`:packages:feature-settings-data`** (`com.tasktracker.featuresettingsdata`) —
`LiveSettingsService`, the adapter between the platform connectivity watcher and the feature's own
vocabulary.
*Structure:* `commonMain`; it is the one data module that lists `:packages:network` explicitly, for
`NetworkMonitor`/`Connectivity`.

**`:packages:feature-settings-presentation`** (`com.tasktracker.featuresettingspresentation`) —
`SettingsScreen`, `SettingsModel`, `SettingsEnvironment`, `SettingsPreviewData`.
*Structure:* same presentation shape as the other two features.

### Application shell — above the architecture

**`:composition-root`** (`com.tasktracker.compositionroot`) — the object graph: `AppGraph` binds
every port to its implementation, `SessionCoordinator` handles sign-out teardown, and
`PlatformAppVersion` is the one `expect`/`actual`.
*Structure:* the **only** module that may see every library, because binding a port requires both
sides at once. It stops there — it cannot see `:shared-ui` or `:androidApp`. Host tests in
`jvmTest`/`iosTest`/`androidHostTest`; five graph assertions need a real `Context` and are the
documented Android-host gap.

**`:shared-ui`** (`com.tasktracker.sharedui`) — the shared screen tree: `TaskTrackerApp`,
`RootScreen`, `NavigationHost`, `SplashScreen`, `ProjectsHost`, `ShellPreferences`, and
`MainViewController` (the iOS entry point).
*Structure:* `commonMain` on the presentation modules and their domain modules, the design system,
routing, di, composition-root and user-defaults — but **never a data module**, so it can ask for
projects without being able to construct one. `commonTest` holds the shell scenarios as shared
`fun`s; each host has an entry point that calls them.

**`:androidApp`** (`com.tasktracker.android`) — `MainActivity` and `TaskTrackerApplication`.
*Structure:* not multiplatform. AGP 9 forbids the Android application plugin inside a KMP module,
so this is a plain Android app that sits above `:composition-root` and `:shared-ui`.

**`iosApp`** — the SwiftUI shell. *Structure:* **not a Gradle module at all.** It is an
XcodeGen-generated Xcode project whose only real content is a few lines of Swift that link
`ComposeApp.framework` by path; see [Running on iOS](#running-on-ios) for what that implies.

**`build-logic`** — the convention plugins (`tasktracker.kmp.library`, `.room`, `.compose`) and
the two architecture checks (`VerifyArchitectureGraphTask`, `VerifyModuleImportsTask`).
*Structure:* an included build (`includeBuild`), so it is compiled separately from the app graph
and is where the checked dependency tables live.

---

## How a request flows

Three walks through the layers, so the diagram above has a concrete meaning. None is a summary of
the architecture — it is the same edge list read as a call stack.

### Signing in

| # | Where | What happens |
| --- | --- | --- |
| 1 | `:shared-ui` `RootScreen` | resolves `AuthSessionPort` from the graph and hands it down through `ProvideAuthSession`; `SignInView` reads it via `LocalAuthSession` |
| 2 | `:packages:feature-auth-presentation` `SignInModel.submit()` | calls the `SignIn` use case, which is the only thing that validates and normalises the credentials |
| 3 | `:packages:feature-auth-domain` `SignIn` | calls `AuthSessionPort.signIn`, typed as the port — this module cannot see the repository |
| 4 | `:packages:feature-auth-data` `AuthRepository` | calls `remote.signIn` (bound to `MockAuthRemote`), then `vault.store(tokens)` → `KeychainStore` → platform keychain, caches the profile in `UserDefaultsStore`, and finally `bus.send(SignedIn)` |
| 5 | `:composition-root` `SessionCoordinator` | collects `sessionUpdates`; on `SignedIn` it writes the identity to `SessionCache` (Room) and calls `router.enterMain()` |
| 6 | `:shared-ui` `RootScreen` | reads `Router.state`, sees `RootDestination.Main`, and cross-fades the sign-in form out for the signed-in area |

Note the ordering in step 4: tokens are retained **before** the session is announced. The inverse
would publish a session that does not survive a relaunch, and step 5 is deliberately in
`:composition-root` because it is the one place that can see both the auth port and the router.

### Moving a work item

| # | Where | What happens |
| --- | --- | --- |
| 1 | `:packages:feature-projects-presentation` | `ProjectBoardScreen` fires `onMove`; `ProjectsHost` (in `:shared-ui`) forwards it to `ProjectBoardModel.move()` |
| 2 | same module | `move()` calls the `MoveWorkItem` use case, which applies `ProjectRules`/`WorkItemRules` and the transition matrix |
| 3 | `:packages:feature-projects-data` `RoomProjectRepository` | opens **one** `inTransaction(database)`: writes the work-item record through `RecordMapping.toRecord()` **and** enqueues its outbox row in the same transaction |
| 4 | same module | after the transaction commits, `changeBus.announceProjectChange(projectID)` wakes the models observing that project — the work-item kind only, because a work-item write cannot move a project |
| 5 | `:packages:sync` `SyncCoordinator.synchronize()` | the single gate decides whether a cycle may run (connectivity + single-flight) and calls `SyncEngine.push()` |
| 6 | `:packages:sync` `SyncEngine` | claims the oldest-first pending rows with `OutboxQueue.claim` — scoped by `pushAs` to the signed-in account — and hands them to `SyncTransport.push` |
| 7 | `:packages:feature-projects-data` `LocalDataSyncSink` | the **pull** side lands here; it writes records and never touches the outbox |

Steps 3 and 7 are the two invariants the whole design rests on: one transaction writes the record
and its outbox row, and a pull never enqueues. A two-step write in step 3 leaves a crash window
where an edit exists with nothing queued; a queuing sink in step 7 makes every pull generate a
push, forever. Both are on the non-negotiable list in `docs/DECISIONS.md`.

### Creating a project

| # | Where | What happens |
| --- | --- | --- |
| 1 | `:packages:feature-projects-presentation` `ProjectEditorModel.save()` | calls `CreateProject`, which applies `ProjectRules` before the port is touched, so a rejected edit never leaves a queued change behind |
| 2 | `:packages:feature-projects-data` `RoomProjectRepository.createProject` | reads the session **once**, folds the creator into `memberIDs`, then writes the record **and** its outbox row in one transaction |
| 3 | `:packages:sync` `SyncEngine.push` | claims the row — scoped by `pushAs` to the signed-in account — and hands it to `SyncTransport.push` |
| 4 | `TaskTrackerServer` `internal/sync/engine.go` `canWrite` | authorizes a project it does not already have against **the payload's own member list**, because a project that does not exist yet has no membership rows to check |

Step 2's addition is not a convenience; it is what makes step 4 pass. A project created without its
creator in that list is not merely unauthorized but **permanently unsyncable**: the server answers
`Rejected`, and the queue dead-letters the change instead of retrying it — which is how a user's
first project ends up sitting in the outbox with nothing on the server and no retry that could ever
succeed. `createProject` is the one place that knows both the session and the payload, so the rule
lives there rather than in the use case. The stored record and the queued payload carry the same
list because `enqueueProject` serialises the very object the insert just wrote. The creator is
**appended**, so the members the user picked keep the order they arranged them in, and a store with
no session adds nobody — an unauthenticated build cannot push, so there is no payload for the rule
to protect.

### Ending a session

| # | Where | What happens |
| --- | --- | --- |
| 1 | `:packages:feature-settings-presentation` `SettingsScreen` | the Sign Out row fires `onSignOut`, which the shell wires to `SettingsPort.endSession()` — the screen cannot name a session, so this is the only thing it can reach |
| 2 | `:packages:feature-settings-data` `LiveSettingsService` | `endSession()` calls the closure the graph injected, which is `SessionCoordinator.signOut()` |
| 3 | `:composition-root` `SessionCoordinator` | records `pendingReason = UserLogout` and calls `session.signOut()` — deliberately through the port, because the port is what forgets the credentials |
| 4 | `:packages:feature-auth-data` `AuthRepository` | clears the vault, removes the cached profile, and publishes `SessionState.SignedOut` |
| 5 | `:composition-root` `SessionCoordinator` | the collector sees `SignedOut`, and because a session was live it runs `SessionTeardown.run(reason)` |
| 6 | `:composition-root` `SessionTeardown` | logs the reason, then `resolver.resetSessionScope()` → `metadata.removeAll()` → `sessionCache.clear()` → `syncCoordinator.stop()` |
| 7 | `:composition-root` `SessionCoordinator` | calls `router.enterAuth()`, which wipes the per-tab stacks |
| 8 | `:shared-ui` `RootScreen` | reads `Router.state`, sees `RootDestination.Auth`, and cross-fades back to `SignInView` |

Three decisions in step 6 are load-bearing, and each is a bug if reversed:

- **Outbox rows are kept.** Every queued row carries the id of the account that queued it, and
  `SyncEngine`'s `pushAs` claims only the signed-in account's rows — so a different user cannot
  push this account's work, and the same user signing back in finds their unsynced edits. Clearing
  them would discard work the user never saw fail.
- **Pull metadata is *not* kept.** Its cursors describe a server-side change log as seen by *this*
  session; a stale cursor under a new account would replay changes that account already applied.
- **`sessionCache.clear()` runs before anything else can write.** A status change made after this
  point is recorded with an unknown author, which is the truth — there is no user to name.

A session can also end without a tap: `AuthInterceptor` calls
`delegate.invalidateSession(reason)` on a `403`, or on a `401` that survives the one allowed
replay, and `TokenVault` publishes `SignedOut` directly. That enters at step 5, with
`pendingReason` empty. `SessionCoordinator`'s `hasLiveSession` guard is what makes this safe on a
cold launch: `restore()` on a fresh install also ends in `SignedOut`, and tearing down there would
clear metadata and stop sync on every start for a session that never existed.

Only `SessionCoordinator` touches `Router.root`. `SessionTeardown` deliberately does not route,
because two owners of `root` is how a sign-out ends up half-applied.
