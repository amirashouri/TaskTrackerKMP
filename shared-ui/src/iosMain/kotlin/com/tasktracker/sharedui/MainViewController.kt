package com.tasktracker.sharedui

import androidx.compose.ui.window.ComposeUIViewController
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.di.Resolver
import platform.UIKit.UIViewController

/**
 * The iOS entry point, exported to Swift as `MainViewControllerKt.mainViewController()`.
 *
 * ## Why this takes no arguments
 *
 * The alternative is a function taking a `Resolver` and letting Swift build the graph,
 * which would force `:shared-ui` to `export(project(":packages:di"))` so that Swift could
 * name the type at all. That export is the wrong dependency direction: it publishes the
 * DI port into the framework's public surface so the platform shell can construct the
 * thing it is meant to be *given*. The graph is owned in Kotlin, exactly as
 * `TaskTrackerApplication` owns it on Android, and Swift's entire contribution is the
 * `UIViewController` return type.
 *
 * The result is that the framework exports exactly one symbol to the app target, and the
 * Gradle module graph stays invisible from Swift - which is the division of labour
 * `shared-ui/build.gradle.kts` states: Swift never sees a Gradle module, only this.
 */
fun mainViewController(): UIViewController = ComposeUIViewController {
    TaskTrackerApp(resolver = IosGraph.resolver)
}

/**
 * The process-lifetime graph on iOS.
 *
 * Mirrors `TaskTrackerApplication` on Android, including the reason the graph is built
 * once and held rather than created per view controller: a rotation, a second window or a
 * scene reconnect must not produce two graphs. `TaskTrackerApplication` gets that for
 * free from the Android process lifecycle; iOS has no equivalent hook that runs exactly
 * once before any view controller exists, so a lazily initialised holder is the honest
 * equivalent rather than pretending `AppDelegate` is that hook.
 */
private object IosGraph {
    val resolver: Resolver by lazy { AppGraph.start() }
}
