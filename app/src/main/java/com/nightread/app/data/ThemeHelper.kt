package com.nightread.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

object ThemeHelper {

    fun isNightTime(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val totalMinutes = hour * 60 + minute

        // From 20:00 to 06:00
        val sunsetMinutes = 20 * 60
        val sunriseMinutes = 6 * 60

        return totalMinutes >= sunsetMinutes || totalMinutes < sunriseMinutes
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
            val isNight = shouldBeNightMode(context)
            val targetMode = if (isNight) {
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
