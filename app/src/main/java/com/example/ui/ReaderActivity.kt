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

        // Setup theme immediately
        applyThemeColors()

        // Hide bars initially
        topBar.visibility = View.GONE
        bottomBar.visibility = View.GONE
        isSystemUiVisible = false

        setContentView(root)
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
                bgColorHex = "#121212"
                textColorHex = "#E0E0E0"
                panelBgHex = "#1E1E1E"
                borderHex = "#2D2D2D"
            }
            "sepia" -> {
                bgColorHex = "#FAF6EE"
                textColorHex = "#2C2C2C"
                panelBgHex = "#FAF6EE"
                borderHex = "#E0DCD3"
            }
        }
        
        val bgColor = Color.parseColor(bgColorHex)
        val textColor = Color.parseColor(textColorHex)
        val panelBg = Color.parseColor(panelBgHex)
        val border = Color.parseColor(borderHex)
        
        findViewById<View>(android.R.id.content)?.setBackgroundColor(bgColor)
        viewPager.setBackgroundColor(bgColor)
        
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
        
        // Update SeekBar colors if supported
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val colorStateList = android.content.res.ColorStateList.valueOf(btnTextColor)
            seekBar.progressTintList = colorStateList
            seekBar.thumbTintList = colorStateList
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

    private fun loadBookContent() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Шаг 1: Получение пути к книге...")
                val db = AppDatabase.getDatabase(this@ReaderActivity)
                val book = withContext(Dispatchers.IO) {
                    db.bookDao().getBookById(bookId)
                }

                if (book == null) {
                    Log.e(TAG, "Книга с id=$bookId не найдена в базе данных")
                    Toast.makeText(this@ReaderActivity, "Книга не найдена в базе", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                titleText.text = book.title

                val rawContent: String
                val path = book.filePath

                if (!path.isNullOrEmpty()) {
                    Log.d(TAG, "Шаг 2: Проверка существования файла...")
                    val file = File(path)
                    if (!file.exists()) {
                        Log.e(TAG, "Файл не найден по пути: $path")
                        Toast.makeText(this@ReaderActivity, "Файл не найден", Toast.LENGTH_SHORT).show()
                        finish()
                        return@launch
                    }

                    val size = file.length()
                    Log.d(TAG, "Файл существует. Размер: $size байт, Путь: $path")
                    if (size == 0L) {
                        Log.e(TAG, "Книга пуста (0 байт)")
                        Toast.makeText(this@ReaderActivity, "Книга пуста", Toast.LENGTH_SHORT).show()
                        finish()
                        return@launch
                    }

                    Log.d(TAG, "Шаг 3: Определение кодировки и чтение содержимого...")
                    rawContent = withContext(Dispatchers.IO) {
                        if (path.lowercase().endsWith(".zip")) {
                            readZipFile(file)
                        } else {
                            readBookFile(file)
                        }
                    }
                } else {
                    Log.d(TAG, "Путь к файлу отсутствует. Загружаем текст напрямую из базы данных...")
                    rawContent = book.content
                }

                Log.d(TAG, "Шаг 4: Чтение содержимого выполнено. Количество символов: ${rawContent.length}")
                if (rawContent.trim().isEmpty()) {
                    Log.e(TAG, "Содержимое книги пусто")
                    Toast.makeText(this@ReaderActivity, "Книга пуста", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                Log.d(TAG, "Шаг 5: Разбивка на страницы...")
                pages = withContext(Dispatchers.Default) {
                    // Preprocess paragraph structure & apply syllabic hyphenation on a background thread
                    val formattedText = preprocessTextAndHyphenate(rawContent)
                    splitContentToPages(formattedText)
                }

                Log.d(TAG, "Количество страниц: ${pages.size}")
                if (pages.isEmpty()) {
                    Log.e(TAG, "Не удалось разбить текст на страницы (0 страниц)")
                    Toast.makeText(this@ReaderActivity, "Не удалось разбить текст", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                Log.d(TAG, "Шаг 6: Передача данных в адаптер...")
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

                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        progressText.text = "Стр. ${position + 1} из ${pages.size}"
                        seekBar.progress = position
                        Log.d(TAG, "Текущая страница изменена на: ${position + 1}")
                        
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
                Log.e(TAG, "Ошибка во время чтения/отображения книги", e)
                Toast.makeText(this@ReaderActivity, "Ошибка чтения: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                finish()
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

        // Step B: Inject soft hyphens (\u00AD) into Russian words to enable flawless line-breaks
        val vowels = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
        val special = "ьъйЬЪЙ"
        val wordRegex = """([а-яА-ЯёЁ]+)""".toRegex()

        return wordRegex.replace(processedParagraphs) { matchResult ->
            val word = matchResult.value
            if (word.length < 4 || !word.any { vowels.contains(it) }) {
                word
            } else {
                val sb = java.lang.StringBuilder()
                var vowelCount = 0
                val totalVowels = word.count { vowels.contains(it) }

                if (totalVowels <= 1) {
                    word
                } else {
                    for (i in 0 until word.length) {
                        val char = word[i]
                        sb.append(char)

                        if (vowels.contains(char)) {
                            vowelCount++
                        }

                        // Inject hyphens at safe break points
                        if (vowelCount > 0 && vowelCount < totalVowels) {
                            // 1. Vowel-Consonant-Vowel: мо-ло-ко
                            if (vowels.contains(char) && i + 2 < word.length && !vowels.contains(word[i + 1]) && vowels.contains(word[i + 2])) {
                                if (!special.contains(word[i + 1])) {
                                    sb.append('\u00AD')
                                }
                            }
                            // 2. Consonant-Consonant: кар-та
                            else if (!vowels.contains(char) && !special.contains(char) && i + 1 < word.length && !vowels.contains(word[i + 1]) && !special.contains(word[i + 1])) {
                                var hasVowelLater = false
                                for (j in (i + 2) until word.length) {
                                    if (vowels.contains(word[j])) {
                                        hasVowelLater = true
                                        break
                                    }
                                }
                                if (hasVowelLater) {
                                    sb.append('\u00AD')
                                }
                            }
                            // 3. Special characters (Ь, Ъ, Й)
                            else if (special.contains(char) && i + 1 < word.length) {
                                var hasVowelLater = false
                                for (j in (i + 1) until word.length) {
                                    if (vowels.contains(word[j])) {
                                        hasVowelLater = true
                                        break
                                    }
                                }
                                if (hasVowelLater) {
                                    sb.append('\u00AD')
                                }
                            }
                        }
                    }
                    sb.toString()
                }
            }
        }
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
}
