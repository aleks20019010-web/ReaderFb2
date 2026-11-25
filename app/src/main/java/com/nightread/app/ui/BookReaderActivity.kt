package com.nightread.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.NoteManager
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.DictionaryDownloader
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

class BookReaderActivity : FragmentActivity() {

    private var openedBookTitle: String = ""
    private var openedBookSha1: String = ""
    private var openedBookText: String = ""
    var onNextPage: (() -> Unit)? = null
    var onPrevPage: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
        ) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> onNextPage?.invoke()
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> onPrevPage?.invoke()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        openedBookSha1 = sha1

        setContent {
            var bookTitle by remember { mutableStateOf("Загрузка книги...") }
            var authorName by remember { mutableStateOf("") }
            var bookText by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(sha1) {
                if (sha1.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val db = AppDatabase.getDatabase(applicationContext)
                            val book = db.bookDao().getBookBySha1(sha1)
                            if (book != null) {
                                bookTitle = book.title ?: "Без названия"
                                authorName = book.author ?: ""
                                openedBookTitle = bookTitle

                                val memCached = com.nightread.app.data.ReaderDocumentCache.getRawContent(sha1)
                                if (!memCached.isNullOrEmpty()) {
                                    bookText = memCached
                                } else {
                                    val contentFile = File(cacheDir, "$sha1.rich.content")
                                    val diskFile = if (!book.filePath.isNullOrEmpty()) File(book.filePath) else null
                                    if (contentFile.exists()) {
                                        val cached = try { contentFile.readText() } catch (_: Throwable) { "" }
                                        val diskLen = diskFile?.takeIf { it.exists() }?.length() ?: 0L
                                        val looksTruncated = diskLen > 8000L && cached.length < 1500 && !cached.contains("[CHAPTER]")
                                        val missingParagraphBreaks = cached.length > 2000 && !cached.contains("\n")
                                        if (cached.isBlank() || cached.contains("\ufffd") || cached.startsWith("[sGv") || looksTruncated || missingParagraphBreaks) {
                                            contentFile.delete()
                                        }
                                    }
                                    if (contentFile.exists()) {
                                        bookText = cleanHtmlContent(contentFile.readText())
                                    } else if (diskFile != null && diskFile.exists()) {
                                        val ext = diskFile.extension.lowercase()
                                        val text = when (ext) {
                                            "fb3" -> com.nightread.app.service.Fb3Parser.parse(diskFile, diskFile.nameWithoutExtension).content
                                            "epub" -> com.nightread.app.service.EpubParser.parse(diskFile, diskFile.nameWithoutExtension).content
                                            "fb2" -> com.nightread.app.service.Fb2Parser.parseFullFb2(diskFile, diskFile.nameWithoutExtension).content
                                            "mobi", "azw", "azw3" -> com.nightread.app.service.MobiParser.parse(diskFile, diskFile.nameWithoutExtension).content
                                            "zip", "fbz" -> readZipFile(diskFile)
                                            else -> decodeBytesToString(diskFile.readBytes())
                                        }
                                        val cleaned = cleanHtmlContent(text)
                                        if (cleaned.isNotBlank()) {
                                            try { contentFile.writeText(cleaned) } catch (e: Exception) {}
                                        }
                                        bookText = cleaned
                                    } else if (!book.filePath.isNullOrEmpty()) {
                                        bookText = "Файл книги не найден на диске"
                                    }
                                    val dbAnnot = book.annotation
                                    if (!dbAnnot.isNullOrBlank() && !bookText.contains("[ANNOTATION]") && !bookText.take(300).contains("Аннотация", ignoreCase = true)) {
                                        bookText = "[ANNOTATION]\n$dbAnnot\n[/ANNOTATION]\n\n$bookText"
                                    }
                                    if (bookText.isNotBlank()) {
                                        com.nightread.app.data.ReaderDocumentCache.putRawContent(sha1, bookText)
                                    }
                                }
                                openedBookText = bookText
                            } else {
                                bookTitle = "Книга не найдена"
                                bookText = "Информация о книге отсутствует в базе данных"
                            }
                        } catch (e: Exception) {
                            bookTitle = "Ошибка загрузки"
                            bookText = e.localizedMessage ?: "Не удалось открыть книгу"
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    bookTitle = "Ошибка"
                    bookText = "Не указан идентификатор книги"
                    isLoading = false
                }
            }

            ReaderComposeScreen(
                sha1 = sha1,
                bookTitle = bookTitle,
                authorAndChapter = if (authorName.isNotEmpty()) authorName else "Чтение",
                mainText = bookText,
                isLoading = isLoading,
                onBackClick = { finish() }
            )
        }
    }

    fun navigateToParagraph(paragraphIndex: Int) {
        Toast.makeText(this, "Переход к абзацу ${paragraphIndex + 1}", Toast.LENGTH_SHORT).show()
    }

    fun loadPage(pageIndex: Int) {
        Toast.makeText(this, "Переход к странице ${pageIndex + 1}", Toast.LENGTH_SHORT).show()
    }

    val navigationEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    fun navigateToOffset(offset: Int) {
        navigationEvents.tryEmit(offset)
    }

    fun fetchAndShowFreeDictionary(word: String) {
        if (word.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val dictFile = DictionaryDownloader.getDictionaryFile(this@BookReaderActivity)
            var translation: String? = null
            if (dictFile.exists()) {
                try {
                    val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        dictFile.path,
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                    )
                    val cursor = db.rawQuery("SELECT translation FROM dict WHERE LOWER(word) = ? LIMIT 1", arrayOf(word.lowercase().trim()))
                    if (cursor.moveToFirst()) {
                        translation = cursor.getString(0)
                    }
                    cursor.close()
                    db.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
            withContext(Dispatchers.Main) {
                val message = if (!translation.isNullOrBlank()) {
                    "$word: $translation"
                } else {
                    "Слово '$word' не найдено в локальном словаре."
                }
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@BookReaderActivity)
                    .setTitle("Словарь: $word")
                    .setMessage(message)
                    .setPositiveButton("ОК", null)
                    .show()
            }
        }
    }

    fun onReaderAutoThemeSettingChanged() {
        SettingsManager.setAutoThemeEnabled(this, true)
    }

    fun onAutoBrightnessSettingChanged(enabled: Boolean) {
        SettingsManager.setAutoBrightnessEnabled(this, enabled)
        if (enabled) {
            window.attributes = window.attributes.apply {
                screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    fun getOpenedBookTitle(): String = openedBookTitle


    fun performSmartSearch(query: String) {
        if (query.isBlank() || openedBookText.isBlank()) return
        val matches = openedBookText.windowed(query.length, 1).count { it.equals(query, ignoreCase = true) }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Поиск: '$query'")
            .setMessage(if (matches > 0) "Найдено совпадений: $matches" else "Совпадений не найдено")
            .setPositiveButton("ОК", null)
            .show()
    }

    fun saveNoteForBook(word: String, note: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                NoteManager(this@BookReaderActivity).addNote(
                    bookId = openedBookSha1.ifEmpty { "default_book" },
                    bookTitle = openedBookTitle.ifEmpty { "Книга" },
                    selectedText = word,
                    noteText = note,
                    charOffset = 0
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BookReaderActivity, "Заметка сохранена", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BookReaderActivity, "Ошибка сохранения заметки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun cleanHtmlContent(html: String): String {
        if (html.isBlank()) return ""
        val hasHtmlTags = Regex("<(?:p|div|br|h[1-6]|html|body|span|section|blockquote|li|tr)\\b", RegexOption.IGNORE_CASE).containsMatchIn(html)
        if (!hasHtmlTags) {
            return html
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("&nbsp;", " ")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&hellip;", "…")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace(Regex("[ \t]+\\n"), "\n")
                .replace(Regex("\\n[ \t]+"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        }
        return try {
            val normalizedHtml = html
                .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
                .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
                .replace(Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { m ->
                    val heading = m.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    if (heading.isNotEmpty()) "\n[CHAPTER]$heading[/CHAPTER]\n\n" else ""
                }
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</(?:p|div|li|blockquote|section|tr|h[4-6])>", RegexOption.IGNORE_CASE), "\n\n")
                .replace(Regex("<(?:strong|b)\\b[^>]*>", RegexOption.IGNORE_CASE), "<b>")
                .replace(Regex("</(?:strong|b)>", RegexOption.IGNORE_CASE), "</b>")
                .replace(Regex("<(?:em|i)\\b[^>]*>", RegexOption.IGNORE_CASE), "<i>")
                .replace(Regex("</(?:em|i)>", RegexOption.IGNORE_CASE), "</i>")
                .replace(Regex("<u\\b[^>]*>", RegexOption.IGNORE_CASE), "<u>")
                .replace(Regex("</u>", RegexOption.IGNORE_CASE), "</u>")
                .replace(Regex("<(?:s|del|strike)\\b[^>]*>", RegexOption.IGNORE_CASE), "<s>")
                .replace(Regex("</(?:s|del|strike)>", RegexOption.IGNORE_CASE), "</s>")
                .replace(Regex("<(sup|sub|code)\\b[^>]*>", RegexOption.IGNORE_CASE), "<$1>")
                .replace(Regex("<(?!/?(?:b|i|u|s|sup|sub|code)\\b)[^>]*>", RegexOption.IGNORE_CASE), "")

            normalizedHtml
                .replace("&nbsp;", " ")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&hellip;", "…")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace(Regex("[ \t]+\\n"), "\n")
                .replace(Regex("\\n[ \t]+"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        } catch (e: Exception) {
            html.replace(Regex("<(?!/?(?:b|i|u|s|sup|sub|code)\\b)[^>]*>", RegexOption.IGNORE_CASE), "")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        }
    }

    private fun readZipFile(file: File): String {
        if (com.nightread.app.service.Fb3Parser.isFb3(file)) {
            val parsed = com.nightread.app.service.Fb3Parser.parseFb3(file, file.nameWithoutExtension)
            if (parsed.content.isNotBlank()) return parsed.content
        }
        if (com.nightread.app.data.EpubIdentifierHelper.isEpub(file)) {
            val parsed = com.nightread.app.service.EpubParser.parse(file, file.nameWithoutExtension)
            if (parsed.content.isNotBlank()) return parsed.content
        }
        try {
            java.io.FileInputStream(file).buffered().use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    var fallbackContent: String? = null
                    while (entry != null) {
                        val entryName = entry.name.replace("\\", "/").lowercase()
                        val fileName = entryName.substringAfterLast("/")
                        if (!entry.isDirectory && !entryName.contains("__macosx/") && !fileName.startsWith("._") && fileName != ".ds_store") {
                            when {
                                entryName.endsWith(".fb3") -> {
                                    val bytes = zis.readBytes()
                                    val parsed = com.nightread.app.service.Fb3Parser.parseBytes(bytes, fileName.removeSuffix(".fb3"))
                                    if (parsed.content.isNotBlank()) return parsed.content
                                }
                                entryName.endsWith(".fb2") -> {
                                    val parsed = com.nightread.app.service.Fb2Parser.parseFullFb2Stream(zis, fileName.removeSuffix(".fb2"))
                                    if (parsed.content.isNotBlank()) return parsed.content
                                }
                                entryName.endsWith(".epub") -> {
                                    val tempEpub = File.createTempFile("read_epub_", ".epub", cacheDir)
                                    try {
                                        tempEpub.outputStream().buffered().use { fos -> zis.copyTo(fos) }
                                        val parsed = com.nightread.app.service.EpubParser.parse(tempEpub, fileName.removeSuffix(".epub"))
                                        if (parsed.content.isNotBlank()) return parsed.content
                                    } finally {
                                        tempEpub.delete()
                                    }
                                }
                                entryName.endsWith(".mobi") || entryName.endsWith(".azw") || entryName.endsWith(".azw3") -> {
                                    val bytes = zis.readBytes()
                                    val parsed = com.nightread.app.service.MobiParser.parseBytes(bytes, fileName.substringBeforeLast("."))
                                    if (parsed.content.isNotBlank()) return parsed.content
                                }
                                entryName.endsWith(".xml") || entryName.endsWith(".html") || entryName.endsWith(".htm") -> {
                                    val decoded = decodeBytesToString(zis.readBytes())
                                    if (decoded.isNotBlank()) return decoded
                                }
                                fallbackContent == null -> {
                                    fallbackContent = decodeBytesToString(zis.readBytes())
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                    fallbackContent?.let {
                        if (it.isNotBlank()) return it
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BookReaderActivity", "Error reading zip file ${file.name}", e)
        }
        return ""
    }

    override fun onPause() {
        super.onPause()
        if (openedBookSha1.isNotEmpty()) {
            val sp = getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
            val offset = sp.getInt("book_char_offset_$openedBookSha1", -1)
            if (offset >= 0) {
                sp.edit()
                    .putInt("book_char_offset_$openedBookSha1", offset)
                    .putLong("book_last_timestamp_$openedBookSha1", System.currentTimeMillis())
                    .commit()

                try {
                    com.nightread.app.data.SafeProgressManager.getInstance(this).saveProgressSync(
                        bookId = openedBookSha1,
                        pageIndex = 0,
                        totalPages = 0,
                        textOffset = offset
                    )
                } catch (e: Exception) {
                    android.util.Log.e("BookReaderActivity", "Failed to flush progress onPause", e)
                }
            }
        }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        try {
            val headerSize = if (bytes.size > 1024) 1024 else bytes.size
            val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val encName = match.groupValues[1].trim()
                try {
                    return String(bytes, java.nio.charset.Charset.forName(encName))
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}

        try {
            val utf8Decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            utf8Decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (e: Exception) {
            try {
                return String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                return String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }
}