package com.nightread.app

import android.app.Application
import android.util.Log
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.ThemeManager
import com.nightread.app.service.AutoDiscoveryService
import com.nightread.app.service.AutoDiscoveryWorker
import com.nightread.app.service.ThemeUpdateReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

class MainApplication : Application(), ImageLoaderFactory {
    
    var bookScanner: com.nightread.app.service.NewBookScanner? = null

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30) // Use 30% of available memory for images
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // Use up to 5% of storage for disk cache
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val crashLog = sw.toString()
                
                val prefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("last_crash", crashLog).commit()
            } catch (e: Exception) {
                // Ignore
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        val prefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("last_crash")) {
            // If there's a crash, we still want to initialize essentials like ThemeManager
        }

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
    }
}
