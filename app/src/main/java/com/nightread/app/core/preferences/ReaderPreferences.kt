package com.nightread.app.core.preferences

import android.content.Context
import android.content.SharedPreferences

class ReaderPreferences(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 18f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value).apply()

    var fontFamily: String
        get() = prefs.getString(KEY_FONT_FAMILY, "Sans-Serif") ?: "Sans-Serif"
        set(value) = prefs.edit().putString(KEY_FONT_FAMILY, value).apply()

    var lineSpacing: Float
        get() = prefs.getFloat(KEY_LINE_SPACING, 1.2f)
        set(value) = prefs.edit().putFloat(KEY_LINE_SPACING, value).apply()

    var readerTheme: Int
        get() = prefs.getInt(KEY_READER_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_READER_THEME, value).apply()

    var pageAnimation: String
        get() = prefs.getString(KEY_PAGE_ANIMATION, "slide") ?: "slide"
        set(value) = prefs.edit().putString(KEY_PAGE_ANIMATION, value).apply()

    var isHyphenationEnabled: Boolean
        get() = prefs.getBoolean(KEY_HYPHENATION, true)
        set(value) = prefs.edit().putBoolean(KEY_HYPHENATION, value).apply()

    companion object {
        private const val PREFS_NAME = "reader_prefs"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_LINE_SPACING = "line_spacing"
        private const val KEY_READER_THEME = "reading_theme"
        private const val KEY_PAGE_ANIMATION = "page_animation"
        private const val KEY_HYPHENATION = "hyphenation_enabled"
    }
}
