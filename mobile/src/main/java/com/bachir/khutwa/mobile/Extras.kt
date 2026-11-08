package com.bachir.khutwa.mobile

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import com.bachir.khutwa.shared.TodaySummary
import com.bachir.khutwa.shared.WeatherInfo
import com.bachir.khutwa.shared.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

/** Open-Meteo: free current weather, no key. */
object WeatherClient {
    val now = MutableStateFlow<WeatherInfo?>(null)

    suspend fun fetch(lat: Double, lon: Double): WeatherInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code",
                    lat, lon,
                )
            )
            val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 6000 }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(body).getJSONObject("current")
            WeatherInfo(
                tempC = cur.getDouble("temperature_2m"),
                feelsC = cur.getDouble("apparent_temperature"),
                humidity = cur.getInt("relative_humidity_2m"),
                windKmh = cur.getDouble("wind_speed_10m"),
                code = cur.getInt("weather_code"),
            ).also { now.value = it }
        }.getOrNull()
    }
}

/**
 * Live phone step counter for today (hardware TYPE_STEP_COUNTER), so the number moves while the app is open.
 * Samsung Health's figure (phone + watch) is used when it is higher.
 */
object PhoneSteps : SensorEventListener {
    val today = MutableStateFlow(0L)
    private var registered = false
    private lateinit var prefs: android.content.SharedPreferences

    fun start(ctx: Context) {
        if (registered) return
        prefs = ctx.getSharedPreferences("steps", Context.MODE_PRIVATE)
        val sm = ctx.getSystemService(SensorManager::class.java)
        val s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        registered = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop(ctx: Context) {
        if (!registered) return
        ctx.getSystemService(SensorManager::class.java).unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val total = e.values[0].toLong()
        val day = LocalDate.now().toString()
        var base = prefs.getLong("base_$day", -1)
        val last = prefs.getLong("last", -1)
        if (base < 0) {
            // first reading today: start from the last known reading (counts steps since midnight if the app ran then)
            base = if (last in 0..total && prefs.getString("lastDay", "") == LocalDate.now().minusDays(1).toString()) last else total
            prefs.edit().putLong("base_$day", base).apply()
        }
        if (total < base) { base = 0; prefs.edit().putLong("base_$day", 0).apply() } // phone rebooted
        prefs.edit().putLong("last", total).putString("lastDay", day).apply()
        today.value = total - base
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

/** Water log: written to Health Connect (Samsung Health reads it), local fallback. */
object Water {
    val todayMl = MutableStateFlow(0)
    val WRITE = HealthPermission.getWritePermission(HydrationRecord::class)
    val READ = HealthPermission.getReadPermission(HydrationRecord::class)
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("water", Context.MODE_PRIVATE)
    private fun key() = "ml_${LocalDate.now()}"

    suspend fun refresh(ctx: Context) {
        val local = prefs(ctx).getInt(key(), 0)
        var total = local
        if (HealthSync.available(ctx)) runCatching {
            val client = HealthConnectClient.getOrCreate(ctx)
            if (READ in client.permissionController.getGrantedPermissions()) {
                val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val v = client.aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), TimeRangeFilter.between(start, Instant.now())))[HydrationRecord.VOLUME_TOTAL]
                total = maxOf(local, v?.inMilliliters?.toInt() ?: 0)
            }
        }
        todayMl.value = total
    }

    suspend fun add(ctx: Context, ml: Int) {
        val p = prefs(ctx)
        val n = (p.getInt(key(), 0) + ml).coerceAtLeast(0)
        p.edit().putInt(key(), n).apply()
        todayMl.value = (todayMl.value + ml).coerceAtLeast(0)
        if (ml > 0 && HealthSync.available(ctx)) runCatching {
            val client = HealthConnectClient.getOrCreate(ctx)
            if (WRITE in client.permissionController.getGrantedPermissions()) {
                val end = Instant.now()
                val zone = ZoneId.systemDefault().rules.getOffset(end)
                client.insertRecords(listOf(HydrationRecord(
                    startTime = end.minusSeconds(60), startZoneOffset = zone, endTime = end, endZoneOffset = zone,
                    volume = Volume.milliliters(ml.toDouble()),
                    metadata = Metadata.manualEntry(device = Device(type = Device.TYPE_PHONE)),
                )))
            }
        }
    }
}

/** Suggestions built from today's data — short, specific, Arabic. */
object Tips {
    data class Tip(val title: String, val body: String, val kind: Kind)
    enum class Kind { WATER, SLEEP, STEPS, HEAT, RECOVERY, STREAK, PACE }

    fun build(t: TodaySummary, water: Int, waterGoal: Int, workouts: List<Workout>, streak: Int, weather: WeatherInfo?): List<Tip> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val out = mutableListOf<Tip>()
        weather?.let {
            if (it.feelsC >= 35) out += Tip("الجو حار ${it.feelsC.toInt()}°", "تمرّن الصبح بدري أو بعد المغرب، وخذ موية معك.", Kind.HEAT)
            else if (it.feelsC in 15.0..26.0) out += Tip("الجو مناسب للجري", "${it.tempC.toInt()}° و${it.ar}. فرصة لتمرين برا.", Kind.HEAT)
        }
        t.sleepMin?.let {
            if (it < 360) out += Tip("نومك قليل أمس", "نمت ${it / 60} ساعات و${it % 60} دقيقة. خلي تمرين اليوم خفيف، مشي أو جري هادي.", Kind.SLEEP)
        }
        if (water < waterGoal * (hour - 7).coerceIn(0, 14) / 14 - 250 && hour in 10..22)
            out += Tip("اشرب موية", "شربت ${water} مل من ${waterGoal}. كاس الحين يرفع طاقتك.", Kind.WATER)
        val left = t.stepGoal - t.steps
        if (hour >= 17 && left in 1..4000) out += Tip("قريب من هدف الخطوات", "باقي ${left} خطوة — مشية ${(left / 110).coerceAtLeast(5)} دقيقة تكفي.", Kind.STEPS)
        val last = workouts.firstOrNull()
        if (last != null && last.avgHr != null && last.maxHr != null && last.maxHr >= 175 &&
            System.currentTimeMillis() - last.endMs < 20 * 3600_000L)
            out += Tip("يوم استشفاء", "آخر تمرين كان قوي (نبض ${last.maxHr}). اليوم مشي أو تمدد أفضل.", Kind.RECOVERY)
        if (streak >= 2) out += Tip("$streak أيام ورا بعض", "لا تكسر السلسلة — حتى ١٥ دقيقة مشي تحسب.", Kind.STREAK)
        val runs = workouts.filter { it.runM > 1000 }.take(5)
        if (runs.size >= 3) {
            val avg = runs.mapNotNull { it.avgPaceSec }.average()
            if (!avg.isNaN()) out += Tip("وتيرتك المعتادة", "معدلك في آخر ${runs.size} جريات ${com.bachir.khutwa.shared.Fmt.pace(avg.toInt())} /كم. جرّب جري مخصص للسرعات.", Kind.PACE)
        }
        if (out.isEmpty()) out += Tip("ابدأ بخطوة", "مشية ٢٠ دقيقة اليوم ترفع نشاطك وتحسن نومك.", Kind.STEPS)
        return out
    }
}
