package com.nightread.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

object ThemeHelper {

    fun shouldBeNightMode(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 21
    }

    fun applyTheme(context: Context) {
        val targetMode = if (shouldBeNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }
}
