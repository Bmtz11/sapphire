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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bachir.khutwa.shared.Fmt
import com.bachir.khutwa.shared.IntervalPlan
import java.util.UUID

/** Build or edit a custom run: warm-up, N × (run / walk), cool-down. */
@Composable
fun PlanEditor(planId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val existing = planId?.let { id -> Settings.current.plans.firstOrNull { it.id == id } }
    var plan by remember { mutableStateOf(existing ?: IntervalPlan(UUID.randomUUID().toString().take(8), "خطتي")) }
    var byDistance by remember { mutableStateOf(plan.runM > 0) }

    fun save() {
        val final = if (byDistance) plan.copy(runSec = 0, runM = plan.runM.coerceAtLeast(100)) else plan.copy(runM = 0, runSec = plan.runSec.coerceAtLeast(30))
        Settings.update(ctx) { s ->
            val list = if (s.plans.any { it.id == final.id }) s.plans.map { if (it.id == final.id) final else it } else s.plans + final
            s.copy(plans = list, activePlanId = final.id)
        }
        onDone()
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoundButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onDone)
                    Text(if (existing == null) "خطة جري جديدة" else "تعديل الخطة", style = txt(22, p.text, FontWeight.Medium), modifier = Modifier.weight(1f))
                    if (existing != null && Settings.current.plans.size > 1) RoundButton(Icons.Outlined.DeleteOutline, "حذف") {
                        Settings.update(ctx) { s -> s.copy(plans = s.plans.filter { it.id != existing.id }) }
                        onDone()
                    }
                }
            }
            item {
                ToneCard(CardTone.INTERVALS, Modifier.fillMaxWidth(), radius = 30.dp, pad = 18.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BasicTextField(
                            plan.name, { plan = plan.copy(name = it.take(30)) },
                            textStyle = txt(20, Color.White, FontWeight.SemiBold), singleLine = true, cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33000000)).padding(12.dp),
                        )
                        PlanStrip(plan)
                        Row {
                            Text(planSummary(plan), style = txt(13, Color.White.copy(alpha = 0.9f)), modifier = Modifier.weight(1f))
                            if (!byDistance) Text(Fmt.duration(plan.totalSec * 1000L), style = numStyle(18.sp, Color.White))
                        }
                    }
                }
            }
            item {
                PlainCard(Modifier.fillMaxWidth(), radius = 26.dp) {
                    Column {
                        Row2("الإحماء (مشي خفيف)") { Step(Fmt.duration(plan.warmupSec * 1000L), { plan = plan.copy(warmupSec = (plan.warmupSec - 60).coerceAtLeast(0)) }, { plan = plan.copy(warmupSec = plan.warmupSec + 60) }) }
                        Line()
                        Row2("الجري بـ") {
                            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(p.bg).padding(3.dp)) {
                                listOf("وقت" to false, "مسافة" to true).forEach { (l, v) ->
                                    Box(Modifier.clip(RoundedCornerShape(11.dp)).background(if (byDistance == v) p.invBg else p.bg)
                                        .bounceClick { byDistance = v; if (v && plan.runM == 0) plan = plan.copy(runM = 400) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                                        Text(l, style = txt(13, if (byDistance == v) p.invText else p.muted, FontWeight.Medium))
                                    }
                                }
                            }
                        }
                        Line()
                        if (byDistance) Row2("مسافة كل جري") { Step("${plan.runM} م", { plan = plan.copy(runM = (plan.runM - 100).coerceAtLeast(100)) }, { plan = plan.copy(runM = plan.runM + 100) }) }
                        else Row2("وقت كل جري") { Step(Fmt.duration(plan.runSec * 1000L), { plan = plan.copy(runSec = (plan.runSec - 30).coerceAtLeast(30)) }, { plan = plan.copy(runSec = plan.runSec + 30) }) }
                        Line()
                        Row2("مشي بين الجري") { Step(Fmt.duration(plan.walkSec * 1000L), { plan = plan.copy(walkSec = (plan.walkSec - 15).coerceAtLeast(0)) }, { plan = plan.copy(walkSec = plan.walkSec + 15) }) }
                        Line()
                        Row2("عدد التكرار") { Step("${plan.repeats}×", { plan = plan.copy(repeats = (plan.repeats - 1).coerceAtLeast(1)) }, { plan = plan.copy(repeats = (plan.repeats + 1).coerceAtMost(30)) }) }
                        Line()
                        Row2("التهدئة") { Step(Fmt.duration(plan.cooldownSec * 1000L), { plan = plan.copy(cooldownSec = (plan.cooldownSec - 60).coerceAtLeast(0)) }, { plan = plan.copy(cooldownSec = plan.cooldownSec + 60) }) }
                    }
                }
            }
        }
        CtaPill("حفظ واستخدام الخطة", Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp), onClick = { save() })
    }
}

@Composable
private fun Line() = HorizontalDivider(color = LocalPalette.current.line.copy(alpha = 0.35f), thickness = 1.dp)

@Composable
private fun Row2(label: String, control: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = txt(15, p.text), modifier = Modifier.weight(1f))
        control()
    }
}

@Composable
private fun Step(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, p.line, CircleShape).bounceClick(onClick = onMinus), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, "ناقص", tint = p.text, modifier = Modifier.size(18.dp))
        }
        Text(value, style = numStyle(18.sp, p.text, FontWeight.Bold), modifier = Modifier.width(76.dp), textAlign = TextAlign.Center)
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, p.line, CircleShape).bounceClick(onClick = onPlus), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, "زائد", tint = p.text, modifier = Modifier.size(18.dp))
        }
    }
}
