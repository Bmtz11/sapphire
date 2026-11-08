package com.bachir.khutwa.mobile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

fun String.ar(): String = map { if (it in '0'..'9') '٠' + (it - '0') else it }.joinToString("")
fun Number.grouped(): String = "%,d".format(Locale.US, this.toLong())

fun txt(size: Int, color: Color, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Readex, fontSize = size.sp, color = color, fontWeight = weight)

fun weekStartMs(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) add(Calendar.DAY_OF_MONTH, -1)
}.timeInMillis

@Composable
fun HomeScreen(
    today0: TodaySummary,
    phoneSteps: Long,
    waterMl: Int,
    weather: com.bachir.khutwa.shared.WeatherInfo?,
    tips: List<Tips.Tip>,
    onWater: (Int) -> Unit,
    workouts: List<Workout>,
    streak: Int,
    showConnect: Boolean,
    onConnect: () -> Unit,
    onStart: () -> Unit,
    onOpen: (String) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val set = Settings.current
    val today = today0.copy(steps = maxOf(today0.steps, phoneSteps))
    val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todays = workouts.filter { it.startMs >= dayStart }
    val weekKm = workouts.filter { it.startMs >= weekStartMs() }.sumOf { it.distanceM } / 1000

    Box(
        Modifier.fillMaxSize().background(p.bg).then(
            if (p.dark) Modifier.liveBlobs(listOf(Blob(Color(0x8C783CDC), 0.25f, 0.0f, 0.75f)), drift = 0.12f, periodMs = 14000)
            else Modifier.liveBlobs(listOf(Blob(Color(0xD9F4D84A), 0.0f, 0.28f, 0.6f), Blob(Color(0xB3F7E37A), 1.05f, 0.75f, 0.45f)), drift = 0.1f, periodMs = 14000)
        )
    ) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(set.name, streak, weather, Modifier.enter(0)) }
            item { StepsHero(today, Modifier.enter(1)) }
            item {
                Row(Modifier.enter(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeartCard(today)
                    SleepCard(today)
                }
            }
            item {
                Row(Modifier.enter(3), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MinutesCard(today)
                    WorkoutsCard(today)
                }
            }
            item {
                Row(Modifier.enter(4), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WaterCard(waterMl, set.waterGoal, Modifier.weight(1.6f), onWater)
                    KcalCard(today.kcal, Modifier.weight(1f))
                }
            }
            if (tips.isNotEmpty()) item { TipsRow(tips, Modifier.enter(5)) }
            item { WeekGoalCard(weekKm, set.weeklyGoalKm, Modifier.enter(6)) }
            if (showConnect) item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, p.line, RoundedCornerShape(24.dp))
                        .bounceClick(onClick = onConnect).padding(16.dp)
                ) {
                    Text("اربط مع Samsung Health عشان تظهر الخطوات والنبض والنوم", style = txt(14, p.text, FontWeight.Medium))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("تمارين اليوم", style = txt(18, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text("${todays.size}".ar() + " تمرين", style = txt(13, p.muted))
                }
            }
            if (todays.isEmpty()) item {
                PlainCard(Modifier.fillMaxWidth()) { Text("ما فيه تمارين اليوم للحين. يلا!", style = txt(14, p.muted)) }
            }
            itemsIndexed(todays, key = { _, w -> w.id }) { i, w -> WorkoutRow(w, Modifier.enter(5 + i)) { onOpen(w.id) } }
            val older = workouts.filter { it.startMs < dayStart }.take(15)
            if (older.isNotEmpty()) {
                item { Text("قبل", style = txt(16, p.text, FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp)) }
                itemsIndexed(older, key = { _, w -> w.id }) { _, w -> WorkoutRow(w) { onOpen(w.id) } }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtaPill("ابدأ تمرين", Modifier.weight(1f), onStart)
            RoundIcon(Icons.Outlined.BarChart, "الإحصائيات", onStats)
            RoundIcon(Icons.Outlined.Settings, "الإعدادات", onSettings)
        }
    }
}

@Composable
fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 62.dp) {
    val p = LocalPalette.current
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (p.dark) Color(0xFF141518) else Color.White.copy(alpha = 0.85f))
            .border(1.dp, p.line, CircleShape)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = p.text, modifier = Modifier.size(22.dp)) }
}

