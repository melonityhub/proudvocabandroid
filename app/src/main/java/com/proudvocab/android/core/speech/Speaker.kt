package com.proudvocab.android.core.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Thin wrapper around Android's text-to-speech, used for the "pronounce"
 * buttons and for the dictation game.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Pair<String, Locale>>()

    init {
        runCatching { tts = TextToSpeech(context.applicationContext, this) }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                @Deprecated("legacy")
                override fun onError(utteranceId: String?) {}
            })
            while (pending.isNotEmpty()) {
                val (text, locale) = pending.removeFirst()
                speakNow(text, locale)
            }
        } else {
            // No engine: drop the backlog instead of queueing forever.
            pending.clear()
        }
    }

    fun speak(text: String, languageCode: String) {
        val locale = runCatching { Locale.forLanguageTag(languageCode) }.getOrNull() ?: Locale.US
        if (!ready) {
            // Keep only the most recent asks; the queue is flushed once the
            // engine finishes initialising.
            if (pending.size >= 8) pending.removeFirst()
            pending.addLast(text to locale)
            return
        }
        speakNow(text, locale)
    }

    private fun speakNow(text: String, locale: Locale) {
        val engine = tts ?: return
        runCatching {
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setLanguage(Locale.US)
            }
            engine.setSpeechRate(0.92f)
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text.hashCode().toString())
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, text.hashCode().toString())
        }
    }

    fun stop() = runCatching { tts?.stop() }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
    }
}
