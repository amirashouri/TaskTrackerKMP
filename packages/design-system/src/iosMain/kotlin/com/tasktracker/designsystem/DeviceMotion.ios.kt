package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.CoreMotion.CMMotionManager
import kotlinx.cinterop.*

@Composable
actual fun DeviceMotion(): DeviceMotionController {
    return remember { IosDeviceMotion() }
}

private class IosDeviceMotion : DeviceMotionController {
    private val motionManager = CMMotionManager()
    private val _roll = mutableStateOf(0f)
    private val _pitch = mutableStateOf(0f)
    
    @Composable
    override fun roll(): Float = _roll.value
    
    @Composable
    override fun pitch(): Float = _pitch.value

    init {
        motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
        motionManager.startDeviceMotionUpdates()
    }

    @Composable
    fun updateFromMotion() {
        val motion = motionManager.deviceMotion
        if (motion != null) {
            val attitude = motion.attitude
            // roll/pitch in radians, normalize to -1..1 (Double -> Float)
            _roll.value = (attitude.roll / 3.14159).toFloat().coerceIn(-1f, 1f)
            _pitch.value = (attitude.pitch / 3.14159).toFloat().coerceIn(-1f, 1f)
        }
    }

    fun stop() {
        motionManager.stopDeviceMotionUpdates()
    }
}