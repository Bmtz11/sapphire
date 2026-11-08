package com.bachir.khutwa.shared

import kotlin.math.max

/**
 * Smooths GPS fixes with a simple Kalman filter weighted by each fix's accuracy.
 * Removes the zig-zag jitter that inflates distance, especially when walking slowly.
 */
class KalmanLatLon(private val qMetresPerSecond: Double = 3.0) {
    private var lat = 0.0
    private var lon = 0.0
    private var variance = -1.0      // negative = not initialised
    private var lastT = 0L

    fun process(lat0: Double, lon0: Double, accuracy: Float, t: Long): Pair<Double, Double> {
        val acc = max(accuracy.toDouble(), 3.0)
        if (variance < 0) {
            lat = lat0; lon = lon0; variance = acc * acc; lastT = t
            return lat to lon
        }
        val dt = (t - lastT).coerceAtLeast(0)
        if (dt > 0) { variance += dt * qMetresPerSecond * qMetresPerSecond / 1000.0; lastT = t }
        val k = variance / (variance + acc * acc)
        lat += k * (lat0 - lat)
        lon += k * (lon0 - lon)
        variance *= (1 - k)
        return lat to lon
    }

    fun reset() { variance = -1.0 }
}

/**
 * Ascent / descent with a dead-band so sensor noise isn't counted as climbing.
 * Feed it barometer altitude (best) or GPS altitude.
 */
class ElevationTracker(private val threshold: Double = 3.0) {
    var ascent = 0.0; private set
    var descent = 0.0; private set
    var current: Double? = null; private set
    private var ref: Double? = null
    private var smooth: Double? = null

    fun feed(alt: Double) {
        if (alt.isNaN()) return
        val s = smooth?.let { it + 0.25 * (alt - it) } ?: alt
        smooth = s
        current = s
        val r = ref ?: run { ref = s; return }
        when {
            s - r >= threshold -> { ascent += s - r; ref = s }
            r - s >= threshold -> { descent += r - s; ref = s }
        }
    }
}

/** Calorie estimates (kcal) — distance-based for walk/run, MET × time for the rest. */
object Calories {
    /** kcal per kg per km. */
    fun perKgKm(mode: Mode, type: WorkoutType) = when {
        type == WorkoutType.HIKE -> 0.75
        mode == Mode.RUN -> 1.036
        else -> 0.53
    }

    /** MET for time-based sports. */
    fun met(type: WorkoutType, speedKmh: Double) = when (type) {
        WorkoutType.FOOTBALL -> 7.0
        WorkoutType.CYCLING -> when {
            speedKmh < 16 -> 4.0
            speedKmh < 19 -> 6.8
            speedKmh < 22 -> 8.0
            else -> 10.0
        }
        else -> 3.5
    }
}
