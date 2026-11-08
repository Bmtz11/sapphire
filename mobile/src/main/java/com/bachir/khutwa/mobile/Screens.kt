package com.bachir.khutwa.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bachir.khutwa.shared.Fmt
import com.bachir.khutwa.shared.LiveStats
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.Workout
import com.bachir.khutwa.shared.WorkoutType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ───────────────────────── Start: choose workout ─────────────────────────

@Composable
fun StartScreen(onBack: () -> Unit, onStart: (WorkoutType, Int) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val set by Settings.state.collectAsState()
    var type by remember { mutableStateOf(WorkoutType.WALK) }
    var goalKm by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack)
                    Text("وش بتتمرن اليوم؟", style = txt(22, p.text, FontWeight.Medium))
                }
            }
            item { TypeCard(CardTone.WALK, "مشي", "يكتشف الجري تلقائياً ويقسم الجلسة لمقاطع.", type == WorkoutType.WALK, Modifier.enter(0)) { type = WorkoutType.WALK } }
            item { TypeCard(CardTone.RUN, "جري", "وتيرتك بالصوت، والمشي ينحسب استراحة.", type == WorkoutType.RUN, Modifier.enter(1)) { type = WorkoutType.RUN } }
            item { TypeCard(CardTone.FOOTBALL, "كرة قدم", "مسافة، سبرنتات، أعلى سرعة ونبض.", type == WorkoutType.FOOTBALL, Modifier.enter(2)) { type = WorkoutType.FOOTBALL } }

            if (type != WorkoutType.FOOTBALL) item {
                Column(Modifier.enter(3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هدف المسافة", style = txt(14, p.text, FontWeight.Medium))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0, 1, 2, 3, 5, 8, 10, 21)) { km ->
                            Chip(if (km == 0) "بدون" else "$km كم", goalKm == km) { goalKm = km }
                        }
                    }
                }
            }
            item {
                PlainCard(Modifier.fillMaxWidth().enter(4), radius = 24.dp, pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("المدرب الصوتي", style = txt(14, p.text, FontWeight.Medium))
                            Text(
                                if (type == WorkoutType.FOOTBALL) "بداية ونهاية المباراة"
                                else "كل ${if (set.announceEveryM == 500) "٥٠٠ متر" else "كيلو"} + تغيير المشي والجري",
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
        CtaPill(
            "ابدأ ${type.ar}" + if (goalKm > 0 && type != WorkoutType.FOOTBALL) " • $goalKm كم" else "",
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
            onClick = { onStart(type, if (type == WorkoutType.FOOTBALL) 0 else goalKm * 1000) }, play = true,
        )
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
private fun TypeCard(tone: CardTone, title: String, desc: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val h by animateFloatAsState(if (selected) 180f else 120f, tween(350, easing = FastOutSlowInEasing), label = "h")
    val ring by animateColorAsState(if (selected) p.text else Color.Transparent, label = "ring")
    Box(modifier.fillMaxWidth().border(2.dp, ring, RoundedCornerShape(42.dp)).padding(4.dp)) {
        ToneCard(tone, Modifier.fillMaxWidth().height(h.dp).bounceClick(onClick = onClick), radius = 38.dp, pad = 20.dp) {
            Text(title, style = txt(26, Color.White, FontWeight.SemiBold), modifier = Modifier.align(Alignment.TopStart))
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color.White).size(32.dp),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Check, null, tint = Color(0xFF0B0B0C), modifier = Modifier.size(18.dp)) }
            Text(desc, style = txt(13, Color.White.copy(alpha = 0.92f)).copy(lineHeight = 20.sp),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.8f), maxLines = 2)
        }
    }
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
    val color = modeColor(type, Mode.WALK)
    val pulse = remember { Animatable(0.6f) }
    LaunchedEffect(n) { pulse.snapTo(0.6f); pulse.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF070708))
            .liveBlobs(listOf(Blob(color, 0.5f, 0.45f, 0.8f), Blob(Color(0xFF070708), 0.5f, 0.45f, 0.32f)), drift = 0.04f, periodMs = 3000),
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
    mode == Mode.RUN -> RunOrange
    else -> WalkBlue
}

// ───────────────────────── Live workout ─────────────────────────

