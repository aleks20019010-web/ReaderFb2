package com.nightread.app.ui

import android.graphics.Typeface
import android.os.Build
import android.util.Log

object FontUtils {
    private const val TAG = "FontUtils"

    fun createTypeface(family: String, numericWeight: Int): Typeface {
        val baseTypeface = when (family.lowercase()) {
            "roboto" -> Typeface.SANS_SERIF
            "times new roman", "georgia", "merriweather" -> {
                // Для serif лучше явно брать из Typeface, а не хардкодить строку
                Typeface.create("serif", Typeface.NORMAL)
            }
            "opendyslexic" -> {
                // OpenDyslexic часто подключают как кастомный шрифт; если его нет — fallback на sans-serif
                Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            }
            "monospace" -> Typeface.MONOSPACE
            else -> {
                Log.d(TAG, "Unknown font family '$family', fallback to DEFAULT")
                Typeface.DEFAULT
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // API 28+ поддерживает create(typeface, weight, italic)
            Typeface.create(baseTypeface, numericWeight, false)
        } else {
            // На старых версиях Android нет поддержки numeric weight: мапим на BOLD/NORMAL
            val style = if (numericWeight >= 600) Typeface.BOLD else Typeface.NORMAL
            Log.d(TAG, "Legacy device: mapping weight $numericWeight to style ${if (style == Typeface.BOLD) "BOLD" else "NORMAL"}")
            Typeface.create(baseTypeface, style)
        }
    }
}
