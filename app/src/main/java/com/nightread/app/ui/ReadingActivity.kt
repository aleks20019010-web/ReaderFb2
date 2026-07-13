package com.nightread.app.ui

import android.os.Bundle
import android.content.Context
import android.text.TextPaint
import android.util.Log
import com.nightread.app.service.BookParser
import com.nightread.app.service.EpubParser
import com.nightread.app.service.MobiParser
import com.nightread.app.service.TxtParser
import com.nightread.app.ui.NoteBottomSheet
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
import kotlinx.coroutines.flow.collectLatest
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
    private lateinit var btnBookmark: ImageButton
    private lateinit var btnBookmarksList: ImageButton
    private lateinit var btnChaptersList: ImageButton
    private lateinit var fabBookmark: FloatingActionButton

    private var sha1: String = ""
    private var bookTitle: String = ""
    private var bookContent: String = ""
    private var bookNotes: Map<String, String> = emptyMap()
    private var splitResult = PageSplitter.PageResult()
    private var progressiveJob: kotlinx.coroutines.Job? = null
    private var isSplittingFinished = false
    private var isBarsVisible = false
    private var isNightMode = false
    private var sensorManager: android.hardware.SensorManager? = null
    private var lightSensor: android.hardware.Sensor? = null
    private var lightSensorListener: android.hardware.SensorEventListener? = null
    private var lastThemeTransitionTime: Long = 0L
    private var viewAmberFilter: android.view.View? = null
    private var viewExtraDim: android.view.View? = null
    private var tvSleepTimerIndicator: TextView? = null
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var sleepTimerRemainingSeconds: Int = 0
    private var accelerometer: android.hardware.Sensor? = null
    private var shakeListener: android.hardware.SensorEventListener? = null
    private var lastShakeTime: Long = 0L
    private lateinit var gestureDetector: android.view.GestureDetector
    private lateinit var tvBrightness: TextView
    private var startX = 0f
    private var isGestureConsumed = false

    private var lastFontSize = 0f
    private var lastFontFamily = ""
    private var lastFontWeight = ""
    private var lastLineSpacing = 0f
    private var lastPageAnimation = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedBrightness = SettingsManager.getBrightness(this)
        if (savedBrightness > 0) {
            BrightnessHelper.setBrightness(this, savedBrightness)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading) // Actually we updated activity_reader.xml but wait, I didn't change setContentView name! Let me use activity_reader.

        viewPager = findViewById(R.id.viewPager)
        viewAmberFilter = findViewById(R.id.viewAmberFilter)
        viewExtraDim = findViewById(R.id.viewExtraDim)
        tvSleepTimerIndicator = findViewById(R.id.tvSleepTimerIndicator)
        updateAmberFilter()
        updateExtraDim()
        updateSleepTimer()
        updatePageTransformer()
        progressBar = findViewById(R.id.progressBar)
        topBar = findViewById(R.id.topBar)
        tvBrightness = findViewById(R.id.tvBrightness)
        bottomBar = findViewById(R.id.bottomBar)
        tvPageInfo = findViewById(R.id.tvPageInfo)
        seekBar = findViewById(R.id.seekBar)
        btnChaptersList = findViewById(R.id.btnChaptersList)
        btnChaptersList.setOnClickListener {
            showChaptersDialog()
        }
        
        tvTitle = findViewById(R.id.tvTitle)
        btnSettings = findViewById(R.id.btnSettings)
        btnBookmark = findViewById(R.id.btnBookmark)
        btnBookmark.setOnClickListener {
            toggleBookmark()
        }
        btnBookmarksList = findViewById(R.id.btnBookmarksList)
        btnBookmarksList.setOnClickListener {
            if (sha1.isNotEmpty()) {
                BookmarksListBottomSheet.newInstance(sha1).show(supportFragmentManager, "BookmarksList")
            }
        }
        fabBookmark = findViewById(R.id.fabBookmark)
        fabBookmark.setOnClickListener {
            toggleBookmark()
        }
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        hideSystemBars()
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        fabBookmark.visibility = View.GONE
        isBarsVisible = false
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
        SettingsManager.setLastReadBookSha1(this, sha1)
        SettingsManager.setCurrentlyReading(this, true)

        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                SettingsManager.setCurrentlyReading(this@ReadingActivity, false)
                if (isTaskRoot) {
                    val mainIntent = android.content.Intent(this@ReadingActivity, com.nightread.app.MainActivity::class.java).apply {
                        putExtra("PREVENT_AUTO_OPEN", true)
                    }
                    startActivity(mainIntent)
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
        lastPageAnimation = SettingsManager.getPageAnimation(this)

        val startReadingTheme = SettingsManager.getReadingTheme(this)
        isNightMode = startReadingTheme == "dark" || startReadingTheme == "contrast"

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        lightSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)

        lifecycleScope.launch {
            SettingsManager.settingsChanged.collectLatest {
                updateSystemBarsColors()
                updatePageTransformer()
                updateAmberFilter()
                updateExtraDim()
                updateSleepTimer()
                
                // Re-evaluate light sensor listener state on settings change
                if (SettingsManager.isAutoLightNightEnabled(this@ReadingActivity)) {
                    registerLightSensor()
                } else {
                    unregisterLightSensor()
                }
                // Check if layout-affecting settings changed
                val newFontSize = SettingsManager.getFontSize(this@ReadingActivity)
                val newFontFamily = SettingsManager.getFontFamily(this@ReadingActivity)
                val newFontWeight = SettingsManager.getFontWeight(this@ReadingActivity)
                val newLineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity)
                
                val layoutChanged = newFontSize != lastFontSize || 
                                   newFontFamily != lastFontFamily || 
                                   newFontWeight != lastFontWeight ||
                                   newLineSpacing != lastLineSpacing

                if (layoutChanged) {
                    kotlinx.coroutines.delay(300) // Debounce fast slider changes
                }

                android.util.Log.d("ReadingActivity", "Settings changed. Layout changed: $layoutChanged")

                lastFontSize = newFontSize
                lastFontFamily = newFontFamily
                lastFontWeight = newFontWeight
                lastLineSpacing = newLineSpacing

                if (layoutChanged && bookContent.isNotEmpty() && splitResult != null) {
                    val currentIdx = viewPager.currentItem
                    val targetOffset = if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                        splitResult.offsets[currentIdx]
                    } else {
                        -1
                    }
                    recalculatePages(targetOffset)
                }
            }
        }

        loadBook()
        
        var lastWidth = 0
        var lastHeight = 0
        viewPager.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight) && lastWidth != 0 && lastHeight != 0) {
                lastWidth = w
                lastHeight = h
                if (bookContent.isNotEmpty() && isSplittingFinished) {
                    val currentIdx = viewPager.currentItem
                    val targetOffset = if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                        splitResult.offsets[currentIdx]
                    } else {
                        -1
                    }
                    lifecycleScope.launch {
                        recalculatePages(targetOffset)
                    }
                }
            } else if (w > 0 && h > 0) {
                lastWidth = w
                lastHeight = h
            }
        }
        startEyeComfortFadeIn()
    }

    private fun loadBook() {
        progressBar.visibility = View.VISIBLE
        val tvLoadingProgress = findViewById<TextView>(R.id.tvLoadingProgress)
        tvLoadingProgress.visibility = View.VISIBLE
        tvLoadingProgress.text = "Загрузка книги..."

        // Check cache before loading
        if (BookCache.sha1 != sha1) {
            Log.i("READING_DEBUG", "SHA-1 mismatch: Cache has '${BookCache.sha1}', opening '$sha1'. Clearing BookCache.")
            BookCache.clear()
        } else {
            Log.i("READING_DEBUG", "SHA-1 matches cache: '$sha1'. Reusing BookCache.")
        }

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

            // Update lastReadTime immediately when opening the book
            withContext(Dispatchers.IO) {
                db.bookDao().updateProgress(sha1, book.currentProgressChar, System.currentTimeMillis())
            }

            try {
                if (BookCache.sha1 == sha1 && BookCache.content.isNotEmpty()) {
                    tvLoadingProgress.text = "Книга загружена из кэша..."
                    Log.i("READING_DEBUG", "Reusing cached book content for SHA-1: $sha1")
                    bookContent = BookCache.content
                    bookNotes = BookCache.notes
                } else {
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
                    tvLoadingProgress.text = "Чтение файла..."
                    Log.d("READING_DEBUG", "Loading file: $filePath, size: ${file.length()}")
                    
                    val parsedBook = withContext(Dispatchers.IO) {
                        parseBookFile(file)
                    }
                    
                    tvLoadingProgress.text = "Обработка текста..."
                    bookContent = withContext(Dispatchers.Default) {
                        parsedBook.content.trim().trim('\u000C').trim()
                    }
                    bookNotes = parsedBook.notes
                    
                    if (bookContent.isEmpty()) {
                        CustomToast.show(this@ReadingActivity, "Не удалось прочитать текст")
                        finish()
                        return@launch
                    }
                    
                    BookCache.sha1 = sha1
                    BookCache.content = bookContent
                    BookCache.notes = bookNotes
                    Log.i("READING_DEBUG", "Successfully loaded book from disk and updated BookCache for SHA-1: $sha1")
                }

                // Wait for view to be laid out
                viewPager.post {
                    lifecycleScope.launch {
                        val targetOffset = intent.getIntExtra("TARGET_OFFSET", -1)
                        if (targetOffset >= 0) {
                            recalculatePages(targetOffset)
                        } else {
                            val localOffset = book.currentProgressChar
                            recalculatePages(localOffset)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                Log.e("READING_DEBUG", "Error loading book", e)
                if (bookContent.isNotEmpty() || (BookCache.sha1 == sha1 && BookCache.content.isNotEmpty())) {
                    Log.d("READING_DEBUG", "Suppressed non-critical loading error because book content is already available.")
                } else {
                    CustomToast.show(this@ReadingActivity, "Ошибка чтения файла")
                    finish()
                }
            }
        }
    }
    private fun preprocessTextAndHyphenate(text: String): String {
        var processedText = text.replace(Regex("([ \\t\\r\\n]*\\n[ \\t\\r\\n]*)+"), "\n    ")
        processedText = processedText.trim().trim('\u000C').trim()
        HyphenationPatterns.load("ru")
        val result = com.nightread.app.ui.HyphenatorHelper.hyphenate(processedText)
        val hyphensInResult = result.count { it == '\u00AD' }
        Log.d("ReadingActivity", "preprocessTextAndHyphenate: original text length: ${text.length}, processed length: ${result.length}, contains $hyphensInResult soft hyphens.")
        Log.d("ReadingActivity", "Sample: ${result.take(200)}")
        return result
    }

    private suspend fun recalculatePages(targetCharOffset: Int = -1) {
        if (!kotlin.coroutines.coroutineContext.isActive) return
        
        var resolvedCharOffset = targetCharOffset
        if (resolvedCharOffset < 0 && splitResult != null) {
            val currentIdx = viewPager.currentItem
            if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                resolvedCharOffset = splitResult.offsets[currentIdx]
            }
        }
        
        val width = viewPager.width
        val height = viewPager.height
        if (width <= 0 || height <= 0) return

        progressBar.visibility = View.VISIBLE
        val tvLoadingProgress = findViewById<TextView>(R.id.tvLoadingProgress)
        tvLoadingProgress.visibility = View.VISIBLE
        tvLoadingProgress.text = "Разбивка на страницы..."

        val paint = TextPaint().apply {
            textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.scaledDensity
            val family = SettingsManager.getFontFamily(this@ReadingActivity)
            val numericWeight = SettingsManager.getFontWeightAsInt(this@ReadingActivity)
            typeface = FontUtils.createTypeface(family, numericWeight)
        }

        // Match padding in PageFragment: 16dp left + 16dp right = 32dp
        val paddingHorizontal = (32 * resources.displayMetrics.density).toInt()
        // Match padding in PageFragment: 8dp top + 8dp bottom = 16dp + topInset
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()
        
        val availableWidth = width - paddingHorizontal
        val availableHeight = height - paddingVertical

        val hyphenationEnabled = com.nightread.app.data.SettingsManager.isHyphenationEnabled(this@ReadingActivity)
        val currentKey = "${width}_${height}_${paint.textSize}_${SettingsManager.getFontFamily(this@ReadingActivity)}_${SettingsManager.getFontWeightAsInt(this@ReadingActivity)}_${SettingsManager.getLineSpacing(this@ReadingActivity)}_hyphen=$hyphenationEnabled"
        if (BookCache.sha1 == sha1 && BookCache.layoutKey == currentKey && BookCache.splitResult?.isFinished == true) {
            splitResult = BookCache.splitResult!!
            isSplittingFinished = true
            tvLoadingProgress.visibility = View.GONE
            progressBar.visibility = View.GONE
            
            if (viewPager.adapter == null) {
                viewPager.adapter = ReaderPagerAdapter(this@ReadingActivity, splitResult.pages)
            } else {
                (viewPager.adapter as ReaderPagerAdapter).pages = splitResult.pages
                viewPager.adapter?.notifyDataSetChanged()
            }
            
            var targetPage = 0
            if (resolvedCharOffset >= 0) {
                targetPage = splitResult.offsets.indexOfLast { it <= resolvedCharOffset }.coerceAtLeast(0)
            } else {
                val currentIdx = viewPager.currentItem
                val oldOffsets = splitResult.offsets
                if (currentIdx < oldOffsets.size) {
                    val offset = oldOffsets[currentIdx]
                    targetPage = splitResult.offsets.indexOfLast { it <= offset }.coerceAtLeast(0)
                }
            }
            if (targetPage < splitResult.pages.size) {
                viewPager.setCurrentItem(targetPage, false)
            }
            updateBottomBar(targetPage)
            showBarsWithAnimation(animateFab = true)
            return
        }

        progressiveJob?.cancel()
        viewPager.adapter = ReaderPagerAdapter(this@ReadingActivity, splitResult.pages)
        isSplittingFinished = false
        var isFirstRender = true

        val textToSplit = if (hyphenationEnabled) {
            if (BookCache.sha1 == this.sha1 && BookCache.isHyphenated == true && BookCache.hyphenatedContent != null) {
                BookCache.hyphenatedContent!!
            } else {
                val hyphenated = withContext(Dispatchers.Default) {
                    com.nightread.app.ui.HyphenationPatterns.load("ru")
                    com.nightread.app.ui.HyphenatorHelper.hyphenate(bookContent)
                }
                BookCache.hyphenatedContent = hyphenated
                BookCache.isHyphenated = true
                hyphenated
            }
        } else {
            BookCache.isHyphenated = false
            BookCache.hyphenatedContent = null
            bookContent
        }

        progressiveJob = lifecycleScope.launch {
            PageSplitter.splitTextProgressive(
                text = textToSplit,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                paint = paint,
                lineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity),
                alignment = "left",
                isHyphenationEnabled = SettingsManager.isHyphenationEnabled(this@ReadingActivity)
            ) { result ->
                val oldCount = splitResult.pages.size
                splitResult = result
                isSplittingFinished = result.isFinished
                
                val adapter = viewPager.adapter as ReaderPagerAdapter
                adapter.pages = result.pages
                val newCount = result.pages.size
                
                if (isFirstRender) {
                    adapter.notifyDataSetChanged()
                } else if (newCount > oldCount) {
                    adapter.notifyItemRangeInserted(oldCount, newCount - oldCount)
                }
                
                if (isFirstRender && result.pages.isNotEmpty()) {
                    isFirstRender = false
                    progressBar.visibility = View.GONE
                    tvLoadingProgress.visibility = View.GONE
                    
                    var targetPage = 0
                    if (resolvedCharOffset >= 0) {
                        targetPage = result.offsets.indexOfLast { it <= resolvedCharOffset }.coerceAtLeast(0)
                    } else {
                        targetPage = 0
                    }
                    if (targetPage < result.pages.size) {
                        viewPager.setCurrentItem(targetPage, false)
                    }
                    updateBottomBar(targetPage)
                    showBarsWithAnimation(animateFab = true)
                } else {
                    if (resolvedCharOffset >= 0 && result.pages.isNotEmpty()) {
                        val targetPage = result.offsets.indexOfLast { it <= resolvedCharOffset }.coerceAtLeast(0)
                        if (targetPage < result.pages.size && viewPager.currentItem != targetPage) {
                            viewPager.setCurrentItem(targetPage, false)
                        }
                    }
                    updateBottomBar(viewPager.currentItem)
                }

                if (result.isFinished) {
                    BookCache.layoutKey = currentKey
                    BookCache.splitResult = result
                }
            }
        }
    }

    private fun getTopInset(): Int {
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView) ?: return 0
        val displayCutoutInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())
        val statusBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        return maxOf(statusBarInsets.top, displayCutoutInsets.top)
    }

    private var startY = 0f
    private var startBrightness = 0f
    private var startWarmth = 0
    private var isChangingBrightness = false
    private var isChangingWarmth = false

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
                saveProgress()
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
                isChangingWarmth = false
                isGestureConsumed = false
                
                if (!isTouchOnUiBars(event)) {
                    if (event.x < width / 2f) {
                        isChangingBrightness = true
                        startBrightness = BrightnessHelper.getBrightness(this)
                    } else {
                        isChangingWarmth = true
                        startWarmth = com.nightread.app.data.SettingsManager.getAmberFilterIntensity(this)
                    }
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isChangingBrightness || isChangingWarmth) {
                    val dx = event.x - startX
                    val dy = startY - event.y // positive is up
                    
                    // To prevent interfering with page swipes (horizontal), we require:
                    // 1. Vertical scroll displacement (abs(dy)) is greater than horizontal scroll displacement (abs(dx))
                    // 2. Vertical scroll displacement exceeds threshold
                    if (Math.abs(dy) > 10 * resources.displayMetrics.density && Math.abs(dy) > Math.abs(dx) * 1.5f || isGestureConsumed) {
                        if (!isGestureConsumed) {
                            isGestureConsumed = true
                            val cancelEvent = android.view.MotionEvent.obtain(event)
                            cancelEvent.action = android.view.MotionEvent.ACTION_CANCEL
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                        
                        if (isChangingBrightness) {
                            val deltaBrightness = dy / (height / 2f)
                            val newBrightness = (startBrightness + deltaBrightness).coerceIn(0.01f, 1f)
                            BrightnessHelper.setBrightness(this, newBrightness)
                            com.nightread.app.data.SettingsManager.setBrightness(this, newBrightness)
                            
                            tvBrightness.visibility = android.view.View.VISIBLE
                            tvBrightness.text = "☀ ${(newBrightness * 100).toInt()}%"
                        } else if (isChangingWarmth) {
                            val deltaWarmth = (dy / (height / 2f) * 100).toInt()
                            val newWarmth = (startWarmth + deltaWarmth).coerceIn(0, 100)
                            
                            // Auto enable amber filter if adjusting warmth above 0
                            if (newWarmth > 0 && !com.nightread.app.data.SettingsManager.isAmberFilterEnabled(this)) {
                                com.nightread.app.data.SettingsManager.setAmberFilterEnabled(this, true)
                            }
                            com.nightread.app.data.SettingsManager.setAmberFilterIntensity(this, newWarmth)
                            updateAmberFilter()
                            
                            tvBrightness.visibility = android.view.View.VISIBLE
                            tvBrightness.text = "🔸 Теплота: $newWarmth%"
                        }
                        return true
                    }
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (isGestureConsumed) {
                    tvBrightness.visibility = android.view.View.GONE
                    isChangingBrightness = false
                    isChangingWarmth = false
                    return true
                }
                isChangingBrightness = false
                isChangingWarmth = false
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

        fabBookmark.getGlobalVisibleRect(rect)
        if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) return true
        
        return false
    }

    fun performSmartSearch(query: String) {
        val index = bookContent.indexOf(query, ignoreCase = true)
        if (index != -1) {
            jumpToBookmarkOffset(index)
            Toast.makeText(this, "Найдено в книге", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Текст не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChaptersDialog() {
        ChapterListBottomSheet.newInstance(sha1, bookContent).apply {
            setOnChapterClickListener { offset ->
                jumpToBookmarkOffset(offset)
            }
        }.show(supportFragmentManager, "ChapterList")
    }

    private fun updateAiButtonsVisibility() {
        btnChaptersList.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        updateAiButtonsVisibility()
        registerLightSensor()
        if (SettingsManager.isSleepTimerEnabled(this) && SettingsManager.isShakeToExtendEnabled(this)) {
            registerShakeListener()
        }
    }

    private fun toggleBars() {
        if (isBarsVisible) {
            hideBarsWithAnimation()
        } else {
            showBarsWithAnimation(animateFab = true)
        }
    }

    private fun showBarsWithAnimation(animateFab: Boolean = true) {
        if (isBarsVisible) return
        isBarsVisible = true
        
        topBar.visibility = View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        showSystemBars()
        
        if (animateFab) {
            fabBookmark.visibility = View.VISIBLE
            fabBookmark.scaleX = 0f
            fabBookmark.scaleY = 0f
            fabBookmark.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        } else {
            fabBookmark.visibility = View.VISIBLE
            fabBookmark.scaleX = 1f
            fabBookmark.scaleY = 1f
        }
    }

    private fun hideBarsWithAnimation() {
        if (!isBarsVisible) return
        isBarsVisible = false
        
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        hideSystemBars()
        
        fabBookmark.animate()
            .scaleX(0f)
            .scaleY(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                fabBookmark.visibility = View.GONE
            }
            .start()
    }

    private fun toggleNightMode() {
        val currentReadingTheme = SettingsManager.getReadingTheme(this)
        val isCurrentDark = currentReadingTheme == "dark" || currentReadingTheme == "contrast"
        
        if (isCurrentDark) {
            val prevTheme = getSharedPreferences("reader_prefs", MODE_PRIVATE).getString("prev_reading_theme", "sepia") ?: "sepia"
            val target = if (prevTheme == "dark" || prevTheme == "contrast") "sepia" else prevTheme
            SettingsManager.setReadingTheme(this, target)
            isNightMode = false
        } else {
            getSharedPreferences("reader_prefs", MODE_PRIVATE).edit()
                .putString("prev_reading_theme", currentReadingTheme)
                .apply()
            SettingsManager.setReadingTheme(this, "dark")
            isNightMode = true
        }
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
        val total = splitResult.pages.size
        if (isSplittingFinished) {
            tvPageInfo.text = "Стр. ${position + 1} из $total"
            if (total > 1) {
                seekBar.progress = (position * 100) / (total - 1)
            } else {
                seekBar.progress = 0
            }
        } else {
            tvPageInfo.text = "Стр. ${position + 1} из $total (загрузка...)"
            seekBar.progress = 0
        }

        if (sha1.isNotEmpty() && splitResult != null && position >= 0 && position < splitResult.offsets.size) {
            val charOffset = splitResult.offsets[position]
            lifecycleScope.launch(Dispatchers.IO) {
                val db = com.nightread.app.data.BookmarkDatabase.getDatabase(this@ReadingActivity)
                val exists = db.bookmarkDao().getBookmarkAtOffset(sha1, charOffset) != null
                withContext(Dispatchers.Main) {
                    btnBookmark.setImageResource(
                        if (exists) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
                    )
                    fabBookmark.setImageResource(
                        if (exists) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
                    )
                }
            }
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
        val themeName = SettingsManager.getReadingTheme(this)
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
        unregisterLightSensor()
        unregisterShakeListener()
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
                    
                    // Trigger background incremental progress synchronization if Yandex Disk integration is active
                    val hasToken = !com.nightread.app.data.YandexDiskManager.getToken(this@ReadingActivity).isNullOrEmpty()
                    if (hasToken) {
                        com.nightread.app.service.ProgressSyncWorker.scheduleProgressSync(this@ReadingActivity, sha1, charOffset)
                    }
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
            "epub" -> {
                com.nightread.app.service.EpubParser.extractText(file)
            }
            "mobi", "azw3" -> {
                com.nightread.app.service.MobiParser.parseMobi(file, "").content
            }
            "pdf" -> {
                com.nightread.app.service.PdfParser.extractText(file)
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

    fun jumpToBookmarkOffset(targetOffset: Int) {
        lifecycleScope.launch {
            if (isSplittingFinished) {
                val targetPage = splitResult.offsets.indexOfLast { it <= targetOffset }.coerceAtLeast(0)
                if (targetPage < splitResult.pages.size) {
                    viewPager.setCurrentItem(targetPage, false)
                }
                updateBottomBar(targetPage)
                showBarsWithAnimation(animateFab = true)
            } else {
                recalculatePages(targetOffset)
            }
        }
    }

    private fun updatePageTransformer() {
        val animMode = SettingsManager.getPageAnimation(this)
        if (animMode == lastPageAnimation) return
        lastPageAnimation = animMode
        
        val transformer = when (animMode) {
            "fade" -> FadePageTransformer()
            "depth" -> DepthPageTransformer()
            "zoom" -> ZoomOutPageTransformer()
            "none" -> NoAnimationTransformer()
            else -> SlidePageTransformer() // "slide"
        }
        viewPager.setPageTransformer(transformer)
    }

    private fun toggleBookmark() {
        if (sha1.isEmpty() || splitResult == null) return
        val currentIdx = viewPager.currentItem
        if (currentIdx >= 0 && currentIdx < splitResult.offsets.size && currentIdx < splitResult.pages.size) {
            val charOffset = splitResult.offsets[currentIdx]
            val pageText = splitResult.pages[currentIdx]
            val snippet = if (pageText.length > 80) pageText.take(80).toString() + "..." else pageText.toString()

            lifecycleScope.launch(Dispatchers.IO) {
                val db = com.nightread.app.data.BookmarkDatabase.getDatabase(this@ReadingActivity)
                val existing = db.bookmarkDao().getBookmarkAtOffset(sha1, charOffset)
                if (existing != null) {
                    db.bookmarkDao().deleteBookmark(existing)
                    withContext(Dispatchers.Main) {
                        btnBookmark.setImageResource(R.drawable.ic_bookmark)
                        fabBookmark.setImageResource(R.drawable.ic_bookmark)
                        CustomToast.show(this@ReadingActivity, "Закладка удалена")
                    }
                } else {
                    val newBookmark = com.nightread.app.data.BookmarkEntity(
                        bookSha1 = sha1,
                        bookTitle = bookTitle,
                        charOffset = charOffset,
                        pageIndex = currentIdx,
                        snippet = snippet
                    )
                    db.bookmarkDao().insertBookmark(newBookmark)
                    withContext(Dispatchers.Main) {
                        btnBookmark.setImageResource(R.drawable.ic_bookmark_filled)
                        fabBookmark.setImageResource(R.drawable.ic_bookmark_filled)
                        CustomToast.show(this@ReadingActivity, "Закладка добавлена")
                    }
                }
            }
        }
    }

    private fun parseBookFile(file: File): BookParser.ParsedBook {
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "fb2", "xml" -> {
                Fb2Parser.parse(file, file.nameWithoutExtension)
            }
            "epub" -> {
                EpubParser.parse(file, file.nameWithoutExtension)
            }
            "mobi", "azw3" -> {
                MobiParser.parse(file, file.nameWithoutExtension)
            }
            "pdf" -> {
                com.nightread.app.service.PdfParser.parse(file, file.nameWithoutExtension)
            }
            "txt" -> {
                TxtParser.parse(file, file.nameWithoutExtension)
            }
            "zip" -> {
                var parsed = BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
                try {
                    FileInputStream(file).use { fis ->
                        ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && (entry.name.endsWith(".fb2") || entry.name.endsWith(".xml"))) {
                                    val tempFile = File.createTempFile("zip_fb2", ".fb2")
                                    tempFile.deleteOnExit()
                                    tempFile.outputStream().use { fos ->
                                        zis.copyTo(fos)
                                    }
                                    parsed = Fb2Parser.parse(tempFile, file.nameWithoutExtension)
                                    tempFile.delete()
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ReadingActivity", "Error parsing zipped FB2", e)
                }
                parsed
            }
            else -> BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    fun showFootnote(noteId: String) {
        val noteText = bookNotes[noteId] ?: "Текст сноски не найден."
        
        // Save current page and offset
        val savedPage = viewPager.currentItem
        val savedCharOffset = if (savedPage in splitResult.offsets.indices) {
            splitResult.offsets[savedPage]
        } else {
            0
        }
        
        Log.d("ReadingActivity", "Saved position before showing footnote: page $savedPage, offset $savedCharOffset")
        
        val bottomSheet = NoteBottomSheet.newInstance(noteId, noteText)
        bottomSheet.setOnDismissListener {
            Log.d("ReadingActivity", "Restoring position after closing footnote: page $savedPage, offset $savedCharOffset")
            if (viewPager.currentItem != savedPage) {
                viewPager.setCurrentItem(savedPage, false)
            }
        }
        bottomSheet.show(supportFragmentManager, "NoteBottomSheet")
    }

    private fun registerLightSensor() {
        if (sensorManager == null) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        }
        if (lightSensor == null) {
            lightSensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT)
        }

        if (lightSensor != null && lightSensorListener == null) {
            lightSensorListener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                    if (event == null) return
                    
                    val lux = event.values[0]
                    // Always set ambient lux in SettingsManager for dynamic variable typography
                    SettingsManager.setAmbientLux(this@ReadingActivity, lux)

                    if (!SettingsManager.isAutoLightNightEnabled(this@ReadingActivity)) return
                    
                    val currentTime = System.currentTimeMillis()
                    // Add simple cooldown (5 seconds) to avoid rapid transitions
                    if (currentTime - lastThemeTransitionTime < 5000) return

                    val currentTheme = SettingsManager.getReadingTheme(this@ReadingActivity)
                    
                    if (lux < 8.0f) {
                        // Environment is dark, switch to deep "dark" theme
                        if (currentTheme != "dark" && currentTheme != "contrast") {
                            // Save pre-sensor theme to restore it later if environment gets bright
                            getSharedPreferences("reader_prefs", MODE_PRIVATE).edit()
                                .putString("pre_sensor_theme", currentTheme)
                                .apply()
                            
                            SettingsManager.setReadingTheme(this@ReadingActivity, "dark")
                            isNightMode = true
                            lastThemeTransitionTime = currentTime
                            Log.d("ReadingActivity", "Ambient light low ($lux lux). Switched to dark theme automatically.")
                        }
                    } else if (lux > 25.0f) {
                        // Environment is bright, restore previous non-dark theme (or "sepia" as default)
                        if (currentTheme == "dark" || currentTheme == "contrast") {
                            val savedTheme = getSharedPreferences("reader_prefs", MODE_PRIVATE)
                                .getString("pre_sensor_theme", "sepia") ?: "sepia"
                            
                            val targetTheme = if (savedTheme == "dark" || savedTheme == "contrast") "sepia" else savedTheme
                            SettingsManager.setReadingTheme(this@ReadingActivity, targetTheme)
                            isNightMode = false
                            lastThemeTransitionTime = currentTime
                            Log.d("ReadingActivity", "Ambient light high ($lux lux). Restored previous theme: $targetTheme.")
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
            }
            
            sensorManager?.registerListener(
                lightSensorListener,
                lightSensor,
                android.hardware.SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.d("ReadingActivity", "Registered light sensor listener.")
        }
    }

    private fun unregisterLightSensor() {
        lightSensorListener?.let {
            sensorManager?.unregisterListener(it)
            lightSensorListener = null
            Log.d("ReadingActivity", "Unregistered light sensor listener.")
        }
    }

    private fun updateAmberFilter() {
        val filter = viewAmberFilter ?: return
        val isEnabled = SettingsManager.isAmberFilterEnabled(this)
        if (isEnabled) {
            val intensity = SettingsManager.getAmberFilterIntensity(this)
            // Map 0..100% to alpha 0..180 to avoid absolute solid colors
            val alpha = (intensity * 1.8).toInt().coerceIn(0, 180)
            filter.setBackgroundColor(android.graphics.Color.argb(alpha, 255, 140, 0))
            filter.visibility = android.view.View.VISIBLE
        } else {
            filter.visibility = android.view.View.GONE
        }
    }

    private fun updateSleepTimer() {
        // Cancel existing job if any
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        val isEnabled = SettingsManager.isSleepTimerEnabled(this)
        val indicator = tvSleepTimerIndicator

        if (isEnabled) {
            val durationMinutes = SettingsManager.getSleepTimerDuration(this)
            sleepTimerRemainingSeconds = durationMinutes * 60
            indicator?.visibility = android.view.View.VISIBLE
            updateSleepTimerIndicatorText()

            if (SettingsManager.isShakeToExtendEnabled(this)) {
                registerShakeListener()
            } else {
                unregisterShakeListener()
            }

            // Start ticking
            sleepTimerJob = lifecycleScope.launch {
                while (sleepTimerRemainingSeconds > 0) {
                    kotlinx.coroutines.delay(1000)
                    sleepTimerRemainingSeconds--
                    updateSleepTimerIndicatorText()

                    if (sleepTimerRemainingSeconds == 0) {
                        // Sleep timer finished! Close reader activity
                        vibrateDevice()
                        android.widget.Toast.makeText(
                            this@ReadingActivity,
                            "Таймер сна сработал. Спокойной ночи!",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        } else {
            indicator?.visibility = android.view.View.GONE
            unregisterShakeListener()
        }
    }

    private fun updateSleepTimerIndicatorText() {
        val indicator = tvSleepTimerIndicator ?: return
        val minutes = sleepTimerRemainingSeconds / 60
        val seconds = sleepTimerRemainingSeconds % 60
        indicator.text = String.format("⏳ %d:%02d", minutes, seconds)
    }

    private fun extendSleepTimer() {
        if (!SettingsManager.isSleepTimerEnabled(this)) return
        val extendSeconds = 10 * 60
        sleepTimerRemainingSeconds += extendSeconds
        // Cap at 120 minutes max to be reasonable
        if (sleepTimerRemainingSeconds > 120 * 60) {
            sleepTimerRemainingSeconds = 120 * 60
        }
        updateSleepTimerIndicatorText()
        vibrateDevice()
        android.widget.Toast.makeText(
            this,
            "Таймер сна продлен на 10 минут ⏳",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun registerShakeListener() {
        if (shakeListener != null) return
        val sm = sensorManager ?: return
        val acc = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
        accelerometer = acc

        shakeListener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                val ev = event ?: return
                if (ev.sensor.type == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                    val x = ev.values[0]
                    val y = ev.values[1]
                    val z = ev.values[2]

                    // Calculate acceleration vector magnitude subtracting gravity (approx 9.8)
                    val gForce = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - 9.8f
                    if (gForce > 5.0f) { // Shake force threshold
                        val now = System.currentTimeMillis()
                        if (now - lastShakeTime > 2000) { // Limit shake extension once per 2 seconds
                            lastShakeTime = now
                            extendSleepTimer()
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        sm.registerListener(shakeListener, acc, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        Log.d("ReadingActivity", "Registered shake-to-extend listener.")
    }

    private fun unregisterShakeListener() {
        val listener = shakeListener ?: return
        sensorManager?.unregisterListener(listener)
        shakeListener = null
        Log.d("ReadingActivity", "Unregistered shake-to-extend listener.")
    }

    private fun vibrateDevice() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(150)
                }
            }
        } catch (e: Exception) {
            // Ignore if permission or hardware error
        }
    }

    private fun updateExtraDim() {
        val filter = viewExtraDim ?: return
        val isEnabled = SettingsManager.isExtraDimEnabled(this)
        if (isEnabled) {
            val intensity = SettingsManager.getExtraDimIntensity(this)
            // Map 0..90% to alpha 0..230 to avoid absolute black screen
            val alpha = (intensity * 2.5).toInt().coerceIn(0, 230)
            filter.setBackgroundColor(android.graphics.Color.argb(alpha, 0, 0, 0))
            filter.visibility = android.view.View.VISIBLE
        } else {
            filter.visibility = android.view.View.GONE
        }
    }

    private fun startEyeComfortFadeIn() {
        val fadeView = findViewById<android.view.View>(R.id.viewEyeComfortFade) ?: return
        fadeView.visibility = android.view.View.VISIBLE
        fadeView.alpha = 1.0f
        
        fadeView.animate()
            .alpha(0.0f)
            .setDuration(1800) // 1.8 seconds smooth fade-in
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                fadeView.visibility = android.view.View.GONE
            }
            .start()
    }
}