@Composable
fun CtaPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, play: Boolean = false) {
    val p = LocalPalette.current
    Row(
        modifier.height(62.dp).clip(RoundedCornerShape(31.dp)).background(p.invBg).bounceClick(onClick = onClick)
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = txt(17, p.invText, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        Box(Modifier.size(46.dp).clip(CircleShape).background(p.invText), contentAlignment = Alignment.Center) {
            Icon(if (play) Icons.Filled.PlayArrow else Icons.Filled.NorthEast, contentDescription = null, tint = p.invBg, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Header(name: String, streak: Int, weather: com.bachir.khutwa.shared.WeatherInfo?, modifier: Modifier) {
    val p = LocalPalette.current
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = if (hour < 12) "صباح الخير" else "مساء الخير"
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(Date()), style = txt(13, p.muted))
            Text("$greet، $name", style = txt(22, p.text, FontWeight.SemiBold))
            weather?.let { Box(Modifier.padding(top = 6.dp)) { WeatherChip(it, onDark = p.dark) } }
        }
        if (streak > 0) Row(
            Modifier.clip(RoundedCornerShape(18.dp)).background(if (p.dark) Color(0xFF1C1D21) else Color.White).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = RunOrange, modifier = Modifier.size(18.dp))
            Text("$streak", style = numStyle(18.sp, p.text, FontWeight.Bold))
            Text(if (streak == 1) "يوم" else "أيام", style = txt(12, p.muted))
        }
    }
}

@Composable
private fun StepsHero(t: TodaySummary, modifier: Modifier) {
    val p = LocalPalette.current
    val frac = (t.steps.toFloat() / t.stepGoal).coerceIn(0f, 1f)
    val pct = (frac * 100).toInt()
    val sub = "$pct٪ من ${t.stepGoal.grouped()} • ${"%.1f".format(Locale.US, t.distanceM / 1000)} كم".ar()

    if (p.dark) {
        ToneCard(CardTone.STEPS, modifier.fillMaxWidth().height(200.dp), radius = 38.dp, pad = 20.dp) {
            Text("خطوات اليوم", style = txt(15, Color.White), modifier = Modifier.align(Alignment.TopStart))
            Column(Modifier.align(Alignment.BottomStart)) {
                CountText(t.steps, numStyle(72.sp, Color.White))
                Text(sub, style = txt(13, Color.White.copy(alpha = 0.88f)))
            }
            Ring(frac, Color(0xFFF5E15A), Color.White.copy(alpha = 0.3f), 92.dp, Modifier.align(Alignment.TopEnd))
        }
    } else {
        Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("خطوات اليوم", style = txt(14, p.muted))
                CountText(t.steps, numStyle(80.sp, p.text))
                Text(sub, style = txt(13, p.muted))
            }
            Ring(frac, p.text, p.line, 100.dp, Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
fun Ring(frac: Float, color: Color, track: Color, size: Dp, modifier: Modifier = Modifier, stroke: Dp = 5.dp) {
    val f by animateFloatAsState(frac, tween(1200, easing = FastOutSlowInEasing), label = "ring")
    Canvas(modifier.size(size)) {
        val sw = stroke.toPx()
        val arcSize = Size(this.size.width - sw * 2, this.size.height - sw * 2)
        drawCircle(track, radius = arcSize.width / 2, style = Stroke(1.5.dp.toPx()))
        drawArc(color, -90f, 360f * f, false, Offset(sw, sw), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
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
                else Text(title, style = txt(15, fg), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                corner?.let { Text(it, style = txt(11, sub), maxLines = 1) }
            }
            if (!p.dark) Text(title, style = txt(15, fg, FontWeight.Medium), maxLines = 1)
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
            if (t.hr != null) CountText(t.hr!!.toLong(), numStyle(38.sp, fg), { "$it" }) else Text("--", style = numStyle(38.sp, fg))
            Text(" ن/د", style = txt(12, sub), modifier = Modifier.padding(bottom = 6.dp))
        }
        BeatingHeart(t.hr, if (LocalPalette.current.dark) HeartRed else fg, 22.dp)
    }
}

@Composable
private fun RowScope.SleepCard(t: TodaySummary) = StatCard(
    "النوم", Icons.Outlined.Bedtime, CardTone.SLEEP, corner = "آخر ليلة",
) { fg, sub ->
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        val m = t.sleepMin
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
            if (m == null) Text("--", style = numStyle(38.sp, fg))
            else {
                Text("${m / 60}", style = numStyle(38.sp, fg))
                Text("س ", style = txt(12, sub), modifier = Modifier.padding(bottom = 6.dp))
                Text("${m % 60}", style = numStyle(38.sp, fg))
                Text("د", style = txt(12, sub), modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun RowScope.MinutesCard(t: TodaySummary) = StatCard(
    "دقائق التمرين", Icons.Outlined.Timer, CardTone.MINUTES, CardTone.MINUTES_LIGHT,
    corner = "/ ${t.exerciseGoalMin}".ar(),
) { fg, _ ->
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CountText(t.exerciseMin.toLong(), numStyle(38.sp, fg), { "$it" }, Modifier.weight(1f))
        val filled = ((t.exerciseMin.toFloat() / t.exerciseGoalMin) * 7).toInt().coerceIn(0, 7)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(7) { i -> Box(Modifier.size(6.dp).clip(CircleShape).background(if (i < filled) fg else fg.copy(alpha = 0.35f))) }
        }
    }
}

@Composable
private fun RowScope.WorkoutsCard(t: TodaySummary) = StatCard(
    "التمارين", Icons.Outlined.CalendarMonth, CardTone.WORKOUTS,
) { fg, sub ->
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
        Column {
            CountText(t.workoutsWeek.toLong(), numStyle(38.sp, fg), { "$it" })
            Text("الأسبوع", style = txt(11, sub))
        }
        Column {
            CountText(t.workoutsMonth.toLong(), numStyle(38.sp, sub), { "$it" })
            Text("الشهر", style = txt(11, sub))
        }
    }
}

@Composable
fun WeekGoalCard(km: Double, goalKm: Int, modifier: Modifier = Modifier) {
    val frac = (km / goalKm.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    val f by animateFloatAsState(frac, tween(1200, easing = FastOutSlowInEasing), label = "goal")
    ToneCard(CardTone.GOAL, modifier.fillMaxWidth(), radius = 30.dp, pad = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("هدف الأسبوع", style = txt(14, Color.White))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.1f".format(Locale.US, km), style = numStyle(34.sp, Color.White))
                        Text(" / $goalKm كم", style = txt(13, Color.White.copy(alpha = 0.85f)), modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
                Text(if (frac >= 1f) "حققته!" else "باقي ${"%.1f".format(Locale.US, (goalKm - km).coerceAtLeast(0.0))} كم".ar(),
                    style = txt(13, if (frac >= 1f) FootballGreen else Color.White.copy(alpha = 0.9f), FontWeight.Medium))
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxWidth(f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(FootballGreen))
            }
        }
    }
}

fun typeIcon(type: WorkoutType, runM: Double = 0.0): ImageVector = when (type) {
    WorkoutType.FOOTBALL -> Icons.Filled.SportsSoccer
    WorkoutType.HIKE -> Icons.Filled.Terrain
    WorkoutType.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    WorkoutType.TREADMILL -> Icons.Filled.FitnessCenter
    WorkoutType.INTERVALS -> Icons.Filled.Bolt
    WorkoutType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    WorkoutType.WALK -> if (runM > 50) Icons.AutoMirrored.Filled.DirectionsRun else Icons.AutoMirrored.Filled.DirectionsWalk
}

@Composable
fun WorkoutRow(w: Workout, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val time = SimpleDateFormat("EEE h:mm a", Locale("ar")).format(Date(w.startMs))
    PlainCard(modifier.fillMaxWidth().bounceClick(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIcon(typeIcon(w.type, w.runM), p.invText, p.line, 44.dp, filled = p.invBg)
                Column(Modifier.weight(1f)) {
                    Text(w.title, style = txt(15, p.text, FontWeight.SemiBold))
                    Text("$time • ${if (w.source == "watch") "الساعة" else "الجوال"}", style = txt(12, p.muted), maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (w.type == WorkoutType.FOOTBALL) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${w.activeMs / 60_000}", style = numStyle(26.sp, p.text))
                            Text(" د", style = txt(12, p.muted))
                        }
                        Text("${w.sprints} سبرنت".ar(), style = txt(12, p.muted))
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Fmt.dist(w.distanceM), style = numStyle(26.sp, p.text))
                            Text(" كم", style = txt(12, p.muted))
                        }
                        Text("${Fmt.duration(w.activeMs)} • ${Fmt.pace(w.avgPaceSec)}", style = numStyle(13.sp, p.muted, FontWeight.SemiBold))
                    }
                }
            }
            if (w.type != WorkoutType.FOOTBALL && w.segments.size > 1) SegmentBar(w.segments, 8.dp)
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
fun SegmentBar(segments: List<Segment>, height: Dp) {
    val total = segments.sumOf { it.distanceM }.toFloat()
    Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total <= 0f) {
            Box(Modifier.fillMaxSize().background(Color(0x332A2D33)))
        } else {
            segments.forEach { s ->
                val f = (s.distanceM / total).toFloat()
                if (f > 0.005f) Box(Modifier.weight(f).fillMaxSize().background(if (s.mode == Mode.RUN) RunOrange else WalkBlue))
            }
        }
    }
}



