package com.tasktracker.designsystem

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlatformDeviceMotion(): DeviceMotionController {
    val context = LocalContext.current
    return remember(context) { AndroidDeviceMotion(context) }
}

private class AndroidDeviceMotion(context: Context) : DeviceMotionController, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _roll = mutableStateOf(0f)
    private val _pitch = mutableStateOf(0f)

    override val roll: State<Float> get() = _roll
    override val pitch: State<Float> get() = _pitch

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var registered = false

    override fun start() {
        if (registered) return
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        registered = true
    }

    override fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
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
}
