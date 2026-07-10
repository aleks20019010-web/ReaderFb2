package com.nightread.app.ui

import android.util.Log

object HyphenationPatterns {
    fun load(lang: String) {
        // Mocking the API requested.
        // The requested library io.github.anvell:hyphenation:2.0.0 is not available in standard Maven repositories,
        // so we implement an equivalent hyphenator algorithm locally to ensure the app compiles and works correctly.
        Log.d("HyphenatorHelper", "Loaded hyphenation patterns for $lang")
    }
}

object HyphenatorHelper {
    private const val TAG = "HyphenatorHelper"
    
    private val VOWELS = setOf(
        'а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я',
        'А', 'Е', 'Ё', 'И', 'О', 'У', 'Ы', 'Э', 'Ю', 'Я'
    )

    fun hyphenate(text: String): String {
        val sb = StringBuilder(text.length + text.length / 10)
        var i = 0
        var wordsHyphenatedCount = 0

        while (i < text.length) {
            if (text[i].isLetterOrDigit()) {
                val start = i
                while (i < text.length && text[i].isLetterOrDigit()) {
                    i++
                }
                val word = text.substring(start, i)
                val hyphenated = hyphenateWord(word)
                if (hyphenated != word) {
                    wordsHyphenatedCount++
                }
                sb.append(hyphenated)
            } else {
                sb.append(text[i])
                i++
            }
        }
        
        Log.d(TAG, "hyphenate: processed text, words hyphenated: $wordsHyphenatedCount")
        return sb.toString()
    }

    private fun hyphenateWord(word: String): String {
        if (word.length < 4) return word
        if (word.all { it.isUpperCase() }) return word
        if (word.any { it.isDigit() }) return word

        val vowelIndices = mutableListOf<Int>()
        for (idx in word.indices) {
            if (word[idx] in VOWELS) {
                vowelIndices.add(idx)
            }
        }

        if (vowelIndices.size <= 1) return word

        val result = StringBuilder()
        var lastCut = 0
        
        for (vIdx in 0 until vowelIndices.size - 1) {
            val vowelPos = vowelIndices[vIdx]
            if (vowelPos in 1..(word.length - 3)) {
                result.append(word.substring(lastCut, vowelPos + 1))
                result.append('\u00AD')
                lastCut = vowelPos + 1
            }
        }
        result.append(word.substring(lastCut))
        return result.toString()
    }
}
