package com.bachir.khutwa.shared

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface CoachEvent {
    data class ModeChanged(val mode: Mode, val lastSegment: Segment) : CoachEvent
    data class KmSplit(val km: Int, val splitSec: Int, val currentPaceSec: Int?) : CoachEvent
}

/** Platform-free recorder used by both the watch and the phone. */
class WorkoutRecorder(
    private val id: String,
    private val startMs: Long,
    private val source: String,
    val type: WorkoutType = WorkoutType.WALK,
    private val classifier: GaitClassifier = GaitClassifier(),
) {
    /** Football: no walk/run announcements, but sprints + top speed. */
    private val speaks = type != WorkoutType.FOOTBALL
    private var sprints = 0
    private var sprintSince: Long? = null
    private var sprintCounted = false
    private var maxSpeed = 0.0
    private val points = ArrayList<TrackPoint>()
    private val cumDist = ArrayList<Double>()
    private val segments = ArrayList<Segment>()
    private val splits = ArrayList<Split>()

    var distance = 0.0; private set
    val mode get() = classifier.mode

    private var segStartMs = startMs
    private var segStartDist = 0.0
    private var kmStartMs = startMs

    private var smoothSpeed = 0.0
    private var hasSpeed = false
    private var externalSpeedT = 0L
    private var cadence: Double? = null
    private var cadenceT = 0L
    private var hr: Int? = null
    private var hrSum = 0L
    private var hrCount = 0
    private var maxHr = 0
    private var lastFixT = 0L

    fun onHeartRate(bpm: Int) {
        if (bpm !in 30..230) return
        hr = bpm; hrSum += bpm; hrCount++
        if (bpm > maxHr) maxHr = bpm
    }

    fun onCadence(t: Long, spm: Double) { cadence = spm; cadenceT = t }

    /** Speed coming from the sensor stack (Health Services / GPS doppler). */
    fun onSpeed(t: Long, mps: Double) { externalSpeedT = t; feedSpeed(t, mps) }

    private fun feedSpeed(t: Long, mps: Double) {
        if (mps.isNaN() || mps < 0 || mps > 12) return
        if (mps > maxSpeed) maxSpeed = mps
        // sprint = ≥ 18 km/h held for 1.5 s
        if (mps >= 5.0) {
            val since = sprintSince ?: t.also { sprintSince = it }
            if (!sprintCounted && t - since >= 1_500) { sprints++; sprintCounted = true }
        } else if (mps < 4.0) {
            sprintSince = null; sprintCounted = false
        }
        smoothSpeed = if (!hasSpeed) mps else smoothSpeed + 0.35 * (mps - smoothSpeed)
        hasSpeed = true
    }

    fun onLocation(p: TrackPoint, gpsSpeed: Double? = null): List<CoachEvent> {
        val events = mutableListOf<CoachEvent>()
        if ((p.acc ?: 0f) > 30f) return events
        lastFixT = p.t

        val prev = points.lastOrNull()
        if (prev != null) {
            val dt = (p.t - prev.t) / 1000.0
            if (dt <= 0) return events
            val d = haversine(prev, p)
            if (d / dt > 12.0) return events           // GPS jump
            distance += d
            if (gpsSpeed == null && p.t - externalSpeedT > 5_000) feedSpeed(p.t, d / dt)
        }
        gpsSpeed?.let { onSpeed(p.t, it) }
        points += p
        cumDist += distance

        val cad = cadence?.takeIf { p.t - cadenceT < 10_000 }
        classifier.update(p.t, smoothSpeed, cad)?.let { switchAt ->
            val at = switchAt.coerceAtLeast(segStartMs)
            val distAt = distanceAt(at)
            val oldMode = if (classifier.mode == Mode.RUN) Mode.WALK else Mode.RUN
            val seg = Segment(oldMode, segStartMs, at, distAt - segStartDist)
            if (seg.endMs > seg.startMs) segments += seg
            segStartMs = at
            segStartDist = distAt
            events += CoachEvent.ModeChanged(classifier.mode, seg)
        }

        while (distance >= (splits.size + 1) * 1000.0) {
            val km = splits.size + 1
            val sec = ((p.t - kmStartMs) / 1000).toInt()
            splits += Split(km, sec)
            kmStartMs = p.t
            events += CoachEvent.KmSplit(km, sec, currentPaceSec())
        }
        return if (speaks) events else emptyList()
    }

    fun currentPaceSec(): Int? = if (!hasSpeed || smoothSpeed < 0.5) null else (1000 / smoothSpeed).toInt()

    fun stats(now: Long): LiveStats {
        val elapsed = now - startMs
        val curSeg = distance - segStartDist
        val runDone = segments.filter { it.mode == Mode.RUN }.sumOf { it.distanceM }
        val walkDone = segments.filter { it.mode == Mode.WALK }.sumOf { it.distanceM }
        return LiveStats(
            mode = mode,
            elapsedMs = elapsed,
            distanceM = distance,
            paceSec = currentPaceSec(),
            avgPaceSec = if (distance > 50) ((elapsed / 1000.0) / (distance / 1000.0)).toInt() else null,
            hr = hr,
            runM = runDone + if (mode == Mode.RUN) curSeg else 0.0,
            walkM = walkDone + if (mode == Mode.WALK) curSeg else 0.0,
            gpsOk = now - lastFixT < 10_000,
            type = type,
            sprints = sprints,
            segments = segments.toList() +
                (if (now > segStartMs) listOf(Segment(mode, segStartMs, now, curSeg)) else emptyList()),
        )
    }

    fun finish(endMs: Long): Workout {
        val segs = segments.toMutableList()
        if (endMs > segStartMs) segs += Segment(mode, segStartMs, endMs, distance - segStartDist)
        return Workout(
            id = id, startMs = startMs, endMs = endMs, distanceM = distance,
            points = points.toList(), segments = segs, splits = splits.toList(),
            avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            maxHr = maxHr.takeIf { it > 0 }, source = source,
            type = type, sprints = sprints, maxSpeedMps = maxSpeed,
        )
    }

    private fun distanceAt(t: Long): Double {
        var i = points.size - 1
        while (i > 0 && points[i].t > t) i--
        return if (i >= 0) cumDist[i] else 0.0
    }

    companion object {
        fun haversine(a: TrackPoint, b: TrackPoint): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val h = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
            return 2 * r * atan2(sqrt(h), sqrt(1 - h))
        }
    }
}
