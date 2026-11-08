package com.bachir.khutwa.mobile

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bachir.khutwa.shared.tr
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.cos
import kotlin.math.sin

enum class Shield { WHITE, BLUE, ORANGE, PURPLE, MEGA, ULTIMATE, ARMORED, POWER, GOLD, PLATINUM, SAPPHIRE, POWER_MEGA, MOON, MOON_GOLD, RUN, RUN_GOLD, GOAL }

enum class BadgeGroup { DAILY, WEEKLY, MONTHLY, SLEEP, RUN_KM, RUN_MIN, GOALS }

data class BadgeDef(val id: String, val group: BadgeGroup, val title: String, val need: String, val label: String, val style: Shield)
data class Earned(val def: BadgeDef, val count: Int, val last: LocalDate?)

object Badges {
    val DEFS = listOf(
        BadgeDef("d5", BadgeGroup.DAILY, "درع أبيض", "٥ آلاف خطوة في يوم", "5K", Shield.WHITE),
        BadgeDef("d7", BadgeGroup.DAILY, "درع أزرق", "٧ آلاف خطوة في يوم", "7K", Shield.BLUE),
        BadgeDef("d10", BadgeGroup.DAILY, "درع برتقالي", "١٠ آلاف خطوة في يوم", "10K", Shield.ORANGE),
        BadgeDef("d15", BadgeGroup.DAILY, "درع بنفسجي", "١٥ ألف خطوة في يوم", "15K", Shield.PURPLE),
        BadgeDef("d20", BadgeGroup.DAILY, "درع ميجا", "٢٠ ألف خطوة في يوم", "20K", Shield.MEGA),
        BadgeDef("d30", BadgeGroup.DAILY, "Ultimate Walk", "٣٠ ألف خطوة في يوم", "30K", Shield.ULTIMATE),
        BadgeDef("w70", BadgeGroup.WEEKLY, "درع مدرّع", "٧٠ ألف خطوة في أسبوع", "70K", Shield.ARMORED),
        BadgeDef("w100", BadgeGroup.WEEKLY, "درع الباور", "١٠٠ ألف خطوة في أسبوع", "100K", Shield.POWER),
        BadgeDef("m150", BadgeGroup.MONTHLY, "درع ذهبي", "١٥٠ ألف خطوة في شهر", "150K", Shield.GOLD),
        BadgeDef("m180", BadgeGroup.MONTHLY, "درع فضي بلاتيني", "١٨٠ ألف خطوة في شهر", "180K", Shield.PLATINUM),
        BadgeDef("m200", BadgeGroup.MONTHLY, "درع سافاير", "٢٠٠ ألف خطوة في شهر", "200K", Shield.SAPPHIRE),
        BadgeDef("m300", BadgeGroup.MONTHLY, "درع الباور ميجا", "٣٠٠ ألف خطوة في شهر", "300K", Shield.POWER_MEGA),
        BadgeDef("s7", BadgeGroup.SLEEP, "نوم ممتاز", "٧ ساعات نوم أو أكثر", "7h", Shield.MOON),
        BadgeDef("s8", BadgeGroup.SLEEP, "نوم عميق", "٨ ساعات نوم أو أكثر", "8h", Shield.MOON_GOLD),
        BadgeDef("s3n", BadgeGroup.SLEEP, "٣ ليالي ممتازة", "٣ ليالي ورا بعض فوق ٧ ساعات", "×3", Shield.MOON),
        BadgeDef("s7n", BadgeGroup.SLEEP, "أسبوع نوم ممتاز", "٧ ليالي ورا بعض فوق ٧ ساعات", "×7", Shield.MOON_GOLD),
        BadgeDef("r3", BadgeGroup.RUN_KM, "جري ٣ كم", "٣ كم جري في تمرين", "3K", Shield.RUN),
        BadgeDef("r5", BadgeGroup.RUN_KM, "جري ٥ كم", "٥ كم جري في تمرين", "5K", Shield.RUN),
        BadgeDef("r10", BadgeGroup.RUN_KM, "جري ١٠ كم", "١٠ كم جري في تمرين", "10K", Shield.RUN),
        BadgeDef("r21", BadgeGroup.RUN_KM, "نص ماراثون", "٢١.١ كم جري", "21K", Shield.RUN_GOLD),
        BadgeDef("r42", BadgeGroup.RUN_KM, "ماراثون", "٤٢.٢ كم جري", "42K", Shield.RUN_GOLD),
        BadgeDef("rm15", BadgeGroup.RUN_MIN, "١٥ دقيقة جري", "١٥ دقيقة جري متواصل", "15'", Shield.RUN),
        BadgeDef("rm30", BadgeGroup.RUN_MIN, "٣٠ دقيقة جري", "٣٠ دقيقة جري", "30'", Shield.RUN),
        BadgeDef("rm60", BadgeGroup.RUN_MIN, "ساعة جري", "٦٠ دقيقة جري", "60'", Shield.RUN_GOLD),
        BadgeDef("rm90", BadgeGroup.RUN_MIN, "٩٠ دقيقة جري", "٩٠ دقيقة جري", "90'", Shield.RUN_GOLD),
        BadgeDef("g_steps", BadgeGroup.GOALS, "هدف الخطوات", "حققت هدف خطواتك اليومي", "✓", Shield.GOAL),
        BadgeDef("g_week", BadgeGroup.GOALS, "هدف الأسبوع", "حققت هدف المسافة الأسبوعي", "★", Shield.GOAL),
    )

