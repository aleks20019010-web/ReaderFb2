package com.nightread.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

object ThemeHelper {

    fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 20 || hour < 6
    }

    fun shouldBeNightMode(context: Context): Boolean {
        if (!SettingsManager.isAutoLightNightEnabled(context)) {
            val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        return isNightTime()
    }

    fun applyTheme(context: Context) {
        if (SettingsManager.isAutoLightNightEnabled(context)) {
            val targetMode = if (isNightTime()) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode)
            }
        }
    }
}