/** Water: tap to add a glass; the fill rises with a little wave. */
@Composable
fun WaterCard(ml: Int, goal: Int, modifier: Modifier, onAdd: (Int) -> Unit) {
    val frac = (ml / goal.toFloat()).coerceIn(0f, 1f)
    val f by animateFloatAsState(frac, tween(900, easing = FastOutSlowInEasing), label = "w")
    val wave by androidx.compose.animation.core.rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * Math.PI).toFloat(), androidx.compose.animation.core.infiniteRepeatable(tween(2600, easing = androidx.compose.animation.core.LinearEasing)), label = "ph",
    )
    Box(modifier.height(150.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFF0B2440))) {
        Canvas(Modifier.matchParentSize()) {
            val top = size.height * (1f - f)
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, top)
                var x = 0f
                while (x <= size.width) {
                    lineTo(x, top + 6.dp.toPx() * kotlin.math.sin(wave + x / size.width * 6.28f))
                    x += 8f
                }
                lineTo(size.width, size.height); close()
            }
            drawPath(path, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF1E6FD9)), startY = top, endY = size.height))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الموية", style = txt(15, Color.White), modifier = Modifier.weight(1f))
                Text("/ ${goal.grouped()} مل", style = txt(11, Color.White.copy(alpha = 0.85f)))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                CountText(ml.toLong(), numStyle(34.sp, Color.White))
                Text(" مل", style = txt(12, Color.White.copy(alpha = 0.85f)), modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WaterBtn("+٢٥٠", Modifier.weight(1f)) { onAdd(250) }
                WaterBtn("+٥٠٠", Modifier.weight(1f)) { onAdd(500) }
                WaterBtn("−", Modifier.width(40.dp)) { onAdd(-250) }
            }
        }
    }
}

