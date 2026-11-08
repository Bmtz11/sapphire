package com.bachir.khutwa.shared

import kotlinx.serialization.Serializable

/** Everything on the phone's Settings screen. */
@Serializable
data class AppSettings(
    val name: String = "بشير",
    val theme: Int = 0,                 // 0 auto, 1 light, 2 dark
    val coachOn: Boolean = true,
    val announceEveryM: Int = 1000,
    val voiceRate: Float = 1.0f,
    val maxHr: Int = 190,
    val hrAlertZone: Int = 4,           // 0 off; alert when above zone N
    val stepGoal: Int = 10_000,
    val exerciseGoalMin: Int = 60,
    val weeklyGoalKm: Int = 15,
    val runSpeedKmh: Double = 7.4,
    val autoPause: Boolean = false,
    val countdown: Boolean = true,
    val keepScreenOn: Boolean = true,
    val syncHealth: Boolean = true,
    val profile: Profile = Profile(),
    val waterGoalMl: Int = 0,           // 0 = auto from weight
    val plans: List<IntervalPlan> = DEFAULT_PLANS,
    val activePlanId: String = DEFAULT_PLANS.first().id,
    val weatherOn: Boolean = true,
    val routeColorBySpeed: Boolean = true,
) {
    val activePlan get() = plans.firstOrNull { it.id == activePlanId } ?: plans.firstOrNull()
    val waterGoal get() = if (waterGoalMl > 0) waterGoalMl else profile.waterGoalMl

    fun coach(goalM: Int, goalSec: Int = 0) = CoachConfig(
        coachOn = coachOn, announceEveryM = announceEveryM, maxHr = maxHr,
        hrAlertZone = hrAlertZone, goalM = goalM, goalSec = goalSec, runSpeedKmh = runSpeedKmh, autoPause = autoPause,
        profile = profile, plan = activePlan,
    )

    companion object {
        val DEFAULT_PLANS = listOf(
            IntervalPlan("p1", "بداية الجري: ٢ جري / ١ مشي", warmupSec = 300, runSec = 120, walkSec = 60, repeats = 8, cooldownSec = 300),
            IntervalPlan("p2", "٥ جري / ١ مشي", warmupSec = 300, runSec = 300, walkSec = 60, repeats = 5, cooldownSec = 300),
            IntervalPlan("p3", "سرعات ٤٠٠ متر", warmupSec = 600, runM = 400, walkSec = 90, repeats = 6, cooldownSec = 300),
        )
        fun encode(s: AppSettings) = WorkoutJson.json.encodeToString(serializer(), s)
        fun decode(s: String) = runCatching { WorkoutJson.json.decodeFromString(serializer(), s) }.getOrDefault(AppSettings())
    }
}
