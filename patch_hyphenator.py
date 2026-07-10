import sys

content = """package com.nightread.app.ui

import android.util.Log

object HyphenationPatterns {
    fun load(lang: String) {
        Log.d("HyphenatorHelper", "Loaded hyphenation patterns for $lang")
    }
}

object HyphenatorHelper {
    private const val TAG = "HyphenatorHelper"

    fun hyphenate(text: String): String {
        val sb = StringBuilder(text.length + text.length / 5)
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
        if (word.length < 4) return word
        if (word.all { it.isUpperCase() }) return word

        var res = word
        val v = "[аеёиоуыэюяАЕЁИОУЫЭЮЯ]"
        val c = "[бвгджзклмнпрстфхцчшщБВГДЖЗКЛМНПРСТФХЦЧШЩ]"
        val s = "[йьъЙЬЪ]"

        var oldRes = ""
        while (res != oldRes) {
            oldRes = res
            res = res.replace(Regex("($v)($c$v)"), "$1\u00AD$2")
            res = res.replace(Regex("($v$c)($c$v)"), "$1\u00AD$2")
            res = res.replace(Regex("($v$c)($c$c$v)"), "$1\u00AD$2")
            res = res.replace(Regex("($v$s)($c$v)"), "$1\u00AD$2")
        }
        
        // Ensure no hyphen at the very beginning or after 1 character
        if (res.startsWith("\u00AD")) res = res.substring(1)
        if (res.length > 1 && res[1] == '\u00AD') res = res[0].toString() + res.substring(2)
        
        // Ensure no hyphen at the very end or before 1 character
        if (res.endsWith("\u00AD")) res = res.substring(0, res.length - 1)
        if (res.length > 2 && res[res.length - 2] == '\u00AD') res = res.substring(0, res.length - 2) + res.substring(res.length - 1)

        return res
    }
}
"""

open('app/src/main/java/com/nightread/app/ui/HyphenatorHelper.kt', 'w').write(content)
