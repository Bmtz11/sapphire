package com.bachir.khutwa.wear

import android.content.Context
import com.bachir.khutwa.shared.Workout
import com.bachir.khutwa.shared.WorkoutJson
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

object PhoneLink {
    const val PATH_SPEAK = "/khutwa/speak"
    const val PATH_WORKOUT = "/khutwa/workout/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Used when the watch has no Arabic voice: the phone speaks instead (earbuds on the phone). */
    fun speakOnPhone(ctx: Context, text: String) {
        scope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(ctx).connectedNodes.await()
                nodes.forEach { Wearable.getMessageClient(ctx).sendMessage(it.id, PATH_SPEAK, text.toByteArray()).await() }
            }
        }
    }

    /** Data Layer keeps the item and delivers it whenever the phone reconnects. */
    suspend fun sendWorkout(ctx: Context, w: Workout) {
        val req = PutDataMapRequest.create(PATH_WORKOUT + w.id).apply {
            dataMap.putAsset("data", Asset.createFromBytes(WorkoutJson.gzip(w)))
            dataMap.putLong("ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(ctx).putDataItem(req).await()
    }
}

object WatchStore {
    fun save(ctx: Context, w: Workout) {
        val dir = File(ctx.filesDir, "workouts").apply { mkdirs() }
        File(dir, "${w.id}.json").writeText(WorkoutJson.encode(w))
        // keep only the last 20 on the watch
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
    }
}
