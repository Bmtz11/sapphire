package com.bachir.khutwa.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bachir.khutwa.shared.Fmt
import com.bachir.khutwa.shared.LiveStats
import com.bachir.khutwa.shared.Mode
import com.bachir.khutwa.shared.WorkoutType
import com.bachir.khutwa.shared.tr

/**
 * The workout notification: visible on the lock screen, and on Android 16 / One UI 8 it is a
 * promoted "Live Update" — it shows in Samsung's Now Bar with a progress bar and a running icon.
 */
object LiveNotif {
    const val CHANNEL = "live_workout"
    const val ID = 7

    private fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("التمرين الشغال"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
    }

    private fun open(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(ctx: Context, action: String, code: Int) = PendingIntent.getService(
        ctx, code, Intent(ctx, PhoneWorkoutService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    fun build(ctx: Context, s: LiveStats?, remote: Boolean = false): Notification {
        channel(ctx)
        val label = when {
            s == null -> tr("تمرين")
            s.paused -> tr("متوقف")
            s.interval != null -> s.interval!!.step.label
            !s.type.walkRun || s.type == WorkoutType.HIKE -> s.type.ar
            s.mode == Mode.RUN -> tr("جري")
            else -> tr("مشي")
        } + if (remote) " ⌚" else ""
        val dist = s?.let { Fmt.dist(it.distanceM) + " " + tr("كم") } ?: ""
        val line = s?.let {
            listOfNotNull(
                if (it.type == WorkoutType.CYCLING) String.format(java.util.Locale.US, "%.1f km/h", it.speedKmh) else Fmt.pace(it.paceSec) + " /" + tr("كم"),
                it.hr?.let { h -> "♥ ${h}" },
                if (it.steps > 0) "${it.steps} " + tr("خطوة") else null,
            ).joinToString(" • ")
        } ?: ""
        val progress: Int? = s?.let {
            when {
                it.interval != null -> (it.interval!!.fraction * 100).toInt()
                it.goalSec > 0 -> (it.elapsedMs / 10L / it.goalSec).toInt()
                it.goalM > 0 -> (it.distanceM * 100 / it.goalM).toInt()
                else -> null
            }?.coerceIn(0, 100)
        }
        val whenMs = System.currentTimeMillis() - (s?.elapsedMs ?: 0)
        val pauseAction = if (s?.paused == true) "resume" else "pause"
        val pauseLabel = if (s?.paused == true) tr("استئناف") else tr("إيقاف مؤقت")

        if (Build.VERSION.SDK_INT >= 36) {
            val b = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_run)
                .setContentTitle("${label} • ${dist}")
                .setContentText(line)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_WORKOUT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open(ctx))
                .setWhen(whenMs)
                .setShowWhen(true)
                .setUsesChronometer(s?.paused != true)
                .setShortCriticalText(dist)
                .setRequestPromotedOngoing(true)
                .apply { if (!remote) addAction(Notification.Action.Builder(null, pauseLabel, action(ctx, pauseAction, 1)).build()) }
            val style = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressTrackerIcon(Icon.createWithResource(ctx, R.drawable.ic_run))
                .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(android.graphics.Color.parseColor("#FF7A1A"))))
            if (progress != null) style.setProgress(progress) else style.setProgressIndeterminate(false).setProgress(0)
            b.setStyle(style)
            return b.build()
        }
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_run)
            .setContentTitle("${label} • ${dist}")
            .setContentText(line)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open(ctx))
            .setWhen(whenMs)
            .setShowWhen(true)
            .setUsesChronometer(s?.paused != true)
            .apply { if (progress != null) setProgress(100, progress, false) }
            .apply { if (!remote) addAction(0, pauseLabel, action(ctx, pauseAction, 1)) }
            .build()
    }

    fun update(ctx: Context, s: LiveStats?) {
        ctx.getSystemService(NotificationManager::class.java).notify(ID, build(ctx, s))
    }
}
