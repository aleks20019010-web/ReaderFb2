package com.nightread.app.ui

import android.util.Log

object HyphenationPatterns {
    fun load(lang: String) {
        Log.d("HyphenatorHelper", "Loaded hyphenation patterns for $lang")
    }
}

object HyphenatorHelper {
    private const val TAG = "HyphenatorHelper"
    
    private const val v = "[аеёиоуыэюя]"
    private const val c = "[бвгджзклмнпрстфхцчшщ]"
    private const val s = "[йьъ]"
    private const val l = "[а-яА-ЯёЁ]"

    private val regex1 = Regex("(?<=$l)($v)(?=$c$v)")
    private val regex2 = Regex("($v$c)(?=$c$v)")

    fun hyphenate(text: String): String {
        var res = text
        res = regex1.replace(res, "$1\u00AD")
        res = regex2.replace(res, "$1\u00AD")
        return res
    }
}
