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

    @Volatile private var nodes: List<String>? = null
    @Volatile private var nodesAt = 0L

    /** Live mirror of the running workout to the phone (cheap MessageClient, node list cached 30 s). */
    fun sendLive(ctx: Context, body: String) {
        scope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val ids = nodes?.takeIf { now - nodesAt < 30_000 }
                    ?: Wearable.getNodeClient(ctx).connectedNodes.await().map { it.id }.also { nodes = it; nodesAt = now }
                ids.forEach { Wearable.getMessageClient(ctx).sendMessage(it, "/khutwa/live", body.toByteArray()).await() }
            }
        }
    }

    /** Media control on the phone's player (play/pause/next/prev). */
    fun media(ctx: Context, key: String) {
        scope.launch {
            runCatching {
                Wearable.getNodeClient(ctx).connectedNodes.await().forEach {
                    Wearable.getMessageClient(ctx).sendMessage(it.id, "/khutwa/media", key.toByteArray()).await()
                }
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


/** Commands from the phone while the workout runs on the watch. */
class WatchCmdListener : com.google.android.gms.wearable.WearableListenerService() {
    override fun onMessageReceived(event: com.google.android.gms.wearable.MessageEvent) {
        if (event.path != "/khutwa/cmd") return
        when (event.data.decodeToString()) {
            "pause" -> ExerciseService.pause(this)
            "resume" -> ExerciseService.resume(this)
            "stop" -> ExerciseService.stop(this)
            "speak" -> ExerciseService.speakStatus(this)
        }
    }
}
