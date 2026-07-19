package com.nightread.app.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.ImageView
import coil.load
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
import android.view.KeyEvent
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
import androidx.core.graphics.ColorUtils
import com.nightread.app.R
import com.nightread.app.ui.customlayout.CustomReaderPageView
import com.nightread.app.ui.customlayout.PageSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest


class BookReaderActivity : AppCompatActivity() {

    private lateinit var readerView: CustomReaderPageView
    private lateinit var webView: android.webkit.WebView
    private lateinit var touchInterceptor: View
    private lateinit var ivBookCoverPage: ImageView
    private lateinit var pageIndicatorView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rootLayout: FrameLayout
    private lateinit var ambientGlowView: View
    private lateinit var amberFilterOverlay: View
    private lateinit var extraDimOverlay: View
    private lateinit var glassyTransitionOverlay: View
    private var isReaderReady = false
    private lateinit var topToolbar: View
    private lateinit var bottomToolbar: View
    private lateinit var tvBrightness: TextView
    private lateinit var tvWarmth: TextView
    private var isBarsVisible = true
    private var touchStartY: Float = 0f
    private var touchStartTime: Long = 0L

    // Fullscreen HUD Elements
    private lateinit var fullscreenTopHUD: View
    private lateinit var fullscreenBottomHUD: View
    private lateinit var tvFullscreenTimeBattery: TextView
    private lateinit var tvFullscreenProgressLabel: TextView
    private lateinit var pbFullscreenProgress: ProgressBar
    private var isDraggingVerticalCenter = false
    private val hideFullscreenHUDRunnable = Runnable { hideFullscreenHUD() }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentTextPaint: android.text.TextPaint? = null
    private var isAntiGlareActive = false

