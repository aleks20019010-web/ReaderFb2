package org.readium.r2.navigator.tts

import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator

class PublicationSpeechSynthesizer(
    private val publication: Publication,
    private val engine: TtsEngine,
    private val tokenizer: ContentTokenizer = DefaultContentTokenizer()
) {
    val state: StateFlow<TtsEngine.State> = engine.state

    data class Configuration(
        val rate: Float = 1.0f,
        val pitch: Float = 1.0f,
        val voice: TtsEngine.Voice? = null
    )

    fun load(configuration: Configuration) {
        engine.load(
            TtsEngine.Configuration(
                rate = configuration.rate,
                pitch = configuration.pitch,
                voice = configuration.voice
            )
        )
    }

    fun play(text: String, locator: Locator) {
        val utterances = tokenizer.tokenize(text, locator)
        if (utterances.isNotEmpty()) {
            engine.play(utterances.first())
        }
    }

    fun pause() {
        engine.pause()
    }

    fun resume() {
        engine.resume()
    }

    fun stop() {
        engine.stop()
    }

    fun setRate(rate: Float) {
        engine.setRate(rate)
    }

    fun setPitch(pitch: Float) {
        engine.setPitch(pitch)
    }

    fun close() {
        engine.close()
    }
}
