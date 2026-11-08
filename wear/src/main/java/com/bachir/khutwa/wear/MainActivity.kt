package com.bachir.khutwa.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.bachir.khutwa.shared.CoachConfig
import com.bachir.khutwa.shared.Fmt
import com.bachir.khutwa.shared.LiveSession
import com.bachir.khutwa.shared.LiveStats
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.TodaySummary
import com.bachir.khutwa.shared.Workout
import com.bachir.khutwa.shared.WorkoutType
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ───────── look (same as the phone) ─────────

val Readex = FontFamily(
    Font(R.font.readex_regular, FontWeight.Normal),
    Font(R.font.readex_medium, FontWeight.Medium),
    Font(R.font.readex_semibold, FontWeight.SemiBold),
    Font(R.font.readex_bold, FontWeight.Bold),
)
val Saira = FontFamily(Font(R.font.saira_bold, FontWeight.Bold), Font(R.font.saira_extrabold, FontWeight.ExtraBold))
fun txt(size: Int, color: Color = Color.White, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Readex, fontSize = size.sp, color = color, fontWeight = weight)
fun doto(size: TextUnit) = TextStyle(fontFamily = Saira, fontWeight = FontWeight.ExtraBold, fontSize = size, color = Color.White, textDirection = TextDirection.Ltr)

val Bg = Color(0xFF070708)
val Muted = Color(0xFFA3A6AD)
val WalkBlue = Color(0xFF4FA3F7)
val RunOrange = Color(0xFFFF7A1A)
val HeartRed = Color(0xFFFF5A4E)

data class Blob(val color: Color, val cx: Float, val cy: Float, val r: Float)
fun Modifier.blobs(vararg list: Blob) = drawBehind {
    list.forEach { b ->
        val c = Offset(size.width * b.cx, size.height * b.cy)
        val rad = size.minDimension * b.r
        drawCircle(Brush.radialGradient(listOf(b.color, b.color.copy(alpha = 0f)), c, rad), rad, c)
    }
}

enum class Tone(val base: Brush, val blobs: List<Blob>) {
    STEPS(Brush.linearGradient(listOf(Color(0xFF4A3E5E), Color(0xFF7B4F66), Color(0xFFC77370))), listOf(Blob(Color(0xCC1E0A28), 0.1f, 0.05f, 0.9f))),
    HEART(Brush.linearGradient(listOf(Color(0xFF2A2F2E), Color(0xFF2A2F2E))), listOf(Blob(Color(0x8C96B4A0), 0.9f, 0.0f, 1.2f), Blob(Color(0xFF050505), 0.1f, 1.0f, 0.8f))),
    SLEEP(Brush.linearGradient(listOf(Color(0xFF1C2C8A), Color(0xFF1C2C8A))), listOf(Blob(Color(0x995A78FF), 0.9f, 0.0f, 1.1f))),
    WALK(Brush.linearGradient(listOf(Color(0xFF2A6FD6), Color(0xFF2A6FD6))), listOf(Blob(Color(0xFF071433), 0.2f, 0.2f, 0.85f), Blob(Color(0xCC8CBEFF), 1.0f, 1.2f, 0.8f))),
    RUN(Brush.linearGradient(listOf(Color(0xFFC4532E), Color(0xFFC4532E))), listOf(Blob(Color(0xE63C50F0), 0.2f, 0.25f, 0.6f))),
    FOOTBALL(Brush.linearGradient(listOf(Color(0xFF2E4A3A), Color(0xFF2E4A3A))), listOf(Blob(Color(0x8CA0D278), 0.9f, 0.0f, 1.2f), Blob(Color(0xFF050805), 0.1f, 1.0f, 0.8f))),
    INTERVALS(Brush.linearGradient(listOf(Color(0xFF6A2C8C), Color(0xFF6A2C8C))), listOf(Blob(Color(0xCCFF5AA5), 0.9f, 0.1f, 1.0f))),
    HIKE(Brush.linearGradient(listOf(Color(0xFF5A4A2E), Color(0xFF5A4A2E))), listOf(Blob(Color(0x99E0B060), 0.85f, 0.05f, 1.1f))),
    CYCLING(Brush.linearGradient(listOf(Color(0xFF0F5A5E), Color(0xFF0F5A5E))), listOf(Blob(Color(0x9946E0D0), 0.85f, 0.05f, 1.1f))),
}

