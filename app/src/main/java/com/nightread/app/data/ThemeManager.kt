package com.nightread.app.data

import android.content.Context

object ThemeManager {

    fun shouldBeNightMode(context: Context): Boolean {
        return ThemeHelper.shouldBeNightMode(context)
    }

    fun applyTheme(context: Context) {
        ThemeHelper.applyTheme(context)
    }
}
