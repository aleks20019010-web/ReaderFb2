package org.readium.r2.navigator.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidTtsEngine(
    private val context: Context,
    private val onInitListener: ((Int) -> Unit)? = null
) : TtsEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _state = MutableStateFlow<TtsEngine.State>(TtsEngine.State.Stopped)
    override val state: StateFlow<TtsEngine.State> = _state.asStateFlow()

    private val _voices = mutableListOf<TtsEngine.Voice>()
    override val voices: List<TtsEngine.Voice>
        get() = _voices

    private var currentUtterance: Utterance? = null
    private var currentConfig = TtsEngine.Configuration()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale("ru")
            updateVoices()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    currentUtterance?.let {
                        _state.value = TtsEngine.State.Playing(it, null)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    _state.value = TtsEngine.State.Stopped
                }

                override fun onError(utteranceId: String?) {
                    _state.value = TtsEngine.State.Stopped
                }
            })
        }
        onInitListener?.invoke(status)
    }

    private fun updateVoices() {
        try {
            tts?.voices?.forEach { v ->
                _voices.add(TtsEngine.Voice(v.name, v.name, v.locale ?: Locale.getDefault()))
            }
        } catch (e: Exception) {
            // fallback
        }
    }

    override fun load(configuration: TtsEngine.Configuration) {
        currentConfig = configuration
        configuration.defaultLanguage?.let { tts?.language = it }
        configuration.voice?.let { v ->
            tts?.voices?.find { it.name == v.id }?.let { tts?.voice = it }
        }
        tts?.setSpeechRate(configuration.rate)
        tts?.setPitch(configuration.pitch)
    }

    override fun setRate(rate: Float) {
        currentConfig = currentConfig.copy(rate = rate)
        tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        currentConfig = currentConfig.copy(pitch = pitch)
        tts?.setPitch(pitch)
    }

    override fun setVoice(voice: TtsEngine.Voice) {
        currentConfig = currentConfig.copy(voice = voice)
        tts?.voices?.find { it.name == voice.id }?.let { tts?.voice = it }
    }

    override fun play(utterance: Utterance) {
        if (!isInitialized) return
        currentUtterance = utterance
        val params = HashMap<String, String>().apply {
            put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utterance.id)
        }
        tts?.speak(utterance.text, TextToSpeech.QUEUE_FLUSH, params)
    }

    override fun pause() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
            currentUtterance?.let {
                _state.value = TtsEngine.State.Paused(it)
            }
        }
    }

    override fun resume() {
        currentUtterance?.let { play(it) }
    }

    override fun stop() {
        tts?.stop()
        _state.value = TtsEngine.State.Stopped
        currentUtterance = null
    }

    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _state.value = TtsEngine.State.Stopped
    }
}
