package com.bachir.khutwa.mobile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bachir.khutwa.shared.ArabicCoach
import com.bachir.khutwa.shared.LiveSession
import com.bachir.khutwa.shared.Notif
import com.bachir.khutwa.shared.TrackPoint
import com.bachir.khutwa.shared.TtsSpeaker
import com.bachir.khutwa.shared.WorkoutJson
import com.bachir.khutwa.shared.WorkoutRecorder
import com.bachir.khutwa.shared.WorkoutType
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Workout recorded on the phone only (no watch). */
class PhoneWorkoutService : LifecycleService(), SensorEventListener {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_SPEAK = "speak"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        fun start(ctx: Context, type: WorkoutType, goalM: Int) = ctx.startForegroundService(
            Intent(ctx, PhoneWorkoutService::class.java).setAction(ACTION_START)
                .putExtra("type", type.name).putExtra("goal", goalM)
        )
        fun stop(ctx: Context) = send(ctx, ACTION_STOP)
        fun speakStatus(ctx: Context) = send(ctx, ACTION_SPEAK)
        fun pause(ctx: Context) = send(ctx, ACTION_PAUSE)
        fun resume(ctx: Context) = send(ctx, ACTION_RESUME)
        private fun send(ctx: Context, a: String) = ctx.startService(Intent(ctx, PhoneWorkoutService::class.java).setAction(a))
    }

    private lateinit var speaker: TtsSpeaker
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private var recorder: WorkoutRecorder? = null
    private var ticker: Job? = null
    private val steps = ArrayDeque<Long>()
    private val coachOn get() = Settings.current.coachOn

    override fun onCreate() {
        super.onCreate()
        speaker = TtsSpeaker(this).apply { rate = Settings.current.voiceRate }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> startWorkout(
                runCatching { WorkoutType.valueOf(intent.getStringExtra("type") ?: "") }.getOrDefault(WorkoutType.WALK),
                intent.getIntExtra("goal", 0),
            )
            ACTION_STOP -> stopWorkout()
            ACTION_SPEAK -> speaker.speak(ArabicCoach.status(LiveSession.stats.value))
            ACTION_PAUSE -> recorder?.let { it.pause(now); publish(); if (coachOn) speaker.speak(ArabicCoach.paused()) }
            ACTION_RESUME -> recorder?.let { it.resume(now); publish(); if (coachOn) speaker.speak(ArabicCoach.resumed()) }
        }
        return START_NOT_STICKY
    }

    private fun publish() { recorder?.let { LiveSession.stats.value = it.stats(System.currentTimeMillis()) } }

    @SuppressLint("MissingPermission")
    private fun startWorkout(type: WorkoutType, goalM: Int) {
        if (recorder != null) return
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        ServiceCompat.startForeground(
            this, Notif.ID, Notif.build(this, "تمرين شغال", open),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        val now = System.currentTimeMillis()
        recorder = WorkoutRecorder(UUID.randomUUID().toString(), now, "phone", type, Settings.current.coach(goalM))
        LiveSession.lastWorkout.value = null
        publish()

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L).build()
        fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (coachOn) speaker.speak(ArabicCoach.start(type))

        ticker = lifecycleScope.launch {
            while (isActive) { publish(); delay(1000) }
        }
    }

    private fun stopWorkout() {
        val r = recorder ?: run { stopSelf(); return }
        recorder = null
        ticker?.cancel()
        fused.removeLocationUpdates(locationCallback)
        sensors.unregisterListener(this)
        val w = r.finish(System.currentTimeMillis())
        LiveSession.stats.value = null
        LiveSession.lastWorkout.value = w
        if (coachOn) speaker.speak(ArabicCoach.finish(w))
        WorkoutStore.saveAndSync(this, w)
        lifecycleScope.launch {
            delay(8_000)
            ServiceCompat.stopForeground(this@PhoneWorkoutService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val r = recorder ?: return
            result.locations.forEach { l ->
                val p = TrackPoint(
                    t = l.time, lat = l.latitude, lon = l.longitude,
                    alt = if (l.hasAltitude()) l.altitude else null,
                    acc = if (l.hasAccuracy()) l.accuracy else null,
                )
                r.onLocation(p, if (l.hasSpeed()) l.speed.toDouble() else null)
                    .forEach { speaker.speak(ArabicCoach.phrase(it)) }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val t = System.currentTimeMillis()
        steps.addLast(t)
        while (steps.isNotEmpty() && steps.first() < t - 15_000) steps.removeFirst()
        if (steps.size >= 6) {
            val spanSec = (t - steps.first()) / 1000.0
            if (spanSec > 3) recorder?.onCadence(t, (steps.size - 1) * 60.0 / spanSec)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}

/** Receives finished workouts and voice lines from the watch. */
class WatchListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        // Pull what we need before the buffer is released.
        val items = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path?.startsWith("/khutwa/workout/") == true }
            .mapNotNull { ev ->
                val item = ev.dataItem.freeze()
                DataMapItem.fromDataItem(item).dataMap.getAsset("data")?.let { item.uri to it }
            }

        items.forEach { (uri, asset) ->
            AppScope.launch {
                runCatching {
                    val client = Wearable.getDataClient(applicationContext)
                    val bytes = client.getFdForAsset(asset).await().inputStream.use { it.readBytes() }
                    val w = WorkoutJson.ungzip(bytes)
                    WorkoutStore.saveAndSync(applicationContext, w)
                    client.deleteDataItems(uri).await()
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/khutwa/speak") PhoneVoice.speak(applicationContext, event.data.decodeToString())
    }
}
