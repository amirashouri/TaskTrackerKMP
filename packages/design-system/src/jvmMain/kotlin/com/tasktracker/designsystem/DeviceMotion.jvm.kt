package com.tasktracker.designsystem

import androidx.compose.runtime.Composable

@Composable
actual fun DeviceMotion(): DeviceMotionController = JvmDeviceMotion

private object JvmDeviceMotion : DeviceMotionController {
    @Composable
    override fun roll(): Float = 0f
    @Composable
    override fun pitch(): Float = 0f
}