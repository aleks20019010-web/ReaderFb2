package com.nightread.app

import android.app.Application
import android.util.Log
import com.nightread.app.data.SettingsManager
import com.nightread.app.service.AutoDiscoveryService
import com.nightread.app.service.AutoDiscoveryWorker

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("MainApplication", "MainApplication onCreate: Initializing app.")
        
        if (SettingsManager.isAutoDiscoveryEnabled(this)) {
            AutoDiscoveryWorker.schedule(this)
            try {
                AutoDiscoveryService.start(this)
            } catch (e: Exception) {
                Log.e("MainApplication", "Failed to start AutoDiscoveryService", e)
            }
        }
    }
}
