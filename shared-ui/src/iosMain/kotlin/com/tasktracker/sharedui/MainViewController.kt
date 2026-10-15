package com.tasktracker.sharedui

import androidx.compose.ui.window.ComposeUIViewController
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.compositionroot.platformApiBaseUrl
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
 *
 * ## Why there is no matching `stop`
 *
 * The asymmetry with `TaskTrackerApplication.onTerminate` is deliberate, and it is the
 * difference between a platform that has a teardown hook and one that does not. Android has
 * `onTerminate`, so the graph's three service loops are stopped before the container goes.
 *
 * iOS has no such hook. There is no `applicationWillTerminate` — it is not called for a
 * suspended app — and the last scene disconnecting is not process death, so a `stop` hung off
 * it would cancel the coordinator, the sync engine and the network monitor of an app the user
 * was about to resume. The consequence is the reverse of the Android one and is worth stating
 * plainly rather than leaving to be discovered: on iOS the loops are released by the process
 * exiting, which is the only event after which releasing them would be correct.
 *
 * The alternative — exposing a `stop` for Swift to call from a lifecycle callback it does not
 * reliably receive — would be an API whose correct use is unavailable and whose incorrect use
 * is silent. So there is no such symbol here. If iOS ever grows a real teardown signal, this
 * holder is where `AppGraph.stop(resolver)` belongs, and the doc above is what to replace.
 *
 * ## Why it names a base URL
 *
 * iOS runs beside the Go server as much as Desktop does, so it asks for the real HTTP remote and
 * transport rather than the in-process demo pair. [platformApiBaseUrl] reads the `Info.plist`
 * key a deployment sets (and falls back to the local server for the simulator), so the address
 * lives in the app bundle rather than in this file — the same shared resolver Desktop and
 * Android call.
 */
private object IosGraph {
    val resolver: Resolver by lazy { AppGraph.start(apiBaseUrl = platformApiBaseUrl()) }
}
