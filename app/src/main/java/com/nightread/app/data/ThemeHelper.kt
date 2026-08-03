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
        // Local sunset to sunrise: 19:30 (7:30 PM) to 06:30 (6:30 AM)
        val sunsetMinutes = 19 * 60 + 30
        val sunriseMinutes = 6 * 60 + 30
        return totalMinutes >= sunsetMinutes || totalMinutes < sunriseMinutes
    }

    fun shouldBeNightMode(context: Context): Boolean {
        if (!SettingsManager.isAutoLightNightEnabled(context)) {
            val currentNightMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val lux = SettingsManager.getAmbientLux()
        if (lux < 15f) return true
        if (lux > 30f) return false
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
