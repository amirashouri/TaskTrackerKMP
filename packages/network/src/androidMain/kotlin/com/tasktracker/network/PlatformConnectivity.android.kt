package com.tasktracker.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.tasktracker.core.AppError
import com.tasktracker.core.TTLogger
import com.tasktracker.core.logMessage

/**
 * Android's `ConnectivityManager` default-network callback.
 *
 * `registerDefaultNetworkCallback` rather than `registerNetworkCallback` with a
 * `NetworkRequest`: the default callback follows the system's notion of "the network this
 * app should use", including a validated Wi-Fi that has a route and a VPN that has not.
 * Reimplementing that policy with a `NetworkRequest` is the classic way to report
 * satisfied while every socket is still failing.
 *
 * `registerDefaultNetworkCallback` needs API 24 and the module's `minSdk` is 26, so there
 * is no version branch to get wrong.
 */
actual fun createConnectivityWatcher(host: Any?): ConnectivityWatcher =
    AndroidConnectivityWatcher(context(host))

private fun context(host: Any?): Context = host as? Context
    ?: throw AppError.Unknown(
        "Android needs an application Context to watch connectivity. " +
            "Pass one to NetworkMonitor.forHost(context).",
    )

private class AndroidConnectivityWatcher(private val context: Context) : ConnectivityWatcher {

    private val manager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
            ?: throw AppError.Unknown("This device has no ConnectivityManager.")

    private var onStatus: ((Connectivity) -> Unit)? = null

    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun attach(onStatus: (Connectivity) -> Unit) {
        this.onStatus = onStatus
    }

    override fun start() {
        if (callback != null) return

        val registered = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish()

            override fun onLost(network: Network) = publish()
        }
        // Registered before the first read, so a change that lands between the two cannot
        // be missed — the alternative (read, then register) has a window in which the
        // status is stale by an unknown amount.
        manager.registerDefaultNetworkCallback(registered)
        callback = registered
        publish()
    }

    override fun stop() {
        val registered = callback ?: return
        callback = null
        manager.unregisterNetworkCallback(registered)
        onStatus?.invoke(Connectivity.Unknown)
    }

    private fun publish() {
        val status = status()
        TTLogger.network.debug {
            logMessage {
                append("network: connectivity -> ")
                append(status.name)
            }
        }
        onStatus?.invoke(status)
    }

    /**
     * The current path, classified.
     *
     * `hasCapability(INTERNET)` alone is not enough — that capability is exactly what a
     * captive portal advertises — hence `VALIDATED`, which is the only one that means the
     * system has actually reached the internet. `NET_CAPABILITY_NOT_VPN` is not required:
     * traffic through a VPN is the app's traffic, and rejecting it would report offline
     * for a user who is very much online.
     */
    private fun status(): Connectivity {
        val active = manager.activeNetwork ?: return Connectivity.Unsatisfied
        val capabilities = manager.getNetworkCapabilities(active) ?: return Connectivity.Unsatisfied
        return when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ->
                Connectivity.Satisfied

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
                // A network that exists but has not been validated: the captive portal
                // case. Treated as its own state rather than as offline, because a
                // captive portal is a login, not a dead radio.
                Connectivity.RequiresConnection

            else -> Connectivity.Unsatisfied
        }
    }
}
