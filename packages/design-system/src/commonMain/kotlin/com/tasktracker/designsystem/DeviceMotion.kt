package com.tasktracker.designsystem

import androidx.compose.runtime.Composable

/**
 * Device motion (tilt) for parallax effects on glass surfaces.
 *
 * [roll] is left/right tilt (-1 = left, 1 = right).
 * [pitch] is forward/back tilt (-1 = forward, 1 = back).
 * Both are normalized to -1..1 from platform radians.
 */
@Composable
expect fun DeviceMotion(): DeviceMotionController

interface DeviceMotionController {
    /** Left/right tilt, -1..1. */
    @Composable
    fun roll(): Float
    /** Forward/back tilt, -1..1. */
    @Composable
    fun pitch(): Float
}