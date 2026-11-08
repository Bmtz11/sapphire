package com.bachir.khutwa.mobile

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bachir.khutwa.shared.AppSettings
import com.bachir.khutwa.shared.TodaySummary
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class DaySteps(val date: LocalDate, val steps: Long)

/**
 * Reads today's numbers from Health Connect, then pushes a copy to the watch.
 * Steps come from Samsung Health's own record when it is there: it already merges phone + watch,
 * so it matches the number in Samsung Health instead of double counting.
 */
object TodayReader {
    const val SAMSUNG = "com.sec.android.app.shealth"

    val READ = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    val today = MutableStateFlow(TodaySummary())
    val week = MutableStateFlow<List<DaySteps>>(emptyList())
    /** Dates that had any workout (ours or Samsung Health's), last 60 days — for the streak. */
    val activeDays = MutableStateFlow<Set<LocalDate>>(emptySet())

    suspend fun refresh(ctx: Context) {
        val set = Settings.current
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val date = LocalDate.now(zone)
        val dayStart = date.atStartOfDay(zone).toInstant()
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay(zone).toInstant()
        val monthStart = date.withDayOfMonth(1).atStartOfDay(zone).toInstant()

        val local = WorkoutStore.workouts.value
        var s = TodaySummary(
            stepGoal = set.stepGoal.toLong(),
            exerciseGoalMin = set.exerciseGoalMin,
            exerciseMin = local.filter { it.startMs >= dayStart.toEpochMilli() }.sumOf { it.activeMs / 60_000 }.toInt(),
            workoutsWeek = local.count { it.startMs >= weekStart.toEpochMilli() },
            workoutsMonth = local.count { it.startMs >= monthStart.toEpochMilli() },
            updatedMs = now.toEpochMilli(),
        )
        val days = local.map { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }.toMutableSet()

        if (HealthSync.available(ctx)) runCatching {
            val client = HealthConnectClient.getOrCreate(ctx)
            val granted = client.permissionController.getGrantedPermissions()
            fun has(p: String) = p in granted
            val samsung = setOf(DataOrigin(SAMSUNG))

            if (has(HealthPermission.getReadPermission(StepsRecord::class))) {
                suspend fun steps(origins: Set<DataOrigin>) = client.aggregate(
                    AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(dayStart, now), origins)
                )[StepsRecord.COUNT_TOTAL] ?: 0L
                val fromSamsung = steps(samsung)
                s = s.copy(steps = if (fromSamsung > 0) fromSamsung else steps(emptySet()))

                // last 7 days, one bar per day
                val start = date.minusDays(6).atStartOfDay()
                suspend fun byDay(origins: Set<DataOrigin>) = client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start, LocalDateTime.now()),
                        timeRangeSlicer = Period.ofDays(1),
                        dataOriginFilter = origins,
                    )
                ).map { DaySteps(it.startTime.toLocalDate(), it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
                val w = byDay(samsung).takeIf { l -> l.any { it.steps > 0 } } ?: byDay(emptySet())
                week.value = (0..6).map { i -> date.minusDays(6L - i) }.map { d -> w.firstOrNull { it.date == d } ?: DaySteps(d, 0) }
            }
            if (has(HealthPermission.getReadPermission(DistanceRecord::class))) {
                suspend fun dist(origins: Set<DataOrigin>) = client.aggregate(
                    AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), TimeRangeFilter.between(dayStart, now), origins)
                )[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
                val d = dist(samsung)
                s = s.copy(distanceM = if (d > 0) d else dist(emptySet()))
            }
            if (has(HealthPermission.getReadPermission(HeartRateRecord::class))) {
                val hr = client.readRecords(
                    ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now))
                ).records.flatMap { it.samples }.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
                s = s.copy(hr = hr)
            }
            if (has(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
                val r = client.readRecords(
                    ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(now.minus(3, ChronoUnit.DAYS), now))
                ).records.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
                s = s.copy(restingHr = r)
            }
            if (has(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                // latest night: the session that ended most recently in the last 36 h
                val last = client.readRecords(
                    ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(36, ChronoUnit.HOURS), now))
                ).records.maxByOrNull { it.endTime }
                s = s.copy(sleepMin = last?.let { Duration.between(it.startTime, it.endTime).toMinutes().toInt() })
            }
            if (has(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                val sessions = client.readRecords(
                    ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(date.minusDays(60).atStartOfDay(zone).toInstant(), now))
                ).records
                val month = sessions.filter { !it.startTime.isBefore(monthStart) }
                val todayMin = sessions.filter { it.endTime.isAfter(dayStart) }.sumOf {
                    Duration.between(maxOf(it.startTime, dayStart), it.endTime).toMinutes()
                }.toInt()
                s = s.copy(
                    exerciseMin = maxOf(todayMin, s.exerciseMin),
                    workoutsWeek = maxOf(month.count { !it.startTime.isBefore(weekStart) }, s.workoutsWeek),
                    workoutsMonth = maxOf(month.size, s.workoutsMonth),
                )
                sessions.forEach { days += it.startTime.atZone(zone).toLocalDate() }
            }
        }

        Water.refresh(ctx)
        s = s.copy(
            kcal = local.filter { it.startMs >= dayStart.toEpochMilli() }.sumOf { it.kcal },
            waterMl = Water.todayMl.value,
            waterGoalMl = set.waterGoal,
        )
        activeDays.value = days
        today.value = s
        runCatching { SapphireWidget.push(ctx, s, PhoneSteps.today.value, streak(days)) }
        runCatching { Badges.refresh(ctx) }
        runCatching { pushToWatch(ctx, s, set) }
    }

    /** Current streak of days in a row with a workout (today counts if done, else from yesterday). */
    fun streak(days: Set<LocalDate>): Int {
        var d = LocalDate.now()
        if (d !in days) d = d.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    suspend fun pushSettings(ctx: Context, set: AppSettings) = pushToWatch(ctx, today.value, set)

    private suspend fun pushToWatch(ctx: Context, s: TodaySummary, set: AppSettings) {
        val req = PutDataMapRequest.create("/khutwa/today").apply {
            dataMap.putString("json", TodaySummary.encode(s))
            dataMap.putString("coach", com.bachir.khutwa.shared.CoachConfig.encode(set.coach(0)))
        }.asPutDataRequest()
        Wearable.getDataClient(ctx).putDataItem(req).await()
    }
}
