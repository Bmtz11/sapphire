package com.bachir.khutwa.mobile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings as AndroidSettings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bachir.khutwa.shared.tr
import kotlinx.coroutines.delay

/** Needed so the app may read what's playing (Spotify, YouTube Music, Samsung Music…). */
class MediaListener : NotificationListenerService()

data class NowPlaying(val title: String, val artist: String, val app: String, val playing: Boolean, val art: Bitmap?)

object Music {
    val APPS = listOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.sec.android.app.music" to "Samsung Music",
    )

    fun hasAccess(ctx: Context) = runCatching {
        AndroidSettings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true
    }.getOrDefault(false)

    private fun controller(ctx: Context): MediaController? = runCatching {
        if (!hasAccess(ctx)) return null
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val list = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
    }.getOrNull()

    fun now(ctx: Context): NowPlaying? {
        val c = controller(ctx) ?: return null
        val md = c.metadata ?: return null
        val app = APPS.firstOrNull { it.first == c.packageName }?.second ?: c.packageName.substringAfterLast('.')
        return NowPlaying(
            md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            app, c.playbackState?.state == PlaybackState.STATE_PLAYING,
            md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
        )
    }

    /** Works without any permission: sends a media key to whatever is playing. */
    fun key(ctx: Context, cmd: String) {
        val c = controller(ctx)
        if (c != null) {
            when (cmd) {
                "play", "playpause" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
                "next" -> c.transportControls.skipToNext()
                "prev" -> c.transportControls.skipToPrevious()
            }
            return
        }
        val code = when (cmd) { "next" -> KeyEvent.KEYCODE_MEDIA_NEXT; "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE }
        val am = ctx.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun open(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
    }

    fun installed(ctx: Context) = APPS.filter { ctx.packageManager.getLaunchIntentForPackage(it.first) != null }

    fun askAccess(ctx: Context) = ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Now-playing card with controls; shows app shortcuts when nothing is playing. */
@Composable
fun MusicCard(modifier: Modifier = Modifier, dark: Boolean = true) {
    val ctx = LocalContext.current
    var np by remember { mutableStateOf<NowPlaying?>(null) }
    var access by remember { mutableStateOf(Music.hasAccess(ctx)) }
    LaunchedEffect(Unit) { while (true) { access = Music.hasAccess(ctx); np = Music.now(ctx); delay(1500) } }
    val fg = if (dark) Color.White else LocalPalette.current.text
    val sub = if (dark) Color(0xFFA3A6AD) else LocalPalette.current.muted
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(if (dark) Color(0xD1141518) else LocalPalette.current.card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1DB954).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                val art = np?.art
                if (art != null) Image(art.asImageBitmap(), null, Modifier.size(46.dp), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.MusicNote, null, tint = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(np?.title?.takeIf { it.isNotBlank() } ?: tr("الموسيقى"), style = txt(14, fg, FontWeight.SemiBold), maxLines = 1)
                Text(np?.let { "${it.artist} • ${it.app}" } ?: if (access) tr("شغّل أغنية من تطبيقك") else tr("التحكم شغال • اسمح بعرض اسم الأغنية"), style = txt(11, sub), maxLines = 1)
            }
            CtrlBtn(Icons.Filled.SkipPrevious, fg) { Music.key(ctx, "prev") }
            Box(Modifier.size(42.dp).clip(CircleShape).background(fg).bounceClick { Music.key(ctx, "play") }, contentAlignment = Alignment.Center) {
                Icon(if (np?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = if (dark) Color(0xFF0B0B0C) else Color.White)
            }
            CtrlBtn(Icons.Filled.SkipNext, fg) { Music.key(ctx, "next") }
        }
        if (np == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Music.installed(ctx).forEach { (pkg, name) ->
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(fg.copy(alpha = 0.1f)).bounceClick { Music.open(ctx, pkg) }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(name, style = txt(12, fg, FontWeight.Medium))
                }
            }
            if (!access) Box(Modifier.clip(RoundedCornerShape(14.dp)).background(WalkBlue.copy(alpha = 0.25f)).bounceClick { Music.askAccess(ctx) }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(tr("السماح"), style = txt(12, fg, FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun CtrlBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
}
