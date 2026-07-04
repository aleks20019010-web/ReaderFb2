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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var fontSizeDownBtn: TextView
    private lateinit var fontSizeUpBtn: TextView
    private lateinit var themeBtn: TextView
    private lateinit var libraryBtn: TextView

    private var isSystemUiVisible = false
    private var bookId: Int = -1
    private var pages: List<String> = emptyList()

    private val hideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideRunnable = Runnable {
        hideSystemUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = vm

        // Retrieve book ID from intent
        bookId = intent.getIntExtra("BOOK_ID", -1)
        if (bookId == -1) {
            Log.e(TAG, "ReaderActivity запущен без BOOK_ID")
            Toast.makeText(this, "Книга не найдена", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Programmatic layout assembly
        setupLayout()

        // Load content
        loadBookContent()
    }

    private fun setupLayout() {
        // Enable edge-to-edge full-screen display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // ViewPager2
        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Применяем 3D-анимацию перелистывания страниц (книжный разворот)
            setPageTransformer(BookFlipPageTransformer())
        }
        root.addView(viewPager)

        // Loading ProgressBar
        progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            visibility = View.VISIBLE
        }
        root.addView(progressBar)

        // Top Controls Overlay Bar
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (56 * density).toInt()
            ).apply {
                gravity = Gravity.TOP
            }
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
        }

        backButtonView = TextView(this).apply {
            text = "◀"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, (16 * density).toInt(), 0)
            setOnClickListener {
                finish()
            }
        }
        topBar.addView(backButtonView)

        titleText = TextView(this).apply {
            text = "Чтение"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(titleText)

        infoButtonView = TextView(this).apply {
            text = "ℹ"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), 0, 0, 0)
            setOnClickListener {
                Toast.makeText(this@ReaderActivity, "Разработчик: Google AI Studio\nФорматы: FB2, TXT, ZIP", Toast.LENGTH_LONG).show()
                resetHideTimer()
            }
        }
        topBar.addView(infoButtonView)
        root.addView(topBar)

        // Bottom Controls Overlay Bar
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
        }

        // Bottom Row 1: SeekBar and Page label
        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (8 * density).toInt())
            }
        }

        progressText = TextView(this).apply {
            text = "Стр. - / -"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        seekRow.addView(progressText)

        seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = (12 * density).toInt()
            }
        }
        seekRow.addView(seekBar)
        bottomBar.addView(seekRow)

        // Bottom Row 2: settings action buttons
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fontSizeDownBtn = createPanelButton(this, "А-") {
            adjustFontSize(-1f)
        }
        actionsRow.addView(fontSizeDownBtn)

        fontSizeUpBtn = createPanelButton(this, "А+") {
            adjustFontSize(1f)
        }
        actionsRow.addView(fontSizeUpBtn)

        themeBtn = createPanelButton(this, "Тема") {
            cycleTheme()
        }
        actionsRow.addView(themeBtn)

        libraryBtn = createPanelButton(this, "Библиотека") {
            finish()
        }
        actionsRow.addView(libraryBtn)

        bottomBar.addView(actionsRow)
        root.addView(bottomBar)

        // Thin horizontal progress bar at the bottom representing completed reading progress
        readingProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (4 * density).toInt()
            ).apply {
                gravity = Gravity.BOTTOM
            }
            max = 100
            progress = 0
            visibility = View.INVISIBLE
        }
        root.addView(readingProgressBar)

        // Setup theme immediately
        applyThemeColors()

        // Hide bars initially
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        isSystemUiVisible = false

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

        setContentView(root)
        hideSystemUi()
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

    private fun adjustFontSize(delta: Float) {
        val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        val currentSize = sharedPrefs.getFloat("font_size", 18f)
        val newSize = (currentSize + delta).coerceIn(12f, 30f)
        sharedPrefs.edit().putFloat("font_size", newSize).apply()
        
        Toast.makeText(this, "Шрифт: ${newSize.toInt()}sp", Toast.LENGTH_SHORT).show()
        
        refreshAdapter()
        resetHideTimer()
    }

    private fun cycleTheme() {
        val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        val currentTheme = sharedPrefs.getString("theme", "sepia") ?: "sepia"
        val nextTheme = when (currentTheme) {
            "sepia" -> "light"
            "light" -> "dark"
            else -> "sepia"
        }
        sharedPrefs.edit().putString("theme", nextTheme).apply()
        
        applyThemeColors()
        refreshAdapter()
        resetHideTimer()
    }

    private fun applyThemeColors() {
        val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        val themeName = sharedPrefs.getString("theme", "sepia") ?: "sepia"
        
        var bgColorHex = "#FAF6EE"
        var textColorHex = "#2C2C2C"
        var panelBgHex = "#FAF6EE"
        var borderHex = "#E0DCD3"
        
        when (themeName) {
            "light" -> {
                bgColorHex = "#FFFFFF"
                textColorHex = "#121212"
                panelBgHex = "#F5F5F5"
                borderHex = "#E0E0E0"
            }
            "dark" -> {
                bgColorHex = "#1a1a1a"
                textColorHex = "#E0E0E0"
                panelBgHex = "#1E1E1E"
                borderHex = "#2D2D2D"
            }
            "sepia" -> {
                bgColorHex = "#f5f0e8"
                textColorHex = "#2C2C2C"
                panelBgHex = "#f5f0e8"
                borderHex = "#E0DCD3"
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
        
        // Style Bottom Bar
        bottomBar.background = GradientDrawable().apply {
            setColor(panelBg)
            setStroke(1, border)
        }
        progressText.setTextColor(textColor)
        
        // Update button colors
        val btnTextColor = if (themeName == "dark") Color.parseColor("#E5A93C") else Color.parseColor("#8E6E36")
        fontSizeDownBtn.setTextColor(btnTextColor)
        fontSizeUpBtn.setTextColor(btnTextColor)
        themeBtn.setTextColor(btnTextColor)
        libraryBtn.setTextColor(btnTextColor)
        
        // Update SeekBar and readingProgressBar colors if supported
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val colorStateList = android.content.res.ColorStateList.valueOf(btnTextColor)
            seekBar.progressTintList = colorStateList
            seekBar.thumbTintList = colorStateList
            readingProgressBar.progressTintList = colorStateList
            val trackColor = if (themeName == "dark") Color.parseColor("#33E5A93C") else Color.parseColor("#338E6E36")
            readingProgressBar.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(trackColor)
        }
    }

    private fun updateSystemBarsColors(isNightMode: Boolean) {
        val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        val themeName = sharedPrefs.getString("theme", "sepia") ?: "sepia"
        val bgColorHex = when (themeName) {
            "light" -> "#FFFFFF"
            "dark" -> "#1a1a1a"
            else -> "#f5f0e8" // sepia / warm paper
        }
        val bgColor = Color.parseColor(bgColorHex)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = bgColor
            window.navigationBarColor = bgColor
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                val statusFlag = if (themeName == "dark") 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                val navFlag = if (themeName == "dark") 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                controller.setSystemBarsAppearance(statusFlag, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                controller.setSystemBarsAppearance(navFlag, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            }
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            flags = flags or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                flags = if (themeName == "dark") {
                    flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else {
                    flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                flags = if (themeName == "dark") {
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
        progressBar.visibility = View.GONE
        Toast.makeText(this@ReaderActivity, message, Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000)
            finish()
        }
    }

    private fun loadBookContent() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Шаг 1: Получение пути к книге из БД... ID книги = $bookId")
                val db = AppDatabase.getDatabase(this@ReaderActivity)
                val book = withContext(Dispatchers.IO) {
                    db.bookDao().getBookById(bookId)
                }

                if (book == null) {
                    Log.e(TAG, "Ошибка: Книга с id=$bookId не найдена в базе данных")
                    showErrorAndExit("Ошибка: Книга не найдена в базе данных")
                    return@launch
                }

                titleText.text = book.title

                val rawContent: String
                val path = book.filePath

                if (!path.isNullOrEmpty()) {
                    Log.d(TAG, "Шаг 2: Проверка существования и доступности файла по пути: $path")
                    val file = File(path)
                    if (!file.exists()) {
                        Log.e(TAG, "Ошибка: Файл не найден по пути: $path")
                        showErrorAndExit("Ошибка: Файл книги не найден")
                        return@launch
                    }

                    if (!file.canRead()) {
                        Log.e(TAG, "Ошибка: Нет прав на чтение файла: $path")
                        showErrorAndExit("Ошибка: Нет прав на чтение файла книги")
                        return@launch
                    }

                    val size = file.length()
                    Log.d(TAG, "Файл доступен. Размер: $size байт, Путь: $path")
                    if (size == 0L) {
                        Log.e(TAG, "Ошибка: Файл книги пуст (0 байт)")
                        showErrorAndExit("Ошибка: Файл книги пуст")
                        return@launch
                    }

                    Log.d(TAG, "Шаг 3: Определение кодировки и чтение содержимого...")
                    rawContent = withContext(Dispatchers.IO) {
                        try {
                            if (path.lowercase().endsWith(".zip")) {
                                readZipFile(file)
                            } else {
                                readBookFile(file)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка во время чтения/распаковки файла", e)
                            throw Exception("Файл поврежден или не поддерживается: ${e.localizedMessage}")
                        }
                    }
                } else {
                    Log.d(TAG, "Путь к файлу отсутствует. Загружаем текст напрямую из базы данных...")
                    rawContent = book.content
                }

                Log.d(TAG, "Шаг 4: Чтение содержимого выполнено. Длина текста: ${rawContent.length} символов")
                if (rawContent.trim().isEmpty()) {
                    Log.e(TAG, "Ошибка: Текст книги пуст")
                    showErrorAndExit("Ошибка: Книга пуста")
                    return@launch
                }

                Log.d(TAG, "Шаг 5: Разбивка текста на страницы и применение слогового переноса...")
                pages = withContext(Dispatchers.Default) {
                    try {
                        val formattedText = preprocessTextAndHyphenate(rawContent)
                        splitContentToPages(formattedText)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка во время обработки и разбивки текста на страницы", e)
                        throw Exception("Не удалось обработать текст: ${e.localizedMessage}")
                    }
                }

                Log.d(TAG, "Результат разбивки: получено ${pages.size} страниц")
                if (pages.isEmpty()) {
                    Log.e(TAG, "Ошибка: Разбивка вернула 0 страниц")
                    showErrorAndExit("Ошибка: Не удалось разбить текст на страницы")
                    return@launch
                }

                Log.d(TAG, "Шаг 6: Инициализация и передача данных в адаптер...")
                progressBar.visibility = View.GONE

                val adapter = BookPagerAdapter(this@ReaderActivity, pages)
                viewPager.adapter = adapter

                // Configure seekbar max
                seekBar.max = (pages.size - 1).coerceAtLeast(0)

                // Restore saved page
                val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
                val savedPage = sharedPrefs.getInt("book_page_$bookId", 0)
                val targetPage = savedPage.coerceIn(0, pages.size - 1)
                
                viewPager.setCurrentItem(targetPage, false)
                seekBar.progress = targetPage
                progressText.text = "Стр. ${targetPage + 1} из ${pages.size}"

                // Show and initialize the reading progress bar
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
                        Log.d(TAG, "Текущая страница изменена на: ${position + 1}")
                        
                        // Update bottom reading progress bar
                        val progressPercent = if (pages.size > 0) {
                            ((position + 1) * 100 / pages.size).coerceIn(0, 100)
                        } else {
                            0
                        }
                        readingProgressBar.progress = progressPercent

                        // Save page progress dynamically
                        sharedPrefs.edit().putInt("book_page_$bookId", position).apply()
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

            } catch (e: Exception) {
                Log.e(TAG, "Общая ошибка при загрузке/отображении книги", e)
                showErrorAndExit("Ошибка: ${e.localizedMessage}")
            }
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

    private fun splitContentToPages(content: String): List<String> {
        val result = mutableListOf<String>()
        val pageSize = 2000
        var start = 0
        val length = content.length

        while (start < length) {
            val end = (start + pageSize).coerceAtMost(length)
            result.add(content.substring(start, end))
            start += pageSize
        }
        return result
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 3000)
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
        if (pages.isNotEmpty() && bookId != -1) {
            val currentPageIndex = viewPager.currentItem
            val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putInt("book_page_$bookId", currentPageIndex).apply()
            Log.d(TAG, "Сохранена позиция при паузе: страница ${currentPageIndex + 1}")
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
