package com.nightread.app.data.prefs

import android.content.SharedPreferences

class ReaderPreferences(private val getPrefs: () -> SharedPreferences?) {

    fun getTheme(): String = getPrefs()?.getString("theme", "light") ?: "light"
    fun setTheme(value: String) {
        getPrefs()?.edit()?.putString("theme", value)?.apply()
    }

    fun getFontSize(): Float = getPrefs()?.getFloat("font_size", 18f) ?: 18f
    fun setFontSize(value: Float) {
        getPrefs()?.edit()?.putFloat("font_size", value)?.apply()
    }

    fun getFontFamily(): String = getPrefs()?.getString("font_family", "Roboto") ?: "Roboto"
    fun setFontFamily(value: String) {
        getPrefs()?.edit()?.putString("font_family", value)?.apply()
    }

    fun getFontWeight(): String = getPrefs()?.getString("font_weight", "NORMAL") ?: "NORMAL"
    fun setFontWeight(value: String) {
        getPrefs()?.edit()?.putString("font_weight", value)?.apply()
    }

    fun getLineSpacing(): Float = getPrefs()?.getFloat("line_spacing", 1.4f) ?: 1.4f
    fun setLineSpacing(value: Float) {
        getPrefs()?.edit()?.putFloat("line_spacing", value)?.apply()
    }

    fun getBrightness(): Float = getPrefs()?.getFloat("brightness", 0.8f) ?: 0.8f
    fun setBrightness(value: Float) {
        getPrefs()?.edit()?.putFloat("brightness", value)?.apply()
    }

    fun getPageAnimation(): String = getPrefs()?.getString("page_animation", "SLIDE") ?: "SLIDE"
    fun setPageAnimation(value: String) {
        getPrefs()?.edit()?.putString("page_animation", value)?.apply()
    }

    fun isHyphenationEnabled(): Boolean = getPrefs()?.getBoolean("hyphenation_enabled", true) ?: true
    fun setHyphenationEnabled(value: Boolean) {
        getPrefs()?.edit()?.putBoolean("hyphenation_enabled", value)?.apply()
    }
}
