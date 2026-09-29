package com.tasktracker.network

import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfiable
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_status_unsatisfied
import platform.darwin.dispatch_queue_create

/**
 * `NWPathMonitor`, the same API the original used — not a change for its own sake.
 *
 * It is the only path API on iOS that reports `satisfiable` as distinct from
 * `unsatisfied`, which is what [Connectivity.RequiresConnection] means: the iOS Settings
 * app will not let a user turn off Wi-Fi on a Mac, so a "disconnected" Mac is a network
 * that exists and cannot reach anything. Mapping both to `Unsatisfied` would make the
 * Settings screen tell a user on a train to check their Wi-Fi when it is already on.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun createConnectivityWatcher(host: Any?): ConnectivityWatcher = DarwinConnectivityWatcher()

private class DarwinConnectivityWatcher : ConnectivityWatcher {

    private var monitor: platform.Network.nw_path_monitor_t? = null

    private var onStatus: ((Connectivity) -> Unit)? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun attach(onStatus: (Connectivity) -> Unit) {
        this.onStatus = onStatus
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun start() {
        if (monitor != null) return

        // A serial queue of its own, as the original's. `NWPathMonitor` calls its handler
        // on the queue given here, and the default would run it on whatever thread started
        // the monitor — a shared queue would make a status update contend with whatever
        // else the app is doing.
        val queue = dispatch_queue_create("com.tasktracker.network-monitor", null)
        val created = nw_path_monitor_create()
        monitor = created
        nw_path_monitor_set_queue(created, queue)
        nw_path_monitor_set_update_handler(created) { path ->
            path?.let { publish(nw_path_get_status(it)) }
        }
        nw_path_monitor_start(created)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun stop() {
        val started = monitor ?: return
        monitor = null
        nw_path_monitor_cancel(started)
        onStatus?.invoke(Connectivity.Unknown)
    }

    private fun publish(status: platform.Network.nw_path_status_t) {
        val mapped = when (status) {
            nw_path_status_satisfied -> Connectivity.Satisfied
            nw_path_status_satisfiable -> Connectivity.RequiresConnection
            nw_path_status_unsatisfied -> Connectivity.Unsatisfied
            // `nw_path_status_invalid`, plus anything a future SDK adds: a status this
            // build has never heard of is unknown, not unsatisfied. Treating it as offline
            // would park a sync queue over a value the app cannot interpret.
            else -> Connectivity.Unknown
        }
        TTLogger.network.debug {
            logMessage {
                append("network: connectivity -> ")
                append(mapped.name)
            }
        }
        onStatus?.invoke(mapped)
    }
}
