package com.bachir.khutwa.shared

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface CoachEvent {
    data class ModeChanged(val mode: Mode, val lastSegment: Segment) : CoachEvent
    /** Fired every `announceEveryM` (500 m or 1 km). */
    data class Distance(val meters: Int, val lastSplitSec: Int, val currentPaceSec: Int?) : CoachEvent
    data class GoalHalf(val goalM: Int) : CoachEvent
    data class GoalReached(val goalM: Int, val elapsedMs: Long) : CoachEvent
    data class ZoneHigh(val zone: Int, val hr: Int) : CoachEvent
    data object AutoPaused : CoachEvent
    data object AutoResumed : CoachEvent
}

/** Platform-free recorder used by both the watch and the phone. */
class WorkoutRecorder(
    private val id: String,
    private val startMs: Long,
    private val source: String,
    val type: WorkoutType = WorkoutType.WALK,
    private val config: CoachConfig = CoachConfig(),
) {
    private val classifier = GaitClassifier(
        runSpeed = config.runSpeedKmh / 3.6,
        walkSpeed = (config.runSpeedKmh - 0.9) / 3.6,
    )
    private val football = type == WorkoutType.FOOTBALL

    private val points = ArrayList<TrackPoint>()
    private val cumDist = ArrayList<Double>()
    private val segments = ArrayList<Segment>()
    private val splits = ArrayList<Split>()

    var distance = 0.0; private set
    val mode get() = classifier.mode

    private var segStartMs = startMs
    private var segStartDist = 0.0
    private var kmStartMs = startMs
    private var nextAnnounce = config.announceEveryM.toDouble()
    private var announceStartMs = startMs
    private var goalHalfSaid = false
    private var goalSaid = false

    private var smoothSpeed = 0.0
    private var hasSpeed = false
    private var externalSpeedT = 0L
    private var cadence: Double? = null
    private var cadenceT = 0L
    private var hr: Int? = null
    private var hrSum = 0L
    private var hrCount = 0
    private var maxHr = 0
    private var lastZoneAlert = 0L
    private var lastFixT = 0L

    private var sprints = 0
    private var sprintSince: Long? = null
    private var sprintCounted = false
    private var maxSpeed = 0.0

    // pause
    var paused = false; private set
    private var autoPaused = false
    private var pausedAt = 0L
    private var pausedTotal = 0L
    private var breakTrack = false
    private var slowSince: Long? = null

    fun pause(now: Long) { if (!paused) { paused = true; pausedAt = now } }
    fun resume(now: Long) {
        if (paused) { pausedTotal += now - pausedAt; paused = false; autoPaused = false; breakTrack = true }
    }

    private fun elapsed(now: Long) = (if (paused) pausedAt else now) - startMs - pausedTotal

    fun onHeartRate(t: Long, bpm: Int): List<CoachEvent> {
        if (bpm !in 30..230) return emptyList()
        hr = bpm
        if (paused) return emptyList()
        hrSum += bpm; hrCount++
        if (bpm > maxHr) maxHr = bpm
        val z = hrZone(bpm, config.maxHr)
        if (config.hrAlertZone in 1..4 && z > config.hrAlertZone && t - lastZoneAlert > 90_000) {
            lastZoneAlert = t
            return listOf(CoachEvent.ZoneHigh(z, bpm))
        }
        return emptyList()
    }

    fun onCadence(t: Long, spm: Double) { cadence = spm; cadenceT = t }

    /** Speed coming from the sensor stack (Health Services / GPS doppler). */
    fun onSpeed(t: Long, mps: Double) { externalSpeedT = t; feedSpeed(t, mps) }

    private fun feedSpeed(t: Long, mps: Double) {
        if (mps.isNaN() || mps < 0 || mps > 12) return
        if (!paused && mps > maxSpeed) maxSpeed = mps
        if (mps >= 5.0) {
            val since = sprintSince ?: t.also { sprintSince = it }
            if (!sprintCounted && t - since >= 1_500 && !paused) { sprints++; sprintCounted = true }
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
        gpsSpeed?.let { onSpeed(p.t, it) }

        // auto pause: stopped for 10 s; resume when moving again
        if (config.autoPause && !football) {
            if (smoothSpeed < 0.5) {
                val s = slowSince ?: p.t.also { slowSince = it }
                if (!paused && p.t - s > 10_000) { pause(p.t); autoPaused = true; events += CoachEvent.AutoPaused }
            } else {
                slowSince = null
                if (paused && autoPaused) { resume(p.t); events += CoachEvent.AutoResumed }
            }
        }
        if (paused) return filter(events)

        val prev = points.lastOrNull()
        if (prev != null && !breakTrack) {
            val dt = (p.t - prev.t) / 1000.0
            if (dt <= 0) return filter(events)
            val d = haversine(prev, p)
            if (d / dt > 12.0) return filter(events)            // GPS jump
            distance += d
            if (gpsSpeed == null && p.t - externalSpeedT > 5_000) feedSpeed(p.t, d / dt)
        }
        breakTrack = false
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
            if (!football) events += CoachEvent.ModeChanged(classifier.mode, seg)
        }

        while (distance >= (splits.size + 1) * 1000.0) {
            val km = splits.size + 1
            splits += Split(km, ((p.t - kmStartMs - 0) / 1000).toInt())
            kmStartMs = p.t
        }
        if (distance >= nextAnnounce) {
            val sec = ((p.t - announceStartMs) / 1000).toInt()
            announceStartMs = p.t
            if (!football) events += CoachEvent.Distance(nextAnnounce.toInt(), sec, currentPaceSec())
            nextAnnounce += config.announceEveryM
        }
        if (config.goalM > 0) {
            if (!goalHalfSaid && distance >= config.goalM / 2.0) { goalHalfSaid = true; events += CoachEvent.GoalHalf(config.goalM) }
            if (!goalSaid && distance >= config.goalM) { goalSaid = true; events += CoachEvent.GoalReached(config.goalM, elapsed(p.t)) }
        }
        return filter(events)
    }

    private fun filter(e: List<CoachEvent>) = if (config.coachOn) e else emptyList()

    fun currentPaceSec(): Int? = if (!hasSpeed || smoothSpeed < 0.5 || paused) null else (1000 / smoothSpeed).toInt()

    fun stats(now: Long): LiveStats {
        val curSeg = distance - segStartDist
        val runDone = segments.filter { it.mode == Mode.RUN }.sumOf { it.distanceM }
        val walkDone = segments.filter { it.mode == Mode.WALK }.sumOf { it.distanceM }
        val el = elapsed(now)
        return LiveStats(
            mode = mode,
            elapsedMs = el,
            distanceM = distance,
            paceSec = currentPaceSec(),
            avgPaceSec = if (distance > 50) ((el / 1000.0) / (distance / 1000.0)).toInt() else null,
            hr = hr,
            runM = runDone + if (mode == Mode.RUN) curSeg else 0.0,
            walkM = walkDone + if (mode == Mode.WALK) curSeg else 0.0,
            gpsOk = now - lastFixT < 10_000,
            type = type,
            sprints = sprints,
            segments = segments.toList() +
                (if (now > segStartMs) listOf(Segment(mode, segStartMs, now, curSeg)) else emptyList()),
            paused = paused,
            zone = hrZone(hr, config.maxHr),
            goalM = config.goalM,
        )
    }

    fun finish(endMs: Long): Workout {
        if (paused) resume(endMs)
        val segs = segments.toMutableList()
        if (endMs > segStartMs) segs += Segment(mode, segStartMs, endMs, distance - segStartDist)
        return Workout(
            id = id, startMs = startMs, endMs = endMs, distanceM = distance,
            points = points.toList(), segments = segs, splits = splits.toList(),
            avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            maxHr = maxHr.takeIf { it > 0 }, source = source,
            type = type, sprints = sprints, maxSpeedMps = maxSpeed, pausedMs = pausedTotal,
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
