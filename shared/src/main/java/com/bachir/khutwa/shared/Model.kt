package com.bachir.khutwa.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class Mode { WALK, RUN }

/** What the user picked before starting. */
enum class WorkoutType(val ar: String) { WALK("مشي"), RUN("جري"), FOOTBALL("كرة قدم") }

/** Workout settings shared by phone and watch (the phone pushes its copy to the watch). */
@Serializable
data class CoachConfig(
    val coachOn: Boolean = true,
    val announceEveryM: Int = 1000,     // 500 or 1000
    val maxHr: Int = 190,
    val hrAlertZone: Int = 0,           // 0 = off, else alert when above this zone (1..4)
    val goalM: Int = 0,                 // 0 = no goal for the session
    val runSpeedKmh: Double = 7.4,
    val autoPause: Boolean = false,
) {
    companion object {
        fun encode(c: CoachConfig) = WorkoutJson.json.encodeToString(serializer(), c)
        fun decode(s: String) = WorkoutJson.json.decodeFromString(serializer(), s)
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
) {
    /** Time actually moving (pauses removed). */
    val activeMs get() = (endMs - startMs - pausedMs).coerceAtLeast(0)
    val title get() = when {
        type == WorkoutType.FOOTBALL -> "كرة قدم"
        runM > 50 && walkM > 50 -> "مشي + جري"
        runM > 50 -> "جري"
        else -> "مشي"
    }
    val avgPaceSec get() = if (distanceM > 50) ((activeMs / 1000.0) / (distanceM / 1000.0)).toInt() else null
    val durationMs get() = endMs - startMs
    val runM get() = segments.filter { it.mode == Mode.RUN }.sumOf { it.distanceM }
    val walkM get() = segments.filter { it.mode == Mode.WALK }.sumOf { it.distanceM }
    val runMs get() = segments.filter { it.mode == Mode.RUN }.sumOf { it.endMs - it.startMs }
}

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
)

/** Live state shared between the service and the UI of the same app. */
object LiveSession {
    val stats = MutableStateFlow<LiveStats?>(null)
    val lastWorkout = MutableStateFlow<Workout?>(null)
}

object WorkoutJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
    else "%d:%02d".format(sec / 60, sec % 60)

    fun dist(m: Double): String = "%.2f".format(m / 1000.0)

    fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        else "%02d:%02d".format(s / 60, s % 60)
    }
}
