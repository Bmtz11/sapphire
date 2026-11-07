package com.bachir.khutwa.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bachir.khutwa.shared.Fmt
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.Segment
import com.bachir.khutwa.shared.TodaySummary
import com.bachir.khutwa.shared.Workout
import com.bachir.khutwa.shared.WorkoutType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val USER_NAME = "بشير"

fun String.ar(): String = map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
fun Number.grouped(): String = "%,d".format(Locale.US, this.toLong())

fun txt(size: Int, color: Color, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Readex, fontSize = size.sp, color = color, fontWeight = weight)

@Composable
fun HomeScreen(
    today: TodaySummary,
    workouts: List<Workout>,
    showConnect: Boolean,
    onConnect: () -> Unit,
    onStart: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val p = LocalPalette.current
    val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todays = workouts.filter { it.startMs >= dayStart }

    Box(
        Modifier.fillMaxSize().background(p.bg).then(
            if (p.dark) Modifier.blobs(Blob(Color(0x8C783CDC), 0.25f, 0.0f, 0.75f))
            else Modifier.blobs(Blob(Color(0xD9F4D84A), 0.0f, 0.28f, 0.6f), Blob(Color(0xB3F7E37A), 1.05f, 0.75f, 0.45f))
        )
    ) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }
            item { StepsHero(today) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeartCard(today)
                    SleepCard(today)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MinutesCard(today)
                    WorkoutsCard(today)
                }
            }
            if (showConnect) item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, p.line, RoundedCornerShape(24.dp))
                        .clickable(onClick = onConnect).padding(16.dp)
                ) {
                    Text("اربط مع Samsung Health عشان تظهر الخطوات والنبض والنوم", style = txt(14, p.text, FontWeight.Medium))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("تمارين اليوم", style = txt(18, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text("${workouts.size} تمرين".ar(), style = txt(13, p.muted))
                }
            }
            if (todays.isEmpty()) item {
                PlainCard(Modifier.fillMaxWidth()) {
                    Text("ما فيه تمارين اليوم للحين.", style = txt(14, p.muted))
                }
            }
            items(todays, key = { it.id }) { w -> WorkoutRow(w) { onOpen(w.id) } }
            val older = workouts.filter { it.startMs < dayStart }.take(10)
            if (older.isNotEmpty()) {
                item { Text("قبل", style = txt(16, p.text, FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp)) }
                items(older, key = { it.id }) { w -> WorkoutRow(w) { onOpen(w.id) } }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtaPill("ابدأ تمرين", Modifier.weight(1f), onStart)
        }
    }
}

