package com.bachir.khutwa.shared

import java.util.Locale
import kotlin.math.roundToInt

/** Picks the coach language from the app language. Services call this, not the language objects. */
object Coach {
    private val c get() = if (Tr.en) EnglishCoach else null
    fun start(type: WorkoutType) = c?.start(type) ?: ArabicCoach.start(type)
    fun phrase(e: CoachEvent) = c?.phrase(e) ?: ArabicCoach.phrase(e)
    fun paused() = c?.paused() ?: ArabicCoach.paused()
    fun resumed() = c?.resumed() ?: ArabicCoach.resumed()
    fun status(s: LiveStats?) = c?.status(s) ?: ArabicCoach.status(s)
    fun finish(w: Workout) = c?.finish(w) ?: ArabicCoach.finish(w)
    fun sample() = if (Tr.en) "Nice and easy. You're at five minutes thirty per kilometre." else "يلا نبدأ. وتيرتك الحين خمس دقايق ونص للكيلو."
}

object EnglishCoach {
    fun start(type: WorkoutType) = when (type) {
        WorkoutType.WALK -> "Walk started. I'll let you know when you start running."
        WorkoutType.RUN -> "Run started. I'll give you your pace every kilometre."
        WorkoutType.FOOTBALL -> "Match started. Good luck."
        WorkoutType.INTERVALS -> "Custom run started."
        WorkoutType.HIKE -> "Hike started. I'm tracking your climb."
        WorkoutType.TREADMILL -> "Indoor workout started. Distance comes from your steps."
        WorkoutType.CYCLING -> "Ride started."
    }

    fun phrase(e: CoachEvent): String = when (e) {
        is CoachEvent.ModeChanged ->
            if (e.mode == Mode.RUN) "You're running. Keep it relaxed."
            else "Back to walking. You ran ${distance(e.lastSegment.distanceM)} in ${duration(e.lastSegment.endMs - e.lastSegment.startMs)}."
        is CoachEvent.Distance -> buildString {
            append("${distance(e.meters.toDouble())}. ")
            append(if (e.meters % 1000 == 0) "Last kilometre ${pace(e.lastSplitSec)}." else "Last five hundred metres ${pace(e.lastSplitSec)}.")
            e.currentPaceSec?.let { append(" Current pace ${pace(it)}.") }
        }
        is CoachEvent.GoalHalf -> "Halfway there. ${distance(e.goalM / 2.0)} to go."
        is CoachEvent.GoalReached -> "Well done! You hit your ${distance(e.goalM.toDouble())} goal in ${duration(e.elapsedMs)}."
        is CoachEvent.ZoneHigh -> "Heart rate ${e.hr}, zone ${e.zone}. Ease off a little."
        is CoachEvent.TimeHalf -> "Halfway. ${duration(e.goalSec * 500L)} left."
        is CoachEvent.TimeLeft -> "One minute left. Push."
        is CoachEvent.TimeReached -> "Time's up! You covered ${distance(e.distanceM)}."
        is CoachEvent.IntervalNext -> buildString {
            append("${e.step.label}.")
            if (e.index == 0 && e.total > 1) append(" ${e.total} blocks today.")
            e.next?.let { append(" Then ${it.label}.") }
        }
        CoachEvent.IntervalsDone -> "Custom run complete. Great work! Keep going or finish."
        is CoachEvent.IntervalCountdown -> "${e.sec}"
        CoachEvent.AutoPaused -> "Paused."
        CoachEvent.AutoResumed -> "Resumed."
    }

    fun paused() = "Paused."
    fun resumed() = "Resumed. Let's go."

    fun status(s: LiveStats?): String {
        if (s == null) return "No workout running."
        return buildString {
            append("You're ${if (s.mode == Mode.RUN) "running" else "walking"}. Distance ${distance(s.distanceM)}.")
            s.paceSec?.let { append(" Pace ${pace(it)} per kilometre.") }
            s.hr?.let { append(" Heart rate $it.") }
        }
    }

    fun finish(w: Workout) = buildString {
        append("Workout complete. ${distance(w.distanceM)} in ${duration(w.activeMs)}.")
        if (w.kcal > 20) append(" About ${w.kcal} calories.")
        if (w.ascentM > 15) append(" You climbed ${w.ascentM.roundToInt()} metres.")
        if (w.type == WorkoutType.FOOTBALL) {
            if (w.sprints > 0) append(" ${w.sprints} sprints.")
            if (w.maxSpeedMps > 0) append(" Top speed ${(w.maxSpeedMps * 3.6).roundToInt()} kilometres per hour.")
        } else if (w.runM > 50) append(" ${distance(w.runM)} of it running.")
    }

    fun pace(sec: Int): String {
        val m = sec / 60; val s = sec % 60
        return if (s == 0) "$m minutes" else "$m minutes $s seconds"
    }

    fun duration(ms: Long): String {
        val total = (ms / 1000).toInt()
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return when {
            h > 0 -> "$h hours $m minutes"
            m > 0 -> if (s > 0) "$m minutes $s seconds" else "$m minutes"
            else -> "$s seconds"
        }
    }

    fun distance(m: Double): String =
        if (m < 1000) "${((m / 10).roundToInt() * 10)} metres"
        else "${String.format(Locale.US, "%.1f", m / 1000)} kilometres"
}
