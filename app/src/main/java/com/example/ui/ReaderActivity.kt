package com.example.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ReaderActivity : FragmentActivity() {
    lateinit var viewModel: ReaderViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var rootLayout: FrameLayout
    private var isSystemUiVisible = false

    // Control Overlays
    private lateinit var topControlBar: LinearLayout
    private lateinit var bottomControlBar: LinearLayout
    private lateinit var bookTitleText: TextView
    private lateinit var bookAuthorText: TextView
    private lateinit var chapterInfoText: TextView

    // Settings Panel Container Overlay
    private lateinit var settingsPanelContainer: FrameLayout
    private var isSettingsPanelVisible = false

    // Brightness overlay views
    private lateinit var brightnessOverlay: LinearLayout
    private lateinit var brightnessProgressBar: ProgressBar
    private lateinit var brightnessPercentText: TextView

    // Brightness gesture state
    private var isDraggingBrightness = false
    private var maybeDraggingBrightness = false
    private var startX = 0f
    private var startY = 0f
    private var initialBrightness = 0.5f
    private val touchSlop = 10f
    private var fadeJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Edge-To-Edge rendering and display cutout setup
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Set layout flags as requested to let the window content stretch under status bars natively
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        val bookId = intent.getIntExtra("BOOK_ID", -1)
        if (bookId == -1) {
            finish()
            return
        }

        val vm: ReaderViewModel by viewModels()
        viewModel = vm
        viewModel.loadBook(bookId)

        // 2. Restore saved brightness on startup
        val savedBrightness = getSharedPreferences("reader_prefs", MODE_PRIVATE)
            .getFloat("saved_brightness", -1f)
        if (savedBrightness >= 0f) {
            val lp = window.attributes
            lp.screenBrightness = savedBrightness
            window.attributes = lp
        }

        // 3. Programmatic Layout Assembly
        val density = resources.displayMetrics.density
        rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPageTransformer(BookPageTransformer())
        }
        rootLayout.addView(viewPager)

        // Bottom Page Indicator (centered above bottom of screen, overlaying the book text)
        pageIndicator = TextView(this).apply {
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (12 * density).toInt()
            }
            layoutParams = lp
            textSize = 12f
            setTextColor(Color.GRAY)
        }
        rootLayout.addView(pageIndicator)

        // --- BRIGHTNESS GESTURE HUD OVERLAY ---
        brightnessOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt())
            alpha = 0f
            visibility = View.GONE
            
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#E61A1A1A"))
                cornerRadius = 16 * density
            }
            background = shape
            
            val lp = FrameLayout.LayoutParams(
                (180 * density).toInt(),
                (110 * density).toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
        }

        val brightnessLabel = TextView(this).apply {
            text = "Яркость"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        brightnessOverlay.addView(brightnessLabel)

        brightnessProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 50
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        brightnessOverlay.addView(brightnessProgressBar)

        brightnessPercentText = TextView(this).apply {
            text = "50%"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (6 * density).toInt(), 0, 0)
        }
        brightnessOverlay.addView(brightnessPercentText)
        rootLayout.addView(brightnessOverlay)


        // --- TOP CONTROL BAR OVERLAY ---
        topControlBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            alpha = 0f
            
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#E6232323")) // Dark gray semi-translucent bar
            }
            background = shape

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
            layoutParams = lp
        }

        // Apply WindowInsets dynamically to make topControlBar start below cutout/notch
        ViewCompat.setOnApplyWindowInsetsListener(topControlBar) { v, insets ->
            val systemBars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout
            val cutoutHeight = cutout?.safeInsetTop ?: systemBars.top
            val currentDensity = v.resources.displayMetrics.density
            v.setPadding(
                (12 * currentDensity).toInt(),
                cutoutHeight + (8 * currentDensity).toInt(),
                (12 * currentDensity).toInt(),
                (12 * currentDensity).toInt()
            )
            insets
        }

        val backButton = TextView(this).apply {
            text = "←"
            setTextColor(Color.WHITE)
            textSize = 24f
            setPadding((10 * density).toInt(), (4 * density).toInt(), (14 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                finish()
            }
        }
        topControlBar.addView(backButton)

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        bookTitleText = TextView(this).apply {
            text = "Загрузка..."
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(bookTitleText)

        bookAuthorText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#FF4FC3F7")) // Beautiful cyan
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(bookAuthorText)

        chapterInfoText = TextView(this).apply {
            text = ""
            setTextColor(Color.LTGRAY)
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textContainer.addView(chapterInfoText)
        topControlBar.addView(textContainer)

        val iconsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val newTag = TextView(this).apply {
            text = "NEW  ●"
            setTextColor(Color.parseColor("#FF4FC3F7"))
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((6 * density).toInt(), 0, (8 * density).toInt(), 0)
        }
        iconsLayout.addView(newTag)

        val speakerButton = TextView(this).apply {
            text = "🔊"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                android.widget.Toast.makeText(this@ReaderActivity, "Синтез речи (TTS) запущен", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        iconsLayout.addView(speakerButton)

        val searchButton = TextView(this).apply {
            text = "🔍"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                android.widget.Toast.makeText(this@ReaderActivity, "Поиск", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        iconsLayout.addView(searchButton)

        val listButton = TextView(this).apply {
            text = "≡"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                android.widget.Toast.makeText(this@ReaderActivity, "Содержание книги", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        iconsLayout.addView(listButton)

        val settingsButton = TextView(this).apply {
            text = "⚙"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                if (isSettingsPanelVisible) {
                    hideSettingsPanel()
                } else {
                    showSettingsPanel()
                }
            }
        }
        iconsLayout.addView(settingsButton)

        val moreButton = TextView(this).apply {
            text = "⋮"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding((8 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                android.widget.Toast.makeText(this@ReaderActivity, "Дополнительно", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        iconsLayout.addView(moreButton)

        topControlBar.addView(iconsLayout)
        rootLayout.addView(topControlBar)


        // --- BOTTOM PROGRESS BAR OVERLAY (SEEK BAR & QUICK ACTION BUTTONS) ---
        bottomControlBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            alpha = 0f
            
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#E6232323")) // Translucent dark grey background matching top bar
            }
            background = shape

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            layoutParams = lp
        }

        // Apply safe WindowInsets bottom margins to bottomControlBar
        ViewCompat.setOnApplyWindowInsetsListener(bottomControlBar) { v, insets ->
            val systemBars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val currentDensity = v.resources.displayMetrics.density
            v.setPadding(
                (16 * currentDensity).toInt(),
                (12 * currentDensity).toInt(),
                (16 * currentDensity).toInt(),
                systemBars.bottom + (12 * currentDensity).toInt()
            )
            insets
        }

        val bottomProgressText = TextView(this).apply {
            text = "1 из 1"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (6 * density).toInt())
        }
        bottomControlBar.addView(bottomProgressText)

        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val syncButton = TextView(this).apply {
            text = "↻"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding((8 * density).toInt(), (4 * density).toInt(), (12 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                android.widget.Toast.makeText(this@ReaderActivity, "Прогресс чтения синхронизирован", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        progressRow.addView(syncButton)

        val pageSeekBar = android.widget.SeekBar(this).apply {
            max = 100
            progress = 0
            val activeColor = Color.parseColor("#FF4FC3F7")
            progressTintList = android.content.res.ColorStateList.valueOf(activeColor)
            thumbTintList = android.content.res.ColorStateList.valueOf(activeColor)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#40FFFFFF"))
            
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        viewPager.setCurrentItem(progress, false)
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        progressRow.addView(pageSeekBar)

        val bookmarkButton = TextView(this).apply {
            text = "📌"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding((12 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            setOnClickListener {
                android.widget.Toast.makeText(this@ReaderActivity, "Закладка сохранена!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        progressRow.addView(bookmarkButton)
        bottomControlBar.addView(progressRow)

        rootLayout.addView(bottomControlBar)


        // --- SETTINGS OVERLAY CARD PANEL ---
        settingsPanelContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            alpha = 0f
            setOnClickListener {
                hideSettingsPanel()
            }
        }

        val settingsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt(), (18 * density).toInt())
            
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#F2212121")) // Dark grey translucent
                cornerRadius = 12 * density
                setStroke((1 * density).toInt(), Color.parseColor("#26FFFFFF"))
            }
            background = shape
            
            val lp = FrameLayout.LayoutParams(
                (320 * density).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
            
            setOnClickListener { /* Consume clicks to prevent dismissing overlay */ }
        }

        // Header Title
        val settingsHeader = TextView(this).apply {
            text = "НАСТРОЙКИ EPUB, FB2, MOBI, DOC, DOCX, RTF, TXT И CHM"
            setTextColor(Color.parseColor("#FF4FC3F7"))
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, (14 * density).toInt())
        }
        settingsCard.addView(settingsHeader)

        // Dropdown Builders helper
        fun createDropdown(
            labelText: String,
            currentValueFlow: kotlinx.coroutines.flow.StateFlow<String>,
            options: List<String>,
            onOptionSelected: (String) -> Unit
        ): View {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (12 * density).toInt())
                }
            }
            
            val label = TextView(this).apply {
                text = labelText.uppercase()
                setTextColor(Color.parseColor("#FF90A4AE"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, (4 * density).toInt())
            }
            container.addView(label)
            
            val selectorBox = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                
                val shape = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AFFFFFF"))
                    cornerRadius = 6 * density
                }
                background = shape
            }
            
            val selectedText = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            selectorBox.addView(selectedText)
            
            val arrow = TextView(this).apply {
                text = "▼"
                setTextColor(Color.WHITE)
                textSize = 9f
            }
            selectorBox.addView(arrow)
            
            lifecycleScope.launch {
                currentValueFlow.collect { value ->
                    selectedText.text = value
                }
            }
            
            selectorBox.setOnClickListener {
                val popupMenu = androidx.appcompat.widget.PopupMenu(this@ReaderActivity, selectorBox)
                options.forEach { option ->
                    popupMenu.menu.add(option)
                }
                popupMenu.setOnMenuItemClickListener { menuItem ->
                    onOptionSelected(menuItem.title.toString())
                    true
                }
                popupMenu.show()
            }
            
            container.addView(selectorBox)
            return container
        }

        // Counter builders helper for size / spacing
        fun createCounter(
            labelText: String,
            valueFlow: kotlinx.coroutines.flow.StateFlow<String>,
            onMinus: () -> Unit,
            onPlus: () -> Unit
        ): View {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (12 * density).toInt())
                }
            }
            
            val label = TextView(this).apply {
                text = labelText.uppercase()
                setTextColor(Color.parseColor("#FF90A4AE"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, (4 * density).toInt())
            }
            container.addView(label)
            
            val counterBox = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((4 * density).toInt(), (2 * density).toInt(), (4 * density).toInt(), (2 * density).toInt())
                
                val shape = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AFFFFFF"))
                    cornerRadius = 6 * density
                }
                background = shape
            }
            
            val minusButton = TextView(this).apply {
                text = "⊖"
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding((16 * density).toInt(), (4 * density).toInt(), (16 * density).toInt(), (4 * density).toInt())
                setOnClickListener { onMinus() }
            }
            counterBox.addView(minusButton)
            
            val valueText = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            counterBox.addView(valueText)
            
            val plusButton = TextView(this).apply {
                text = "⊕"
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding((16 * density).toInt(), (4 * density).toInt(), (16 * density).toInt(), (4 * density).toInt())
                setOnClickListener { onPlus() }
            }
            counterBox.addView(plusButton)
            
            lifecycleScope.launch {
                valueFlow.collect { value ->
                    valueText.text = value
                }
            }
            
            container.addView(counterBox)
            return container
        }

        // Toggles / switches builder helper
        fun createToggle(
            labelText: String,
            stateFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
            onCheckedChange: (Boolean) -> Unit
        ): View {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (6 * density).toInt(), 0, (6 * density).toInt())
                }
            }
            
            val label = TextView(this).apply {
                text = labelText
                setTextColor(Color.WHITE)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            container.addView(label)
            
            val toggle = androidx.appcompat.widget.SwitchCompat(this@ReaderActivity).apply {
                val activeColor = Color.parseColor("#FF4FC3F7")
                thumbTintList = android.content.res.ColorStateList.valueOf(activeColor)
                trackTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            }
            toggle.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(isChecked)
            }
            
            lifecycleScope.launch {
                stateFlow.collect { isChecked ->
                    if (toggle.isChecked != isChecked) {
                        toggle.isChecked = isChecked
                    }
                }
            }
            container.addView(toggle)
            return container
        }

        // Add settings components to card
        // 1. ЛИСТАТЬ СТРАНИЦЫ
        settingsCard.addView(createDropdown(
            "Листать страницы",
            viewModel.scrollDirectionState,
            listOf("Горизонтально", "Вертикально")
        ) { selected ->
            viewModel.setScrollDirection(selected)
        })

        // 2. ЦВЕТОВАЯ СХЕМА
        val mappedThemeFlow = MutableStateFlow("День")
        lifecycleScope.launch {
            viewModel.themeState.collect { internalTheme ->
                mappedThemeFlow.value = when (internalTheme) {
                    "night" -> "Ночь"
                    "sepia" -> "Сепия"
                    else -> "День"
                }
            }
        }
        settingsCard.addView(createDropdown(
            "Цветовая схема",
            mappedThemeFlow,
            listOf("День", "Ночь", "Сепия")
        ) { selected ->
            val internalTheme = when (selected) {
                "Ночь" -> "night"
                "Сепия" -> "sepia"
                else -> "day"
            }
            viewModel.setTheme(internalTheme)
        })

        // 3. ШРИФТ
        settingsCard.addView(createDropdown(
            "Шрифт",
            viewModel.fontFamilyState,
            listOf("Merriweather", "Roboto", "Sans Serif", "Serif", "Monospace")
        ) { selected ->
            viewModel.setFontFamily(selected)
        })

        // 4. РАЗМЕР ШРИФТА
        val fontSizeStringFlow = MutableStateFlow("18")
        lifecycleScope.launch {
            viewModel.fontSizeState.collect { size ->
                fontSizeStringFlow.value = String.format("%.0f", size)
            }
        }
        settingsCard.addView(createCounter(
            "Размер шрифта",
            fontSizeStringFlow,
            onMinus = { viewModel.changeFontSize(-1f) },
            onPlus = { viewModel.changeFontSize(1f) }
        ))

        // 5. ЖИРНОСТЬ ШРИФТА
        val fontWeightContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (12 * density).toInt())
            }
        }
        val fontWeightLabel = TextView(this).apply {
            text = "ЖИРНОСТЬ ШРИФТА"
            setTextColor(Color.parseColor("#FF90A4AE"))
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        fontWeightContainer.addView(fontWeightLabel)
        val weightSeekBar = android.widget.SeekBar(this).apply {
            max = 1
            progress = 0
            val activeColor = Color.parseColor("#FF4FC3F7")
            progressTintList = android.content.res.ColorStateList.valueOf(activeColor)
            thumbTintList = android.content.res.ColorStateList.valueOf(activeColor)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        viewModel.setFontWeight(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
        lifecycleScope.launch {
            viewModel.fontWeightState.collect { weight ->
                weightSeekBar.progress = weight
            }
        }
        fontWeightContainer.addView(weightSeekBar)
        settingsCard.addView(fontWeightContainer)

        // 6. МЕЖДУСТРОЧНЫЙ ИНТЕРВАЛ
        val spacingStringFlow = MutableStateFlow("1.4")
        lifecycleScope.launch {
            viewModel.lineSpacingState.collect { valSpacing ->
                // Represent as percentage as in the screenshot, e.g. "95%"
                val percentVal = (valSpacing * 100 / 1.4).toInt()
                spacingStringFlow.value = "$percentVal%"
            }
        }
        settingsCard.addView(createCounter(
            "Междустрочный интервал",
            spacingStringFlow,
            onMinus = { viewModel.changeLineSpacing(-0.1f) },
            onPlus = { viewModel.changeLineSpacing(0.1f) }
        ))

        // 7. ВЫРАВНИВАТЬ ТЕКСТ ПО
        val alignmentStringFlow = MutableStateFlow("Ширине + Перенос слов")
        lifecycleScope.launch {
            viewModel.fontAlignmentState.collect { align ->
                alignmentStringFlow.value = when (align) {
                    "left" -> "По левому краю"
                    "right" -> "По правому краю"
                    "center" -> "По центру"
                    else -> "Ширине + Перенос слов"
                }
            }
        }
        settingsCard.addView(createDropdown(
            "Выравнивать текст по",
            alignmentStringFlow,
            listOf("Ширине + Перенос слов", "По левому краю", "По правому краю", "По центру")
        ) { selected ->
            val internalAlign = when (selected) {
                "По левому краю" -> "left"
                "По правому краю" -> "right"
                "По центру" -> "center"
                else -> "justify"
            }
            viewModel.setFontAlignment(internalAlign)
        })

        // 8. Две страницы в альбомной ориентации экрана
        settingsCard.addView(createToggle(
            "Две страницы в альбомной ориентации экрана",
            viewModel.twoPagesLandscapeState
        ) { checked ->
            viewModel.setTwoPagesLandscape(checked)
        })

        // 9. Поля страниц
        settingsCard.addView(createToggle(
            "Поля страниц",
            viewModel.pageMarginsState
        ) { checked ->
            viewModel.setPageMargins(checked)
        })

        // 10. Кнопка ОБЩИЕ НАСТРОЙКИ
        val generalSettingsBtn = TextView(this).apply {
            text = "ОБЩИЕ НАСТРОЙКИ"
            setTextColor(Color.parseColor("#FF90A4AE"))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, (14 * density).toInt(), 0, 0)
            setOnClickListener {
                android.widget.Toast.makeText(this@ReaderActivity, "Общие настройки", android.widget.Toast.LENGTH_SHORT).show()
                hideSettingsPanel()
            }
        }
        settingsCard.addView(generalSettingsBtn)

        settingsPanelContainer.addView(settingsCard)
        rootLayout.addView(settingsPanelContainer)

        setContentView(rootLayout)

        // Hide bars initially
        hideSystemUi()

        // 4. Dynamic Dimensions listener to send boundaries to pagination engine
        rootLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            recalculateDimensions()
        }

        // Trigger dynamic dimension updates when settings affect padding
        lifecycleScope.launch {
            viewModel.pageMarginsState.collect { _ ->
                recalculateDimensions()
            }
        }

        // 5. Collect ViewModel States to update the UI
        lifecycleScope.launch {
            viewModel.bookState.collect { book ->
                book?.let {
                    bookTitleText.text = it.title
                    bookAuthorText.text = it.author
                }
            }
        }

        lifecycleScope.launch {
            viewModel.pagesState.collect { pages ->
                if (pages.isNotEmpty()) {
                    val adapter = ReaderPagerAdapter(this@ReaderActivity, pages)
                    viewPager.adapter = adapter
                    viewPager.setCurrentItem(viewModel.currentPage.value, false)
                    pageSeekBar.max = (pages.size - 1).coerceAtLeast(0)
                    pageSeekBar.progress = viewModel.currentPage.value
                    bottomProgressText.text = "${viewModel.currentPage.value + 1} из ${pages.size}"
                    updatePageIndicator(viewModel.currentPage.value, pages.size)
                    updateChapterInfo(viewModel.currentPage.value, pages.size)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentPage.collect { page ->
                if (viewPager.currentItem != page) {
                    viewPager.setCurrentItem(page, false)
                }
                pageSeekBar.progress = page
                val total = viewModel.pagesState.value.size
                if (total > 0) {
                    bottomProgressText.text = "${page + 1} из $total"
                    updatePageIndicator(page, total)
                    updateChapterInfo(page, total)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.themeState.collect { theme ->
                applyTheme(theme)
            }
        }

        lifecycleScope.launch {
            viewModel.scrollDirectionState.collect { direction ->
                viewPager.orientation = if (direction == "Вертикально") {
                    ViewPager2.ORIENTATION_VERTICAL
                } else {
                    ViewPager2.ORIENTATION_HORIZONTAL
                }
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setCurrentPage(position)
            }
        })
    }

    private fun recalculateDimensions() {
        val screenWidth = rootLayout.width
        val screenHeight = rootLayout.height
        if (screenWidth > 0 && screenHeight > 0) {
            val currentDensity = resources.displayMetrics.density
            val isMarginsEnabled = viewModel.pageMarginsState.value
            val sideMarginDp = if (isMarginsEnabled) 16 else 6
            val bottomMarginDp = if (isMarginsEnabled) 48 else 32
            
            val topPadding = (54 * currentDensity).toInt()
            val leftPadding = (sideMarginDp * currentDensity).toInt()
            val rightPadding = (sideMarginDp * currentDensity).toInt()
            val bottomPadding = (bottomMarginDp * currentDensity).toInt()
            
            val availableW = screenWidth - leftPadding - rightPadding
            val availableH = screenHeight - topPadding - bottomPadding
            
            if (availableW > 0 && availableH > 0) {
                viewModel.updateDimensions(availableW, availableH, currentDensity)
            }
        }
    }

    private fun updatePageIndicator(currentPage: Int, totalPages: Int) {
        pageIndicator.text = "${currentPage + 1} из $totalPages"
    }

    private fun updateChapterInfo(currentPage: Int, totalPages: Int) {
        val book = viewModel.bookState.value
        val seriesOrCat = book?.series ?: book?.category ?: "Книга"
        chapterInfoText.text = "$seriesOrCat  . - стр. ${currentPage + 1} / $totalPages"
    }

    private fun applyTheme(theme: String) {
        val colorHex = when (theme) {
            "night" -> "#1a1a1a"
            "sepia" -> "#f4ecd8"
            else -> "#f5f0e8"
        }
        val textColorHex = when (theme) {
            "night" -> "#80e0e0e0"
            "sepia" -> "#805c4033"
            else -> "#803a3a3a"
        }
        val backgroundColor = Color.parseColor(colorHex)
        rootLayout.setBackgroundColor(backgroundColor)
        pageIndicator.setTextColor(Color.parseColor(textColorHex))
        
        // Ensure system status and navigation bars are fully transparent and inherit rootLayout background
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        // Setup light status bar text/icons or dark status bar text/icons dynamically based on the current theme
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = (theme != "night")
        windowInsetsController.isAppearanceLightNavigationBars = (theme != "night")
    }

    fun toggleSystemUi() {
        if (isSystemUiVisible) {
            hideSystemUi()
            hideControlPanels()
            hideSettingsPanel()
        } else {
            showSystemUi()
            showControlPanels()
        }
    }

    private fun hideSystemUi() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        isSystemUiVisible = false
    }

    private fun showSystemUi() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        isSystemUiVisible = true
    }

    private fun showControlPanels() {
        topControlBar.visibility = View.VISIBLE
        topControlBar.animate().alpha(1f).setDuration(200).start()
        
        bottomControlBar.visibility = View.VISIBLE
        bottomControlBar.animate().alpha(1f).setDuration(200).start()
    }

    private fun hideControlPanels() {
        topControlBar.animate().alpha(0f).setDuration(200).withEndAction {
            topControlBar.visibility = View.GONE
        }.start()
        
        bottomControlBar.animate().alpha(0f).setDuration(200).withEndAction {
            bottomControlBar.visibility = View.GONE
        }.start()
    }

    private fun showSettingsPanel() {
        settingsPanelContainer.visibility = View.VISIBLE
        settingsPanelContainer.animate().alpha(1f).setDuration(200).start()
        isSettingsPanelVisible = true
    }

    private fun hideSettingsPanel() {
        settingsPanelContainer.animate().alpha(0f).setDuration(200).withEndAction {
            settingsPanelContainer.visibility = View.GONE
        }.start()
        isSettingsPanelVisible = false
    }

    // --- BRIGHTNESS GESTURE CONTROL PANEL ---
    private fun showBrightnessOverlay(percentage: Int) {
        fadeJob?.cancel()
        brightnessProgressBar.progress = percentage
        brightnessPercentText.text = "$percentage%"
        
        if (brightnessOverlay.visibility != View.VISIBLE) {
            brightnessOverlay.visibility = View.VISIBLE
            brightnessOverlay.animate().alpha(1f).setDuration(150).start()
        } else {
            brightnessOverlay.alpha = 1f
        }
    }

    private fun hideBrightnessOverlayWithDelay() {
        fadeJob?.cancel()
        fadeJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(1000)
            brightnessOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    brightnessOverlay.visibility = View.GONE
                }
                .start()
        }
    }

    // Intercept touch events along the vertical left/right 70dp edges of screen
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        val edgeWidthPx = 70 * density
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                if (x < edgeWidthPx || x > (screenWidth - edgeWidthPx)) {
                    maybeDraggingBrightness = true
                    startX = ev.x
                    startY = ev.y
                    
                    var currentBr = window.attributes.screenBrightness
                    if (currentBr < 0) {
                        currentBr = 0.5f
                    }
                    initialBrightness = currentBr
                } else {
                    maybeDraggingBrightness = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (maybeDraggingBrightness) {
                    val deltaX = Math.abs(ev.x - startX)
                    val deltaY = ev.y - startY
                    val absDeltaY = Math.abs(deltaY)
                    
                    if (!isDraggingBrightness && absDeltaY > touchSlop && absDeltaY > deltaX) {
                        isDraggingBrightness = true
                        showBrightnessOverlay((initialBrightness * 100).toInt())
                    }
                    
                    if (isDraggingBrightness) {
                        val sensitivityFactor = 1.2f
                        val brightnessChange = (-deltaY / screenHeight) * sensitivityFactor
                        val newBrightness = (initialBrightness + brightnessChange).coerceIn(0.01f, 1.0f)
                        
                        val lp = window.attributes
                        lp.screenBrightness = newBrightness
                        window.attributes = lp
                        
                        showBrightnessOverlay((newBrightness * 100).toInt())
                        return true // Intercept event to prevent ViewPager horizontal swiping
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingBrightness) {
                    isDraggingBrightness = false
                    maybeDraggingBrightness = false
                    
                    val finalBrightness = window.attributes.screenBrightness
                    if (finalBrightness >= 0f) {
                        getSharedPreferences("reader_prefs", MODE_PRIVATE)
                            .edit()
                            .putFloat("saved_brightness", finalBrightness)
                            .apply()
                    }
                    
                    hideBrightnessOverlayWithDelay()
                    return true
                }
                maybeDraggingBrightness = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // Turn pages using volume hardware buttons
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val nextPage = viewPager.currentItem + 1
                if (nextPage < (viewPager.adapter?.itemCount ?: 0)) {
                    viewPager.setCurrentItem(nextPage, true)
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val prevPage = viewPager.currentItem - 1
                if (prevPage >= 0) {
                    viewPager.setCurrentItem(prevPage, true)
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveProgress()
    }
}

class BookPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        val pageWidth = view.width

        when {
            position < -1 -> {
                view.alpha = 0f
            }
            position <= 0 -> {
                view.alpha = 1f
                view.translationX = 0f
                view.translationZ = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            }
            position <= 1 -> {
                view.alpha = 1f - position
                view.translationX = pageWidth * -position
                view.translationZ = -1f

                val scaleFactor = 0.85f + (1f - 0.85f) * (1f - Math.abs(position))
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
            }
            else -> {
                view.alpha = 0f
            }
        }
    }
}
