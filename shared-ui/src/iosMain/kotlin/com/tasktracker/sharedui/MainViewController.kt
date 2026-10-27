package com.tasktracker.sharedui

import androidx.compose.ui.window.ComposeUIViewController
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.compositionroot.platformApiBaseUrl
import com.tasktracker.di.Resolver
import platform.UIKit.UIViewController

/** iOS entry point exported to Swift as `MainViewControllerKt.mainViewController()`. */
fun mainViewController(): UIViewController = ComposeUIViewController {
    TaskTrackerApp(resolver = IosGraph.resolver)
}

/** Lazy process-lifetime graph holder. */
private object IosGraph {
    val resolver: Resolver by lazy { AppGraph.start(apiBaseUrl = platformApiBaseUrl()) }
}