    private lateinit var viewModel: ReaderViewModel
    private var touchStartX: Float = 0f
    private var lastPageAnimationIdx: Int = 0
    private var lastPage: Int = -1
    private var systemTopInset: Int = 0
    private var systemBottomInset: Int = 0
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var silentModeJob: kotlinx.coroutines.Job? = null
    private var currentPageOpenTime: Long = System.currentTimeMillis()
    private var currentPageWordCount: Int = 0
    private var currentWpm: Float = 250f
    private var enteredLowSpeedTime: Long = 0L
    private var triggeredLowSpeedVibration: Boolean = false
    private var isDndActiveByApp: Boolean = false
    private var originalInterruptionFilter: Int = -1
    private var sensorManager: android.hardware.SensorManager? = null
    private var accelerometer: android.hardware.Sensor? = null
    private var lightSensor: android.hardware.Sensor? = null
    private var accelerometerListener: android.hardware.SensorEventListener? = null
    private var lightSensorListener: android.hardware.SensorEventListener? = null
    private var remainingTimeMs: Long = 0
    private var isWebViewLoading = false
    private var brightnessAnimator: android.animation.ValueAnimator? = null
    private var longPressRunnable: Runnable? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book)

        com.nightread.app.ui.customlayout.PageSplitter.init(this)
        com.nightread.app.ui.HyphenatorHelper.init(this)

        readerView = findViewById(R.id.bookReaderView)
        webView = findViewById(R.id.webView)
        touchInterceptor = findViewById(R.id.touchInterceptor)
        ivBookCoverPage = findViewById(R.id.ivBookCoverPage)
        rootLayout = findViewById(R.id.rootView)
        ambientGlowView = findViewById(R.id.ambientGlowView)
        amberFilterOverlay = findViewById(R.id.amberFilterOverlay)
        extraDimOverlay = findViewById(R.id.extraDimOverlay)
        glassyTransitionOverlay = findViewById(R.id.glassyTransitionOverlay)
        tvBrightness = findViewById(R.id.tvBrightness)
        tvWarmth = findViewById(R.id.tvWarmth)
        
        // Initialize Reader Splash Screen Background
        val readerSplashStarryBg = findViewById<com.nightread.app.ui.StarryNightView>(R.id.reader_splash_starry_bg)
        readerSplashStarryBg?.setFireflyThemeColor(Color.parseColor("#FFE3A8"))

        viewModel = ViewModelProvider(this).get(ReaderViewModel::class.java)

        val btnBack = findViewById<View>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val btnSettings = findViewById<View>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, "settings")
        }



        val btnBookmark = findViewById<ImageButton>(R.id.btnBookmark)
        btnBookmark.visibility = View.GONE
        btnBookmark.setOnClickListener {
            val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
            if (sha1.isNotEmpty()) {
                BookNavigationDialog.newInstance(sha1, 1).show(supportFragmentManager, "navigation")
            }
        }

        val btnChapters = findViewById<ImageButton>(R.id.btnChapters)
        btnChapters.visibility = View.GONE
        btnChapters.setOnClickListener {
            val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
            if (sha1.isNotEmpty()) {
                BookNavigationDialog.newInstance(sha1, 0).show(supportFragmentManager, "navigation")
            }
        }

        val btnNotes = findViewById<ImageButton>(R.id.btnNotes)
        btnNotes.setOnClickListener {
            val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
            if (sha1.isNotEmpty()) {
                BookNavigationDialog.newInstance(sha1, 2).show(supportFragmentManager, "navigation")
            }
        }

        val bookmarkArea = findViewById<View>(R.id.bookmarkArea)
        bookmarkArea.visibility = View.GONE
        bookmarkArea.setOnClickListener {
            val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
            val pageIdx = viewModel.currentPage.value
            val offset = viewModel.getOffsetForPage(pageIdx)
            val title = findViewById<TextView>(R.id.tvBookTitle).text.toString()

            if (sha1.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = com.nightread.app.data.BookmarkDatabase.getDatabase(this@BookReaderActivity)
                    val dao = db.bookmarkDao()
                    val existing = dao.getBookmarkAtOffset(sha1, offset)

                    if (existing != null) {
                        dao.deleteBookmark(existing)
                        withContext(Dispatchers.Main) {
                            CustomToast.show(this@BookReaderActivity, "Закладка удалена")
                        }
                    } else {
                        val newBookmark = com.nightread.app.data.BookmarkEntity(
                            bookSha1 = sha1,
                            bookTitle = title,
                            charOffset = offset,
                            pageIndex = pageIdx,
                            snippet = "...",
                            timestamp = System.currentTimeMillis()
                        )
                        dao.insertBookmark(newBookmark)
                        withContext(Dispatchers.Main) {
                            CustomToast.show(this@BookReaderActivity, "Закладка создана")
                        }
                    }
                }
            }
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
        fullscreenTopHUD = findViewById(R.id.fullscreenTopHUD)
        fullscreenBottomHUD = findViewById(R.id.fullscreenBottomHUD)
        tvFullscreenTimeBattery = findViewById(R.id.tvFullscreenTimeBattery)
        tvFullscreenProgressLabel = findViewById(R.id.tvFullscreenProgressLabel)
        pbFullscreenProgress = findViewById(R.id.pbFullscreenProgress)
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Update the page indicator text for real-time feedback without expensive re-rendering
                    val total = viewModel.pagesState.value.size
                    pageIndicatorView.text = "Стр. ${progress + 1} из $total"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    viewModel.setCurrentPage(it.progress)
                }
            }
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
            
            systemTopInset = topInset
            systemBottomInset = navBarInsets.bottom
            
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
            
            // 3. Reader view padding uses 16dp margins on the sides and bottom, and accounts for status bar at the top and navigation bar at the bottom
            readerView.setPadding(
                (16 * density).toInt(),
                topInset + (8 * density).toInt(),
                (16 * density).toInt(),
                navBarInsets.bottom + (16 * density).toInt()
            )
            
            insets
        }

        // Setup WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            textZoom = 100
            allowFileAccess = true
            allowContentAccess = true
        }
        webView.addJavascriptInterface(WebAppInterface(this), "AndroidInterface")
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                progressBar.visibility = View.GONE
                if (isAntiGlareActive) {
                    val themeKey = viewModel.themeState.value
                    val (_, textColor) = getThemeColors(themeKey)
                    val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)
                    webView.evaluateJavascript("if (typeof applyAntiGlare !== 'undefined') { applyAntiGlare(true, '$textColorHex'); }", null)
                }
                val book = viewModel.bookState.value
                if (book != null) {
                    val pIndex = book.currentProgressChar
                    webView.postDelayed({
                        webView.evaluateJavascript("scrollToParagraph('p_$pIndex');") { result ->
                            if (result != "true" && pIndex > 0) {
                                // Retry once after 300ms if layout was not ready
                                webView.postDelayed({
                                    webView.evaluateJavascript("scrollToParagraph('p_$pIndex');") { secondResult ->
                                        isWebViewLoading = false
                                        if (secondResult != "true") {
                                            // Final fallback if paragraph scroll completely fails
                                            val pageIdx = viewModel.currentPage.value
                                            val w = webView.width
                                            if (w > 0 && pageIdx > 0) {
                                                webView.scrollTo((pageIdx - 1) * w, 0)
                                                webView.evaluateJavascript("reportCurrentParagraph();", null)
                                            }
                                        }
                                        hideReaderSplash()
                                    }
                                }, 300)
                            } else {
                                isWebViewLoading = false
                                if (result != "true") {
                                    // If pIndex == 0 or not found, fall back to page index
                                    val pageIdx = viewModel.currentPage.value
                                    val w = webView.width
                                    if (w > 0 && pageIdx > 0) {
                                        webView.scrollTo((pageIdx - 1) * w, 0)
                                        webView.evaluateJavascript("reportCurrentParagraph();", null)
                                    }
                                }
                                hideReaderSplash()
                            }
                        }
                    }, 250)
                } else {
                    isWebViewLoading = false
                    val pageIdx = viewModel.currentPage.value
                    val w = webView.width
                    if (w > 0 && pageIdx > 0) {
                        webView.scrollTo((pageIdx - 1) * w, 0)
                    }
                    hideReaderSplash()
                }
            }
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
                onPageChangedForSpeedTracker(it)
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

        // Collect Font Settings changes to show glassy transitions
        lifecycleScope.launch {
            viewModel.fontSettingsChanged.collect {
                triggerGlassTransition()
            }
        }

        startSilentModeTracker()



        var touchStartX = 0f
        var touchStartY = 0f
        var touchStartTime = 0L
        var isDraggingVerticalLeft = false
        var isDraggingVerticalRight = false
        var initialGestureValue = 0f
        val hideIndicatorsRunnable = Runnable {
            tvBrightness.visibility = View.GONE
            tvWarmth.visibility = View.GONE
        }

        val gestureTouchListener = View.OnTouchListener { view, event ->
            val screenWidth = view.width.toFloat()
            val screenHeight = view.height.toFloat()
            if (screenWidth <= 0 || screenHeight <= 0) return@OnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchStartTime = System.currentTimeMillis()

                    longPressRunnable = Runnable {
                        val currentTextOnPage = viewModel.pagesState.value.getOrNull(viewModel.currentPage.value)?.toString() ?: ""
                        if (currentTextOnPage.isNotEmpty() && currentTextOnPage != "[BOOK_COVER]") {
                            val contextSnippet = if (currentTextOnPage.length > 150) currentTextOnPage.substring(0, 150) + "..." else currentTextOnPage
                            showWordActionOrNoteDialog(currentTextOnPage, contextSnippet)
                        }
                    }
                    handler.postDelayed(longPressRunnable!!, 600)

                    brightnessAnimator?.cancel()
                    isDraggingVerticalLeft = false
                    isDraggingVerticalRight = false
                    isDraggingVerticalCenter = false
                    if (event.x < screenWidth * 0.35f) {
                        isDraggingVerticalLeft = true
                        val lp = window.attributes
                        initialGestureValue = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                    } else if (event.x > screenWidth * 0.65f) {
                        isDraggingVerticalRight = true
                        initialGestureValue = com.nightread.app.data.SettingsManager.getAmberFilterIntensity(this@BookReaderActivity).toFloat()
                    } else {
                        isDraggingVerticalCenter = true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = event.x - touchStartX
                    val diffY = event.y - touchStartY
                    val duration = System.currentTimeMillis() - touchStartTime

                    if (Math.abs(diffX) > 15 || Math.abs(diffY) > 15) {
                        longPressRunnable?.let {
                            handler.removeCallbacks(it)
                            longPressRunnable = null
                        }
                    }

                    if (duration > 100 && Math.abs(diffY) > 50 && Math.abs(diffY) > Math.abs(diffX) * 2f) {
                        if (isDraggingVerticalLeft) {
                            val delta = -diffY / screenHeight
                            val newBrightness = (initialGestureValue + delta).coerceIn(0.01f, 1.0f)
                            val lp = window.attributes
                            lp.screenBrightness = newBrightness
                            window.attributes = lp
                            com.nightread.app.data.SettingsManager.setBrightness(this@BookReaderActivity, newBrightness)
                            
                            tvBrightness.visibility = View.VISIBLE
                            tvBrightness.text = "☀ ${(newBrightness * 100).toInt()}%"
                            handler.removeCallbacks(hideIndicatorsRunnable)
                        } else if (isDraggingVerticalRight) {
                            val delta = (-diffY / screenHeight) * 100f
                            val newIntensity = (initialGestureValue + delta).coerceIn(0f, 100f).toInt()
                            com.nightread.app.data.SettingsManager.setAmberFilterEnabled(this@BookReaderActivity, true)
                            com.nightread.app.data.SettingsManager.setAmberFilterIntensity(this@BookReaderActivity, newIntensity)
                            applyScreenSettings()
                            
                            tvWarmth.visibility = View.VISIBLE
                            tvWarmth.text = "🌡 $newIntensity%"
                            handler.removeCallbacks(hideIndicatorsRunnable)
                        } else if (isDraggingVerticalCenter && diffY < 0 && !isBarsVisible) {
                            showFullscreenHUD()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let {
                        handler.removeCallbacks(it)
                        longPressRunnable = null
                    }
                    val diffX = event.x - touchStartX
                    val diffY = event.y - touchStartY
                    val duration = System.currentTimeMillis() - touchStartTime

                    if (isDraggingVerticalCenter && diffY < -50 && !isBarsVisible) {
                        showFullscreenHUD()
                    } else if (Math.abs(diffX) > 100 && Math.abs(diffX) > Math.abs(diffY) * 1.5 && duration < 500) {
                        if (diffX > 0) {
                            viewModel.setCurrentPage(viewModel.currentPage.value - 1)
                        } else {
                            viewModel.setCurrentPage(viewModel.currentPage.value + 1)
                        }
                    } else if (Math.abs(diffX) < 25 && Math.abs(diffY) < 25 && duration < 300) {
                        toggleToolbars()
                    }
                    
                    isDraggingVerticalLeft = false
                    isDraggingVerticalRight = false
                    isDraggingVerticalCenter = false
                    handler.postDelayed(hideIndicatorsRunnable, 1000)
                }
            }
            true
        }

        readerView.setOnTouchListener(gestureTouchListener)
        touchInterceptor.setOnTouchListener(gestureTouchListener)

        lifecycleScope.launch {
            com.nightread.app.data.SettingsManager.settingsChanged.collectLatest {
                applyScreenSettings()
            }
        }
        applyScreenSettings()

        val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        if (sha1.isNotEmpty()) {
            com.nightread.app.data.SettingsManager.setLastReadBookSha1(this, sha1)
            viewModel.loadBook(sha1)
        }
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

    private fun applyTheme(themeKey: String, animate: Boolean = false) {
        val (bgColor, textColor) = getThemeColors(themeKey)
        
        if (animate) {
            val oldBgColor = (rootLayout.background as? android.graphics.drawable.ColorDrawable)?.color ?: getThemeColors(themeKey).first
            val bgAnimation = android.animation.ValueAnimator.ofObject(
                android.animation.ArgbEvaluator(),
                oldBgColor,
                bgColor
            )
            bgAnimation.duration = 800
            bgAnimation.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                rootLayout.setBackgroundColor(color)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = color
                }
            }
            bgAnimation.start()

            val textOldColor = if (::tvFullscreenTimeBattery.isInitialized) tvFullscreenTimeBattery.currentTextColor else textColor
            val txtAnimation = android.animation.ValueAnimator.ofObject(
                android.animation.ArgbEvaluator(),
                textOldColor,
                textColor
            )
            txtAnimation.duration = 800
            txtAnimation.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                if (::tvFullscreenTimeBattery.isInitialized) {
                    tvFullscreenTimeBattery.setTextColor(color)
                }
                if (::tvFullscreenProgressLabel.isInitialized) {
                    tvFullscreenProgressLabel.setTextColor(color)
                }
                currentTextPaint?.color = color
                readerView.invalidate()
            }
            txtAnimation.start()
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = bgColor
            }
            rootLayout.setBackgroundColor(bgColor)
            if (::tvFullscreenTimeBattery.isInitialized) {
                tvFullscreenTimeBattery.setTextColor(textColor)
            }
            if (::tvFullscreenProgressLabel.isInitialized) {
                tvFullscreenProgressLabel.setTextColor(textColor)
            }
            currentTextPaint?.color = textColor
            readerView.invalidate()
        }
        
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
        findViewById<ImageButton>(R.id.btnNotes).imageTintList = buttonTint
        
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.progressTintList = ColorStateList.valueOf(accentColor)
        seekBar.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(progressBgColor)

        if (::pbFullscreenProgress.isInitialized && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            pbFullscreenProgress.progressTintList = ColorStateList.valueOf(accentColor)
            // A subtle background tint using the primary text color with alpha
            val bgTrackColor = (textColor and 0x00FFFFFF) or 0x22000000
            pbFullscreenProgress.progressBackgroundTintList = ColorStateList.valueOf(bgTrackColor)
        }
        
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
        
        val filePath = viewModel.bookState.value?.filePath ?: ""
        val isWebViewBook = filePath.endsWith(".fb2", true) || 
                           filePath.endsWith(".fb2.zip", true) || 
                           filePath.endsWith(".zip", true) ||
                           filePath.endsWith(".epub", true)

        if (pages.isEmpty()) return
        
        // Fix: Allow WebView books to proceed even if pageIdx is out of bounds for the temporary marker list
        if (!isWebViewBook && pageIdx !in pages.indices) return

        val text = if (pageIdx in pages.indices) pages[pageIdx] else pages.first()
        
        if (text.toString() == "[BOOK_COVER]") {
            readerView.visibility = View.GONE
            webView.visibility = View.GONE
            touchInterceptor.visibility = View.GONE
            ivBookCoverPage.visibility = View.VISIBLE
            val book = viewModel.bookState.value
            if (book != null && !book.coverPath.isNullOrEmpty() && java.io.File(book.coverPath).exists()) {
                ivBookCoverPage.load(java.io.File(book.coverPath))
            } else {
                ivBookCoverPage.setImageResource(R.drawable.ic_book_placeholder)
            }
            updatePageIndicator()
            hideReaderSplash()
            return
        } else if (text.toString().startsWith("WEBVIEW_CONTENT") || text.toString().startsWith("WEBVIEW_PAGE_") || isWebViewBook) {
            readerView.visibility = View.GONE
            ivBookCoverPage.visibility = View.GONE
            webView.visibility = View.VISIBLE
            touchInterceptor.visibility = View.VISIBLE
            
            val themeKey = viewModel.themeState.value
            val fontSize = viewModel.fontSizeState.value
            val lineSpacing = viewModel.lineSpacingState.value
            val fontFamily = viewModel.fontFamilyState.value
            val fontWeight = viewModel.fontWeightState.value
            val fontAlignment = viewModel.fontAlignmentState.value
            val pageMargins = viewModel.pageMarginsState.value
            
            val density = resources.displayMetrics.density
            val paddingTop = systemTopInset + (8 * density).toInt()
            val paddingBottom = systemBottomInset + (16 * density).toInt()
            val paddingLeft = (16 * density).toInt()
            val paddingRight = (16 * density).toInt()

            val settingsKey = "${viewModel.bookState.value?.sha1}_${themeKey}_${fontSize}_${lineSpacing}_${fontFamily}_${fontWeight}_${fontAlignment}_${pageMargins}_${paddingTop}_${paddingBottom}"
            val loadedKey = webView.tag as? String
            
            val currentBookSha1 = viewModel.bookState.value?.sha1 ?: ""
            val isSameBook = loadedKey != null && currentBookSha1.isNotEmpty() && loadedKey.startsWith(currentBookSha1)
            
            if (isSameBook && loadedKey != settingsKey) {
                val parts = loadedKey!!.split("_")
                val oldTheme = if (parts.size > 1) parts[1] else ""
                val oldFontSize = if (parts.size > 2) parts[2].toFloatOrNull() ?: 16f else 16f
                val oldLineSpacing = if (parts.size > 3) parts[3].toFloatOrNull() ?: 1.2f else 1.2f
                val oldFontFamily = if (parts.size > 4) parts[4] else ""
                val oldFontWeight = if (parts.size > 5) parts[5].toIntOrNull() ?: 0 else 0
                val oldFontAlignment = if (parts.size > 6) parts[6] else ""
                
                webView.tag = settingsKey
                isWebViewLoading = false
                
                if (oldTheme != themeKey) {
                    val (bgColor, textColor) = getThemeColors(themeKey)
                    val bgColorHex = String.format("#%06X", 0xFFFFFF and bgColor)
                    val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)
                    
                    webView.evaluateJavascript("if (typeof applyThemeChange !== 'undefined') { applyThemeChange('$bgColorHex', '$textColorHex', 800); }", null)
                    applyTheme(themeKey, animate = true)
                } else {
                    applyTheme(themeKey, animate = false)
                }
                
                if (oldFontSize != fontSize || oldLineSpacing != lineSpacing || oldFontFamily != fontFamily || oldFontWeight != fontWeight || oldFontAlignment != fontAlignment) {
                    webView.evaluateJavascript(
                        "if (typeof applyFontChange !== 'undefined') { applyFontChange('$fontFamily', $fontSize, $lineSpacing, '$fontAlignment', $fontWeight); }",
                        null
                    )
                }
                
                updatePageIndicator()
            } else if (loadedKey != settingsKey) {
                isWebViewLoading = true
                progressBar.visibility = View.VISIBLE
                webView.tag = settingsKey
                
                lifecycleScope.launch(Dispatchers.IO) {
                    val fullContent = BookCache.content
                    val paddingTopDp = (paddingTop / density).toInt()
                    val paddingBottomDp = (paddingBottom / density).toInt()
                    val paddingLeftDp = 16
                    val paddingRightDp = 16
                    
                    val book = viewModel.bookState.value
                    val isEpub = book?.filePath?.endsWith(".epub", true) == true
                    
                    val html = if (isEpub) {
                        com.nightread.app.service.EpubToHtmlConverter.convert(
                            fullContent,
                            themeKey,
                            fontSize,
                            lineSpacing,
                            fontFamily,
                            fontWeight,
                            fontAlignment,
                            pageMargins,
                            paddingTopDp,
                            paddingBottomDp,
                            paddingLeftDp,
                            paddingRightDp
                        )
                    } else {
                        com.nightread.app.service.Fb2ToHtmlConverterAdvanced.convert(
                            fullContent,
                            themeKey,
                            fontSize,
                            lineSpacing,
                            fontFamily,
                            fontWeight,
                            fontAlignment,
                            pageMargins,
                            paddingTopDp,
                            paddingBottomDp,
                            paddingLeftDp,
                            paddingRightDp
                        )
                    }
                    
                    var indexFile: java.io.File? = null
                    if (isEpub && book != null) {
                        val file = java.io.File(book.filePath ?: "")
                        if (file.exists()) {
                            val extractedDir = java.io.File(cacheDir, "extracted_epubs/${book.sha1}")
                            com.nightread.app.data.EpubIdentifierHelper.unzip(file, extractedDir)
                            val metadata = com.nightread.app.data.EpubIdentifierHelper.getEpubMetadata(file)
                            val opfDir = metadata?.opfDir ?: ""
                            indexFile = java.io.File(extractedDir, if (opfDir.isNotEmpty()) "$opfDir/index_rendered.html" else "index_rendered.html")
                            try {
                                indexFile.parentFile?.mkdirs()
                                indexFile.writeText(html, Charsets.UTF_8)
                            } catch (e: Exception) {
                                android.util.Log.e("BookReaderActivity", "Error writing EPUB index_rendered.html", e)
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        if (isEpub && indexFile != null && indexFile.exists()) {
                            webView.loadUrl("file://" + indexFile.absolutePath)
                        } else {
                            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                        }
                    }
                }
            } else {
                isWebViewLoading = false
                val w = webView.width
                if (w > 0 && pageIdx > 0) {
                    webView.scrollTo((pageIdx - 1) * w, 0)
                } else if (w > 0) {
                    webView.scrollTo(0, 0)
                }
                webView.postDelayed({
                    if (viewModel.pagesState.value.size <= 1) {
                        webView.evaluateJavascript("calculatePages();", null)
                    }
                    webView.evaluateJavascript("reportCurrentParagraph();", null)
                }, 100)
            }
            updatePageIndicator()
            return
        } else {
            readerView.visibility = View.VISIBLE
            webView.visibility = View.GONE
            touchInterceptor.visibility = View.GONE
            ivBookCoverPage.visibility = View.GONE
        }
        
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
            if (isAntiGlareActive) {
                isFakeBoldText = true
                val isLight = themeKey.lowercase() in listOf("light", "beige", "sepia", "sepia_contrast")
                color = if (isLight) Color.BLACK else Color.WHITE
            } else {
                isFakeBoldText = false
                color = textColor
            }
        }
        currentTextPaint = paint
        
        val availableWidth = readerView.width - readerView.paddingLeft - readerView.paddingRight
        val alignSetting = viewModel.fontAlignmentState.value.lowercase()
        val align = when (alignSetting) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val isJustify = alignSetting == "justify"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            paint.letterSpacing = if (isJustify) 0.01f else -0.02f
        }
        val isHyphen = com.nightread.app.data.SettingsManager.isHyphenationEnabled(this)
        
        PageSplitter.getPageLayout(
            pageIdx,
            text,
            paint,
            availableWidth,
            align,
            viewModel.lineSpacingState.value, 0f,
            hyphenation = isHyphen,
            justify = isJustify
        ) { layout ->
            progressBar.visibility = View.GONE
            readerView.setLayout(layout, isJustify)
            hideReaderSplash()
        }
        
        // Show progress bar while loading
        if (!PageSplitter.isPageCached(pageIdx)) {
            progressBar.visibility = View.VISIBLE
        }



    }

    private fun updatePageIndicator() {
        val page = viewModel.currentPage.value + 1
        val total = viewModel.pagesState.value.size
        pageIndicatorView.text = "Стр. $page из $total"
    }

    private fun triggerPageTurnHaptic() {
        if (com.nightread.app.data.SettingsManager.isSilentModeEnabled(this) && currentWpm > 400f) {
            // Silence all vibrations at high reading speeds
            return
        }
        if (com.nightread.app.data.SettingsManager.isHapticFeedbackEnabled(this)) {
            readerView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private val activePageView: View
        get() {
            val isWebViewBook = viewModel.bookState.value?.filePath?.let {
                it.endsWith(".fb2", true) || it.endsWith(".fb2.zip", true) || it.endsWith(".zip", true) || it.endsWith(".epub", true)
            } == true
            if (isWebViewBook) return webView
            if (viewModel.pagesState.value.getOrNull(viewModel.currentPage.value)?.toString() == "[BOOK_COVER]") return ivBookCoverPage
            return readerView
        }

    private fun updatePageWithAnimation(newPageIdx: Int) {
        val animMode = com.nightread.app.data.SettingsManager.getPageAnimation(this)
        val pages = viewModel.pagesState.value
        
        val filePath = viewModel.bookState.value?.filePath ?: ""
        val isWebViewBook = filePath.endsWith(".fb2", true) || 
                           filePath.endsWith(".fb2.zip", true) || 
                           filePath.endsWith(".zip", true)

        if (pages.isEmpty()) return
        if (!isWebViewBook && newPageIdx !in pages.indices) return

        if (animMode == "none") {
            updatePage()
            return
        }

        val currentView = activePageView

        when (animMode) {
            "fade" -> {
                currentView.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        updatePage()
                        val nextView = activePageView
                        nextView.alpha = 0f
                        nextView.animate()
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
                
                currentView.animate()
                    .translationX(if (isForward) -screenWidth else screenWidth)
                    .setDuration(50)
                    .withEndAction {
                        updatePage()
                        val nextView = activePageView
                        nextView.translationX = startTranslationX
                        nextView.animate()
                            .translationX(0f)
                            .setDuration(50)
                            .start()
                    }
                    .start()
            }
            "depth" -> {
                currentView.animate()
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        updatePage()
                        val nextView = activePageView
                        nextView.scaleX = 0.8f
                        nextView.scaleY = 0.8f
                        nextView.alpha = 0f
                        nextView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
            "zoom" -> {
                currentView.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        updatePage()
                        val nextView = activePageView
                        nextView.scaleX = 0.7f
                        nextView.scaleY = 0.7f
                        nextView.alpha = 0f
                        nextView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
            "curl" -> {
                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                currentView.animate()
                    .translationX(-screenWidth / 2f)
                    .scaleX(0.8f)
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        updatePage()
                        val nextView = activePageView
                        nextView.translationX = screenWidth / 2f
                        nextView.scaleX = 0.8f
                        nextView.alpha = 0f
                        nextView.animate()
                            .translationX(0f)
                            .scaleX(1f)
                            .alpha(1f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
            }
            else -> {
                currentView.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        updatePage()
                        val nextView = activePageView
                        nextView.alpha = 0f
                        nextView.animate()
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
            // Hide the fullscreen HUD immediately if main bars are shown
            handler.removeCallbacks(hideFullscreenHUDRunnable)
            if (::fullscreenTopHUD.isInitialized) {
                fullscreenTopHUD.visibility = View.GONE
                fullscreenTopHUD.alpha = 0f
            }
            if (::fullscreenBottomHUD.isInitialized) {
                fullscreenBottomHUD.visibility = View.GONE
                fullscreenBottomHUD.alpha = 0f
            }

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

        val context = this
        val enabled = com.nightread.app.data.SettingsManager.isSleepTimerEnabled(context)
        if (!enabled) {
            updateSensors()
            return
        }

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

        updateSensors()
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        animateBrightnessRise()
    }

    override fun onPause() {
        super.onPause()
        brightnessAnimator?.cancel()
        viewModel.saveProgress()
        unregisterSensors()
        restoreDndFilter()
    }

    private fun updateSensors() {
        unregisterSensors()
        registerSensors()
    }

    private fun registerSensors() {
        val context = this
        if (sensorManager == null) {
            sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        }
        
        val sleepTimerEnabled = com.nightread.app.data.SettingsManager.isSleepTimerEnabled(context)
        val shakeEnabled = com.nightread.app.data.SettingsManager.isShakeToExtendEnabled(context) && sleepTimerEnabled
        val autoThemeEnabled = com.nightread.app.data.SettingsManager.isAutoLightNightEnabled(context)
        
        if (shakeEnabled) {
            if (accelerometerListener == null) {
                accelerometerListener = object : android.hardware.SensorEventListener {
                    private var lastShakeTime = 0L
                    override fun onSensorChanged(event: android.hardware.SensorEvent) {
                        if (event.sensor.type == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                            val x = event.values[0]
                            val y = event.values[1]
                            val z = event.values[2]
                            val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - android.hardware.SensorManager.GRAVITY_EARTH
                            if (acceleration > 5.0f) {
                                val now = System.currentTimeMillis()
                                if (now - lastShakeTime > 3000) {
                                    lastShakeTime = now
                                    remainingTimeMs += 5 * 60 * 1000L
                                    CustomToast.show(context, "Время сна продлено на 5 минут", android.widget.Toast.LENGTH_SHORT)
                                }
                            }
                        }
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                }
            }
            accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                sensorManager?.registerListener(accelerometerListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        if (autoThemeEnabled || true) { // Always register to support both auto-theme and anti-glare
            if (lightSensorListener == null) {
                lightSensorListener = object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(event: android.hardware.SensorEvent) {
                        if (event.sensor.type == android.hardware.Sensor.TYPE_LIGHT) {
                            val lux = event.values[0]
                            handleLightSensorChanged(lux)
                        }
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                }
            }
            lightSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
            lightSensor?.let {
                sensorManager?.registerListener(lightSensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun unregisterSensors() {
        accelerometerListener?.let {
            sensorManager?.unregisterListener(it)
        }
        lightSensorListener?.let {
            sensorManager?.unregisterListener(it)
        }
        accelerometerListener = null
        lightSensorListener = null
    }

    private fun handleLightSensorChanged(lux: Float) {
        val antiGlare = lux > 10000f
        if (isAntiGlareActive != antiGlare) {
            isAntiGlareActive = antiGlare
            updatePage()
            
            // Update WebView styling
            val themeKey = viewModel.themeState.value
            val (_, textColor) = getThemeColors(themeKey)
            val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)
            webView.evaluateJavascript("if (typeof applyAntiGlare !== 'undefined') { applyAntiGlare($antiGlare, '$textColorHex'); }", null)
        }

        if (com.nightread.app.data.SettingsManager.isAutoLightNightEnabled(this)) {
            val currentTheme = viewModel.themeState.value
            // Thresholds: < 10 lux for night, > 40 lux for sepia
            val targetTheme = if (lux < 10f) {
                "dark"
            } else if (lux > 40f) {
                "sepia"
            } else {
                return // Middle ground, keep current
            }
            
            if (currentTheme != targetTheme) {
                com.nightread.app.data.SettingsManager.setReadingTheme(this, targetTheme)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        brightnessAnimator?.cancel()
        sleepTimerJob?.cancel()
        unregisterSensors()
    }

    private fun animateBrightnessRise() {
        val savedBrightness = com.nightread.app.data.SettingsManager.getBrightness(this)
        val targetBrightness = if (savedBrightness >= 0.01f) savedBrightness else 0.5f
        
        val startBrightness = 0.01f
        if (targetBrightness <= startBrightness) {
            val lp = window.attributes
            lp.screenBrightness = targetBrightness
            window.attributes = lp
            return
        }

        brightnessAnimator?.cancel()
        brightnessAnimator = android.animation.ValueAnimator.ofFloat(startBrightness, targetBrightness).apply {
            duration = 1500
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                val currentLp = window.attributes
                currentLp.screenBrightness = value
                window.attributes = currentLp
            }
            start()
        }
    }

    private fun hideReaderSplash() {
        runOnUiThread {
            isReaderReady = true
            val splash = findViewById<View>(R.id.reader_splash_overlay) ?: return@runOnUiThread
            if (splash.visibility == View.GONE || splash.alpha == 0f) return@runOnUiThread
            
            splash.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    splash.visibility = View.GONE
                }
                .start()
        }
    }

    private var glassTransitionRunnable: Runnable? = null

    fun triggerGlassTransition() {
        if (!isReaderReady) return
        
        runOnUiThread {
            val overlay = glassyTransitionOverlay
            overlay.animate().cancel()
            
            glassTransitionRunnable?.let { handler.removeCallbacks(it) }
            
            val themeKey = viewModel.themeState.value
            val (bgColor, _) = getThemeColors(themeKey)
            val alphaColor = Color.argb(190, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            overlay.setBackgroundColor(alphaColor)
            
            overlay.alpha = 0f
            overlay.visibility = View.VISIBLE
            
            overlay.animate()
                .alpha(1f)
                .setDuration(120)
                .withEndAction {
                    val run = Runnable {
                        val viewToAnimate = activePageView
                        viewToAnimate.animate().cancel()
                        viewToAnimate.alpha = 0.7f
                        viewToAnimate.scaleX = 0.98f
                        viewToAnimate.scaleY = 0.98f
                        
                        viewToAnimate.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(700)
                            .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                            .start()
                        
                        overlay.animate()
                            .alpha(0f)
                            .setDuration(650)
                            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                            .withEndAction {
                                overlay.visibility = View.GONE
                            }
                            .start()
                    }
                    glassTransitionRunnable = run
                    handler.postDelayed(run, 100)
                }
                .start()
        }
    }

    fun loadPage(pageNumber: Int) {
        viewModel.setCurrentPage(pageNumber)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode
        


        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    val currentPage = viewModel.currentPage.value
                    val totalPages = viewModel.pagesState.value.size
                    if (currentPage < totalPages - 1) {
                        viewModel.setCurrentPage(currentPage + 1)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    val currentPage = viewModel.currentPage.value
                    if (currentPage > 0) {
                        viewModel.setCurrentPage(currentPage - 1)
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun showFootnote(noteId: String) {}
    fun performSmartSearch(word: String) {}

    fun onWebViewPagesCalculated(totalPages: Int) {
        viewModel.setWebViewPageCount(totalPages)
        val pageIdx = viewModel.currentPage.value
        val w = webView.width
        if (w > 0 && pageIdx > 0) {
            webView.scrollTo((pageIdx - 1) * w, 0)
        }
    }

    fun onParagraphVisible(pId: String) {
        if (isWebViewLoading) return
        val pIndex = pId.substringAfter("p_").toIntOrNull() ?: return
        viewModel.updateWebViewParagraphProgress(pIndex)
    }

    fun onWebViewPageRestored(pageIndex: Int) {
        if (isWebViewLoading) return
        viewModel.setWebViewPageRestored(pageIndex + 1)
    }

    fun saveNoteForBook(selectedText: String, noteText: String) {
        viewModel.addNote(selectedText, noteText)
    }

    fun showWordActionOrNoteDialog(selectedText: String, contextSnippet: String) {
        WordActionBottomSheet.newInstance(selectedText, contextSnippet)
            .show(supportFragmentManager, "word_action")
    }



    class WebAppInterface(private val activity: BookReaderActivity) {
        @android.webkit.JavascriptInterface
        fun onPagesCalculated(totalPages: Int) {
            activity.runOnUiThread {
                activity.onWebViewPagesCalculated(totalPages)
            }
        }

        @android.webkit.JavascriptInterface
        fun onParagraphVisible(pId: String) {
            activity.runOnUiThread {
                activity.onParagraphVisible(pId)
            }
        }

        @android.webkit.JavascriptInterface
        fun onPageRestored(pageIndex: Int) {
            activity.runOnUiThread {
                activity.onWebViewPageRestored(pageIndex)
            }
        }

        @android.webkit.JavascriptInterface
        fun onTextSelected(selectedText: String, contextSnippet: String) {
            activity.runOnUiThread {
                activity.showWordActionOrNoteDialog(selectedText, contextSnippet)
            }
        }
    }

    private fun onPageChangedForSpeedTracker(pageIndex: Int) {
        currentPageOpenTime = System.currentTimeMillis()
        val pages = viewModel.pagesState.value
        val textOnPage = pages.getOrNull(pageIndex)?.toString() ?: ""
        currentPageWordCount = countWords(textOnPage)
        currentWpm = 250f
        enteredLowSpeedTime = 0L
        triggeredLowSpeedVibration = false
    }

    private fun countWords(text: String): Int {
        if (text.isBlank() || text == "[BOOK_COVER]") return 0
        return text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
    }

    private fun startSilentModeTracker() {
        silentModeJob?.cancel()
        silentModeJob = lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val context = this@BookReaderActivity
                if (!com.nightread.app.data.SettingsManager.isSilentModeEnabled(context)) {
                    restoreDndFilter()
                    continue
                }

                val elapsedMs = System.currentTimeMillis() - currentPageOpenTime
                val elapsedSeconds = elapsedMs / 1000f

                if (elapsedSeconds >= 5f && currentPageWordCount > 0) {
                    currentWpm = (currentPageWordCount.toFloat() / (elapsedSeconds / 60f))
                } else {
                    currentWpm = 250f
                }

                // 1. High speed condition (> 400 WPM)
                if (currentWpm > 400f) {
                    activateDndFilter()
                } else {
                    restoreDndFilter()
                }

                // 2. Low speed condition (< 100 WPM)
                if (currentWpm < 100f) {
                    if (enteredLowSpeedTime == 0L) {
                        enteredLowSpeedTime = System.currentTimeMillis()
                    } else {
                        val timeInLowSpeed = System.currentTimeMillis() - enteredLowSpeedTime
                        if (timeInLowSpeed >= 2 * 60 * 1000L && !triggeredLowSpeedVibration) {
                            triggerLightVibration()
                            triggeredLowSpeedVibration = true
                        }
                    }
                } else {
                    enteredLowSpeedTime = 0L
                    triggeredLowSpeedVibration = false
                }
            }
        }
    }

    private fun triggerLightVibration() {
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        }
    }

    private fun activateDndFilter() {
        if (!com.nightread.app.data.SettingsManager.isSilentModeEnabled(this)) return
        if (isDndActiveByApp) return

        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (notificationManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    if (notificationManager.isNotificationPolicyAccessGranted) {
                        originalInterruptionFilter = notificationManager.getCurrentInterruptionFilter()
                        notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        isDndActiveByApp = true
                    }
                } catch (e: Exception) {
                    // Gracefully ignore
                }
            }
        }
    }

    private fun restoreDndFilter() {
        if (!isDndActiveByApp) return

        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        if (notificationManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    if (notificationManager.isNotificationPolicyAccessGranted && originalInterruptionFilter != -1) {
                        notificationManager.setInterruptionFilter(originalInterruptionFilter)
                    }
                } catch (e: Exception) {
                    // Gracefully ignore
                }
            }
        }
        isDndActiveByApp = false
        originalInterruptionFilter = -1
    }

    private fun showFullscreenHUD() {
        if (isBarsVisible) return // Only show in fullscreen mode
        
        updateFullscreenHUDData()
        
        handler.removeCallbacks(hideFullscreenHUDRunnable)
        
        if (fullscreenTopHUD.visibility != View.VISIBLE) {
            fullscreenTopHUD.visibility = View.VISIBLE
            fullscreenTopHUD.animate()
                .alpha(1f)
                .setDuration(250)
                .setListener(null)
                .start()
        }
        
        if (fullscreenBottomHUD.visibility != View.VISIBLE) {
            fullscreenBottomHUD.visibility = View.VISIBLE
            fullscreenBottomHUD.animate()
                .alpha(1f)
                .setDuration(250)
                .setListener(null)
                .start()
        }
        
        handler.postDelayed(hideFullscreenHUDRunnable, 2000)
    }

    private fun hideFullscreenHUD() {
        if (!::fullscreenTopHUD.isInitialized || !::fullscreenBottomHUD.isInitialized) return
        
        fullscreenTopHUD.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction { fullscreenTopHUD.visibility = View.GONE }
            .start()
            
        fullscreenBottomHUD.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction { fullscreenBottomHUD.visibility = View.GONE }
            .start()
    }

    private fun updateFullscreenHUDData() {
        if (!::tvFullscreenTimeBattery.isInitialized || !::tvFullscreenProgressLabel.isInitialized || !::pbFullscreenProgress.isInitialized) return
        
        // 1. Update Time
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val currentTimeStr = sdf.format(java.util.Date())
        
        // 2. Update Battery percentage
        val batteryStatus: android.content.Intent? = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else -1
        val batteryStr = if (batteryPct != -1) "  •  🔋 $batteryPct%" else ""
        
        tvFullscreenTimeBattery.text = "$currentTimeStr$batteryStr"
        
        // 3. Update Page Progress Bar
        val currentPage = viewModel.currentPage.value
        val totalPages = viewModel.pagesState.value.size
        if (totalPages > 0) {
            tvFullscreenProgressLabel.text = "Стр. ${currentPage + 1} из $totalPages"
            pbFullscreenProgress.max = totalPages
            pbFullscreenProgress.progress = currentPage + 1
        } else {
            tvFullscreenProgressLabel.text = ""
            pbFullscreenProgress.progress = 0
        }
    }
}