    val earned = MutableStateFlow<List<Earned>>(emptyList())
    /** Badges earned since the last time the app looked — shown as a celebration. */
    val fresh = MutableStateFlow<List<BadgeDef>>(emptyList())

    suspend fun refresh(ctx: Context) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val days = mutableMapOf<LocalDate, Long>()       // steps per day
        val sleeps = mutableMapOf<LocalDate, Int>()      // minutes per night (keyed by wake-up day)

        if (HealthSync.available(ctx)) runCatching {
            val client = HealthConnectClient.getOrCreate(ctx)
            val granted = client.permissionController.getGrantedPermissions()
            if (androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class) in granted) {
                suspend fun byDay(origins: Set<DataOrigin>) = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL),
                        TimeRangeFilter.between(today.minusDays(120).atStartOfDay(), LocalDateTime.now()), Period.ofDays(1), origins)
                ).associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
                val s = byDay(setOf(DataOrigin(TodayReader.SAMSUNG)))
                days += if (s.values.any { it > 0 }) s else byDay(emptySet())
            }
            if (androidx.health.connect.client.permission.HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
                client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,
                    TimeRangeFilter.between(Instant.now().minus(60, ChronoUnit.DAYS), Instant.now()))).records.forEach {
                    val d = it.endTime.atZone(zone).toLocalDate()
                    sleeps[d] = (sleeps[d] ?: 0) + Duration.between(it.startTime, it.endTime).toMinutes().toInt()
                }
            }
        }
        // today's phone counter can be ahead of Samsung's sync
        days[today] = maxOf(days[today] ?: 0, PhoneSteps.today.value)

        val keys = mutableSetOf<String>()
        val result = mutableListOf<Earned>()
        val set = Settings.current
        val workouts = WorkoutStore.workouts.value
        fun add(def: BadgeDef, hits: List<Pair<String, LocalDate>>) {
            hits.forEach { keys += "${def.id}:${it.first}" }
            result += Earned(def, hits.size, hits.maxByOrNull { it.second.toEpochDay() }?.second)
        }
        fun steps(def: BadgeDef) = def.label.removeSuffix("K").toLong() * 1000

        DEFS.forEach { def ->
            when (def.group) {
                BadgeGroup.DAILY -> add(def, days.filter { it.value >= steps(def) }.map { it.key.toString() to it.key })
                BadgeGroup.WEEKLY -> add(def, days.entries.groupBy { it.key.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)) }
                    .filter { g -> g.value.sumOf { it.value } >= steps(def) }.map { "w${it.key}" to it.key.plusDays(6).let { d -> if (d.isAfter(today)) today else d } })
                BadgeGroup.MONTHLY -> add(def, days.entries.groupBy { it.key.withDayOfMonth(1) }
                    .filter { g -> g.value.sumOf { it.value } >= steps(def) }.map { "m${it.key}" to it.key.withDayOfMonth(it.key.lengthOfMonth()).let { d -> if (d.isAfter(today)) today else d } })
                BadgeGroup.SLEEP -> when (def.id) {
                    "s7" -> add(def, sleeps.filter { it.value >= 420 }.map { it.key.toString() to it.key })
                    "s8" -> add(def, sleeps.filter { it.value >= 480 }.map { it.key.toString() to it.key })
                    else -> {
                        val need = if (def.id == "s3n") 3 else 7
                        val good = sleeps.filter { it.value >= 420 }.keys.sortedBy { it.toEpochDay() }
                        var run = 0; var prev: LocalDate? = null
                        val hits = mutableListOf<Pair<String, LocalDate>>()
                        good.forEach { d -> run = if (prev != null && prev!!.plusDays(1) == d) run + 1 else 1; prev = d; if (run % need == 0) hits += d.toString() to d }
                        add(def, hits)
                    }
                }
                BadgeGroup.RUN_KM -> {
                    val km = mapOf("r3" to 3.0, "r5" to 5.0, "r10" to 10.0, "r21" to 21.1, "r42" to 42.2)[def.id]!!
                    add(def, workouts.filter { it.runM >= km * 1000 || (it.type.walkRun && it.type != com.bachir.khutwa.shared.WorkoutType.WALK && it.distanceM >= km * 1000) }
                        .map { it.id to Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() })
                }
                BadgeGroup.RUN_MIN -> {
                    val min = def.id.removePrefix("rm").toInt()
                    add(def, workouts.filter { it.runMs >= min * 60_000L || (it.type == com.bachir.khutwa.shared.WorkoutType.RUN && it.activeMs >= min * 60_000L) }
                        .map { it.id to Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() })
                }
                BadgeGroup.GOALS -> when (def.id) {
                    "g_steps" -> add(def, days.filter { it.value >= set.stepGoal }.map { it.key.toString() to it.key })
                    else -> add(def, workouts.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)) }
                        .filter { g -> g.value.sumOf { it.distanceM } >= set.weeklyGoalKm * 1000.0 }.map { "w${it.key}" to it.key })
                }
            }
        }
        earned.value = result

        val prefs = ctx.getSharedPreferences("badges", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", null)
        if (seen != null) {
            val newDefs = (keys - seen).mapNotNull { k -> DEFS.firstOrNull { it.id == k.substringBefore(':') } }.distinct()
            if (newDefs.isNotEmpty()) fresh.value = fresh.value + newDefs
        }
        prefs.edit().putStringSet("seen", keys).apply()
    }
}

