package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.tasktracker.core.isDesktopApp
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.di.Resolver

/**
 * The Desktop entry point.
 *
 * Modeled on `MainViewController.kt`, the same shape iOS already uses: the
 * graph is built once and held in process scope, and the platform shell
 * contributes only the window and the graph. Nothing in `commonMain` changes
 * for Desktop.
 *
 * Unlike the mobile shells, Desktop's entry is a Compose `application {}`
 * block rather than a bare `main()`. That is the canonical way to open a
 * top-level `Window` on Compose Desktop; the `Window` composable runs inside
 * the application's composable scope.
 *
 * ## Why this takes no arguments
 *
 * The alternative is a `main` that builds the graph itself by reaching into
 * `:packages:di` or `:composition-root` from the platform side — which is the
 * wrong dependency direction. The graph is owned in Kotlin, exactly as
 * `TaskTrackerApplication` owns it on Android and `IosGraph` owns it on iOS.
 */
fun main() = startDesktopProcess {
    val resolver = DesktopGraph.resolver
    application {
        Window(onCloseRequest = ::exitApplication) {
            DesktopContent(resolver = resolver)
        }
    }
}

/**
 * Marks this process as the Desktop app, and only then opens the shell.
 *
 * The order is the contract, not a style choice. The shell's graph builds every store when
 * it is started, and both JVM storage seams pick their backend from [isDesktopApp] at
 * construction time — `platformPreferences()` returns the file-backed backend only once the
 * flag is set, and the JVM `systemKeychain()` likewise hands out the in-memory stub while it
 * is clear. Dropping the assignment, or opening the shell first, leaves a Desktop app that
 * holds its session and its appearance in memory alone and loses both on every restart, with
 * nothing on screen to say so.
 *
 * Extracted from `main` because that decision has to be reachable from a test. `main` opens a
 * window and blocks until it closes, so nothing in the suite could enter the sequence that
 * decides whether Desktop persists anything; `DesktopEntryPointJvmTest` asserts the flag is
 * already set at the moment the shell is opened, which fails both when the assignment is
 * deleted and when the two steps are swapped.
 */
internal fun startDesktopProcess(openShell: () -> Unit) {
    isDesktopApp = true
    openShell()
}

/**
 * The preferences file the Desktop app owns.
 *
 * Exists so the Desktop shell can open the same file the preferences backend writes
 * without reaching across the architecture boundary into `:packages:database`.
 * `user-defaults` already depends on `core` only and owns this path locally for exactly
 * that reason; the shell reads the path here so the run task / desktop launcher can point
 * at it without adding a new module edge.
 */
public val preferencesFilePath: String
    get() = com.tasktracker.userdefaults.preferencesFile().path

@Composable
private fun DesktopContent(resolver: Resolver) {
    TaskTrackerApp(resolver = resolver)
}

/**
 * The process-lifetime graph on Desktop.
 *
 * Mirrors `IosGraph` on iOS, including the reason the graph is built once and
 * held rather than created per window or per recomposition: a second window or a
 * re-launch must not produce two graphs.
 *
 * ## Why there is no matching `stop`
 *
 * Same asymmetry as iOS. A Desktop process exits when the window closes or the
 * process is killed; at that point releasing the three looping services is
 * correct, and hooking a `stop` off a window-close callback would cancel them for
 * an app the user was about to resume. So there is no such symbol here. If Desktop
 * ever needs explicit teardown, this holder is where it belongs.
 */
private object DesktopGraph {
    val resolver: Resolver by lazy { AppGraph.start() }
}