@Composable
fun ToneBox(tone: Tone, modifier: Modifier, radius: Dp, pad: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(tone.base).blobs(*tone.blobs.toTypedArray()).padding(pad), content = content)
}

// ───────── today's numbers pushed from the phone ─────────

object WatchToday {
    val today = MutableStateFlow<TodaySummary?>(null)
    val coach = MutableStateFlow(CoachConfig())
    fun read(item: com.google.android.gms.wearable.DataItem) {
        if (item.uri.path != "/khutwa/today") return
        val map = DataMapItem.fromDataItem(item).dataMap
        map.getString("json")?.let { runCatching { today.value = TodaySummary.decode(it) } }
        map.getString("coach")?.let { runCatching { coach.value = CoachConfig.decode(it) } }
    }
}

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp(onRunning = ::keepScreen) }
    }

    override fun onResume() {
        super.onResume()
        val dc = Wearable.getDataClient(this)
        dc.addListener(this)
        dc.dataItems.addOnSuccessListener { buf -> buf.forEach { WatchToday.read(it.freeze()) }; buf.release() }
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(events: com.google.android.gms.wearable.DataEventBuffer) {
        events.filter { it.type == DataEvent.TYPE_CHANGED }.forEach { WatchToday.read(it.dataItem.freeze()) }
    }

    private fun keepScreen(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 36) add("android.permission.health.READ_HEART_RATE")
    else add(Manifest.permission.BODY_SENSORS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
fun WearApp(onRunning: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val stats by LiveSession.stats.collectAsState()
    val last by LiveSession.lastWorkout.collectAsState()
    val today by WatchToday.today.collectAsState()
    var pending by remember { mutableStateOf(WorkoutType.WALK) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) ExerciseService.start(ctx, pending)
    }
    LaunchedEffect(stats != null) { onRunning(stats != null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
                val s = stats
                val w = last
                when {
                    s != null -> LiveScreen(s, onTapPace = { ExerciseService.speakStatus(ctx) }, onEnd = { ExerciseService.stop(ctx) })
                    w != null -> SummaryScreen(w) { LiveSession.lastWorkout.value = null }
                    else -> HomeScreen(today) { type -> pending = type; launcher.launch(requiredPermissions()) }
                }
            }
        }
    }
}

// ───────── home: today + choose workout ─────────

@Composable
private fun HomeScreen(today: TodaySummary?, onStart: (WorkoutType) -> Unit) {
    val state = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(30_000) } }

    ScalingLazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text(SimpleDateFormat("h:mm", Locale.US).format(now), style = txt(14, weight = FontWeight.SemiBold)) }
        item { StepsCard(today) }
        item { HeartCard(today) }
        item { Text("ابدأ تمرين", style = txt(12, Muted), modifier = Modifier.padding(top = 4.dp)) }
        items(WorkoutType.entries.size) { i ->
            val t = WorkoutType.entries[i]
            val tone = when (t) {
                WorkoutType.WALK -> Tone.WALK
                WorkoutType.RUN -> Tone.RUN
                WorkoutType.FOOTBALL -> Tone.FOOTBALL
                WorkoutType.INTERVALS -> Tone.INTERVALS
                WorkoutType.HIKE -> Tone.HIKE
                WorkoutType.TREADMILL -> Tone.SLEEP
                WorkoutType.CYCLING -> Tone.CYCLING
            }
            ToneBox(tone, Modifier.fillMaxWidth(0.84f).height(if (i == 0) 64.dp else 52.dp).clickable { onStart(t) }, 22.dp, 11.dp) {
                Column(Modifier.align(Alignment.CenterStart)) {
                    Text(t.ar, style = txt(13, weight = FontWeight.SemiBold))
                    if (t == WorkoutType.INTERVALS) WatchToday.coach.value.plan?.let { Text(it.name, style = txt(9, Color.White.copy(alpha = 0.9f)), maxLines = 1) }
                    else if (i == 0) Text("يكتشف الجري تلقائياً", style = txt(9, Color.White.copy(alpha = 0.92f)))
                }
                Box(Modifier.align(Alignment.CenterEnd).size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { PlayTriangle() }
            }
        }
    }
}

@Composable
private fun PlayTriangle() {
    Canvas(Modifier.size(10.dp)) {
        val p = Path().apply { moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.2f, size.height); close() }
        drawPath(p, Color(0xFF0B0B0C))
    }
}

