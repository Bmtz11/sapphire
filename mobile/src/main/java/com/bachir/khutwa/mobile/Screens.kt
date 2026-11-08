package com.bachir.khutwa.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bachir.khutwa.shared.Fmt
import com.bachir.khutwa.shared.IntervalPlan
import com.bachir.khutwa.shared.IntervalStep
import com.bachir.khutwa.shared.LiveStats
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.WeatherInfo
import com.bachir.khutwa.shared.Workout
import com.bachir.khutwa.shared.WorkoutRecorder
import com.bachir.khutwa.shared.WorkoutType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val TYPE_DESC = mapOf(
    WorkoutType.WALK to "يكتشف الجري تلقائياً ويقسم الجلسة لمقاطع مشي وجري.",
    WorkoutType.RUN to "وتيرتك بالصوت، والمشي ينحسب استراحة.",
    WorkoutType.INTERVALS to "خطة جري ومشي بالوقت أو المسافة، والمدرب يقول لك متى تبدّل.",
    WorkoutType.HIKE to "مسار وصعود ونزول بالباروميتر.",
    WorkoutType.TREADMILL to "داخلي بدون GPS — المسافة من خطواتك وطولك.",
    WorkoutType.CYCLING to "سرعة ومسافة ومسار.",
    WorkoutType.FOOTBALL to "مسافة، سبرنتات، أعلى سرعة ونبض.",
)

// ───────────────────────── Start: choose workout ─────────────────────────

@Composable
fun StartScreen(onBack: () -> Unit, onEditPlan: (String?) -> Unit, onStart: (WorkoutType, Int, Int) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val set by Settings.state.collectAsState()
    var type by remember { mutableStateOf(WorkoutType.WALK) }
    var goalMode by remember { mutableIntStateOf(0) }   // 0 none, 1 distance, 2 time
    var goalKm by remember { mutableIntStateOf(5) }
    var goalMin by remember { mutableIntStateOf(30) }
    val types = WorkoutType.entries

    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack)
                    Text("وش بتتمرن اليوم؟", style = txt(22, p.text, FontWeight.Medium))
                }
            }
            types.chunked(2).forEachIndexed { row, pair ->
                item {
                    Row(Modifier.enter(row), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { t -> TypeTile(t, type == t, Modifier.weight(1f)) { type = t } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                AnimatedContent(type, label = "desc") { t ->
                    Text(TYPE_DESC[t] ?: "", style = txt(13, p.muted), modifier = Modifier.padding(horizontal = 4.dp))
                }
            }

            if (type == WorkoutType.INTERVALS) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("خطتك", style = txt(15, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        Chip("+ خطة جديدة", false) { onEditPlan(null) }
                    }
                }
                items(set.plans, key = { it.id }) { plan ->
                    PlanCard(plan, plan.id == set.activePlan?.id, onSelect = { Settings.update(ctx) { it.copy(activePlanId = plan.id) } }, onEdit = { onEditPlan(plan.id) })
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الهدف", style = txt(15, p.text, FontWeight.SemiBold))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("بدون", goalMode == 0) { goalMode = 0 }
                            if (type != WorkoutType.FOOTBALL) Chip("مسافة", goalMode == 1) { goalMode = 1 }
                            Chip("وقت", goalMode == 2) { goalMode = 2 }
                        }
                        AnimatedVisibility(goalMode == 1, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(1, 2, 3, 5, 8, 10, 15, 21, 42)) { km -> Chip("$km كم", goalKm == km) { goalKm = km } }
                            }
                        }
                        AnimatedVisibility(goalMode == 2, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(10, 15, 20, 30, 45, 60, 90, 120)) { m -> Chip("$m د", goalMin == m) { goalMin = m } }
                            }
                        }
                    }
                }
            }
            item {
                PlainCard(Modifier.fillMaxWidth(), radius = 24.dp, pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("المدرب الصوتي", style = txt(14, p.text, FontWeight.Medium))
                            Text(
                                when (type) {
                                    WorkoutType.FOOTBALL -> "بداية ونهاية المباراة"
                                    WorkoutType.INTERVALS -> "كل مرحلة + عد تنازلي قبل التبديل"
                                    else -> "كل ${if (set.announceEveryM == 500) "٥٠٠ متر" else "كيلو"} + الأهداف"
                                },
                                style = txt(12, p.muted),
                            )
                        }
                        Switch(
                            checked = set.coachOn,
                            onCheckedChange = { on -> Settings.update(ctx) { it.copy(coachOn = on) } },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = p.invText, checkedTrackColor = p.invBg,
                                uncheckedThumbColor = p.muted, uncheckedTrackColor = p.bg, uncheckedBorderColor = p.line,
                            ),
                        )
                    }
                }
            }
        }
        val gM = if (goalMode == 1 && type != WorkoutType.FOOTBALL && type != WorkoutType.INTERVALS) goalKm * 1000 else 0
        val gS = if (goalMode == 2 && type != WorkoutType.INTERVALS) goalMin * 60 else 0
        CtaPill(
            "ابدأ ${type.ar}" + when { gM > 0 -> " • $goalKm كم"; gS > 0 -> " • $goalMin د"; else -> "" },
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
            onClick = { onStart(type, gM, gS) }, play = true,
        )
    }
}

