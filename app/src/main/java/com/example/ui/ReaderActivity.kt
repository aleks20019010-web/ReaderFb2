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

    private var isSystemUiVisible = true
    private var bookId: Int = -1
    private var pages: List<String> = emptyList()

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

        // Programmatic layout assembly for robustness and speed
        setupLayout()

        // Load content in a safe IO coroutine
        loadBookContent()
    }

    private fun setupLayout() {
        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#FAF6EE")) // Warm paper background
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
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FAF6EE"))
                setStroke(1, Color.parseColor("#E0DCD3"))
            }
        }

        val backButton = TextView(this).apply {
            text = "◀"
            textSize = 20f
            setTextColor(Color.parseColor("#2C2C2C"))
            gravity = Gravity.CENTER
            setPadding(0, 0, (16 * density).toInt(), 0)
            setOnClickListener {
                finish()
            }
        }
        topBar.addView(backButton)

        titleText = TextView(this).apply {
            text = "Чтение"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2C2C2C"))
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        topBar.addView(titleText)
        root.addView(topBar)

        // Bottom Controls Overlay Bar
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply {
                gravity = Gravity.BOTTOM
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FAF6EE"))
                setStroke(1, Color.parseColor("#E0DCD3"))
            }
        }

        progressText = TextView(this).apply {
            text = "Загрузка..."
            textSize = 14f
            setTextColor(Color.parseColor("#706B64"))
        }
        bottomBar.addView(progressText)
        root.addView(bottomBar)

        setContentView(root)
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
                    splitContentToPages(rawContent)
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

                // Restore saved page
                val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
                val savedPage = sharedPrefs.getInt("book_page_$bookId", 0)
                val targetPage = savedPage.coerceIn(0, pages.size - 1)
                
                viewPager.setCurrentItem(targetPage, false)
                progressText.text = "Страница ${targetPage + 1} из ${pages.size}"

                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        progressText.text = "Страница ${position + 1} из ${pages.size}"
                        Log.d(TAG, "Текущая страница изменена на: ${position + 1}")
                        
                        // Save page progress dynamically
                        sharedPrefs.edit().putInt("book_page_$bookId", position).apply()
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
        // 1. Try checking for XML encoding attribute
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

        // 2. Try UTF-8
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

        // 3. Try Windows-1251
        Log.d(TAG, "Определение кодировки: пробуем windows-1251...")
        try {
            val charset = Charset.forName("windows-1251")
            Log.d(TAG, "Определение кодировки: успешно определена как windows-1251")
            val content = String(bytes, charset)
            return if (fileName.endsWith(".fb2")) parseFb2(content) else content
        } catch (e: Exception) {
            Log.d(TAG, "Определение кодировки: windows-1251 не подошла")
        }

        // 4. Try KOI8-R
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

    fun toggleSystemUi() {
        isSystemUiVisible = !isSystemUiVisible
        topBar.visibility = if (isSystemUiVisible) View.VISIBLE else View.GONE
        bottomBar.visibility = if (isSystemUiVisible) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        // Ensure current page is preserved on pause
        if (pages.isNotEmpty() && bookId != -1) {
            val currentPageIndex = viewPager.currentItem
            val sharedPrefs = getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit().putInt("book_page_$bookId", currentPageIndex).apply()
            Log.d(TAG, "Сохранена позиция при паузе: страница ${currentPageIndex + 1}")
        }
    }
}
