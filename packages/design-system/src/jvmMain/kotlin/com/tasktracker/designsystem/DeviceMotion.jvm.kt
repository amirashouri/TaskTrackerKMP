package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

@Composable
actual fun PlatformDeviceMotion(): DeviceMotionController = JvmDeviceMotion

/**
 * A desktop has no tilt, and the specular must render as if the device is level —
 * which a static zero State does without any of the sensor plumbing. The object is
 * a singleton, so every surface on desktop also shares one controller for free.
 */
private object JvmDeviceMotion : DeviceMotionController {
    override val roll: State<Float> = mutableStateOf(0f)
    override val pitch: State<Float> = mutableStateOf(0f)
    override fun start() {}
    override fun stop() {}
}
