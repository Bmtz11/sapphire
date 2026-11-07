package com.bachir.khutwa.shared

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Arabic TTS that ducks music while speaking.
 * If the device has no Arabic voice, [fallback] is called instead (the watch relays to the phone).
 */
class TtsSpeaker(context: Context) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).build()

    private val tts = TextToSpeech(app, this)
    private var ready = false
    private val pending = mutableListOf<String>()
    private var counter = 0

    var arabicAvailable = false; private set
    var fallback: ((String) -> Unit)? = null
    var rate: Float = 1.0f
        set(v) { field = v; if (ready) tts.setSpeechRate(v) }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            var r = tts.setLanguage(Locale("ar", "SA"))
            if (r < 0) r = tts.setLanguage(Locale("ar"))
            arabicAvailable = r >= 0
            tts.setAudioAttributes(attrs)
            tts.setSpeechRate(rate)
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

    fun speak(text: String) {
        if (!ready) { pending += text; return }
        if (!arabicAvailable) { fallback?.invoke(text); return }
        audio.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "k${counter++}")
    }

    fun shutdown() {
        tts.stop(); tts.shutdown()
        audio.abandonAudioFocusRequest(focus)
    }
}