// ───────── shield drawing ─────────

private fun shieldPath(w: Float, h: Float, inset: Float = 0f) = Path().apply {
    val l = w * 0.08f + inset; val r = w * 0.92f - inset; val t = h * 0.04f + inset; val b = h * 0.98f - inset
    moveTo(l, t + h * 0.08f)
    quadraticTo(w / 2, t - h * 0.03f, r, t + h * 0.08f)
    lineTo(r, h * 0.5f)
    cubicTo(r, h * 0.76f, w * 0.72f, h * 0.9f, w / 2, b)
    cubicTo(w * 0.28f, h * 0.9f, l, h * 0.76f, l, h * 0.5f)
    close()
}

private fun brushFor(s: Shield, w: Float, h: Float): Brush = when (s) {
    Shield.WHITE -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFBFC4CC)), Offset.Zero, Offset(w, h))
    Shield.BLUE -> Brush.linearGradient(listOf(Color(0xFF7CC4FF), Color(0xFF1E5BD9)), Offset.Zero, Offset(w, h))
    Shield.ORANGE -> Brush.linearGradient(listOf(Color(0xFFFFC266), Color(0xFFFF5A1A)), Offset.Zero, Offset(w, h))
    Shield.PURPLE -> Brush.linearGradient(listOf(Color(0xFFD1A6FF), Color(0xFF6A2CD9)), Offset.Zero, Offset(w, h))
    Shield.MEGA -> Brush.sweepGradient(listOf(Color(0xFFFF3DB8), Color(0xFF7A5CFF), Color(0xFF2FD4FF), Color(0xFFFF3DB8)), Offset(w / 2, h / 2))
    Shield.ULTIMATE -> Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF050506)), Offset.Zero, Offset(w, h))
    Shield.ARMORED -> Brush.linearGradient(listOf(Color(0xFFB8C0CA), Color(0xFF5A636E), Color(0xFF8D96A1)), Offset.Zero, Offset(w, h))
    Shield.POWER -> Brush.linearGradient(listOf(Color(0xFFFFE14D), Color(0xFFFF3B30)), Offset(0f, h), Offset(w, 0f))
    Shield.GOLD -> Brush.linearGradient(listOf(Color(0xFFFFF0A8), Color(0xFFE5B530), Color(0xFFA9781A)), Offset.Zero, Offset(w, h))
    Shield.PLATINUM -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD3D9E0), Color(0xFF9AA4AF), Color(0xFFE9EDF1)), Offset.Zero, Offset(w, h))
    Shield.SAPPHIRE -> Brush.radialGradient(listOf(Color(0xFF5B8CFF), Color(0xFF1B3FD8), Color(0xFF071A66)), Offset(w * 0.35f, h * 0.3f), w * 0.9f)
    Shield.POWER_MEGA -> Brush.sweepGradient(listOf(Color(0xFFFF3B30), Color(0xFFFFE14D), Color(0xFF2FD47A), Color(0xFF2FD4FF), Color(0xFF7A5CFF), Color(0xFFFF3B30)), Offset(w / 2, h / 2))
    Shield.MOON -> Brush.linearGradient(listOf(Color(0xFF5A78FF), Color(0xFF1C2C8A)), Offset.Zero, Offset(w, h))
    Shield.MOON_GOLD -> Brush.linearGradient(listOf(Color(0xFF3A2C8C), Color(0xFF130A3A)), Offset.Zero, Offset(w, h))
    Shield.RUN -> Brush.linearGradient(listOf(Color(0xFFFF9A4D), Color(0xFFC4322E)), Offset.Zero, Offset(w, h))
    Shield.RUN_GOLD -> Brush.linearGradient(listOf(Color(0xFFFFD166), Color(0xFFFF5A1A), Color(0xFF8C1D12)), Offset.Zero, Offset(w, h))
    Shield.GOAL -> Brush.linearGradient(listOf(Color(0xFF5BF0A4), Color(0xFF138A55)), Offset.Zero, Offset(w, h))
}

