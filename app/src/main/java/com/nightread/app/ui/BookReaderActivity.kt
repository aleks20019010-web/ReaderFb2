package com.nightread.app.ui

import android.content.Intent
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
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent
import android.view.ActionMode
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.SeekBar
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.nightread.app.data.SettingsManager
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


class BookReaderActivity : BaseActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsManager.applyLocale(newBase))
    }

    private var bookReaderFragment: com.nightread.app.ui.BookReaderFragment? = null
    private val noteManager by lazy { com.nightread.app.data.NoteManager(this) }
    private var openedBookSha1: String? = null
    private lateinit var ivBookCoverPage: ImageView
    private lateinit var pageIndicatorView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rootLayout: FrameLayout
    private lateinit var ambientGlowView: View
    private lateinit var amberFilterOverlay: View
    private lateinit var extraDimOverlay: View
    private lateinit var glassyTransitionOverlay: View
    private var isReaderReady = false
    private var hasRunDawnAnimation = false
    private lateinit var topToolbar: View
    private lateinit var bottomToolbar: View
    private lateinit var tvBrightness: TextView
    private lateinit var tvWarmth: TextView
    private var isBarsVisible = true
    private var isPageTurning = false
    private var touchStartY: Float = 0f
    private var touchStartTime: Long = 0L
    private var isDraggingVerticalLeft = false
    private var isDraggingVerticalRight = false
    private var isHorizontalSwipeLocked = false
    private var initialGestureValue = 0f

    // Fullscreen HUD Elements
    private lateinit var fullscreenTopHUD: View
    private lateinit var fullscreenBottomHUD: View
    private lateinit var tvFullscreenTimeBattery: TextView
    private lateinit var tvFullscreenProgressLabel: TextView
    private lateinit var pbFullscreenProgress: ProgressBar
    private var isDraggingVerticalCenter = false
    private val hideFullscreenHUDRunnable = Runnable { hideFullscreenHUD() }
    private val hideIndicatorsRunnable = Runnable {
        if (::tvBrightness.isInitialized) tvBrightness.visibility = View.GONE
        if (::tvWarmth.isInitialized) tvWarmth.visibility = View.GONE
    }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentTextPaint: android.text.TextPaint? = null
    private var isAntiGlareActive = false

    private var isUserTrackingSeekBar = false
    private var seekBarAnimator: android.animation.ValueAnimator? = null
    private var hudProgressAnimator: android.animation.ValueAnimator? = null

    private fun animateSeekBarProgress(seekBar: SeekBar, targetProgress: Int) {
        if (isUserTrackingSeekBar) {
            seekBar.progress = targetProgress
            return
        }
        val currentProgress = seekBar.progress
        if (currentProgress == targetProgress) return

        seekBarAnimator?.cancel()
        if (lastPage == -1) {
            seekBar.progress = targetProgress
        } else {
            seekBarAnimator = android.animation.ObjectAnimator.ofInt(
                seekBar,
                "progress",
                currentProgress,
                targetProgress
            ).apply {
                duration = 300L
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }
        }
    }

    private fun animateFullscreenProgress(progressBar: ProgressBar, targetProgress: Int) {
        val currentProgress = progressBar.progress
        if (currentProgress == targetProgress) return

        hudProgressAnimator?.cancel()
        if (lastPage == -1) {
            progressBar.progress = targetProgress
        } else {
            hudProgressAnimator = android.animation.ObjectAnimator.ofInt(
                progressBar,
                "progress",
                currentProgress,
                targetProgress
            ).apply {
                duration = 300L
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }
        }
    }

    private lateinit var viewModel: ReaderViewModel
    private var touchStartX: Float = 0f
    private var lastPageAnimationIdx: Int = 0
    private var lastPage: Int = -1
    private var lastBookmarkCheckedPageIdx: Int = -1
    private var systemTopInset: Int = 0
    var systemCutoutTop: Int = 0
    private var systemBottomInset: Int = 0
    private var cachedMaxTopInset: Int = 0
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
    private var lastKnownLux: Float? = null
    private var remainingTimeMs: Long = 0
    private var isWebViewLoading = false
    private var brightnessAnimator: android.animation.ValueAnimator? = null
    private var pageScrollAnimator: android.animation.ValueAnimator? = null
    private var longPressRunnable: Runnable? = null
    private var currentLanguage: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLanguage = SettingsManager.getLanguage(this)
        setContentView(R.layout.activity_book)

        lifecycleScope.launch(Dispatchers.IO) {
            com.nightread.app.data.DictionaryDownloader.initDictionaryFromAssets(this@BookReaderActivity)
        }
        com.nightread.app.ui.HyphenatorHelper.init(this)

        ivBookCoverPage = findViewById(R.id.ivBookCoverPage) ?: ImageView(this)
        rootLayout = findViewById(R.id.rootView)
        ambientGlowView = findViewById(R.id.ambientGlowView)
        amberFilterOverlay = findViewById(R.id.amberFilterOverlay)
        extraDimOverlay = findViewById(R.id.extraDimOverlay)
        glassyTransitionOverlay = findViewById(R.id.glassyTransitionOverlay)
        tvBrightness = findViewById(R.id.tvBrightness)
        tvWarmth = findViewById(R.id.tvWarmth)
        
        // Initialize Reader Splash Screen Background
        val readerSplashStarryBg = findViewById<View>(R.id.reader_splash_starry_bg)?.findViewById<com.nightread.app.ui.StarryNightView>(R.id.starryOverlay)
        readerSplashStarryBg?.setFireflyThemeColor(Color.parseColor("#FFE3A8"))

        viewModel = ViewModelProvider(this).get(ReaderViewModel::class.java)

        val btnBack = findViewById<View>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val warningOverlay = findViewById<View>(R.id.accidentalExitWarning)
        var lastBackTime = 0L
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackTime < 2000L) {
                    finish()
                } else {
                    lastBackTime = now
                    warningOverlay.animate().cancel()
                    warningOverlay.visibility = View.VISIBLE
                    warningOverlay.alpha = 0f
                    warningOverlay.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .withEndAction {
                            warningOverlay.animate()
                                .alpha(0f)
                                .setStartDelay(2000)
                                .setDuration(300)
                                .withEndAction { warningOverlay.visibility = View.GONE }
                                .start()
                        }
                        .start()
                }
            }
        })

        val btnSettings = findViewById<View>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, "settings")
        }

        val btnTts = findViewById<ImageButton>(R.id.btnTts)
        btnTts?.visibility = View.VISIBLE
        btnTts?.setOnClickListener {
            val title = findViewById<TextView>(R.id.tvTitle)?.text?.toString() ?: "NightRead"
            val textToSpeak = getTtsTextToSpeak()
            val ttsSheet = TtsSettingsBottomSheet.newInstance(textToSpeak, title)
            ttsSheet.setTtsListener(object : TtsSettingsBottomSheet.TtsSettingsListener {
                override fun onTtsStartRequested(speed: Float, pitch: Float, voiceName: String?, continuous: Boolean) {
                    startOrResumeTts()
                }

                override fun onTtsPauseRequested() {
                    val intent = Intent(this@BookReaderActivity, com.nightread.app.service.TtsForegroundService::class.java).apply {
                        action = com.nightread.app.service.TtsForegroundService.ACTION_PAUSE
                    }
                    startService(intent)
                }

                override fun onTtsStopRequested() {
                    val intent = Intent(this@BookReaderActivity, com.nightread.app.service.TtsForegroundService::class.java).apply {
                        action = com.nightread.app.service.TtsForegroundService.ACTION_STOP
                    }
                    startService(intent)
                }

                override fun onTtsSpeedChanged(speed: Float) {
                    val intent = Intent(this@BookReaderActivity, com.nightread.app.service.TtsForegroundService::class.java).apply {
                        action = com.nightread.app.service.TtsForegroundService.ACTION_SET_SPEED
                        putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_SPEED, speed)
                    }
                    startService(intent)
                }

                override fun onTtsPitchChanged(pitch: Float) {
                    val intent = Intent(this@BookReaderActivity, com.nightread.app.service.TtsForegroundService::class.java).apply {
                        action = com.nightread.app.service.TtsForegroundService.ACTION_SET_PITCH
                        putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_PITCH, pitch)
                    }
                    startService(intent)
                }

                override fun onTtsVoiceChanged(voiceName: String) {
                    val intent = Intent(this@BookReaderActivity, com.nightread.app.service.TtsForegroundService::class.java).apply {
                        action = com.nightread.app.service.TtsForegroundService.ACTION_SET_VOICE
                        putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_VOICE, voiceName)
                    }
                    startService(intent)
                }
            })
            ttsSheet.show(supportFragmentManager, "TtsSettingsBottomSheet")
        }

        val btnSearch = findViewById<ImageButton>(R.id.btnSearch)
        btnSearch.visibility = View.VISIBLE
        btnSearch.setOnClickListener {
            val sheet = BookRagSearchBottomSheet.newInstance()
            sheet.setOnResultSelectedListener { offset, pageIndex ->
                if (offset >= 0) {
                    navigateToOffset(offset)
                } else if (pageIndex >= 0) {
                    loadPage(pageIndex)
                }
            }
            sheet.show(supportFragmentManager, "rag_search")
        }

        val btnChapters = findViewById<ImageButton>(R.id.btnChapters)
        btnChapters.visibility = View.VISIBLE
        btnChapters.setOnClickListener {
            val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
            if (sha1.isNotEmpty()) {
                BookNavigationDialog.newInstance(sha1, 0).show(supportFragmentManager, "navigation")
            }
        }

        val btnNotes = findViewById<ImageButton>(R.id.btnNotes)
        btnNotes?.visibility = View.GONE

        val btnBottomBookmark = findViewById<ImageButton>(R.id.btnBottomBookmark)
        btnBottomBookmark.setOnClickListener {
            toggleBookmark()
        }
        btnBottomBookmark.setOnLongClickListener {
            val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
            if (sha1.isNotEmpty()) {
                BookNavigationDialog.newInstance(sha1, 1).show(supportFragmentManager, "navigation")
            }
            true
        }

        val bookmarkArea = findViewById<View>(R.id.bookmarkArea)
        bookmarkArea.visibility = View.VISIBLE
        bookmarkArea.setOnClickListener {
            toggleBookmark()
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
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeekBar = true
                seekBarAnimator?.cancel()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeekBar = false
                seekBar?.let {
                    viewModel.setCurrentPage(it.progress)
                    bookReaderFragment?.go(it.progress)
                }
            }
        })

        // Enable full screen edge-to-edge transparency and hide status bar safely
        hideSystemUI()

        // Apply WindowInsets for status bars and navigation bars to prevent overlap with notch/camera cutout
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val density = resources.displayMetrics.density
            
            // Calculate top inset safely: preserve cached maximum top inset and maintain a safe minimum (36dp) so text never slides under notch/camera
            val defaultMinTopInset = (36 * density).toInt()
            val currentMeasuredTop = maxOf(statusBarInsets.top, displayCutout.top)
            if (currentMeasuredTop > cachedMaxTopInset) {
                cachedMaxTopInset = currentMeasuredTop
            }
            
            val topInset = maxOf(currentMeasuredTop, cachedMaxTopInset, defaultMinTopInset)
            
            systemTopInset = topInset
            systemCutoutTop = displayCutout.top
            bookReaderFragment?.updateTopMargin(systemCutoutTop)
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
            insets
        }

        // Collect Book Details and load into Book Reader
        lifecycleScope.launch {
            viewModel.bookState.collectLatest { book ->
                if (book != null) {
                    findViewById<TextView>(R.id.tvBookTitle)?.text = book.title
                    findViewById<TextView>(R.id.tvTitle)?.text = book.title
                    if (openedBookSha1 != book.sha1) {
                        openedBookSha1 = book.sha1
                        openBook(book)
                    }
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

        lifecycleScope.launch {
            viewModel.fontSizeState.collectLatest { updatePage() }
        }
        lifecycleScope.launch {
            viewModel.lineSpacingState.collectLatest { updatePage() }
        }
        lifecycleScope.launch {
            viewModel.fontFamilyState.collectLatest { updatePage() }
        }
        lifecycleScope.launch {
            viewModel.fontWeightState.collectLatest { updatePage() }
        }
        lifecycleScope.launch {
            viewModel.pageMarginsState.collectLatest { updatePage() }
        }

        startSilentModeTracker()





        lifecycleScope.launch {
            com.nightread.app.data.SettingsManager.settingsChanged.collectLatest {
                applyScreenSettings()
            }
        }
        applyScreenSettings()

        val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        val targetOffset = intent.getIntExtra("NAVIGATE_TO_OFFSET", -1)
        if (sha1.isNotEmpty()) {
            com.nightread.app.data.SettingsManager.setLastReadBookSha1(this, sha1)
            viewModel.loadBook(sha1, targetOffset)
        }


    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.dispatchTouchEvent(event)
        val screenWidth = rootLayout.width.toFloat()
        val screenHeight = rootLayout.height.toFloat()
        if (screenWidth <= 0 || screenHeight <= 0) return super.dispatchTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchStartTime = System.currentTimeMillis()
                isHorizontalSwipeLocked = false
                isDraggingVerticalLeft = false
                isDraggingVerticalRight = false
                isDraggingVerticalCenter = false
                brightnessAnimator?.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                val diffX = event.x - touchStartX
                val diffY = event.y - touchStartY
                val duration = System.currentTimeMillis() - touchStartTime

                if (Math.abs(diffX) > Math.abs(diffY) * 1.2f && Math.abs(diffX) > 20f) {
                    isHorizontalSwipeLocked = true
                }

                if (!isHorizontalSwipeLocked && duration > 100 && Math.abs(diffY) > 50 && Math.abs(diffY) > Math.abs(diffX) * 2f) {
                    if (!isDraggingVerticalLeft && !isDraggingVerticalRight && !isDraggingVerticalCenter) {
                        if (touchStartX < screenWidth * 0.35f) {
                            isDraggingVerticalLeft = true
                            val lp = window.attributes
                            initialGestureValue = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                        } else if (touchStartX > screenWidth * 0.65f) {
                            isDraggingVerticalRight = true
                            initialGestureValue = com.nightread.app.data.SettingsManager.getAmberFilterIntensity(this@BookReaderActivity).toFloat()
                        } else {
                            isDraggingVerticalCenter = true
                        }
                    }
                }

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
                    return true
                } else if (isDraggingVerticalRight) {
                    val delta = (-diffY / screenHeight) * 100f
                    val newIntensity = (initialGestureValue + delta).coerceIn(0f, 100f).toInt()
                    com.nightread.app.data.SettingsManager.setAmberFilterEnabled(this@BookReaderActivity, true)
                    com.nightread.app.data.SettingsManager.setAmberFilterIntensity(this@BookReaderActivity, newIntensity)
                    applyScreenSettings()
                    
                    tvWarmth.visibility = View.VISIBLE
                    tvWarmth.text = "🌡 $newIntensity%"
                    handler.removeCallbacks(hideIndicatorsRunnable)
                    return true
                } else if (isDraggingVerticalCenter && diffY < 0 && !isBarsVisible) {
                    val dragDistance = -diffY
                    val maxDragDistance = 350f
                    val progress = (dragDistance / maxDragDistance).coerceIn(0f, 1f)
                    showFullscreenHUDProgress(progress)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val diffX = event.x - touchStartX
                val diffY = event.y - touchStartY
                val duration = System.currentTimeMillis() - touchStartTime

                if (isDraggingVerticalLeft || isDraggingVerticalRight) {
                    handler.postDelayed(hideIndicatorsRunnable, 1000)
                    isDraggingVerticalLeft = false
                    isDraggingVerticalRight = false
                    return true
                } else if (!isHorizontalSwipeLocked && isDraggingVerticalCenter && !isBarsVisible && Math.abs(diffY) > Math.abs(diffX) * 2f) {
                    if (diffY < -50) {
                        showFullscreenHUD()
                    } else {
                        hideFullscreenHUD()
                    }
                    isDraggingVerticalCenter = false
                    return true
                } else if (Math.abs(diffX) < 30f && Math.abs(diffY) < 30f && duration < 500) {
                    val touchX = event.x
                    val touchY = event.y
                    
                    if (touchX > screenWidth * 0.35f && touchX < screenWidth * 0.65f &&
                        touchY > screenHeight * 0.3f && touchY < screenHeight * 0.7f) {
                        toggleToolbars()
                        return true
                    }
                }
                
                isDraggingVerticalLeft = false
                isDraggingVerticalRight = false
                isDraggingVerticalCenter = false
            }
        }

        return super.dispatchTouchEvent(event)
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
            bgAnimation.duration = 1000
            bgAnimation.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                rootLayout.setBackgroundColor(color)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                }
            }
            bgAnimation.start()

            val textOldColor = if (::tvFullscreenTimeBattery.isInitialized) tvFullscreenTimeBattery.currentTextColor else textColor
            val txtAnimation = android.animation.ValueAnimator.ofObject(
                android.animation.ArgbEvaluator(),
                textOldColor,
                textColor
            )
            txtAnimation.duration = 1000
            txtAnimation.addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                if (::tvFullscreenTimeBattery.isInitialized) {
                    tvFullscreenTimeBattery.setTextColor(color)
                }
                if (::tvFullscreenProgressLabel.isInitialized) {
                    tvFullscreenProgressLabel.setTextColor(color)
                }
                currentTextPaint?.color = color
                rootLayout.invalidate()
            }
            txtAnimation.start()
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            }
            rootLayout.setBackgroundColor(bgColor)
            if (::tvFullscreenTimeBattery.isInitialized) {
                tvFullscreenTimeBattery.setTextColor(textColor)
            }
            if (::tvFullscreenProgressLabel.isInitialized) {
                tvFullscreenProgressLabel.setTextColor(textColor)
            }
            currentTextPaint?.color = textColor
            rootLayout.invalidate()
        }
        
        val topToolbar = findViewById<View>(R.id.topToolbar)
        val bottomToolbar = findViewById<View>(R.id.bottomToolbar)
        
        topToolbar.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_glass_panel_90)
        bottomToolbar.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_glass_panel_90)
        
        val barTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
        val iconTint = androidx.core.content.ContextCompat.getColor(this, R.color.icon_tint)
        val accentColor = androidx.core.content.ContextCompat.getColor(this, R.color.accent)
        val progressBgColor = androidx.core.content.ContextCompat.getColor(this, R.color.divider)
        
        findViewById<TextView>(R.id.tvBookTitle)?.setTextColor(barTextColor)
        pageIndicatorView.setTextColor(barTextColor)
        
        val buttonTint = ColorStateList.valueOf(iconTint)
        findViewById<ImageButton>(R.id.btnBack)?.imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnSettings)?.imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnTts)?.imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnSearch)?.imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnChapters)?.imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnNotes)?.imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnBottomBookmark)?.imageTintList = buttonTint
        
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.progressTintList = ColorStateList.valueOf(accentColor)
        seekBar.thumbTintList = ColorStateList.valueOf(accentColor)
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(progressBgColor)
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

    private fun openBook(book: com.nightread.app.data.BookEntity) {
        val splashOverlay = findViewById<View>(R.id.reader_splash_overlay)
        splashOverlay?.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val file = java.io.File(book.filePath ?: "")
            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    splashOverlay?.visibility = View.GONE
                    com.nightread.app.ui.CustomToast.show(this@BookReaderActivity, "Файл книги не найден")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                var fragment = supportFragmentManager.findFragmentByTag("book_reader") as? com.nightread.app.ui.BookReaderFragment
                if (fragment == null) {
                    fragment = com.nightread.app.ui.BookReaderFragment()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.bookContainerView, fragment, "book_reader")
                        .commitNowAllowingStateLoss()
                }
                fragment.initBook(file, book.sha1)
                bookReaderFragment = fragment

                updatePage()
            }
        }
    }

    fun showCustomSelectionBottomSheet(selectedText: String, contextSnippet: String) {
        if (selectedText.isBlank()) return
        val sheet = com.nightread.app.ui.SelectionBottomSheet.newInstance(selectedText)
        sheet.setTtsListener { text ->
            val title = findViewById<TextView>(R.id.tvBookTitle).text.toString()
            val speed = com.nightread.app.data.SettingsManager.getTtsSpeed(this)
            val pitch = com.nightread.app.data.SettingsManager.getTtsPitch(this)
            val voice = com.nightread.app.data.SettingsManager.getTtsVoice(this)
            val intent = Intent(this, com.nightread.app.service.TtsForegroundService::class.java).apply {
                action = com.nightread.app.service.TtsForegroundService.ACTION_START
                val charOffset = viewModel.bookState.value?.currentProgressChar ?: 0
                val startIdx = viewModel.getParagraphIndexFromOffset(charOffset)
                putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_START_IDX, startIdx)
                putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_TEXT, text)
                putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_BOOK_TITLE, title)
                putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_SPEED, speed)
                putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_PITCH, pitch)
                putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_VOICE, voice)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        sheet.show(supportFragmentManager, "custom_selection_sheet")
    }



    private fun updatePage() {
        bookReaderFragment?.updatePreferences()
    }

    private fun updatePageIndicator() {
        val cur = bookReaderFragment?.currentPage?.value ?: 0
        val total = bookReaderFragment?.totalPages?.value ?: 1
        val percent = if (total > 1) (cur.toDouble() / (total - 1).toDouble() * 100).toInt() else 0
        findViewById<TextView>(R.id.tvPageIndicator)?.text = "Стр. ${cur + 1} из $total ($percent%)"
        
        // Update dog-ear bookmark status
        val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        val offset = cur

        lifecycleScope.launch(Dispatchers.IO) {
            val db = com.nightread.app.data.BookmarkDatabase.getDatabase(this@BookReaderActivity)
            val isBookmarked = db.bookmarkDao().getBookmarkAtOffset(sha1, offset) != null
            withContext(Dispatchers.Main) {
                val ivDogEar = findViewById<ImageView>(R.id.ivDogEar)
                
                val btnBottomBookmark = findViewById<ImageButton>(R.id.btnBottomBookmark)
                if (btnBottomBookmark != null) {
                    val accentColor = androidx.core.content.ContextCompat.getColor(this@BookReaderActivity, R.color.accent)
                    if (isBookmarked) {
                        btnBottomBookmark.setImageResource(R.drawable.ic_bookmark_filled)
                        btnBottomBookmark.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFB74D.toInt())
                    } else {
                        btnBottomBookmark.setImageResource(R.drawable.ic_bookmark)
                        val buttonTintList = android.content.res.ColorStateList.valueOf(
                            androidx.core.content.ContextCompat.getColor(this@BookReaderActivity, R.color.icon_tint)
                        )
                        btnBottomBookmark.imageTintList = buttonTintList
                    }
                }

                if (ivDogEar != null) {
                    val currentPageIdx = viewModel.currentPage.value
                    val isPageChange = currentPageIdx != lastBookmarkCheckedPageIdx
                    lastBookmarkCheckedPageIdx = currentPageIdx

                    val currentlyVisible = ivDogEar.visibility == View.VISIBLE && ivDogEar.alpha > 0.1f

                    if (isPageChange) {
                        // Instant display without animation during page changes
                        ivDogEar.animate().cancel()
                        ivDogEar.translationY = 0f
                        ivDogEar.alpha = 1.0f
                        ivDogEar.visibility = if (isBookmarked) View.VISIBLE else View.GONE
                    } else {
                        // Interactive toggle on the same page - play gorgeous slide/fade animations
                        val density = resources.displayMetrics.density
                        val slideOffset = if (ivDogEar.height > 0) ivDogEar.height.toFloat() else 33f * density
                        if (isBookmarked && !currentlyVisible) {
                            ivDogEar.animate().cancel()
                            ivDogEar.visibility = View.VISIBLE
                            ivDogEar.translationY = -slideOffset
                            ivDogEar.alpha = 0f
                            ivDogEar.animate()
                                .translationY(0f)
                                .alpha(1.0f)
                                .setDuration(300)
                                .setInterpolator(android.view.animation.DecelerateInterpolator())
                                .start()
                        } else if (!isBookmarked && currentlyVisible) {
                            ivDogEar.animate().cancel()
                            ivDogEar.animate()
                                .translationY(-slideOffset)
                                .alpha(0f)
                                .setDuration(250)
                                .setInterpolator(android.view.animation.AccelerateInterpolator())
                                .withEndAction {
                                    ivDogEar.visibility = View.GONE
                                }
                                .start()
                        }
                    }
                }
            }
        }
    }

    private fun triggerPageTurnHaptic() {
        if (com.nightread.app.data.SettingsManager.isSilentModeEnabled(this) && currentWpm > 400f) {
            return
        }
        if (com.nightread.app.data.SettingsManager.isHapticFeedbackEnabled(this)) {
            rootLayout.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private val activePageView: View
        get() {
            return findViewById<View>(R.id.bookContainerView) ?: rootLayout
        }

    private fun updatePageWithAnimation(newPageIdx: Int) {
        val animMode = com.nightread.app.data.SettingsManager.getPageAnimation(this)
        val pages = viewModel.pagesState.value
        
        val filePath = viewModel.bookState.value?.filePath ?: ""
        val isWebViewBook = com.nightread.app.data.BookFormatHelper.isWebViewBook(filePath)

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
                val isForward = newPageIdx >= lastPageAnimationIdx
                val startTranslationX = if (isForward) screenWidth else -screenWidth
                lastPageAnimationIdx = newPageIdx
                
                currentView.animate()
                    .translationX(if (isForward) -screenWidth else screenWidth)
                    .setDuration(160)
                    .withEndAction {
                        updatePage()
                        val nextView = activePageView
                        nextView.translationX = startTranslationX
                        nextView.animate()
                            .translationX(0f)
                            .setDuration(160)
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

    fun toggleToolbars() {
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
            
            val maxRadius = Math.max(rootLayout.width, rootLayout.height).toFloat()
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

    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
            if (::rootLayout.isInitialized) {
                rootLayout.requestApplyInsets()
            }
        }
    }

    private val ttsStatusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == com.nightread.app.service.TtsForegroundService.BROADCAST_TTS_STATUS) {
                val isSpeaking = intent.getBooleanExtra(com.nightread.app.service.TtsForegroundService.EXTRA_IS_SPEAKING, false)
                val isDone = intent.getBooleanExtra(com.nightread.app.service.TtsForegroundService.EXTRA_UTTERANCE_DONE, false)
                val paragraphId = intent.getStringExtra(com.nightread.app.service.TtsForegroundService.EXTRA_PARAGRAPH_ID)

                if (isSpeaking && !paragraphId.isNullOrEmpty()) {
                    bookReaderFragment?.highlightParagraph(paragraphId)
                    bookReaderFragment?.go(paragraphId)
                } else if (!isSpeaking) {
                    bookReaderFragment?.clearHighlight()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (::rootLayout.isInitialized) {
            rootLayout.requestApplyInsets()
        }
        val lang = SettingsManager.getLanguage(this)
        if (lang != currentLanguage) {
            currentLanguage = lang
            recreate()
            return
        }
        registerSensors()
        animateBrightnessRise()
        if (com.nightread.app.data.SettingsManager.isReaderAutoThemeEnabled(this)) {
            reEvaluateAutoTheme(lastKnownLux)
        }
        val filter = android.content.IntentFilter(com.nightread.app.service.TtsForegroundService.BROADCAST_TTS_STATUS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ttsStatusReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(ttsStatusReceiver, filter)
        }
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        super.onActionModeStarted(mode)
        try {
            mode?.finish()
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(ttsStatusReceiver)
        } catch (e: Exception) {}
        brightnessAnimator?.cancel()
        silentModeJob?.cancel()
        sleepTimerJob?.cancel()
        viewModel.saveProgress()
        unregisterSensors()
        restoreDndFilter()
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveProgress()
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
        val autoThemeEnabled = com.nightread.app.data.SettingsManager.isReaderAutoThemeEnabled(context)
        
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
                sensorManager?.registerListener(lightSensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_FASTEST)
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
        lastKnownLux = lux
        com.nightread.app.data.SettingsManager.setAmbientLux(this, lux)
        val antiGlare = lux > 10000f
        if (isAntiGlareActive != antiGlare) {
            isAntiGlareActive = antiGlare
            updatePage()
        }

        if (com.nightread.app.data.SettingsManager.isAutoBrightnessEnabled(this)) {
            adjustAutoBrightness(lux)
        }
        reEvaluateAutoTheme(lux)
    }

    private var autoBrightnessAnimator: android.animation.ValueAnimator? = null
    private var lastAutoBrightnessTarget: Float = -1f

    fun onAutoBrightnessSettingChanged(enabled: Boolean) {
        if (enabled) {
            lastKnownLux?.let { adjustAutoBrightness(it) }
        } else {
            autoBrightnessAnimator?.cancel()
            animateBrightnessRise()
        }
    }

    private fun adjustAutoBrightness(lux: Float) {
        if (!com.nightread.app.data.SettingsManager.isAutoBrightnessEnabled(this)) {
            autoBrightnessAnimator?.cancel()
            return
        }
        val targetBrightness = when {
            lux <= 50f -> 0.1f
            lux >= 1000f -> 1.0f
            else -> 0.1f + ((lux - 50f) / 950f) * 0.9f
        }.coerceIn(0.1f, 1.0f)

        if (Math.abs(lastAutoBrightnessTarget - targetBrightness) < 0.02f && lastAutoBrightnessTarget != -1f) {
            return
        }
        lastAutoBrightnessTarget = targetBrightness

        val currentBrightness = if (window.attributes.screenBrightness < 0f) {
            0.5f
        } else {
            window.attributes.screenBrightness
        }

        autoBrightnessAnimator?.cancel()
        autoBrightnessAnimator = android.animation.ValueAnimator.ofFloat(currentBrightness, targetBrightness).apply {
            duration = 600
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                val lp = window.attributes
                lp.screenBrightness = value
                window.attributes = lp
            }
            start()
        }
    }

    fun onReaderAutoThemeSettingChanged() {
        updateSensors()
        if (com.nightread.app.data.SettingsManager.isReaderAutoThemeEnabled(this)) {
            reEvaluateAutoTheme(lastKnownLux)
        }
    }

    private fun reEvaluateAutoTheme(lux: Float? = lastKnownLux) {
        if (com.nightread.app.data.SettingsManager.isReaderAutoThemeEnabled(this)) {
            val currentTheme = viewModel.themeState.value
            val preferredDayTheme = com.nightread.app.data.SettingsManager.getUserPreferredDayTheme(this)
            val preferredNightTheme = com.nightread.app.data.SettingsManager.getUserPreferredNightTheme(this)
            
            val currentLux = lux ?: com.nightread.app.data.SettingsManager.getAmbientLux()
            val isNightLux = currentLux < 15f
            val targetTheme = if (isNightLux) preferredNightTheme else preferredDayTheme
            
            if (currentTheme != targetTheme) {
                com.nightread.app.data.SettingsManager.setAutoReadingTheme(this, targetTheme)
                viewModel.setTheme(targetTheme)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        brightnessAnimator?.cancel()
        autoBrightnessAnimator?.cancel()
        sleepTimerJob?.cancel()
        silentModeJob?.cancel()
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

    fun hideReaderSplash() {
        runOnUiThread {
            isReaderReady = true
            val splash = findViewById<View>(R.id.reader_splash_overlay) ?: return@runOnUiThread
            if (splash.visibility == View.GONE || splash.alpha == 0f) return@runOnUiThread
            
            if (!hasRunDawnAnimation) {
                hasRunDawnAnimation = true
            }
            
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

    fun navigateToOffset(offset: Int) {
        val pIndex = viewModel.getParagraphIndexFromOffset(offset)
        bookReaderFragment?.go("p_$pIndex")
    }

    fun loadPage(pageNumber: Int) {
        bookReaderFragment?.go(pageNumber)
    }

    fun navigateToParagraph(pIndex: Int) {
        bookReaderFragment?.go("p_$pIndex")
    }

    fun toggleBookmark() {
        val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        val pageIdx = bookReaderFragment?.currentPage?.value ?: 0
        val offset = pageIdx
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
                        updatePageIndicator()
                    }
                } else {
                    val snippetText = "Страница ${pageIdx + 1}"
                    val newBookmark = com.nightread.app.data.BookmarkEntity(
                        bookSha1 = sha1,
                        bookTitle = title,
                        charOffset = offset,
                        pageIndex = pageIdx,
                        snippet = snippetText,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insertBookmark(newBookmark)
                    withContext(Dispatchers.Main) {
                        CustomToast.show(this@BookReaderActivity, "Закладка добавлена")
                        updatePageIndicator()
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode
        


        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    onReaderSwipeLeft()
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    onReaderSwipeRight()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                onReaderSwipeLeft()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                onReaderSwipeRight()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showFootnote(noteId: String) {}
    fun performSmartSearch(word: String) {}

    fun onWebViewPagesCalculated(totalPages: Int) {
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar?.max = (totalPages - 1).coerceAtLeast(0)
    }
    fun onParagraphVisible(pId: String) {}
    fun onWebViewPageRestored(pageIndex: Int) {}

    fun updateProgressFromFragment(currentPage: Int, totalPages: Int) {
        viewModel.setCurrentPage(currentPage)
        updatePageIndicator()
        
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        if (!isUserTrackingSeekBar && seekBar != null) {
            seekBar.max = (totalPages - 1).coerceAtLeast(0)
            seekBar.progress = currentPage
        }

        findViewById<ProgressBar>(R.id.pbFullscreenProgress)?.let {
            it.max = (totalPages - 1).coerceAtLeast(0)
            it.progress = currentPage
        }
        
        val percent = if (totalPages > 1) currentPage.toDouble() / (totalPages - 1).toDouble() else 0.0
        viewModel.updateProgress(percent)
    }
    private fun ensureWebViewAligned() {}

    fun saveNoteForBook(selectedText: String, noteText: String) {}

    fun showWordActionOrNoteDialog(selectedText: String, contextSnippet: String) {}

    fun fetchAndShowFreeDictionary(word: String) {
        val cleanWord = word.trim().trim(' ', '«', '»', '\"', '\'', '.', ',', '!', '?', ';', ':', '(', ')', '[', ']', '{', '}')
        if (cleanWord.isEmpty()) return

        // 1. Check offline dictionary first
        if (com.nightread.app.data.DictionaryDownloader.isDictionaryDownloaded(this)) {
            try {
                val dictFile = com.nightread.app.data.DictionaryDownloader.getDictionaryFile(this)
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(dictFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                var translation: String? = null

                val tableCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                val tables = mutableListOf<String>()
                while (tableCursor.moveToNext()) {
                    tables.add(tableCursor.getString(0))
                }
                tableCursor.close()

                for (tableName in tables) {
                    if (tableName == "android_metadata") continue
                    try {
                        val colCursor = db.rawQuery("PRAGMA table_info($tableName)", null)
                        val cols = mutableListOf<String>()
                        while (colCursor.moveToNext()) {
                            cols.add(colCursor.getString(1).lowercase())
                        }
                        colCursor.close()

                        val wordCol = cols.firstOrNull { it.contains("word") || it.contains("term") || it.contains("title") } ?: cols.getOrNull(0)
                        val transCol = cols.firstOrNull { it.contains("trans") || it.contains("def") || it.contains("meaning") || it.contains("ru") } ?: cols.getOrNull(1)

                        if (wordCol != null && transCol != null) {
                            val cursor = db.rawQuery("SELECT $transCol FROM $tableName WHERE $wordCol = ? COLLATE NOCASE LIMIT 1", arrayOf(cleanWord))
                            if (cursor.moveToFirst()) {
                                translation = cursor.getString(0)
                                cursor.close()
                                break
                            }
                            cursor.close()
                        }
                    } catch (e: Exception) {
                        // skip table
                    }
                }
                db.close()

                if (!translation.isNullOrBlank()) {
                    val resultHtml = formatHtml("<b><font color='#E94560'>$cleanWord</font></b><br/><br/>Перевод (офлайн): <b><font color='#4CAF50'>$translation</font></b>")
                    runOnUiThread {
                        showDictionaryResultDialog(cleanWord, resultHtml)
                    }
                    return
                }
            } catch (e: Exception) {
                Log.e("BookReaderActivity", "Offline dictionary query error", e)
            }
        }

        // 2. Fallback to online API if offline dictionary not present or word not found
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Словарь")
            .setMessage("Поиск определения для «$cleanWord»...")
            .setCancelable(true)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            val isCyrillic = cleanWord.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
            try {
                if (isCyrillic) {
                    val encodedRu = java.net.URLEncoder.encode(cleanWord, "UTF-8")
                    val translateUrl = "https://api.mymemory.translated.net/get?q=$encodedRu&langpair=ru|en"
                    val enTranslation = fetchTranslationFromMyMemory(translateUrl)

                    if (!enTranslation.isNullOrBlank()) {
                        val encodedEn = java.net.URLEncoder.encode(enTranslation, "UTF-8")
                        val dictUrl = "https://api.dictionaryapi.dev/api/v2/entries/en/$encodedEn"
                        val (code, jsonResponse) = httpGet(dictUrl)

                        val parsedResult: CharSequence = if (code == 200 && !jsonResponse.isNullOrBlank()) {
                            parseFreeDictionaryJson(enTranslation, jsonResponse, ruWord = cleanWord)
                        } else {
                            formatHtml("<b><font color='#E94560'>$cleanWord</font></b><br/><br/>Перевод на английский: <b>$enTranslation</b><br/><br/><i>Определение на английском в словаре не найдено.</i>")
                        }

                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            showDictionaryResultDialog(cleanWord, parsedResult)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            progressDialog.dismiss()
                            showDictionaryResultDialog(cleanWord, "Не удалось найти перевод для «$cleanWord».")
                        }
                    }
                } else {
                    val encodedWord = java.net.URLEncoder.encode(cleanWord, "UTF-8")
                    val dictUrl = "https://api.dictionaryapi.dev/api/v2/entries/en/$encodedWord"
                    val (code, jsonResponse) = httpGet(dictUrl)

                    val translateUrl = "https://api.mymemory.translated.net/get?q=$encodedWord&langpair=en|ru"
                    val ruTranslation = fetchTranslationFromMyMemory(translateUrl)

                    val parsedResult: CharSequence = if (code == 200 && !jsonResponse.isNullOrBlank()) {
                        parseFreeDictionaryJson(cleanWord, jsonResponse, ruTranslation = ruTranslation)
                    } else if (!ruTranslation.isNullOrBlank()) {
                        formatHtml("<b><font color='#E94560'>$cleanWord</font></b><br/><br/>Перевод: <b><font color='#4CAF50'>$ruTranslation</font></b><br/><br/><i>Подробные определения на английском не найдены.</i>")
                    } else {
                        "Определение для «$cleanWord» не найдено в FreeDictionaryAPI."
                    }

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        showDictionaryResultDialog(cleanWord, parsedResult)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showDictionaryResultDialog(cleanWord, "Не удалось получить информацию из словаря: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    fun fetchAndShowYandexDictionary(word: String) {
        fetchAndShowFreeDictionary(word)
    }

    private fun httpGet(urlString: String): Pair<Int, String?> {
        return try {
            val url = java.net.URL(urlString)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            val code = conn.responseCode
            val stream = if (code == 200) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            Pair(code, text)
        } catch (e: Exception) {
            Pair(-1, null)
        }
    }

    private fun fetchTranslationFromMyMemory(urlString: String): String? {
        val (code, response) = httpGet(urlString)
        if (code == 200 && !response.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(response)
                val responseData = json.optJSONObject("responseData")
                val translatedText = responseData?.optString("translatedText")
                if (!translatedText.isNullOrBlank() && !translatedText.equals("NO QUERY SPECIFIED", true)) {
                    return translatedText
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseFreeDictionaryJson(word: String, responseText: String, ruWord: String? = null, ruTranslation: String? = null): CharSequence {
        try {
            val jsonArray = org.json.JSONArray(responseText)
            if (jsonArray.length() == 0) {
                return "Определение для «$word» не найдено."
            }

            val sb = StringBuilder()
            if (ruWord != null) {
                sb.append("<b><font color='#E94560'>$ruWord</font></b> — <i>$word</i><br/><br/>")
            } else {
                sb.append("<b><font color='#E94560'>$word</font></b>")
            }

            val firstObj = jsonArray.getJSONObject(0)
            var phonetic = firstObj.optString("phonetic")
            if (phonetic.isEmpty()) {
                val phoneticsArr = firstObj.optJSONArray("phonetics")
                if (phoneticsArr != null) {
                    for (i in 0 until phoneticsArr.length()) {
                        val pObj = phoneticsArr.getJSONObject(i)
                        val text = pObj.optString("text")
                        if (text.isNotEmpty()) {
                            phonetic = text
                            break
                        }
                    }
                }
            }

            if (phonetic.isNotEmpty()) {
                sb.append(" <font color='#888888'>[$phonetic]</font>")
            }
            sb.append("<br/>")

            if (!ruTranslation.isNullOrBlank()) {
                sb.append("<b>Перевод:</b> <font color='#4CAF50'>$ruTranslation</font><br/>")
            }

            for (i in 0 until jsonArray.length()) {
                val entryObj = jsonArray.getJSONObject(i)
                val meanings = entryObj.optJSONArray("meanings") ?: continue

                for (m in 0 until meanings.length()) {
                    val meaningObj = meanings.getJSONObject(m)
                    val partOfSpeech = meaningObj.optString("partOfSpeech")
                    sb.append("<br/><b><i>$partOfSpeech</i></b><br/>")

                    val definitions = meaningObj.optJSONArray("definitions") ?: continue
                    val maxDefs = minOf(definitions.length(), 4)
                    for (d in 0 until maxDefs) {
                        val defObj = definitions.getJSONObject(d)
                        val definition = defObj.optString("definition")
                        val example = defObj.optString("example")

                        sb.append("• $definition<br/>")
                        if (example.isNotEmpty()) {
                            sb.append("&nbsp;&nbsp;&nbsp;&nbsp;<i><font color='#888888'>“$example”</font></i><br/>")
                        }
                    }
                }
            }

            return formatHtml(sb.toString())
        } catch (e: Exception) {
            return "Ошибка разбора ответа словаря: ${e.message}"
        }
    }

    private fun formatHtml(html: String): CharSequence {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            android.text.Html.fromHtml(html)
        }
    }

    private fun showDictionaryResultDialog(word: String, content: CharSequence) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Словарь: $word")
            .setMessage(content)
            .setPositiveButton("ОК", null)
            .show()
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
                activity.showCustomSelectionBottomSheet(selectedText, contextSnippet)
            }
        }

        @android.webkit.JavascriptInterface
        fun lookupYandexDictionary(word: String) {
            activity.runOnUiThread {
                activity.fetchAndShowFreeDictionary(word)
            }
        }

        @android.webkit.JavascriptInterface
        fun lookupWord(word: String) {
            activity.runOnUiThread {
                activity.fetchAndShowFreeDictionary(word)
            }
        }

        @android.webkit.JavascriptInterface
        fun onWordLongClick(word: String) {}
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
        if (!com.nightread.app.data.SettingsManager.isHapticFeedbackEnabled(this)) return
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

    @android.annotation.SuppressLint("WrongConstant")
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
        
        fullscreenTopHUD.animate().cancel()
        if (fullscreenTopHUD.visibility != View.VISIBLE) {
            fullscreenTopHUD.visibility = View.VISIBLE
        }
        fullscreenTopHUD.animate()
            .alpha(1f)
            .setDuration(250)
            .setListener(null)
            .start()
        
        fullscreenBottomHUD.animate().cancel()
        if (fullscreenBottomHUD.visibility != View.VISIBLE) {
            fullscreenBottomHUD.visibility = View.VISIBLE
        }
        fullscreenBottomHUD.animate()
            .alpha(1f)
            .setDuration(250)
            .setListener(null)
            .start()
        
        handler.postDelayed(hideFullscreenHUDRunnable, 2000)
    }

    private fun showFullscreenHUDProgress(progress: Float) {
        if (isBarsVisible) return // Only show in fullscreen mode
        if (!::fullscreenTopHUD.isInitialized || !::fullscreenBottomHUD.isInitialized) return
        
        updateFullscreenHUDData()
        
        handler.removeCallbacks(hideFullscreenHUDRunnable)
        
        val alphaVal = progress.coerceIn(0f, 1f)
        
        if (fullscreenTopHUD.visibility != View.VISIBLE) {
            fullscreenTopHUD.visibility = View.VISIBLE
        }
        fullscreenTopHUD.animate().cancel()
        fullscreenTopHUD.alpha = alphaVal
        
        if (fullscreenBottomHUD.visibility != View.VISIBLE) {
            fullscreenBottomHUD.visibility = View.VISIBLE
        }
        fullscreenBottomHUD.animate().cancel()
        fullscreenBottomHUD.alpha = alphaVal
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
            animateFullscreenProgress(pbFullscreenProgress, currentPage + 1)
        } else {
            tvFullscreenProgressLabel.text = ""
            pbFullscreenProgress.progress = 0
        }
    }

    private fun getParagraphText(pIndex: Int): String {
        if (BookCache.content.isEmpty()) return ""
        val totalParagraphs = BookCache.totalParagraphCount
        if (pIndex !in 0 until totalParagraphs) return ""
        val startOffset = viewModel.getOffsetForParagraphIndex(pIndex)
        val endOffset = if (pIndex + 1 < totalParagraphs) {
            viewModel.getOffsetForParagraphIndex(pIndex + 1)
        } else {
            BookCache.content.length
        }
        if (startOffset in 0..BookCache.content.length && endOffset in startOffset..BookCache.content.length) {
            val raw = BookCache.content.substring(startOffset, endOffset)
            return raw.replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'")
                .replace("&#171;", "«")
                .replace("&#187;", "»")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
        return ""
    }

    /**
     * Переключает тему приложения между светлой и тёмной через AppCompatDelegate.setDefaultNightMode().
     */
    fun toggleTheme() {
        val currentNightMode = resources.configuration.uiMode.and(android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        val targetMode = if (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        } else {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        }
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    fun onReaderSwipeLeft() {
        if (isPageTurning) return
        if (bookReaderFragment != null && bookReaderFragment?.isVisible == true) {
            isPageTurning = true
            bookReaderFragment?.goForward(animated = true)
            handler.postDelayed({ isPageTurning = false }, 250)
        }
    }

    fun onReaderSwipeRight() {
        if (isPageTurning) return
        if (bookReaderFragment != null && bookReaderFragment?.isVisible == true) {
            isPageTurning = true
            bookReaderFragment?.goBackward(animated = true)
            handler.postDelayed({ isPageTurning = false }, 250)
        }
    }

    fun onReaderTapLeft() {
        onReaderSwipeRight()
    }

    fun onReaderTapRight() {
        onReaderSwipeLeft()
    }

    fun adjustBrightnessByDrag(distanceY: Float) {
        val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1000f)
        val delta = distanceY / screenHeight
        val lp = window.attributes
        val current = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
        val newBrightness = (current + delta).coerceIn(0.01f, 1.0f)
        lp.screenBrightness = newBrightness
        window.attributes = lp
        com.nightread.app.data.SettingsManager.setBrightness(this, newBrightness)

        tvBrightness.visibility = View.VISIBLE
        tvBrightness.text = "☀ ${(newBrightness * 100).toInt()}%"
        handler.removeCallbacks(hideIndicatorsRunnable)
    }

    fun adjustWarmthByDrag(distanceY: Float) {
        val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1000f)
        val currentIntensity = com.nightread.app.data.SettingsManager.getAmberFilterIntensity(this).toFloat()
        val delta = (distanceY / screenHeight) * 100f
        val newIntensity = (currentIntensity + delta).coerceIn(0f, 100f).toInt()
        com.nightread.app.data.SettingsManager.setAmberFilterEnabled(this, true)
        com.nightread.app.data.SettingsManager.setAmberFilterIntensity(this, newIntensity)
        applyScreenSettings()

        tvWarmth.visibility = View.VISIBLE
        tvWarmth.text = "🌡 $newIntensity%"
        handler.removeCallbacks(hideIndicatorsRunnable)
    }

    fun onGestureEnded() {
        handler.postDelayed(hideIndicatorsRunnable, 1000)
    }

    fun getOpenedBookTitle(): String {
        return viewModel.bookState.value?.title ?: findViewById<TextView>(R.id.tvTitle)?.text?.toString() ?: "NightRead"
    }

    fun getTtsTextToSpeak(): String {
        return ""
    }

    fun startOrResumeTts() {
        val speed = com.nightread.app.data.SettingsManager.getTtsSpeed(this)
        val pitch = com.nightread.app.data.SettingsManager.getTtsPitch(this)
        val voice = com.nightread.app.data.SettingsManager.getTtsVoice(this)
        val title = getBookTitle()
        
        val filePath = viewModel.bookState.value?.filePath ?: ""
        val isWebViewBook = com.nightread.app.data.BookFormatHelper.isWebViewBook(filePath)
                           
        var startIdx = 0
        if (isWebViewBook) {
            startIdx = viewModel.bookState.value?.currentProgressChar ?: 0
        } else {
            val charOffset = viewModel.getOffsetForPage(viewModel.currentPage.value)
            startIdx = viewModel.getParagraphIndexFromOffset(charOffset)
        }
        
        if (!isWebViewBook && com.nightread.app.ui.BookCache.content.isNotEmpty()) {
            val lines = com.nightread.app.ui.BookCache.content.split("\n")
            val paragraphs = mutableListOf<com.nightread.app.service.TtsParagraph>()
            var currentOffset = 0
            var pIndex = 0
            val charOffsetForText = viewModel.getOffsetForPage(viewModel.currentPage.value)
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    var remaining = trimmed
                    while (remaining.isNotEmpty()) {
                        val chunk = if (remaining.length > 3800) remaining.substring(0, 3800) else remaining
                        remaining = if (remaining.length > 3800) remaining.substring(3800) else ""
                        paragraphs.add(com.nightread.app.service.TtsParagraph("p_$pIndex", chunk))
                        if (charOffsetForText >= currentOffset && charOffsetForText <= currentOffset + line.length) {
                            startIdx = pIndex
                        }
                        pIndex++
                    }
                }
                currentOffset += line.length + 1
            }
            com.nightread.app.service.TtsDataProvider.paragraphs = paragraphs
        }

        val intent = Intent(this, com.nightread.app.service.TtsForegroundService::class.java).apply {
            action = com.nightread.app.service.TtsForegroundService.ACTION_START
            putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_START_IDX, startIdx)
            putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_BOOK_TITLE, title)
            putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_SPEED, speed)
            putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_PITCH, pitch)
            putExtra(com.nightread.app.service.TtsForegroundService.EXTRA_VOICE, voice)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    fun getBookTitle(): String {
        return viewModel.bookState.value?.title ?: findViewById<TextView>(R.id.tvTitle)?.text?.toString() ?: "NightRead"
    }

    fun pauseTts() {
        val intent = Intent(this, com.nightread.app.service.TtsForegroundService::class.java).apply {
            action = com.nightread.app.service.TtsForegroundService.ACTION_PAUSE
        }
        startService(intent)
    }

    fun stopTts() {
        val intent = Intent(this, com.nightread.app.service.TtsForegroundService::class.java).apply {
            action = com.nightread.app.service.TtsForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    fun readNextTtsChunk() {
        onReaderSwipeLeft()
        handler.postDelayed({ startOrResumeTts() }, 400)
    }

    fun readPreviousTtsChunk() {
        onReaderSwipeRight()
        handler.postDelayed({ startOrResumeTts() }, 400)
    }



}
