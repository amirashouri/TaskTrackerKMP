package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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
fun main() = application {
    val resolver = DesktopGraph.resolver
    Window(onCloseRequest = ::exitApplication) {
        DesktopContent(resolver = resolver)
    }
}

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