private fun rimFor(s: Shield) = when (s) {
    Shield.ULTIMATE, Shield.GOLD, Shield.MOON_GOLD, Shield.RUN_GOLD -> Color(0xFFFFD166)
    Shield.WHITE, Shield.PLATINUM -> Color(0xFF8B9099)
    else -> Color.White.copy(alpha = 0.75f)
}

private fun textFor(s: Shield) = when (s) {
    Shield.WHITE, Shield.PLATINUM -> Color(0xFF1A1C21)
    Shield.ULTIMATE -> Color(0xFFFFD166)
    else -> Color.White
}

private fun DrawScope.drawShield(style: Shield, locked: Boolean, shine: Float) {
    val w = size.width; val h = size.height
    val outer = shieldPath(w, h)
    if (locked) {
        drawPath(outer, Color(0x22FFFFFF))
        drawPath(outer, Color(0x55FFFFFF), style = Stroke(2.dp.toPx()))
        return
    }
    drawPath(outer, brushFor(style, w, h))
    clipPath(outer) {
        // moving diagonal shine
        val x = -w + shine * w * 2.5f
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), Offset(x, 0f), Offset(x + w * 0.6f, h)))
        when (style) {
            Shield.ARMORED -> listOf(0.2f to 0.18f, 0.8f to 0.18f, 0.2f to 0.6f, 0.8f to 0.6f).forEach { (cx, cy) ->
                drawCircle(Color(0xFFDDE3EA), radius = w * 0.035f, center = Offset(w * cx, h * cy))
            }
            Shield.SAPPHIRE -> {
                val c = Offset(w / 2, h * 0.45f)
                repeat(6) { i ->
                    val a = Math.toRadians(i * 60.0)
                    drawLine(Color.White.copy(alpha = 0.18f), c, Offset(c.x + (w * cos(a)).toFloat(), c.y + (w * sin(a)).toFloat()), 1.5f)
                }
            }
            Shield.POWER, Shield.POWER_MEGA -> {
                val bolt = Path().apply {
                    moveTo(w * 0.56f, h * 0.12f); lineTo(w * 0.36f, h * 0.52f); lineTo(w * 0.5f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.86f); lineTo(w * 0.66f, h * 0.42f); lineTo(w * 0.52f, h * 0.42f); close()
                }
                drawPath(bolt, Color.White.copy(alpha = 0.22f))
            }
            Shield.MOON, Shield.MOON_GOLD -> drawCircle(Color.White.copy(alpha = 0.15f), radius = w * 0.3f, center = Offset(w * 0.62f, h * 0.36f))
            else -> {}
        }
    }
    drawPath(shieldPath(w, h, w * 0.07f), rimFor(style), style = Stroke(1.6.dp.toPx()))
    drawPath(outer, rimFor(style).copy(alpha = 0.9f), style = Stroke(2.2.dp.toPx()))
}

@Composable
fun ShieldBadge(def: BadgeDef, size: Dp, locked: Boolean = false, animate: Boolean = true) {
    val shine by rememberInfiniteTransition(label = "shine").animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "s")
    Box(Modifier.size(size, size * 1.12f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) { drawShield(def.style, locked, if (animate) shine else 0.3f) }
        Text(
            def.label, style = numStyle((size.value * 0.24f).sp, if (locked) Color(0x88FFFFFF) else textFor(def.style)),
            textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = size * 0.1f),
        )
    }
}

