package com.nightread.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import com.nightread.app.R
import com.nightread.app.data.SettingsManager

class SettingsBottomSheet : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use custom style for dialog styling (dimming, floating window, etc.)
        setStyle(STYLE_NORMAL, R.style.SettingsDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the compact XML layout
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        // Enable closing by tapping outside
        dialog?.setCanceledOnTouchOutside(true)

        // 1. Close Button
        view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            dismiss()
        }

        // 2. Font Selection (Spinner)
        val fontOptions = listOf("Roboto", "Times New Roman", "Georgia", "Merriweather", "Lora", "EB Garamond", "Literata", "OpenDyslexic", "Monospace")
        val spinnerFont = view.findViewById<Spinner>(R.id.spinnerFont)
        val fontAdapter = SettingsSpinnerAdapter(context, fontOptions).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinnerFont.adapter = fontAdapter
        
        val currentFont = SettingsManager.getFontFamily(context)
        val fontIdx = fontOptions.indexOf(currentFont).coerceAtLeast(0)
        spinnerFont.setSelection(fontIdx)
        spinnerFont.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedFont = fontOptions[position]
                if (selectedFont != SettingsManager.getFontFamily(context)) {
                    SettingsManager.setFontFamily(context, selectedFont)
                    applyThemeColors(SettingsManager.getReadingTheme(context), this@SettingsBottomSheet.requireView())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Quick Font Buttons Hookup
        val btnFontSans = view.findViewById<TextView>(R.id.btnFontSans)
        val btnFontSerif = view.findViewById<TextView>(R.id.btnFontSerif)
        val btnFontMono = view.findViewById<TextView>(R.id.btnFontMono)

        btnFontSans?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getFontFamily(context) != "Roboto") {
                SettingsManager.setFontFamily(context, "Roboto")
                val idx = fontOptions.indexOf("Roboto").coerceAtLeast(0)
                spinnerFont.setSelection(idx)
                applyThemeColors(SettingsManager.getReadingTheme(context), view)
            }
        }

        btnFontSerif?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getFontFamily(context) != "Georgia") {
                SettingsManager.setFontFamily(context, "Georgia")
                val idx = fontOptions.indexOf("Georgia").coerceAtLeast(0)
                spinnerFont.setSelection(idx)
                applyThemeColors(SettingsManager.getReadingTheme(context), view)
            }
        }

        btnFontMono?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getFontFamily(context) != "Monospace") {
                SettingsManager.setFontFamily(context, "Monospace")
                val idx = fontOptions.indexOf("Monospace").coerceAtLeast(0)
                spinnerFont.setSelection(idx)
                applyThemeColors(SettingsManager.getReadingTheme(context), view)
            }
        }

        // 3. Font Size (SeekBar)
        val tvFontSizeValue = view.findViewById<TextView>(R.id.tvFontSizeValue)
        val seekBarFontSize = view.findViewById<SeekBar>(R.id.seekBarFontSize)
        val currentFontSize = SettingsManager.getFontSize(context).toInt()
        tvFontSizeValue.text = "$currentFontSize sp"
        seekBarFontSize.progress = (currentFontSize - 14).coerceIn(0, 14)
        var lastFontSizeProgress = seekBarFontSize.progress
        seekBarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSize = progress + 14
                tvFontSizeValue.text = "$newSize sp"
                if (fromUser) {
                    SettingsManager.setFontSize(context, newSize.toFloat())
                    if (progress != lastFontSizeProgress) {
                        seekBar?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        lastFontSizeProgress = progress
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 4. Font Weight (SeekBar)
        val tvFontWeightValue = view.findViewById<TextView>(R.id.tvFontWeightValue)
        val seekBarFontWeight = view.findViewById<SeekBar>(R.id.seekBarFontWeight)
        
        fun getWeightLabel(weight: Int): String {
            return when (weight) {
                100 -> "Сверхтонкий (100)"
                150 -> "Ультратонкий (150)"
                200 -> "Тонкий (200)"
                250 -> "Очень легкий (250)"
                300 -> "Легкий (300)"
                350 -> "Книжный (350)"
                400 -> "Обычный (400)"
                450 -> "Литературный (450)"
                500 -> "Средний (500)"
                550 -> "Умеренно жирный (550)"
                600 -> "Полужирный (600)"
                650 -> "Плотный (650)"
                700 -> "Жирный (700)"
                750 -> "Очень жирный (750)"
                800 -> "Сверхжирный (800)"
                850 -> "Экстратяжелый (850)"
                900 -> "Тяжелый (900)"
                else -> "Обычный ($weight)"
            }
        }

        val currentWeight = SettingsManager.getFontWeightAsInt(context)
        tvFontWeightValue?.text = getWeightLabel(currentWeight)
        seekBarFontWeight?.progress = ((currentWeight - 100) / 50).coerceIn(0, 16)
        var lastFontWeightProgress = seekBarFontWeight?.progress ?: 6
        seekBarFontWeight?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val weightValue = (progress * 50) + 100
                tvFontWeightValue?.text = getWeightLabel(weightValue)
                if (fromUser) {
                    SettingsManager.setFontWeight(context, weightValue.toString())
                    if (progress != lastFontWeightProgress) {
                        seekBar?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        lastFontWeightProgress = progress
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 5. Theme Selection (Spinner)
        val themeKeys = listOf("light", "dark", "sepia", "sepia_contrast", "amoled")
        val themeNames = mapOf(
            "light" to "День",
            "dark" to "Ночь",
            "sepia" to "Сепия",
            "sepia_contrast" to "Сепия контраст",
            "amoled" to "Абсолютная сингулярность"
        )
        val themeDisplayNames = themeKeys.map { themeNames[it] ?: it }
        val spinnerTheme = view.findViewById<Spinner>(R.id.spinnerTheme)
        val themeAdapter = SettingsSpinnerAdapter(context, themeDisplayNames).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinnerTheme.adapter = themeAdapter
        
        val currentTheme = SettingsManager.getReadingTheme(context)
        val themeIdx = themeKeys.indexOf(currentTheme).coerceAtLeast(0)
        spinnerTheme.setSelection(themeIdx)
        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKey = themeKeys[position]
                if (selectedKey != SettingsManager.getReadingTheme(context)) {
                    SettingsManager.setReadingTheme(context, selectedKey)
                    applyThemeColors(selectedKey, this@SettingsBottomSheet.requireView())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Keep reading themes active and independent of app's auto-theme
        spinnerTheme.isEnabled = true
        spinnerTheme.alpha = 1.0f

        // 5b. Page Flip Animation Selection (Spinner)
        val animKeys = listOf("curl", "slide", "fade", "depth", "zoom", "none")
        val animNames = mapOf(
            "curl" to "3D Page Curl (изгиб листа)",
            "slide" to "Стандартный (слайд)",
            "fade" to "Fade (исчезновение)",
            "depth" to "Depth (глубина)",
            "zoom" to "Zoom Out (уменьшение)",
            "none" to "Без анимации"
        )
        val animDisplayNames = animKeys.map { animNames[it] ?: it }
        val spinnerAnimation = view.findViewById<Spinner>(R.id.spinnerAnimation)
        val animAdapter = SettingsSpinnerAdapter(context, animDisplayNames).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinnerAnimation.adapter = animAdapter

        val currentAnim = SettingsManager.getPageAnimation(context)
        val animIdx = animKeys.indexOf(currentAnim).coerceAtLeast(0)
        spinnerAnimation.setSelection(animIdx)
        spinnerAnimation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKey = animKeys[position]
                if (selectedKey != SettingsManager.getPageAnimation(context)) {
                    SettingsManager.setPageAnimation(context, selectedKey)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 5c. Text Alignment Selection (Spinner)
        val alignKeys = listOf("left", "center", "right", "justify")
        val alignNames = mapOf(
            "left" to "По левому краю",
            "center" to "По центру",
            "right" to "По правому краю",
            "justify" to "По ширине"
        )
        val alignDisplayNames = alignKeys.map { alignNames[it] ?: it }
        val spinnerAlignment = view.findViewById<Spinner>(R.id.spinnerAlignment)
        val alignAdapter = SettingsSpinnerAdapter(context, alignDisplayNames).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinnerAlignment.adapter = alignAdapter

        val readerPrefs = context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
        val currentAlign = readerPrefs.getString("saved_font_alignment", "justify") ?: "justify"
        val alignIdx = alignKeys.indexOf(currentAlign).coerceAtLeast(0)
        spinnerAlignment.setSelection(alignIdx)
        spinnerAlignment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKey = alignKeys[position]
                if (selectedKey != readerPrefs.getString("saved_font_alignment", "justify")) {
                    readerPrefs.edit().putString("saved_font_alignment", selectedKey).apply()
                    SettingsManager.notifyChanged()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }


        // 7. Auto-Discovery Switch
        val switchAutoDiscovery = view.findViewById<SwitchCompat>(R.id.switchAutoDiscovery)
        switchAutoDiscovery.isChecked = SettingsManager.isAutoDiscoveryEnabled(context)
        switchAutoDiscovery.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != SettingsManager.isAutoDiscoveryEnabled(context)) {
                SettingsManager.setAutoDiscoveryEnabled(context, isChecked)
                if (isChecked) {
                    com.nightread.app.service.AutoDiscoveryWorker.schedule(context)
                    com.nightread.app.service.AutoDiscoveryService.start(context)
                } else {
                    com.nightread.app.service.AutoDiscoveryWorker.cancel(context)
                    com.nightread.app.service.AutoDiscoveryService.stop(context)
                }
            }
        }

        // 7b. Auto-Light-Night Switch
        val switchAutoLightNight = view.findViewById<SwitchCompat>(R.id.switchAutoLightNight)
        switchAutoLightNight.isChecked = SettingsManager.isAutoLightNightEnabled(context)
        switchAutoLightNight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != SettingsManager.isAutoLightNightEnabled(context)) {
                SettingsManager.setAutoLightNightEnabled(context, isChecked)
                com.nightread.app.data.ThemeManager.applyTheme(context)
                if (isChecked) {
                    com.nightread.app.service.ThemeUpdateReceiver.scheduleNextThemeAlarm(context)
                } else {
                    com.nightread.app.service.ThemeUpdateReceiver.cancelAlarm(context)
                }
            }
        }

        // 7c. Amber Filter Switch & Intensity Hookup
        val switchAmberFilter = view.findViewById<SwitchCompat>(R.id.switchAmberFilter)
        val layoutAmberIntensity = view.findViewById<LinearLayout>(R.id.layoutAmberIntensity)
        val tvAmberIntensityValue = view.findViewById<TextView>(R.id.tvAmberIntensityValue)
        val seekBarAmberIntensity = view.findViewById<SeekBar>(R.id.seekBarAmberIntensity)

        val initialAmberEnabled = SettingsManager.isAmberFilterEnabled(context)
        switchAmberFilter.isChecked = initialAmberEnabled
        layoutAmberIntensity.visibility = if (initialAmberEnabled) View.VISIBLE else View.GONE

        val initialIntensity = SettingsManager.getAmberFilterIntensity(context)
        tvAmberIntensityValue.text = "$initialIntensity%"
        seekBarAmberIntensity.progress = initialIntensity

        switchAmberFilter.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != SettingsManager.isAmberFilterEnabled(context)) {
                SettingsManager.setAmberFilterEnabled(context, isChecked)
                layoutAmberIntensity.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }

        seekBarAmberIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvAmberIntensityValue.text = "$progress%"
                if (fromUser) {
                    SettingsManager.setAmberFilterIntensity(context, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 7c_2. Extra Dim Switch & Intensity Hookup
        val switchExtraDim = view.findViewById<SwitchCompat>(R.id.switchExtraDim)
        val layoutExtraDimIntensity = view.findViewById<LinearLayout>(R.id.layoutExtraDimIntensity)
        val tvExtraDimIntensityValue = view.findViewById<TextView>(R.id.tvExtraDimIntensityValue)
        val seekBarExtraDimIntensity = view.findViewById<SeekBar>(R.id.seekBarExtraDimIntensity)

        val initialExtraDimEnabled = SettingsManager.isExtraDimEnabled(context)
        switchExtraDim.isChecked = initialExtraDimEnabled
        layoutExtraDimIntensity.visibility = if (initialExtraDimEnabled) View.VISIBLE else View.GONE

        val initialExtraDimIntensity = SettingsManager.getExtraDimIntensity(context)
        tvExtraDimIntensityValue.text = "$initialExtraDimIntensity%"
        seekBarExtraDimIntensity.progress = initialExtraDimIntensity

        switchExtraDim.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != SettingsManager.isExtraDimEnabled(context)) {
                SettingsManager.setExtraDimEnabled(context, isChecked)
                layoutExtraDimIntensity.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }

        seekBarExtraDimIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvExtraDimIntensityValue.text = "$progress%"
                if (fromUser) {
                    SettingsManager.setExtraDimIntensity(context, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 7d. Sleep Timer Switches & Seekbar Hookup
        val switchSleepTimer = view.findViewById<SwitchCompat>(R.id.switchSleepTimer)
        val layoutSleepTimerDuration = view.findViewById<LinearLayout>(R.id.layoutSleepTimerDuration)
        val tvSleepTimerValue = view.findViewById<TextView>(R.id.tvSleepTimerValue)
        val seekBarSleepTimer = view.findViewById<SeekBar>(R.id.seekBarSleepTimer)
        val switchShakeToExtend = view.findViewById<SwitchCompat>(R.id.switchShakeToExtend)
        val layoutShakeToExtend = view.findViewById<RelativeLayout>(R.id.layoutShakeToExtend)

        val initialSleepTimerEnabled = SettingsManager.isSleepTimerEnabled(context)
        switchSleepTimer.isChecked = initialSleepTimerEnabled
        layoutSleepTimerDuration.visibility = if (initialSleepTimerEnabled) View.VISIBLE else View.GONE
        layoutShakeToExtend.visibility = if (initialSleepTimerEnabled) View.VISIBLE else View.GONE

        val initialDuration = SettingsManager.getSleepTimerDuration(context).coerceAtLeast(5)
        tvSleepTimerValue.text = "$initialDuration мин"
        seekBarSleepTimer.progress = initialDuration

        switchSleepTimer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != SettingsManager.isSleepTimerEnabled(context)) {
                SettingsManager.setSleepTimerEnabled(context, isChecked)
                layoutSleepTimerDuration.visibility = if (isChecked) View.VISIBLE else View.GONE
                layoutShakeToExtend.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }

        seekBarSleepTimer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val duration = progress.coerceAtLeast(5)
                tvSleepTimerValue.text = "$duration мин"
                if (fromUser) {
                    SettingsManager.setSleepTimerDuration(context, duration)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val initialShake = SettingsManager.isShakeToExtendEnabled(context)
        switchShakeToExtend.isChecked = initialShake
        switchShakeToExtend.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != SettingsManager.isShakeToExtendEnabled(context)) {
                SettingsManager.setShakeToExtendEnabled(context, isChecked)
            }
        }

        // 8. Color Scheme Circle Buttons Hookup
        val btnThemeLight = view.findViewById<FrameLayout>(R.id.btnThemeLight)
        val btnThemeSepia = view.findViewById<FrameLayout>(R.id.btnThemeSepia)
        val btnThemeDark = view.findViewById<FrameLayout>(R.id.btnThemeDark)

        btnThemeSepia.visibility = View.VISIBLE

        btnThemeLight.isEnabled = true
        btnThemeLight.alpha = 1.0f
        btnThemeSepia.isEnabled = true
        btnThemeSepia.alpha = 1.0f
        btnThemeDark.isEnabled = true
        btnThemeDark.alpha = 1.0f

        btnThemeLight.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getReadingTheme(context) != "light") {
                SettingsManager.setReadingTheme(context, "light")
                val idx = themeKeys.indexOf("light").coerceAtLeast(0)
                spinnerTheme.setSelection(idx)
                applyThemeColors("light", view)
            }
        }

        btnThemeSepia.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getReadingTheme(context) != "sepia") {
                SettingsManager.setReadingTheme(context, "sepia")
                val idx = themeKeys.indexOf("sepia").coerceAtLeast(0)
                spinnerTheme.setSelection(idx)
                applyThemeColors("sepia", view)
            }
        }

        btnThemeDark.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getReadingTheme(context) != "dark") {
                SettingsManager.setReadingTheme(context, "dark")
                val idx = themeKeys.indexOf("dark").coerceAtLeast(0)
                spinnerTheme.setSelection(idx)
                applyThemeColors("dark", view)
            }
        }

        // 9. Bedtime Mode Quick Preset Card Click Listener
        val cardBedtimeMode = view.findViewById<androidx.cardview.widget.CardView>(R.id.cardBedtimeMode)
        cardBedtimeMode.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            
            // Apply settings values
            SettingsManager.setReadingTheme(context, "dark")
            SettingsManager.setAmberFilterEnabled(context, true)
            SettingsManager.setAmberFilterIntensity(context, 50)
            SettingsManager.setExtraDimEnabled(context, true)
            SettingsManager.setExtraDimIntensity(context, 30)
            SettingsManager.setSleepTimerEnabled(context, true)
            SettingsManager.setSleepTimerDuration(context, 30)
            
            // Sync local UI controls
            // Theme Spinner
            val darkIdx = themeKeys.indexOf("dark").coerceAtLeast(0)
            spinnerTheme.setSelection(darkIdx)
            applyThemeColors("dark", view)
            
            // Amber Filter Controls
            switchAmberFilter.isChecked = true
            layoutAmberIntensity.visibility = View.VISIBLE
            tvAmberIntensityValue.text = "50%"
            seekBarAmberIntensity.progress = 50
            
            // Extra Dim Controls
            switchExtraDim.isChecked = true
            layoutExtraDimIntensity.visibility = View.VISIBLE
            tvExtraDimIntensityValue.text = "30%"
            seekBarExtraDimIntensity.progress = 30
            
            // Sleep Timer Controls
            switchSleepTimer.isChecked = true
            layoutSleepTimerDuration.visibility = View.VISIBLE
            layoutShakeToExtend.visibility = View.VISIBLE
            tvSleepTimerValue.text = "30 мин"
            seekBarSleepTimer.progress = 30
            
            // Notify user
            CustomToast.show(
                context,
                context.getString(R.string.bedtime_mode_activated),
                Toast.LENGTH_SHORT
            )
        }

        // 7f. Haptic Feedback control
        val switchHapticFeedback = view.findViewById<SwitchCompat>(R.id.switchHapticFeedback)
        switchHapticFeedback.isChecked = SettingsManager.isHapticFeedbackEnabled(context)
        switchHapticFeedback.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != SettingsManager.isHapticFeedbackEnabled(context)) {
                SettingsManager.setHapticFeedbackEnabled(context, isChecked)
                if (isChecked) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
        }

        // Apply initial colors based on current theme
        applyThemeColors(currentTheme, view)
    }

    private fun applyThemeColors(themeKey: String, rootView: View) {
        val context = requireContext()
        
        // The user requested to make all labels/texts/captions inside reading settings a single dark violet color.
        // To ensure high contrast and readability of dark violet text, the settings panel maintains a consistent
        // beautiful light purplish-cream theme, while the controls (seekbars, active rings, switch tracks)
        // match the active theme's accent color.
        val cardBgHex = "#F5F0F8"
        val itemBgHex = "#EAE2F3"
        val dividerHex = "#D2C5E3"
        val textPrimaryHex = "#2A1A36" // Dark violet brand color
        val textSecondaryHex = "#2A1A36" // Also dark violet, as requested

        val accentHex = when (themeKey) {
            "light", "beige" -> "#D35400"
            "sepia", "sepia_contrast" -> "#8E44AD"
            "contrast" -> "#9B59B6"
            "amoled" -> "#D354FF"
            else -> "#9B59B6"
        }

        val cardBgColor = Color.parseColor(cardBgHex)
        val itemBgColor = Color.parseColor(itemBgHex)
        val accentColor = Color.parseColor(accentHex)
        val textPrimaryColor = Color.parseColor(textPrimaryHex)
        val textSecondaryColor = Color.parseColor(textSecondaryHex)
        val dividerColor = Color.parseColor(dividerHex)

        // 1. Root CardView background color morphing
        val cardRoot = rootView.findViewById<androidx.cardview.widget.CardView>(R.id.settingsCardRoot)
        cardRoot?.setCardBackgroundColor(cardBgColor)

        // Bedtime mode card background morphing
        val cardBedtime = rootView.findViewById<androidx.cardview.widget.CardView>(R.id.cardBedtimeMode)
        cardBedtime?.setCardBackgroundColor(itemBgColor)

        // 2. Primary Titles and Text Values
        rootView.findViewById<TextView>(R.id.tvSettingsTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvFontSizeValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvFontWeightValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvSleepTimerTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvSleepTimerValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvShakeToExtendTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvBedtimeTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvHapticFeedbackTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvAutoDiscoveryTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvAutoLightNightTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvAmberFilterTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvAmberIntensityValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvExtraDimTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvExtraDimIntensityValue)?.setTextColor(textPrimaryColor)

        // 3. Secondary Labels and Descriptions
        rootView.findViewById<TextView>(R.id.tvColorSchemeLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvFontLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvFontSizeLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvFontWeightLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvThemeLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvAnimationLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvAlignmentLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvAutoDiscoveryDesc)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvAutoLightNightDesc)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvAmberFilterDesc)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvAmberIntensityLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvExtraDimDesc)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvExtraDimIntensityLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvSleepTimerDesc)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvSleepTimerDurationLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvShakeToExtendDesc)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvBedtimeDesc)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvHapticFeedbackDesc)?.setTextColor(textSecondaryColor)

        // 4. Content Dividers
        rootView.findViewById<View>(R.id.dividerTop)?.setBackgroundColor(dividerColor)
        rootView.findViewById<View>(R.id.dividerMiddle)?.setBackgroundColor(dividerColor)

        // 5. Navigation/Close image button tinting
        val btnClose = rootView.findViewById<ImageButton>(R.id.btnClose)
        btnClose?.imageTintList = ColorStateList.valueOf(textPrimaryColor)

        // 6. Font size, font weight & line spacing SeekBars coloring
        val seekBarFontSize = rootView.findViewById<SeekBar>(R.id.seekBarFontSize)
        val seekBarFontWeight = rootView.findViewById<SeekBar>(R.id.seekBarFontWeight)
        val seekBarAmberIntensity = rootView.findViewById<SeekBar>(R.id.seekBarAmberIntensity)
        val seekBarExtraDimIntensity = rootView.findViewById<SeekBar>(R.id.seekBarExtraDimIntensity)
        val seekBarSleepTimer = rootView.findViewById<SeekBar>(R.id.seekBarSleepTimer)
        seekBarFontSize?.progressTintList = ColorStateList.valueOf(accentColor)
        seekBarFontSize?.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBarFontWeight?.progressTintList = ColorStateList.valueOf(accentColor)
        seekBarFontWeight?.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBarAmberIntensity?.progressTintList = ColorStateList.valueOf(accentColor)
        seekBarAmberIntensity?.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBarExtraDimIntensity?.progressTintList = ColorStateList.valueOf(accentColor)
        seekBarExtraDimIntensity?.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBarSleepTimer?.progressTintList = ColorStateList.valueOf(accentColor)
        seekBarSleepTimer?.thumbTintList = ColorStateList.valueOf(accentColor)

        // 7. Auto-discovery SwitchCompat coloring
        val switchAutoDiscovery = rootView.findViewById<SwitchCompat>(R.id.switchAutoDiscovery)
        switchAutoDiscovery?.trackTintList = ColorStateList.valueOf(accentColor)
        switchAutoDiscovery?.thumbTintList = ColorStateList.valueOf(textPrimaryColor)

        val switchAutoLightNight = rootView.findViewById<SwitchCompat>(R.id.switchAutoLightNight)
        switchAutoLightNight?.trackTintList = ColorStateList.valueOf(accentColor)
        switchAutoLightNight?.thumbTintList = ColorStateList.valueOf(textPrimaryColor)

        val switchAmberFilter = rootView.findViewById<SwitchCompat>(R.id.switchAmberFilter)
        switchAmberFilter?.trackTintList = ColorStateList.valueOf(accentColor)
        switchAmberFilter?.thumbTintList = ColorStateList.valueOf(textPrimaryColor)

        val switchExtraDim = rootView.findViewById<SwitchCompat>(R.id.switchExtraDim)
        switchExtraDim?.trackTintList = ColorStateList.valueOf(accentColor)
        switchExtraDim?.thumbTintList = ColorStateList.valueOf(textPrimaryColor)

        val switchSleepTimer = rootView.findViewById<SwitchCompat>(R.id.switchSleepTimer)
        switchSleepTimer?.trackTintList = ColorStateList.valueOf(accentColor)
        switchSleepTimer?.thumbTintList = ColorStateList.valueOf(textPrimaryColor)

        val switchShakeToExtend = rootView.findViewById<SwitchCompat>(R.id.switchShakeToExtend)
        switchShakeToExtend?.trackTintList = ColorStateList.valueOf(accentColor)
        switchShakeToExtend?.thumbTintList = ColorStateList.valueOf(textPrimaryColor)

        // 8. Custom Theme Circle selection ring highlights & colors
        val ringLight = rootView.findViewById<View>(R.id.ringThemeLight)
        val ringSepia = rootView.findViewById<View>(R.id.ringThemeSepia)
        val ringDark = rootView.findViewById<View>(R.id.ringThemeDark)

        val isLightActive = themeKey == "light" || themeKey == "beige"
        val isSepiaActive = themeKey == "sepia" || themeKey == "sepia_contrast"
        val isDarkActive = themeKey == "dark" || themeKey == "contrast" || themeKey == "amoled"

        ringLight?.visibility = if (isLightActive) View.VISIBLE else View.INVISIBLE
        ringSepia?.visibility = if (isSepiaActive) View.VISIBLE else View.INVISIBLE
        ringDark?.visibility = if (isDarkActive) View.VISIBLE else View.INVISIBLE

        ringLight?.backgroundTintList = ColorStateList.valueOf(accentColor)
        ringSepia?.backgroundTintList = ColorStateList.valueOf(accentColor)
        ringDark?.backgroundTintList = ColorStateList.valueOf(accentColor)

        // 9. Programmatic styled background drawable for the Spinners to prevent XML color collision
        val spinnerBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(itemBgColor)
            setStroke(2, accentColor)
        }
        rootView.findViewById<Spinner>(R.id.spinnerFont)?.background = spinnerBg
        rootView.findViewById<Spinner>(R.id.spinnerTheme)?.background = spinnerBg
        rootView.findViewById<Spinner>(R.id.spinnerAnimation)?.background = spinnerBg
        rootView.findViewById<Spinner>(R.id.spinnerAlignment)?.background = spinnerBg

        // 10. Programmatic background and text colors for quick font switching buttons
        val currentFont = SettingsManager.getFontFamily(context)
        val isSansSelected = currentFont == "Roboto"
        val isSerifSelected = currentFont == "Georgia" || currentFont == "Times New Roman" || currentFont == "Merriweather" || currentFont == "Lora" || currentFont == "EB Garamond" || currentFont == "Literata"
        val isMonoSelected = currentFont == "Monospace"

        val btnFontSans = rootView.findViewById<TextView>(R.id.btnFontSans)
        val btnFontSerif = rootView.findViewById<TextView>(R.id.btnFontSerif)
        val btnFontMono = rootView.findViewById<TextView>(R.id.btnFontMono)

        fun createFontButtonBg(selected: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                if (selected) {
                    setColor(accentColor)
                } else {
                    setColor(itemBgColor)
                    setStroke(2, dividerColor)
                }
            }
        }

        btnFontSans?.background = createFontButtonBg(isSansSelected)
        btnFontSans?.setTextColor(if (isSansSelected) cardBgColor else textSecondaryColor)

        btnFontSerif?.background = createFontButtonBg(isSerifSelected)
        btnFontSerif?.setTextColor(if (isSerifSelected) cardBgColor else textSecondaryColor)

        btnFontMono?.background = createFontButtonBg(isMonoSelected)
        btnFontMono?.setTextColor(if (isMonoSelected) cardBgColor else textSecondaryColor)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val metrics = resources.displayMetrics
            
            // Width: 55% of screen size, height: wrap content but maximum 40% of screen height
            val width = (metrics.widthPixels * 0.55).toInt().coerceAtLeast(300)

            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.TOP or Gravity.END)

            // Layout parameter offsets
            val params = window.attributes
            params.x = 24  // margin from right edge
            params.y = 120 // margin from top edge (to avoid overlap with action bar/cutouts)
            window.attributes = params

            // Apply custom enter/exit slide animations from top-right
            window.setWindowAnimations(R.style.SettingsDialogAnimation)
        }
    }

    private class SettingsSpinnerAdapter<T>(
        context: android.content.Context,
        objects: List<T>
    ) : ArrayAdapter<T>(context, R.layout.spinner_item, objects) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            if (view is TextView) {
                view.setTextColor(Color.parseColor("#2A1A36")) // Dark violet text
                view.setBackgroundColor(Color.TRANSPARENT)
            }
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            if (view is TextView) {
                view.setTextColor(Color.parseColor("#2A1A36")) // Dark violet text
                view.setBackgroundColor(Color.parseColor("#EAE2F3")) // Match item Bg color
            }
            return view
        }
    }
}
