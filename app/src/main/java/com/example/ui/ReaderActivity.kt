package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.data.AppDatabase
import com.example.data.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class ReaderActivity : FragmentActivity() {
    private val TAG = "READING_DEBUG"

    // Keep compatibility with any possible external references
    lateinit var viewModel: ReaderViewModel
    private val vm: ReaderViewModel by viewModels()

    private lateinit var viewPager: ViewPager2
    private lateinit var progressBar: ProgressBar
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var titleText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var readingProgressBar: ProgressBar

    private lateinit var backButtonView: TextView
    private lateinit var infoButtonView: TextView
    private lateinit var settingsBtn: ImageView

    private var isSystemUiVisible = false
    private var bookSha1: String = ""
    private var bookPath: String? = null
    private var pages: List<String> = emptyList()
    private var rawBookContent: String? = null
    private var lastFontSize = 0f
    private var lastFontFamily = ""
    private var lastFontWeight = ""
    private var lastLineSpacing = 0f
    private var preprocessedText: String? = null

    // Gestures and touch detection
    private lateinit var gestureDetector: android.view.GestureDetector
    private var isTrackingBrightness = false
    private var startY = 0f
    private var startBrightness = 0.5f
    private val BRIGHTNESS_EDGE_WIDTH_PERCENT = 0.15f

    private val hideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideRunnable = Runnable {
        hideSystemUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = vm

        Log.d("READING_FIX", "Шаг 1 [Получение пути]: Начало работы onCreate. Извлечение данных из Intent...")
        val intentPath = intent.getStringExtra("book_path") ?: intent.getStringExtra("BOOK_PATH")
        bookSha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        Log.d("READING_FIX", "READING_FIX: Получено из Intent: book_path = $intentPath, BOOK_SHA1 = $bookSha1")

        // Programmatic layout assembly
        setupLayout()
        
        // Initialize gesture detection
        setupGestures()

        // Observe settings changes flow
        lifecycleScope.launchWhenStarted {
            com.example.data.SettingsManager.settingsChanged.collect {
                Log.d(TAG, "Settings changed flow triggered!")
                applyThemeColors()
                
                val fontSize = com.example.data.SettingsManager.getFontSize(this@ReaderActivity)
                val fontFamily = com.example.data.SettingsManager.getFontFamily(this@ReaderActivity)
                val fontWeight = com.example.data.SettingsManager.getFontWeight(this@ReaderActivity)
                val lineSpacingMultiplier = com.example.data.SettingsManager.getLineSpacing(this@ReaderActivity)

                val layoutChanged = fontSize != lastFontSize ||
                        fontFamily != lastFontFamily ||
                        fontWeight != lastFontWeight ||
                        lineSpacingMultiplier != lastLineSpacing

                lastFontSize = fontSize
                lastFontFamily = fontFamily
                lastFontWeight = fontWeight
                lastLineSpacing = lineSpacingMultiplier

                val content = rawBookContent
                if (content != null) {
                    if (layoutChanged) {
                        val currentPos = if (pages.isNotEmpty()) viewPager.currentItem else 0
                        processAndDisplayContent(content)
                        if (pages.isNotEmpty()) {
                            val targetPage = currentPos.coerceIn(0, pages.size - 1)
                            viewPager.setCurrentItem(targetPage, false)
                        }
                    } else {
                        // Only theme colors changed, notify existing fragments
                        val adapter = viewPager.adapter as? BookPagerAdapter
                        adapter?.notifyDataSetChanged()
                    }
                }
            }
        }

        if (bookSha1.isNotEmpty()) {
            Log.d("READING_FIX", "READING_FIX: Запуск асинхронного получения пути из БД по BOOK_SHA1: $bookSha1")
            loadBookFromDbAndRead()
        } else if (intentPath != null) {
            bookPath = intentPath
            Log.d("READING_FIX", "READING_FIX: Найден прямой путь к книге в Intent: $bookPath")
            loadBook(bookPath!!)
        } else {
            Log.e("READING_FIX", "READING_FIX: Ошибка: в Intent не передан ни BOOK_SHA1, ни book_path")
            Toast.makeText(this, "Книга не найдена (не передан SHA-1 или путь)", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupLayout() {
        // Enable edge-to-edge full-screen display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(com.example.R.layout.activity_reading)

        val root = findViewById<FrameLayout>(com.example.R.id.rootContainer)
        viewPager = findViewById(com.example.R.id.viewPager)
        progressBar = findViewById(com.example.R.id.progressBar)
        topBar = findViewById(com.example.R.id.topBar)
        bottomBar = findViewById(com.example.R.id.bottomBar)
        progressText = findViewById(com.example.R.id.progressText)
        titleText = findViewById(com.example.R.id.titleText)
        seekBar = findViewById(com.example.R.id.seekBar)
        readingProgressBar = findViewById(com.example.R.id.readingProgressBar)

        backButtonView = findViewById(com.example.R.id.backButtonView)
        infoButtonView = findViewById(com.example.R.id.infoButtonView)
        settingsBtn = findViewById(com.example.R.id.settingsBtn)

        viewPager.setPageTransformer(BookFlipPageTransformer())

        backButtonView.setOnClickListener { finish() }
        infoButtonView.setOnClickListener {
            Toast.makeText(this@ReaderActivity, "Разработчик: Google AI Studio\nФорматы: FB2, TXT, ZIP", Toast.LENGTH_LONG).show()
        }
        settingsBtn.setOnClickListener {
            val bottomSheet = SettingsBottomSheet()
            bottomSheet.show(supportFragmentManager, "SettingsBottomSheet")
        }

        // Setup theme immediately
        applyThemeColors()

        // Hide bars initially
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        isSystemUiVisible = false

        val density = resources.displayMetrics.density

        // Apply WindowInsets safely for Notch, StatusBar and NavigationBar
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val displayCutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            val topInset = maxOf(statusBarInsets.top, displayCutoutInsets.top)

            // Adjust top bar top padding and height to accommodate status bar and notch
            topBar.setPadding((16 * density).toInt(), topInset, (16 * density).toInt(), 0)
            val topParams = topBar.layoutParams as FrameLayout.LayoutParams
            topParams.height = (56 * density).toInt() + topInset
            topBar.layoutParams = topParams

            // Adjust bottom bar bottom padding to accommodate system navigation controls
            bottomBar.setPadding(
                (16 * density).toInt(),
                (8 * density).toInt(),
                (16 * density).toInt(),
                (8 * density).toInt() + navBarInsets.bottom
            )

            // Adjust reading progress bar bottom margin to stay above navigation bar
            val barParams = readingProgressBar.layoutParams as FrameLayout.LayoutParams
            barParams.bottomMargin = navBarInsets.bottom
            readingProgressBar.layoutParams = barParams

            insets
        }

        hideSystemUi()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (::viewPager.isInitialized) {
            if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                val currentItem = viewPager.currentItem
                if (currentItem < (viewPager.adapter?.itemCount ?: 0) - 1) {
                    viewPager.setCurrentItem(currentItem + 1, true)
                }
                return true
            } else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                val currentItem = viewPager.currentItem
                if (currentItem > 0) {
                    viewPager.setCurrentItem(currentItem - 1, true)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private val hudHideRunnable = Runnable {
        findViewById<View>(com.example.R.id.brightnessHud)?.visibility = View.GONE
    }

    private fun showBrightnessFeedback(percentage: Int) {
        val hud = findViewById<TextView>(com.example.R.id.brightnessHud) ?: return
        hud.text = "☀ Яркость: $percentage%"
        hud.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hudHideRunnable)
        hideHandler.postDelayed(hudHideRunnable, 1000)
    }

    private fun toggleNightMode() {
        val currentTheme = com.example.data.SettingsManager.getTheme(this)
        if (currentTheme == "dark") {
            val prevTheme = com.example.data.SettingsManager.getPreviousTheme(this)
            com.example.data.SettingsManager.setTheme(this, prevTheme)
            Toast.makeText(this, "Восстановлена тема: ${themeNameRussian(prevTheme)}", Toast.LENGTH_SHORT).show()
        } else {
            com.example.data.SettingsManager.setTheme(this, "dark")
            Toast.makeText(this, "Ночной режим", Toast.LENGTH_SHORT).show()
        }
    }

    private fun themeNameRussian(theme: String): String {
        return when (theme) {
            "light" -> "День"
            "dark" -> "Ночь"
            "sepia" -> "Сепия"
            "sepia_contrast" -> "Сепия контраст"
            "contrast" -> "Контраст"
            "beige" -> "Бежевый"
            else -> theme
        }
    }

    private fun setupGestures() {
        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                val density = resources.displayMetrics.density
                val x = e.x
                val y = e.y

                // Tapping top-left 80x80 dp toggles night/day theme
                if (x <= 80 * density && y <= 80 * density) {
                    toggleNightMode()
                    return true
                }

                // Tapping center/any other area toggles bars
                toggleSystemUi()
                return true
            }
        })
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        val density = resources.displayMetrics.density
        val brightnessEdgeWidth = 60f * density
        
        when (ev.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                // Brightness only on the right edge, starting from the vertical middle and below (y >= screenHeight / 2)
                val inBrightnessZone = ev.x > (screenWidth - brightnessEdgeWidth) && ev.y >= (screenHeight / 2.0f)
                if (inBrightnessZone) {
                    isTrackingBrightness = true
                    startY = ev.y
                    val lp = window.attributes
                    startBrightness = if (lp.screenBrightness < 0f) {
                        try {
                            android.provider.Settings.System.getInt(
                                contentResolver,
                                android.provider.Settings.System.SCREEN_BRIGHTNESS
                            ) / 255f
                        } catch (e: Exception) {
                            0.5f
                        }
                    } else {
                        lp.screenBrightness
                    }
                } else {
                    isTrackingBrightness = false
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isTrackingBrightness) {
                    val deltaY = startY - ev.y
                    val deltaBrightness = deltaY / (screenHeight * 0.5f)
                    val newBrightness = (startBrightness + deltaBrightness).coerceIn(0.01f, 1.0f)
                    
                    val lp = window.attributes
                    lp.screenBrightness = newBrightness
                    window.attributes = lp
                    
                    val pct = (newBrightness * 100).toInt()
                    showBrightnessFeedback(pct)
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (isTrackingBrightness) {
                    isTrackingBrightness = false
                    val deltaY = Math.abs(startY - ev.y)
                    // If it was a short tap instead of a real scroll, let the gesture detector handle it
                    if (deltaY < 10 * density) {
                        gestureDetector.onTouchEvent(ev)
                    }
                    return true
                }
            }
        }
        
        if (!isTrackingBrightness) {
            if (gestureDetector.onTouchEvent(ev)) {
                return true
            }
        }
        
        return super.dispatchTouchEvent(ev)
    }

    private fun createPanelButton(context: Context, text: String, onClick: View.OnClickListener): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
            gravity = Gravity.CENTER
            setOnClickListener(onClick)
        }
    }

    private fun applyThemeColors() {
        val themeName = com.example.data.SettingsManager.getTheme(this)
        
        var bgColorHex = "#F5F0E8"
        var textColorHex = "#3D2C1A"
        var panelBgHex = "#EDE5D8"
        var borderHex = "#E0D8C8"
        
        when (themeName) {
            "light" -> {
                bgColorHex = "#FFFFFF"
                textColorHex = "#121212"
                panelBgHex = "#F5F5F5"
                borderHex = "#E0E0E0"
            }
            "dark" -> {
                bgColorHex = "#1E1A16"
                textColorHex = "#E8E0D8"
                panelBgHex = "#2A261F"
                borderHex = "#2A261F"
            }
            "sepia" -> {
                bgColorHex = "#F5F0E8"
                textColorHex = "#3D2C1A"
                panelBgHex = "#EDE5D8"
                borderHex = "#E0D8C8"
            }
            "sepia_contrast" -> {
                bgColorHex = "#F5E6C8"
                textColorHex = "#1A1A1A"
                panelBgHex = "#EAD9B8"
                borderHex = "#D6C39F"
            }
            "contrast" -> {
                bgColorHex = "#000000"
                textColorHex = "#FFFF00"
                panelBgHex = "#111111"
                borderHex = "#333333"
            }
            "beige" -> {
                bgColorHex = "#F4ECD8"
                textColorHex = "#3B2F1F"
                panelBgHex = "#EADFC5"
                borderHex = "#DED0B2"
            }
        }
        
        val bgColor = Color.parseColor(bgColorHex)
        val textColor = Color.parseColor(textColorHex)
        val panelBg = Color.parseColor(panelBgHex)
        val border = Color.parseColor(borderHex)
        
        findViewById<View>(android.R.id.content)?.setBackgroundColor(bgColor)
        viewPager.setBackgroundColor(bgColor)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
        window.decorView.setBackgroundColor(bgColor)
        
        // Update status bar and navigation bar colors
        updateSystemBarsColors(themeName == "dark")
        
        // Style Top Bar
        topBar.background = GradientDrawable().apply {
            setColor(panelBg)
            setStroke(1, border)
        }
        titleText.setTextColor(textColor)
        backButtonView.setTextColor(textColor)
        infoButtonView.setTextColor(textColor)
        // settingsBtn (ImageView) has a gorgeous volumetric gold/bronze color from its drawable, so we keep its natural colors!
        
        // Style Bottom Bar
        bottomBar.background = GradientDrawable().apply {
            setColor(panelBg)
            setStroke(1, border)
        }
        progressText.setTextColor(textColor)
        
        // Update button colors
        val btnTextColor = Color.parseColor("#C4956A")
        
        // Update SeekBar and readingProgressBar colors if supported
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val colorStateList = android.content.res.ColorStateList.valueOf(btnTextColor)
            seekBar.progressTintList = colorStateList
            seekBar.thumbTintList = colorStateList
            readingProgressBar.progressTintList = colorStateList
            val trackColor = Color.parseColor("#33C4956A")
            readingProgressBar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(trackColor)
        }
    }

    private fun updateSystemBarsColors(isNightMode: Boolean) {
        val themeName = com.example.data.SettingsManager.getTheme(this)
        val bgColorHex = when (themeName) {
            "light" -> "#FFFFFF"
            "dark" -> "#1E1A16"
            "sepia" -> "#F5F0E8"
            "sepia_contrast" -> "#F5E6C8"
            "contrast" -> "#000000"
            "beige" -> "#F4ECD8"
            else -> "#F5F0E8"
        }
        val bgColor = Color.parseColor(bgColorHex)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = bgColor
            window.navigationBarColor = bgColor
        }

        val isDark = themeName == "dark" || themeName == "contrast"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                val statusFlag = if (isDark) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                val navFlag = if (isDark) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                controller.setSystemBarsAppearance(statusFlag, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                controller.setSystemBarsAppearance(navFlag, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            flags = flags or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                flags = if (isDark) {
                    flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else {
                    flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                flags = if (isDark) {
                    flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun refreshAdapter() {
        if (pages.isNotEmpty()) {
            val currentPos = viewPager.currentItem
            val adapter = BookPagerAdapter(this, pages)
            viewPager.adapter = adapter
            viewPager.setCurrentItem(currentPos, false)
        }
    }

    private fun showErrorAndExit(message: String) {
        Log.e("READING_DEBUG", "READING_DEBUG: Ошибка в приложении, выход с сообщением: $message")
        progressBar.visibility = View.GONE
        Toast.makeText(this@ReaderActivity, message, Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000)
            finish()
        }
    }

    private fun loadBook() {
        val path = bookPath
        Log.d("READING_DEBUG", "READING_DEBUG: Вызов loadBook() без аргументов. Текущий сохранённый путь = $path")
        if (path != null) {
            loadBook(path)
        } else {
            Log.e("READING_DEBUG", "READING_DEBUG: Ошибка: Вызов loadBook() без установленного пути")
            showErrorAndExit("Книга не загружена: отсутствует путь к файлу")
        }
    }

    private suspend fun findBookFileBySha1(sha1: String, originalPath: String?): File? {
        Log.d("READING_DEBUG", "Поиск файла по SHA-1: $sha1")
        // 1. Check in application's private imported folder: filesDir/imported_books/
        val importedFolder = File(filesDir, "imported_books")
        if (importedFolder.exists()) {
            val matchingFiles = importedFolder.listFiles()?.filter { it.name.startsWith(sha1) }
            if (!matchingFiles.isNullOrEmpty()) {
                Log.d("READING_DEBUG", "Файл найден в private imported_books: ${matchingFiles[0].absolutePath}")
                return matchingFiles[0]
            }
        }

        // 2. Let's look in standard directories: Download, Documents, Books, etc.
        val searchDirs = listOf(
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/Books"),
            getExternalFilesDir(null)
        ).filterNotNull()

        // To make search super fast, let's first search for files that have the same name or extension as originalPath,
        // or any files with .fb2, .zip, .txt, .epub extension.
        val targetName = originalPath?.let { File(it).name }
        val candidates = mutableListOf<File>()

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val list = dir.listFiles() ?: continue
                for (f in list) {
                    if (f.isFile) {
                        val ext = f.extension.lowercase()
                        if (ext == "fb2" || ext == "zip" || ext == "txt" || ext == "epub") {
                            if (targetName != null && f.name.equals(targetName, ignoreCase = true)) {
                                candidates.add(0, f) // high priority candidate
                            } else {
                                candidates.add(f)
                            }
                        }
                    }
                }
            }
        }

        // Compute SHA-1 for the candidate files
        for (cand in candidates) {
            try {
                val candSha1 = withContext(Dispatchers.IO) {
                    val digest = java.security.MessageDigest.getInstance("SHA-1")
                    val buffer = ByteArray(8192)
                    FileInputStream(cand).use { fis ->
                        var bytesRead = fis.read(buffer)
                        while (bytesRead != -1) {
                            digest.update(buffer, 0, bytesRead)
                            bytesRead = fis.read(buffer)
                        }
                    }
                    digest.digest().joinToString("") { String.format("%02x", it) }
                }
                if (candSha1 == sha1) {
                    Log.d("READING_DEBUG", "Файл найден по SHA-1 в папке: ${cand.absolutePath}")
                    return cand
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }

    private fun loadBookFromDbAndRead() {
        Log.d("READING_DEBUG", "READING_DEBUG: Начало loadBookFromDbAndRead() для SHA-1 книги: $bookSha1")
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                Log.d("READING_DEBUG", "Шаг 1 [БД]: Запрос информации о книге по SHA-1: $bookSha1")
                val db = AppDatabase.getDatabase(this@ReaderActivity)
                val book = withContext(Dispatchers.IO) {
                    db.bookDao().getBookBySha1(bookSha1)
                }
                
                if (book == null) {
                    Log.e("READING_DEBUG", "READING_DEBUG: Книга с SHA-1 $bookSha1 не найдена в базе данных")
                    showErrorAndExit("Ошибка: Книга не найдена в базе данных")
                    return@launch
                }
                
                Log.d("READING_DEBUG", "READING_DEBUG: Успешно получена книга из БД: ${book.title}")
                titleText.text = book.title
                
                val path = book.filePath
                if (path.isNullOrEmpty()) {
                    Log.d("READING_DEBUG", "READING_DEBUG: Путь отсутствует в БД, используем встроенный текст (длина: ${book.content.length})")
                    loadBookWithContent(book.content, "Встроенный текст")
                } else {
                    Log.d("READING_DEBUG", "READING_DEBUG: Найден путь в БД: $path. Проверка доступности файла...")
                    val file = File(path)
                    if (file.exists()) {
                        bookPath = path
                        loadBook(path)
                    } else {
                        Log.d("READING_DEBUG", "READING_DEBUG: Файл не найден по пути $path. Запуск резервного поиска по SHA-1...")
                        val foundFile = findBookFileBySha1(bookSha1, path)
                        if (foundFile != null) {
                            Log.d("READING_DEBUG", "READING_DEBUG: Резервный файл найден: ${foundFile.absolutePath}. Обновление БД...")
                            bookPath = foundFile.absolutePath
                            withContext(Dispatchers.IO) {
                                db.bookDao().updateBook(book.copy(filePath = foundFile.absolutePath))
                            }
                            loadBook(foundFile.absolutePath)
                        } else {
                            Log.e("READING_DEBUG", "READING_DEBUG: Файл не найден по SHA-1 в резервных папках")
                            showErrorAndExit("Ошибка: Файл книги не найден на устройстве")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("READING_DEBUG", "READING_DEBUG: Исключение на Шаге 1 [БД]: ${t.message}", t)
                showErrorAndExit("Ошибка загрузки книги из БД: ${t.localizedMessage}")
            }
        }
    }

    private fun loadBook(path: String) {
        Log.d("READING_DEBUG", "READING_DEBUG: Начало loadBook(path) по пути: $path")
        progressBar.visibility = View.VISIBLE
        preprocessedText = null
        lifecycleScope.launch {
            try {
                Log.d("READING_DEBUG", "Шаг 2 [Чтение]: Проверка существования файла...")
                val file = File(path)
                if (!file.exists()) {
                    Log.e("READING_DEBUG", "READING_DEBUG: Файл не найден по пути: $path")
                    showErrorAndExit("Ошибка: Файл книги не найден")
                    return@launch
                }
                
                if (!file.canRead()) {
                    Log.e("READING_DEBUG", "READING_DEBUG: Нет прав на чтение файла: $path")
                    showErrorAndExit("Ошибка: Нет прав на чтение файла книги")
                    return@launch
                }
                
                val size = file.length()
                Log.d("READING_DEBUG", "READING_DEBUG: Файл доступен. Размер: $size байт, Путь: $path")
                if (size == 0L) {
                    Log.e("READING_DEBUG", "READING_DEBUG: Файл пуст (0 байт)")
                    showErrorAndExit("Ошибка: Файл книги пуст")
                    return@launch
                }

                Log.d("READING_DEBUG", "Шаг 2.1 [Чтение]: Определение кодировки и извлечение содержимого...")
                val rawContent = withContext(Dispatchers.IO) {
                    try {
                        if (path.lowercase().endsWith(".zip")) {
                            readZipFile(file)
                        } else {
                            readBookFile(file)
                        }
                    } catch (e: Exception) {
                        Log.e("READING_DEBUG", "READING_DEBUG: Исключение при чтении файла", e)
                        throw Exception("Файл поврежден или не поддерживается: ${e.localizedMessage}")
                    }
                }

                Log.d("READING_DEBUG", "Шаг 2.2 [Чтение]: Файл успешно прочитан. Длина содержимого: ${rawContent.length} символов")
                if (rawContent.trim().isEmpty()) {
                    Log.e("READING_DEBUG", "READING_DEBUG: Содержимое файла пусто")
                    showErrorAndExit("Ошибка: Книга пуста")
                    return@launch
                }

                if (titleText.text.isNullOrEmpty()) {
                    titleText.text = file.nameWithoutExtension
                }

                Log.d("READING_DEBUG", "READING_DEBUG: Переход к разметке и отображению текста...")
                processAndDisplayContent(rawContent)

            } catch (t: Throwable) {
                Log.e("READING_DEBUG", "READING_DEBUG: Исключение на Шаге 2 [Чтение]: ${t.message}", t)
                showErrorAndExit("Ошибка чтения файла: ${t.localizedMessage}")
            }
        }
    }

    private fun loadBookWithContent(content: String, sourceName: String) {
        Log.d("READING_DEBUG", "READING_DEBUG: Начало loadBookWithContent() для источника $sourceName")
        progressBar.visibility = View.VISIBLE
        preprocessedText = null
        lifecycleScope.launch {
            try {
                Log.d("READING_DEBUG", "Шаг 2 [Встроенный текст]: Обработка встроенного текста (длина: ${content.length})")
                if (content.trim().isEmpty()) {
                    Log.e("READING_DEBUG", "READING_DEBUG: Встроенный текст пуст")
                    showErrorAndExit("Ошибка: Книга пуста")
                    return@launch
                }
                processAndDisplayContent(content)
            } catch (t: Throwable) {
                Log.e("READING_DEBUG", "READING_DEBUG: Исключение на Шаге 2 [Встроенный текст]: ${t.message}", t)
                showErrorAndExit("Ошибка обработки встроенного текста: ${t.localizedMessage}")
            }
        }
    }

    private suspend fun processAndDisplayContent(rawContent: String) {
        rawBookContent = rawContent
        val vpWidth = viewPager.width
        val vpHeight = viewPager.height

        Log.d("READING_DEBUG", "Шаг 3 [Разбивка]: Начало обработки и разметки страниц. Размеры ViewPager: ${vpWidth}x${vpHeight}")
        
        try {
            pages = withContext(Dispatchers.Default) {
                try {
                    val formattedText = preprocessedText ?: run {
                        Log.d("READING_DEBUG", "Шаг 3.1 [Разбивка]: Препроцессинг и перенос слов...")
                        val res = preprocessTextAndHyphenate(rawContent)
                        preprocessedText = res
                        res
                    }
                    Log.d("READING_DEBUG", "Шаг 3.2 [Разбивка]: Разбивка текста на страницы через PageSplitter...")
                    splitContentToPages(formattedText, vpWidth, vpHeight)
                } catch (e: Exception) {
                    Log.e("READING_DEBUG", "READING_DEBUG: Исключение во время разбивки на страницы в фоновом потоке", e)
                    throw Exception("Не удалось разбить текст на страницы: ${e.localizedMessage}")
                }
            }

            Log.d("READING_DEBUG", "Шаг 3.3 [Разбивка]: Разбивка успешно завершена. Всего страниц: ${pages.size}")
            if (pages.isEmpty()) {
                Log.e("READING_DEBUG", "READING_DEBUG: Результат разбивки пуст")
                showErrorAndExit("Ошибка: Не удалось разбить текст на страницы")
                return
            }

            Log.d("READING_DEBUG", "Шаг 4 [Отображение]: Настройка BookPagerAdapter и UI")
            progressBar.visibility = View.GONE

            val activeAdapter = viewPager.adapter as? BookPagerAdapter
            if (activeAdapter != null) {
                activeAdapter.updatePages(pages)
            } else {
                val newAdapter = BookPagerAdapter(this@ReaderActivity, pages)
                viewPager.adapter = newAdapter
            }

            seekBar.max = (pages.size - 1).coerceAtLeast(0)

            // Восстановление сохраненной страницы
            val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            val savedPage = if (bookSha1.isNotEmpty()) sharedPrefs.getInt("book_page_$bookSha1", 0) else 0
            val targetPage = savedPage.coerceIn(0, pages.size - 1)
            
            viewPager.setCurrentItem(targetPage, false)
            seekBar.progress = targetPage
            progressText.text = "Стр. ${targetPage + 1} из ${pages.size}"

            readingProgressBar.visibility = View.VISIBLE
            val initialProgress = if (pages.size > 0) {
                ((targetPage + 1) * 100 / pages.size).coerceIn(0, 100)
            } else {
                0
            }
            readingProgressBar.progress = initialProgress

            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    progressText.text = "Стр. ${position + 1} из ${pages.size}"
                    seekBar.progress = position
                    Log.d("READING_DEBUG", "READING_DEBUG: Отображение: Текущая страница изменена на: ${position + 1}")
                    
                    val progressPercent = if (pages.size > 0) {
                        ((position + 1) * 100 / pages.size).coerceIn(0, 100)
                    } else {
                        0
                    }
                    readingProgressBar.progress = progressPercent

                    if (bookSha1.isNotEmpty()) {
                        sharedPrefs.edit().putInt("book_page_$bookSha1", position).apply()
                    }
                }
            })

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val targetPage = progress.coerceIn(0, pages.size - 1)
                        viewPager.setCurrentItem(targetPage, false)
                        progressText.text = "Стр. ${targetPage + 1} из ${pages.size}"
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    hideHandler.removeCallbacks(hideRunnable)
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    resetHideTimer()
                }
            })
            
            Log.d("READING_DEBUG", "READING_DEBUG: Успешно завершена вся логика загрузки и отображения книги!")
            
        } catch (t: Throwable) {
            Log.e("READING_DEBUG", "READING_DEBUG: Исключение на Шаге 3 или 4 [Разметка/Отображение]: ${t.message}", t)
            showErrorAndExit("Ошибка разметки или отображения книги: ${t.localizedMessage}")
        }
    }

    private fun readBookFile(file: File): String {
        val bytes = file.readBytes()
        return decodeBytesWithLogs(bytes, file.name)
    }

    private fun readZipFile(file: File): String {
        FileInputStream(file).use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (!entry.isDirectory && (entryName.endsWith(".fb2") || entryName.endsWith(".txt"))) {
                        Log.d(TAG, "Найдена книга в архиве: $entryName")
                        val bytes = zis.readBytes()
                        return decodeBytesWithLogs(bytes, entryName)
                    }
                    entry = zis.nextEntry
                }
            }
        }
        throw IllegalArgumentException("Книга не найдена внутри архива ZIP")
    }

    private fun decodeBytesWithLogs(bytes: ByteArray, fileName: String): String {
        var encodingHeader: String? = null
        try {
            val previewSize = if (bytes.size > 1024) 1024 else bytes.size
            val preview = String(bytes, 0, previewSize, StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(preview)
            if (match != null) {
                encodingHeader = match.groupValues[1].trim()
                Log.d(TAG, "Обнаружена кодировка в заголовке XML: $encodingHeader")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка декодирования заголовка", e)
        }

        if (encodingHeader != null) {
            try {
                val charset = Charset.forName(encodingHeader)
                Log.d(TAG, "Определение кодировки: успешно определена как $encodingHeader")
                val content = String(bytes, charset)
                return if (fileName.endsWith(".fb2")) parseFb2(content) else content
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось декодировать используя заголовок кодировки $encodingHeader. Пробуем фоллбеки.", e)
            }
        }

        // Try UTF-8
        Log.d(TAG, "Определение кодировки: пробуем UTF-8...")
        try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            Log.d(TAG, "Определение кодировки: успешно определена как UTF-8")
            val content = charBuffer.toString()
            return if (fileName.endsWith(".fb2")) parseFb2(content) else content
        } catch (e: Exception) {
            Log.d(TAG, "Определение кодировки: UTF-8 не подошла")
        }

        // Try Windows-1251
        Log.d(TAG, "Определение кодировки: пробуем windows-1251...")
        try {
            val charset = Charset.forName("windows-1251")
            Log.d(TAG, "Определение кодировки: успешно определена как windows-1251")
            val content = String(bytes, charset)
            return if (fileName.endsWith(".fb2")) parseFb2(content) else content
        } catch (e: Exception) {
            Log.d(TAG, "Определение кодировки: windows-1251 не подошла")
        }

        // Try KOI8-R
        Log.d(TAG, "Определение кодировки: пробуем KOI8-R...")
        try {
            val charset = Charset.forName("KOI8-R")
            Log.d(TAG, "Определение кодировки: успешно определена как KOI8-R")
            val content = String(bytes, charset)
            return if (fileName.endsWith(".fb2")) parseFb2(content) else content
        } catch (e: Exception) {
            Log.d(TAG, "Определение кодировки: KOI8-R не подошла")
        }

        // Default fallback to ISO-8859-1
        Log.w(TAG, "Определение кодировки: кодировка не определена, используем ISO-8859-1 по умолчанию")
        val content = String(bytes, StandardCharsets.ISO_8859_1)
        return if (fileName.endsWith(".fb2")) parseFb2(content) else content
    }

    private fun parseFb2(rawText: String): String {
        Log.d(TAG, "Запуск парсера FB2...")
        val bodyStart = rawText.indexOf("<body>", ignoreCase = true)
        val bodyEnd = rawText.lastIndexOf("</body>", ignoreCase = true)
        val bodyContent = if (bodyStart != -1 && bodyEnd > bodyStart) {
            rawText.substring(bodyStart + 6, bodyEnd)
        } else {
            rawText
        }

        // Strip binaries
        var clean = bodyContent.replace("""<binary[^>]*>[\s\S]*?</binary>""".toRegex(RegexOption.IGNORE_CASE), "")
        // Handle chapters/titles
        clean = clean.replace("""<title[^>]*>""".toRegex(RegexOption.IGNORE_CASE), "\u000C\n")
        clean = clean.replace("""</title>""".toRegex(RegexOption.IGNORE_CASE), "\n\n")
        // Handle paragraphs
        clean = clean.replace("""<p[^>]*>""".toRegex(RegexOption.IGNORE_CASE), "    ")
        clean = clean.replace("""</p>""".toRegex(RegexOption.IGNORE_CASE), "\n")
        // Remove remaining tags
        clean = clean.replace("<[^>]*>".toRegex(), "")

        // Normalize white spaces
        clean = clean.replace("\r", "")
        clean = clean.replace("\n{3,}".toRegex(), "\n\n")
        
        return clean.lines().joinToString("\n") { line ->
            if (line.trim().isEmpty()) ""
            else {
                val indent = line.takeWhile { it.isWhitespace() }
                val trimmed = line.substring(indent.length).replace("\\s+".toRegex(), " ")
                indent + trimmed
            }
        }.trim()
    }

    private fun preprocessTextAndHyphenate(rawContent: String): String {
        // Step A: Re-format paragraphs to have minimal distance and clean 4-space indents
        val processedParagraphs = rawContent.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "    $it" }

        // Step B: Inject soft hyphens (\u00AD) using the fast, optimized RussianHyphenator
        return RussianHyphenator.hyphenate(processedParagraphs)
    }

    private fun splitContentToPages(content: String, measuredWidth: Int, measuredHeight: Int): List<String> {
        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity
        
        // Measure viewPager size, fall back to screen size if not measured yet
        var width = measuredWidth
        var height = measuredHeight
        if (width <= 0 || height <= 0) {
            val displayMetrics = resources.displayMetrics
            width = displayMetrics.widthPixels
            height = displayMetrics.heightPixels
        }
        
        // Horizontal padding: 16dp on each side (32dp total)
        val paddingHorizontal = (32 * density).toInt()
        
        val availableWidth = (width - paddingHorizontal).coerceAtLeast(100)
        
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val statusBarTop = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val displayCutoutTop = insets?.getInsets(WindowInsetsCompat.Type.displayCutout())?.top ?: 0
        val paddingTop = maxOf(statusBarTop, displayCutoutTop)
        val paddingBottom = (12 * density).toInt()

        // We must subtract paddingTop and paddingBottom to get the actual canvas height available for text layout
        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(100)

        Log.d("READING_DEBUG", "splitContentToPages: screenHeight=$height, availableHeight=$availableHeight, availableWidth=$availableWidth, paddingTop=$paddingTop, paddingBottom=$paddingBottom")
        
        val fontSize = com.example.data.SettingsManager.getFontSize(this)
        val fontFamily = com.example.data.SettingsManager.getFontFamily(this)
        val fontWeight = com.example.data.SettingsManager.getFontWeight(this)
        val lineSpacingMultiplier = com.example.data.SettingsManager.getLineSpacing(this)

        val baseTypeface = when (fontFamily) {
            "Roboto" -> android.graphics.Typeface.SANS_SERIF
            "Times New Roman" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            "Georgia" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            "Merriweather" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            "OpenDyslexic" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
            "Monospace" -> android.graphics.Typeface.MONOSPACE
            else -> android.graphics.Typeface.DEFAULT
        }
        val style = when (fontWeight) {
            "Bold" -> android.graphics.Typeface.BOLD
            else -> android.graphics.Typeface.NORMAL
        }
        val resolvedTypeface = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val numericWeight = when (fontWeight) {
                "Normal" -> 400
                "Medium" -> 500
                "Bold" -> 700
                "ExtraBold" -> 900
                else -> 400
            }
            android.graphics.Typeface.create(baseTypeface, numericWeight, false)
        } else {
            android.graphics.Typeface.create(baseTypeface, style)
        }

        val paint = android.text.TextPaint().apply {
            textSize = fontSize * scaledDensity
            typeface = resolvedTypeface
        }
        
        val result = PageSplitter.splitText(
            text = content,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            paint = paint,
            lineSpacing = lineSpacingMultiplier,
            alignment = "justify"
        )
        return result.pages
    }

    private fun resetHideTimer() {
        // Empty to prevent hiding UI elements on a timer as per requirements
    }

    private fun hideSystemUi() {
        isSystemUiVisible = false
        hideHandler.removeCallbacks(hideRunnable)

        // Immersive full screen: hide status and navigation bars using WindowInsetsControllerCompat
        val window = this.window
        val decorView = window.decorView
        val controller = WindowCompat.getInsetsController(window, decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val density = resources.displayMetrics.density
        val topTargetY = -(topBar.height.toFloat().takeIf { it > 0 } ?: (56 * density))
        val bottomTargetY = (bottomBar.height.toFloat().takeIf { it > 0 } ?: (150 * density))

        topBar.animate()
            .translationY(topTargetY)
            .alpha(0f)
            .setDuration(300)
            .setListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!isSystemUiVisible) {
                        topBar.visibility = View.GONE
                    }
                }
            })

        bottomBar.animate()
            .translationY(bottomTargetY)
            .alpha(0f)
            .setDuration(300)
            .setListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!isSystemUiVisible) {
                        bottomBar.visibility = View.GONE
                    }
                }
            })
    }

    private fun showSystemUi() {
        isSystemUiVisible = true
        hideHandler.removeCallbacks(hideRunnable)

        // Show status and navigation bars using WindowInsetsControllerCompat
        val window = this.window
        val decorView = window.decorView
        val controller = WindowCompat.getInsetsController(window, decorView)
        controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())

        val density = resources.displayMetrics.density
        val topStartY = -(topBar.height.toFloat().takeIf { it > 0 } ?: (56 * density))
        val bottomStartY = (bottomBar.height.toFloat().takeIf { it > 0 } ?: (150 * density))

        if (topBar.visibility == View.GONE) {
            topBar.visibility = View.VISIBLE
            topBar.alpha = 0f
            topBar.translationY = topStartY
        }
        if (bottomBar.visibility == View.GONE) {
            bottomBar.visibility = View.VISIBLE
            bottomBar.alpha = 0f
            bottomBar.translationY = bottomStartY
        }

        topBar.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .setListener(null)

        bottomBar.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(300)
            .setListener(null)

        resetHideTimer()
    }

    fun toggleSystemUi() {
        if (isSystemUiVisible) {
            hideSystemUi()
        } else {
            showSystemUi()
        }
    }

    override fun onPause() {
        super.onPause()
        hideHandler.removeCallbacks(hideRunnable)
        if (pages.isNotEmpty() && bookSha1.isNotEmpty()) {
            val currentPageIndex = viewPager.currentItem
            val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putInt("book_page_$bookSha1", currentPageIndex).apply()
            Log.d(TAG, "Сохранена позиция при паузе: страница ${currentPageIndex + 1}")

            // Update database progress to show correctly on main shelf
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = AppDatabase.getDatabase(this@ReaderActivity)
                    val book = db.bookDao().getBookBySha1(bookSha1)
                    if (book != null) {
                        val newOffset = (currentPageIndex * 1000).coerceIn(0, book.totalCharacters)
                        db.bookDao().updateProgress(bookSha1, newOffset, System.currentTimeMillis())
                        Log.d(TAG, "Успешно обновлен прогресс в БД: SHA-1 = $bookSha1, смещение = $newOffset")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при обновлении прогресса в БД при паузе", e)
                }
            }
        }
    }

    /**
     * Кастомный PageTransformer для имитации 3D-эффекта перелистывания страниц бумажной книги.
     * Реализует вращение по оси Y (эффект переворота листа), а также плавное масштабирование (scale)
     * и прозрачность (alpha) для создания реалистичной глубины и объема.
     */
    class BookFlipPageTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            val width = page.width.toFloat()
            val height = page.height.toFloat()

            // Настройка расстояния камеры для предотвращения обрезания и 3D-искажений
            page.cameraDistance = 20000f

            // Динамический расчет Z-index (translationZ) для предотвращения наложения текста и слоев
            if (position < -1f || position > 1f) {
                page.translationZ = -100f
            } else if (position <= 0f) {
                page.translationZ = 100f - position
            } else {
                page.translationZ = -100f + (1f - position)
            }

            when {
                position < -1f -> { // [-Infinity, -1)
                    // Страница полностью ушла влево за пределы экрана
                    page.alpha = 0f
                }
                position <= 0f -> { // [-1, 0]
                    // Текущая страница плавно перелистывается влево (уходит)
                    // Плавное исчезновение при полном перевороте на 90 градусов
                    page.alpha = 1f + position

                    // Нейтрализуем стандартный сдвиг ViewPager2, чтобы зафиксировать страницу на левой оси (корешок книги)
                    page.translationX = -position * width

                    // Точка вращения находится на левой границе по центру вертикали (корешок)
                    page.pivotX = 0f
                    page.pivotY = height / 2f

                    // Поворачиваем страницу на угол до -90 градусов
                    page.rotationY = 90f * position

                    // Применяем легкое масштабирование для имитации ухода вглубь при повороте
                    val scaleFactor = 1f + (0.05f * position) // от 0.95 до 1.0
                    page.scaleX = scaleFactor
                    page.scaleY = scaleFactor
                }
                position <= 1f -> { // (0, 1]
                    // Следующая страница лежит плоско под текущей и ждет раскрытия.
                    // Она плавно проявляется по мере открытия верхней страницы.
                    page.alpha = 1f - position * 0.5f // от 0.5 до 1.0

                    // Нейтрализуем стандартный сдвиг ViewPager2, сохраняя страницу абсолютно статичной
                    page.translationX = -position * width

                    // Точка масштабирования находится в центре страницы
                    page.pivotX = width / 2f
                    page.pivotY = height / 2f
                    page.rotationY = 0f

                    // Страница плавно увеличивается (поднимается на передний план) при раскрытии верхнего листа
                    val scaleFactor = 0.95f + (0.05f * (1f - position)) // от 0.95 до 1.0
                    page.scaleX = scaleFactor
                    page.scaleY = scaleFactor
                }
                else -> { // (1, +Infinity]
                    // Страница находится далеко справа за пределами экрана
                    page.alpha = 0f
                }
            }
        }
    }
}