@Composable
private fun StepsCard(t: TodaySummary?) {
    val steps = t?.steps ?: 0
    val goal = t?.stepGoal ?: 10_000
    val filled = ((steps.toFloat() / goal) * 10).toInt().coerceIn(0, 10)
    ToneBox(Tone.STEPS, Modifier.fillMaxWidth(0.82f).height(76.dp), 24.dp, 11.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth()) {
                Text("خطوات اليوم", style = txt(10, weight = FontWeight.Medium), modifier = Modifier.weight(1f))
                Text("${(steps * 100 / goal)}٪", style = txt(9, Color.White.copy(alpha = 0.88f)))
            }
            Text(if (t == null) "--" else "%,d".format(Locale.US, steps), style = doto(30.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(10) { i -> Box(Modifier.size(3.dp).clip(CircleShape).background(if (i < filled) Color.White else Color.White.copy(alpha = 0.4f))) }
            }
        }
    }
}

@Composable
private fun HeartCard(t: TodaySummary?) {
    ToneBox(Tone.HEART, Modifier.fillMaxWidth(0.72f).height(56.dp), 22.dp, 11.dp) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("نبض القلب", style = txt(9))
                Text("${t?.hr ?: "--"}", style = txt(18, weight = FontWeight.Medium))
            }
            Canvas(Modifier.size(48.dp, 16.dp)) {
                val w = size.width; val h = size.height
                val path = Path().apply {
                    moveTo(0f, h * 0.6f); lineTo(w * 0.3f, h * 0.6f); lineTo(w * 0.36f, h * 0.15f); lineTo(w * 0.43f, h * 0.95f)
                    lineTo(w * 0.5f, h * 0.4f); lineTo(w * 0.55f, h * 0.6f); lineTo(w, h * 0.6f)
                }
                drawPath(path, HeartRed, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

// ───────── live ─────────

@Composable
private fun LiveScreen(s: LiveStats, onTapPace: () -> Unit, onEnd: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { delay(3000); confirm = false } }
    val football = s.type == WorkoutType.FOOTBALL
    val color = when { s.paused -> Color(0xFF6B6F76); football -> Color(0xFF3DD68C); s.mode == Mode.RUN -> RunOrange; else -> WalkBlue }
    val glow = when { football -> Color(0xFF2E7A50); s.mode == Mode.RUN -> RunOrange; else -> Color(0xFF2A6FD6) }
    val label = when {
        s.paused -> "متوقف"
        s.interval != null -> "${s.interval!!.step.kind.ar} ${s.interval!!.index + 1}/${s.interval!!.total}"
        !s.type.walkRun || s.type == WorkoutType.HIKE -> s.type.ar
        s.mode == Mode.RUN -> "جري"
        else -> "مشي"
    }
    // progress ring: fraction of the current kilometer
    val kmFrac = ((s.distanceM % 1000) / 1000).toFloat()

    Box(Modifier.fillMaxSize().blobs(Blob(glow.copy(alpha = 0.9f), 0.5f, 0.3f, 0.75f), Blob(Color(0xFF070708), 0.5f, 0.3f, 0.28f))) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 3.dp.toPx()
            drawCircle(Color(0xFF1C1D21), radius = size.minDimension / 2 - sw, style = Stroke(sw))
            drawArc(color, -90f, 360f * kmFrac, false,
                Offset(sw, sw), androidx.compose.ui.geometry.Size(size.width - sw * 2, size.height - sw * 2), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(label, style = txt(9, Color(0xFF0B0B0C), FontWeight.Bold))
                }
                Text(Fmt.duration(s.elapsedMs), style = txt(11, weight = FontWeight.SemiBold))
            }
            val iv = s.interval
            val left = if (s.goalSec > 0) (s.goalSec - s.elapsedMs / 1000).coerceAtLeast(0) else null
            val big = when {
                iv != null -> iv.remainingSec?.let { Fmt.duration(it * 1000L) } ?: "${iv.remainingM}م"
                left != null -> Fmt.duration(left * 1000)
                s.type == WorkoutType.CYCLING -> String.format(java.util.Locale.US, "%.1f", s.speedKmh)
                else -> Fmt.pace(s.paceSec)
            }
            val sub = when {
                iv != null -> iv.step.label + (iv.next?.let { " ← ${it.kind.ar}" } ?: "")
                left != null -> "متبقي • ${Fmt.pace(s.paceSec)}"
                s.type == WorkoutType.CYCLING -> "كم/س"
                else -> "الوتيرة /كم"
            }
            Text(big, style = doto(44.sp), modifier = Modifier.clickable(onClick = onTapPace))
            Text(sub, style = txt(9, Color.White.copy(alpha = 0.9f)), maxLines = 1)
            if (s.steps > 0 || s.kcal > 0) Text("${s.steps} خطوة • ${s.kcal} سعرة" + if (s.ascentM > 3) " • ↑${s.ascentM.roundToInt()}م" else "", style = txt(9, Muted))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ToneBox(Tone.HEART, Modifier.width(70.dp).height(38.dp), 14.dp, 7.dp) {
                    Column {
                        Text("النبض", style = txt(9, Color.White.copy(alpha = 0.85f)))
                        Text("${s.hr ?: "--"} ♥", style = txt(13, weight = FontWeight.SemiBold))
                    }
                }
                ToneBox(Tone.SLEEP, Modifier.width(70.dp).height(38.dp), 14.dp, 7.dp) {
                    Column {
                        Text(if (football) "سبرنت" else "المسافة", style = txt(9, Color.White.copy(alpha = 0.85f)))
                        Text(if (football) "${s.sprints}" else "${Fmt.dist(s.distanceM)} كم", style = txt(13, weight = FontWeight.SemiBold))
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color.White)
                        .clickable { if (s.paused) ExerciseService.resume(ctx) else ExerciseService.pause(ctx) },
                    contentAlignment = Alignment.Center,
                ) { Text(if (s.paused) "▶" else "II", style = txt(11, Color(0xFF0B0B0C), FontWeight.Bold)) }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (confirm) Color(0xFFE53935) else Color(0xFF1C1D21))
                        .clickable { if (confirm) onEnd() else confirm = true }.padding(horizontal = 14.dp, vertical = 6.dp)
                ) { Text(if (confirm) "تأكيد" else "إنهاء", style = txt(10)) }
            }
        }
    }
}

