package com.tasktracker.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tasktracker.compositionroot.apiRootRequiresLocalNetworkAccess
import com.tasktracker.sharedui.TaskTrackerApp

/**
 * The Android entry point.
 *
 * It does four things and delegates everything else: enables edge-to-edge so the shared
 * content can draw under the system bars the way the SwiftUI original does, asks for
 * local-network access when this build's server needs it, obtains the graph the
 * [TaskTrackerApplication] built, and hands both to [TaskTrackerApp].
 *
 * There is deliberately no Android-specific navigation or view state here. A second
 * screen type appearing in this file would be the first sign that the platform shell is
 * growing responsibilities the shared module owns.
 */
class MainActivity : ComponentActivity() {

    /**
     * The local-network prompt.
     *
     * A property, because that is where the activity-result API accepts a registration: it
     * must happen before the activity is started, and a property initialiser runs in the
     * constructor. The answer is deliberately not remembered here — it changes nothing the
     * screen does, only whether sockets to a local root are allowed, and a denial has to
     * stay an ordinary retryable request failure rather than a state the UI branches on.
     */
    private val localNetworkAccess =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as TaskTrackerApplication
        requestLocalNetworkAccessIfNeeded(app.apiBaseUrl)

        setContent {
            TaskTrackerApp(resolver = app.resolver)
        }
    }

    /**
     * Asks for local-network access when the build's root is on the local network.
     *
     * Only from Android 16 onward: that is where the permission exists, and on anything
     * older it is implicitly granted with `INTERNET`, so asking would be a prompt for a
     * permission the platform does not have. Only for a local root: a deployment pointed at
     * a public hostname never touches the local network, and a "nearby devices" prompt there
     * would be access the app cannot use. See [apiRootRequiresLocalNetworkAccess] for the
     * classification and the measured failure it exists to prevent.
     *
     * The constants are only *named* below the API guard, which is what makes the inlined
     * strings safe on an older device: lint cannot see through the early return.
     */
    @SuppressLint("InlinedApi")
    private fun requestLocalNetworkAccessIfNeeded(apiBaseUrl: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        if (!apiRootRequiresLocalNetworkAccess(apiBaseUrl)) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_LOCAL_NETWORK,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) localNetworkAccess.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
}
