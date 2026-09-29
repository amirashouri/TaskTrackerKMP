package com.tasktracker.network

/**
 * The JVM always reports a satisfied path.
 *
 * There is no portable way to ask a desktop JVM whether it is offline — `NetworkInterface`
 * is up whenever the machine is powered on, and a VPN or a captive network is
 * indistinguishable from a direct connection. Reporting `Satisfied` is the honest answer
 * for a platform that cannot know, and it is what the plan specifies: the only consumer
 * that cares is the sync engine, and on the JVM a wrong "offline" would park a queue that
 * could have drained. The "Force Offline" override remains available for a test.
 */
actual fun createConnectivityWatcher(host: Any?): ConnectivityWatcher = JvmConnectivityWatcher()

private class JvmConnectivityWatcher : ConnectivityWatcher {

    private var onStatus: ((Connectivity) -> Unit)? = null

    private var running = false

    override fun attach(onStatus: (Connectivity) -> Unit) {
        this.onStatus = onStatus
    }

    override fun start() {
        if (running) return
        running = true
        onStatus?.invoke(Connectivity.Satisfied)
    }

    override fun stop() {
        if (!running) return
        running = false
        // Reported rather than left at Satisfied: a stopped monitor is one the app has
        // torn down, and "unknown" is the honest state of a monitor that is not watching.
        onStatus?.invoke(Connectivity.Unknown)
    }
}