@Composable
private fun TypeTile(type: WorkoutType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val ring by animateColorAsState(if (selected) p.text else Color.Transparent, label = "ring")
    val scale by animateFloatAsState(if (selected) 1f else 0.97f, label = "sc")
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.border(2.dp, ring, RoundedCornerShape(34.dp)).padding(3.dp)) {
        ToneCard(toneFor(type), Modifier.fillMaxWidth().height(104.dp).bounceClick(onClick = onClick), radius = 31.dp, pad = 14.dp) {
            Icon(typeIcon(type), null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).size(26.dp))
            if (selected) Box(Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color(0xFF0B0B0C), modifier = Modifier.size(15.dp))
            }
            Text(type.ar, style = txt(17, Color.White, FontWeight.SemiBold), modifier = Modifier.align(Alignment.BottomStart), maxLines = 1)
        }
    }
}

@Composable
fun PlanCard(plan: IntervalPlan, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    val p = LocalPalette.current
    val ring by animateColorAsState(if (selected) CardTone.INTERVALS.base else p.line.copy(alpha = 0f), label = "pr")
    PlainCard(Modifier.fillMaxWidth().border(2.dp, ring, RoundedCornerShape(26.dp)).bounceClick(onClick = onSelect), radius = 26.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = txt(15, p.text, FontWeight.SemiBold), maxLines = 1)
                    Text(planSummary(plan), style = txt(12, p.muted), maxLines = 1)
                }
                Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, p.line, CircleShape).bounceClick(onClick = onEdit), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, "تعديل", tint = p.text, modifier = Modifier.size(16.dp))
                }
            }
            PlanStrip(plan)
        }
    }
}

fun planSummary(plan: IntervalPlan): String {
    val run = if (plan.runM > 0) "${plan.runM} م" else "${plan.runSec / 60} د"
    val walk = if (plan.walkSec % 60 == 0) "${plan.walkSec / 60} د" else "${plan.walkSec} ث"
    val total = plan.totalSec / 60
    return "${plan.repeats} × جري $run / مشي $walk" + if (plan.runM == 0) " • $total د" else ""
}

/** A tiny timeline: each block is coloured by kind and sized by its length. */
@Composable
fun PlanStrip(plan: IntervalPlan, current: Int = -1) {
    val steps = plan.steps()
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEachIndexed { i, s ->
            val w = if (s.meters > 0) s.meters / 3f else s.sec.toFloat()
            val c = when (s.kind) {
                IntervalStep.Kind.RUN -> RunOrange
                IntervalStep.Kind.WALK -> WalkBlue
                else -> Color(0xFF8B9099)
            }
            Box(Modifier.weight(w.coerceAtLeast(1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                .background(if (current >= 0 && i < current) c.copy(alpha = 0.35f) else c)
                .then(if (i == current) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(3.dp)) else Modifier))
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (selected) p.invBg else p.card, label = "chip")
    val fg by animateColorAsState(if (selected) p.invText else p.text, label = "chipfg")
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bg).border(1.dp, if (selected) bg else p.line, RoundedCornerShape(20.dp))
            .bounceClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(label, style = txt(14, fg, FontWeight.Medium)) }
}

