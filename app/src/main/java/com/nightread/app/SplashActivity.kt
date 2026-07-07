package com.nightread.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookRepository
import com.nightread.app.data.SettingsManager
import com.nightread.app.service.NewBookScanner
import com.nightread.app.service.AutoDiscoveryWorker
import com.nightread.app.service.AutoDiscoveryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private val TAG = "SplashActivity"

    private lateinit var brandContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvPercentage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Enable Edge-to-Edge feel
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        brandContainer = findViewById(R.id.brand_container)
        progressBar = findViewById(R.id.progress_bar)
        tvStatus = findViewById(R.id.tv_status)
        tvPercentage = findViewById(R.id.tv_percentage)

        // 1. Centered logo animation fade-in and subtle zoom-in (800ms)
        brandContainer.scaleX = 0.8f
        brandContainer.scaleY = 0.8f
        brandContainer.animate()
            .alpha(1f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(800)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Start initialization logic
        startInitialization()
    }

    private fun startInitialization() {
        // Safety Watchdog: If loading takes longer than 5 seconds, show "Идёт загрузка..."
        val watchdogJob = lifecycleScope.launch {
            delay(5000)
            if (lifecycleScope.isActive) {
                tvStatus.text = "Идёт загрузка..."
            }
        }

        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // Step 1: Database preparation (0% -> 20%)
                animateProgressTo(20, "Подготовка базы данных...", 400)
                val db = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext)
                }

                // Step 2: Query library book count (20% -> 50%)
                animateProgressTo(50, "Проверка книг в библиотеке...", 400)
                val repository = BookRepository(db.bookDao(), db.noteDao())
                val booksCount = withContext(Dispatchers.IO) {
                    repository.getBooksCount()
                }
                Log.d(TAG, "Database check: total books count = $booksCount")

                // Step 3: Check/Trigger background auto-discovery
                val isAutoDiscoveryEnabled = SettingsManager.isAutoDiscoveryEnabled(applicationContext)

                if (booksCount > 0 && isAutoDiscoveryEnabled) {
                    tvStatus.text = "Фоновый поиск новых книг..."
                    Log.d(TAG, "Books exist and auto-discovery is enabled. Triggering background work.")
                    AutoDiscoveryWorker.runOnce(applicationContext)
                    AutoDiscoveryWorker.schedule(applicationContext)
                    try {
                        AutoDiscoveryService.start(applicationContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start AutoDiscoveryService", e)
                    }
                    animateProgressTo(90, "Фоновый поиск запущен", 400)
                } else {
                    tvStatus.text = "Проверка библиотеки..."
                    Log.d(TAG, "Library is empty or auto-discovery disabled. No auto-discovery triggered.")
                    animateProgressTo(90, "Библиотека готова", 400)
                }

                // Step 4: Finished (90% -> 100%)
                animateProgressTo(100, "Готово!", 300)

                // Enforce minimum display time of 1.5 seconds
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 1500) {
                    delay(1500 - elapsed)
                }

                // Cancel the watchdog
                watchdogJob.cancel()

                // Proceed to MainActivity
                navigateToMain()

            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                watchdogJob.cancel()
                // On failure, fallback to MainActivity so the user isn't stuck
                navigateToMain()
            }
        }
    }

    private suspend fun animateProgressTo(target: Int, status: String, duration: Long) {
        withContext(Dispatchers.Main) {
            tvStatus.text = status
        }
        val start = progressBar.progress
        if (start >= target) return
        val steps = target - start
        val delayTime = duration / steps
        for (p in start..target) {
            withContext(Dispatchers.Main) {
                progressBar.progress = p
                tvPercentage.text = "$p%"
            }
            delay(delayTime)
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        // Smooth transition override
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
