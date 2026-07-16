package com.nightread.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

object ThemeHelper {

    fun shouldBeNightMode(context: Context): Boolean {
        if (!SettingsManager.isAutoLightNightEnabled(context)) {
            val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 21
    }

    fun shouldBeNightMode(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 21
    }

    fun applyTheme(context: Context) {
        val targetMode = if (SettingsManager.isAutoLightNightEnabled(context)) {
            if (shouldBeNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        } else {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }
}
