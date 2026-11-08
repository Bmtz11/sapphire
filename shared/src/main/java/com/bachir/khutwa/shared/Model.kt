package com.bachir.khutwa.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class Mode { WALK, RUN }

/** What the user picked before starting. */
enum class WorkoutType(private val arName: String, val gps: Boolean = true, val walkRun: Boolean = true) {
    WALK("مشي"), // notr
    RUN("جري"), // notr
    INTERVALS("جري مخصص"), // notr
    HIKE("هايكنج"), // notr
    TREADMILL("سير/جري داخلي", gps = false), // notr
    CYCLING("دراجة", walkRun = false), // notr
    FOOTBALL("كرة قدم", walkRun = false); // notr
    val ar get() = tr(arName)
}

/** One block of a custom (interval) run. Ends by time or by distance. */
@Serializable
data class IntervalStep(val kind: Kind, val sec: Int = 0, val meters: Int = 0) {
    enum class Kind(private val arName: String) { WARMUP("إحماء"), RUN("جري"), WALK("مشي"), COOLDOWN("تهدئة"); val ar get() = tr(arName) } // notr
    val label get() = if (meters > 0) "${kind.ar} ${if (meters >= 1000) tr("{0} كم", meters / 1000.0) else tr("{0} متر", meters)}"
    else "${kind.ar} ${if (sec % 60 == 0) tr("{0} د", sec / 60) else "${sec / 60}:${"%02d".format(sec % 60)}"}"
}

@Serializable
data class IntervalPlan(
    val id: String,
    val name: String,
    val warmupSec: Int = 300,
    val runSec: Int = 120,
    val runM: Int = 0,           // if > 0, run blocks end by distance instead of time
    val walkSec: Int = 60,
    val repeats: Int = 6,
    val cooldownSec: Int = 300,
) {
    fun steps(): List<IntervalStep> = buildList {
        if (warmupSec > 0) add(IntervalStep(IntervalStep.Kind.WARMUP, warmupSec))
        repeat(repeats) { i ->
            add(IntervalStep(IntervalStep.Kind.RUN, if (runM > 0) 0 else runSec, runM))
            if (i < repeats - 1 && walkSec > 0) add(IntervalStep(IntervalStep.Kind.WALK, walkSec))
        }
        if (cooldownSec > 0) add(IntervalStep(IntervalStep.Kind.COOLDOWN, cooldownSec))
    }
    val totalSec get() = steps().sumOf { it.sec }
}

@Serializable
data class Profile(
    val weightKg: Double = 75.0,
    val heightCm: Int = 175,
    val age: Int = 30,
    val male: Boolean = true,
) {
    /** Step length estimates (m) for indoor distance. */
    val walkStride get() = heightCm * 0.415 / 100
    val runStride get() = heightCm * 0.65 / 100
    val bmi get() = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
    val waterGoalMl get() = (weightKg * 35).toInt().let { (it / 250) * 250 }.coerceIn(1500, 4500)
}

/** Workout settings shared by phone and watch (the phone pushes its copy to the watch). */
@Serializable
data class CoachConfig(
    val coachOn: Boolean = true,
    val announceEveryM: Int = 1000,     // 500 or 1000
    val maxHr: Int = 190,
    val hrAlertZone: Int = 0,           // 0 = off, else alert when above this zone (1..4)
    val goalM: Int = 0,                 // 0 = no distance goal
    val goalSec: Int = 0,               // 0 = no time goal
    val runSpeedKmh: Double = 7.4,
    val autoPause: Boolean = false,
    val profile: Profile = Profile(),
    val plan: IntervalPlan? = null,     // active custom run (for INTERVALS)
    val english: Boolean = false,
    val voiceName: String? = null,
) {
    companion object {
        fun encode(c: CoachConfig) = WorkoutJson.json.encodeToString(serializer(), c)
        fun decode(s: String) = WorkoutJson.json.decodeFromString(serializer(), s)
    }
}

