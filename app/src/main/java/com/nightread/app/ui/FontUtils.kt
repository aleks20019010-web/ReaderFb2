package com.nightread.app.ui

import android.graphics.Typeface
import android.os.Build

object FontUtils {
    fun createTypeface(family: String, numericWeight: Int): Typeface {
        val baseTypeface = when (family) {
            "Roboto" -> Typeface.SANS_SERIF
            "Times New Roman", "Georgia", "Merriweather" -> Typeface.create("serif", Typeface.NORMAL)
            "OpenDyslexic" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "Monospace" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(baseTypeface, numericWeight, false)
        } else {
            val style = if (numericWeight >= 600) Typeface.BOLD else Typeface.NORMAL
            Typeface.create(baseTypeface, style)
        }
    }
}
