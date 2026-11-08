package com.bachir.khutwa.shared

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Coach voice. Prefers Google's TTS engine and its most natural (highest quality, neural/network) voice
 * for the language, a touch slower and lower than default so it sounds calm rather than robotic.
 * Ducks music while speaking. If no voice exists for the language, [fallback] is called (watch → phone).
 */
class TtsSpeaker(
    context: Context,
    private val english: Boolean = false,
    private val voiceName: String? = null,
) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).build()

    private val tts: TextToSpeech = if (hasGoogle(app)) TextToSpeech(app, this, GOOGLE) else TextToSpeech(app, this)
    private var ready = false
    private val pending = mutableListOf<String>()
    private var counter = 0

    var available = false; private set
    @Deprecated("use available") val arabicAvailable get() = available
    var fallback: ((String) -> Unit)? = null
    var rate: Float = 0.95f
        set(v) { field = v; if (ready) tts.setSpeechRate(v) }
    var pitch: Float = 0.95f
        set(v) { field = v; if (ready) tts.setPitch(v) }

    private val lang get() = if (english) "en" else "ar"

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            var r = tts.setLanguage(if (english) Locale.US else Locale("ar", "SA"))
            if (r < 0) r = tts.setLanguage(Locale(lang))
            available = r >= 0
            if (available) pickVoice()
            tts.setAudioAttributes(attrs)
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (!tts.isSpeaking) audio.abandonAudioFocusRequest(focus) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { audio.abandonAudioFocusRequest(focus) }
            })
        }
        ready = true
        pending.forEach(::speak)
        pending.clear()
    }

    /** All voices for the current language, best first. */
    fun voices(): List<Voice> = runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language == lang && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.latency }.thenByDescending { it.isNetworkConnectionRequired })
    }.getOrDefault(emptyList())

    private fun pickVoice() {
        val list = voices()
        val chosen = voiceName?.let { n -> list.firstOrNull { it.name == n } } ?: list.firstOrNull()
        chosen?.let { tts.voice = it }
    }

    fun useVoice(name: String) { voices().firstOrNull { it.name == name }?.let { tts.voice = it } }

    fun speak(text: String) {
        if (!ready) { pending += text; return }
        if (!available) { fallback?.invoke(text); return }
        audio.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "k${counter++}")
    }

    fun stop() = tts.stop()

    fun shutdown() {
        tts.stop(); tts.shutdown()
        audio.abandonAudioFocusRequest(focus)
    }

    companion object {
        const val GOOGLE = "com.google.android.tts"
        fun hasGoogle(ctx: Context) = runCatching { ctx.packageManager.getPackageInfo(GOOGLE, 0); true }.getOrDefault(false)
    }
}
