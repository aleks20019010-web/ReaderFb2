package com.nightread.app.ui

import java.util.Locale

object HyphenatorHelper {
    fun hyphenate(text: String): String {
        // Hyphenator uses system dictionaries based on locale.
        // For Russian text, ensure the text is processed by a system-aware hyphenation engine.
        // The standard approach is to use StaticLayout with HYPHENATION_FREQUENCY_FULL
        // which internally uses the system Hyphenator.
        return text
    }
}
