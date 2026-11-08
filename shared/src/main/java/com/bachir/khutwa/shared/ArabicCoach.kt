package com.bachir.khutwa.shared

import java.util.Locale
import kotlin.math.roundToInt

/** All spoken lines. Edit the wording here — both apps use it. */
object ArabicCoach {

    fun start(type: WorkoutType = WorkoutType.WALK) = when (type) {
        WorkoutType.WALK -> "بدأنا المشي. أول ما تبدأ تجري بنتبه لك."
        WorkoutType.RUN -> "بدأنا الجري. بعطيك وتيرتك كل كيلو."
        WorkoutType.FOOTBALL -> "بدأت المباراة. بالتوفيق."
        WorkoutType.INTERVALS -> "بدأنا الجري المخصص."
        WorkoutType.HIKE -> "بدأنا الهايكنج. بحسب لك الصعود والنزول."
        WorkoutType.TREADMILL -> "بدأنا التمرين الداخلي. المسافة من خطواتك."
        WorkoutType.CYCLING -> "بدأنا ركوب الدراجة."
    }

    fun phrase(e: CoachEvent): String = when (e) {
        is CoachEvent.ModeChanged ->
            if (e.mode == Mode.RUN) "بديت الجري. خلك على نفس مريح."
            else "رجعت للمشي. جريت ${distance(e.lastSegment.distanceM)} في ${duration(e.lastSegment.endMs - e.lastSegment.startMs)}."

        is CoachEvent.Distance -> buildString {
            append("${distance(e.meters.toDouble())}. ")
            if (e.meters % 1000 == 0) append("الكيلو الأخير ${pace(e.lastSplitSec)}.")
            else append("آخر خمسمية متر ${pace(e.lastSplitSec)}.")
            e.currentPaceSec?.let { append(" وتيرتك الحين ${pace(it)}.") }
        }
        is CoachEvent.GoalHalf -> "وصلت نص الهدف. باقي ${distance(e.goalM / 2.0)}."
        is CoachEvent.GoalReached -> "مبروك! حققت هدف ${distance(e.goalM.toDouble())} في ${duration(e.elapsedMs)}."
        is CoachEvent.ZoneHigh -> "نبضك ${e.hr}، أنت في المنطقة ${e.zone}. خفف شوي."
        is CoachEvent.TimeHalf -> "مر نص الوقت. باقي ${duration(e.goalSec * 500L)}."
        is CoachEvent.TimeLeft -> "باقي دقيقة وحدة. شد حيلك."
        is CoachEvent.TimeReached -> "خلص الوقت! قطعت ${distance(e.distanceM)}."
        is CoachEvent.IntervalNext -> buildString {
            append("${e.step.label}.")
            if (e.index == 0 && e.total > 1) append(" عندك ${e.total} مراحل.")
            e.next?.let { append(" بعدها ${it.label}.") }
        }
        CoachEvent.IntervalsDone -> "خلصت التمرين المخصص. عاش! تقدر تكمل أو تنهي."
        is CoachEvent.IntervalCountdown -> "${e.sec}"
        CoachEvent.AutoPaused -> "إيقاف مؤقت."
        CoachEvent.AutoResumed -> "كملنا."
    }

    fun paused() = "إيقاف مؤقت."
    fun resumed() = "كملنا. يلا."

    fun status(s: LiveStats?): String {
        if (s == null) return "ما فيه تمرين شغال."
        val mode = if (s.mode == Mode.RUN) "تجري" else "تمشي"
        return buildString {
            append("أنت $mode. المسافة ${distance(s.distanceM)}.")
            s.paceSec?.let { append(" الوتيرة ${pace(it)} للكيلو.") }
            s.hr?.let { append(" النبض $it.") }
        }
    }

    fun finish(w: Workout) = buildString {
        append("انتهى التمرين. المسافة ${distance(w.distanceM)} في ${duration(w.activeMs)}.")
        if (w.kcal > 20) append(" حرقت تقريباً ${w.kcal} سعرة.")
        if (w.ascentM > 15) append(" صعدت ${w.ascentM.roundToInt()} متر.")
        if (w.type == WorkoutType.FOOTBALL) {
            if (w.sprints > 0) append(" سويت ${w.sprints} سبرنت.")
            if (w.maxSpeedMps > 0) append(" أعلى سرعة ${(w.maxSpeedMps * 3.6).roundToInt()} كيلو بالساعة.")
        } else if (w.runM > 50) append(" منها جري ${distance(w.runM)}.")
    }

    fun pace(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return if (s == 0) "${m} ${minuteWord(m)}" else "${m} ${minuteWord(m)} و ${s} ثانية"
    }

    fun duration(ms: Long): String {
        val total = (ms / 1000).toInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "$h ساعة و $m ${minuteWord(m)}"
            m > 0 -> if (s > 0) "$m ${minuteWord(m)} و $s ثانية" else "$m ${minuteWord(m)}"
            else -> "$s ثانية"
        }
    }

    fun distance(m: Double): String =
        if (m < 1000) "${((m / 10).roundToInt() * 10)} متر"
        else "${String.format(Locale.US, "%.1f", m / 1000)} كيلو"

    private fun minuteWord(n: Int) = when (n) {
        1 -> "دقيقة"
        2 -> "دقيقتين"
        in 3..10 -> "دقائق"
        else -> "دقيقة"
    }
}