@Composable
fun CtaPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, play: Boolean = false) {
    val p = LocalPalette.current
    Row(
        modifier.height(62.dp).clip(RoundedCornerShape(31.dp)).background(p.invBg).clickable(onClick = onClick)
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = txt(17, p.invText, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        Box(Modifier.size(46.dp).clip(CircleShape).background(p.invText), contentAlignment = Alignment.Center) {
            Icon(
                if (play) Icons.Filled.PlayArrow else Icons.Filled.NorthEast,
                contentDescription = null, tint = p.invBg, modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Header() {
    val p = LocalPalette.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = if (hour < 12) "صباح الخير" else "مساء الخير"
    Column {
        Text(SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(Date()), style = txt(13, p.muted))
        Text("$greet، $USER_NAME", style = txt(22, p.text, FontWeight.SemiBold))
    }
}

@Composable
private fun StepsHero(t: TodaySummary) {
    val p = LocalPalette.current
    val frac = (t.steps.toFloat() / t.stepGoal).coerceIn(0f, 1f)
    val pct = (frac * 100).toInt()
    val sub = "$pct٪ من هدف ${t.stepGoal.grouped()} • ${"%.1f".format(Locale.US, t.distanceM / 1000)} كم".ar()

    if (p.dark) {
        ToneCard(CardTone.STEPS, Modifier.fillMaxWidth().height(200.dp), radius = 38.dp, pad = 20.dp) {
            Column(Modifier.align(Alignment.TopStart)) { Text("خطوات اليوم", style = txt(15, Color.White)) }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(t.steps.grouped(), style = dotoStyle(76.sp, Color.White))
                Text(sub, style = txt(13, Color.White.copy(alpha = 0.88f)))
            }
            Ring(frac, Color(0xFFF5E15A), Color.White.copy(alpha = 0.35f), 96.dp, Modifier.align(Alignment.TopEnd))
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("خطوات اليوم", style = txt(14, p.muted))
                Text(t.steps.grouped(), style = dotoStyle(84.sp, p.text))
                Text(sub, style = txt(13, p.muted))
            }
            Ring(frac, p.text, p.line, 104.dp, Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
fun Ring(frac: Float, color: Color, track: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val sw = 5.dp.toPx()
        val inset = sw
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawCircle(track, radius = arcSize.width / 2, style = Stroke(1.5.dp.toPx()))
        drawArc(color, -90f, 360f * frac, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Card shell: gradient in dark, white (or the one gradient card) in light. */
@Composable
private fun RowScope.StatCard(
    title: String, icon: ImageVector, darkTone: CardTone, lightTone: CardTone? = null,
    corner: String? = null, content: @Composable (Color, Color) -> Unit,
) {
    val p = LocalPalette.current
    val tone = if (p.dark) darkTone else lightTone
    val fg = if (tone != null) Color.White else p.text
    val sub = if (tone != null) Color.White.copy(alpha = 0.85f) else p.muted
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!p.dark) CircleIcon(icon, fg, if (tone != null) Color.White.copy(alpha = 0.45f) else p.line)
                else Text(title, style = txt(15, fg))
                Spacer(Modifier.weight(1f))
                corner?.let { Text(it, style = txt(12, sub)) }
            }
            if (!p.dark) Text(title, style = txt(15, fg, FontWeight.Medium))
            content(fg, sub)
        }
    }
    val m = Modifier.weight(1f).height(168.dp)
    if (tone != null) ToneCard(tone, m) { inner() } else PlainCard(m, radius = 34.dp) { inner() }
}

@Composable
private fun RowScope.HeartCard(t: TodaySummary) = StatCard(
    "نبض القلب", Icons.Outlined.FavoriteBorder, CardTone.HEART,
    corner = t.restingHr?.let { "راحة $it".ar() },
) { fg, sub ->
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
            Text(t.hr?.toString() ?: "--", style = txt(34, fg, FontWeight.SemiBold))
            Text(" ن/د", style = txt(12, sub), modifier = Modifier.padding(bottom = 6.dp))
        }
        val lineColor = if (LocalPalette.current.dark) HeartRed else fg
        Canvas(Modifier.size(58.dp, 26.dp)) {
            val w = size.width; val h = size.height
            val path = Path().apply {
                moveTo(0f, h * 0.6f); lineTo(w * 0.2f, h * 0.6f); lineTo(w * 0.28f, h * 0.2f)
                lineTo(w * 0.36f, h * 0.95f); lineTo(w * 0.45f, h * 0.45f); lineTo(w * 0.5f, h * 0.6f); lineTo(w, h * 0.6f)
            }
            drawPath(path, lineColor, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun RowScope.SleepCard(t: TodaySummary) = StatCard(
    "النوم", Icons.Outlined.Bedtime, CardTone.SLEEP, corner = "أمس",
) { fg, sub ->
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        val m = t.sleepMin
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
            if (m == null) Text("--", style = txt(34, fg, FontWeight.SemiBold))
            else {
                Text("${m / 60}", style = txt(34, fg, FontWeight.SemiBold))
                Text("س ", style = txt(12, sub), modifier = Modifier.padding(bottom = 6.dp))
                Text("${m % 60}", style = txt(34, fg, FontWeight.SemiBold))
                Text("د", style = txt(12, sub), modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            listOf(10, 20, 14, 26, 18, 8).forEachIndexed { i, hgt ->
                Box(Modifier.width(4.dp).height(hgt.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i % 2 == 1) fg else fg.copy(alpha = 0.35f)))
            }
        }
    }
}

@Composable
private fun RowScope.MinutesCard(t: TodaySummary) = StatCard(
    "دقائق التمرين", Icons.Outlined.Timer, CardTone.MINUTES, CardTone.MINUTES_LIGHT,
    corner = "الهدف ${t.exerciseGoalMin}".ar(),
) { fg, _ ->
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${t.exerciseMin}", style = txt(34, fg, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        val filled = ((t.exerciseMin.toFloat() / t.exerciseGoalMin) * 7).toInt().coerceIn(0, 7)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(7) { i ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (i < filled) fg else fg.copy(alpha = 0.4f)))
            }
        }
    }
}

@Composable
private fun RowScope.WorkoutsCard(t: TodaySummary) = StatCard(
    "التمارين", Icons.Outlined.CalendarMonth, CardTone.WORKOUTS,
) { fg, sub ->
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
        Column {
            Text("${t.workoutsWeek}", style = txt(34, fg, FontWeight.SemiBold))
            Text("الأسبوع", style = txt(11, sub))
        }
        Column {
            Text("${t.workoutsMonth}", style = txt(34, sub, FontWeight.SemiBold))
            Text("الشهر", style = txt(11, sub))
        }
    }
}

fun typeIcon(type: WorkoutType, runM: Double = 0.0): ImageVector = when {
    type == WorkoutType.FOOTBALL -> Icons.Filled.SportsSoccer
    type == WorkoutType.RUN || runM > 50 -> Icons.AutoMirrored.Filled.DirectionsRun
    else -> Icons.AutoMirrored.Filled.DirectionsWalk
}

@Composable
fun WorkoutRow(w: Workout, onClick: () -> Unit) {
    val p = LocalPalette.current
    val time = SimpleDateFormat("h:mm a", Locale("ar")).format(Date(w.startMs))
    PlainCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIcon(typeIcon(w.type, w.runM), p.invText, p.line, 44.dp, filled = p.invBg)
                Column(Modifier.weight(1f)) {
                    Text(w.title, style = txt(15, p.text, FontWeight.SemiBold))
                    Text("$time • ${if (w.source == "watch") "الساعة" else "الجوال"}" + if (w.hcSynced) " ✓" else "", style = txt(12, p.muted))
                }
                Column {
                    if (w.type == WorkoutType.FOOTBALL) {
                        Text("${w.durationMs / 60_000} د".ar(), style = txt(22, p.text, FontWeight.SemiBold))
                        Text("${Fmt.dist(w.distanceM)} كم • ${w.sprints} سبرنت".ar(), style = txt(12, p.muted))
                    } else {
                        Text("${Fmt.dist(w.distanceM)} كم", style = txt(22, p.text, FontWeight.SemiBold))
                        Text("${w.durationMs / 60_000} د • ${Fmt.pace(w.avgPaceSec)} /كم".ar(), style = txt(12, p.muted))
                    }
                }
            }
            if (w.type != WorkoutType.FOOTBALL && w.segments.size > 1) {
                SegmentBar(w.segments, 8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(WalkBlue, "مشي ${"%.1f".format(Locale.US, w.walkM / 1000)} كم".ar())
                    Legend(RunOrange, "جري ${"%.1f".format(Locale.US, w.runM / 1000)} كم".ar())
                }
            }
        }
    }
}

@Composable
fun Legend(c: Color, label: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Text(label, style = txt(12, p.muted))
    }
}

@Composable
fun SegmentBar(segments: List<Segment>, height: Dp, tail: Float = 0f) {
    val total = segments.sumOf { it.distanceM }.toFloat()
    Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total <= 0f) {
            Box(Modifier.fillMaxSize().background(Color(0xFF2A2D33)))
        } else {
            segments.forEach { s ->
                val f = (s.distanceM / total).toFloat()
                if (f > 0.005f) Box(Modifier.weight(f).fillMaxSize().background(if (s.mode == Mode.RUN) RunOrange else WalkBlue))
            }
            if (tail > 0f) Box(Modifier.weight(tail).fillMaxSize().background(Color(0xFF2A2D33)))
        }
    }
}
