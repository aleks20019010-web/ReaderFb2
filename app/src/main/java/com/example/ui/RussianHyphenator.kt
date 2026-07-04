package com.example.ui

object RussianHyphenator {
    private val VOWELS = setOf(
        'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я',
        'А', 'Е', 'Ё', 'И', 'О', 'У', 'Ы', 'Э', 'Ю', 'Я'
    )

    private val SPECIAL = setOf('ь', 'ъ', 'Ь', 'Ъ')

    /**
     * Inserts soft hyphens (\u00AD) into Russian words inside the given text.
     */
    fun hyphenate(text: String): String {
        val sb = java.lang.StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i].isLetter()) {
                val start = i
                while (i < text.length && text[i].isLetter()) {
                    i++
                }
                val word = text.substring(start, i)
                sb.append(hyphenateWord(word))
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun hyphenateWord(word: String): String {
        if (word.length <= 4) return word // don't hyphenate short words

        // Count vowels first using primitive array to avoid boxed ArrayList allocation
        val vowelIndices = IntArray(word.length)
        var vowelCount = 0
        for (idx in word.indices) {
            if (word[idx] in VOWELS) {
                vowelIndices[vowelCount++] = idx
            }
        }

        // If less than 2 vowels, no hyphenation is possible
        if (vowelCount < 2) return word

        val result = java.lang.StringBuilder()
        var lastHyphenIndex = 0

        // We can place hyphens between vowels, but keeping rules in mind
        for (vIdx in 0 until vowelCount - 1) {
            val v1 = vowelIndices[vIdx]
            val v2 = vowelIndices[vIdx + 1]

            // Try to find a good hyphenation point between v1 and v2
            var hyphenPos = -1

            val dist = v2 - v1
            if (dist == 1) {
                // Two vowels side by side: e.g. "иг-ра" or "по-эт". We can hyphenate right between them
                hyphenPos = v1 + 1
            } else if (dist == 2) {
                // One consonant between vowels: e.g. "мо-ло-ко". Hyphen is right after the first vowel
                // unless the consonant is й or followed by special char
                val midChar = word[v1 + 1]
                if (midChar !in SPECIAL && midChar.lowercaseChar() != 'й') {
                    hyphenPos = v1 + 1
                }
            } else {
                // Multiple consonants between vowels: e.g. "сест-ра", "кар-та", "пись-мо"
                // Check for special characters: ь, ъ, й after v1
                val charAfterV1 = word[v1 + 1]
                if (charAfterV1 in SPECIAL || charAfterV1.lowercaseChar() == 'й') {
                    // Hyphenate after ь/ъ/й: e.g. "пись-мо", "май-ка"
                    hyphenPos = v1 + 2
                } else {
                    // Standard split: hyphenate before the last consonant in the cluster
                    // e.g. "сест-ра", "кар-ти-на"
                    hyphenPos = v2 - 1
                }
            }

            // Apply rules: 
            // 1. Cannot leave a single letter at the beginning (hyphenPos > 1)
            // 2. Cannot move a single letter to the next line (hyphenPos < word.length - 1)
            if (hyphenPos in 2..(word.length - 2)) {
                result.append(word.substring(lastHyphenIndex, hyphenPos))
                result.append('\u00AD') // Soft hyphen
                lastHyphenIndex = hyphenPos
            }
        }
        result.append(word.substring(lastHyphenIndex))
        return result.toString()
    }
}
