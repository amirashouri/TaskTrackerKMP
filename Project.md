# Project.md

How this project is built, run, and verified. Companion to [`PLAN.md`](PLAN.md), which is the
design record — why each decision was made. This file is the short operational answer to "how do
I build and run this, and what will bite me".

---

## Requirements

JDK 25. This machine has exactly one JDK and `java_home` cannot find it, so **every** Gradle
invocation needs:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Without it the build dies immediately with `Unable to locate a Java Runtime`.

---

## The command that decides whether you are done

```bash
./gradlew jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports :androidApp:compileDebugKotlin
```

`./gradlew check` is that plus `testAndroidHostTest`, which is **red for documented,
pre-existing reasons** (see `PLAN.md`, "Where things stand"). Do not fix it as a side effect of
other work, and do not report it as a regression you caused — check whether it was already
failing.

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

## Tests

| Host | Task | State |
| --- | --- | --- |
| JVM | `jvmTest` | fast feedback; run this |
| iOS simulator | `iosSimulatorArm64Test` | slower; needed for anything touching `expect`/`actual` or rendered pixels |
| Android host | `testAndroidHostTest` | Robolectric; **red for documented pre-existing reasons** |

A test that renders Compose and reads pixels must call `thisHostRasterisesText()` — an `expect` in
`:packages:design-system`'s `commonTest` with actuals in `jvmTest`, `iosTest`, `androidHostTest`.
Without it the Android host fails on layout, not behaviour. Be aware the skip registers as a
*pass* on that host, not a skip.

### Compose resources

`:packages:design-system` is the only module that configures `compose.resources`, and every string
lives there. A new `stringResource` key needs `./gradlew :packages:design-system:generateComposeRes`
if the generated accessors do not appear.

---

## When something renders differently on iOS than on the JVM

Measure it on both. They disagree: the same token on the same backdrop measured 5.37:1 on the JVM
and 3.98:1 on iOS, because iOS rasterises a small glyph's stroke at roughly 85% coverage. Tuning
to one host ships a defect to the other. `tools/inspect_frame.py` reads a PNG with no third-party
dependencies and prints geometry, contrast, and an ASCII map.

Rendered pixel assertions belong in a test whose name says what is being measured. See
`PLAN.md` §16 for a case where the assertion looked reasonable and encoded the defect as correct.

---

## Where things are

Gradle modules are the architecture boundary; see
`build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts` for the checked
dependency table. `:composition-root` binds implementations, `:packages:design-system` owns all
shared visual tokens and Compose resources, and shared behaviour lives in `commonMain`.
