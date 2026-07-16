package com.nightread.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Layout
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.SeekBar
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nightread.app.R
import com.nightread.app.ui.customlayout.CustomReaderPageView
import com.nightread.app.ui.customlayout.PageSplitter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class BookReaderActivity : AppCompatActivity() {

    private lateinit var readerView: CustomReaderPageView
    private lateinit var pageIndicatorView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rootLayout: FrameLayout
    private lateinit var ambientGlowView: View
    private lateinit var amberFilterOverlay: View
    private lateinit var extraDimOverlay: View
    private lateinit var topToolbar: View
    private lateinit var bottomToolbar: View
    private var isBarsVisible = true
    private var touchStartY: Float = 0f
    private var touchStartTime: Long = 0L

    private lateinit var viewModel: ReaderViewModel
    private var touchStartX: Float = 0f
    private var lastPageAnimationIdx: Int = 0
    private var lastPage: Int = -1
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var sensorManager: android.hardware.SensorManager? = null
    private var accelerometer: android.hardware.Sensor? = null
    private var sensorListener: android.hardware.SensorEventListener? = null
    private var remainingTimeMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book)

        readerView = findViewById(R.id.bookReaderView)
        rootLayout = findViewById(R.id.rootView)
        ambientGlowView = findViewById(R.id.ambientGlowView)
        amberFilterOverlay = findViewById(R.id.amberFilterOverlay)
        extraDimOverlay = findViewById(R.id.extraDimOverlay)
        viewModel = ViewModelProvider(this).get(ReaderViewModel::class.java)

        val btnBack = findViewById<View>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val btnSettings = findViewById<View>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, "settings")
        }

        topToolbar = findViewById(R.id.topToolbar)
        bottomToolbar = findViewById(R.id.bottomToolbar)
        isBarsVisible = true
        
        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        val progressParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        rootLayout.addView(progressBar, progressParams)

        pageIndicatorView = findViewById(R.id.tvPageIndicator)
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) viewModel.setCurrentPage(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Enable full screen edge-to-edge transparency and hide the status bar to hide clock, battery icons, etc.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
        }

        // Apply WindowInsets for status bars and navigation bars to prevent overlap with notch/camera cutout
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val density = resources.displayMetrics.density
            
            // Calculate status bar height dynamically; fallback to 24dp if hidden
            val statusBarHeight = if (statusBarInsets.top > 0) statusBarInsets.top else (24 * density).toInt()
            val topInset = maxOf(statusBarHeight, displayCutout.top)
            
            // 1. Top toolbar handles status bar height and cutout
            topToolbar.setPadding(
                (8 * density).toInt(),
                topInset + (4 * density).toInt(),
                (8 * density).toInt(),
                (4 * density).toInt()
            )
            val topParams = topToolbar.layoutParams
            topParams.height = FrameLayout.LayoutParams.WRAP_CONTENT
            topToolbar.layoutParams = topParams
            
            // 2. Bottom toolbar handles navigation bar height
            bottomToolbar.setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                navBarInsets.bottom + (12 * density).toInt()
            )
            val bottomParams = bottomToolbar.layoutParams
            bottomParams.height = FrameLayout.LayoutParams.WRAP_CONTENT
            bottomToolbar.layoutParams = bottomParams
            
            // 3. Reader view padding uses 16dp margins on the sides and bottom, and accounts for status bar at the top
            readerView.setPadding(
                (16 * density).toInt(),
                topInset + (8 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )
            
            insets
        }

        // Setup real content bounds listener for dynamic pagination scaling
        val updateDims = {
            val contentW = readerView.width - readerView.paddingLeft - readerView.paddingRight
            val contentH = readerView.height - readerView.paddingTop - readerView.paddingBottom
            if (contentW > 0 && contentH > 0) {
                viewModel.updateDimensions(contentW, contentH, resources.displayMetrics.density)
            }
        }
        readerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateDims()
        }

        // Collect Current Page
        lifecycleScope.launch {
            viewModel.currentPage.collectLatest {
                updatePageWithAnimation(it)
                updatePageIndicator()
                seekBar.progress = it
                if (lastPage != -1 && lastPage != it) {
                    triggerPageTurnHaptic()
                }
                lastPage = it
            }
        }

        // Collect Pages list
        lifecycleScope.launch {
            viewModel.pagesState.collectLatest {
                if (it.isNotEmpty()) {
                    updatePage()
                    updatePageIndicator()
                    seekBar.max = it.size - 1
                }
            }
        }

        // Collect Active Theme Selection
        lifecycleScope.launch {
            viewModel.themeState.collectLatest { theme ->
                applyTheme(theme)
                updatePage()
            }
        }

        // Collect Book Details for dynamic title update
        lifecycleScope.launch {
            viewModel.bookState.collectLatest { book ->
                if (book != null) {
                    findViewById<TextView>(R.id.tvBookTitle)?.text = book.title
                }
            }
        }



        readerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchStartTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.x - touchStartX
                    val diffY = event.y - touchStartY
                    val duration = System.currentTimeMillis() - touchStartTime
                    
                    if (Math.abs(diffX) > 100 && Math.abs(diffX) > Math.abs(diffY) * 1.5 && duration < 500) {
                        if (diffX > 0) viewModel.setCurrentPage(viewModel.currentPage.value - 1)
                        else viewModel.setCurrentPage(viewModel.currentPage.value + 1)
                    } else if (Math.abs(diffX) < 25 && Math.abs(diffY) < 25 && duration < 300) {
                        toggleToolbars()
                    }
                }
            }
            true
        }

        lifecycleScope.launch {
            com.nightread.app.data.SettingsManager.settingsChanged.collectLatest {
                applyScreenSettings()
            }
        }
        applyScreenSettings()

        val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        if (sha1.isNotEmpty()) viewModel.loadBook(sha1)
    }

    private fun getThemeColors(themeKey: String): Pair<Int, Int> {
        return when (themeKey.lowercase()) {
            "light", "beige" -> Color.parseColor("#FFFBF0") to Color.parseColor("#1A1A1A")
            "sepia", "sepia_contrast" -> Color.parseColor("#F4ECD8") to Color.parseColor("#5C4033")
            "dark", "contrast" -> Color.parseColor("#121212") to Color.parseColor("#E0E0E0")
            "amoled" -> Color.parseColor("#000000") to Color.parseColor("#FFFFFF")
            else -> Color.parseColor("#FFFBF0") to Color.parseColor("#1A1A1A")
        }
    }

    private fun applyTheme(themeKey: String) {
        val (bgColor, textColor) = getThemeColors(themeKey)
        rootLayout.setBackgroundColor(bgColor)
        
        val topToolbar = findViewById<View>(R.id.topToolbar)
        val bottomToolbar = findViewById<View>(R.id.bottomToolbar)
        
        // Define Purple Fog (App's Style) Colors for the Toolbars
        val barBgColor = Color.parseColor("#2A1A3E")      // BgPanelDark / BgCardDark
        val barTextColor = Color.parseColor("#E8D8F0")    // TextPrimaryDark / IconTintDark
        val accentColor = Color.parseColor("#9B59B6")     // AccentDark
        val progressBgColor = Color.parseColor("#3A2A4E")  // DividerDark
        
        topToolbar.setBackgroundColor(barBgColor)
        bottomToolbar.setBackgroundColor(barBgColor)
        
        findViewById<TextView>(R.id.tvBookTitle)?.setTextColor(barTextColor)
        pageIndicatorView.setTextColor(barTextColor)
        
        val buttonTint = ColorStateList.valueOf(barTextColor)
        findViewById<ImageButton>(R.id.btnBack).imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnSettings).imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnBookmark).imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnChapters).imageTintList = buttonTint
        
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.progressTintList = ColorStateList.valueOf(accentColor)
        seekBar.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(progressBgColor)
        
        // Handle light/dark status bar icon appearances
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                val isLight = themeKey.lowercase() in listOf("light", "beige", "sepia", "sepia_contrast")
                if (isLight) {
                    controller.setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else {
                    controller.setSystemBarsAppearance(
                        0,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val isLight = themeKey.lowercase() in listOf("light", "beige", "sepia", "sepia_contrast")
            var flags = window.decorView.systemUiVisibility
            if (isLight) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun updatePage() {
        val pages = viewModel.pagesState.value
        val pageIdx = viewModel.currentPage.value
        if (pages.isEmpty() || pageIdx !in pages.indices) return

        val text = pages[pageIdx]
        
        val density = resources.displayMetrics.density
        val paint = TextPaint().apply {
            textSize = viewModel.fontSizeState.value * density
            textLocale = java.util.Locale("ru", "RU")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                letterSpacing = -0.02f
            }
            
            val weightVal = com.nightread.app.data.SettingsManager.getFontWeightAsInt(this@BookReaderActivity)
            val style = if (weightVal >= 600) Typeface.BOLD else Typeface.NORMAL
            val family = viewModel.fontFamilyState.value
            val fontResId = when (family) {
                "EB Garamond" -> R.font.eb_garamond
                "Literata" -> R.font.literata
                "Lora" -> R.font.lora
                else -> null
            }
            val baseTypeface = if (fontResId != null) {
                try {
                    androidx.core.content.res.ResourcesCompat.getFont(this@BookReaderActivity, fontResId) ?: Typeface.DEFAULT
                } catch (e: Exception) {
                    Typeface.DEFAULT
                }
            } else {
                when (family) {
                    "Roboto", "Sans Serif", "OpenDyslexic" -> Typeface.SANS_SERIF
                    "Serif", "Times New Roman", "Georgia", "Merriweather" -> Typeface.SERIF
                    "Monospace" -> Typeface.MONOSPACE
                    else -> Typeface.DEFAULT
                }
            }
            
            typeface = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                Typeface.create(baseTypeface, weightVal, false)
            } else {
                Typeface.create(baseTypeface, style)
            }
            
            val themeKey = viewModel.themeState.value
            val (_, textColor) = getThemeColors(themeKey)
            color = textColor
        }
        
        val availableWidth = readerView.width - readerView.paddingLeft - readerView.paddingRight
        val alignSetting = viewModel.fontAlignmentState.value.lowercase()
        val align = when (alignSetting) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val isJustify = alignSetting == "justify"
        val isHyphen = com.nightread.app.data.SettingsManager.isHyphenationEnabled(this)
        
        val layout = PageSplitter.createStaticLayout(
            text, 0, text.length, paint, availableWidth,
            align,
            viewModel.lineSpacingState.value, 0f,
            hyphenation = isHyphen,
            justify = isJustify
        )
        readerView.setLayout(layout, isJustify)
    }

    private fun updatePageIndicator() {
        val page = viewModel.currentPage.value + 1
        val total = viewModel.pagesState.value.size
        pageIndicatorView.text = "Стр. $page из $total"
    }

    private fun triggerPageTurnHaptic() {
        if (com.nightread.app.data.SettingsManager.isHapticFeedbackEnabled(this)) {
            readerView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun updatePageWithAnimation(newPageIdx: Int) {
        val animMode = com.nightread.app.data.SettingsManager.getPageAnimation(this)
        val pages = viewModel.pagesState.value
        if (pages.isEmpty() || newPageIdx !in pages.indices) return

        if (animMode == "none") {
            updatePage()
            return
        }

        when (animMode) {
            "fade" -> {
                readerView.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        updatePage()
                        readerView.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
            "slide" -> {
                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                val isForward = newPageIdx > lastPageAnimationIdx
                val startTranslationX = if (isForward) screenWidth else -screenWidth
                
                readerView.animate()
                    .translationX(if (isForward) -screenWidth else screenWidth)
                    .alpha(0.5f)
                    .setDuration(200)
                    .withEndAction {
                        updatePage()
                        readerView.translationX = startTranslationX
                        readerView.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
            "depth" -> {
                readerView.animate()
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        updatePage()
                        readerView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
            "zoom" -> {
                readerView.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        updatePage()
                        readerView.scaleX = 0.7f
                        readerView.scaleY = 0.7f
                        readerView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
            else -> {
                readerView.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        updatePage()
                        readerView.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        }
        lastPageAnimationIdx = newPageIdx
    }

    private fun toggleToolbars() {
        isBarsVisible = !isBarsVisible
        
        val duration = 250L
        val interpolator = android.view.animation.DecelerateInterpolator()
        
        if (isBarsVisible) {
            if (topToolbar.visibility == View.GONE) {
                topToolbar.alpha = 0f
                topToolbar.translationY = -topToolbar.height.toFloat()
                topToolbar.visibility = View.VISIBLE
            }
            if (bottomToolbar.visibility == View.GONE) {
                bottomToolbar.alpha = 0f
                bottomToolbar.translationY = bottomToolbar.height.toFloat()
                bottomToolbar.visibility = View.VISIBLE
            }
            
            topToolbar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction(null)
                .start()
                
            bottomToolbar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction(null)
                .start()
        } else {
            topToolbar.animate()
                .translationY(-topToolbar.height.toFloat())
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction { topToolbar.visibility = View.GONE }
                .start()
                
            bottomToolbar.animate()
                .translationY(bottomToolbar.height.toFloat())
                .alpha(0f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction { bottomToolbar.visibility = View.GONE }
                .start()
        }
    }

    private fun applyScreenSettings() {
        val context = this
        
        // 1. Amber Filter Overlay
        val amberEnabled = com.nightread.app.data.SettingsManager.isAmberFilterEnabled(context)
        if (amberEnabled) {
            val intensity = com.nightread.app.data.SettingsManager.getAmberFilterIntensity(context)
            val alphaFraction = (intensity / 100f) * 0.45f
            val colorVal = Color.argb((alphaFraction * 255).toInt(), 255, 145, 0)
            amberFilterOverlay.setBackgroundColor(colorVal)
            amberFilterOverlay.visibility = View.VISIBLE
        } else {
            amberFilterOverlay.visibility = View.GONE
        }

        // 2. Extra Dim Overlay
        val dimEnabled = com.nightread.app.data.SettingsManager.isExtraDimEnabled(context)
        if (dimEnabled) {
            val intensity = com.nightread.app.data.SettingsManager.getExtraDimIntensity(context)
            val alphaFraction = (intensity / 100f) * 0.85f
            val colorVal = Color.argb((alphaFraction * 255).toInt(), 0, 0, 0)
            extraDimOverlay.setBackgroundColor(colorVal)
            extraDimOverlay.visibility = View.VISIBLE
        } else {
            extraDimOverlay.visibility = View.GONE
        }

        // 3. Ambient Glow background center drawable
        val glowEnabled = com.nightread.app.data.SettingsManager.isAmbientGlowEnabled(context)
        if (glowEnabled) {
            val intensity = com.nightread.app.data.SettingsManager.getAmbientGlowIntensity(context)
            val colorKey = com.nightread.app.data.SettingsManager.getAmbientGlowColor(context)
            val glowColorHex = when (colorKey) {
                "amber" -> "#FF9800"
                "moon" -> "#D2C5E3"
                "indigo" -> "#3F51B5"
                else -> "#D2C5E3"
            }
            val baseColor = Color.parseColor(glowColorHex)
            val alphaVal = ((intensity / 100f) * 0.5f * 255).toInt()
            val centerColor = Color.argb(alphaVal, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val finalEdgeColor = Color.TRANSPARENT
            
            val maxRadius = Math.max(readerView.width, readerView.height).toFloat()
            val radius = if (maxRadius > 0f) maxRadius * 0.8f else 500f
            
            val glowDrawable = android.graphics.drawable.GradientDrawable().apply {
                gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(centerColor, finalEdgeColor)
                gradientRadius = radius
                setGradientCenter(0.5f, 0.5f)
            }
            ambientGlowView.background = glowDrawable
            ambientGlowView.visibility = View.VISIBLE
        } else {
            ambientGlowView.visibility = View.GONE
        }

        // 4. Sleep Timer
        setupSleepTimer()
    }

    private fun setupSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        
        sensorManager?.let { sm ->
            sensorListener?.let { sl ->
                sm.unregisterListener(sl)
            }
        }
        sensorListener = null

        val context = this
        val enabled = com.nightread.app.data.SettingsManager.isSleepTimerEnabled(context)
        if (!enabled) return

        val durationMinutes = com.nightread.app.data.SettingsManager.getSleepTimerDuration(context)
        remainingTimeMs = durationMinutes * 60 * 1000L

        sleepTimerJob = lifecycleScope.launch {
            while (remainingTimeMs > 0) {
                kotlinx.coroutines.delay(1000)
                remainingTimeMs -= 1000
                if (remainingTimeMs <= 0) {
                    CustomToast.show(context, "Время чтения истекло. Приложение уходит в сон.", android.widget.Toast.LENGTH_LONG)
                    finish()
                }
            }
        }

        val shakeEnabled = com.nightread.app.data.SettingsManager.isShakeToExtendEnabled(context)
        if (shakeEnabled) {
            sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
            accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
            
            if (accelerometer != null) {
                var lastShakeTime = 0L
                sensorListener = object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(event: android.hardware.SensorEvent) {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        
                        val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - android.hardware.SensorManager.GRAVITY_EARTH
                        if (acceleration > 5.0f) {
                            val now = System.currentTimeMillis()
                            if (now - lastShakeTime > 3000) {
                                lastShakeTime = now
                                remainingTimeMs += 5 * 60 * 1000L
                                CustomToast.show(context, "Таймер сна продлен на 5 минут!", android.widget.Toast.LENGTH_SHORT)
                            }
                        }
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                }
                sensorManager?.registerListener(sensorListener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepTimerJob?.cancel()
        sensorManager?.let { sm ->
            sensorListener?.let { sl ->
                sm.unregisterListener(sl)
            }
        }
    }

    fun loadPage(pageNumber: Int) {
        viewModel.setCurrentPage(pageNumber)
    }

    fun showFootnote(noteId: String) {}
    fun performSmartSearch(word: String) {}
}