@Composable
fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(if (p.dark) Color(0xFF17181B) else Color.White.copy(alpha = 0.8f))
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = p.text, modifier = Modifier.size(20.dp)) }
}

// ───────────────────────── Countdown ─────────────────────────

@Composable
fun CountdownScreen(type: WorkoutType, onDone: () -> Unit) {
    var n by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (n > 0) { delay(900); n-- }
        delay(450); onDone()
    }
    val color = toneFor(type).base
    val pulse = remember { Animatable(0.6f) }
    LaunchedEffect(n) { pulse.snapTo(0.6f); pulse.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF070708))
            .liveBlobs(listOf(Blob(color, 0.5f, 0.45f, 0.85f), Blob(Color(0xFF070708), 0.5f, 0.45f, 0.32f)), drift = 0.04f, periodMs = 3000),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(n, transitionSpec = { (scaleIn(initialScale = 1.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut()) }, label = "cd") { v ->
            Text(if (v > 0) "$v" else "يلا!", style = if (v > 0) numStyle(180.sp, Color.White) else txt(72, Color.White, FontWeight.Bold),
                modifier = Modifier.graphicsLayer { scaleX = pulse.value.coerceAtLeast(0.85f); scaleY = pulse.value.coerceAtLeast(0.85f) })
        }
        Text(type.ar, style = txt(18, Color.White.copy(alpha = 0.85f)), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
}

fun modeColor(type: WorkoutType, mode: Mode) = when {
    type == WorkoutType.FOOTBALL -> FootballGreen
    type == WorkoutType.CYCLING -> Color(0xFF46E0D0)
    type == WorkoutType.HIKE -> Color(0xFFE0B060)
    mode == Mode.RUN -> RunOrange
    else -> WalkBlue
}

// ───────────────────────── Live workout ─────────────────────────

@Composable
fun LiveScreen(s: LiveStats) {
    val ctx = LocalContext.current
    val set by Settings.state.collectAsState()
    val iv = s.interval
    val target = when {
        s.paused -> Color(0xFF6B6F76)
        iv != null -> when (iv.step.kind) { IntervalStep.Kind.RUN -> RunOrange; IntervalStep.Kind.WALK -> WalkBlue; else -> Color(0xFF8E7BFF) }
        else -> modeColor(s.type, s.mode)
    }
    val color by animateColorAsState(target, tween(700), label = "mode")
    val breathe by rememberInfiniteTransition(label = "br").animateFloat(
        0.66f, 0.8f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    val chip = when {
        s.paused -> "متوقف"
        iv != null -> "${iv.step.kind.ar} • ${iv.index + 1}/${iv.total}"
        !s.type.walkRun || s.type == WorkoutType.HIKE || s.type == WorkoutType.TREADMILL -> s.type.ar + if (s.type == WorkoutType.TREADMILL && s.mode == Mode.RUN) " • جري" else ""
        s.mode == Mode.RUN -> "جري"
        else -> "مشي"
    }
    val timeLeft = if (s.goalSec > 0) (s.goalSec - s.elapsedMs / 1000).coerceAtLeast(0) else null

    // big number + its label
    val (bigLabel, bigValue, bigSub) = when {
        iv != null -> Triple(iv.step.label, iv.remainingSec?.let { Fmt.duration(it * 1000L) } ?: "${iv.remainingM} م",
            iv.next?.let { "بعدها: ${it.label}" } ?: "آخر مرحلة")
        timeLeft != null -> Triple("الوقت المتبقي", Fmt.duration(timeLeft * 1000), "الوتيرة ${Fmt.pace(s.paceSec)} /كم")
        s.type == WorkoutType.CYCLING -> Triple("السرعة كم/س", String.format(Locale.US, "%.1f", s.speedKmh), "اضغط للسماع")
        s.type == WorkoutType.FOOTBALL -> Triple("الوقت", Fmt.duration(s.elapsedMs), "اضغط للسماع")
        else -> Triple("الوتيرة الحالية", Fmt.pace(s.paceSec), "دقيقة / كم • اضغط للسماع")
    }
    val ringFrac = when {
        iv != null -> iv.fraction
        s.goalSec > 0 -> (s.elapsedMs / 1000f / s.goalSec).coerceIn(0f, 1f)
        s.goalM > 0 -> (s.distanceM / s.goalM).toFloat().coerceIn(0f, 1f)
        else -> null
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF070708))
            .liveBlobs(listOf(Blob(color, 0.5f, 0.24f, breathe), Blob(Color(0xFF070708), 0.5f, 0.24f, 0.26f)), drift = 0.05f, periodMs = 7000)
            .statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(color).padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF0B0B0C)))
                    AnimatedContent(chip, label = "chip") { Text(it, style = txt(15, Color(0xFF0B0B0C), FontWeight.Bold)) }
                }
                Spacer(Modifier.weight(1f))
                s.weather?.let { WeatherChip(it) }
                if (s.type.gps) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.LocationOn, null, tint = if (s.gpsOk) FootballGreen else Color(0xFFC3C6CC), modifier = Modifier.size(16.dp))
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(290.dp), contentAlignment = Alignment.Center) {
                ringFrac?.let { Ring(it, Color.White, Color.White.copy(alpha = 0.16f), 270.dp, stroke = 6.dp) }
                Column(
                    Modifier.pointerInput(Unit) { detectTapGestures(onTap = { PhoneWorkoutService.speakStatus(ctx) }) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(bigLabel, label = "bl") { Text(it, style = txt(15, Color.White.copy(alpha = 0.92f), FontWeight.Medium)) }
                    Text(bigValue, style = numStyle(if (bigValue.length > 5) 84.sp else 112.sp, Color.White))
                    Text(bigSub, style = txt(13, Color.White.copy(alpha = 0.85f)))
                }
            }
        }
        if (iv != null) item {
            set.activePlan?.let { PlanStrip(it, iv.index) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveBox(if (timeLeft != null) "مضى" else "الوقت", Fmt.duration(s.elapsedMs), null, Modifier.weight(1f))
                LiveBox("المسافة", Fmt.dist(s.distanceM), " كم", Modifier.weight(1f))
                if (s.type == WorkoutType.FOOTBALL) LiveBox("سبرنت", "${s.sprints}", null, Modifier.weight(1f))
                else LiveHeart(s.hr, s.zone, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("خطوات", s.steps.grouped(), Modifier.weight(1f))
                MiniStat("خطوة/د", s.cadence?.toString() ?: "--", Modifier.weight(1f))
                MiniStat("سعرات", "${s.kcal}", Modifier.weight(1f))
                if (s.type == WorkoutType.HIKE || s.ascentM > 3) MiniStat("صعود م", "${s.ascentM.roundToInt()}", Modifier.weight(1f))
                else if (s.type == WorkoutType.FOOTBALL) LiveHeartMini(s.hr, Modifier.weight(1f))
                else MiniStat("المعدل", Fmt.pace(s.avgPaceSec), Modifier.weight(1f))
            }
        }
        if (s.hr != null) item { ZoneBar(s.zone, set.hrAlertZone) }
        if (s.type.walkRun && s.type != WorkoutType.INTERVALS) item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xD1141518)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row {
                    Text("مقاطع الجلسة", style = txt(12, Color(0xFFA3A6AD)), modifier = Modifier.weight(1f))
                    Text("جري ${"%.2f".format(Locale.US, s.runM / 1000)} • مشي ${"%.2f".format(Locale.US, s.walkM / 1000)}", style = numStyle(13.sp, Color(0xFFA3A6AD), FontWeight.SemiBold))
                }
                SegmentBar(s.segments, 10.dp)
            }
        }
        item {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Color.White)
                        .bounceClick { if (s.paused) PhoneWorkoutService.resume(ctx) else PhoneWorkoutService.pause(ctx) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (s.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (s.paused) "استئناف" else "إيقاف مؤقت",
                        tint = Color(0xFF0B0B0C), modifier = Modifier.size(28.dp))
                }
                HoldToEnd(Modifier.weight(1f)) { PhoneWorkoutService.stop(ctx) }
            }
        }
    }
}

