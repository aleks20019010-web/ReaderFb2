package com.nightread.app.scanner

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

data class FingerprintResult(
    val fingerprint: String,
    val textHash: String?,
    val author: String,
    val title: String,
    val fileSize: Long,
    val format: String
)

object BookFingerprintGenerator {

    private const val TAG = "BookFingerprint"
    private const val BUFFER_SIZE = 8192 // 8 KB

    private val STOP_WORDS = setOf(
        // English stop words
        "the", "a", "an", "and", "or", "of", "in", "to", "is", "it", "for", "on", "with", "as", "by", "at",
        // Russian stop words
        "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то", "все", "она", "так",
        "его", "но", "да", "ты", "к", "у", "же", "вы", "за", "бы", "по", "только", "ее", "мне", "было",
        "вот", "от", "меня", "еще", "нет", "о", "из", "ему", "теперь", "когда", "даже", "ну", "вдруг",
        "ли", "если", "уже", "или", "ни", "быть", "был", "него", "до", "вас", "нибудь", "опять", "уж",
        "вам", "ведь", "там", "потом", "себя", "ничего", "ей", "может", "они", "тут", "где", "есть",
        "надо", "ней", "для", "мы", "тебя", "их", "чем", "была", "сам", "чтоб", "без", "будто", "чего",
        "раз", "тоже", "себе", "под", "будет", "ж", "тогда", "кто", "этот", "того", "потому", "этого",
        "какой", "совсем", "ним", "здесь", "этом", "один", "почти", "мой", "тем", "чтобы", "нее",
        "сейчас", "были", "куда", "зачем", "всех", "никогда", "можно", "при", "наконец", "два", "об",
        "другой", "хоть", "после", "над", "больше", "тот", "через", "эти", "нас", "про", "всего", "них",
        "какая", "много", "разве", "три", "эту", "моя", "впрочем", "хорошо", "свою", "этой", "перед",
        "иногда", "лучше", "чуть", "том", "нельзя", "такой", "им", "более", "всегда", "конечно", "всю", "между"
    )

