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
) {
    fun coach(goalM: Int) = CoachConfig(
        coachOn = coachOn, announceEveryM = announceEveryM, maxHr = maxHr,
        hrAlertZone = hrAlertZone, goalM = goalM, runSpeedKmh = runSpeedKmh, autoPause = autoPause,
    )

    companion object {
        fun encode(s: AppSettings) = WorkoutJson.json.encodeToString(serializer(), s)
        fun decode(s: String) = runCatching { WorkoutJson.json.decodeFromString(serializer(), s) }.getOrDefault(AppSettings())
    }
}
