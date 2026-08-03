package org.readium.r2.navigator.tts

import org.readium.r2.shared.publication.Locator

interface ContentTokenizer {
    fun tokenize(text: String, locator: Locator): List<Utterance>
}

class DefaultContentTokenizer : ContentTokenizer {
    override fun tokenize(text: String, locator: Locator): List<Utterance> {
        if (text.isBlank()) return emptyList()
        val sentences = text.split(Regex("(?<=[.!?\\n])\\s+"))
        var charIndex = 0
        return sentences.filter { it.isNotBlank() }.mapIndexed { index, sentence ->
            val start = charIndex
            val end = start + sentence.length
            charIndex = end + 1
            val sentenceLocator = locator.copy(
                text = Locator.Text(
                    highlight = sentence,
                    before = text.substring(0, maxOf(0, start)),
                    after = text.substring(minOf(text.length, end))
                )
            )
            Utterance(
                id = "utterance_$index",
                text = sentence,
                locator = sentenceLocator
            )
        }
    }
}