@Serializable
data class WeatherInfo(
    val tempC: Double,
    val feelsC: Double,
    val humidity: Int,
    val windKmh: Double,
    val code: Int,
) {
    val ar get() = when (code) {
        0 -> tr("صافي")
        1, 2 -> tr("غائم جزئياً")
        3 -> tr("غائم")
        45, 48 -> tr("ضباب")
        in 51..67 -> tr("مطر")
        in 71..77 -> tr("ثلج")
        in 80..82 -> tr("زخات مطر")
        in 95..99 -> tr("عواصف")
        else -> "—"
    }
}

/** Heart-rate zone 1..5 from % of max HR (50/60/70/80/90). */
fun hrZone(hr: Int?, maxHr: Int): Int {
    if (hr == null || hr <= 0) return 0
    val pct = hr * 100 / maxHr
    return when {
        pct >= 90 -> 5
        pct >= 80 -> 4
        pct >= 70 -> 3
        pct >= 60 -> 2
        pct >= 50 -> 1
        else -> 0
    }
}

/** Today's numbers the phone reads from Health Connect and pushes to the watch. */
@Serializable
data class TodaySummary(
    val steps: Long = 0,
    val stepGoal: Long = 10_000,
    val distanceM: Double = 0.0,
    val hr: Int? = null,
    val restingHr: Int? = null,
    val sleepMin: Int? = null,
    val exerciseMin: Int = 0,
    val exerciseGoalMin: Int = 60,
    val workoutsWeek: Int = 0,
    val workoutsMonth: Int = 0,
    val kcal: Int = 0,
    val waterMl: Int = 0,
    val waterGoalMl: Int = 2500,
    val updatedMs: Long = 0,
) {
    companion object {
        fun encode(s: TodaySummary) = WorkoutJson.json.encodeToString(serializer(), s)
        fun decode(s: String) = WorkoutJson.json.decodeFromString(serializer(), s)
    }
}

@Serializable
data class TrackPoint(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val acc: Float? = null,
)

@Serializable
data class Segment(val mode: Mode, val startMs: Long, val endMs: Long, val distanceM: Double)

@Serializable
data class Split(val km: Int, val durationSec: Int)

@Serializable
data class Workout(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val points: List<TrackPoint>,
    val segments: List<Segment>,
    val splits: List<Split>,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val source: String,            // "watch" | "phone"
    val hcSynced: Boolean = false,
    val type: WorkoutType = WorkoutType.WALK,
    val sprints: Int = 0,
    val maxSpeedMps: Double = 0.0,
    val pausedMs: Long = 0,
    val steps: Int = 0,
    val kcal: Int = 0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val weather: WeatherInfo? = null,
    val planName: String? = null,
) {
    /** Time actually moving (pauses removed). */
    val activeMs get() = (endMs - startMs - pausedMs).coerceAtLeast(0)
    val title get() = when {
        planName != null -> planName
        !type.walkRun || type == WorkoutType.HIKE || type == WorkoutType.TREADMILL -> type.ar
        runM > 50 && walkM > 50 -> tr("مشي + جري")
        runM > 50 -> tr("جري")
        else -> tr("مشي")
    }
    val avgPaceSec get() = if (distanceM > 50) ((activeMs / 1000.0) / (distanceM / 1000.0)).toInt() else null
    val avgCadence get() = if (activeMs > 60_000 && steps > 0) (steps * 60_000L / activeMs).toInt() else null
    val durationMs get() = endMs - startMs
    val runM get() = segments.filter { it.mode == Mode.RUN }.sumOf { it.distanceM }
    val walkM get() = segments.filter { it.mode == Mode.WALK }.sumOf { it.distanceM }
    val runMs get() = segments.filter { it.mode == Mode.RUN }.sumOf { it.endMs - it.startMs }
}

