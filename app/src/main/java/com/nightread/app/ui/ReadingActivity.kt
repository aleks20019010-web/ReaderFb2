package com.nightread.app.ui

import android.os.Bundle
import android.text.TextPaint
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.SettingsManager
import com.nightread.app.service.Fb2Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream

class ReadingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var progressBar: ProgressBar
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var tvPageInfo: TextView
    private lateinit var seekBar: SeekBar
    
    private lateinit var tvTitle: TextView
    private lateinit var btnSettings: ImageButton

    private var sha1: String = ""
    private var bookTitle: String = ""
    private var bookContent: String = ""
    private var splitResult: PageSplitter.PageResult? = null
    private var isBarsVisible = false
    private var isNightMode = false
    private lateinit var gestureDetector: android.view.GestureDetector
    private lateinit var tvBrightness: TextView
    private var startX = 0f
    private var isGestureConsumed = false

    private var lastFontSize = 0f
    private var lastFontFamily = ""
    private var lastFontWeight = ""
    private var lastLineSpacing = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedBrightness = SettingsManager.getBrightness(this)
        if (savedBrightness > 0) {
            BrightnessHelper.setBrightness(this, savedBrightness)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading) // Actually we updated activity_reader.xml but wait, I didn't change setContentView name! Let me use activity_reader.

        viewPager = findViewById(R.id.viewPager)
        progressBar = findViewById(R.id.progressBar)
        topBar = findViewById(R.id.topBar)
        tvBrightness = findViewById(R.id.tvBrightness)
        bottomBar = findViewById(R.id.bottomBar)
        tvPageInfo = findViewById(R.id.tvPageInfo)
        seekBar = findViewById(R.id.seekBar)
        
        tvTitle = findViewById(R.id.tvTitle)
        btnSettings = findViewById(R.id.btnSettings)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        hideSystemBars()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        updateSystemBarsColors()

        sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        if (sha1.isEmpty()) {
            CustomToast.show(this, "Книга не найдена")
            finish()
            return
        }

        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTaskRoot) {
                    startActivity(android.content.Intent(this@ReadingActivity, com.nightread.app.MainActivity::class.java))
                }
                finish()
            }
        })

        setupGestures()
        setupSeekBar()
        
        btnSettings.setOnClickListener {
            SettingsBottomSheet().show(supportFragmentManager, "Settings")
        }

        lastFontSize = SettingsManager.getFontSize(this)
        lastFontFamily = SettingsManager.getFontFamily(this)
        lastFontWeight = SettingsManager.getFontWeight(this)
        lastLineSpacing = SettingsManager.getLineSpacing(this)

        lifecycleScope.launch {
            SettingsManager.settingsChanged.collect {
                updateSystemBarsColors()
                // Check if layout-affecting settings changed
                val newFontSize = SettingsManager.getFontSize(this@ReadingActivity)
                val newFontFamily = SettingsManager.getFontFamily(this@ReadingActivity)
                val newFontWeight = SettingsManager.getFontWeight(this@ReadingActivity)
                val newLineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity)
                
                val layoutChanged = newFontSize != lastFontSize || 
                                   newFontFamily != lastFontFamily || 
                                   newFontWeight != lastFontWeight || 
                                   newLineSpacing != lastLineSpacing

                lastFontSize = newFontSize
                lastFontFamily = newFontFamily
                lastFontWeight = newFontWeight
                lastLineSpacing = newLineSpacing

                if (layoutChanged && bookContent.isNotEmpty() && splitResult != null) {
                    recalculatePages()
                }
            }
        }

        loadBook()
    }

    private fun loadBook() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ReadingActivity)
            val book = withContext(Dispatchers.IO) {
                db.bookDao().getBookBySha1(sha1)
            }

            if (book == null) {
                CustomToast.show(this@ReadingActivity, "Книга не найдена в БД")
                finish()
                return@launch
            }

            bookTitle = book.title
            tvTitle.text = bookTitle

            val filePath = book.filePath
            if (filePath.isNullOrEmpty()) {
                CustomToast.show(this@ReadingActivity, "Путь к файлу пуст")
                finish()
                return@launch
            }

            val file = File(filePath)
            if (!file.exists()) {
                CustomToast.show(this@ReadingActivity, "Файл не найден на диске")
                finish()
                return@launch
            }

            try {
                Log.d("READING_DEBUG", "Loading file: $filePath, size: ${file.length()}")
                
                var rawContent = withContext(Dispatchers.IO) {
                    extractTextFromFile(file)
                }
                
                bookContent = withContext(Dispatchers.Default) {
                    preprocessTextAndHyphenate(rawContent)
                }

                if (bookContent.isEmpty()) {
                    CustomToast.show(this@ReadingActivity, "Не удалось прочитать текст")
                    finish()
                    return@launch
                }

                // Wait for view to be laid out
                viewPager.post {
                    lifecycleScope.launch {
                        recalculatePages(book.currentProgressChar)
                    }
                }
            } catch (e: Exception) {
                Log.e("READING_DEBUG", "Error loading book", e)
                CustomToast.show(this@ReadingActivity, "Ошибка чтения файла")
                finish()
            }
        }
    }

    private fun preprocessTextAndHyphenate(text: String): String {
        var processedText = text.replace(Regex("(\\s*\\n\\s*)+"), "\n    ")
        return com.nightread.app.service.RussianHyphenator.hyphenate(processedText).trim()
    }

    private suspend fun recalculatePages(targetCharOffset: Int = -1) {
        if (!kotlin.coroutines.coroutineContext.isActive) return
        
        val width = viewPager.width
        val height = viewPager.height
        if (width <= 0 || height <= 0) return

        progressBar.visibility = View.VISIBLE

        val paint = TextPaint().apply {
            textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.scaledDensity
            val family = SettingsManager.getFontFamily(this@ReadingActivity)
            val weight = SettingsManager.getFontWeight(this@ReadingActivity)
            val baseTypeface = when (family) {
                "Roboto" -> android.graphics.Typeface.SANS_SERIF
                "Times New Roman" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "Georgia" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "Merriweather" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "OpenDyslexic" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
                "Monospace" -> android.graphics.Typeface.MONOSPACE
                else -> android.graphics.Typeface.DEFAULT
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val numericWeight = when (weight) {
                    "Normal" -> 400
                    "Medium" -> 500
                    "Bold" -> 700
                    "ExtraBold" -> 900
                    else -> 400
                }
                typeface = android.graphics.Typeface.create(baseTypeface, numericWeight, false)
            } else {
                val style = if (weight == "Bold" || weight == "ExtraBold") android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                typeface = android.graphics.Typeface.create(baseTypeface, style)
            }
        }

        // Using display metrics logic inside splitter could be done, but we use match_parent.
        // Wait, PageSplitter doesn't account for padding unless we subtract it from availableWidth.
        val extraHorizontalMargin = resources.getDimensionPixelSize(R.dimen.reader_horizontal_margin)
        val paddingHorizontal = (26 * resources.displayMetrics.density).toInt() + (extraHorizontalMargin * 2)
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset() // 8dp bottom + top inset
        
        val availableWidth = width - paddingHorizontal
        val availableHeight = height - paddingVertical

        Log.d("READING_DEBUG", "Recalculating pages: w=$availableWidth, h=$availableHeight")

        val result = PageSplitter.splitText(
            text = bookContent,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            paint = paint,
            lineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity),
            alignment = "justify"
        )
        
        if (!kotlin.coroutines.coroutineContext.isActive) return

        splitResult = result
        viewPager.adapter = ReaderPagerAdapter(this@ReadingActivity, result.pages)

        var targetPage = 0
        
        if (targetCharOffset >= 0) {
            targetPage = result.offsets.indexOfLast { it <= targetCharOffset }.coerceAtLeast(0)
        } else {
            // Find current offset if re-calculating
            val currentIdx = viewPager.currentItem
            val oldOffsets = splitResult?.offsets
            if (oldOffsets != null && currentIdx < oldOffsets.size) {
                val offset = oldOffsets[currentIdx]
                targetPage = result.offsets.indexOfLast { it <= offset }.coerceAtLeast(0)
            }
        }
        
        if (targetPage < result.pages.size) {
            viewPager.setCurrentItem(targetPage, false)
        }
        updateBottomBar(targetPage)
        
        progressBar.visibility = View.GONE
    }

    private fun getTopInset(): Int {
        val insets = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets, window.decorView)
        val displayCutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        return maxOf(statusBarInsets.top, displayCutoutInsets.top)
    }

    private var startY = 0f
    private var startBrightness = 0f
    private var isChangingBrightness = false

    private fun setupGestures() {
        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                val cornerSize = 80 * resources.displayMetrics.density
                if (e.x < cornerSize && e.y < cornerSize) {
                    toggleNightMode()
                } else {
                    toggleBars()
                }
                return super.onSingleTapConfirmed(e)
            }
        })
        
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateBottomBar(position)
            }
        })
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        val width = window.decorView.width
        val height = window.decorView.height
        
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isChangingBrightness = false
                isGestureConsumed = false
                
                val rightEdge = width - (60 * resources.displayMetrics.density)
                if (event.x > rightEdge && event.y > height / 2f) {
                    if (!isTouchOnUiBars(event)) {
                        isChangingBrightness = true
                        startBrightness = BrightnessHelper.getBrightness(this)
                    }
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isChangingBrightness) {
                    val dy = startY - event.y
                    if (Math.abs(dy) > 10 * resources.displayMetrics.density || isGestureConsumed) {
                        if (!isGestureConsumed) {
                            isGestureConsumed = true
                            val cancelEvent = android.view.MotionEvent.obtain(event)
                            cancelEvent.action = android.view.MotionEvent.ACTION_CANCEL
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                        
                        val deltaBrightness = dy / (height / 2f)
                        val newBrightness = (startBrightness + deltaBrightness).coerceIn(0.01f, 1f)
                        BrightnessHelper.setBrightness(this, newBrightness)
                        com.nightread.app.data.SettingsManager.setBrightness(this, newBrightness)
                        
                        tvBrightness.visibility = android.view.View.VISIBLE
                        tvBrightness.text = "☀ ${(newBrightness * 100).toInt()}%"
                        return true
                    }
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (isGestureConsumed) {
                    tvBrightness.visibility = android.view.View.GONE
                    return true
                }
            }
        }
        
        if (!isTouchOnUiBars(event)) {
            gestureDetector.onTouchEvent(event)
        }
        
        return super.dispatchTouchEvent(event)
    }

    private fun isTouchOnUiBars(event: android.view.MotionEvent): Boolean {
        if (!isBarsVisible) return false
        val rect = android.graphics.Rect()
        topBar.getGlobalVisibleRect(rect)
        if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) return true
        
        bottomBar.getGlobalVisibleRect(rect)
        if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) return true
        
        return false
    }

    private fun toggleBars() {
        isBarsVisible = !isBarsVisible
        topBar.visibility = if (isBarsVisible) View.VISIBLE else View.GONE
        bottomBar.visibility = if (isBarsVisible) View.VISIBLE else View.GONE
        if (isBarsVisible) {
            showSystemBars()
        } else {
            hideSystemBars()
        }
    }

    private fun toggleNightMode() {
        isNightMode = !isNightMode
        val newTheme = if (isNightMode) "dark" else SettingsManager.getPreviousTheme(this)
        SettingsManager.setTheme(this, newTheme)
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && splitResult != null) {
                    val maxPages = splitResult!!.pages.size - 1
                    val targetPage = (progress * maxPages / 100)
                    viewPager.setCurrentItem(targetPage, false)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateBottomBar(position: Int) {
        val total = splitResult?.pages?.size ?: 1
        tvPageInfo.text = "Стр. ${position + 1} из $total"
        if (total > 1) {
            seekBar.progress = (position * 100) / (total - 1)
        } else {
            seekBar.progress = 0
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun updateSystemBarsColors() {
        val themeName = SettingsManager.getTheme(this)
        val bgColor = when (themeName) {
            "light" -> "#FFFFFF"
            "dark" -> "#1A1A1A"
            "sepia" -> "#F5F0E8"
            "sepia_contrast" -> "#F5E6C8"
            "contrast" -> "#000000"
            "beige" -> "#F4ECD8"
            else -> "#F5F0E8"
        }
        val parsedColor = android.graphics.Color.parseColor(bgColor)
        
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        
        window.statusBarColor = parsedColor
        window.navigationBarColor = parsedColor

        // Ensure the root view and window background match the active page theme color
        findViewById<android.view.View>(R.id.rootView)?.setBackgroundColor(parsedColor)
        window.decorView.setBackgroundColor(parsedColor)

        val isLightTheme = when (themeName) {
            "light", "sepia", "sepia_contrast", "beige" -> true
            else -> false
        }
        
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = isLightTheme
            controller.isAppearanceLightNavigationBars = isLightTheme
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val next = viewPager.currentItem + 1
                if (splitResult != null && next < splitResult!!.pages.size) {
                    viewPager.setCurrentItem(next, true)
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val prev = viewPager.currentItem - 1
                if (prev >= 0) {
                    viewPager.setCurrentItem(prev, true)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }

    private fun saveProgress() {
        if (sha1.isEmpty() || splitResult == null) return
        val currentIdx = viewPager.currentItem
        if (currentIdx < splitResult!!.offsets.size) {
            val charOffset = splitResult!!.offsets[currentIdx]
            val totalChars = if (bookContent.isNotEmpty()) bookContent.length else 0
            // Run on an independent scope so it completes even if the activity finishes or its lifecycleScope is cancelled
            val saveScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
            saveScope.launch {
                try {
                    AppDatabase.getDatabase(this@ReadingActivity)
                        .bookDao().updateProgressAndPage(sha1, charOffset, currentIdx, totalChars, System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.e("READING_DEBUG", "Error saving progress in DB", e)
                }
            }
        }
    }

    private fun extractTextFromFile(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "fb2", "xml" -> {
                val xmlContent = readFb2WithEncoding(file)
                Fb2Parser.extractText(xmlContent)
            }
            "zip" -> {
                var content = ""
                FileInputStream(file).use { fis ->
                    ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && (entry.name.endsWith(".fb2") || entry.name.endsWith(".xml"))) {
                                val bytes = zis.readBytes()
                                val xmlContent = decodeFb2Bytes(bytes)
                                content = Fb2Parser.extractText(xmlContent)
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
                content
            }
            "txt" -> file.readText(StandardCharsets.UTF_8)
            else -> ""
        }
    }

    private fun readFb2WithEncoding(file: File): String {
        val bytes = file.readBytes()
        return decodeFb2Bytes(bytes)
    }

    private fun decodeFb2Bytes(bytes: ByteArray): String {
        val header = String(bytes, 0, minOf(bytes.size, 1024), StandardCharsets.ISO_8859_1)
        val match = Regex("""<\?xml[^>]*encoding=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(header)
        val charsetName = match?.groupValues?.get(1)?.trim() ?: "UTF-8"
        return try {
            String(bytes, java.nio.charset.Charset.forName(charsetName))
        } catch (e: Exception) {
            String(bytes, StandardCharsets.UTF_8)
        }
    }
}
