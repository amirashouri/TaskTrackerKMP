package com.tasktracker.android

import android.app.Application
import com.tasktracker.compositionroot.AppGraph
import com.tasktracker.compositionroot.platformApiBaseUrl
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
        // `this`, not an activity context: the graph is process-scoped, and the keychain
        // and preferences backends need a context that outlives any window. Both take
        // `.applicationContext` internally, so passing the Application here is also the
        // point at which that leak-safety is established.
        //
        // `apiBaseUrl` is the address the graph talks to, read from this app's manifest
        // meta-data by the shared resolver. Naming it is what binds the real HTTP remote and
        // transport; without it the graph would keep the in-process demo pair and never reach
        // the server. See `platformApiBaseUrl` for where the value comes from.
        resolver = AppGraph.start(this, apiBaseUrl = platformApiBaseUrl(this))
    }

    override fun onTerminate() {
        // `AppGraph.stop`, not `Resolver.stop`. The latter releases the container and nothing
        // else, which would leave the three service loops — the session listener, the sync
        // engine and the network monitor — collecting for the rest of the process with no
        // graph left to route through. `AppGraph.stop` resolves each one and stops it first,
        // and only then releases the container they were resolved from.
        //
        // Only called on emulators, but leaving it out means a leaked container has no
        // counterpart to be released by.
        if (::resolver.isInitialized) AppGraph.stop(resolver)
        super.onTerminate()
    }
}
