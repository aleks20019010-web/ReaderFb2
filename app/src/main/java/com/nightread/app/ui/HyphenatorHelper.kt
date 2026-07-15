package com.nightread.app.ui

object HyphenatorHelper {
    private val vowels = setOf('а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я', 'А', 'Е', 'Ё', 'И', 'О', 'У', 'Ы', 'Э', 'Ю', 'Я')
    private val consonants = setOf('б', 'в', 'г', 'д', 'ж', 'з', 'й', 'к', 'л', 'м', 'н', 'п', 'р', 'с', 'т', 'ф', 'х', 'ц', 'ч', 'ш', 'щ',
                                   'Б', 'В', 'Г', 'Д', 'Ж', 'З', 'Й', 'К', 'Л', 'М', 'Н', 'П', 'Р', 'С', 'Т', 'Ф', 'Х', 'Ц', 'Ч', 'Ш', 'Щ')
    private val signs = setOf('ь', 'ъ', 'Ь', 'Ъ')

    private fun isVowel(c: Char) = vowels.contains(c)
    private fun isConsonant(c: Char) = consonants.contains(c)
    private fun isSign(c: Char) = signs.contains(c)

    fun hyphenate(text: String): String {
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
                result.append(hyphenateWord(word))
                continue // i is already at the next character
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }

    private fun hyphenateWord(word: String): String {
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
                
                // V-CV
                if (isVowel(c2) && isConsonant(c3) && isVowel(c4)) {
                    canBreak = true
                }
                // VC-CV
                else if (isVowel(c1) && isConsonant(c2) && isConsonant(c3) && isVowel(c4)) {
                    if (c3 != 'й' && c3 != 'Й' && !isSign(c4)) {
                        canBreak = true
                    }
                }
                // VC-CCV
                else if (isVowel(c1) && isConsonant(c2) && isConsonant(c3) && isConsonant(c4) && (i+3 < word.length && isVowel(word[i+3]))) {
                    if (c3 != 'й' && c3 != 'Й') {
                        canBreak = true
                    }
                }
                // Break after 'й'
                else if ((c2 == 'й' || c2 == 'Й') && isConsonant(c3)) {
                    canBreak = true
                }
                // Break after sign
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
