package org.readium.r2.navigator.tts

import org.readium.r2.shared.publication.Locator

data class Utterance(
    val id: String,
    val text: String,
    val locator: Locator,
    val duration: Long? = null
)
