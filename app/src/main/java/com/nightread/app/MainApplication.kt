package com.nightread.app

import android.app.Application
import android.util.Log
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.ThemeManager
import com.nightread.app.service.AutoDiscoveryService
import com.nightread.app.service.AutoDiscoveryWorker
import com.nightread.app.service.ThemeUpdateReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {
    
    var bookScanner: com.nightread.app.service.NewBookScanner? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("MainApplication", "MainApplication onCreate: Initializing app.")
        
        // Reset sync state and cancel pending sync tasks if they were interrupted
        try {
            // Cancel any pending sync work
            androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag("YandexSyncWork")
            
            if (com.nightread.app.data.SyncSettingsManager.isSyncing(this)) {
                Log.w("SYNC_ERROR", "Detected interrupted sync during application startup. Resetting flag and cleaning cache.")
                com.nightread.app.data.SyncSettingsManager.setSyncing(this, false)
                com.nightread.app.data.SyncSettingsManager.setInterruptedFlag(this, true)
                com.nightread.app.data.YandexSyncState.reset()
                
                // Cleanup temporary files
                val cacheDir = this.cacheDir
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("temp_sha_") || file.name.startsWith("temp_down_")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("SYNC_ERROR", "Error during sync state cleanup on app startup", e)
        }

        // Apply theme immediately on startup
        ThemeManager.applyTheme(this)
        if (SettingsManager.isAutoThemeEnabled(this)) {
            ThemeUpdateReceiver.scheduleNextThemeAlarm(this)
        }

        // Auto-load AI model if configured and enabled
        if (SettingsManager.isAiEnabled(this) && SettingsManager.isAiAutoLoad(this)) {
            val activePath = SettingsManager.getAiModelPath(this)
            if (activePath != null && java.io.File(activePath).exists()) {
                Log.i("MainApplication", "Found downloaded AI model, initiating asynchronous loading...")
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        com.nightread.app.service.LocalAIManager.loadModel(this@MainApplication, activePath)
                        Log.i("MainApplication", "Asynchronous AI model loading complete.")
                    } catch (e: Exception) {
                        Log.e("MainApplication", "Failed to auto-load AI model at startup", e)
                    }
                }
            } else {
                Log.i("MainApplication", "AI model path is not set or file does not exist.")
            }
        }
    }
}
