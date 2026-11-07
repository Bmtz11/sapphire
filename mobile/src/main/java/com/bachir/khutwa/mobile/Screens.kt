package com.bachir.khutwa.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ───────────────────────── Start: choose workout ─────────────────────────

@Composable
fun StartScreen(onBack: () -> Unit, onStart: (WorkoutType) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var type by remember { mutableStateOf(WorkoutType.WALK) }
    var coach by remember { mutableStateOf(Prefs.coach(ctx)) }

    Column(
        Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack)
            Text("وش بتتمرن اليوم؟", style = txt(22, p.text, FontWeight.Medium))
        }
        TypeCard(CardTone.WALK, "مشي", "يكتشف الجري تلقائياً ويقسم الجلسة لمقاطع مشي وجري.", type == WorkoutType.WALK, 196.dp) { type = WorkoutType.WALK }
        TypeCard(CardTone.RUN, "جري", "مدرب صوتي بالوتيرة كل كيلو، ومقاطع المشي تنحسب استراحة.", type == WorkoutType.RUN, 168.dp) { type = WorkoutType.RUN }
        TypeCard(CardTone.FOOTBALL, "كرة قدم", "المسافة والسبرنتات وأعلى سرعة والنبض طول المباراة.", type == WorkoutType.FOOTBALL, 168.dp) { type = WorkoutType.FOOTBALL }

        PlainCard(Modifier.fillMaxWidth(), radius = 24.dp, pad = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("المدرب الصوتي", style = txt(14, p.text, FontWeight.Medium))
                    Text(
                        if (type == WorkoutType.FOOTBALL) "بداية ونهاية المباراة" else "كل كيلو + عند تغيير المشي والجري",
                        style = txt(12, p.muted),
                    )
                }
                Switch(
                    checked = coach,
                    onCheckedChange = { coach = it; Prefs.setCoach(ctx, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = p.invText, checkedTrackColor = p.invBg,
                        uncheckedThumbColor = p.muted, uncheckedTrackColor = p.bg,
                    ),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        CtaPill("ابدأ ${type.ar}", Modifier.fillMaxWidth(), onClick = { onStart(type) }, play = true)
    }
}

@Composable
private fun TypeCard(tone: CardTone, title: String, desc: String, selected: Boolean, height: Dp, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, p.text, RoundedCornerShape(42.dp)).padding(4.dp) else Modifier.padding(4.dp))
    ) {
        ToneCard(tone, Modifier.fillMaxWidth().height(height - 8.dp).clickable(onClick = onClick), radius = 38.dp, pad = 20.dp) {
            Text(title, style = txt(26, Color.White, FontWeight.SemiBold), modifier = Modifier.align(Alignment.TopStart))
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(15.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text("محدد", style = txt(12, Color(0xFF0B0B0C), FontWeight.Medium)) }
            Text(desc, style = txt(13, Color.White.copy(alpha = 0.92f)).copy(lineHeight = 21.sp),
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.72f))
        }
    }
}

@Composable
fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, bg: Color? = null) {
    val p = LocalPalette.current
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(bg ?: if (p.dark) Color(0xFF17181B) else Color.White.copy(alpha = 0.7f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = p.text, modifier = Modifier.size(20.dp)) }
}

// ───────────────────────── Live workout ─────────────────────────

