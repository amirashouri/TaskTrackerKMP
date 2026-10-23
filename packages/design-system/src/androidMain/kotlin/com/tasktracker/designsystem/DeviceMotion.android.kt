package com.tasktracker.designsystem

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun DeviceMotion(): DeviceMotionController {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    return remember { AndroidDeviceMotion(sensorManager) }
}

private class AndroidDeviceMotion(
    private val sensorManager: SensorManager,
) : DeviceMotionController, SensorEventListener {

    private val _roll = mutableStateOf(0f)
    private val _pitch = mutableStateOf(0f)
    
    @Composable
    override fun roll(): Float = _roll.value
    
    @Composable
    override fun pitch(): Float = _pitch.value

    private var rotationMatrix = FloatArray(9)
    private var orientation = FloatArray(3)
    private var registered = false

    init {
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
            registered = true
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            // orientation[0] = azimuth, orientation[1] = pitch, orientation[2] = roll (radians)
            // Normalize to -1..1 (radian range is roughly -PI..PI)
            _roll.value = (orientation[2] / 3.14159f).coerceIn(-1f, 1f)
            _pitch.value = (orientation[1] / 3.14159f).coerceIn(-1f, 1f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    fun stop() {
        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }
    }
}