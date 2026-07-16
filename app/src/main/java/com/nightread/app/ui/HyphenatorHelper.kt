package com.nightread.app.ui

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import com.nightread.app.ui.customlayout.PageSplitter

object HyphenatorHelper {
    private var texHyphenator: TexHyphenator? = null

    class TexHyphenator(patterns: List<String>) {
        private class Pattern(
            val chars: String,
            val levels: IntArray,
            val startsWithDot: Boolean,
            val endsWithDot: Boolean
        )

        private val patternList = ArrayList<Pattern>()

        init {
            for (line in patterns) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("%") || trimmed.startsWith("\\")) continue
                
                var cleanChars = StringBuilder()
                val levels = ArrayList<Int>()
                var startsWithDot = false
                var endsWithDot = false
                
                var s = trimmed
                if (s.startsWith(".")) {
                    startsWithDot = true
                    s = s.substring(1)
                }
                if (s.endsWith(".")) {
                    endsWithDot = true
                    s = s.substring(0, s.length - 1)
                }
                
                var i = 0
                levels.add(0) // Before first char
                while (i < s.length) {
                    val c = s[i]
                    if (c.isDigit()) {
                        levels[levels.size - 1] = c - '0'
                    } else {
                        cleanChars.append(c)
                        levels.add(0)
                    }
                    i++
                }
                
                patternList.add(Pattern(cleanChars.toString(), levels.toIntArray(), startsWithDot, endsWithDot))
            }
        }

        fun hyphenateWord(word: String, minLeft: Int = 2, minRight: Int = 2): String {
            if (word.length < minLeft + minRight) return word
            
            val lowercaseWord = word.lowercase()
            val levels = IntArray(word.length + 1)
            val dotted = ".$lowercaseWord."
            
            for (pattern in patternList) {
                var startPos = 0
                while (true) {
                    val idx = dotted.indexOf(pattern.chars, startPos)
                    if (idx == -1) break
                    
                    val isAtStart = idx == 0
                    val isAtEnd = idx + pattern.chars.length == dotted.length
                    
                    if (pattern.startsWithDot && !isAtStart) {
                        startPos = idx + 1
                        continue
                    }
                    if (pattern.endsWithDot && !isAtEnd) {
                        startPos = idx + 1
                        continue
                    }
                    
                    for (i in pattern.levels.indices) {
                        val wordLevelPos = idx - 1 + i
                        if (wordLevelPos in levels.indices) {
                            levels[wordLevelPos] = maxOf(levels[wordLevelPos], pattern.levels[i])
                        }
                    }
                    
                    startPos = idx + 1
                }
            }
            
            val result = StringBuilder()
            for (i in word.indices) {
                result.append(word[i])
                if (i >= minLeft - 1 && i < word.length - minRight) {
                    val level = levels[i + 1]
                    if (level % 2 != 0) {
                        result.append('\u00AD') // Soft hyphen
                    }
                }
            }
            return result.toString()
        }
    }

    private val vowels = setOf('а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я', 'А', 'Е', 'Ё', 'И', 'О', 'У', 'Ы', 'Э', 'Ю', 'Я')
    private val consonants = setOf('б', 'в', 'г', 'д', 'ж', 'з', 'й', 'к', 'л', 'м', 'н', 'п', 'р', 'с', 'т', 'ф', 'х', 'ц', 'ч', 'ш', 'щ',
                                   'Б', 'В', 'Г', 'Д', 'Ж', 'З', 'Й', 'К', 'Л', 'М', 'Н', 'П', 'Р', 'С', 'Т', 'Ф', 'Х', 'Ц', 'Ч', 'Ш', 'Щ')
    private val signs = setOf('ь', 'ъ', 'Ь', 'Ъ')

    private fun isVowel(c: Char) = vowels.contains(c)
    private fun isConsonant(c: Char) = consonants.contains(c)
    private fun isSign(c: Char) = signs.contains(c)

    fun init(context: Context) {
        if (texHyphenator != null) return
        try {
            val patterns = ArrayList<String>()
            val inputStream = context.resources.openRawResource(com.nightread.app.R.raw.hyph_ru_ru)
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    patterns.add(line!!)
                }
            }
            texHyphenator = TexHyphenator(patterns)
            android.util.Log.d("HyphenatorHelper", "Successfully loaded TeX hyphenation patterns!")
        } catch (e: Exception) {
            android.util.Log.e("HyphenatorHelper", "Failed to load TeX hyphenation patterns", e)
        }
    }

    fun hyphenate(text: String, context: Context? = null, paint: android.text.TextPaint? = null): String {
        if (context != null && texHyphenator == null) {
            init(context)
        }
        
        val hyphenator = texHyphenator ?: return fallbackHyphenate(text)
        
        val minLeft = paint?.let { PageSplitter.minLeftHyphenLimitMap[it] } ?: 2
        val minRight = paint?.let { PageSplitter.minRightHyphenLimitMap[it] } ?: 2
        
        val result = StringBuilder(text.length + text.length / 5)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isLetter()) {
                val start = i
                while (i < text.length && text[i].isLetter()) {
                    i++
                }
                val word = text.substring(start, i)
                result.append(hyphenator.hyphenateWord(word, minLeft, minRight))
                continue
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }

    private fun fallbackHyphenate(text: String): String {
        val result = StringBuilder(text.length + text.length / 5)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c.isLetter()) {
                val start = i
                while (i < text.length && text[i].isLetter()) {
                    i++
                }
                val word = text.substring(start, i)
                result.append(fallbackHyphenateWord(word))
                continue
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }

    private fun fallbackHyphenateWord(word: String): String {
        if (word.length < 4) return word

        val result = StringBuilder()
        var i = 0
        while (i < word.length) {
            result.append(word[i])
            
            if (i >= 1 && i < word.length - 2) {
                val c1 = word[i-1]
                val c2 = word[i]
                val c3 = word[i+1]
                val c4 = word[i+2]
                
                var canBreak = false
                
                if (isVowel(c2) && isConsonant(c3) && isVowel(c4)) {
                    canBreak = true
                }
                else if (isVowel(c1) && isConsonant(c2) && isConsonant(c3) && isVowel(c4)) {
                    if (c3 != 'й' && c3 != 'Й' && !isSign(c4)) {
                        canBreak = true
                    }
                }
                else if (isVowel(c1) && isConsonant(c2) && isConsonant(c3) && isConsonant(c4) && (i+3 < word.length && isVowel(word[i+3]))) {
                    if (c3 != 'й' && c3 != 'Й') {
                        canBreak = true
                    }
                }
                else if ((c2 == 'й' || c2 == 'Й') && isConsonant(c3)) {
                    canBreak = true
                }
                else if (isSign(c2) && isConsonant(c3)) {
                    canBreak = true
                }
                
                if (canBreak) {
                    if (hasVowel(word.substring(0, i + 1)) && hasVowel(word.substring(i + 1))) {
                        result.append('\u00AD')
                    }
                }
            }
            i++
        }
        return result.toString()
    }
    
    private fun hasVowel(s: String): Boolean {
        for (i in s.indices) {
            if (isVowel(s[i])) return true
        }
        return false
    }
}