// ───────── summary ─────────

@Composable
private fun SummaryScreen(w: Workout, onDone: () -> Unit) {
    val football = w.type == WorkoutType.FOOTBALL
    Box(Modifier.fillMaxSize().blobs(Blob(Color(0x8C783CDC), 0.5f, 0.0f, 0.55f)).clickable(onClick = onDone)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Text("✓", style = txt(9, Color(0xFF0B0B0C), FontWeight.Bold))
                }
                Text("انتهى التمرين", style = txt(10, weight = FontWeight.SemiBold))
            }
            ToneBox(Tone.STEPS, Modifier.width(165.dp).height(68.dp), 22.dp, 10.dp) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.dist(w.distanceM), style = doto(32.sp), modifier = Modifier.weight(1f))
                        Text("كم • ${w.title}", style = txt(9, Color.White.copy(alpha = 0.9f)))
                    }
                    if (!football) SegBar(w)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Mini(Tone.SLEEP, Fmt.duration(w.durationMs), "الوقت")
                if (football) Mini(Tone.RUN, "${w.sprints}", "سبرنت")
                else Mini(Tone.RUN, Fmt.dist(w.runM), "جري كم")
                Mini(Tone.HEART, "${w.avgHr ?: "--"}", "النبض")
            }
            Text(if (football) "أعلى سرعة ${(w.maxSpeedMps * 3.6).roundToInt()} كم/س" else "ينرسل للجوال تلقائياً", style = txt(9, Muted))
        }
    }
}

@Composable
private fun SegBar(w: Workout) {
    val total = w.segments.sumOf { it.distanceM }.toFloat()
    if (total <= 0f) return
    Row(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        w.segments.forEach { s ->
            val f = (s.distanceM / total).toFloat()
            if (f > 0.005f) Box(Modifier.weight(f).fillMaxSize().background(if (s.mode == Mode.RUN) RunOrange else WalkBlue))
        }
    }
}

@Composable
private fun Mini(tone: Tone, value: String, label: String) {
    ToneBox(tone, Modifier.width(52.dp).height(37.dp), 13.dp, 6.dp) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text(value, style = txt(10, weight = FontWeight.SemiBold))
            Text(label, style = txt(9, Color.White.copy(alpha = 0.85f)))
        }
    }
}

