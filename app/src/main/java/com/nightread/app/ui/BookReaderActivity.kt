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

    private lateinit var viewModel: ReaderViewModel
    private var touchStartX: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book)

        readerView = findViewById(R.id.bookReaderView)
        rootLayout = findViewById(R.id.rootView)
        viewModel = ViewModelProvider(this).get(ReaderViewModel::class.java)

        val btnBack = findViewById<View>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val btnSettings = findViewById<View>(R.id.btnSettings)
        btnSettings.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, "settings")
        }

        val topToolbar = findViewById<View>(R.id.topToolbar)
        val bottomToolbar = findViewById<View>(R.id.bottomToolbar)
        var isBarsVisible = true

        readerView.setOnClickListener {
            isBarsVisible = !isBarsVisible
            topToolbar.visibility = if (isBarsVisible) View.VISIBLE else View.GONE
            bottomToolbar.visibility = if (isBarsVisible) View.VISIBLE else View.GONE
        }
        
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

        // Enable full screen edge-to-edge transparency
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
            window.statusBarColor = Color.TRANSPARENT
        }

        // Apply WindowInsets for status bars and navigation bars to prevent overlap with notch/camera cutout
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val density = resources.displayMetrics.density
            
            // 1. Top toolbar handles status bar height
            topToolbar.setPadding(
                topToolbar.paddingLeft,
                statusBarInsets.top,
                topToolbar.paddingRight,
                topToolbar.paddingBottom
            )
            val topParams = topToolbar.layoutParams
            topParams.height = (56 * density).toInt() + statusBarInsets.top
            topToolbar.layoutParams = topParams
            
            // 2. Bottom toolbar handles navigation bar height
            bottomToolbar.setPadding(
                bottomToolbar.paddingLeft,
                bottomToolbar.paddingTop,
                bottomToolbar.paddingRight,
                navBarInsets.bottom
            )
            val bottomParams = bottomToolbar.layoutParams
            bottomParams.height = (56 * density).toInt() + navBarInsets.bottom
            bottomToolbar.layoutParams = bottomParams
            
            // 3. Reader view padding uses 16dp margins on the sides and bottom, and accounts for status bar at the top
            readerView.setPadding(
                (16 * density).toInt(),
                statusBarInsets.top + (8 * density).toInt(),
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
                updatePage()
                updatePageIndicator()
                seekBar.progress = it
            }
        }

        // Collect Pages list
        lifecycleScope.launch {
            viewModel.pagesState.collectLatest {
                updatePage()
                updatePageIndicator()
                seekBar.max = if (it.isNotEmpty()) it.size - 1 else 0
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
                MotionEvent.ACTION_DOWN -> touchStartX = event.x
                MotionEvent.ACTION_UP -> {
                    val diff = event.x - touchStartX
                    if (Math.abs(diff) > 100) {
                        if (diff > 0) viewModel.setCurrentPage(viewModel.currentPage.value - 1)
                        else viewModel.setCurrentPage(viewModel.currentPage.value + 1)
                    }
                }
            }
            true
        }

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
        
        val barBgColor = when (themeKey.lowercase()) {
            "light", "beige" -> Color.parseColor("#EFE9D9")
            "sepia", "sepia_contrast" -> Color.parseColor("#EADFCA")
            "dark", "contrast" -> Color.parseColor("#1E1E1E")
            "amoled" -> Color.parseColor("#0D0D0D")
            else -> Color.parseColor("#EFE9D9")
        }
        
        topToolbar.setBackgroundColor(barBgColor)
        bottomToolbar.setBackgroundColor(barBgColor)
        
        findViewById<TextView>(R.id.tvBookTitle)?.setTextColor(textColor)
        pageIndicatorView.setTextColor(textColor)
        
        val buttonTint = ColorStateList.valueOf(textColor)
        findViewById<ImageButton>(R.id.btnBack).imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnSettings).imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnBookmark).imageTintList = buttonTint
        findViewById<ImageButton>(R.id.btnChapters).imageTintList = buttonTint
        
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        seekBar.progressTintList = buttonTint
        seekBar.thumbTintList = buttonTint
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(
            if (themeKey.lowercase() in listOf("dark", "amoled", "contrast")) Color.DKGRAY else Color.LTGRAY
        )
        
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
            
            val style = if (viewModel.fontWeightState.value == 1) Typeface.BOLD else Typeface.NORMAL
            typeface = when (viewModel.fontFamilyState.value) {
                "Roboto", "Sans Serif" -> Typeface.create(Typeface.SANS_SERIF, style)
                "Serif" -> Typeface.create(Typeface.SERIF, style)
                "Monospace" -> Typeface.create(Typeface.MONOSPACE, style)
                "Merriweather" -> Typeface.create("serif", style)
                else -> Typeface.create(Typeface.DEFAULT, style)
            }
            
            val themeKey = viewModel.themeState.value
            val (_, textColor) = getThemeColors(themeKey)
            color = textColor
        }
        
        val availableWidth = readerView.width - readerView.paddingLeft - readerView.paddingRight
        val align = when (viewModel.fontAlignmentState.value.lowercase()) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        
        val layout = PageSplitter.createStaticLayout(
            text, 0, text.length, paint, availableWidth,
            align,
            viewModel.lineSpacingState.value, 0f, false
        )
        readerView.setLayout(layout)
    }

    private fun updatePageIndicator() {
        val page = viewModel.currentPage.value + 1
        val total = viewModel.pagesState.value.size
        pageIndicatorView.text = "Стр. $page из $total"
    }

    fun loadPage(pageNumber: Int) {
        viewModel.setCurrentPage(pageNumber)
    }

    fun showFootnote(noteId: String) {}
    fun performSmartSearch(word: String) {}
}
