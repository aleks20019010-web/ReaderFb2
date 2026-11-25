package com.nightread.app.service

import android.util.Log
import com.nightread.app.service.BookParser.ParsedBook
import org.xmlpull.v1.XmlPullParser
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
        return parseFullFb2(file, defaultTitle)
    }

    fun parseFullFb2(file: File, defaultTitle: String): ParsedBook {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.absolutePath}")
            return createFallbackBook(defaultTitle)
        }

        return try {
            file.inputStream().buffered().use { stream ->
                parseFullFb2Stream(stream, defaultTitle)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse full FB2 file: ${file.name}", e)
            createFallbackBook(defaultTitle)
        }
    }

    /**
     * Parses full FB2 book text from any InputStream (including ZipInputStream),
     * auto-detecting charset, stopping before <binary> to avoid OOM, sanitizing
     * broken HTML entities, and falling back to tag-stripping if XML is malformed.
     */
    fun parseFullFb2Stream(stream: InputStream, defaultTitle: String): ParsedBook {
        return try {
            val headerBytes = ByteArray(2048)
            var headerRead = 0
            while (headerRead < headerBytes.size) {
                val r = stream.read(headerBytes, headerRead, headerBytes.size - headerRead)
                if (r <= 0) break
                headerRead += r
            }
            if (headerRead <= 0) return createFallbackBook(defaultTitle)

            val actualHeader = if (headerRead == headerBytes.size) headerBytes else headerBytes.copyOf(headerRead)
            val detectedCharset = detectCharset(actualHeader, actualHeader.size)
            val combined = java.io.SequenceInputStream(ByteArrayInputStream(actualHeader), stream)
            parseFullFb2Stream(combined, detectedCharset, defaultTitle)
        } catch (e: Throwable) {
            Log.e(TAG, "parseFullFb2Stream(auto) failed for $defaultTitle", e)
            createFallbackBook(defaultTitle)
        }
    }

    fun parseFullFb2Stream(stream: InputStream, charset: Charset, defaultTitle: String): ParsedBook {
        val rawXml = readFb2TextBeforeBinary(stream, charset)
        if (rawXml.isBlank()) {
            return createFallbackBook(defaultTitle)
        }

        val sanitizedXml = sanitizeFb2XmlForPullParser(rawXml)

        var title = ""
        var firstName = ""
        var middleName = ""
        var lastName = ""
        val annotationBuilder = StringBuilder()
        val notes = mutableMapOf<String, String>()
        val contentBuilder = StringBuilder(100_000)

        var inDescription = false
        var inAnnotation = false
        var inBody = false
        var isNotesBody = false
        var inTitle = false
        var currentTag = ""
        val currentSectionTitle = StringBuilder()
        val currentParagraph = StringBuilder()
        val styleTagStack = ArrayDeque<String?>()
        var currentNoteId = ""
        val currentNoteText = StringBuilder()
        var parserFailedEarly = false

        try {
            val parser = parserFactory.newPullParser()
            parser.setInput(java.io.StringReader(sanitizedXml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name?.lowercase() ?: ""
                        when (currentTag) {
                            "description" -> inDescription = true
                            "annotation" -> if (inDescription) inAnnotation = true
                            "body" -> {
                                inBody = true
                                val bodyName = parser.getAttributeValue(null, "name")?.lowercase() ?: ""
                                isNotesBody = bodyName.contains("notes") || bodyName.contains("comments")
                            }
                            "title" -> {
                                if (inBody) {
                                    inTitle = true
                                    currentSectionTitle.clear()
                                }
                            }
                            "subtitle" -> {
                                if (inBody && !isNotesBody) {
                                    currentParagraph.clear()
                                    styleTagStack.clear()
                                }
                            }
                            "section" -> {
                                val id = parser.getAttributeValue(null, "id") ?: ""
                                if (isNotesBody && id.isNotEmpty()) {
                                    currentNoteId = id
                                    currentNoteText.clear()
                                }
                            }
                            "p", "v" -> {
                                if (!inTitle) {
                                    currentParagraph.clear()
                                    styleTagStack.clear()
                                }
                            }
                            "emphasis", "em", "i" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("<i>")
                            }
                            "strong", "b" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("<b>")
                            }
                            "u" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("<u>")
                            }
                            "strikethrough", "s", "del" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("<s>")
                            }
                            "sup" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("<sup>")
                            }
                            "sub" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("<sub>")
                            }
                            "code" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("<code>")
                            }
                            "style" -> {
                                if (inBody && !isNotesBody && !inTitle) {
                                    val styleName = parser.getAttributeValue(null, "name")?.lowercase().orEmpty()
                                    val mapped = when {
                                        styleName.contains("underline") || styleName == "u" -> "u"
                                        styleName.contains("italic") || styleName.contains("emphasis") -> "i"
                                        styleName.contains("bold") || styleName.contains("strong") -> "b"
                                        styleName.contains("strike") -> "s"
                                        else -> null
                                    }
                                    styleTagStack.addLast(mapped)
                                    if (mapped != null) currentParagraph.append("<$mapped>")
                                }
                            }
                            "empty-line" -> {
                                if (inBody && !isNotesBody) {
                                    contentBuilder.append("\n")
                                }
                            }
                            "binary" -> break
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""
                        if (inDescription) {
                            when {
                                inAnnotation -> annotationBuilder.append(text)
                                currentTag == "book-title" && title.isEmpty() -> title = text.trim()
                                currentTag == "first-name" && firstName.isEmpty() -> firstName = text.trim()
                                currentTag == "middle-name" && middleName.isEmpty() -> middleName = text.trim()
                                currentTag == "last-name" && lastName.isEmpty() -> lastName = text.trim()
                            }
                        } else if (inBody) {
                            if (isNotesBody) {
                                if (currentNoteId.isNotEmpty()) {
                                    currentNoteText.append(text)
                                }
                            } else if (inTitle) {
                                currentSectionTitle.append(text)
                            } else {
                                currentParagraph.append(
                                    text.replace("&", "&amp;")
                                        .replace("<", "&lt;")
                                        .replace(">", "&gt;")
                                )
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val endTag = parser.name?.lowercase() ?: ""
                        when (endTag) {
                            "description" -> {
                                inDescription = false
                                inAnnotation = false
                            }
                            "annotation" -> inAnnotation = false
                            "body" -> {
                                inBody = false
                                isNotesBody = false
                            }
                            "title" -> {
                                if (inBody && inTitle) {
                                    inTitle = false
                                    val t = currentSectionTitle.toString()
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                    if (t.isNotEmpty()) {
                                        contentBuilder.append("[CHAPTER]").append(t).append("[/CHAPTER]\n\n")
                                    }
                                }
                            }
                            "subtitle" -> {
                                if (inBody && !isNotesBody) {
                                    val s = currentParagraph.toString().trim()
                                    if (s.isNotEmpty()) {
                                        contentBuilder.append(s).append("\n\n")
                                    }
                                    currentParagraph.clear()
                                    styleTagStack.clear()
                                }
                            }
                            "emphasis", "em", "i" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("</i>")
                            }
                            "strong", "b" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("</b>")
                            }
                            "u" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("</u>")
                            }
                            "strikethrough", "s", "del" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("</s>")
                            }
                            "sup" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("</sup>")
                            }
                            "sub" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("</sub>")
                            }
                            "code" -> {
                                if (inBody && !isNotesBody && !inTitle) currentParagraph.append("</code>")
                            }
                            "style" -> {
                                if (inBody && !isNotesBody && !inTitle && styleTagStack.isNotEmpty()) {
                                    styleTagStack.removeLast()?.let { currentParagraph.append("</$it>") }
                                }
                            }
                            "p", "v" -> {
                                if (inDescription && inAnnotation) {
                                    annotationBuilder.append("\n")
                                } else if (inBody) {
                                    if (isNotesBody && currentNoteId.isNotEmpty()) {
                                        currentNoteText.append(" ")
                                    } else if (inTitle) {
                                        currentSectionTitle.append(" ")
                                    } else {
                                        val p = currentParagraph.toString().trim()
                                        if (p.isNotEmpty()) {
                                            contentBuilder.append(p).append("\n\n")
                                        }
                                        currentParagraph.clear()
                                        styleTagStack.clear()
                                    }
                                }
                            }
                            "section" -> {
                                if (isNotesBody && currentNoteId.isNotEmpty()) {
                                    notes[currentNoteId] = currentNoteText.toString().trim()
                                    currentNoteId = ""
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Throwable) {
            parserFailedEarly = true
            Log.w(TAG, "XmlPullParser stopped early during full FB2 parse: ${e.message}")
        }

        var finalContent = contentBuilder.toString().trim()
        val bodyStartIdx = rawXml.indexOf("<body", ignoreCase = true)
        val estimatedBodySize = if (bodyStartIdx >= 0) (rawXml.length - bodyStartIdx) else rawXml.length
        if (parserFailedEarly || (estimatedBodySize > 2000 && finalContent.length < estimatedBodySize / 8)) {
            val fallbackText = extractBodyTextFallback(rawXml)
            if (fallbackText.length > finalContent.length) {
                finalContent = fallbackText
            }
        }

        if (title.isBlank()) {
            TITLE_REGEX.find(rawXml.take(65536))?.groupValues?.get(1)?.let {
                title = cleanXmlText(it)
            }
        }

        val authorList = listOf(firstName, middleName, lastName).filter { it.isNotBlank() }
        val author = if (authorList.isNotEmpty()) authorList.joinToString(" ") else "Unknown Author"

        return ParsedBook(
            title = title.ifBlank { defaultTitle },
            author = author,
            content = finalContent,
            notes = notes,
            annotation = annotationBuilder.toString().trim().ifBlank { null }
        )
    }

    private fun readFb2TextBeforeBinary(stream: InputStream, charset: Charset): String {
        val maxChars = 16 * 1024 * 1024 // 16M chars cap for safety
        val sb = StringBuilder(65536)
        val reader = java.io.BufferedReader(java.io.InputStreamReader(stream, charset), 32768)
        val buf = CharArray(16384)
        while (sb.length < maxChars) {
            val n = reader.read(buf)
            if (n <= 0) break
            val prevLen = sb.length
            sb.append(buf, 0, n)
            val searchFrom = (prevLen - 16).coerceAtLeast(0)
            val binIdx = sb.indexOf("<binary", searchFrom, ignoreCase = true)
            if (binIdx != -1) {
                sb.setLength(binIdx)
                break
            }
        }
        return sb.toString()
    }

    private fun sanitizeFb2XmlForPullParser(xml: String): String {
        val sb = StringBuilder(xml.length)
        var i = 0
        val len = xml.length
        while (i < len) {
            val c = xml[i]
            if (c.code < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                i++
                continue
            }
            if (c == '&') {
                val semi = xml.indexOf(';', i + 1)
                if (semi != -1 && semi - i <= 12) {
                    val entity = xml.substring(i + 1, semi)
                    when (entity.lowercase()) {
                        "lt", "gt", "amp", "quot", "apos" -> {
                            sb.append(xml, i, semi + 1)
                            i = semi + 1
                            continue
                        }
                        "nbsp" -> { sb.append(' '); i = semi + 1; continue }
                        "mdash" -> { sb.append('—'); i = semi + 1; continue }
                        "ndash" -> { sb.append('–'); i = semi + 1; continue }
                        "laquo" -> { sb.append('«'); i = semi + 1; continue }
                        "raquo" -> { sb.append('»'); i = semi + 1; continue }
                        "hellip" -> { sb.append('…'); i = semi + 1; continue }
                        "copy" -> { sb.append('©'); i = semi + 1; continue }
                        "shy" -> { i = semi + 1; continue }
                        "deg" -> { sb.append('°'); i = semi + 1; continue }
                        "times" -> { sb.append('×'); i = semi + 1; continue }
                        else -> {
                            if (entity.startsWith("#")) {
                                sb.append(xml, i, semi + 1)
                                i = semi + 1
                                continue
                            }
                            sb.append("&amp;")
                            i++
                            continue
                        }
                    }
                } else {
                    sb.append("&amp;")
                    i++
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun extractBodyTextFallback(rawXml: String): String {
        val bodyStart = rawXml.indexOf("<body", ignoreCase = true)
        if (bodyStart == -1) return ""
        val bodyTagEnd = rawXml.indexOf('>', bodyStart)
        if (bodyTagEnd == -1) return ""

        var bodyEnd = rawXml.indexOf("</body>", bodyTagEnd, ignoreCase = true)
        if (bodyEnd == -1) bodyEnd = rawXml.length
        val bodyChunk = rawXml.substring(bodyTagEnd + 1, bodyEnd)

        val withChapters = bodyChunk
            .replace(Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) { m ->
                val inner = cleanXmlText(m.groupValues[1])
                if (inner.isNotBlank()) "\n[CHAPTER]$inner[/CHAPTER]\n\n" else ""
            }
            .replace(Regex("</(?:p|v|subtitle|section|poem|stanza|epigraph|cite)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<empty-line\\s*/?>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<emphasis\\b[^>]*>", RegexOption.IGNORE_CASE), "<i>")
            .replace(Regex("</emphasis>", RegexOption.IGNORE_CASE), "</i>")
            .replace(Regex("<strong\\b[^>]*>", RegexOption.IGNORE_CASE), "<b>")
            .replace(Regex("</strong>", RegexOption.IGNORE_CASE), "</b>")
            .replace(Regex("<strikethrough\\b[^>]*>", RegexOption.IGNORE_CASE), "<s>")
            .replace(Regex("</strikethrough>", RegexOption.IGNORE_CASE), "</s>")
            .replace(Regex("<(u|b|i|s|sup|sub|code)\\b[^>]*>", RegexOption.IGNORE_CASE), "<$1>")
            .replace(Regex("<(?!/?(?:b|i|u|s|sup|sub|code)\\b)[^>]+>", RegexOption.IGNORE_CASE), "")

        return decodeCommonEntities(withChapters)
            .replace(Regex("[ \\t\\r]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun cleanXmlText(text: String): String {
        return decodeXmlEntities(text.replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeCommonEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&hellip;", "…")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
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