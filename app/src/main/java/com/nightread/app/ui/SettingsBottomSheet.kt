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
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
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
        val fontOptions = listOf("Roboto", "Times New Roman", "Georgia", "Merriweather", "OpenDyslexic", "Monospace")
        val spinnerFont = view.findViewById<Spinner>(R.id.spinnerFont)
        val fontAdapter = ArrayAdapter(context, R.layout.spinner_item, fontOptions).apply {
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
                    applyThemeColors(SettingsManager.getTheme(context), this@SettingsBottomSheet.requireView())
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
                applyThemeColors(SettingsManager.getTheme(context), view)
            }
        }

        btnFontSerif?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getFontFamily(context) != "Georgia") {
                SettingsManager.setFontFamily(context, "Georgia")
                val idx = fontOptions.indexOf("Georgia").coerceAtLeast(0)
                spinnerFont.setSelection(idx)
                applyThemeColors(SettingsManager.getTheme(context), view)
            }
        }

        btnFontMono?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getFontFamily(context) != "Monospace") {
                SettingsManager.setFontFamily(context, "Monospace")
                val idx = fontOptions.indexOf("Monospace").coerceAtLeast(0)
                spinnerFont.setSelection(idx)
                applyThemeColors(SettingsManager.getTheme(context), view)
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
        val themeKeys = listOf("light", "dark", "sepia", "sepia_contrast", "contrast", "beige")
        val themeNames = mapOf(
            "light" to "День",
            "dark" to "Ночь",
            "sepia" to "Сепия",
            "sepia_contrast" to "Сепия контраст",
            "contrast" to "Контраст",
            "beige" to "Бежевый"
        )
        val themeDisplayNames = themeKeys.map { themeNames[it] ?: it }
        val spinnerTheme = view.findViewById<Spinner>(R.id.spinnerTheme)
        val themeAdapter = ArrayAdapter(context, R.layout.spinner_item, themeDisplayNames).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinnerTheme.adapter = themeAdapter
        
        val currentTheme = SettingsManager.getTheme(context)
        val themeIdx = themeKeys.indexOf(currentTheme).coerceAtLeast(0)
        spinnerTheme.setSelection(themeIdx)
        spinnerTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKey = themeKeys[position]
                if (selectedKey != SettingsManager.getTheme(context)) {
                    SettingsManager.setTheme(context, selectedKey)
                    applyThemeColors(selectedKey, this@SettingsBottomSheet.requireView())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 6. Line Spacing (SeekBar)
        val tvLineSpacingValue = view.findViewById<TextView>(R.id.tvLineSpacingValue)
        val seekBarLineSpacing = view.findViewById<SeekBar>(R.id.seekBarLineSpacing)
        val currentLineSpacing = SettingsManager.getLineSpacing(context)
        tvLineSpacingValue.text = String.format("%.2f", currentLineSpacing)
        seekBarLineSpacing.progress = (((currentLineSpacing - 1.0f) * 10).toInt()).coerceIn(0, 10)
        var lastLineSpacingProgress = seekBarLineSpacing.progress
        seekBarLineSpacing.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSpacing = 1.0f + (progress / 10.0f)
                tvLineSpacingValue.text = String.format("%.2f", newSpacing)
                if (fromUser) {
                    SettingsManager.setLineSpacing(context, newSpacing)
                    if (progress != lastLineSpacingProgress) {
                        seekBar?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        lastLineSpacingProgress = progress
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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

        // 8. Color Scheme Circle Buttons Hookup
        val btnThemeLight = view.findViewById<FrameLayout>(R.id.btnThemeLight)
        val btnThemeSepia = view.findViewById<FrameLayout>(R.id.btnThemeSepia)
        val btnThemeDark = view.findViewById<FrameLayout>(R.id.btnThemeDark)

        btnThemeLight.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getTheme(context) != "light") {
                SettingsManager.setTheme(context, "light")
                val idx = themeKeys.indexOf("light").coerceAtLeast(0)
                spinnerTheme.setSelection(idx)
                applyThemeColors("light", view)
            }
        }

        btnThemeSepia.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getTheme(context) != "sepia") {
                SettingsManager.setTheme(context, "sepia")
                val idx = themeKeys.indexOf("sepia").coerceAtLeast(0)
                spinnerTheme.setSelection(idx)
                applyThemeColors("sepia", view)
            }
        }

        btnThemeDark.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (SettingsManager.getTheme(context) != "dark") {
                SettingsManager.setTheme(context, "dark")
                val idx = themeKeys.indexOf("dark").coerceAtLeast(0)
                spinnerTheme.setSelection(idx)
                applyThemeColors("dark", view)
            }
        }

        // Apply initial colors based on current theme
        applyThemeColors(currentTheme, view)
    }

    private fun applyThemeColors(themeKey: String, rootView: View) {
        val context = requireContext()
        
        // Define color scheme values
        val cardBgHex: String
        val itemBgHex: String
        val accentHex: String
        val textPrimaryHex: String
        val textSecondaryHex: String
        val dividerHex: String

        when (themeKey) {
            "light", "beige" -> {
                cardBgHex = "#FAF6F0"
                itemBgHex = "#EFE9E2"
                accentHex = "#D35400"
                textPrimaryHex = "#2C3E50"
                textSecondaryHex = "#7F8C8D"
                dividerHex = "#E0D5C1"
            }
            "sepia", "sepia_contrast" -> {
                cardBgHex = "#F4ECD8"
                itemBgHex = "#EADCB9"
                accentHex = "#8E44AD"
                textPrimaryHex = "#5B3A29"
                textSecondaryHex = "#8F7365"
                dividerHex = "#D5C5B5"
            }
            "contrast" -> {
                cardBgHex = "#000000"
                itemBgHex = "#1A1A1A"
                accentHex = "#FFFFFF"
                textPrimaryHex = "#FFFFFF"
                textSecondaryHex = "#AAAAAA"
                dividerHex = "#333333"
            }
            else -> { // "dark" or default
                cardBgHex = "#1A0D2A"
                itemBgHex = "#2A1A3E"
                accentHex = "#9B59B6"
                textPrimaryHex = "#E8D8F0"
                textSecondaryHex = "#B8A0C8"
                dividerHex = "#3A2A4E"
            }
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

        // 2. Primary Titles and Text Values
        rootView.findViewById<TextView>(R.id.tvSettingsTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvFontSizeValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvFontWeightValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvLineSpacingValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvAutoDiscoveryTitle)?.setTextColor(textPrimaryColor)

        // 3. Secondary Labels and Descriptions
        rootView.findViewById<TextView>(R.id.tvColorSchemeLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvFontLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvFontSizeLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvFontWeightLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvThemeLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvLineSpacingLabel)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvAutoDiscoveryDesc)?.setTextColor(textSecondaryColor)

        // 4. Content Dividers
        rootView.findViewById<View>(R.id.dividerTop)?.setBackgroundColor(dividerColor)
        rootView.findViewById<View>(R.id.dividerMiddle)?.setBackgroundColor(dividerColor)

        // 5. Navigation/Close image button tinting
        val btnClose = rootView.findViewById<ImageButton>(R.id.btnClose)
        btnClose?.imageTintList = ColorStateList.valueOf(textPrimaryColor)

        // 6. Font size, font weight & line spacing SeekBars coloring
        val seekBarFontSize = rootView.findViewById<SeekBar>(R.id.seekBarFontSize)
        val seekBarFontWeight = rootView.findViewById<SeekBar>(R.id.seekBarFontWeight)
        val seekBarLineSpacing = rootView.findViewById<SeekBar>(R.id.seekBarLineSpacing)
        seekBarFontSize?.progressTintList = ColorStateList.valueOf(accentColor)
        seekBarFontSize?.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBarFontWeight?.progressTintList = ColorStateList.valueOf(accentColor)
        seekBarFontWeight?.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBarLineSpacing?.progressTintList = ColorStateList.valueOf(accentColor)
        seekBarLineSpacing?.thumbTintList = ColorStateList.valueOf(accentColor)

        // 7. Auto-discovery SwitchCompat coloring
        val switchAutoDiscovery = rootView.findViewById<SwitchCompat>(R.id.switchAutoDiscovery)
        switchAutoDiscovery?.trackTintList = ColorStateList.valueOf(accentColor)
        switchAutoDiscovery?.thumbTintList = ColorStateList.valueOf(textPrimaryColor)

        // 8. Custom Theme Circle selection ring highlights & colors
        val ringLight = rootView.findViewById<View>(R.id.ringThemeLight)
        val ringSepia = rootView.findViewById<View>(R.id.ringThemeSepia)
        val ringDark = rootView.findViewById<View>(R.id.ringThemeDark)

        val isLightActive = themeKey == "light" || themeKey == "beige"
        val isSepiaActive = themeKey == "sepia" || themeKey == "sepia_contrast"
        val isDarkActive = themeKey == "dark" || themeKey == "contrast"

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

        // 10. Programmatic background and text colors for quick font switching buttons
        val currentFont = SettingsManager.getFontFamily(context)
        val isSansSelected = currentFont == "Roboto"
        val isSerifSelected = currentFont == "Georgia" || currentFont == "Times New Roman" || currentFont == "Merriweather"
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
            
            // Width: 50% of screen size, height: wrap content but maximum 40% of screen height
            val width = (metrics.widthPixels * 0.50).toInt().coerceAtLeast(300)

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
}