@Composable
fun LiveScreen(s: LiveStats) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val football = s.type == WorkoutType.FOOTBALL
    val modeColor = when {
        football -> Color(0xFF3DD68C)
        s.mode == Mode.RUN -> RunOrange
        else -> WalkBlue
    }
    val glow = when {
        football -> listOf(Blob(Color(0xFF2E7A50), 0.5f, 0.38f, 0.75f), Blob(Color(0xFF041A0E), 0.5f, 0.38f, 0.3f))
        s.mode == Mode.RUN -> listOf(Blob(Color(0xFFFF7A1A), 0.5f, 0.38f, 0.75f), Blob(Color(0xFF3A0A00), 0.5f, 0.38f, 0.3f))
        else -> listOf(Blob(Color(0xFF2A6FD6), 0.5f, 0.38f, 0.75f), Blob(Color(0xFF071433), 0.5f, 0.38f, 0.3f))
    }
    val chip = if (football) "كرة قدم" else if (s.mode == Mode.RUN) "جري" else "مشي"

    Column(
        Modifier.fillMaxSize().background(Color(0xFF070708)).blobs(*glow.toTypedArray())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(modeColor).padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF0B0B0C)))
                Text(chip, style = txt(15, Color(0xFF0B0B0C), FontWeight.Bold))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, tint = Color(0xFFC3C6CC), modifier = Modifier.size(15.dp))
                Text(if (s.gpsOk) " GPS" else " ينتظر GPS", style = txt(13, Color(0xFFC3C6CC)))
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 70.dp).clickable { PhoneWorkoutService.speakStatus(ctx) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("الوتيرة الحالية", style = txt(14, Color.White.copy(alpha = 0.9f)))
            Text(Fmt.pace(s.paceSec), style = dotoStyle(124.sp, Color.White))
            Text("دقيقة / كم • اضغط للسماع", style = txt(14, Color.White.copy(alpha = 0.9f)))
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiveBox("الوقت", Fmt.duration(s.elapsedMs), null, Modifier.weight(1f))
            LiveBox("المسافة", Fmt.dist(s.distanceM), " كم", Modifier.weight(1f))
            LiveBox("النبض", s.hr?.toString() ?: "--", " ♥", Modifier.weight(1f), HeartRed)
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xD1141518)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (football) {
                Row {
                    Text("السبرنتات", style = txt(12, Color(0xFFA3A6AD)), modifier = Modifier.weight(1f))
                    Text("${s.sprints}", style = txt(20, Color.White, FontWeight.SemiBold))
                }
            } else {
                Row {
                    Text("مقاطع الجلسة", style = txt(12, Color(0xFFA3A6AD)), modifier = Modifier.weight(1f))
                    Text("جري ${"%.1f".format(Locale.US, s.runM / 1000)} • مشي ${"%.1f".format(Locale.US, s.walkM / 1000)} كم".ar(),
                        style = txt(12, Color(0xFFA3A6AD)))
                }
                SegmentBar(s.segments, 10.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFF141518))
                .border(1.dp, Color(0xFF3A3C42), RoundedCornerShape(32.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { confirm = true }, onLongPress = { PhoneWorkoutService.stop(ctx) })
                },
            contentAlignment = Alignment.Center,
        ) { Text("اضغط مطوّل للإنهاء", style = txt(16, Color.White, FontWeight.Medium)) }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("تنهي التمرين؟", style = txt(18, Color.White, FontWeight.SemiBold)) },
        confirmButton = { TextButton(onClick = { confirm = false; PhoneWorkoutService.stop(ctx) }) { Text("إنهاء", style = txt(15, Color.White)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("كمّل", style = txt(15, Color(0xFFA3A6AD))) } },
        containerColor = Color(0xFF141518),
    )
}

@Composable
private fun LiveBox(label: String, value: String, unit: String?, modifier: Modifier, unitColor: Color = Color(0xFFA3A6AD)) {
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xD1141518)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = txt(12, Color(0xFFA3A6AD)))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = txt(24, Color.White, FontWeight.SemiBold))
            unit?.let { Text(it, style = txt(11, unitColor), modifier = Modifier.padding(bottom = 4.dp)) }
        }
    }
}

// ───────────────────────── Workout detail + map ─────────────────────────

