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
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.bachir.khutwa.shared.TodaySummary
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Reads today's numbers from Health Connect (Samsung Health writes steps, sleep and heart rate there),
 * then pushes a copy to the watch.
 */
object TodayReader {
    val READ = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    val today = MutableStateFlow(TodaySummary())

    suspend fun refresh(ctx: Context) {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val date = LocalDate.now(zone)
        val dayStart = date.atStartOfDay(zone).toInstant()
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).atStartOfDay(zone).toInstant()
        val monthStart = date.withDayOfMonth(1).atStartOfDay(zone).toInstant()

        // Fallback: our own workouts only.
        val local = WorkoutStore.workouts.value
        var s = TodaySummary(
            exerciseMin = local.filter { it.startMs >= dayStart.toEpochMilli() }.sumOf { it.durationMs / 60_000 }.toInt(),
            workoutsWeek = local.count { it.startMs >= weekStart.toEpochMilli() },
            workoutsMonth = local.count { it.startMs >= monthStart.toEpochMilli() },
            updatedMs = now.toEpochMilli(),
        )

        if (HealthSync.available(ctx)) runCatching {
            val client = HealthConnectClient.getOrCreate(ctx)
            val granted = client.permissionController.getGrantedPermissions()
            fun has(p: String) = p in granted

            if (has(HealthPermission.getReadPermission(StepsRecord::class))) {
                val agg = client.aggregate(
                    AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(dayStart, now))
                )
                s = s.copy(steps = agg[StepsRecord.COUNT_TOTAL] ?: 0L)
            }
            if (has(HealthPermission.getReadPermission(DistanceRecord::class))) {
                val agg = client.aggregate(
                    AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), TimeRangeFilter.between(dayStart, now))
                )
                s = s.copy(distanceM = agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0)
            }
            if (has(HealthPermission.getReadPermission(HeartRateRecord::class))) {
                val hr = client.readRecords(
                    ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minus(6, ChronoUnit.HOURS), now))
                ).records.flatMap { it.samples }.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
                s = s.copy(hr = hr)
            }
            if (has(HealthPermission.getReadPermission(RestingHeartRateRecord::class))) {
                val r = client.readRecords(
                    ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(dayStart.minus(1, ChronoUnit.DAYS), now))
                ).records.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
                s = s.copy(restingHr = r)
            }
            if (has(HealthPermission.getReadPermission(SleepSessionRecord::class))) {
                val sleeps = client.readRecords(
                    ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(dayStart.minus(12, ChronoUnit.HOURS), now))
                ).records
                val min = sleeps.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                s = s.copy(sleepMin = min.takeIf { it > 0 })
            }
            if (has(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                val sessions = client.readRecords(
                    ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(monthStart, now))
                ).records
                val todayMin = sessions.filter { it.endTime.isAfter(dayStart) }.sumOf {
                    Duration.between(maxOf(it.startTime, dayStart), it.endTime).toMinutes()
                }.toInt()
                s = s.copy(
                    exerciseMin = todayMin,
                    workoutsWeek = sessions.count { !it.startTime.isBefore(weekStart) },
                    workoutsMonth = sessions.size,
                )
            }
        }

        today.value = s
        runCatching { pushToWatch(ctx, s) }
    }

    private suspend fun pushToWatch(ctx: Context, s: TodaySummary) {
        val req = PutDataMapRequest.create("/khutwa/today").apply {
            dataMap.putString("json", TodaySummary.encode(s))
        }.asPutDataRequest()
        Wearable.getDataClient(ctx).putDataItem(req).await()
    }
}