@Composable
private fun WaterBtn(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = txt(13, Color.White, FontWeight.SemiBold))
    }
}

@Composable
private fun KcalCard(kcal: Int, modifier: Modifier) {
    ToneCard(CardTone.WORKOUTS, modifier.height(150.dp), radius = 30.dp, pad = 16.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Column {
                CountText(kcal.toLong(), numStyle(34.sp, Color.White), { "$it" })
                Text("سعرة من التمارين", style = txt(11, Color.White.copy(alpha = 0.9f)), maxLines = 2)
            }
        }
    }
}

@Composable
private fun TipsRow(tips: List<Tips.Tip>, modifier: Modifier) {
    androidx.compose.foundation.lazy.LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(tips.size) { i ->
            val t = tips[i]
            val tone = when (t.kind) {
                Tips.Kind.WATER -> CardTone.WATER
                Tips.Kind.SLEEP -> CardTone.SLEEP
                Tips.Kind.HEAT -> CardTone.HIKE
                Tips.Kind.RECOVERY -> CardTone.HEART
                Tips.Kind.STREAK -> CardTone.WORKOUTS
                Tips.Kind.PACE -> CardTone.INTERVALS
                Tips.Kind.STEPS -> CardTone.GOAL
            }
            ToneCard(tone, Modifier.width(250.dp).height(118.dp), radius = 26.dp, pad = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(t.title, style = txt(15, Color.White, FontWeight.SemiBold), maxLines = 1)
                    Text(t.body, style = txt(12, Color.White.copy(alpha = 0.92f)).copy(lineHeight = 18.sp), maxLines = 3)
                }
            }
        }
    }
}