@Composable
fun DetailScreen(w: Workout, onBack: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    var askDelete by remember { mutableStateOf(false) }
    val date = SimpleDateFormat("EEEE h:mm a", Locale("ar")).format(Date(w.startMs))

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
                    Text("$date • ${if (w.source == "watch") "الساعة" else "الجوال"}", style = txt(12, p.muted))
                }
                RoundButton(Icons.Outlined.DeleteOutline, "حذف", { askDelete = true })
            }
        }

        item {
            Box(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(38.dp)).background(Color(0xFF0E1013))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    RouteMap(w, Modifier.fillMaxSize())
                }
                if (w.type != WorkoutType.FOOTBALL) Row(
                    Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapChip(WalkBlue, "مشي"); MapChip(RunOrange, "جري")
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)
                        .clip(RoundedCornerShape(28.dp)).background(Color(0xE0141518)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("المسافة", style = txt(12, Color(0xFFA3A6AD)))
                        Text(Fmt.dist(w.distanceM), style = dotoStyle(52.sp, Color.White))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        MapStat("الوقت", Fmt.duration(w.durationMs))
                        if (w.type == WorkoutType.FOOTBALL) MapStat("أعلى سرعة", "${(w.maxSpeedMps * 3.6).roundToInt()}")
                        else MapStat("المعدل", Fmt.pace(w.avgPaceSec))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (w.type == WorkoutType.FOOTBALL) {
                    SmallTone(CardTone.FOOTBALL, "السبرنتات", "${w.sprints}", "", "أكثر من ١٨ كم/س", Modifier.weight(1f))
                    SmallTone(CardTone.SLEEP, "أعلى سرعة", "${(w.maxSpeedMps * 3.6).roundToInt()}", " كم/س", "", Modifier.weight(1f))
                } else {
                    SmallTone(CardTone.RUN, "جري", Fmt.dist(w.runM), " كم", segPace(w, Mode.RUN), Modifier.weight(1f))
                    SmallTone(CardTone.WALK, "مشي", Fmt.dist(w.walkM), " كم", segPace(w, Mode.WALK), Modifier.weight(1f))
                }
                SmallTone(CardTone.HEART, "النبض", w.avgHr?.toString() ?: "--", " ♥", w.maxHr?.let { "أعلى $it".ar() } ?: "", Modifier.weight(1f))
            }
        }

        if (w.type != WorkoutType.FOOTBALL && w.segments.isNotEmpty()) item {
            PlainCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("المقاطع", style = txt(16, p.text, FontWeight.SemiBold))
                    SegmentBar(w.segments, 10.dp)
                    w.segments.forEach { s ->
                        val pace = if (s.distanceM > 20) ((s.endMs - s.startMs) / 1000 / (s.distanceM / 1000)).toInt() else null
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.width(4.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(if (s.mode == Mode.RUN) RunOrange else WalkBlue))
                            Text(if (s.mode == Mode.RUN) "جري" else "مشي", style = txt(13, p.text), modifier = Modifier.width(44.dp))
                            Text("${Fmt.dist(s.distanceM)} كم", style = txt(13, p.text), modifier = Modifier.weight(1f))
                            Text(Fmt.duration(s.endMs - s.startMs), style = txt(13, p.muted), modifier = Modifier.width(56.dp))
                            Text(Fmt.pace(pace), style = txt(13, p.muted))
                        }
                    }
                }
            }
        }

        if (w.splits.isNotEmpty()) item { SplitsCard(w) }

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
    return if (d > 50) "${Fmt.pace((t / (d / 1000)).toInt())} /كم".ar() else ""
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
        Text(value, style = txt(20, Color.White, FontWeight.SemiBold))
    }
}

@Composable
private fun SmallTone(tone: CardTone, label: String, value: String, unit: String, sub: String, modifier: Modifier) {
    ToneCard(tone, modifier.height(104.dp), radius = 26.dp, pad = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = txt(12, Color.White))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = txt(22, Color.White, FontWeight.SemiBold))
                Text(unit, style = txt(11, if (unit.contains("♥")) HeartRed else Color.White), modifier = Modifier.padding(bottom = 3.dp))
            }
            if (sub.isNotEmpty()) Text(sub, style = txt(11, Color.White.copy(alpha = 0.9f)))
        }
    }
}

@Composable
private fun SplitsCard(w: Workout) {
    val p = LocalPalette.current
    val fastest = w.splits.minOf { it.durationSec }
    val slowest = w.splits.maxOf { it.durationSec }.coerceAtLeast(fastest + 1)
    PlainCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الكيلومترات", style = txt(16, p.text, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("أسرع كيلو ${Fmt.pace(fastest)}", style = txt(12, p.muted))
            }
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                w.splits.takeLast(8).forEach { sp ->
                    // faster km = taller bar
                    val f = 0.35f + 0.65f * (slowest - sp.durationSec).toFloat() / (slowest - fastest)
                    val kmStart = sp.km - 1
                    val runShare = runShareOfKm(w, kmStart)
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(Fmt.pace(sp.durationSec), style = txt(11, if (sp.durationSec == fastest) p.text else p.muted))
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth().height((78 * f).dp).clip(RoundedCornerShape(10.dp))) {
                            if (runShare < 1f) Box(Modifier.fillMaxWidth().weight((1f - runShare).coerceAtLeast(0.001f)).background(WalkBlue))
                            if (runShare > 0f) Box(Modifier.fillMaxWidth().weight(runShare.coerceAtLeast(0.001f)).background(RunOrange))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${sp.km}".ar(), style = txt(11, p.muted))
                    }
                }
            }
        }
    }
}

/** Share of a kilometer that was run, from segment distances. */
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
