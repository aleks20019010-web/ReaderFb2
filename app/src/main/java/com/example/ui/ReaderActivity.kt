package com.example.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.flow.collect
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
    private lateinit var fontSizeValueText: TextView
    private lateinit var spacingValueText: TextView

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
        
        // 1. Edge-To-Edge rendering
        WindowCompat.setDecorFitsSystemWindows(window, false)

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

        // Bottom Page Indicator (centered above bottom of screen)
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
            setPadding((16 * density).toInt(), (28 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            visibility = View.GONE
            alpha = 0f
            
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#F21A1A1A")) // Elegant semi-transparent dark bar
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

        val backButton = TextView(this).apply {
            text = "◀  Назад"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((10 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            setOnClickListener {
                finish()
            }
        }
        topControlBar.addView(backButton)

        bookTitleText = TextView(this).apply {
            text = "Чтение"
            setTextColor(Color.WHITE)
            textSize = 15f
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxLines = 1
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        topControlBar.addView(bookTitleText)

        val themeToggleButton = TextView(this).apply {
            text = "☼ / 🌙"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding((16 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            setOnClickListener {
                viewModel.toggleTheme()
            }
        }
        topControlBar.addView(themeToggleButton)
        rootLayout.addView(topControlBar)

        // --- BOTTOM CONTROL BAR OVERLAY (FONT & SPACING CONTROLS) ---
        bottomControlBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            visibility = View.GONE
            alpha = 0f
            
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#F21A1A1A"))
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

        // Font size control row
        val fontRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (8 * density).toInt())
            }
        }

        val fontLabel = TextView(this).apply {
            text = "Шрифт:"
            setTextColor(Color.LTGRAY)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams((70 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fontRow.addView(fontLabel)

        val fontMinus = TextView(this).apply {
            text = " А - "
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((16 * density).toInt(), (6 * density).toInt(), (16 * density).toInt(), (6 * density).toInt())
            setOnClickListener {
                viewModel.changeFontSize(-1f)
            }
        }
        fontRow.addView(fontMinus)

        fontSizeValueText = TextView(this).apply {
            text = "18"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fontRow.addView(fontSizeValueText)

        val fontPlus = TextView(this).apply {
            text = " А + "
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((16 * density).toInt(), (6 * density).toInt(), (16 * density).toInt(), (6 * density).toInt())
            setOnClickListener {
                viewModel.changeFontSize(1f)
            }
        }
        fontRow.addView(fontPlus)
        bottomControlBar.addView(fontRow)

        // Line Spacing control row
        val spacingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val spacingLabel = TextView(this).apply {
            text = "Интервал:"
            setTextColor(Color.LTGRAY)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams((70 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        spacingRow.addView(spacingLabel)

        val spacingMinus = TextView(this).apply {
            text = " S - "
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((16 * density).toInt(), (6 * density).toInt(), (16 * density).toInt(), (6 * density).toInt())
            setOnClickListener {
                viewModel.changeLineSpacing(-0.1f)
            }
        }
        spacingRow.addView(spacingMinus)

        spacingValueText = TextView(this).apply {
            text = "1.4"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        spacingRow.addView(spacingValueText)

        val spacingPlus = TextView(this).apply {
            text = " S + "
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((16 * density).toInt(), (6 * density).toInt(), (16 * density).toInt(), (6 * density).toInt())
            setOnClickListener {
                viewModel.changeLineSpacing(0.1f)
            }
        }
        spacingRow.addView(spacingPlus)
        bottomControlBar.addView(spacingRow)

        val helpTipText = TextView(this).apply {
            text = "Свайп по краям экрана регулирует яркость"
            setTextColor(Color.GRAY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        bottomControlBar.addView(helpTipText)
        rootLayout.addView(bottomControlBar)

        setContentView(rootLayout)

        // Hide bars initially
        hideSystemUi()

        // 4. Dynamic Dimensions listener to send boundaries to pagination engine
        rootLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val screenWidth = rootLayout.width
            val screenHeight = rootLayout.height
            val currentDensity = resources.displayMetrics.density
            
            // Available text viewport size subtracting padding limits
            val topPadding = (54 * currentDensity).toInt()
            val leftPadding = (16 * currentDensity).toInt()
            val rightPadding = (16 * currentDensity).toInt()
            val bottomPadding = (48 * currentDensity).toInt()
            
            val availableW = screenWidth - leftPadding - rightPadding
            val availableH = screenHeight - topPadding - bottomPadding
            
            if (availableW > 0 && availableH > 0) {
                viewModel.updateDimensions(availableW, availableH, currentDensity)
            }
        }

        // 5. Collect ViewModel States to update the UI
        lifecycleScope.launch {
            viewModel.bookState.collect { book ->
                book?.let {
                    bookTitleText.text = it.title
                }
            }
        }

        lifecycleScope.launch {
            viewModel.pagesState.collect { pages ->
                if (pages.isNotEmpty()) {
                    val adapter = ReaderPagerAdapter(this@ReaderActivity, pages)
                    viewPager.adapter = adapter
                    viewPager.setCurrentItem(viewModel.currentPage.value, false)
                    updatePageIndicator(viewModel.currentPage.value, pages.size)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentPage.collect { page ->
                if (viewPager.currentItem != page) {
                    viewPager.setCurrentItem(page, false)
                }
                val total = viewModel.pagesState.value.size
                if (total > 0) {
                    updatePageIndicator(page, total)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.themeState.collect { theme ->
                applyTheme(theme)
            }
        }

        lifecycleScope.launch {
            viewModel.fontSizeState.collect { size ->
                fontSizeValueText.text = String.format("%.0f", size)
            }
        }

        lifecycleScope.launch {
            viewModel.lineSpacingState.collect { spacing ->
                spacingValueText.text = String.format("%.1f", spacing)
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setCurrentPage(position)
            }
        })
    }

    private fun updatePageIndicator(currentPage: Int, totalPages: Int) {
        pageIndicator.text = "${currentPage + 1} из $totalPages"
    }

    private fun applyTheme(theme: String) {
        if (theme == "night") {
            rootLayout.setBackgroundColor(Color.parseColor("#1a1a1a"))
            pageIndicator.setTextColor(Color.parseColor("#80e0e0e0"))
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#f5f0e8"))
            pageIndicator.setTextColor(Color.parseColor("#803a3a3a"))
        }
    }

    fun toggleSystemUi() {
        if (isSystemUiVisible) {
            hideSystemUi()
            hideControlPanels()
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
