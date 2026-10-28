package com.tasktracker.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tasktracker.compositionroot.apiRootRequiresLocalNetworkAccess
import com.tasktracker.sharedui.TaskTrackerApp
import com.tasktracker.sharedui.receiveDeepLink

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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        val app = application as TaskTrackerApplication
        requestLocalNetworkAccessIfNeeded(app.apiBaseUrl)

        // A cold start *from* a link. Buffered by the shared sink, so delivering it before
        // the composition starts is safe; `savedInstanceState == null` keeps an activity
        // recreation from replaying a link the process already applied.
        if (savedInstanceState == null) {
            intent.deepLink()?.let(::receiveDeepLink)
        }

        setContent {
            TaskTrackerApp(resolver = app.resolver)
        }
    }

    /**
     * A link that arrives while the app is already running.
     *
     * `singleTop` keeps this instance, so a scan from a browser or another app lands here
     * instead of building a second shell over the first. The intent becomes the activity's
     * current intent so a later recreation sees the same URL, and the shared sink applies it
     * to the live router through the collector that is already running.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.deepLink()?.let(::receiveDeepLink)
    }

    /**
     * The URL on a `VIEW` intent, or `null` for the launcher intent.
     *
     * The manifest filter already restricts the scheme, but the action check keeps a stray
     * `data` on some other intent from being treated as navigation.
     */
    private fun Intent.deepLink(): String? =
        takeIf { action == Intent.ACTION_VIEW }?.dataString

    /**
     * Asks for local-network access when the build's root is on the local network.
     *
     * Only from Android 17 (API 37) onward: `ACCESS_LOCAL_NETWORK` was added there (the SDK's
     * `api-versions.xml` pins it at `since="37.0"`), and on anything older it is not a
     * permission the platform knows. On an Android 16 device the request below would launch
     * for a permission that does not exist and resolve as denied without a dialog, which is
     * why the guard is 37 and not the earlier `BAKLAVA` (36) codename. Only for a local root:
     * a deployment pointed at a public hostname never touches the local network, and a
     * "nearby devices" prompt there would be access the app cannot use. See
     * [apiRootRequiresLocalNetworkAccess] for the classification and the measured failure it
     * exists to prevent.
     *
     * The constants are only *named* below the API guard, which is what makes the inlined
     * strings safe on an older device: lint cannot see through the early return.
     */
    @SuppressLint("InlinedApi")
    private fun requestLocalNetworkAccessIfNeeded(apiBaseUrl: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
        if (!apiRootRequiresLocalNetworkAccess(apiBaseUrl)) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_LOCAL_NETWORK,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) localNetworkAccess.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
}
