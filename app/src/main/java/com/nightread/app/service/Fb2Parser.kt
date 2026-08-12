package com.nightread.app.service

import android.util.Log
import com.nightread.app.service.BookParser.ParsedBook
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Метаданные книги, извлечённые из FB2 файла.
 */
data class BookMetadata(
    val title: String,
    val author: String,
    val content: String,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val language: String? = null,
    val annotation: String? = null
)

/**
 * Гибридный парсер FB2 файлов.
 * 
 * Стратегия:
 * 1. Пробуем XmlPullParser (корректная кодировка, структура XML)
 * 2. При ошибке XML валидации падаем на Regex парсер (устойчивость к повреждённым файлам)
 * 3. Результат объединяет скорость, точность и отказоустойчивость
 */
object Fb2Parser : BookParser {

    private const val TAG = "Fb2Parser"
    private const val HEADER_SIZE = 256 * 1024 // 256KB для считывания заголовка
    private const val MAX_ANNOTATION_SIZE = 100 * 1024 // 100KB для аннотации

    // Регулярные выражения для fallback парсинга
    private val TITLE_REGEX = Regex(
        "<book-title\\b[^>]*>\\s*(.*?)\\s*</book-title>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val AUTHOR_REGEX = Regex(
        "<author\\b[^>]*>\\s*(.*?)\\s*</author>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val FIRST_NAME_REGEX = Regex(
        "<first-name\\b[^>]*>\\s*(.*?)\\s*</first-name>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val MIDDLE_NAME_REGEX = Regex(
        "<middle-name\\b[^>]*>\\s*(.*?)\\s*</middle-name>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val LAST_NAME_REGEX = Regex(
        "<last-name\\b[^>]*>\\s*(.*?)\\s*</last-name>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val SEQUENCE_REGEX = Regex(
        "<sequence\\b[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val LANG_REGEX = Regex(
        "<lang\\b[^>]*>\\s*([^<]+?)\\s*</lang>",
        RegexOption.IGNORE_CASE
    )
    private val ANNOTATION_REGEX = Regex(
        "<annotation\\b[^>]*>\\s*(.*?)\\s*</annotation>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val ENCODING_REGEX = Regex(
        "encoding\\s*=\\s*[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE
    )

    // Фабрика XmlPullParser
    private val parserFactory by lazy {
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
    }

