package com.nightread.app.ui

import android.util.Log

object HyphenationPatterns {
    fun load(lang: String) {
        Log.d("HyphenatorHelper", "Loaded hyphenation patterns for $lang")
    }
}

object HyphenatorHelper {
    private const val TAG = "HyphenatorHelper"

    private const val v = "[аеёиоуыэюяАЕЁИОУЫЭЮЯ]"
    private const val c = "[бвгджзклмнпрстфхцчшщБВГДЖЗКЛМНПРСТФХЦЧШЩ]"
    private const val s = "[йьъЙЬЪ]"

    private val regex1 = Regex("($v)($c$v)")
    private val regex2 = Regex("($v$c)($c$v)")
    private val regex3 = Regex("($v$c)($c$c$v)")
    private val regex4 = Regex("($v$s)($c$v)")

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
        var oldRes = ""
        while (res != oldRes) {
            oldRes = res
            res = regex1.replace(res, "$1\u00AD$2")
            res = regex2.replace(res, "$1\u00AD$2")
            res = regex3.replace(res, "$1\u00AD$2")
            res = regex4.replace(res, "$1\u00AD$2")
        }
        
        // Ensure no hyphen at the very beginning or after 1 character
        if (res.startsWith("­")) res = res.substring(1)
        if (res.length > 1 && res[1] == '­') res = res[0].toString() + res.substring(2)
        
        // Ensure no hyphen at the very end or before 1 character
        if (res.endsWith("­")) res = res.substring(0, res.length - 1)
        if (res.length > 2 && res[res.length - 2] == '­') res = res.substring(0, res.length - 2) + res.substring(res.length - 1)

        return res
    }
}
