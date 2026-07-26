package com.nightread.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.SettingsManager

class SettingsFragment : Fragment() {

    private val viewModel: BookViewModel by lazy {
        ViewModelProvider(this)[BookViewModel::class.java]
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val ctx = context ?: return@registerForActivityResult
            viewModel.importBookFromUri(it, ctx) { success, message ->
                CustomToast.show(ctx, message)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.settings_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()
        view.findViewById<StarryNightView>(R.id.starryOverlay)?.transparentBackground = true

        // Glass Header
        val glassHeader = view.findViewById<View>(R.id.glassHeader)
        glassHeader.findViewById<TextView>(R.id.header_title).text = getString(R.string.drawer_settings)
        val btnLeft = glassHeader.findViewById<ImageButton>(R.id.header_btn_left)
        btnLeft.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        btnLeft.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        // --- ЯЗЫК ИНТЕРФЕЙСА ---
        val spinnerLanguage = view.findViewById<Spinner>(R.id.spinnerLanguage)
        val languages = listOf(getString(R.string.language_russian), getString(R.string.language_english))
        val languageCodes = listOf("ru", "en")

        val languageAdapter = ArrayAdapter(ctx, R.layout.spinner_item, languages)
        languageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerLanguage.adapter = languageAdapter

        val currentLang = SettingsManager.getLanguage(ctx)
        val langIndex = languageCodes.indexOf(currentLang).coerceAtLeast(0)
        spinnerLanguage.setSelection(langIndex, false)

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val selectedLang = languageCodes[position]
                if (SettingsManager.getLanguage(ctx) != selectedLang) {
                    SettingsManager.setLanguage(ctx, selectedLang)
                    val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(selectedLang)
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
                    activity?.recreate()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- НАСТРОЙКИ БИБЛИОТЕКИ ---
        view.findViewById<Button>(R.id.btnScanLibrary).setOnClickListener {
            viewModel.startLocalBookScan()
            CustomToast.show(ctx, getString(R.string.settings_toast_scan_started))
        }

        view.findViewById<Button>(R.id.btnManualImport).setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }

        view.findViewById<Button>(R.id.btnDeleteDuplicates).setOnClickListener {
            CleanupDialogFragment().show(parentFragmentManager, "CleanupDialogFragment")
        }

        // --- СИНХРОНИЗАЦИЯ ---
        val switchAutoSync = view.findViewById<SwitchCompat>(R.id.switchAutoSync)
        switchAutoSync.isChecked = SettingsManager.isAutoSyncEnabled(ctx)
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoSyncEnabled(ctx, isChecked)
            com.nightread.app.service.AutoSyncScheduler.scheduleAutoSync(ctx)
        }

        val isEn = SettingsManager.getLanguage(ctx) == "en"
        val periods = if (isEn) {
            listOf("1 day", "2 days", "3 days", "7 days")
        } else {
            listOf("1 день", "2 дня", "3 дня", "7 дней")
        }
        val periodValues = listOf(1, 2, 3, 7)
        val periodAdapter = ArrayAdapter(ctx, R.layout.spinner_item, periods)
        periodAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        val spinnerPeriod = view.findViewById<Spinner>(R.id.spinnerSyncPeriod)
        spinnerPeriod.adapter = periodAdapter

        val currentPeriodDays = SettingsManager.getAutoSyncIntervalDays(ctx)
        val selectionIndex = periodValues.indexOf(currentPeriodDays).coerceAtLeast(0)
        spinnerPeriod.setSelection(selectionIndex)

        spinnerPeriod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val selectedDays = periodValues[position]
                if (SettingsManager.getAutoSyncIntervalDays(ctx) != selectedDays) {
                    SettingsManager.setAutoSyncIntervalDays(ctx, selectedDays)
                    if (SettingsManager.isAutoSyncEnabled(ctx)) {
                        com.nightread.app.service.AutoSyncScheduler.scheduleAutoSync(ctx)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val tvSyncTimeValue = view.findViewById<TextView>(R.id.tvSyncTimeValue)
        val currentStartTime = SettingsManager.getAutoSyncStartTime(ctx)
        tvSyncTimeValue.text = currentStartTime

        view.findViewById<View>(R.id.layoutSyncTime).setOnClickListener {
            val parts = tvSyncTimeValue.text.toString().split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 3
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            TimePickerDialog(ctx, { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                tvSyncTimeValue.text = formattedTime
                SettingsManager.setAutoSyncStartTime(ctx, formattedTime)
                if (SettingsManager.isAutoSyncEnabled(ctx)) {
                    com.nightread.app.service.AutoSyncScheduler.scheduleAutoSync(ctx)
                }
            }, hour, minute, true).show()
        }

        // --- ОЧИСТКА И ВОССТАНОВЛЕНИЕ ---
        view.findViewById<Button>(R.id.btnResetCache).setOnClickListener {
            viewModel.clearScanCache()
            CustomToast.show(ctx, getString(R.string.settings_toast_cache_reset))
        }

        view.findViewById<Button>(R.id.btnClearLibrary).setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle(R.string.settings_clear_library_confirm_title)
                .setMessage(R.string.settings_clear_library_confirm_msg)
                .setPositiveButton(R.string.settings_clear_library_confirm_ok) { _, _ ->
                    viewModel.clearLibrary()
                    viewModel.cancelAllScanningTasks()
                    CustomToast.show(ctx, getString(R.string.settings_toast_library_cleared))
                }
                .setNegativeButton(R.string.settings_clear_library_confirm_cancel, null)
                .show()
        }

        view.findViewById<TextView>(R.id.tvAppVersion).text = getString(R.string.settings_app_version, com.nightread.app.BuildConfig.VERSION_NAME)
    }
}
