package io.github.mojri.hesabyar.ui.utils

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects a deliberate phone shake from accelerometer samples.
 * A shake = at least [requiredJolts] samples above [thresholdG] (in g-force,
 * gravity excluded by magnitude) inside [windowMs], then a [cooldownMs] pause.
 * [onSample] is pure logic so it can be unit-tested without Android.
 */
class ShakeDetector(
  private val thresholdG: Float = 2.4f,
  private val requiredJolts: Int = 3,
  private val windowMs: Long = 1_000L,
  private val cooldownMs: Long = 2_000L,
  private val onShake: () -> Unit
) : SensorEventListener {
  private val joltTimes = ArrayDeque<Long>()
  private var lastShakeMs = Long.MIN_VALUE

  fun onSample(
    x: Float,
    y: Float,
    z: Float,
    nowMs: Long
  ) {
    val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
    if (g < thresholdG) return
    if (lastShakeMs != Long.MIN_VALUE && nowMs - lastShakeMs < cooldownMs) return
    joltTimes.addLast(nowMs)
    while (joltTimes.isNotEmpty() && nowMs - joltTimes.first() > windowMs) joltTimes.removeFirst()
    if (joltTimes.size >= requiredJolts) {
      joltTimes.clear()
      lastShakeMs = nowMs
      onShake()
    }
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
    onSample(event.values[0], event.values[1], event.values[2], System.currentTimeMillis())
  }

  override fun onAccuracyChanged(
    sensor: Sensor?,
    accuracy: Int
  ) = Unit

  /** Returns false when the device has no accelerometer. */
  fun register(sensorManager: SensorManager): Boolean {
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
    return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
  }

  fun unregister(sensorManager: SensorManager) = sensorManager.unregisterListener(this)
}
