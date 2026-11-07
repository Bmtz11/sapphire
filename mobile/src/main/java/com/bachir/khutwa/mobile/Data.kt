package com.bachir.khutwa.mobile

import android.app.Application
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.TtsSpeaker
import com.bachir.khutwa.shared.Workout
import com.bachir.khutwa.shared.WorkoutJson
import com.bachir.khutwa.shared.WorkoutType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId

class KhutwaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WorkoutStore.refresh(this)
    }
}

val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Workouts saved as JSON files on the phone. */
object WorkoutStore {
    val workouts = MutableStateFlow<List<Workout>>(emptyList())

    private fun dir(ctx: Context) = File(ctx.filesDir, "workouts").apply { mkdirs() }

    fun refresh(ctx: Context) {
        workouts.value = dir(ctx).listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { WorkoutJson.decode(it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.startMs } ?: emptyList()
    }

    fun save(ctx: Context, w: Workout) {
        File(dir(ctx), "${w.id}.json").writeText(WorkoutJson.encode(w))
        refresh(ctx)
    }

    fun get(id: String) = workouts.value.firstOrNull { it.id == id }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.json").delete(); refresh(ctx)
    }

    /** Save, then push to Health Connect. Called for phone workouts and ones arriving from the watch. */
    fun saveAndSync(ctx: Context, w: Workout) {
        save(ctx, w)
        AppScope.launch { if (HealthSync.write(ctx, w)) save(ctx, w.copy(hcSynced = true)) }
    }

    fun syncPending(ctx: Context) = AppScope.launch {
        workouts.value.filter { !it.hcSynced }.forEach { w ->
            if (HealthSync.write(ctx, w)) save(ctx, w.copy(hcSynced = true))
        }
    }
}

/** Writes sessions (with walk/run segments + GPS route) into Health Connect → Samsung Health. */
object HealthSync {
    val WRITE = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
    )
    val PERMISSIONS = WRITE + TodayReader.READ

    fun available(ctx: Context) =
        HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(ctx: Context): Boolean = available(ctx) &&
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(WRITE)

    suspend fun write(ctx: Context, w: Workout): Boolean = runCatching {
        if (!granted(ctx)) return false
        val client = HealthConnectClient.getOrCreate(ctx)
        val rules = ZoneId.systemDefault().rules
        val start = Instant.ofEpochMilli(w.startMs)
        val end = Instant.ofEpochMilli(w.endMs)
        val device = Device(type = if (w.source == "watch") Device.TYPE_WATCH else Device.TYPE_PHONE)
        val hasRun = w.runM > 50
        val football = w.type == WorkoutType.FOOTBALL
        val type = when {
            football -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
            hasRun || w.type == WorkoutType.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            else -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        }

        val route = w.points.filter { it.t in w.startMs..w.endMs }.map {
            ExerciseRoute.Location(
                time = Instant.ofEpochMilli(it.t),
                latitude = it.lat,
                longitude = it.lon,
                horizontalAccuracy = it.acc?.let { a -> Length.meters(a.toDouble()) },
                altitude = it.alt?.let(Length::meters),
            )
        }.takeIf { it.isNotEmpty() }?.let { ExerciseRoute(it) }

        val segments = w.segments.filter { it.endMs > it.startMs }.map {
            ExerciseSegment(
                startTime = Instant.ofEpochMilli(it.startMs),
                endTime = Instant.ofEpochMilli(it.endMs),
                segmentType = if (it.mode == Mode.RUN) ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING
                else ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING,
            )
        }

        fun session(segs: List<ExerciseSegment>) = ExerciseSessionRecord(
            startTime = start, startZoneOffset = rules.getOffset(start),
            endTime = end, endZoneOffset = rules.getOffset(end),
            metadata = Metadata.activelyRecorded(device = device, clientRecordId = w.id, clientRecordVersion = 1L),
            exerciseType = type,
            title = w.title,
            segments = segs,
            exerciseRoute = route,
        )
        // If Health Connect rejects the segment mix for this exercise type, save the session without segments.
        val record = if (football) session(emptyList())
        else runCatching { session(segments) }.getOrElse { session(emptyList()) }

        val records = buildList {
            add(record)
            if (w.distanceM >= 1) add(
                DistanceRecord(
                    startTime = start, startZoneOffset = rules.getOffset(start),
                    endTime = end, endZoneOffset = rules.getOffset(end),
                    distance = Length.meters(w.distanceM),
                    metadata = Metadata.activelyRecorded(device = device, clientRecordId = w.id + "-d", clientRecordVersion = 1L),
                )
            )
        }
        client.insertRecords(records)
        true
    }.getOrDefault(false)
}

object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("khutwa", Context.MODE_PRIVATE)
    fun coach(ctx: Context) = p(ctx).getBoolean("coach", true)
    fun setCoach(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("coach", on).apply()
}

/** Phone voice used when the watch relays coach lines. */
object PhoneVoice {
    @Volatile private var speaker: TtsSpeaker? = null
    fun speak(ctx: Context, text: String) {
        val s = speaker ?: synchronized(this) { speaker ?: TtsSpeaker(ctx).also { speaker = it } }
        s.speak(text)
    }
}
