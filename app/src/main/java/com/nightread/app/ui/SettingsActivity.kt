package com.nightread.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.nightread.app.R
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.ThemeManager

class SettingsActivity : BaseActivity() {

    private val viewModel: BookViewModel by lazy {
        ViewModelProvider(this)[BookViewModel::class.java]
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importBookFromUri(it, this) { success, message ->
                CustomToast.show(this, message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_fragment)

        // Support Edge-to-Edge immersion and safe areas
        val rootLayout = findViewById<View>(R.id.rootSettings)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            val topPadding = insets.top + (12 * resources.displayMetrics.density).toInt()
            val bottomPadding = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(0, topPadding, 0, bottomPadding)
            windowInsets
        }

        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Настройки"
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // --- НАСТРОЙКИ БИБЛИОТЕКИ ---
        // Buttons
        findViewById<Button>(R.id.btnScanLibrary).setOnClickListener {
            viewModel.startLocalBookScan()
            CustomToast.show(this, "Сканирование библиотеки запущено")
        }

        findViewById<Button>(R.id.btnManualImport).setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btnDeleteDuplicates).setOnClickListener {
            CleanupDialogFragment().show(supportFragmentManager, "CleanupDialogFragment")
        }

        // --- СИНХРОНИЗАЦИЯ ---
        // Auto-Sync Switch
        val switchAutoSync = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAutoSync)
        switchAutoSync.isChecked = SettingsManager.isAutoSyncEnabled(this)
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoSyncEnabled(this, isChecked)
            scheduleAutoSync(this)
        }

        // Sync Period Spinner
        val periods = listOf("1 день", "2 дня", "3 дня", "7 дней")
        val periodValues = listOf(1, 2, 3, 7)
        val periodAdapter = ArrayAdapter(this, R.layout.spinner_item, periods)
        periodAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        val spinnerPeriod = findViewById<Spinner>(R.id.spinnerSyncPeriod)
        spinnerPeriod.adapter = periodAdapter

        val currentPeriodDays = SettingsManager.getAutoSyncIntervalDays(this)
        val selectionIndex = periodValues.indexOf(currentPeriodDays).coerceAtLeast(0)
        spinnerPeriod.setSelection(selectionIndex)

        spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedDays = periodValues[position]
                if (SettingsManager.getAutoSyncIntervalDays(this@SettingsActivity) != selectedDays) {
                    SettingsManager.setAutoSyncIntervalDays(this@SettingsActivity, selectedDays)
                    if (SettingsManager.isAutoSyncEnabled(this@SettingsActivity)) {
                        scheduleAutoSync(this@SettingsActivity)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Sync Start Time Value
        val tvSyncTimeValue = findViewById<TextView>(R.id.tvSyncTimeValue)
        val currentStartTime = SettingsManager.getAutoSyncStartTime(this)
        tvSyncTimeValue.text = currentStartTime

        findViewById<View>(R.id.layoutSyncTime).setOnClickListener {
            val parts = tvSyncTimeValue.text.toString().split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 3
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            android.app.TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                tvSyncTimeValue.text = formattedTime
                SettingsManager.setAutoSyncStartTime(this, formattedTime)
                if (SettingsManager.isAutoSyncEnabled(this)) {
                    scheduleAutoSync(this)
                }
            }, hour, minute, true).show()
        }

        // --- ОЧИСТКА И ВОССТАНОВЛЕНИЕ ---
        findViewById<Button>(R.id.btnResetCache).setOnClickListener {
            viewModel.clearScanCache()
            CustomToast.show(this, "Кэш сканирования успешно сброшен")
        }

        findViewById<Button>(R.id.btnClearLibrary).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Очистить библиотеку?")
                .setMessage("Все книги будут удалены из базы данных приложения. Это действие необратимо.")
                .setPositiveButton("Удалить всё") { _, _ ->
                    viewModel.clearLibrary()
                    viewModel.cancelAllScanningTasks()
                    CustomToast.show(this, "Библиотека полностью очищена")
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        // App Version
        findViewById<TextView>(R.id.tvAppVersion).text = "Версия: ${com.nightread.app.BuildConfig.VERSION_NAME}"
    }

    private fun scheduleAutoSync(context: Context) {
        try {
            val workManager = androidx.work.WorkManager.getInstance(context)
            workManager.cancelUniqueWork("YandexAutoSyncWork")

            if (!SettingsManager.isAutoSyncEnabled(context)) {
                return
            }

            val days = SettingsManager.getAutoSyncIntervalDays(context)
            val startTime = SettingsManager.getAutoSyncStartTime(context)

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val initialDelayMs = calculateInitialDelay(startTime)

            val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.nightread.app.service.AutoSyncWorker>(
                days.toLong(), java.util.concurrent.TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("YandexAutoSyncWork")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "YandexAutoSyncWork",
                androidx.work.ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                workRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("SettingsActivity", "Error scheduling auto sync", e)
        }
    }

    private fun calculateInitialDelay(timeStr: String): Long {
        try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return 0L
            val targetHour = parts[0].toIntOrNull() ?: 3
            val targetMinute = parts[1].toIntOrNull() ?: 0

            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                set(java.util.Calendar.MINUTE, targetMinute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            return target.timeInMillis - now.timeInMillis
        } catch (e: Exception) {
            return 0L
        }
    }
}
