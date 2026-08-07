package com.nightread.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BookReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""

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

    fun navigateToParagraph(paragraphIndex: Int) {}
    fun loadPage(pageIndex: Int) {}
    fun navigateToOffset(offset: Int) {}
    fun fetchAndShowFreeDictionary(word: String) {}
    fun onReaderAutoThemeSettingChanged() {}
    fun onAutoBrightnessSettingChanged(enabled: Boolean) {}
    fun getOpenedBookTitle(): String = ""
    fun pauseTts() {}
    fun startOrResumeTts() {}
    fun stopTts() {}
    fun readPreviousTtsChunk() {}
    fun readNextTtsChunk() {}
    fun performSmartSearch(query: String) {}
    fun saveNoteForBook(word: String, note: String) {}

    private fun cleanHtmlContent(html: String): String {
        return try {
            val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(html)
            }
            spanned.toString()
                .replace(Regex("\\r?\\n\\s*\\r?\\n"), "\n\n")
                .trim()
        } catch (e: Exception) {
            html.replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
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
