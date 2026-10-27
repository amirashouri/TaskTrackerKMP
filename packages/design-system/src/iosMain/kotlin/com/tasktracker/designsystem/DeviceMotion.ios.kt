package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.CoreMotion.CMDeviceMotion
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

@Composable
actual fun PlatformDeviceMotion(): DeviceMotionController {
    return remember { IosDeviceMotion() }
}

private class IosDeviceMotion : DeviceMotionController {
    private val motionManager = CMMotionManager()
    private val _roll = mutableStateOf(0f)
    private val _pitch = mutableStateOf(0f)

    override val roll: State<Float> get() = _roll
    override val pitch: State<Float> get() = _pitch

    override fun start() {
        // The handler runs on the main queue, which is the thread Compose for iOS composes
        // and draws on — so the State writes are snapshot-safe and a draw-phase reader sees
        // every frame's attitude. Without a handler the updates are never delivered and the
        // specular never moves; the handler, not startDeviceMotionUpdates(), is what makes
        // the sensor real.
        if (motionManager.deviceMotionAvailable && !motionManager.deviceMotionActive) {
            motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
            // The ObjC selector is `startDeviceMotionUpdatesToQueue:withHandler:`, and Kotlin/Native
            // keeps that whole first part as the function name rather than folding the queue into a
            // named parameter — `startDeviceMotionUpdates(toQueue = …)` reads as the queue-less
            // overload and does not compile. Nothing caught this before because the iOS target had
            // not been compiled since the handler was added; see the handoff.
            motionManager.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { motion, _ ->
                motion?.let { writeAttitude(it) }
            }
        }
    }

    override fun stop() {
        motionManager.stopDeviceMotionUpdates()
    }

    private fun writeAttitude(motion: CMDeviceMotion) {
        val attitude = motion.attitude
        // roll/pitch in radians, normalize to -1..1 (Double -> Float)
        _roll.value = (attitude.roll / 3.14159).toFloat().coerceIn(-1f, 1f)
        _pitch.value = (attitude.pitch / 3.14159).toFloat().coerceIn(-1f, 1f)
    }
}