@Composable
fun LiveScreen(s: LiveStats) {
    val ctx = LocalContext.current
    val set by Settings.state.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val football = s.type == WorkoutType.FOOTBALL
    val target = if (s.paused) Color(0xFF6B6F76) else modeColor(s.type, s.mode)
    val color by animateColorAsState(target, tween(700), label = "mode")
    val breathe by rememberInfiniteTransition(label = "br").animateFloat(
        0.68f, 0.8f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    val chip = if (s.paused) "متوقف" else if (football) "كرة قدم" else if (s.mode == Mode.RUN) "جري" else "مشي"

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070708))
            .liveBlobs(listOf(Blob(color, 0.5f, 0.36f, breathe), Blob(Color(0xFF070708), 0.5f, 0.36f, 0.28f)), drift = 0.05f, periodMs = 7000)
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(color).padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF0B0B0C)))
                AnimatedContent(chip, label = "chip") { Text(it, style = txt(15, Color(0xFF0B0B0C), FontWeight.Bold)) }
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.LocationOn, null, tint = if (s.gpsOk) FootballGreen else Color(0xFFC3C6CC), modifier = Modifier.size(16.dp))
            Text(if (s.gpsOk) " GPS" else " ينتظر GPS", style = txt(13, Color(0xFFC3C6CC)))
        }

        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            if (s.goalM > 0) Ring((s.distanceM / s.goalM).toFloat().coerceIn(0f, 1f), Color.White, Color.White.copy(alpha = 0.18f), 270.dp, stroke = 6.dp)
            Column(
                Modifier.pointerInput(Unit) { detectTapGestures(onTap = { PhoneWorkoutService.speakStatus(ctx) }) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (football) "الوقت" else "الوتيرة الحالية", style = txt(14, Color.White.copy(alpha = 0.9f)))
                Text(if (football) Fmt.duration(s.elapsedMs) else Fmt.pace(s.paceSec), style = numStyle(if (football) 96.sp else 120.sp, Color.White))
                Text(
                    if (s.goalM > 0) "الهدف ${"%.1f".format(Locale.US, s.goalM / 1000.0)} كم • ${((s.distanceM / s.goalM) * 100).roundToInt().coerceAtMost(100)}٪".ar()
                    else if (football) "اضغط للسماع" else "دقيقة / كم • اضغط للسماع",
                    style = txt(14, Color.White.copy(alpha = 0.9f)),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!football) LiveBox("الوقت", Fmt.duration(s.elapsedMs), null, Modifier.weight(1f))
            LiveBox("المسافة", Fmt.dist(s.distanceM), " كم", Modifier.weight(1f))
            if (football) LiveBox("سبرنت", "${s.sprints}", null, Modifier.weight(1f))
            LiveHeart(s.hr, s.zone, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        if (s.hr != null) { ZoneBar(s.zone, set.hrAlertZone); Spacer(Modifier.height(10.dp)) }
        if (!football) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xD1141518)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row {
                Text("مقاطع الجلسة", style = txt(12, Color(0xFFA3A6AD)), modifier = Modifier.weight(1f))
                Text("جري ${"%.2f".format(Locale.US, s.runM / 1000)} • مشي ${"%.2f".format(Locale.US, s.walkM / 1000)}", style = numStyle(13.sp, Color(0xFFA3A6AD), FontWeight.SemiBold))
            }
            SegmentBar(s.segments, 10.dp)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("تنهي التمرين؟", style = txt(18, Color.White, FontWeight.SemiBold)) },
        confirmButton = { TextButton(onClick = { confirm = false; PhoneWorkoutService.stop(ctx) }) { Text("إنهاء", style = txt(15, Color.White)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("كمّل", style = txt(15, Color(0xFFA3A6AD))) } },
        containerColor = Color(0xFF141518),
    )
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
    var askDelete by remember { mutableStateOf(false) }
    var fullMap by remember { mutableStateOf(false) }
    if (fullMap) FullMap(w, p.dark) { fullMap = false }
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
                    Text(w.title, style = txt(20, p.text, FontWeight.SemiBold))
                    Text("$date • ${if (w.source == "watch") "الساعة" else "الجوال"}", style = txt(12, p.muted), maxLines = 1)
                }
                RoundButton(Icons.Outlined.DeleteOutline, "حذف") { askDelete = true }
            }
        }

        item {
            Box(Modifier.fillMaxWidth().height(440.dp).clip(RoundedCornerShape(38.dp)).background(if (p.dark) Color(0xFF0E1013) else Color(0xFFF2F2F2)).enter(0)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    RouteMap(w, p.dark, Modifier.fillMaxSize())
                }
                Box(Modifier.matchParentSize().bounceClick { fullMap = true })
                if (w.type != WorkoutType.FOOTBALL) Row(
                    Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) { MapChip(WalkBlue, "مشي"); MapChip(RunOrange, "جري") }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(14.dp).size(40.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.OpenInFull, "ملء الشاشة", tint = Color(0xFF0B0B0C), modifier = Modifier.size(18.dp)) }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
                        .clip(RoundedCornerShape(28.dp)).background(Color(0xE6141518)).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("المسافة كم", style = txt(12, Color(0xFFA3A6AD)))
                        CountText((w.distanceM).toLong(), numStyle(50.sp, Color.White), { "%.2f".format(Locale.US, it / 1000.0) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        MapStat("الوقت", Fmt.duration(w.activeMs))
                        if (w.type == WorkoutType.FOOTBALL) MapStat("أعلى سرعة", "${(w.maxSpeedMps * 3.6).roundToInt()}")
                        else MapStat("المعدل /كم", Fmt.pace(w.avgPaceSec))
                    }
                }
            }
        }

        item {
            Row(Modifier.enter(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (w.type == WorkoutType.FOOTBALL) {
                    SmallTone(CardTone.FOOTBALL, "السبرنتات", "${w.sprints}", "", "فوق ١٨ كم/س", Modifier.weight(1f))
                    SmallTone(CardTone.SLEEP, "أعلى سرعة", "${(w.maxSpeedMps * 3.6).roundToInt()}", " كم/س", "", Modifier.weight(1f))
                } else {
                    SmallTone(CardTone.RUN, "جري", Fmt.dist(w.runM), " كم", segPace(w, Mode.RUN), Modifier.weight(1f))
                    SmallTone(CardTone.WALK, "مشي", Fmt.dist(w.walkM), " كم", segPace(w, Mode.WALK), Modifier.weight(1f))
                }
                SmallTone(CardTone.HEART, "النبض", w.avgHr?.toString() ?: "--", "", w.maxHr?.let { "أعلى $it".ar() } ?: "", Modifier.weight(1f))
            }
        }

        if (w.type != WorkoutType.FOOTBALL && w.segments.isNotEmpty()) item {
            PlainCard(Modifier.fillMaxWidth().enter(2)) {
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

        if (w.splits.isNotEmpty()) item { SplitsCard(w, Modifier.enter(3)) }

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

private fun segPace(w: Workout, mode: Mode): String {
    val segs = w.segments.filter { it.mode == mode }
    val d = segs.sumOf { it.distanceM }
    val t = segs.sumOf { it.endMs - it.startMs } / 1000.0
    return if (d > 50) "${Fmt.pace((t / (d / 1000)).toInt())} /كم" else ""
}

@Composable
private fun MapChip(c: Color, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xBF070708)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Text(label, style = txt(12, Color.White))
    }
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
                    val runShare = runShareOfKm(w, sp.km - 1)
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(Fmt.pace(sp.durationSec), style = numStyle(12.sp, if (sp.durationSec == fastest) p.text else p.muted, FontWeight.Bold))
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth().height((84 * f * grow.value).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(10.dp))) {
                            if (runShare < 1f) Box(Modifier.fillMaxWidth().weight((1f - runShare).coerceAtLeast(0.001f)).background(WalkBlue))
                            if (runShare > 0f) Box(Modifier.fillMaxWidth().weight(runShare.coerceAtLeast(0.001f)).background(RunOrange))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${sp.km}", style = numStyle(12.sp, p.muted, FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

private fun runShareOfKm(w: Workout, kmIndex: Int): Float {
    val from = kmIndex * 1000.0
    val to = from + 1000.0
    var pos = 0.0
    var run = 0.0
    w.segments.forEach { s ->
        val a = pos; val b = pos + s.distanceM
        val overlap = (minOf(b, to) - maxOf(a, from)).coerceAtLeast(0.0)
        if (s.mode == Mode.RUN) run += overlap
        pos = b
    }
    return (run / 1000.0).toFloat().coerceIn(0f, 1f)
}

