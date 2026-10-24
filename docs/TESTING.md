# Testing — policy and commands

This is the authoritative answer to “how are tests run, where does a test live, and what
counts as a pass”. `test-baseline.json` holds the generated totals;
`test-known-failures.json` holds the named allowlist; `docs/TEST-HARNESS-PLAN.md` holds the
rollout that will make the allowlist empty.

## Commands

| Command | What it runs | When |
| --- | --- | --- |
| `scripts/check.sh` | JVM tests, architecture checks, baseline, summary | The gate before pushing; the default |
| `scripts/check.sh quick <module> [filter…]` | JVM tests for one module, filtered with `--tests`; result XML kept; no baseline or summary | While editing a file or a scenario — **not** a gate |
| `scripts/check.sh fast <module>` | JVM tests for one module (for example `packages:core`); still a gate | While iterating on one module whose whole suite is affordable |
| `scripts/check.sh full` | JVM + iOS simulator + Android host + graph + baseline + summary | Before a PR; required for UI, `expect`/`actual`, and cross-platform changes |
| `scripts/check.sh jvm` | JVM tests only | Targeted JVM run |
| `scripts/check.sh ios` | iOS simulator tests only | Targeted iOS run |
| `scripts/check.sh android` | Android host tests only | Targeted Robolectric run |
| `scripts/check.sh graph` | `verifyArchitectureGraph`, `verifyModuleImports`, Android compile | Dependency changes, and before pushing a change to the build scripts |
| `scripts/check.sh lint` | `:androidApp:lintDebug` | Manifest / Android lint changes |
| `scripts/check.sh docs` | Nothing | Docs-only changes |

Every **gate** run uses Gradle `--continue`, removes the selected hosts’ old result
directories first, prints one compact failure summary, then runs
`scripts/check-baseline.sh` for the hosts that actually ran. The command exits green when
every failure is named in `test-known-failures.json`; a new failure, a stale allowlist
entry, a changed total, or a missing host result exits red.

`quick` is the one exception, and it is not a gate. It keeps the previous result XML —
pruning the directory makes Gradle’s test task out of date, so every gate re-executes the
whole suite instead of reporting `UP-TO-DATE` — and it skips the summary and baseline
steps, both of which measure whole-host totals and would report every test the filter
did not run as missing. Use it while iterating, then run `fast` or `full` before pushing.

The default scope is JVM plus the two architecture checks. The Android compile
(`:androidApp:compileDebugKotlin`, Room/KSP plus resources) is the heaviest non-test task
in the build and lives in `graph` and `full` instead: a dependency change is already the
thing `graph` is for, and `full` needs the Android host anyway.

## Hosts

| Host | Task | What it is for |
| --- | --- | --- |
| JVM | `jvmTest` | Fast feedback; pure logic and JVM-only seams |
| iOS simulator | `iosSimulatorArm64Test` | iOS rasterisation, `expect`/`actual`, native behaviour |
| Android host | `testAndroidHostTest` | Robolectric; Android-only `Context`, Compose layout at a phone viewport |

Desktop is **not** a fourth host. The Desktop app is `shared-ui/src/jvmMain`; its tests are
`shared-ui/src/jvmTest`, which is the JVM target’s test source set.

## Where a test goes

1. **Pure logic, no composition** — `commonTest`, plain `@Test`.
2. **Compose behaviour through a screen or the shell** — a `commonTest` scenario object
   plus thin per-host entry points. The assertions live in the scenario; each host only
   supplies `runComposeUiTest(block = Scenarios.name)`.
3. **Pixels in `:packages:design-system`** — `commonTest` with the raster guard. The whole
   measurement, including every assertion, lives inside the guarded composition.
4. **A real `Context`, Room, or a platform service** — the same scenario-object split as 2,
   with `@RunWith(RobolectricTestRunner::class)` on the Android entry point.
5. **A real file, reflection, or a JVM-only API** — `jvmTest` or `androidHostTest`.

An Android-host Compose test class must carry:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
```

A test class in `commonTest` that composes UI runs on the Android host as plain JUnit, so
it cannot rely on a Robolectric runner. Such a test either moves to a host entry point, or
asks the host capability before composing.

## Guards and skips

A host that cannot run an assertion must say so **before** composing, and the framework
must record it as a skip rather than a pass. The target contract is:

- `assumeHostRasterisesText()` — the Android actual uses `org.junit.Assume`; JVM and iOS
  are no-ops because they can rasterise.
- `assumeHostCapability(supported)` — wide layout, system back, Room stores, and similar
  host capabilities.
- `thisHostRasterisesText()`, `thisHostExpressesDesktopWidth()`,
  `thisHostDeliversSystemBack()`, `thisHostOpensRoomStores()` — the predicates the
  scenarios use to decide whether the capability exists.

A guard that returns early while assertions remain outside it is a defect: on a host that
cannot run, the assertions either fail on a null reading or pass without measuring
anything. Keep the measurement and the assertion inside the guarded block.

## Android host specifics

- Robolectric’s default viewport is much smaller than a phone. Compose tests declare a
  phone qualifier (`robolectric.properties` or a shared `@Config`); wide-layout scenarios
  opt into a wide qualifier or skip honestly.
- `GraphicsMode.NATIVE` is required for Compose layout and rasterisation.
- Pixel tests do not run on this host. They ask `assumeHostRasterisesText()` and are
  recorded as skipped.
- `sqliteJni` is not on the host’s `java.library.path`, so a Room-backed scenario asks
  `thisHostOpensRoomStores()` first.

## Baseline and known failures

- `test-baseline.json` is generated by `scripts/test-baseline.py` from
  `**/build/test-results/*/TEST-*.xml`. Never hand-edit it.
- Regenerate it in the same change as a test addition, removal, or skip change. Run
  `scripts/check.sh full` first (it may be red because the old baseline has the previous
  totals), then run `scripts/test-baseline.py` and commit both changes together.
- `test-known-failures.json` is a checked-in map of `host → test id → reason`. It starts
  with the standing red, shrinks as each cause is fixed, and must be empty before the
  matrix is considered green.
- `scripts/check-baseline.sh` fails on:
  - a failure not in the allowlist;
  - an allowlisted test that now passes;
  - a total that differs from the committed baseline;
  - a host with no result files or zero tests.
- A failure count is never the gate. Two tests can swap places and leave the count
  unchanged; the identities are what `check-baseline.sh` compares.

## Test names

Backtick full sentences, with no commas or periods. Kotlin/Native rejects both, and only
when the common source is compiled for Native — a name can look fine under
`:module:jvmTest` and break `:module:iosSimulatorArm64Test`.

## Related documents

- `docs/TEST-HARNESS-PLAN.md` — the phased cleanup and acceptance criteria.
- `docs/TEST-HOSTS.md` — the historical failure catalog; the policy above supersedes it.
- `docs/DECISIONS.md` — the measurement lore behind pixel and contrast tests.
- `.agents/skills/kmp-compose-tests/SKILL.md` — the procedural companion.
