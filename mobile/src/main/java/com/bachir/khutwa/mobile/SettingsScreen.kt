package com.bachir.khutwa.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bachir.khutwa.shared.AppSettings
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit, onConnectHealth: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val s by Settings.state.collectAsState()
    fun up(f: (AppSettings) -> AppSettings) = Settings.update(ctx, f)

    LazyColumn(
        Modifier.fillMaxSize().background(p.bg).statusBarsPadding(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack)
                Text("الإعدادات", style = txt(22, p.text, FontWeight.SemiBold))
            }
        }

        item {
            Section("عام", Modifier.enter(0)) {
                Item("الاسم") {
                    var name by remember(s.name) { mutableStateOf(s.name) }
                    BasicTextField(
                        name, { name = it; up { st -> st.copy(name = it.take(20)) } },
                        textStyle = txt(15, p.text, FontWeight.Medium), singleLine = true, cursorBrush = SolidColor(p.text),
                        modifier = Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).background(p.bg).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Divider()
                Item("المظهر") {
                    Segmented(listOf("تلقائي", "فاتح", "داكن"), s.theme) { v -> up { it.copy(theme = v) } }
                }
            }
        }

        item {
            Section("الأهداف", Modifier.enter(1)) {
                Item("هدف الخطوات اليومي") { Stepper(s.stepGoal.grouped(), { up { it.copy(stepGoal = (it.stepGoal - 500).coerceAtLeast(1000)) } }, { up { it.copy(stepGoal = it.stepGoal + 500) } }) }
                Divider()
                Item("دقائق التمرين اليومية") { Stepper("${s.exerciseGoalMin}", { up { it.copy(exerciseGoalMin = (it.exerciseGoalMin - 5).coerceAtLeast(10)) } }, { up { it.copy(exerciseGoalMin = it.exerciseGoalMin + 5) } }) }
                Divider()
                Item("هدف الأسبوع (كم)") { Stepper("${s.weeklyGoalKm}", { up { it.copy(weeklyGoalKm = (it.weeklyGoalKm - 1).coerceAtLeast(1)) } }, { up { it.copy(weeklyGoalKm = it.weeklyGoalKm + 1) } }) }
            }
        }

        item {
            Section("المدرب الصوتي", Modifier.enter(2)) {
                Item("تشغيل المدرب") { Toggle(s.coachOn) { v -> up { it.copy(coachOn = v) } } }
                Divider()
                Item("ينطق كل") { Segmented(listOf("٥٠٠ م", "١ كم"), if (s.announceEveryM == 500) 0 else 1) { v -> up { it.copy(announceEveryM = if (v == 0) 500 else 1000) } } }
                Divider()
                Item("سرعة الصوت") {
                    Stepper("%.1fx".format(Locale.US, s.voiceRate),
                        { up { it.copy(voiceRate = ((it.voiceRate - 0.1f).coerceAtLeast(0.6f) * 10).toInt() / 10f) } },
                        { up { it.copy(voiceRate = ((it.voiceRate + 0.1f).coerceAtMost(1.6f) * 10 + 0.5f).toInt() / 10f) } })
                }
            }
        }

        item {
            Section("النبض", Modifier.enter(3)) {
                Item("أقصى نبض", "تقريباً ٢٢٠ ناقص عمرك") { Stepper("${s.maxHr}", { up { it.copy(maxHr = (it.maxHr - 1).coerceAtLeast(140)) } }, { up { it.copy(maxHr = (it.maxHr + 1).coerceAtMost(220)) } }) }
                Divider()
                Item("نبّهني إذا تعديت") {
                    Segmented(listOf("لا", "م٢", "م٣", "م٤"), when (s.hrAlertZone) { 2 -> 1; 3 -> 2; 4 -> 3; else -> 0 }) { v ->
                        up { it.copy(hrAlertZone = if (v == 0) 0 else v + 1) }
                    }
                }
                Divider()
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZoneBar(5, s.hrAlertZone)
                    Row {
                        (1..5).forEach { z ->
                            val lo = s.maxHr * (40 + z * 10) / 100
                            Text("م$z\n$lo+".ar(), style = txt(10, p.muted), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            Section("التمرين", Modifier.enter(4)) {
                Item("عدّ تنازلي قبل البداية") { Toggle(s.countdown) { v -> up { it.copy(countdown = v) } } }
                Divider()
                Item("إيقاف تلقائي إذا وقفت", "للمشي والجري") { Toggle(s.autoPause) { v -> up { it.copy(autoPause = v) } } }
                Divider()
                Item("الشاشة تبقى شغالة") { Toggle(s.keepScreenOn) { v -> up { it.copy(keepScreenOn = v) } } }
                Divider()
                Item("سرعة اكتشاف الجري", "فوقها يعتبرك تجري") {
                    Stepper("%.1f".format(Locale.US, s.runSpeedKmh),
                        { up { it.copy(runSpeedKmh = ((it.runSpeedKmh - 0.2).coerceAtLeast(5.0) * 10).toLong() / 10.0) } },
                        { up { it.copy(runSpeedKmh = ((it.runSpeedKmh + 0.2).coerceAtMost(11.0) * 10 + 0.5).toLong() / 10.0) } })
                }
            }
        }

        item {
            Section("Samsung Health", Modifier.enter(5)) {
                Item("حفظ التمارين في Samsung Health") { Toggle(s.syncHealth) { v -> up { it.copy(syncHealth = v) } } }
                Divider()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onConnectHealth).padding(vertical = 12.dp)) {
                    Text("صلاحيات Health Connect", style = txt(15, p.text), modifier = Modifier.weight(1f))
                    Text("فتح", style = txt(14, WalkBlue, FontWeight.Medium))
                }
            }
        }
        item { Text("سافاير • ١.١", style = txt(12, p.muted), modifier = Modifier.fillMaxWidth().padding(8.dp)) }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = txt(13, p.muted, FontWeight.Medium), modifier = Modifier.padding(horizontal = 6.dp))
        PlainCard(Modifier.fillMaxWidth(), radius = 26.dp, pad = 16.dp) { Column { content() } }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = LocalPalette.current.line.copy(alpha = 0.35f), thickness = 1.dp)

@Composable
private fun Item(label: String, hint: String? = null, control: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = txt(15, p.text))
            hint?.let { Text(it, style = txt(11, p.muted)) }
        }
        control()
    }
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Switch(
        on, onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = p.invText, checkedTrackColor = p.invBg,
            uncheckedThumbColor = p.muted, uncheckedTrackColor = p.bg, uncheckedBorderColor = p.line,
        ),
    )
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepBtn(Icons.Filled.Remove, "ناقص", onMinus)
        Text(value, style = numStyle(20.sp, p.text, FontWeight.Bold), modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepBtn(Icons.Filled.Add, "زائد", onPlus)
    }
}

@Composable
private fun StepBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, p.line, CircleShape).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = p.text, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(p.bg).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(11.dp)).background(if (on) p.invBg else p.bg)
                    .bounceClick { onSelect(i) }.padding(horizontal = 12.dp, vertical = 7.dp)
            ) { Text(o, style = txt(13, if (on) p.invText else p.muted, FontWeight.Medium)) }
        }
    }
}

