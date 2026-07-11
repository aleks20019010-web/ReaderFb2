package com.nightread.app.data

import android.content.Context

object ThemeManager {

    fun shouldBeNightMode(): Boolean {
        return ThemeHelper.shouldBeNightMode()
    }

    fun applyTheme(context: Context) {
        ThemeHelper.applyTheme(context)
    }
}
