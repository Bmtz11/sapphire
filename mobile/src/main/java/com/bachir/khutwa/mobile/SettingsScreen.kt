package com.bachir.khutwa.mobile

import com.bachir.khutwa.shared.tr
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
                RoundButton(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع"), onBack)
                Text(tr("الإعدادات"), style = txt(22, p.text, FontWeight.SemiBold))
            }
        }

        item {
            Section(tr("عام"), Modifier.enter(0)) {
                Item(tr("الاسم")) {
                    var name by remember(s.name) { mutableStateOf(s.name) }
                    BasicTextField(
                        name, { name = it; up { st -> st.copy(name = it.take(20)) } },
                        textStyle = txt(15, p.text, FontWeight.Medium), singleLine = true, cursorBrush = SolidColor(p.text),
                        modifier = Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).background(p.bg).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Divider()
                Item(tr("المظهر")) {
                    Segmented(listOf(tr("تلقائي"), tr("فاتح"), tr("داكن")), s.theme) { v -> up { it.copy(theme = v) } }
                }
                Divider()
                Item(tr("اللغة")) {
                    Segmented(listOf(tr("عربي"), "English"), if (s.english) 1 else 0) { v -> up { it.copy(english = v == 1, voiceName = null) } }
                }
            }
        }

        item {
            Section(tr("بياناتك"), Modifier.enter(1)) {
                val pr = s.profile
                Item(tr("الوزن (كجم)")) { Stepper(String.format(Locale.US, "%.1f", pr.weightKg), { up { it.copy(profile = it.profile.copy(weightKg = (it.profile.weightKg - 0.5).coerceAtLeast(30.0))) } }, { up { it.copy(profile = it.profile.copy(weightKg = it.profile.weightKg + 0.5)) } }) }
                Divider()
                Item(tr("الطول (سم)")) { Stepper("${pr.heightCm}", { up { it.copy(profile = it.profile.copy(heightCm = (it.profile.heightCm - 1).coerceAtLeast(120))) } }, { up { it.copy(profile = it.profile.copy(heightCm = (it.profile.heightCm + 1).coerceAtMost(230))) } }) }
                Divider()
                Item(tr("العمر")) { Stepper("${pr.age}", { up { it.copy(profile = it.profile.copy(age = (it.profile.age - 1).coerceAtLeast(10)), maxHr = 220 - (it.profile.age - 1).coerceAtLeast(10)) } }, { up { it.copy(profile = it.profile.copy(age = (it.profile.age + 1).coerceAtMost(99)), maxHr = 220 - (it.profile.age + 1).coerceAtMost(99)) } }) }
                Divider()
                Item(tr("الجنس")) { Segmented(listOf(tr("ذكر"), tr("أنثى")), if (pr.male) 0 else 1) { v -> up { it.copy(profile = it.profile.copy(male = v == 0)) } } }
                Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(tr("مؤشر كتلة الجسم"), style = txt(15, p.text), modifier = Modifier.weight(1f))
                    val bmi = pr.bmi
                    Text(String.format(Locale.US, "%.1f", bmi), style = numStyle(18.sp, p.text, FontWeight.Bold))
                    Text(" • " + when { bmi < 18.5 -> tr("نحيف"); bmi < 25 -> tr("طبيعي"); bmi < 30 -> tr("زيادة"); else -> tr("سمنة") }, style = txt(13, p.muted))
                }
                Text(tr("الوزن والطول للسعرات وطول الخطوة في التمارين الداخلية."), style = txt(11, p.muted))
            }
        }

        item {
            Section(tr("الأهداف"), Modifier.enter(1)) {
                Item(tr("الموية اليومية (مل)"), if (s.waterGoalMl == 0) tr("تلقائي من وزنك") else null) {
                    Stepper(s.waterGoal.grouped(), { up { it.copy(waterGoalMl = (it.waterGoal - 250).coerceAtLeast(1000)) } }, { up { it.copy(waterGoalMl = it.waterGoal + 250) } })
                }
                Divider()
                Item(tr("هدف الخطوات اليومي")) { Stepper(s.stepGoal.grouped(), { up { it.copy(stepGoal = (it.stepGoal - 500).coerceAtLeast(1000)) } }, { up { it.copy(stepGoal = it.stepGoal + 500) } }) }
                Divider()
                Item(tr("دقائق التمرين اليومية")) { Stepper("${s.exerciseGoalMin}", { up { it.copy(exerciseGoalMin = (it.exerciseGoalMin - 5).coerceAtLeast(10)) } }, { up { it.copy(exerciseGoalMin = it.exerciseGoalMin + 5) } }) }
                Divider()
                Item(tr("هدف الأسبوع (كم)")) { Stepper("${s.weeklyGoalKm}", { up { it.copy(weeklyGoalKm = (it.weeklyGoalKm - 1).coerceAtLeast(1)) } }, { up { it.copy(weeklyGoalKm = it.weeklyGoalKm + 1) } }) }
            }
        }

        item {
            Section(tr("المدرب الصوتي"), Modifier.enter(2)) {
                Item(tr("تشغيل المدرب")) { Toggle(s.coachOn) { v -> up { it.copy(coachOn = v) } } }
                Divider()
                Item(tr("ينطق كل")) { Segmented(listOf(tr("٥٠٠ م"), tr("١ كم")), if (s.announceEveryM == 500) 0 else 1) { v -> up { it.copy(announceEveryM = if (v == 0) 500 else 1000) } } }
                Divider()
                Item(tr("سرعة الصوت")) {
                    Stepper("%.1fx".format(Locale.US, s.voiceRate),
                        { up { it.copy(voiceRate = ((it.voiceRate - 0.1f).coerceAtLeast(0.6f) * 10).toInt() / 10f) } },
                        { up { it.copy(voiceRate = ((it.voiceRate + 0.1f).coerceAtMost(1.6f) * 10 + 0.5f).toInt() / 10f) } })
                }
                Divider()
                Item(tr("نبرة الصوت"), tr("أقل = أهدى وأريح")) {
                    Stepper("%.2f".format(Locale.US, s.voicePitch),
                        { up { it.copy(voicePitch = ((it.voicePitch - 0.05f).coerceAtLeast(0.7f) * 100 + 0.5f).toInt() / 100f) } },
                        { up { it.copy(voicePitch = ((it.voicePitch + 0.05f).coerceAtMost(1.3f) * 100 + 0.5f).toInt() / 100f) } })
                }
                Divider()
                VoicePicker(s.english, s.voiceName, s.voiceRate, s.voicePitch) { name -> up { it.copy(voiceName = name) } }
            }
        }

        item {
            Section(tr("النبض"), Modifier.enter(3)) {
                Item(tr("أقصى نبض"), tr("تقريباً ٢٢٠ ناقص عمرك")) { Stepper("${s.maxHr}", { up { it.copy(maxHr = (it.maxHr - 1).coerceAtLeast(140)) } }, { up { it.copy(maxHr = (it.maxHr + 1).coerceAtMost(220)) } }) }
                Divider()
                Item(tr("نبّهني إذا تعديت")) {
                    Segmented(listOf(tr("لا"), tr("م٢"), tr("م٣"), tr("م٤")), when (s.hrAlertZone) { 2 -> 1; 3 -> 2; 4 -> 3; else -> 0 }) { v ->
                        up { it.copy(hrAlertZone = if (v == 0) 0 else v + 1) }
                    }
                }
                Divider()
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZoneBar(5, s.hrAlertZone)
                    Row {
                        (1..5).forEach { z ->
                            val lo = s.maxHr * (40 + z * 10) / 100
                            Text(tr("م{0}\n{1}+", z, lo).ar(), style = txt(10, p.muted), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            Section(tr("التمرين"), Modifier.enter(4)) {
                Item(tr("عدّ تنازلي قبل البداية")) { Toggle(s.countdown) { v -> up { it.copy(countdown = v) } } }
                Divider()
                Item(tr("إيقاف تلقائي إذا وقفت"), tr("للمشي والجري")) { Toggle(s.autoPause) { v -> up { it.copy(autoPause = v) } } }
                Divider()
                Item(tr("الخريطة ثلاثية الأبعاد"), tr("مباني ومنظور مائل")) { Toggle(s.map3d) { v -> up { it.copy(map3d = v) } } }
                Divider()
                Item(tr("التحكم بالموسيقى"), if (Music.hasAccess(ctx)) tr("مفعّل") else "Spotify • YouTube Music • Samsung") {
                    Text(if (Music.hasAccess(ctx)) "✓" else tr("تفعيل"), style = txt(14, WalkBlue, FontWeight.Medium),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).bounceClick { Music.askAccess(ctx) }.padding(8.dp))
                }
                Divider()
                Item(tr("الطقس أثناء التمرين")) { Toggle(s.weatherOn) { v -> up { it.copy(weatherOn = v) } } }
                Divider()
                Item(tr("الشاشة تبقى شغالة")) { Toggle(s.keepScreenOn) { v -> up { it.copy(keepScreenOn = v) } } }
                Divider()
                Item(tr("سرعة اكتشاف الجري"), tr("فوقها يعتبرك تجري")) {
                    Stepper("%.1f".format(Locale.US, s.runSpeedKmh),
                        { up { it.copy(runSpeedKmh = ((it.runSpeedKmh - 0.2).coerceAtLeast(5.0) * 10).toLong() / 10.0) } },
                        { up { it.copy(runSpeedKmh = ((it.runSpeedKmh + 0.2).coerceAtMost(11.0) * 10 + 0.5).toLong() / 10.0) } })
                }
            }
        }

        item {
            Section("Samsung Health", Modifier.enter(5)) {
                Item(tr("حفظ التمارين في Samsung Health")) { Toggle(s.syncHealth) { v -> up { it.copy(syncHealth = v) } } }
                Divider()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).bounceClick(onClick = onConnectHealth).padding(vertical = 12.dp)) {
                    Text(tr("صلاحيات Health Connect"), style = txt(15, p.text), modifier = Modifier.weight(1f))
                    Text(tr("فتح"), style = txt(14, WalkBlue, FontWeight.Medium))
                }
            }
        }
        item { Text(tr("سافاير") + " • " + "1.4".ar(), style = txt(12, p.muted), modifier = Modifier.fillMaxWidth().padding(8.dp)) }
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
        StepBtn(Icons.Filled.Remove, tr("ناقص"), onMinus)
        Text(value, style = numStyle(20.sp, p.text, FontWeight.Bold), modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepBtn(Icons.Filled.Add, tr("زائد"), onPlus)
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



/** Lists the installed TTS voices (best quality first) so the user picks a calm one, with a preview. */
@Composable
private fun VoicePicker(english: Boolean, current: String?, rate: Float, pitch: Float, onPick: (String?) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val speaker = remember(english) { com.bachir.khutwa.shared.TtsSpeaker(ctx, english) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    var voices by remember(english) { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    LaunchedEffect(speaker) {
        repeat(20) { if (voices.isEmpty()) { kotlinx.coroutines.delay(400); voices = speaker.voices() } }
    }
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("صوت المدرب"), style = txt(15, p.text))
        if (voices.isEmpty()) Text(tr("ثبّت Google Text-to-Speech لأصوات أنعم."), style = txt(12, p.muted))
        voices.take(8).forEachIndexed { i, v ->
            val sel = v.name == current || (current == null && i == 0)
            val q = if (v.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH) "HD" else if (v.quality >= android.speech.tts.Voice.QUALITY_HIGH) "HQ" else ""
            val net = if (v.isNetworkConnectionRequired) " • " + tr("نت") else ""
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (sel) WalkBlue.copy(alpha = 0.16f) else p.bg)
                    .bounceClick {
                        onPick(v.name)
                        speaker.rate = rate; speaker.pitch = pitch
                        speaker.useVoice(v.name); speaker.speak(com.bachir.khutwa.shared.Coach.sample())
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("صوت {0}", i + 1) + "  " + v.locale.country, style = txt(14, p.text, if (sel) FontWeight.SemiBold else FontWeight.Normal), modifier = Modifier.weight(1f))
                Text(q + net, style = txt(11, p.muted))
                Spacer(Modifier.width(8.dp))
                Text("▶", style = txt(13, WalkBlue))
            }
        }
    }
}