    override fun parse(file: File, defaultTitle: String): ParsedBook {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.absolutePath}")
            return createFallbackBook(defaultTitle)
        }

        return try {
            file.inputStream().use { inputStream ->
                val metadata = parse(inputStream, defaultTitle)
                ParsedBook(
                    title = metadata.title,
                    author = metadata.author,
                    content = metadata.content,
                    notes = emptyMap(),
                    annotation = metadata.annotation
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse FB2 file: ${file.name}", e)
            createFallbackBook(defaultTitle)
        }
    }

    fun parse(inputStream: InputStream, defaultTitle: String): BookMetadata {
        return try {
            val buffer = ByteArray(HEADER_SIZE)
            var totalRead = 0
            while (totalRead < HEADER_SIZE) {
                val read = inputStream.read(buffer, totalRead, HEADER_SIZE - totalRead)
                if (read <= 0) break
                totalRead += read
            }

            if (totalRead <= 0) {
                return createFallbackMetadata(defaultTitle)
            }

            val headerBytes = if (totalRead == HEADER_SIZE) buffer else buffer.copyOf(totalRead)

            // 1. Пробуем XmlPullParser
            try {
                ByteArrayInputStream(headerBytes).use { stream ->
                    parseWithXmlPullParser(stream, defaultTitle)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "XmlPullParser failed, falling back to Regex for header parsing", e)
                parseWithRegex(headerBytes, headerBytes.size, defaultTitle)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse FB2 InputStream", e)
            createFallbackMetadata(defaultTitle)
        }
    }

    fun parse(fb2Content: String, defaultTitle: String): BookMetadata {
        if (fb2Content.isBlank()) return createFallbackMetadata(defaultTitle)
        val stream = fb2Content.byteInputStream(Charsets.UTF_8)
        val metadata = parse(stream, defaultTitle)
        return metadata.copy(content = fb2Content)
    }

    fun extractAnnotation(fb2Content: String): String? {
        if (fb2Content.isBlank()) return null
        val metadata = parse(fb2Content, "")
        return metadata.annotation
    }

    /**
     * Парсинг через XmlPullParser (для корректных XML файлов).
     */
    private fun parseWithXmlPullParser(inputStream: InputStream, defaultTitle: String): BookMetadata {
        val parser = parserFactory.newPullParser()
        parser.setInput(inputStream, null)

        var title = ""
        var firstName = ""
        var middleName = ""
        var lastName = ""
        var series: String? = null
        var seriesIndex: Int? = null
        var language: String? = null
        var annotation: String? = null
        var parsingDescription = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name

                    when {
                        tagName == "description" -> {
                            parsingDescription = true
                        }
                        parsingDescription -> {
                            when {
                                tagName == "book-title" && title.isEmpty() -> {
                                    title = parser.nextText().trim()
                                }
                                tagName == "first-name" && firstName.isEmpty() -> {
                                    firstName = parser.nextText().trim()
                                }
                                tagName == "middle-name" && middleName.isEmpty() -> {
                                    middleName = parser.nextText().trim()
                                }
                                tagName == "last-name" && lastName.isEmpty() -> {
                                    lastName = parser.nextText().trim()
                                }
                                tagName == "sequence" -> {
                                    series = parser.getAttributeValue(null, "name")?.trim()
                                    parser.getAttributeValue(null, "number")?.toIntOrNull()?.let {
                                        seriesIndex = it
                                    }
                                }
                                tagName == "lang" && language == null -> {
                                    language = parser.nextText().trim()
                                }
                                tagName == "annotation" && annotation == null -> {
                                    annotation = readAnnotationWithXmlParser(parser)
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "description" && parsingDescription) {
                        break
                    }
                }
            }

            eventType = parser.next()
        }

        return buildMetadata(title, firstName, middleName, lastName, series, seriesIndex, language, annotation, defaultTitle)
    }

    /**
     * Читает аннотацию через XmlPullParser с ограничением размера.
     */
    private fun readAnnotationWithXmlParser(parser: XmlPullParser): String? {
        val annotationText = StringBuilder()
        var depth = 1

        try {
            var eventType = parser.next()
            while (eventType != XmlPullParser.END_DOCUMENT && depth > 0) {
                val tagName = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                    }
                    XmlPullParser.TEXT -> {
                        val txt = parser.text
                        if (txt != null) {
                            annotationText.append(txt)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                        if (tagName == "p" || tagName == "v") {
                            annotationText.append("\n")
                        }
                        if ((tagName == "annotation" || tagName == "description") && depth <= 0) {
                            break
                        }
                    }
                }
                if (depth <= 0) break
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading annotation with XmlPullParser", e)
            return null
        }

        return annotationText.toString()
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n\\s*\\n"), "\n\n")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * Fallback парсинг через Regex (для повреждённых XML файлов).
     */
    private fun parseWithRegex(bytes: ByteArray, length: Int, defaultTitle: String): BookMetadata {
        try {
            if (length <= 0) return createFallbackMetadata(defaultTitle)

            val charset = detectCharset(bytes, length)
            val content = String(bytes, 0, length, charset)

            var title = ""
            var firstName = ""
            var middleName = ""
            var lastName = ""
            var series: String? = null
            var seriesIndex: Int? = null
            var language: String? = null
            var annotation: String? = null

            TITLE_REGEX.find(content)?.let {
                title = decodeXmlEntities(it.groupValues[1].trim())
            }

            AUTHOR_REGEX.find(content)?.let { authorMatch ->
                val authorBlock = authorMatch.groupValues[1]
                FIRST_NAME_REGEX.find(authorBlock)?.let {
                    firstName = decodeXmlEntities(it.groupValues[1].trim())
                }
                MIDDLE_NAME_REGEX.find(authorBlock)?.let {
                    middleName = decodeXmlEntities(it.groupValues[1].trim())
                }
                LAST_NAME_REGEX.find(authorBlock)?.let {
                    lastName = decodeXmlEntities(it.groupValues[1].trim())
                }
            }

            SEQUENCE_REGEX.find(content)?.let { seqMatch ->
                val seqContent = seqMatch.value
                val nameMatch = Regex("name\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(seqContent)
                series = nameMatch?.groupValues?.get(1)?.trim()?.let { decodeXmlEntities(it) }

                val indexMatch = Regex("number\\s*=\\s*[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE).find(seqContent)
                seriesIndex = indexMatch?.groupValues?.get(1)?.toIntOrNull()
            }

            LANG_REGEX.find(content)?.let {
                language = decodeXmlEntities(it.groupValues[1].trim())
            }

            ANNOTATION_REGEX.find(content)?.let { annMatch ->
                annotation = cleanAnnotationWithRegex(annMatch.groupValues[1])
            }

            return buildMetadata(title, firstName, middleName, lastName, series, seriesIndex, language, annotation, defaultTitle)
        } catch (e: Exception) {
            Log.e(TAG, "Regex fallback parsing failed", e)
            return createFallbackMetadata(defaultTitle)
        }
    }

    /**
     * Очистка аннотации через Regex (удаление тегов и декодирование XML сущностей).
     */
    private fun cleanAnnotationWithRegex(text: String): String? {
        if (text.isBlank()) return null

        try {
            var result = text
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            result = decodeXmlEntities(result)

            return result.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning annotation with Regex", e)
            return text.take(500)
        }
    }

    /**
     * Декодирует XML сущности (именованные, десятичные и шестнадцатеричные).
     */
    private fun decodeXmlEntities(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        val namedEntities = mapOf(
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&apos;" to "'",
            "&nbsp;" to " ",
            "&mdash;" to "—",
            "&ndash;" to "–",
            "&ldquo;" to "\"",
            "&rdquo;" to "\"",
            "&lsquo;" to "'",
            "&rsquo;" to "'",
            "&hellip;" to "…"
        )
        for ((entity, char) in namedEntities) {
            if (result.contains(entity)) {
                result = result.replace(entity, char)
            }
        }

        if (result.contains("&#")) {
            result = result.replace(Regex("&#(\\d+);")) {
                val code = it.groupValues[1].toIntOrNull()
                if (code != null && code in 32..0x10FFFF) {
                    String(Character.toChars(code))
                } else {
                    it.value
                }
            }

            result = result.replace(Regex("&#x([0-9A-Fa-f]+);")) {
                val code = it.groupValues[1].toIntOrNull(16)
                if (code != null && code in 32..0x10FFFF) {
                    String(Character.toChars(code))
                } else {
                    it.value
                }
            }
        }

        return result
    }

    /**
     * Определяет кодировку из XML-декларации.
     */
    private fun detectCharset(bytes: ByteArray, length: Int): Charset {
        try {
            val prefix = String(bytes, 0, minOf(200, length), Charsets.UTF_8)
            ENCODING_REGEX.find(prefix)?.let {
                val encodingName = it.groupValues[1]
                return try {
                    Charset.forName(encodingName)
                } catch (e: Exception) {
                    Log.w(TAG, "Unsupported encoding: $encodingName, falling back to UTF-8")
                    Charsets.UTF_8
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect encoding, falling back to UTF-8")
        }
        return Charsets.UTF_8
    }

    private fun buildMetadata(
        title: String,
        firstName: String,
        middleName: String = "",
        lastName: String,
        series: String?,
        seriesIndex: Int?,
        language: String?,
        annotation: String?,
        defaultTitle: String
    ): BookMetadata {
        val finalTitle = title.ifBlank { defaultTitle }
        val authorList = listOf(firstName, middleName, lastName).filter { it.isNotBlank() }
        val finalAuthor = if (authorList.isNotEmpty()) {
            authorList.joinToString(" ")
        } else {
            "Unknown Author"
        }

        return BookMetadata(
            title = finalTitle,
            author = finalAuthor,
            content = "",
            series = series,
            seriesIndex = seriesIndex,
            language = language,
            annotation = annotation
        )
    }

    private fun createFallbackMetadata(defaultTitle: String): BookMetadata {
        return BookMetadata(
            title = defaultTitle,
            author = "Unknown Author",
            content = "",
            series = null,
            seriesIndex = null,
            language = null,
            annotation = null
        )
    }

    private fun createFallbackBook(defaultTitle: String): ParsedBook {
        return ParsedBook(
            title = defaultTitle,
            author = "Unknown Author",
            content = "",
            notes = emptyMap(),
            annotation = null
        )
    }
}