@Composable
fun WeatherChip(w: WeatherInfo, onDark: Boolean = true) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (onDark) Color(0x99141518) else Color(0x14000000)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("${w.tempC.roundToInt()}°", style = numStyle(14.sp, if (onDark) Color.White else LocalPalette.current.text, FontWeight.Bold))
        Text("${w.ar} • ${w.humidity}٪".ar(), style = txt(11, if (onDark) Color(0xFFC3C6CC) else LocalPalette.current.muted))
    }
}

/** Press and hold: a fill sweeps across, ends when full. */
@Composable
private fun HoldToEnd(modifier: Modifier, onEnd: () -> Unit) {
    val prog = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (holding) { prog.animateTo(1f, tween(1200)); if (prog.value >= 1f) onEnd() }
        else prog.animateTo(0f, tween(250))
    }
    Box(
        modifier.height(64.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFF141518))
            .border(1.dp, Color(0xFF3A3C42), RoundedCornerShape(32.dp))
            .pointerInput(Unit) { detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(prog.value).background(Color(0xFFE53935)))
        Text(if (holding) "كمّل ضغط…" else "اضغط مطوّل للإنهاء", style = txt(16, Color.White, FontWeight.Medium))
    }
}

@Composable
private fun LiveBox(label: String, value: String, unit: String?, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xD1141518)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = txt(12, Color(0xFFA3A6AD)))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = numStyle(28.sp, Color.White))
            unit?.let { Text(it, style = txt(11, Color(0xFFA3A6AD)), modifier = Modifier.padding(bottom = 4.dp)) }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xB3141518)).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(value, style = numStyle(20.sp, Color.White, FontWeight.Bold), maxLines = 1)
        Text(label, style = txt(10, Color(0xFFA3A6AD)), maxLines = 1)
    }
}

