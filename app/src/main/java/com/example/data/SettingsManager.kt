package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SettingsManager {
    private const val PREFS_NAME = "reader_prefs"
    
    const val KEY_THEME = "theme"
    const val KEY_PREVIOUS_THEME = "previous_theme"
    const val KEY_FONT_SIZE = "font_size"
    const val KEY_FONT_FAMILY = "font_family"
    const val KEY_FONT_WEIGHT = "font_weight"
    const val KEY_LINE_SPACING = "line_spacing"

    private val _settingsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val settingsChanged: SharedFlow<Unit> = _settingsChanged.asSharedFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun notifyChanged() {
        _settingsChanged.tryEmit(Unit)
    }

    fun getTheme(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, "sepia") ?: "sepia"
    }

    fun setTheme(context: Context, theme: String) {
        val prefs = getPrefs(context)
        if (theme != "dark") {
            prefs.edit().putString(KEY_PREVIOUS_THEME, theme).apply()
        }
        prefs.edit().putString(KEY_THEME, theme).apply()
        notifyChanged()
    }

    fun getPreviousTheme(context: Context): String {
        return getPrefs(context).getString(KEY_PREVIOUS_THEME, "sepia") ?: "sepia"
    }

    fun getFontSize(context: Context): Float {
        return getPrefs(context).getFloat(KEY_FONT_SIZE, 18f)
    }

    fun setFontSize(context: Context, size: Float) {
        getPrefs(context).edit().putFloat(KEY_FONT_SIZE, size).apply()
        notifyChanged()
    }

    fun getFontFamily(context: Context): String {
        return getPrefs(context).getString(KEY_FONT_FAMILY, "Roboto") ?: "Roboto"
    }

    fun setFontFamily(context: Context, family: String) {
        getPrefs(context).edit().putString(KEY_FONT_FAMILY, family).apply()
        notifyChanged()
    }

    fun getFontWeight(context: Context): String {
        return getPrefs(context).getString(KEY_FONT_WEIGHT, "Normal") ?: "Normal"
    }

    fun setFontWeight(context: Context, weight: String) {
        getPrefs(context).edit().putString(KEY_FONT_WEIGHT, weight).apply()
        notifyChanged()
    }

    fun getLineSpacing(context: Context): Float {
        return getPrefs(context).getFloat(KEY_LINE_SPACING, 1.2f)
    }

    fun setLineSpacing(context: Context, spacing: Float) {
        getPrefs(context).edit().putFloat(KEY_LINE_SPACING, spacing).apply()
        notifyChanged()
    }
}