    /**
     * Нормализация строки:
     * - приведение к нижнему регистру
     * - удаление пунктуации и спецсимволов
     * - удаление стоп-слов
     * - удаление лишних пробелов
     * - сортировка слов по алфавиту
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val lower = text.lowercase(Locale.ROOT)
        // Заменяем пунктуацию и спецсимволы на пробелы
        val cleaned = lower.replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
        val words = cleaned.split(Regex("\\s+"))
            .filter { it.isNotBlank() && !STOP_WORDS.contains(it) }
            .sorted()
        return words.joinToString(" ")
    }

    /**
     * Вычисление SHA-256 для строки
     */
    fun computeSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Главный метод генерации отпечатка книги.
     */
    fun generate(file: File): FingerprintResult {
        val path = file.absolutePath
        val lowerName = file.name.lowercase(Locale.ROOT)
        val fileSize = file.length()
        val format = when {
            lowerName.endsWith(".fb2.zip") || lowerName.endsWith(".fbz") -> "fb2.zip"
            lowerName.endsWith(".fb2") -> "fb2"
            lowerName.endsWith(".epub") -> "epub"
            lowerName.endsWith(".mobi") -> "mobi"
            lowerName.endsWith(".azw3") -> "azw3"
            lowerName.endsWith(".azw") -> "azw"
            else -> "unknown"
        }

        var rawAuthor = ""
        var rawTitle = ""
        var cleanTextHash: String? = null

        try {
            when (format) {
                "fb2" -> {
                    FileInputStream(file).use { fis ->
                        BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                            val parsed = parseFb2(bis)
                            rawAuthor = parsed.author
                            rawTitle = parsed.title
                            if (parsed.cleanText.isNotBlank()) {
                                cleanTextHash = computeSha256(parsed.cleanText)
                            }
                        }
                    }
                }
                "fb2.zip" -> {
                    FileInputStream(file).use { fis ->
                        ZipInputStream(BufferedInputStream(fis, BUFFER_SIZE)).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && entry.name.lowercase(Locale.ROOT).endsWith(".fb2")) {
                                    val parsed = parseFb2(zis)
                                    rawAuthor = parsed.author
                                    rawTitle = parsed.title
                                    if (parsed.cleanText.isNotBlank()) {
                                        cleanTextHash = computeSha256(parsed.cleanText)
                                    }
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                }
                "epub" -> {
                    val parsed = parseEpub(file)
                    rawAuthor = parsed.author
                    rawTitle = parsed.title
                    if (parsed.cleanText.isNotBlank()) {
                        cleanTextHash = computeSha256(parsed.cleanText)
                    }
                }
                "mobi", "azw", "azw3" -> {
                    // Для MOBI/AZW с DRM или без извлечения текста — пропускаем SHA256 текста
                    val parsed = parseMobiHeader(file)
                    rawAuthor = parsed.author
                    rawTitle = parsed.title
                    cleanTextHash = null
                }
                else -> {
                    rawTitle = file.nameWithoutExtension
                    cleanTextHash = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating fingerprint for file: ${file.name}", e)
        }

        // Если автор или название не определились — используем имя файла как название
        if (rawTitle.isBlank()) {
            rawTitle = file.nameWithoutExtension
        }

        val normAuthor = normalize(rawAuthor)
        val normTitle = normalize(rawTitle)

        // Формула композитного ключа:
        // Если текст извлечён: normalize(Author) + "|" + normalize(Title) + "|" + SHA256(clean_text)
        // Для MOBI/AZW или если текст не извлечён: normalize(Author) + "|" + normalize(Title) + "|" + fileSize
        val fingerprint = if (!cleanTextHash.isNullOrBlank()) {
            "$normAuthor|$normTitle|$cleanTextHash"
        } else {
            "$normAuthor|$normTitle|$fileSize"
        }

        return FingerprintResult(
            fingerprint = fingerprint,
            textHash = cleanTextHash,
            author = normAuthor,
            title = normTitle,
            fileSize = fileSize,
            format = format
        )
    }

    private data class ParsedBookData(
        val author: String,
        val title: String,
        val cleanText: String
    )

    /**
     * Парсинг FB2 с помощью XmlPullParser без загрузки всего файла в память.
     * Игнорирует теги <binary> (обложки/картинки).
     */
    private fun parseFb2(inputStream: InputStream): ParsedBookData {
        var author = ""
        var title = ""
        val textBuilder = StringBuilder()

        var currentTag = ""
        var insideTitleInfo = false
        var insideAuthor = false
        var insideBinary = false

        val firstName = StringBuilder()
        val lastName = StringBuilder()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase(Locale.ROOT)
                        if (currentTag == "title-info") insideTitleInfo = true
                        if (insideTitleInfo && currentTag == "author") insideAuthor = true
                        if (currentTag == "binary") insideBinary = true
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""
                        if (insideTitleInfo) {
                            if (insideAuthor) {
                                if (currentTag == "first-name") firstName.append(text)
                                if (currentTag == "last-name") lastName.append(text)
                            } else if (currentTag == "book-title") {
                                title += text
                            }
                        } else if (!insideBinary && text.isNotBlank()) {
                            // Собираем очищенный текст книги
                            textBuilder.append(text).append(" ")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.lowercase(Locale.ROOT)
                        if (tag == "title-info") insideTitleInfo = false
                        if (tag == "author" && insideTitleInfo) {
                            insideAuthor = false
                            val fullAuthor = "$firstName $lastName".trim()
                            if (fullAuthor.isNotBlank() && author.isBlank()) {
                                author = fullAuthor
                            }
                        }
                        if (tag == "binary") insideBinary = false
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Если возникла ошибка XML-парсинга, возвращаем то, что успели собрать
            Log.w(TAG, "FB2 XML parse warning: ${e.message}")
        }

        return ParsedBookData(
            author = author,
            title = title,
            cleanText = textBuilder.toString().trim()
        )
    }

    /**
     * Парсинг EPUB через ZipInputStream без полной распаковки
     */
    private fun parseEpub(file: File): ParsedBookData {
        var author = ""
        var title = ""
        val textBuilder = StringBuilder()

        try {
            FileInputStream(file).use { fis ->
                ZipInputStream(BufferedInputStream(fis, BUFFER_SIZE)).use { zis ->
                    var entry = zis.nextEntry
                    val htmlEntries = mutableListOf<ByteArray>()

                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val nameLower = entry.name.lowercase(Locale.ROOT)
                            if (nameLower.endsWith(".opf")) {
                                val opfData = zis.readBytes()
                                val meta = parseOpfMetadata(opfData)
                                author = meta.first
                                title = meta.second
                            } else if (nameLower.endsWith(".xhtml") || nameLower.endsWith(".html") || nameLower.endsWith(".htm")) {
                                // Собираем текст из HTML/XHTML файлов EPUB
                                val htmlText = stripHtmlTags(String(zis.readBytes(), Charsets.UTF_8))
                                if (htmlText.isNotBlank()) {
                                    textBuilder.append(htmlText).append(" ")
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EPUB file: ${file.name}", e)
        }

        return ParsedBookData(
            author = author,
            title = title,
            cleanText = textBuilder.toString().trim()
        )
    }

    /**
     * Извлечение метаданных title и creator из OPF-файла EPUB
     */
    private fun parseOpfMetadata(opfBytes: ByteArray): Pair<String, String> {
        var author = ""
        var title = ""
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(opfBytes.inputStream(), "UTF-8")

            var eventType = parser.eventType
            var currentTag = ""
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name.lowercase(Locale.ROOT)
                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""
                        if (currentTag.endsWith("creator") || currentTag == "author") {
                            if (author.isBlank()) author = text.trim()
                        } else if (currentTag.endsWith("title")) {
                            if (title.isBlank()) title = text.trim()
                        }
                    }
                    XmlPullParser.END_TAG -> currentTag = ""
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "OPF metadata parse warning: ${e.message}")
        }
        return Pair(author, title)
    }

    /**
     * Очистка HTML/XML тегов без тяжелых DOM-библиотек
     */
    private fun stripHtmlTags(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Извлечение базовой информации о заголовке для MOBI/AZW файлов
     */
    private fun parseMobiHeader(file: File): ParsedBookData {
        var title = ""
        var author = ""
        try {
            // Для MOBI пытаемся извлечь название из заглавия файла или имени
            val name = file.nameWithoutExtension
            if (name.contains("-")) {
                val parts = name.split("-", limit = 2)
                author = parts[0].trim()
                title = parts[1].trim()
            } else {
                title = name
            }
        } catch (e: Exception) {
            title = file.nameWithoutExtension
        }
        return ParsedBookData(author = author, title = title, cleanText = "")
    }
}
