package com.bachir.khutwa.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.concurrent.futures.await
import androidx.core.app.ServiceCompat
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bachir.khutwa.shared.ArabicCoach
import com.bachir.khutwa.shared.LiveSession
import com.bachir.khutwa.shared.Notif
import com.bachir.khutwa.shared.TrackPoint
import com.bachir.khutwa.shared.TtsSpeaker
import com.bachir.khutwa.shared.WorkoutRecorder
import com.bachir.khutwa.shared.WorkoutType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class ExerciseService : LifecycleService() {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_SPEAK = "speak"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"

        fun start(ctx: Context, type: WorkoutType) = send(ctx, ACTION_START, type)
        fun stop(ctx: Context) = send(ctx, ACTION_STOP)
        fun speakStatus(ctx: Context) = send(ctx, ACTION_SPEAK)
        fun pause(ctx: Context) = send(ctx, ACTION_PAUSE)
        fun resume(ctx: Context) = send(ctx, ACTION_RESUME)

        private fun send(ctx: Context, action: String, type: WorkoutType? = null) {
            val i = Intent(ctx, ExerciseService::class.java).setAction(action)
            type?.let { i.putExtra("type", it.name) }
            if (action == ACTION_START) ctx.startForegroundService(i) else ctx.startService(i)
        }
    }

    private lateinit var client: ExerciseClient
    private lateinit var speaker: TtsSpeaker
    private var recorder: WorkoutRecorder? = null
    private var ticker: Job? = null
    /** Coach settings pushed from the phone (defaults if the phone hasn't sent any yet). */
    private val coach get() = WatchToday.coach.value

    private val bootInstant: Instant
        get() = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())

    override fun onCreate() {
        super.onCreate()
        client = HealthServices.getClient(this).exerciseClient
        speaker = TtsSpeaker(this).apply { fallback = { PhoneLink.speakOnPhone(this@ExerciseService, it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startWorkout(
                runCatching { WorkoutType.valueOf(intent.getStringExtra("type") ?: "") }.getOrDefault(WorkoutType.WALK)
            )
            ACTION_STOP -> stopWorkout()
            ACTION_SPEAK -> speaker.speak(ArabicCoach.status(LiveSession.stats.value))
            ACTION_PAUSE -> recorder?.let { r ->
                r.pause(System.currentTimeMillis())
                lifecycleScope.launch { runCatching { client.pauseExerciseAsync().await() } }
                LiveSession.stats.value = r.stats(System.currentTimeMillis())
                if (coach.coachOn) speaker.speak(ArabicCoach.paused())
            }
            ACTION_RESUME -> recorder?.let { r ->
                r.resume(System.currentTimeMillis())
                lifecycleScope.launch { runCatching { client.resumeExerciseAsync().await() } }
                LiveSession.stats.value = r.stats(System.currentTimeMillis())
                if (coach.coachOn) speaker.speak(ArabicCoach.resumed())
            }
        }
        return START_NOT_STICKY
    }

    private fun startWorkout(type: WorkoutType) {
        if (recorder != null) return
        goForeground()
        val now = System.currentTimeMillis()
        val r = WorkoutRecorder(UUID.randomUUID().toString(), now, "watch", type, coach)
        recorder = r
        LiveSession.lastWorkout.value = null
        LiveSession.stats.value = r.stats(now)
        val hsType = when (type) {
            WorkoutType.WALK -> ExerciseType.WALKING
            WorkoutType.RUN -> ExerciseType.RUNNING
            WorkoutType.FOOTBALL -> ExerciseType.SOCCER
        }

        lifecycleScope.launch {
            val caps = client.getCapabilitiesAsync().await()
            val typeCaps = caps.getExerciseTypeCapabilities(hsType)
            val wanted: Set<DataType<*, *>> = setOf<DataType<*, *>>(
                DataType.HEART_RATE_BPM, DataType.SPEED, DataType.LOCATION, DataType.STEPS_PER_MINUTE,
            ).filter { it in typeCaps.supportedDataTypes }.toSet()

            client.setUpdateCallback(callback)
            val config = ExerciseConfig.builder(hsType)
                .setDataTypes(wanted)
                .setIsGpsEnabled(DataType.LOCATION in wanted)
                .setIsAutoPauseAndResumeEnabled(false)
                .build()
            client.startExerciseAsync(config).await()
            if (coach.coachOn) speaker.speak(ArabicCoach.start(type))
        }

        ticker = lifecycleScope.launch {
            while (isActive) {
                recorder?.let { LiveSession.stats.value = it.stats(System.currentTimeMillis()) }
                delay(1000)
            }
        }
    }

    private fun stopWorkout() {
        val r = recorder ?: run { stopSelf(); return }
        recorder = null
        ticker?.cancel()
        lifecycleScope.launch {
            runCatching { client.endExerciseAsync().await() }
            runCatching { client.clearUpdateCallbackAsync(callback).await() }
            val w = r.finish(System.currentTimeMillis())
            LiveSession.stats.value = null
            LiveSession.lastWorkout.value = w
            if (coach.coachOn) speaker.speak(ArabicCoach.finish(w))
            WatchStore.save(this@ExerciseService, w)
            runCatching { PhoneLink.sendWorkout(this@ExerciseService, w) }
            delay(8_000) // let the summary finish speaking
            ServiceCompat.stopForeground(this@ExerciseService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val r = recorder ?: return
            val boot = bootInstant
            val m = update.latestMetrics

            m.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { dp ->
                r.onHeartRate(dp.getTimeInstant(boot).toEpochMilli(), dp.value.toInt()).forEach { speaker.speak(ArabicCoach.phrase(it)) }
            }
            m.getData(DataType.STEPS_PER_MINUTE).lastOrNull()?.let {
                r.onCadence(it.getTimeInstant(boot).toEpochMilli(), it.value.toDouble())
            }
            m.getData(DataType.SPEED).forEach { r.onSpeed(it.getTimeInstant(boot).toEpochMilli(), it.value) }
            m.getData(DataType.LOCATION).forEach { dp ->
                val l = dp.value
                val p = TrackPoint(
                    t = dp.getTimeInstant(boot).toEpochMilli(),
                    lat = l.latitude, lon = l.longitude,
                    alt = l.altitude.takeIf { !it.isNaN() },
                )
                r.onLocation(p).forEach { speaker.speak(ArabicCoach.phrase(it)) }
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {}
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {}
    }

    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val type = if (Build.VERSION.SDK_INT >= 34)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        ServiceCompat.startForeground(this, Notif.ID, Notif.build(this, "تمرين شغال", open), type)
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
