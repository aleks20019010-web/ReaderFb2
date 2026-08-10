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
import com.nightread.app.service.TtsForegroundService
import com.nightread.app.tts.AppTtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File

class BookReaderActivity : FragmentActivity() {

    private var openedBookTitle: String = ""
    private var openedBookSha1: String = ""
    private var openedBookText: String = ""
    private var ttsManager: AppTtsManager? = null

    var onNextPage: (() -> Unit)? = null
    var onPrevPage: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    onNextPage?.invoke()
                    return true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    onPrevPage?.invoke()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                                val contentFile = File(cacheDir, "$sha1.content")
                                if (contentFile.exists()) {
                                    val cached = contentFile.readText()
                                    if (cached.contains("\ufffd") || cached.contains("") || cached.startsWith("[sGv")) {
                                        contentFile.delete()
                                    }
                                }
                                if (contentFile.exists()) {
                                    bookText = cleanHtmlContent(contentFile.readText())
                                } else if (!book.filePath.isNullOrEmpty()) {
                                    val f = File(book.filePath)
                                    if (f.exists()) {
                                        val ext = f.extension.lowercase()
                                        val text = when (ext) {
                                            "fb3" -> com.nightread.app.service.Fb3Parser.parse(f, f.nameWithoutExtension).content
                                            "epub" -> com.nightread.app.service.EpubParser.parse(f, f.nameWithoutExtension).content
                                            "mobi", "azw", "azw3" -> com.nightread.app.service.MobiParser.parse(f, f.nameWithoutExtension).content
                                            "zip" -> readZipFile(f)
                                            else -> decodeBytesToString(f.readBytes())
                                        }
                                        val cleaned = cleanHtmlContent(text)
                                        try { contentFile.writeText(cleaned) } catch (e: Exception) {}
                                        bookText = cleaned
                                    } else {
                                        bookText = "Файл книги не найден на диске"
                                    }
                                }
                                val dbAnnot = book.annotation
                                if (!dbAnnot.isNullOrBlank() && !bookText.contains("[ANNOTATION]") && !bookText.take(300).contains("Аннотация", ignoreCase = true)) {
                                    bookText = "[ANNOTATION]\n$dbAnnot\n[/ANNOTATION]\n\n$bookText"
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

    fun pauseTts() {
        try {
            val intent = Intent(this, TtsForegroundService::class.java).apply {
                action = TtsForegroundService.ACTION_PAUSE
            }
            startService(intent)
        } catch (e: Exception) {
            ttsManager?.pause()
        }
        Toast.makeText(this, "Озвучивание приостановлено", Toast.LENGTH_SHORT).show()
    }

    fun startOrResumeTts() {
        try {
            val intent = Intent(this, TtsForegroundService::class.java).apply {
                action = TtsForegroundService.ACTION_START
                putExtra(TtsForegroundService.EXTRA_TEXT, openedBookText)
                putExtra(TtsForegroundService.EXTRA_BOOK_TITLE, openedBookTitle)
                putExtra(TtsForegroundService.EXTRA_SPEED, SettingsManager.getTtsSpeed(this@BookReaderActivity))
                putExtra(TtsForegroundService.EXTRA_PITCH, SettingsManager.getTtsPitch(this@BookReaderActivity))
                putExtra(TtsForegroundService.EXTRA_VOICE, SettingsManager.getTtsVoice(this@BookReaderActivity))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            if (ttsManager == null) {
                ttsManager = AppTtsManager(applicationContext)
            }
            if (openedBookText.isNotEmpty()) {
                ttsManager?.speak(openedBookText.take(2000))
            }
        }
        Toast.makeText(this, "Озвучивание запущено", Toast.LENGTH_SHORT).show()
    }

    fun stopTts() {
        try {
            val intent = Intent(this, TtsForegroundService::class.java).apply {
                action = TtsForegroundService.ACTION_STOP
            }
            startService(intent)
        } catch (e: Exception) {
            ttsManager?.stop()
        }
        Toast.makeText(this, "Озвучивание остановлено", Toast.LENGTH_SHORT).show()
    }

    fun readPreviousTtsChunk() {
        startOrResumeTts()
    }

    fun readNextTtsChunk() {
        startOrResumeTts()
    }

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
        return try {
            val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(html)
            }
            spanned.toString()
                .replace(Regex("[ \t]+\\n"), "\n")
                .replace(Regex("\\n[ \t]+"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        } catch (e: Exception) {
            html.replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
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
        try {
            java.io.FileInputStream(file).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    var fallbackBytes: ByteArray? = null
                    while (entry != null) {
                        val entryName = entry.name.lowercase()
                        if (!entry.isDirectory && !entryName.startsWith("__macosx") && !entryName.contains(".ds_store")) {
                            if (entryName.endsWith(".fb3")) {
                                val bytes = zis.readBytes()
                                return com.nightread.app.service.Fb3Parser.parseBytes(bytes, entryName.removeSuffix(".fb3")).content
                            } else if (entryName.endsWith(".fb2") || entryName.endsWith(".xml") || entryName.endsWith(".html") || entryName.endsWith(".htm") || entryName.endsWith(".txt")) {
                                val bytes = zis.readBytes()
                                val decoded = decodeBytesToString(bytes)
                                if (decoded.isNotBlank()) return decoded
                            } else if (fallbackBytes == null) {
                                fallbackBytes = zis.readBytes()
                            }
                        }
                        entry = zis.nextEntry
                    }
                    fallbackBytes?.let {
                        val decoded = decodeBytesToString(it)
                        if (decoded.isNotBlank()) return decoded
                    }
                }
            }
        } catch (e: Exception) {}
        return ""
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
