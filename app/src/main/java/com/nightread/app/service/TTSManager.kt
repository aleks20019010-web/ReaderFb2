package com.nightread.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TTSManager(
    context: Context,
    private val onUtteranceStart: (String) -> Unit,
    private val onRangeStart: ((String, Int, Int) -> Unit)? = null
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            var result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTSManager", "Language ${Locale.getDefault()} not supported, trying US")
                result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTSManager", "US locale also not supported")
                }
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("TTSManager", "TTS Started: $utteranceId")
                    utteranceId?.let { onUtteranceStart(it) }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    Log.d("TTSManager", "TTS Range Start: $start, $end")
                    utteranceId?.let { onRangeStart?.invoke(it, start, end) }
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("TTSManager", "TTS Done: $utteranceId")
                }
                override fun onError(utteranceId: String?) {
                    Log.e("TTSManager", "TTS Error: $utteranceId")
                }
            })
            isInitialized = true
        } else {
            Log.e("TTSManager", "TTS initialization failed: $status")
        }
    }

    fun isInitialized(): Boolean = isInitialized

    fun getAvailableVoices(): Set<android.speech.tts.Voice>? {
        return tts?.voices
    }

    fun getAvailableVoiceNames(): List<String> {
        return tts?.voices?.map { it.name }?.toList() ?: emptyList()
    }

    fun setVoiceByName(voiceName: String) {
        tts?.voices?.find { it.name == voiceName }?.let {
            tts?.voice = it
        }
    }

    fun setVoice(voice: android.speech.tts.Voice) {
        tts?.voice = voice
    }

    fun speak(text: String, utteranceId: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun pause() {
        tts?.stop()
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
