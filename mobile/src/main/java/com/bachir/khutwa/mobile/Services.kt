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
import com.bachir.khutwa.shared.Coach
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
        fun start(ctx: Context, type: WorkoutType, goalM: Int, goalSec: Int = 0) = ctx.startForegroundService(
            Intent(ctx, PhoneWorkoutService::class.java).setAction(ACTION_START)
                .putExtra("type", type.name).putExtra("goal", goalM).putExtra("goalSec", goalSec)
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
        val st = Settings.current
        speaker = TtsSpeaker(this, st.english, st.voiceName).apply { rate = st.voiceRate; pitch = st.voicePitch }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val now = System.currentTimeMillis()
        when (intent?.action) {
            ACTION_START -> startWorkout(
                runCatching { WorkoutType.valueOf(intent.getStringExtra("type") ?: "") }.getOrDefault(WorkoutType.WALK),
                intent.getIntExtra("goal", 0),
                intent.getIntExtra("goalSec", 0),
            )
            ACTION_STOP -> stopWorkout()
            ACTION_SPEAK -> speaker.speak(Coach.status(LiveSession.stats.value))
            ACTION_PAUSE -> recorder?.let { it.pause(now); publish(); LiveNotif.update(this, LiveSession.stats.value); if (coachOn) speaker.speak(Coach.paused()) }
            ACTION_RESUME -> recorder?.let { it.resume(now); publish(); LiveNotif.update(this, LiveSession.stats.value); if (coachOn) speaker.speak(Coach.resumed()) }
        }
        return START_NOT_STICKY
    }

    private fun publish() { recorder?.let { LiveSession.stats.value = it.stats(System.currentTimeMillis()) } }

    @SuppressLint("MissingPermission")
    private fun startWorkout(type: WorkoutType, goalM: Int, goalSec: Int) {
        if (recorder != null) return
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        ServiceCompat.startForeground(
            this, LiveNotif.ID, LiveNotif.build(this, null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        val now = System.currentTimeMillis()
        val r = WorkoutRecorder(UUID.randomUUID().toString(), now, "phone", type, Settings.current.coach(goalM, goalSec))
        recorder = r
        weatherAsked = false
        LiveSession.lastWorkout.value = null
        publish()

        if (type.gps) {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(1000L).build()
            fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } else fetchWeatherFromLastLocation()
        sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
        sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            r.elevationFromBaro = true
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (coachOn) speaker.speak(Coach.start(type))
        r.firstInterval()?.let { if (coachOn) speaker.speak(Coach.phrase(it)) }

        ticker = lifecycleScope.launch {
            var n = 0
            while (isActive) {
                recorder?.tick(System.currentTimeMillis())?.forEach { speaker.speak(Coach.phrase(it)) }
                publish()
                if (n++ % 3 == 0) LiveNotif.update(this@PhoneWorkoutService, LiveSession.stats.value)
                delay(1000)
            }
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
        if (coachOn) speaker.speak(Coach.finish(w))
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
                    .forEach { speaker.speak(Coach.phrase(it)) }
                if (!weatherAsked && (!l.hasAccuracy() || l.accuracy < 100)) { weatherAsked = true; fetchWeather(l.latitude, l.longitude) }
            }
        }
    }

    private var weatherAsked = false
    private var lastBaroT = 0L

    private fun fetchWeather(lat: Double, lon: Double) {
        if (!Settings.current.weatherOn) return
        AppScope.launch { WeatherClient.fetch(lat, lon)?.let { w -> recorder?.setWeather(w) } }
    }

    @SuppressLint("MissingPermission")
    private fun fetchWeatherFromLastLocation() {
        fused.lastLocation.addOnSuccessListener { l -> l?.let { weatherAsked = true; fetchWeather(it.latitude, it.longitude) } }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val t = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                recorder?.onStepTotal(t, event.values[0].toLong())?.forEach { speaker.speak(Coach.phrase(it)) }
                return
            }
            Sensor.TYPE_PRESSURE -> {
                if (t - lastBaroT >= 1000) {
                    lastBaroT = t
                    recorder?.onAltitude(SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]).toDouble())
                }
                return
            }
        }
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
                    var w = WorkoutJson.ungzip(bytes)
                    val first = w.points.firstOrNull()
                    if (w.weather == null && first != null && System.currentTimeMillis() - w.startMs < 4 * 3600_000L && Settings.current.weatherOn)
                        WeatherClient.fetch(first.lat, first.lon)?.let { w = w.copy(weather = it) }
                    WorkoutStore.saveAndSync(applicationContext, w)
                    client.deleteDataItems(uri).await()
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/khutwa/speak" -> PhoneVoice.speak(applicationContext, event.data.decodeToString())
            "/khutwa/media" -> Music.key(applicationContext, event.data.decodeToString())
            "/khutwa/live" -> RemoteLive.receive(applicationContext, event.sourceNodeId, event.data.decodeToString())
        }
    }
}


/** A workout running on the watch, mirrored live on the phone (screen, lock screen and Now Bar). */
object RemoteLive {
    const val NOTIF_ID = 8
    val state = kotlinx.coroutines.flow.MutableStateFlow<com.bachir.khutwa.shared.LiveLite?>(null)
    private var node: String? = null

    fun receive(ctx: Context, nodeId: String, body: String) {
        node = nodeId
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (body == "end") {
            state.value = null
            nm.cancel(NOTIF_ID)
            return
        }
        val l = runCatching { com.bachir.khutwa.shared.LiveLite.decode(body) }.getOrNull() ?: return
        state.value = l
        runCatching { nm.notify(NOTIF_ID, LiveNotif.build(ctx, l.toStats(), remote = true)) }
    }

    /** Is the mirrored workout still alive (watch sends every 2 s)? */
    fun fresh(l: com.bachir.khutwa.shared.LiveLite?) = l != null && System.currentTimeMillis() - l.sentMs < 15_000

    fun send(ctx: Context, cmd: String) = AppScope.launch {
        runCatching {
            val nodes = node?.let { listOf(it) } ?: Wearable.getNodeClient(ctx).connectedNodes.await().map { it.id }
            nodes.forEach { Wearable.getMessageClient(ctx).sendMessage(it, "/khutwa/cmd", cmd.toByteArray()).await() }
        }
    }
}

/** Watch connection status for the home screen. */
object WatchLink {
    val connected = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    suspend fun check(ctx: Context) {
        connected.value = runCatching { Wearable.getNodeClient(ctx).connectedNodes.await().firstOrNull { it.isNearby }?.displayName }.getOrNull()
    }
}
