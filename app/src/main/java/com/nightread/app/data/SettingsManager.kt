package com.nightread.app.data

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

    private var prefs: SharedPreferences? = null

    // Cache variables
    private var cachedTheme: String? = null
    private var cachedPrevTheme: String? = null
    private var cachedFontSize: Float? = null
    private var cachedFontFamily: String? = null
    private var cachedFontWeight: String? = null
    private var cachedLineSpacing: Float? = null

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    fun notifyChanged() {
        _settingsChanged.tryEmit(Unit)
    }

    fun getTheme(context: Context): String {
        if (cachedTheme == null) {
            cachedTheme = getPrefs(context).getString(KEY_THEME, "sepia") ?: "sepia"
        }
        return cachedTheme!!
    }

    fun setTheme(context: Context, theme: String) {
        if (cachedTheme == theme) return
        cachedTheme = theme
        val prefs = getPrefs(context)
        if (theme != "dark") {
            cachedPrevTheme = theme
            prefs.edit().putString(KEY_PREVIOUS_THEME, theme).apply()
        }
        prefs.edit().putString(KEY_THEME, theme).apply()
        notifyChanged()
    }

    fun getPreviousTheme(context: Context): String {
        if (cachedPrevTheme == null) {
            cachedPrevTheme = getPrefs(context).getString(KEY_PREVIOUS_THEME, "sepia") ?: "sepia"
        }
        return cachedPrevTheme!!
    }

    fun getFontSize(context: Context): Float {
        if (cachedFontSize == null) {
            cachedFontSize = getPrefs(context).getFloat(KEY_FONT_SIZE, 18f)
        }
        return cachedFontSize!!
    }

    fun setFontSize(context: Context, size: Float) {
        if (cachedFontSize == size) return
        cachedFontSize = size
        getPrefs(context).edit().putFloat(KEY_FONT_SIZE, size).apply()
        notifyChanged()
    }

    fun getFontFamily(context: Context): String {
        if (cachedFontFamily == null) {
            cachedFontFamily = getPrefs(context).getString(KEY_FONT_FAMILY, "Roboto") ?: "Roboto"
        }
        return cachedFontFamily!!
    }

    fun setFontFamily(context: Context, family: String) {
        if (cachedFontFamily == family) return
        cachedFontFamily = family
        getPrefs(context).edit().putString(KEY_FONT_FAMILY, family).apply()
        notifyChanged()
    }

    fun getFontWeight(context: Context): String {
        if (cachedFontWeight == null) {
            cachedFontWeight = getPrefs(context).getString(KEY_FONT_WEIGHT, "Normal") ?: "Normal"
        }
        return cachedFontWeight!!
    }

    fun setFontWeight(context: Context, weight: String) {
        if (cachedFontWeight == weight) return
        cachedFontWeight = weight
        getPrefs(context).edit().putString(KEY_FONT_WEIGHT, weight).apply()
        notifyChanged()
    }

    fun getLineSpacing(context: Context): Float {
        if (cachedLineSpacing == null) {
            cachedLineSpacing = getPrefs(context).getFloat(KEY_LINE_SPACING, 1.2f)
        }
        return cachedLineSpacing!!
    }

    fun setLineSpacing(context: Context, spacing: Float) {
        if (cachedLineSpacing == spacing) return
        cachedLineSpacing = spacing
        getPrefs(context).edit().putFloat(KEY_LINE_SPACING, spacing).apply()
        notifyChanged()
    }
}
