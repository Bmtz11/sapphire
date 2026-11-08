package com.bachir.khutwa.mobile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bachir.khutwa.shared.TodaySummary
import com.bachir.khutwa.shared.tr

/** Home-screen widget: today's steps with progress, water, calories and a quick start. */
class SapphireWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(150.dp, 100.dp), DpSize(260.dp, 120.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
        val t = runCatching { TodaySummary.decode(p.getString("today", "{}")!!) }.getOrDefault(TodaySummary())
        val phone = p.getLong("phoneSteps", 0)
        val streak = p.getInt("streak", 0)
        provideContent { Content(t.copy(steps = maxOf(t.steps, phone)), streak) }
    }

    @Composable
    private fun Content(t: TodaySummary, streak: Int) {
        val wide = LocalSize.current.width >= 240.dp
        val frac = (t.steps.toFloat() / t.stepGoal).coerceIn(0f, 1f)
        val white = ColorProvider(Color.White)
        val muted = ColorProvider(Color(0xFFC9B9D6))
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(Color(0xFF3B2F4C))
                .clickable(actionStartActivity<MainActivity>()).padding(16.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_run), null, GlanceModifier.size(18.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(tr("خطوات اليوم"), style = TextStyle(color = muted, fontSize = 12.sp))
                if (streak > 0) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text("🔥 ${streak}", style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Text("%,d".format(java.util.Locale.US, t.steps), style = TextStyle(color = white, fontSize = 34.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = frac,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                color = ColorProvider(Color(0xFFF5E15A)),
                backgroundColor = ColorProvider(Color(0x33FFFFFF)),
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Text("${(frac * 100).toInt()}% / ${"%,d".format(java.util.Locale.US, t.stepGoal)}", style = TextStyle(color = muted, fontSize = 11.sp))
                if (wide) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text("💧 ${t.waterMl}/${t.waterGoalMl}", style = TextStyle(color = white, fontSize = 11.sp))
                    Spacer(GlanceModifier.width(10.dp))
                    Text("🔥 ${t.kcal} kcal", style = TextStyle(color = white, fontSize = 11.sp))
                }
            }
        }
    }

    companion object {
        suspend fun push(ctx: Context, t: TodaySummary, phoneSteps: Long, streak: Int) {
            ctx.getSharedPreferences("widget", Context.MODE_PRIVATE).edit()
                .putString("today", TodaySummary.encode(t)).putLong("phoneSteps", phoneSteps).putInt("streak", streak).apply()
            runCatching { SapphireWidget().updateAll(ctx) }
        }
    }
}

class SapphireWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SapphireWidget()
}
