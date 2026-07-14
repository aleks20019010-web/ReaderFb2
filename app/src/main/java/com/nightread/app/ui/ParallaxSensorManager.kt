package com.nightread.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

object ParallaxSensorManager {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private val listeners = mutableListOf<ParallaxListener>()
    
    private var lastX = 0f
    private var lastY = 0f
    private const val ALPHA = 0.15f // Low-pass filter smoothing coefficient

    interface ParallaxListener {
        fun onTiltChanged(tiltX: Float, tiltY: Float)
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            
            val rawX = event.values[0] // tilt left/right (approx -10 to 10)
            val rawY = event.values[1] // tilt up/down (approx -10 to 10)
            
            // Low pass filter to smooth out hand shake jitter
            lastX = lastX + ALPHA * (rawX - lastX)
            lastY = lastY + ALPHA * (rawY - lastY)
            
            // Normalize values to range -1.0 to 1.0 (clamped)
            val normX = (lastX / 9.81f).coerceIn(-1f, 1f)
            val normY = (lastY / 9.81f).coerceIn(-1f, 1f)
            
            // Send to all listeners
            listeners.forEach { it.onTiltChanged(normX, normY) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun init(context: Context) {
        if (sensorManager == null) {
            sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
    }

    fun registerListener(listener: ParallaxListener) {
        if (listeners.isEmpty()) {
            sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: ParallaxListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            sensorManager?.unregisterListener(sensorListener)
        }
    }
}
