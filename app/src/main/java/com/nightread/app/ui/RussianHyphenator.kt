package com.nightread.app.ui

import android.util.Log

object RussianHyphenator {
    private val VOWELS = setOf(
        'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я',
        'А', 'Е', 'Ё', 'И', 'О', 'У', 'Ы', 'Э', 'Ю', 'Я'
    )

    /**
     * Inserts soft hyphens (\u00AD) into Russian words inside the given text.
     */
    fun hyphenate(text: String): String {
        val sb = java.lang.StringBuilder()
        var i = 0
        var wordCount = 0
        var totalHyphens = 0
        val sampleWords = mutableListOf<String>()

        while (i < text.length) {
            if (text[i].isLetterOrDigit()) {
                val start = i
                while (i < text.length && text[i].isLetterOrDigit()) {
                    i++
                }
                val word = text.substring(start, i)
                val hyphenated = hyphenateWord(word)
                if (hyphenated != word) {
                    val hyphenCount = hyphenated.count { it == '\u00AD' }
                    totalHyphens += hyphenCount
                    if (sampleWords.size < 5) {
                        sampleWords.add("$word -> ${hyphenated.replace("\u00AD", "[SHY]")}")
                    }
                }
                wordCount++
                sb.append(hyphenated)
            } else {
                sb.append(text[i])
                i++
            }
        }
        
        Log.d("RussianHyphenator", "hyphenate: processed $wordCount words, inserted $totalHyphens soft hyphens.")
        if (sampleWords.isNotEmpty()) {
            Log.d("RussianHyphenator", "Sample processed words: ${sampleWords.joinToString(", ")}")
        }
        return sb.toString()
    }

    private fun hyphenateWord(word: String): String {
        // Rule 1: Do not hyphenate words shorter than 4 characters
        if (word.length < 4) return word

        // Rule 2: Do not hyphenate abbreviations (all letters uppercase)
        if (word.all { it.isUpperCase() }) return word

        // Rule 3: Do not hyphenate words with digits
        if (word.any { it.isDigit() }) return word

        // Find indices of all Russian/Cyrillic vowels in the word
        val vowelIndices = mutableListOf<Int>()
        for (idx in word.indices) {
            if (word[idx] in VOWELS) {
                vowelIndices.add(idx)
            }
        }

        // If there is only one vowel or no vowels, no hyphenation is possible
        if (vowelIndices.size <= 1) return word

        val result = java.lang.StringBuilder()
        var lastCut = 0
        
        // Insert soft hyphen (\u00AD) after each vowel except the last one
        for (vIdx in 0 until vowelIndices.size - 1) {
            val vowelPos = vowelIndices[vIdx]

            // Apply rule: Cannot leave a single letter at the beginning (hyphen index >= 1)
            // and cannot move a single letter to the next line (suffix length >= 2)
            if (vowelPos in 1..(word.length - 3)) {
                result.append(word.substring(lastCut, vowelPos + 1))
                result.append('\u00AD') // Soft hyphen
                lastCut = vowelPos + 1
            }
        }
        result.append(word.substring(lastCut))
        return result.toString()
    }
}

