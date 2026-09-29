package com.tasktracker.android

import android.app.Application
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.di.Resolver

/**
 * Owns the object graph for the process.
 *
 * The graph is built here rather than lazily in `MainActivity` so that a configuration
 * change, a process-restart or a second activity cannot produce two graphs. The original
 * does the same thing in `TaskTrackerApp.swift`.
 */
class TaskTrackerApplication : Application() {

    /** The started graph. Read by the activity, and by nothing else. */
    lateinit var resolver: Resolver
        private set

    override fun onCreate() {
        super.onCreate()
        resolver = AppGraph.start()
    }

    override fun onTerminate() {
        // Only called on emulators, but leaving it out means a leaked container has no
        // counterpart to be released by.
        if (::resolver.isInitialized) resolver.stop()
        super.onTerminate()
    }
}
