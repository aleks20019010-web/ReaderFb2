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
                                    bookText = cleanHtmlContent(contentFile.readText())
                                } else if (!book.filePath.isNullOrEmpty()) {
                                    val f = File(book.filePath)
                                    if (f.exists()) {
                                        val text = when (f.extension.lowercase()) {
                                            "fb3" -> com.nightread.app.service.Fb3Parser.parse(f, f.nameWithoutExtension).content
                                            "epub" -> com.nightread.app.service.EpubParser.parse(f, f.nameWithoutExtension).content
                                            "mobi", "azw", "azw3" -> com.nightread.app.service.MobiParser.parse(f, f.nameWithoutExtension).content
                                            else -> f.readText()
                                        }
                                        try { contentFile.writeText(text) } catch (e: Exception) {}
                                        bookText = text
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
        var text = html
            .replace(Regex("</p>|<br\\s*/?>|</div\\s*>"), "\n\n")
            .replace(Regex("<p[^>]*>|<div[^>]*>"), "")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        return text.trim()
    }
}
