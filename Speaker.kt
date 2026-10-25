package com.mecnun.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Sesli cevap (Android TTS). Konuşurken mikrofon dinlemesi duraklatılır. */
class Speaker(context: Context) {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayList<String>()
    private val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focus: AudioFocusRequest? = null

    @Volatile var speaking = false
        private set
    @Volatile private var queued = 0

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts
                if (t != null) {
                    t.language = Locale("tr", "TR")
                    t.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) = finished()
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = finished()
                    })
                    ready = true
                    synchronized(pending) {
                        pending.forEach { speakNow(it) }
                        pending.clear()
                    }
                }
            }
        }
    }

    private fun finished() {
        synchronized(this) {
            queued = (queued - 1).coerceAtLeast(0)
            if (queued == 0) {
                speaking = false
                focus?.let { am.abandonAudioFocusRequest(it) }
                focus = null
            }
        }
    }

    private fun speakNow(text: String) {
        val t = tts ?: return
        synchronized(this) {
            if (queued == 0) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    ).build()
                am.requestAudioFocus(req)
                focus = req
            }
            queued++
            speaking = true
        }
        t.setSpeechRate(Prefs.speechRate)
        t.setPitch(Prefs.pitch)
        val r = t.speak(text, TextToSpeech.QUEUE_ADD, null, "m" + System.nanoTime())
        if (r != TextToSpeech.SUCCESS) finished()
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            synchronized(pending) { pending.add(text) }
            speaking = true
            return
        }
        speakNow(text)
    }

    fun stop() {
        tts?.stop()
        synchronized(this) {
            queued = 0
            speaking = false
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
