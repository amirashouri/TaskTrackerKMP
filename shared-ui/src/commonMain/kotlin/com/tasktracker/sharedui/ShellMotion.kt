package com.tasktracker.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.tasktracker.designsystem.DeviceMotionController
import com.tasktracker.designsystem.DeviceMotionProvider
import com.tasktracker.designsystem.LocalReducedMotion

/**
 * The platform's Reduce Motion preference, as this host reports it.
 *
 * Read by the shell and not by `:packages:design-system`, which is the package's own contract:
 * it never auto-detects a platform setting, because a design system that reads the system on its
 * own cannot be rendered by a preview, a screenshot test or a second app with a different
 * opinion. The shell asks, and the shell answers — the same division as the device-motion
 * controller's.
 *
 * `true` means *less motion*, not *no animation*: the material snaps to its targets instead of
 * springing to them, and nothing disappears. That is the meaning the platform setting has, and
 * the meaning [LocalReducedMotion] is documented with.
 *
 * ## Asked once per composition, not observed
 *
 * The value is read when the root composes rather than subscribed to. A user who flips the
 * switch mid-session keeps the old answer until the shell next composes, which on Android is a
 * configuration change and on iOS is a return to the foreground — and every use of the value is
 * a draw-phase or animation decision with nothing to notify, so observing it would mean a
 * platform observer per host (`ContentObserver`, `UIAccessibilityReduceMotionStatusDidChange`)
 * to change a spring into a snap one frame earlier than a recomposition would. If that is ever
 * wanted, this is the seam to put it behind, and no call site has to change.
 */
@Composable
internal expect fun platformReducedMotion(): Boolean

/**
 * Provides the two platform motion inputs the glass material reads.
 *
 * They belong at the app root and not per screen, for one reason each. The Reduce Motion
 * preference is a property of the platform, so every glass surface in the shell has to be told
 * the same thing. The device-motion controller is a *sensor*, so one instance per app: the
 * previous arrangement created one per call site with no dispose path, and the whole point of
 * [DeviceMotionProvider] is that the shell owns exactly one and stops it when it leaves.
 *
 * The parameters exist for tests and default to production: no argument reads this host's
 * preference and creates the platform controller, while a test can hand in a preference and a
 * recording controller and assert what the material under it actually receives.
 */
@Composable
internal fun ShellMotionProvider(
    reduceMotion: Boolean = platformReducedMotion(),
    motion: DeviceMotionController? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalReducedMotion provides reduceMotion) {
        DeviceMotionProvider(controller = motion, content = content)
    }
}
