package org.readium.r2.navigator.tts

import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

interface TtsEngine {
    data class Voice(
        val id: String,
        val name: String,
        val language: Locale
    )

    data class Configuration(
        val defaultLanguage: Locale? = null,
        val voice: Voice? = null,
        val rate: Float = 1.0f,
        val pitch: Float = 1.0f
    )

    sealed interface State {
        object Stopped : State
        data class Playing(val utterance: Utterance, val range: IntRange?) : State
        data class Paused(val utterance: Utterance) : State
    }

    val state: StateFlow<State>
    val voices: List<Voice>

    fun load(configuration: Configuration)
    fun setRate(rate: Float)
    fun setPitch(pitch: Float)
    fun setVoice(voice: Voice)
    fun play(utterance: Utterance)
    fun pause()
    fun resume()
    fun stop()
    fun close()
}
