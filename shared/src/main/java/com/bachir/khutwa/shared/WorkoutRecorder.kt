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
    data class TimeHalf(val goalSec: Int) : CoachEvent
    data class TimeLeft(val sec: Int) : CoachEvent
    data class TimeReached(val goalSec: Int, val distanceM: Double) : CoachEvent
    data class ZoneHigh(val zone: Int, val hr: Int) : CoachEvent
    data class IntervalNext(val step: IntervalStep, val next: IntervalStep?, val index: Int, val total: Int) : CoachEvent
    data object IntervalsDone : CoachEvent
    data class IntervalCountdown(val sec: Int) : CoachEvent
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
    private val talksWalkRun = type.walkRun && type != WorkoutType.INTERVALS
    private val profile = config.profile
    private val kalman = KalmanLatLon()
    private val elevation = ElevationTracker()

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
    private var timeHalfSaid = false
    private var timeLeftSaid = false
    private var timeSaid = false

    // speed: median of the last few readings (robust to GPS spikes) then light smoothing
    private val speedWindow = ArrayDeque<Double>()
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
    private var lastTickT = startMs

    private var sprints = 0
    private var sprintSince: Long? = null
    private var sprintCounted = false
    private var maxSpeed = 0.0

    // steps (total since start) & calories
    private var steps = 0
    private var stepsBase: Long? = null
    private var kcal = 0.0
    private var weather: WeatherInfo? = null

    // intervals
    private val plan = if (type == WorkoutType.INTERVALS) config.plan?.steps().orEmpty() else emptyList()
    private var stepIndex = 0
    private var stepStartActive = 0L
    private var stepStartDist = 0.0
    private var lastCountdown = -1

    // pause
    var paused = false; private set
    private var autoPaused = false
    private var pausedAt = 0L
    private var pausedTotal = 0L
    private var breakTrack = false
    private var slowSince: Long? = null

    fun pause(now: Long) { if (!paused) { paused = true; pausedAt = now } }
    fun resume(now: Long) {
        if (paused) { pausedTotal += now - pausedAt; paused = false; autoPaused = false; breakTrack = true; kalman.reset() }
    }

    private fun elapsed(now: Long) = (if (paused) pausedAt else now) - startMs - pausedTotal

    fun setWeather(w: WeatherInfo) { weather = w }

    fun onHeartRate(t: Long, bpm: Int): List<CoachEvent> {
        if (bpm !in 30..230) return emptyList()
        hr = bpm
        if (paused) return emptyList()
        hrSum += bpm; hrCount++
        if (bpm > maxHr) maxHr = bpm
        val z = hrZone(bpm, config.maxHr)
        if (config.hrAlertZone in 1..4 && z > config.hrAlertZone && t - lastZoneAlert > 90_000) {
            lastZoneAlert = t
            return filter(listOf(CoachEvent.ZoneHigh(z, bpm)))
        }
        return emptyList()
    }

    fun onCadence(t: Long, spm: Double) { cadence = spm; cadenceT = t }

    /**
     * Hardware step counter total (phone TYPE_STEP_COUNTER / watch STEPS).
     * Indoor workouts turn steps into distance with the user's stride.
     */
    fun onStepTotal(t: Long, total: Long): List<CoachEvent> {
        val base = stepsBase ?: total.also { stepsBase = it }
        val newSteps = (total - base).toInt().coerceAtLeast(0)
        val delta = newSteps - steps
        if (paused) { stepsBase = base + delta; return emptyList() }
        steps = newSteps
        if (!type.gps && delta > 0) {
            val running = (cadence ?: 0.0) >= 140 || mode == Mode.RUN
            val d = delta * if (running) profile.runStride else profile.walkStride
            return advance(t, d, null)
        }
        return emptyList()
    }

    /** Barometer (or GPS) altitude in metres. */
    fun onAltitude(alt: Double) { if (!paused) elevation.feed(alt) }

    /** Speed coming from the sensor stack (Health Services / GPS doppler). */
    fun onSpeed(t: Long, mps: Double) { externalSpeedT = t; feedSpeed(t, mps) }

    private fun feedSpeed(t: Long, mps: Double) {
        if (mps.isNaN() || mps < 0 || mps > 25) return
        if (!paused && mps > maxSpeed && mps < 12) maxSpeed = mps
        if (mps >= 5.0) {
            val since = sprintSince ?: t.also { sprintSince = it }
            if (!sprintCounted && t - since >= 1_500 && !paused) { sprints++; sprintCounted = true }
        } else if (mps < 4.0) {
            sprintSince = null; sprintCounted = false
        }
        speedWindow.addLast(mps)
        while (speedWindow.size > 5) speedWindow.removeFirst()
        val median = speedWindow.sorted()[speedWindow.size / 2]
        smoothSpeed = if (!hasSpeed) median else smoothSpeed + 0.4 * (median - smoothSpeed)
        hasSpeed = true
    }

    /** Called every second by the service — time goals, intervals and time-based calories. */
    fun tick(now: Long): List<CoachEvent> {
        val events = mutableListOf<CoachEvent>()
        val dt = (now - lastTickT).coerceIn(0, 5_000)
        lastTickT = now
        if (paused) return events
        if (!type.walkRun) kcal += Calories.met(type, smoothSpeed * 3.6) * profile.weightKg * dt / 3_600_000.0
        val el = elapsed(now)

        if (config.goalSec > 0) {
            val left = config.goalSec - (el / 1000).toInt()
            if (!timeHalfSaid && el >= config.goalSec * 500L) { timeHalfSaid = true; events += CoachEvent.TimeHalf(config.goalSec) }
            if (!timeLeftSaid && config.goalSec >= 600 && left in 1..60) { timeLeftSaid = true; events += CoachEvent.TimeLeft(60) }
            if (!timeSaid && left <= 0) { timeSaid = true; events += CoachEvent.TimeReached(config.goalSec, distance) }
        }
        events += checkInterval(el)
        return filter(events)
    }

    private fun checkInterval(el: Long): List<CoachEvent> {
        if (plan.isEmpty() || stepIndex >= plan.size) return emptyList()
        val step = plan[stepIndex]
        val doneSec = (el - stepStartActive) / 1000
        val doneM = distance - stepStartDist
        val finished = if (step.meters > 0) doneM >= step.meters else doneSec >= step.sec
        val out = mutableListOf<CoachEvent>()
        if (!finished && step.meters == 0) {
            val left = (step.sec - doneSec).toInt()
            if (left in 1..3 && left != lastCountdown) { lastCountdown = left; out += CoachEvent.IntervalCountdown(left) }
        }
        if (finished) {
            stepIndex++
            stepStartActive = el
            stepStartDist = distance
            lastCountdown = -1
            out += if (stepIndex >= plan.size) CoachEvent.IntervalsDone
            else CoachEvent.IntervalNext(plan[stepIndex], plan.getOrNull(stepIndex + 1), stepIndex, plan.size)
        }
        return out
    }

    /** The first step of a custom run, said right after "start". */
    fun firstInterval(): CoachEvent? = plan.firstOrNull()?.let { CoachEvent.IntervalNext(it, plan.getOrNull(1), 0, plan.size) }

    fun onLocation(p0: TrackPoint, gpsSpeed: Double? = null): List<CoachEvent> {
        val events = mutableListOf<CoachEvent>()
        if (!type.gps) return events
        if ((p0.acc ?: 0f) > 25f) return events
        lastFixT = p0.t
        gpsSpeed?.let { onSpeed(p0.t, it) }
        p0.alt?.let { if (!elevationFromBaro) elevation.feed(it) }

        // smooth position
        val (la, lo) = kalman.process(p0.lat, p0.lon, p0.acc ?: 10f, p0.t)
        val p = p0.copy(lat = la, lon = lo, alt = elevation.current ?: p0.alt)

        // auto pause: stopped for 10 s; resume when moving again
        if (config.autoPause && type.walkRun) {
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
        var d = 0.0
        if (prev != null && !breakTrack) {
            val dt = (p.t - prev.t) / 1000.0
            if (dt <= 0) return filter(events)
            d = haversine(prev, p)
            if (d / dt > 25.0) return filter(events)               // GPS jump
            // standing still: ignore movement smaller than the fix accuracy
            if (smoothSpeed < 0.4 && d < (p0.acc ?: 10f) * 0.5) d = 0.0
            if (gpsSpeed == null && p.t - externalSpeedT > 5_000) feedSpeed(p.t, d / dt)
        }
        breakTrack = false
        points += p
        events += advance(p.t, d, p)
        return filter(events)
    }

    /** Elevation from barometer: when set, GPS altitude is ignored for climbing. */
    var elevationFromBaro = false

    private fun advance(t: Long, d: Double, p: TrackPoint?): List<CoachEvent> {
        val events = mutableListOf<CoachEvent>()
        distance += d
        if (type.walkRun) kcal += Calories.perKgKm(mode, type) * profile.weightKg * d / 1000.0
        if (p != null) cumDist += distance

        val cad = cadence?.takeIf { t - cadenceT < 10_000 }
        if (type.walkRun) classifier.update(t, if (type.gps) smoothSpeed else cadenceSpeed(cad), cad)?.let { switchAt ->
            val at = switchAt.coerceAtLeast(segStartMs)
            val distAt = if (p != null) distanceAt(at) else distance
            val oldMode = if (classifier.mode == Mode.RUN) Mode.WALK else Mode.RUN
            val seg = Segment(oldMode, segStartMs, at, distAt - segStartDist)
            if (seg.endMs > seg.startMs) segments += seg
            segStartMs = at
            segStartDist = distAt
            if (talksWalkRun) events += CoachEvent.ModeChanged(classifier.mode, seg)
        }

        while (distance >= (splits.size + 1) * 1000.0) {
            splits += Split(splits.size + 1, ((t - kmStartMs) / 1000).toInt())
            kmStartMs = t
        }
        if (distance >= nextAnnounce) {
            val sec = ((t - announceStartMs) / 1000).toInt()
            announceStartMs = t
            if (type != WorkoutType.FOOTBALL) events += CoachEvent.Distance(nextAnnounce.toInt(), sec, currentPaceSec())
            nextAnnounce += config.announceEveryM
        }
        if (config.goalM > 0) {
            if (!goalHalfSaid && distance >= config.goalM / 2.0) { goalHalfSaid = true; events += CoachEvent.GoalHalf(config.goalM) }
            if (!goalSaid && distance >= config.goalM) { goalSaid = true; events += CoachEvent.GoalReached(config.goalM, elapsed(t)) }
        }
        return events
    }

    /** Indoor: a speed-like value from cadence so the walk/run classifier still works. */
    private fun cadenceSpeed(cad: Double?): Double = when {
        cad == null -> 0.0
        cad >= 140 -> config.runSpeedKmh / 3.6 + 0.3
        else -> 1.2
    }

    private fun filter(e: List<CoachEvent>) = if (config.coachOn) e else emptyList()

    fun currentPaceSec(): Int? {
        if (paused) return null
        val sp = if (type.gps) smoothSpeed else indoorSpeed()
        return if (sp < 0.5) null else (1000 / sp).toInt()
    }

    private fun indoorSpeed(): Double {
        val c = cadence?.takeIf { lastTickT - cadenceT < 10_000 } ?: return 0.0
        return c / 60.0 * if (c >= 140) profile.runStride else profile.walkStride
    }

    fun stats(now: Long): LiveStats {
        val curSeg = distance - segStartDist
        val runDone = segments.filter { it.mode == Mode.RUN }.sumOf { it.distanceM }
        val walkDone = segments.filter { it.mode == Mode.WALK }.sumOf { it.distanceM }
        val el = elapsed(now)
        val interval = if (plan.isNotEmpty() && stepIndex < plan.size) {
            val st = plan[stepIndex]
            val doneSec = ((el - stepStartActive) / 1000).toInt()
            val doneM = (distance - stepStartDist).toInt()
            IntervalState(
                index = stepIndex, total = plan.size, step = st, next = plan.getOrNull(stepIndex + 1),
                remainingSec = if (st.meters == 0) (st.sec - doneSec).coerceAtLeast(0) else null,
                remainingM = if (st.meters > 0) (st.meters - doneM).coerceAtLeast(0) else null,
                fraction = if (st.meters > 0) doneM / st.meters.toFloat() else doneSec / st.sec.coerceAtLeast(1).toFloat(),
            )
        } else null
        return LiveStats(
            mode = mode,
            elapsedMs = el,
            distanceM = distance,
            paceSec = currentPaceSec(),
            avgPaceSec = if (distance > 50) ((el / 1000.0) / (distance / 1000.0)).toInt() else null,
            hr = hr,
            runM = runDone + if (mode == Mode.RUN) curSeg else 0.0,
            walkM = walkDone + if (mode == Mode.WALK) curSeg else 0.0,
            gpsOk = !type.gps || now - lastFixT < 10_000,
            type = type,
            sprints = sprints,
            segments = segments.toList() +
                (if (now > segStartMs) listOf(Segment(mode, segStartMs, now, curSeg)) else emptyList()),
            paused = paused,
            zone = hrZone(hr, config.maxHr),
            goalM = config.goalM,
            goalSec = config.goalSec,
            steps = steps,
            cadence = cadence?.takeIf { now - cadenceT < 10_000 }?.toInt(),
            kcal = kcal.toInt(),
            ascentM = elevation.ascent,
            descentM = elevation.descent,
            altitudeM = elevation.current,
            speedKmh = (if (type.gps) smoothSpeed else indoorSpeed()) * 3.6,
            interval = interval,
            weather = weather,
        )
    }

    fun finish(endMs: Long): Workout {
        if (paused) resume(endMs)
        val segs = segments.toMutableList()
        if (endMs > segStartMs) segs += Segment(mode, segStartMs, endMs, distance - segStartDist)
        return Workout(
            id = id, startMs = startMs, endMs = endMs, distanceM = distance,
            points = points.toList(), segments = if (type.walkRun) segs else emptyList(), splits = splits.toList(),
            avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            maxHr = maxHr.takeIf { it > 0 }, source = source,
            type = type, sprints = sprints, maxSpeedMps = maxSpeed, pausedMs = pausedTotal,
            steps = steps, kcal = kcal.toInt(), ascentM = elevation.ascent, descentM = elevation.descent,
            weather = weather, planName = if (type == WorkoutType.INTERVALS) config.plan?.name else null,
        )
    }

    private fun distanceAt(t: Long): Double {
        var i = points.size - 1
        while (i > 0 && points[i].t > t) i--
        return if (i >= 0 && i < cumDist.size) cumDist[i] else distance
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
