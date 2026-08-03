package org.readium.r2.navigator.tts

import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

interface TtsNavigator : Navigator {
    val ttsState: StateFlow<TtsEngine.State>

    fun play()
    fun pause()
    fun resume()
    fun stop()
    fun previous()
    fun next()
    fun go(utterance: Utterance)
}