@Composable
private fun LiveHeart(hr: Int?, zone: Int, modifier: Modifier) {
    val c by animateColorAsState(ZoneColors[zone.coerceIn(0, 5)], label = "z")
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xD1141518)).border(1.5.dp, c.copy(alpha = if (zone > 0) 0.9f else 0f), RoundedCornerShape(26.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(if (zone > 0) "النبض • م$zone".ar() else "النبض", style = txt(12, Color(0xFFA3A6AD)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(hr?.toString() ?: "--", style = numStyle(28.sp, Color.White))
            BeatingHeart(hr, HeartRed, 14.dp)
        }
    }
}

@Composable
private fun LiveHeartMini(hr: Int?, modifier: Modifier) = MiniStat("النبض", hr?.toString() ?: "--", modifier)

@Composable
fun ZoneBar(zone: Int, alertZone: Int) {
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { z ->
            val on = z <= zone
            val a by animateFloatAsState(if (on) 1f else 0.22f, label = "zb")
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(ZoneColors[z].copy(alpha = a))
                .then(if (alertZone > 0 && z == alertZone + 1) Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp)) else Modifier))
        }
    }
}

// ───────────────────────── Workout detail + map ─────────────────────────

@Composable
fun DetailScreen(w: Workout, onBack: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var askDelete by remember { mutableStateOf(false) }
    var fullMap by remember { mutableStateOf<Boolean?>(null) }     // null closed, false open, true open + replay
    var bySpeed by remember { mutableStateOf(Settings.current.routeColorBySpeed) }
    fullMap?.let { replay -> FullMap(w, p.dark, replay) { fullMap = null } }
    val date = SimpleDateFormat("EEEE d MMM • h:mm a", Locale("ar")).format(Date(w.startMs))

    LazyColumn(
        Modifier.fillMaxSize().background(p.bg).statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack)
                Column(Modifier.weight(1f)) {
                    Text(w.title, style = txt(20, p.text, FontWeight.SemiBold), maxLines = 1)
                    Text("$date • ${if (w.source == "watch") "الساعة" else "الجوال"}", style = txt(12, p.muted), maxLines = 1)
                }
                RoundButton(Icons.Outlined.DeleteOutline, "حذف") { askDelete = true }
            }
        }
        w.weather?.let { item { WeatherChip(it, onDark = p.dark) } }

        if (w.points.size >= 2) item {
            Box(Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(38.dp)).background(if (p.dark) Color(0xFF0E1013) else Color(0xFFF2F2F2)).enter(0)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    RouteMap(w, p.dark, Modifier.fillMaxSize(), bySpeed = bySpeed)
                }
                Box(Modifier.matchParentSize().bounceClick { fullMap = false })
                Row(Modifier.align(Alignment.TopStart).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (w.type.walkRun) ModeToggle(bySpeed) { bySpeed = it; Settings.update(ctx) { s -> s.copy(routeColorBySpeed = it) } }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(14.dp).size(40.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.OpenInFull, "ملء الشاشة", tint = Color(0xFF0B0B0C), modifier = Modifier.size(18.dp)) }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).bounceClick { fullMap = true }
                                .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF0B0B0C), modifier = Modifier.size(20.dp))
                            Text("تشغيل المسار", style = txt(13, Color(0xFF0B0B0C), FontWeight.SemiBold))
                        }
                        if (bySpeed || !w.type.walkRun) SpeedLegend(w)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xE6141518)).padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("المسافة كم", style = txt(12, Color(0xFFA3A6AD)))
                            CountText(w.distanceM.toLong(), numStyle(46.sp, Color.White), { "%.2f".format(Locale.US, it / 1000.0) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            MapStat("الوقت", Fmt.duration(w.activeMs))
                            when (w.type) {
                                WorkoutType.FOOTBALL -> MapStat("أعلى سرعة", "${(w.maxSpeedMps * 3.6).roundToInt()}")
                                WorkoutType.CYCLING -> MapStat("المعدل كم/س", String.format(Locale.US, "%.1f", w.distanceM / 1000 / (w.activeMs / 3_600_000.0).coerceAtLeast(0.001)))
                                else -> MapStat("المعدل /كم", Fmt.pace(w.avgPaceSec))
                            }
                        }
                    }
                }
            }
        } else item {
            ToneCard(toneFor(w.type), Modifier.fillMaxWidth().height(170.dp).enter(0), radius = 34.dp, pad = 20.dp) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("المسافة كم", style = txt(13, Color.White))
                    CountText(w.distanceM.toLong(), numStyle(60.sp, Color.White), { "%.2f".format(Locale.US, it / 1000.0) })
                }
                Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                    Text(Fmt.duration(w.activeMs), style = numStyle(26.sp, Color.White))
                    Text("${Fmt.pace(w.avgPaceSec)} /كم", style = numStyle(15.sp, Color.White.copy(alpha = 0.85f), FontWeight.SemiBold))
                }
            }
        }

        item {
            Row(Modifier.enter(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    w.type == WorkoutType.FOOTBALL -> {
                        SmallTone(CardTone.FOOTBALL, "السبرنتات", "${w.sprints}", "", "فوق ١٨ كم/س", Modifier.weight(1f))
                        SmallTone(CardTone.SLEEP, "أعلى سرعة", "${(w.maxSpeedMps * 3.6).roundToInt()}", " كم/س", "", Modifier.weight(1f))
                    }
                    w.type == WorkoutType.HIKE || w.ascentM > 15 -> {
                        SmallTone(CardTone.HIKE, "صعود", "${w.ascentM.roundToInt()}", " م", "", Modifier.weight(1f))
                        SmallTone(CardTone.SLEEP, "نزول", "${w.descentM.roundToInt()}", " م", "", Modifier.weight(1f))
                    }
                    w.type.walkRun -> {
                        SmallTone(CardTone.RUN, "جري", Fmt.dist(w.runM), " كم", segPace(w, Mode.RUN), Modifier.weight(1f))
                        SmallTone(CardTone.WALK, "مشي", Fmt.dist(w.walkM), " كم", segPace(w, Mode.WALK), Modifier.weight(1f))
                    }
                    else -> {
                        SmallTone(CardTone.CYCLING, "أعلى سرعة", "${(w.maxSpeedMps * 3.6).roundToInt()}", " كم/س", "", Modifier.weight(1f))
                        SmallTone(CardTone.HIKE, "صعود", "${w.ascentM.roundToInt()}", " م", "", Modifier.weight(1f))
                    }
                }
                SmallTone(CardTone.HEART, "النبض", w.avgHr?.toString() ?: "--", "", w.maxHr?.let { "أعلى $it".ar() } ?: "", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.enter(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailMini("خطوات", if (w.steps > 0) w.steps.grouped() else "--", Modifier.weight(1f))
                DetailMini("خطوة/د", w.avgCadence?.toString() ?: "--", Modifier.weight(1f))
                DetailMini("سعرات", if (w.kcal > 0) "${w.kcal}" else "--", Modifier.weight(1f))
                DetailMini("صعود/نزول", "${w.ascentM.roundToInt()}/${w.descentM.roundToInt()}", Modifier.weight(1f))
            }
        }

        if (w.points.count { it.alt != null } > 10 && (w.ascentM > 3 || w.type == WorkoutType.HIKE)) item { ElevationCard(w, Modifier.enter(3)) }

        if (w.type.walkRun && w.segments.size > 0 && w.type != WorkoutType.INTERVALS) item {
            PlainCard(Modifier.fillMaxWidth().enter(4)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("المقاطع", style = txt(16, p.text, FontWeight.SemiBold))
                    SegmentBar(w.segments, 10.dp)
                    Row {
                        Spacer(Modifier.width(58.dp))
                        Text("المسافة", style = txt(11, p.muted), modifier = Modifier.weight(1f))
                        Text("الوقت", style = txt(11, p.muted), modifier = Modifier.width(64.dp))
                        Text("الوتيرة", style = txt(11, p.muted), modifier = Modifier.width(56.dp))
                    }
                    w.segments.forEach { s ->
                        val pace = if (s.distanceM > 20) ((s.endMs - s.startMs) / 1000 / (s.distanceM / 1000)).toInt() else null
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(if (s.mode == Mode.RUN) RunOrange else WalkBlue))
                            Text(if (s.mode == Mode.RUN) "جري" else "مشي", style = txt(13, p.text), modifier = Modifier.padding(start = 10.dp).width(44.dp))
                            Text("${Fmt.dist(s.distanceM)} كم", style = numStyle(16.sp, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                            Text(Fmt.duration(s.endMs - s.startMs), style = numStyle(16.sp, p.muted, FontWeight.SemiBold), modifier = Modifier.width(64.dp))
                            Text(Fmt.pace(pace), style = numStyle(16.sp, p.muted, FontWeight.SemiBold), modifier = Modifier.width(56.dp))
                        }
                    }
                }
            }
        }

        if (w.splits.isNotEmpty()) item { SplitsCard(w, Modifier.enter(5)) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Filled.Check, null, tint = if (w.hcSynced) WalkBlue else p.muted, modifier = Modifier.size(16.dp))
                Text(if (w.hcSynced) "محفوظ في Samsung Health" else "ينتظر المزامنة مع Samsung Health", style = txt(12, p.muted))
            }
        }
    }

    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        title = { Text("تحذف التمرين من التطبيق؟", style = txt(18, p.text, FontWeight.SemiBold)) },
        text = { Text("ما ينحذف من Samsung Health.", style = txt(14, p.muted)) },
        confirmButton = { TextButton(onClick = { askDelete = false; onDelete() }) { Text("حذف", style = txt(15, p.text)) } },
        dismissButton = { TextButton(onClick = { askDelete = false }) { Text("إلغاء", style = txt(15, p.muted)) } },
        containerColor = p.card,
    )
}

