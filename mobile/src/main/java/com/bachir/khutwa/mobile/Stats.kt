package com.bachir.khutwa.mobile

import com.bachir.khutwa.shared.tr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bachir.khutwa.shared.Fmt
import com.bachir.khutwa.shared.Workout
import com.bachir.khutwa.shared.WorkoutType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsScreen(workouts: List<Workout>, week: List<DaySteps>, activeDays: Set<LocalDate>, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    val set = Settings.current
    val zone = ZoneId.systemDefault()
    val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val weekKm = workouts.filter { it.startMs >= weekStartMs() }.sumOf { it.distanceM } / 1000
    val monthKm = workouts.filter { it.startMs >= monthStart }.sumOf { it.distanceM } / 1000
    val streak = TodayReader.streak(activeDays)

    LazyColumn(
        Modifier.fillMaxSize().background(p.bg).statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundButton(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع"), onBack)
                Text(tr("الإحصائيات"), style = txt(22, p.text, FontWeight.SemiBold))
            }
        }
        item { StepsWeek(week, set.stepGoal, Modifier.enter(0)) }
        item {
            Row(Modifier.enter(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(CardTone.WORKOUTS, tr("هذا الأسبوع"), "%.1f".format(Locale.US, weekKm), tr("كم"), Modifier.weight(1f))
                Tile(CardTone.SLEEP, tr("هذا الشهر"), "%.1f".format(Locale.US, monthKm), tr("كم"), Modifier.weight(1f))
                Tile(CardTone.MINUTES, tr("الاستمرارية"), "${streak}", if (streak == 1) tr("يوم") else tr("أيام"), Modifier.weight(1f))
            }
        }
        item { WeekGoalCard(weekKm, set.weeklyGoalKm, Modifier.enter(2)) }
        item { MonthDots(activeDays, Modifier.enter(3)) }
        item { Records(workouts, onOpen, Modifier.enter(4)) }
    }
}

@Composable
private fun Tile(tone: CardTone, label: String, value: String, unit: String, modifier: Modifier) {
    ToneCard(tone, modifier.height(112.dp), radius = 26.dp, pad = 14.dp) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = txt(12, Color.White))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = numStyle(32.sp, Color.White))
                Text(" ${unit}", style = txt(11, Color.White.copy(alpha = 0.85f)), modifier = Modifier.padding(bottom = 5.dp))
            }
        }
    }
}

@Composable
private fun StepsWeek(week: List<DaySteps>, goal: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val max = (week.maxOfOrNull { it.steps } ?: 0L).coerceAtLeast(goal.toLong()).toFloat()
    val avg = if (week.isNotEmpty()) week.sumOf { it.steps } / week.size else 0
    PlainCard(modifier.fillMaxWidth(), radius = 30.dp, pad = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(tr("الخطوات — آخر ٧ أيام"), style = txt(15, p.text, FontWeight.SemiBold))
                    Text(tr("المتوسط اليومي"), style = txt(12, p.muted))
                }
                CountText(avg, numStyle(30.sp, p.text))
            }
            if (week.isEmpty()) Text(tr("اربط Samsung Health عشان تظهر الخطوات."), style = txt(13, p.muted))
            else Box(Modifier.fillMaxWidth().height(150.dp)) {
                // goal line
                val goalFrac = goal / max
                Box(Modifier.fillMaxWidth().fillMaxHeight(1f - goalFrac * 0.78f)) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(p.muted.copy(alpha = 0.5f)))
                }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    week.forEachIndexed { i, d ->
                        val grow = remember { Animatable(0f) }
                        LaunchedEffect(d.steps) { delay(i * 70L); grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
                        val hit = d.steps >= goal
                        val today = d.date == LocalDate.now()
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight((d.steps / max * 0.78f * grow.value).coerceIn(0.02f, 0.78f))
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (hit) FootballGreen else if (today) p.text else p.muted.copy(alpha = 0.45f))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale()).take(3),
                                style = txt(10, if (today) p.text else p.muted, if (today) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthDots(days: Set<LocalDate>, modifier: Modifier) {
    val p = LocalPalette.current
    val first = LocalDate.now().withDayOfMonth(1)
    val len = first.lengthOfMonth()
    // Sunday-first grid
    val lead = first.dayOfWeek.value % 7
    PlainCard(modifier.fillMaxWidth(), radius = 30.dp, pad = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Text(first.month.getDisplayName(TextStyle.FULL, appLocale()), style = txt(15, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text(tr("{0} يوم تمرين", days.count { it.month == first.month && it.year == first.year }).ar(), style = txt(12, p.muted))
            }
            val cells = List(lead) { null } + (1..len).map { first.withDayOfMonth(it) }
            cells.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).forEach { i ->
                        val d = row.getOrNull(i)
                        val on = d != null && d in days
                        val today = d == LocalDate.now()
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        d == null -> Color.Transparent
                                        on -> RunOrange
                                        else -> p.muted.copy(alpha = 0.12f)
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (d != null) Text("${d.dayOfMonth}", style = numStyle(13.sp, if (on) Color.White else if (today) p.text else p.muted, FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Records(workouts: List<Workout>, onOpen: (String) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val zone = ZoneId.systemDefault()
    fun dateOf(w: Workout) = Instant.ofEpochMilli(w.startMs).atZone(zone).toLocalDate().let { "${it.dayOfMonth}/${it.monthValue}" }

    val fastestKm = workouts.flatMap { w -> w.splits.map { it.durationSec to w } }.minByOrNull { it.first }
    val longest = workouts.filter { it.type != WorkoutType.FOOTBALL }.maxByOrNull { it.distanceM }
    val longestRun = workouts.maxByOrNull { it.runM }?.takeIf { it.runM > 50 }
    val topSpeed = workouts.maxByOrNull { it.maxSpeedMps }?.takeIf { it.maxSpeedMps > 0 }
    val mostSprints = workouts.filter { it.type == WorkoutType.FOOTBALL }.maxByOrNull { it.sprints }?.takeIf { it.sprints > 0 }

    val rows = buildList {
        fastestKm?.let { add(Triple(tr("أسرع كيلو"), tr("{0} /كم", Fmt.pace(it.first)), it.second)) }
        longest?.let { add(Triple(tr("أطول مسافة"), tr("{0} كم", Fmt.dist(it.distanceM)), it)) }
        longestRun?.let { add(Triple(tr("أطول جري"), tr("{0} كم", Fmt.dist(it.runM)), it)) }
        topSpeed?.let { add(Triple(tr("أعلى سرعة"), tr("{0} كم/س", (it.maxSpeedMps * 3.6).roundToInt()), it)) }
        mostSprints?.let { add(Triple(tr("أكثر سبرنتات"), "${it.sprints}", it)) }
    }
    PlainCard(modifier.fillMaxWidth(), radius = 30.dp, pad = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("أرقامك القياسية"), style = txt(15, p.text, FontWeight.SemiBold))
            if (rows.isEmpty()) Text(tr("سجّل أول تمرين وتطلع أرقامك هنا."), style = txt(13, p.muted))
            rows.forEach { (label, value, w) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).bounceClick { onOpen(w.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = txt(14, p.text, FontWeight.Medium))
                        Text(dateOf(w), style = numStyle(12.sp, p.muted, FontWeight.SemiBold))
                    }
                    Text(value, style = numStyle(22.sp, p.text))
                }
            }
        }
    }
}