private fun groupTitle(g: BadgeGroup) = when (g) {
    BadgeGroup.DAILY -> tr("خطوات اليوم")
    BadgeGroup.WEEKLY -> tr("خطوات الأسبوع")
    BadgeGroup.MONTHLY -> tr("خطوات الشهر")
    BadgeGroup.SLEEP -> tr("النوم")
    BadgeGroup.RUN_KM -> tr("الجري بالكيلومترات")
    BadgeGroup.RUN_MIN -> tr("الجري بالدقائق")
    BadgeGroup.GOALS -> tr("الأهداف")
}

@Composable
fun BadgesScreen(list: List<Earned>, onBack: () -> Unit) {
    val p = LocalPalette.current
    val total = list.count { it.count > 0 }
    LazyColumn(
        Modifier.fillMaxSize().background(p.bg).statusBarsPadding(),
        contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundButton(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع"), onBack)
                Column(Modifier.weight(1f)) {
                    Text(tr("الشارات"), style = txt(22, p.text, FontWeight.SemiBold))
                    Text(tr("{0} من {1}", total, list.size), style = txt(12, p.muted))
                }
            }
        }
        BadgeGroup.entries.forEachIndexed { gi, g ->
            val items = list.filter { it.def.group == g }
            if (items.isEmpty()) return@forEachIndexed
            item {
                PlainCard(Modifier.fillMaxWidth().enter(gi), radius = 28.dp, pad = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(groupTitle(g), style = txt(16, p.text, FontWeight.SemiBold))
                        items.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { e -> BadgeCell(e, Modifier.weight(1f)) }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCell(e: Earned, modifier: Modifier) {
    val p = LocalPalette.current
    val locked = e.count == 0
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.graphicsLayer { alpha = if (locked) 0.55f else 1f }) { ShieldBadge(e.def, 70.dp, locked) }
        Text(tr(e.def.title), style = txt(12, p.text, FontWeight.Medium), textAlign = TextAlign.Center, maxLines = 1)
        Text(if (locked) tr(e.def.need) else tr("×{0}", e.count), style = txt(10, p.muted), textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Home strip: latest earned badges. */
@Composable
fun BadgeStrip(list: List<Earned>, modifier: Modifier, onOpen: () -> Unit) {
    val p = LocalPalette.current
    val got = list.filter { it.count > 0 }.sortedByDescending { it.last?.toEpochDay() ?: 0L }.take(5)
    PlainCard(modifier.fillMaxWidth().bounceClick(onClick = onOpen), radius = 28.dp, pad = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("شاراتك"), style = txt(16, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text(tr("{0} من {1}", list.count { it.count > 0 }, list.size), style = txt(12, p.muted))
            }
            if (got.isEmpty()) Text(tr("امشِ ٥ آلاف خطوة اليوم وتاخذ أول درع."), style = txt(13, p.muted))
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { got.forEach { ShieldBadge(it.def, 54.dp) } }
        }
    }
}

/** Pop-up when a new badge is earned. */
@Composable
fun BadgeCelebration(def: BadgeDef, onDone: () -> Unit) {
    val scale = remember { Animatable(0.2f) }
    val spin = remember { Animatable(-25f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) { spin.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
    val glow by rememberInfiniteTransition(label = "g").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r")
    Dialog(onDismissRequest = onDone) {
        Column(
            Modifier.clip(RoundedCornerShape(36.dp)).background(Color(0xFF0E0F12)).bounceClick(onClick = onDone).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(tr("شارة جديدة!"), style = txt(15, Color(0xFFA3A6AD), FontWeight.Medium))
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    rotate(glow) {
                        repeat(12) { i ->
                            rotate(i * 30f) {
                                drawRect(Brush.verticalGradient(listOf(Color(0x55FFD166), Color.Transparent)),
                                    topLeft = Offset(size.width / 2 - 6f, 0f), size = Size(12f, size.height / 2))
                            }
                        }
                    }
                }
                Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = spin.value }) { ShieldBadge(def, 140.dp) }
            }
            Text(tr(def.title), style = txt(24, Color.White, FontWeight.Bold))
            Text(tr(def.need), style = txt(14, Color(0xFFC3C6CC)), textAlign = TextAlign.Center)
        }
    }
}