@Composable
private fun DetailMini(label: String, value: String, modifier: Modifier) {
    val p = LocalPalette.current
    PlainCard(modifier, radius = 20.dp, pad = 12.dp) {
        Column {
            Text(value, style = numStyle(19.sp, p.text, FontWeight.Bold), maxLines = 1)
            Text(label, style = txt(10, p.muted), maxLines = 1)
        }
    }
}

/** Elevation profile over distance, gradient fill. */
@Composable
private fun ElevationCard(w: Workout, modifier: Modifier) {
    val p = LocalPalette.current
    val data = remember(w.id) {
        var d = 0.0
        w.points.mapIndexedNotNull { i, pt ->
            if (i > 0) d += WorkoutRecorder.haversine(w.points[i - 1], pt)
            pt.alt?.let { d to it }
        }
    }
    if (data.size < 2) return
    val minA = data.minOf { it.second }; val maxA = data.maxOf { it.second }.coerceAtLeast(minA + 5)
    val maxD = data.last().first.coerceAtLeast(1.0)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    PlainCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الارتفاع", style = txt(16, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("${minA.roundToInt()}–${maxA.roundToInt()} م", style = numStyle(14.sp, p.muted, FontWeight.Bold))
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    fun x(d: Double) = (d / maxD * size.width).toFloat()
                    fun y(a: Double) = (size.height - ((a - minA) / (maxA - minA) * size.height * 0.9f * grow.value)).toFloat()
                    val line = Path().apply { data.forEachIndexed { i, (d, a) -> if (i == 0) moveTo(x(d), y(a)) else lineTo(x(d), y(a)) } }
                    val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(Color(0x99E0B060), Color(0x00E0B060))))
                    drawPath(line, Color(0xFFE0B060), style = Stroke(2.5.dp.toPx()))
                }
            }
            Row {
                Text("↑ ${w.ascentM.roundToInt()} م", style = numStyle(14.sp, p.text, FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("↓ ${w.descentM.roundToInt()} م", style = numStyle(14.sp, p.muted, FontWeight.Bold))
            }
        }
    }
}

