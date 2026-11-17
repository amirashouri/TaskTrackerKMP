package com.tasktracker.sharedui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.compositionroot.platformApiBaseUrl
import com.tasktracker.designsystem.LocalNativeGlassEnabled
import com.tasktracker.di.Resolver
import platform.UIKit.UIViewController

/** iOS entry point exported to Swift as `MainViewControllerKt.mainViewController()`. */
fun mainViewController(): UIViewController = ComposeUIViewController {
    // The one host with a real UIKit view hierarchy behind it, and therefore the one that
    // may back its glass surfaces with a native view. Every other composition — the Compose
    // test scene included — keeps the default, painted surface.
    CompositionLocalProvider(LocalNativeGlassEnabled provides true) {
        TaskTrackerApp(resolver = IosGraph.resolver)
    }
}

/** Lazy process-lifetime graph holder. */
private object IosGraph {
    val resolver: Resolver by lazy { AppGraph.start(apiBaseUrl = platformApiBaseUrl()) }
}