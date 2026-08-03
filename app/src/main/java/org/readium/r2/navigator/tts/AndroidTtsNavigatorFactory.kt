package org.readium.r2.navigator.tts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

object AndroidTtsNavigatorFactory {
    fun create(
        context: Context,
        publication: Publication,
        initialPreferences: AndroidTtsPreferences = AndroidTtsPreferences()
    ): TtsNavigator {
        val engine = AndroidTtsEngine(context)
        val synthesizer = PublicationSpeechSynthesizer(publication, engine)
        val dummyLocator = publication.locatorFromLink(publication.readingOrder.first())
            ?: error("No reading order available")
        return object : TtsNavigator {
            override val ttsState: StateFlow<TtsEngine.State> = engine.state
            override fun play() {}
            override fun pause() { engine.pause() }
            override fun resume() {}
            override fun stop() { engine.stop() }
            override fun previous() {}
            override fun next() {}
            override fun go(utterance: Utterance) {}
            override fun go(link: Link, animated: Boolean): Boolean = false
            override fun go(locator: Locator, animated: Boolean): Boolean = false
            override val currentLocator: StateFlow<Locator> = MutableStateFlow(dummyLocator)
            fun close() {
                synthesizer.close()
                engine.close()
            }
        }
    }
}