private fun segPace(w: Workout, mode: Mode): String {
    val segs = w.segments.filter { it.mode == mode }
    val d = segs.sumOf { it.distanceM }
    val t = segs.sumOf { it.endMs - it.startMs } / 1000.0
    return if (d > 50) "${Fmt.pace((t / (d / 1000)).toInt())} /كم" else ""
}

@Composable
private fun MapStat(label: String, value: String) {
    Column {
        Text(label, style = txt(12, Color(0xFFA3A6AD)))
        Text(value, style = numStyle(24.sp, Color.White))
    }
}

@Composable
private fun SmallTone(tone: CardTone, label: String, value: String, unit: String, sub: String, modifier: Modifier) {
    ToneCard(tone, modifier.height(110.dp), radius = 26.dp, pad = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = txt(12, Color.White))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = numStyle(28.sp, Color.White))
                Text(unit, style = txt(11, Color.White), modifier = Modifier.padding(bottom = 4.dp))
            }
            if (sub.isNotEmpty()) Text(sub, style = txt(11, Color.White.copy(alpha = 0.9f)), maxLines = 1)
        }
    }
}

@Composable
private fun SplitsCard(w: Workout, modifier: Modifier) {
    val p = LocalPalette.current
    val fastest = w.splits.minOf { it.durationSec }
    val slowest = w.splits.maxOf { it.durationSec }.coerceAtLeast(fastest + 1)
    PlainCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الكيلومترات", style = txt(16, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("أسرع كيلو ", style = txt(12, p.muted))
                Text(Fmt.pace(fastest), style = numStyle(15.sp, p.text, FontWeight.Bold))
            }
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                w.splits.takeLast(8).forEachIndexed { i, sp ->
                    val f = 0.35f + 0.65f * (slowest - sp.durationSec).toFloat() / (slowest - fastest)
                    val grow = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { delay(i * 80L); grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                    val t = (slowest - sp.durationSec).toFloat() / (slowest - fastest)
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(Fmt.pace(sp.durationSec), style = numStyle(12.sp, if (sp.durationSec == fastest) p.text else p.muted, FontWeight.Bold))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height((84 * f * grow.value).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(10.dp)).background(speedColor(t)))
                        Spacer(Modifier.height(6.dp))
                        Text("${sp.km}", style = numStyle(12.sp, p.muted, FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

