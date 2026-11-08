package com.bachir.khutwa.shared

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

object Notif {
    const val CHANNEL = "workout"
    const val ID = 7

    fun build(ctx: Context, text: String, openIntent: PendingIntent?): Notification = builder(ctx, text, openIntent).build()

    fun builder(ctx: Context, text: String, openIntent: PendingIntent?): NotificationCompat.Builder {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("التمرين"), NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(tr("سافاير"))
            .setSmallIcon(ctx.resources.getIdentifier("ic_run", "drawable", ctx.packageName).takeIf { it != 0 } ?: android.R.drawable.ic_menu_mylocation)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(openIntent)
    }
}
