package com.nightread.app

import android.app.Application
import android.util.Log
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.ThemeManager
import com.nightread.app.service.AutoDiscoveryService
import com.nightread.app.service.AutoDiscoveryWorker
import com.nightread.app.service.ThemeUpdateReceiver

class MainApplication : Application() {
    
    var bookScanner: com.nightread.app.service.NewBookScanner? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("MainApplication", "MainApplication onCreate: Initializing app.")
        
        // Apply theme immediately on startup
        ThemeManager.applyTheme(this)
        if (SettingsManager.isAutoThemeEnabled(this)) {
            ThemeUpdateReceiver.scheduleNextThemeAlarm(this)
        }
    }
}
