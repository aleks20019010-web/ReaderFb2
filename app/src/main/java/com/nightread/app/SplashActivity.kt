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
import com.nightread.app.service.NewBookScanner
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

        // 1. Centered logo animation fade-in (0.5 sec)
        brandContainer.animate()
            .alpha(1f)
            .setDuration(500)
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

                // Step 2: Load Library (20% -> 50%)
                animateProgressTo(50, "Загрузка библиотеки...", 400)
                val repository = BookRepository(db.bookDao(), db.noteDao())
                val cachedBooks = withContext(Dispatchers.IO) {
                    db.bookDao().getAllBooks().first()
                }
                Log.d(TAG, "Cached books loaded: ${cachedBooks.size}")

                // Step 3: Scan / Search for new books (50% -> 90%)
                tvStatus.text = "Поиск новых книг..."
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkSelfPermission("android.permission.READ_MEDIA_BOOKS") == PackageManager.PERMISSION_GRANTED
                } else {
                    checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }

                if (hasPermission) {
                    // Instantiate scanner
                    val scanner = withContext(Dispatchers.IO) {
                        val app = application as? MainApplication
                        app?.bookScanner ?: NewBookScanner(applicationContext, db.bookDao()).also {
                            app?.bookScanner = it
                        }
                    }

                    // Perform actual scanning in background and map progress
                    val scanJob = launch(Dispatchers.IO) {
                        try {
                            scanner.scanBooks()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error scanning books during splash", e)
                        }
                    }

                    // Collect scanner progress and map it between 50% and 90%
                    lifecycleScope.launch {
                        scanner.state.collect { state ->
                            if (state.isScanning && state.totalFiles > 0) {
                                val ratio = state.processedFiles.toFloat() / state.totalFiles.toFloat()
                                val progressValue = 50 + (ratio * 40).toInt()
                                withContext(Dispatchers.Main) {
                                    progressBar.progress = progressValue
                                    tvPercentage.text = "$progressValue%"
                                    if (state.status.isNotEmpty()) {
                                        tvStatus.text = state.status
                                    }
                                }
                            }
                        }
                    }

                    scanJob.join()
                    animateProgressTo(90, "Поиск новых книг...", 200)
                } else {
                    // No permission, just smoothly animate to 90%
                    animateProgressTo(90, "Поиск новых книг...", 600)
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
