package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/**
 * Device motion (tilt) for parallax effects on glass surfaces.
 *
 * [roll] is left/right tilt (-1 = left, 1 = right); [pitch] is forward/back tilt
 * (-1 = forward, 1 = back). Both are normalized to -1..1 from platform radians.
 *
 * The values are [State], and **they must be read in draw phase** — inside a
 * `drawBehind`/`drawWithContent` lambda — not in composition. The sensor updates at
 * frame rate; a composition-phase read would recompose every glass surface on screen
 * per motion event, where a draw-phase read only invalidates redraw.
 *
 * Obtain the controller with [rememberDeviceMotion], never by constructing the
 * platform class: the shell provides one app-scoped instance through
 * [DeviceMotionProvider], and every glass surface shares it.
 */
interface DeviceMotionController {
    /** Left/right tilt, -1..1. */
    val roll: State<Float>
    /** Forward/back tilt, -1..1. */
    val pitch: State<Float>

    /** Begin delivering motion updates. Safe to call when already running. */
    fun start()

    /** Release the platform sensor. Called by the owner on dispose. */
    fun stop()
}

/**
 * The app-scoped controller, when the shell has provided one.
 *
 * `null` by default so previews, test hosts and any screen reached before the shell
 * installs the provider still render — [rememberDeviceMotion] falls back to a
 * per-call-site controller in that case, which is the pre-provider behaviour rather
 * than a crash.
 */
val LocalDeviceMotion = compositionLocalOf<DeviceMotionController?> { null }

/**
 * Provides one platform controller to everything under [content].
 *
 * One instance per provider — not one per glass surface — so the platform sensor is
 * registered once for the whole app. The controller is started on enter and stopped
 * on dispose; a screen that leaves composition stops the sensor.
 *
 * The optional [controller] exists for tests: production callers use the no-arg form
 * and get the platform actual.
 */
@Composable
fun DeviceMotionProvider(
    controller: DeviceMotionController? = null,
    content: @Composable () -> Unit,
) {
    val motion = controller ?: PlatformDeviceMotion()
    StartStopMotion(motion)
    CompositionLocalProvider(LocalDeviceMotion provides motion, content = content)
}

/**
 * The shared motion controller, or a private one when no provider is installed.
 *
 * The fallback path exists so the package renders in previews and test hosts that
 * have not adopted [DeviceMotionProvider]; it starts and stops its own controller via
 * the same dispose discipline. Under a provider, this is a plain local read and the
 * sensor lifecycle belongs to the provider alone.
 */
@Composable
fun rememberDeviceMotion(): DeviceMotionController {
    val provided = LocalDeviceMotion.current
    if (provided != null) return provided
    return StartStopMotion(PlatformDeviceMotion())
}

/**
 * The dispose discipline both ownership paths share: start on enter, stop on leave.
 * Internal rather than private so the discipline is testable without a real sensor.
 */
@Composable
internal fun StartStopMotion(controller: DeviceMotionController): DeviceMotionController {
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }
    return controller
}

/** Creates the platform controller. One per [DeviceMotionProvider]; do not call directly. */
@Composable
expect fun PlatformDeviceMotion(): DeviceMotionController
