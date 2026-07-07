package com.nightread.app.ui

import android.app.Activity
import android.view.WindowManager

object BrightnessHelper {
    fun setBrightness(activity: Activity, brightness: Float) {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = brightness.coerceIn(0.01f, 1f)
        activity.window.attributes = layoutParams
    }

    fun getBrightness(activity: Activity): Float {
        val brightness = activity.window.attributes.screenBrightness
        return if (brightness < 0) 0.5f else brightness
    }
}