/** Where the custom run currently is. */
data class IntervalState(
    val index: Int,
    val total: Int,
    val step: IntervalStep,
    val next: IntervalStep?,
    val remainingSec: Int?,     // for time steps
    val remainingM: Int?,       // for distance steps
    val fraction: Float,
)

data class LiveStats(
    val mode: Mode,
    val elapsedMs: Long,
    val distanceM: Double,
    val paceSec: Int?,
    val avgPaceSec: Int?,
    val hr: Int?,
    val runM: Double,
    val walkM: Double,
    val gpsOk: Boolean,
    val type: WorkoutType = WorkoutType.WALK,
    val sprints: Int = 0,
    /** Segments so far, for the live walk/run bar. */
    val segments: List<Segment> = emptyList(),
    val paused: Boolean = false,
    val zone: Int = 0,
    val goalM: Int = 0,
    val goalSec: Int = 0,
    val steps: Int = 0,
    val cadence: Int? = null,
    val kcal: Int = 0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val altitudeM: Double? = null,
    val speedKmh: Double = 0.0,
    val interval: IntervalState? = null,
    val weather: WeatherInfo? = null,
)

/** Live state shared between the service and the UI of the same app. */
object LiveSession {
    val stats = MutableStateFlow<LiveStats?>(null)
    val lastWorkout = MutableStateFlow<Workout?>(null)
}

object WorkoutJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun encode(w: Workout): String = json.encodeToString(Workout.serializer(), w)
    fun decode(s: String): Workout = json.decodeFromString(Workout.serializer(), s)

    fun gzip(w: Workout): ByteArray = ByteArrayOutputStream().also { bos ->
        GZIPOutputStream(bos).use { it.write(encode(w).toByteArray()) }
    }.toByteArray()

    fun ungzip(bytes: ByteArray): Workout =
        decode(GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().decodeToString())
}

object Fmt {
    fun pace(sec: Int?): String = if (sec == null || sec <= 0 || sec > 3600) "--:--"
    else String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)

    fun dist(m: Double): String = String.format(Locale.US, "%.2f", m / 1000.0)

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }
}

/** Compact live snapshot the watch streams to the phone every 2 s. */
@Serializable
data class LiveLite(
    val type: WorkoutType,
    val mode: Mode,
    val elapsedMs: Long,
    val distanceM: Double,
    val paceSec: Int? = null,
    val hr: Int? = null,
    val zone: Int = 0,
    val steps: Int = 0,
    val cadence: Int? = null,
    val kcal: Int = 0,
    val paused: Boolean = false,
    val goalM: Int = 0,
    val goalSec: Int = 0,
    val ascentM: Double = 0.0,
    val speedKmh: Double = 0.0,
    val runM: Double = 0.0,
    val walkM: Double = 0.0,
    val sprints: Int = 0,
    val sentMs: Long = 0,
) {
    fun toStats() = LiveStats(
        mode = mode, elapsedMs = elapsedMs, distanceM = distanceM, paceSec = paceSec, avgPaceSec = null, hr = hr,
        runM = runM, walkM = walkM, gpsOk = true, type = type, sprints = sprints, paused = paused, zone = zone,
        goalM = goalM, goalSec = goalSec, steps = steps, cadence = cadence, kcal = kcal, ascentM = ascentM, speedKmh = speedKmh,
    )

    companion object {
        fun of(s: LiveStats) = LiveLite(
            s.type, s.mode, s.elapsedMs, s.distanceM, s.paceSec, s.hr, s.zone, s.steps, s.cadence, s.kcal, s.paused,
            s.goalM, s.goalSec, s.ascentM, s.speedKmh, s.runM, s.walkM, s.sprints, System.currentTimeMillis(),
        )
        fun encode(l: LiveLite) = WorkoutJson.json.encodeToString(serializer(), l)
        fun decode(s: String) = WorkoutJson.json.decodeFromString(serializer(), s)
    }
}
