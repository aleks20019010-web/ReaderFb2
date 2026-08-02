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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nightread.app.R
import com.nightread.app.data.FileStorageHelper
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.ThemeManager
import com.nightread.app.data.DictionaryDownloader

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

    private var isSelectingForNightMode = false

    private val pickBackgroundLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            lifecycleScope.launch(Dispatchers.IO) {
                val success = FileStorageHelper.saveUserBackground(this@SettingsActivity, selectedUri, isSelectingForNightMode)
                withContext(Dispatchers.Main) {
                    if (success) {
                        CustomToast.show(this@SettingsActivity, getString(R.string.settings_toast_background_updated))
                        GalaxyBgHelper.applyBackground(findViewById(R.id.rootSettings))
                    } else {
                        CustomToast.show(this@SettingsActivity, getString(R.string.settings_toast_background_error))
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_fragment)

        GalaxyBgHelper.applyBackground(findViewById(R.id.rootSettings))
        findViewById<com.nightread.app.ui.StarryNightView>(R.id.starryOverlay)?.transparentBackground = true

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

        // Glass Header
        val glassHeader = findViewById<View>(R.id.glassHeader)
        glassHeader.findViewById<TextView>(R.id.header_title).text = getString(R.string.drawer_settings)
        val btnLeft = glassHeader.findViewById<ImageButton>(R.id.header_btn_left)
        btnLeft.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        btnLeft.setOnClickListener {
            val intent = Intent(this, com.nightread.app.MainActivity::class.java).apply {
                putExtra("OPEN_DRAWER", true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }

        // --- ОФОРМЛЕНИЕ И ТЕМА ---
        val switchAutoTheme = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAutoTheme)
        if (switchAutoTheme != null) {
            switchAutoTheme.isChecked = SettingsManager.isAutoLightNightEnabled(this)
            switchAutoTheme.setOnCheckedChangeListener { _, isChecked ->
                SettingsManager.setAutoLightNightEnabled(this, isChecked)
                com.nightread.app.data.ThemeHelper.applyTheme(this)
            }
        }

        // --- ЯЗЫК ИНТЕРФЕЙСА ---
        val spinnerLanguage = findViewById<Spinner>(R.id.spinnerLanguage)
        val languages = listOf(
            getString(R.string.language_russian),
            getString(R.string.language_english),
            getString(R.string.language_german)
        )
        val languageCodes = listOf("ru", "en", "de")
        
        val languageAdapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        languageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerLanguage.adapter = languageAdapter
        
        val currentLang = SettingsManager.getLanguage(this)
        val langIndex = languageCodes.indexOf(currentLang).coerceAtLeast(0)
        spinnerLanguage.setSelection(langIndex, false)
        
        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLang = languageCodes[position]
                if (SettingsManager.getLanguage(this@SettingsActivity) != selectedLang) {
                    SettingsManager.setLanguage(this@SettingsActivity, selectedLang)
                    // Apply locale using AppCompatDelegate (Standard Jetpack localization)
                    val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(selectedLang)
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
                    recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- НАСТРОЙКИ БИБЛИОТЕКИ ---
        // Buttons
        findViewById<View>(R.id.btnSelectBackground)?.setOnClickListener {
            showPickBackgroundDialog()
        }

        findViewById<Button>(R.id.btnScanLibrary).setOnClickListener {
            viewModel.startLocalBookScan()
            CustomToast.show(this, getString(R.string.settings_toast_scan_started))
        }

        findViewById<Button>(R.id.btnManualImport).setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }

        findViewById<Button>(R.id.btnDeleteDuplicates).setOnClickListener {
            CleanupDialogFragment().show(supportFragmentManager, "CleanupDialogFragment")
        }

        val btnDownloadDict = findViewById<Button>(R.id.btnDownloadDictionary)
        updateDictButtonText(btnDownloadDict)
        btnDownloadDict.setOnClickListener {
            btnDownloadDict.isEnabled = false
            btnDownloadDict.text = "Инициализация... 0%"
            lifecycleScope.launch {
                val success = DictionaryDownloader.downloadDictionary(this@SettingsActivity) { progress, message ->
                    runOnUiThread {
                        btnDownloadDict.text = message
                    }
                }
                btnDownloadDict.isEnabled = true
                updateDictButtonText(btnDownloadDict)
                if (success) {
                    CustomToast.show(this@SettingsActivity, "Словарь успешно скачан")
                } else {
                    CustomToast.show(this@SettingsActivity, "Не удалось скачать словарь")
                }
            }
        }

        // --- СИНХРОНИЗАЦИЯ ---
        // Auto-Sync Switch
        val switchAutoSync = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAutoSync)
        switchAutoSync.isChecked = SettingsManager.isAutoSyncEnabled(this)
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoSyncEnabled(this, isChecked)
            com.nightread.app.service.AutoSyncScheduler.scheduleAutoSync(this)
        }

        // Sync Period Spinner
        // Localized period days label
        val lang = SettingsManager.getLanguage(this)
        val periods = when (lang) {
            "en" -> listOf("1 day", "2 days", "3 days", "7 days")
            "de" -> listOf("1 Tag", "2 Tage", "3 Tage", "7 Tage")
            else -> listOf("1 день", "2 дня", "3 дня", "7 дней")
        }
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
                        com.nightread.app.service.AutoSyncScheduler.scheduleAutoSync(this@SettingsActivity)
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
                    com.nightread.app.service.AutoSyncScheduler.scheduleAutoSync(this)
                }
            }, hour, minute, true).show()
        }

        // --- ОЧИСТКА И ВОССТАНОВЛЕНИЕ ---
        findViewById<Button>(R.id.btnResetCache).setOnClickListener {
            viewModel.clearScanCache()
            CustomToast.show(this, getString(R.string.settings_toast_cache_reset))
        }

        findViewById<Button>(R.id.btnClearLibrary).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_clear_library_confirm_title)
                .setMessage(R.string.settings_clear_library_confirm_msg)
                .setPositiveButton(R.string.settings_clear_library_confirm_ok) { _, _ ->
                    viewModel.clearLibrary()
                    viewModel.cancelAllScanningTasks()
                    CustomToast.show(this, getString(R.string.settings_toast_library_cleared))
                }
                .setNegativeButton(R.string.settings_clear_library_confirm_cancel, null)
                .show()
        }

        // App Version
        findViewById<TextView>(R.id.tvAppVersion).text = getString(R.string.settings_app_version, com.nightread.app.BuildConfig.VERSION_NAME)
    }

    private fun showPickBackgroundDialog() {
        val options = arrayOf(
            getString(R.string.settings_pick_bg_dialog_light),
            getString(R.string.settings_pick_bg_dialog_dark)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_pick_bg_dialog_title)
            .setItems(options) { _, which ->
                isSelectingForNightMode = (which == 1)
                CustomToast.show(this, "Рекомендуемый размер изображения: под разрешение экрана (например, 1080x1920)")
                pickBackgroundLauncher.launch("image/*")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateDictButtonText(button: Button) {
        if (DictionaryDownloader.isDictionaryDownloaded(this)) {
            button.text = "Обновить офлайн-словарь"
        } else {
            button.text = "Скачать офлайн-словарь"
        }
    }
}
