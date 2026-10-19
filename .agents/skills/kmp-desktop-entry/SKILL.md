---
name: kmp-desktop-entry
description: Use when adding, running, or debugging a Compose Desktop (JVM desktop) target in this Kotlin Multiplatform project — the source-set entry point in shared-ui/src/jvmMain, the app-classpath change for compose.desktop.currentOs, the Gradle run task, and per-OS caveats (menu bar, window chrome, fonts, native dialogs).
related:
  - kmp-architecture-workflow
  - kmp-compose-tests
  - rendered-ui-measurement
---

# Compose Desktop entry point

This project's desktop target is the JVM target made into a *fourth shell*, alongside
Android, iOS, and the JVM test host. It is a source-set addition inside `shared-ui`
(`shared-ui/src/jvmMain/`), the same shape iOS already uses for `MainViewController.kt`.
No new Gradle module, no new architecture-table row.

## What this skill covers

- The `jvmMain` entry point that owns the graph and opens a Compose `Window`.
- Moving `compose.desktop.currentOs` from the test classpath onto the app classpath.
- The Gradle `run` task that launches `DesktopMain.main()`.
- The first-layer per-OS notes (macOS, Linux, Windows) that affect how the app feels
  and what it can reach, not what it renders.

What it does **not** cover: feature work, design tokens, the architecture tables (those
are `kmp-architecture-workflow`), or the general test matrix (that is `kmp-compose-tests`).
When in doubt between this skill and one of those, read the one whose subject matches the
file you are touching.

## Before touching the Desktop target

- JDK 25 is required. This machine has exactly one JDK and `java_home` cannot find it, so
  every Gradle invocation needs:
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
  Without it the build dies with `Unable to locate a Java Runtime`.
- Configuration cache is on. If a build behaves as though it ignored an edit, re-run rather
  than debugging the code.
- `applyDefaultHierarchyTemplate()` is called in the convention plugin. That is what makes
  `jvmMain` a real source set. Never assume a file in `src/jvmMain` is compiled until a
  task that compiles that source set has run.
- `NavigationHost.kt` and most of `shared-ui` are untracked. Do not use broad scripted
  search-and-replace in this tree; read the file and edit deliberately.

## The entry point

Model it on `shared-ui/src/iosMain/.../MainViewController.kt`:

- One `main()` function.
- Build the graph once in process scope (`AppGraph.start()`), the same way the other shells
  do. Do not rebuild it per window, per recomposition, or per activation.
- Open a Compose `Window` and render `TaskTrackerApp` inside it.
- Take no arguments from the platform side. The graph is Kotlin-owned, same as iOS.
- Contribute only the window and the graph. No Desktop-specific navigation or state belongs
  here; if something needs to be Desktop-only it is a seam, and seams go through
  `expect`/`actual` in the owning module, not inline in the entry point.

A Desktop-only seam is still a seam. When you add one, supply every actual the configured
hierarchy needs in the same transaction — `jvmMain` actuals are not optional here the way a
test-only host actual sometimes is.

## The app classpath, not only the test classpath

`compose.desktop.currentOs` is already on the `jvmTest` classpath. The Desktop **app**
classpath is the one that does not have it yet.

- Add it to the source set that compiles the entry point (`jvmMain`-side), without dropping
  it from `jvmTest`.
- Confirm the Compose Multiplatform 1.12.1 desktop coordinates resolve, and confirm the
  existing `jvmTest` path still compiles after the change.
- Do not put the dependency only on `jvmTest` again. That is the trap that made the first
  Compose UI tests die in their constructor before anyone had a screen to test.

## The run task

Gradle needs a task that launches `DesktopMain.main()` with the right classpath. Without it,
Desktop exists in Gradle but not as something a developer can launch.

- Name it so a developer can find it (`runDesktop` or the per-module equivalent) and so it
  does not collide with the existing `jvmTest` path.
- It should depend on the `jvmMain` compile output and use a classpath that includes the
  Desktop runtime.
- This is the Desktop equivalent of `:androidApp:installDebug` and of Xcode's
  build-and-run. If you cannot launch it from a single Gradle invocation, it is not done.

## Current state of this target

This target is shipped. Its implementation history is in
`docs/PLAN-ARCHIVE.md`, section 4. Keep launchability and Desktop host coverage aligned
with the current build setup; a passing JVM test host alone does not prove the app can be
launched.

## Per-OS notes

The same target runs on **macOS, Linux, and Windows**. You do not maintain three targets.
What differs per OS is the native stack under the same Kotlin code, not the UI.

- **macOS, Linux, Windows all run the same JVM target.** Compose Desktop uses
  `java.awt`/`Swing`/`skiko` under the hood on all three; the Kotlin is identical.
- **`compose.desktop.currentOs`** is the multiplatform artifact that picks the right native
  library for the OS you are running on. It is why one classpath works on all three.
- **Window chrome is not the same everywhere.** Title bar, dragging, minimize/close buttons,
  and the menu bar are OS-level things Compose either abstracts or leaves as a seam you hook
  yourself. A macOS-drafted window may look fine on all three and still feel non-native on
  Linux or Windows. That is a feel problem, not a render problem.
- **Menu bar, system tray, global shortcuts, native file dialogs** — these are opt-in, not
  turnkey. If the app needs any of them, that is a seam per OS, same pattern as the existing
  `expect`/`actual` seams (keychain, user-defaults, network connectivity).
- **Fonts** may differ by OS and by what is installed. Compose Desktop picks a default; if
  the app relies on a specific font being present, say so rather than discovering it when a
  Linux run looks wrong.

## Persistence is the one real product decision

The JVM target's keychain and user-defaults actuals are in-memory today (test-host stubs).
A Desktop app that signs in and restarts loses its session unless Desktop gets real
persistence for at least one of them. Decide that when you get to it; it is a product choice,
not a build detail. Do not inherit the in-memory stubs by accident and then treat the
resulting logout-on-restart behaviour as a bug.

## Testing and verification

- Desktop is a JVM target. The existing `commonTest` scenarios and pixel tests already run
  on JVM; Desktop runs on JVM. The regression question is whether Desktop passes the same
  suites the JVM host does, and whether it behaves the same when it lands.
- If Desktop becomes its own test-host column rather than folding into JVM, add the
  entry-point class the same way `HostedGraphScenariosTest` does for the
  `Context`-needing graph assertions. Do not duplicate the assertions; duplicate only the
  entry points.
- When a UI property only exists in rendered output, use `rendered-ui-measurement` and
  measure on more than one host if the hosts you can run disagree. Desktop is one of those
  hosts once it exists.
- For architecture-only changes (table edits, new dependencies), run
  `verifyArchitectureGraph` + `verifyModuleImports` after the source and graph are aligned.
  For this source-set shape, the expected result is that no new table row is needed.

## Staleness traps

- Editing a file and then reading `build/reports` can show the previous run's output. Re-run
  with `--rerun-tasks` after an edit made outside Gradle (`cp`, `git checkout`).
- After a revert-and-restore cycle, confirm what is actually on disk before interpreting a
  launch failure. A Desktop app that "does nothing" is frequently a classpath or main-class
  mismatch, not a rendering bug.
- If a run task launches but the window never appears, check the run configuration's main
  class and classpath before reading the UI code.
