package com.nightread.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppTtsManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText

    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private var currentVoice: Voice? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale("ru")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isPlaying.value = false
                }

                override fun onError(utteranceId: String?) {
                    _isPlaying.value = false
                }
            })
        } else {
            isInitialized = false
        }
    }

    fun speak(text: String) {
        if (!isInitialized || text.isBlank()) return
        stop()
        _currentText.value = text
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
        currentVoice?.let { voice ->
            try {
                tts?.voice = voice
            } catch (e: Exception) {
                // ignore
            }
        }
        _isPlaying.value = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_APP_TTS")
    }

    fun pause() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
            _isPlaying.value = false
        }
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
        _currentText.value = ""
    }

    fun setSpeed(rate: Float) {
        speechRate = rate
        if (_isPlaying.value && _currentText.value.isNotEmpty()) {
            speak(_currentText.value)
        }
    }

    fun setPitch(pitch: Float) {
        speechPitch = pitch
        if (_isPlaying.value && _currentText.value.isNotEmpty()) {
            speak(_currentText.value)
        }
    }

    fun setVoice(voice: Voice?) {
        currentVoice = voice
        voice?.let { v ->
            try {
                tts?.voice = v
            } catch (e: Exception) {
                // ignore
            }
        }
        if (_isPlaying.value && _currentText.value.isNotEmpty()) {
            speak(_currentText.value)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isPlaying.value = false
    }
}
