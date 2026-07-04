package com.example.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

object Fb2Parser {
    data class ParsedFb2(
        val title: String,
        val author: String,
        val content: String,
        val series: String?,
        val seriesIndex: Int?,
        val language: String?
    )

    fun parse(rawText: String, fallbackName: String): ParsedFb2 {
        val titleRegex = "<book-title>(.*?)</book-title>".toRegex(RegexOption.IGNORE_CASE)
        val authorFirstRegex = "<first-name>(.*?)</first-name>".toRegex(RegexOption.IGNORE_CASE)
        val authorLastRegex = "<last-name>(.*?)</last-name>".toRegex(RegexOption.IGNORE_CASE)
        val langRegex = "<lang>(.*?)</lang>".toRegex(RegexOption.IGNORE_CASE)
        
        val titleMatch = titleRegex.find(rawText)
        val title = titleMatch?.groupValues?.get(1)?.trim() ?: fallbackName
        
        val first = authorFirstRegex.find(rawText)?.groupValues?.get(1)?.trim() ?: ""
        val last = authorLastRegex.find(rawText)?.groupValues?.get(1)?.trim() ?: ""
        val author = if (first.isNotEmpty() || last.isNotEmpty()) "$first $last".trim() else "Неизвестен"
        
        val langMatch = langRegex.find(rawText)
        val language = langMatch?.groupValues?.get(1)?.trim() ?: "ru"
        
        // Robust sequence parsing
        // Example: <sequence name="SeriesName" number="1"/> or <sequence name="SeriesName"/>
        val sequenceMatch = """<sequence\s+([^>]+)/?>""".toRegex(RegexOption.IGNORE_CASE).find(rawText)
        var parsedSeries: String? = null
        var parsedSeriesIndex: Int? = null
        if (sequenceMatch != null) {
            val attrs = sequenceMatch.groupValues[1]
            val nameMatch = """\bname\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(attrs)
            val numberMatch = """\bnumber\s*=\s*["'](\d+)["']""".toRegex(RegexOption.IGNORE_CASE).find(attrs)
            parsedSeries = nameMatch?.groupValues?.get(1)?.trim()
            parsedSeriesIndex = numberMatch?.groupValues?.get(1)?.toIntOrNull()
        }

        // 1. Extract the actual <body> of the FB2 document to avoid duplicate description or metadata
        val bodyStart = rawText.indexOf("<body>", ignoreCase = true)
        val bodyEnd = rawText.lastIndexOf("</body>", ignoreCase = true)
        val bodyContent = if (bodyStart != -1 && bodyEnd > bodyStart) {
            rawText.substring(bodyStart + 6, bodyEnd)
        } else {
            rawText
        }

        // 2. Clear out any binary cover or base64 data to keep content lightweight
        var processedText = bodyContent.replace("""<binary[^>]*>[\s\S]*?</binary>""".toRegex(RegexOption.IGNORE_CASE), "")

        // 3. Mark titles of chapters (<title>) with form-feed (\u000C) so they start on a fresh page
        processedText = processedText.replace("""<title[^>]*>""".toRegex(RegexOption.IGNORE_CASE), "\u000C\n")
        processedText = processedText.replace("""</title>""".toRegex(RegexOption.IGNORE_CASE), "\n\n")

        // 4. Retain paragraphs (<p>) using standard Russian indentation (красная строка) and single line break
        processedText = processedText.replace("""<p[^>]*>""".toRegex(RegexOption.IGNORE_CASE), "    ")
        processedText = processedText.replace("""</p>""".toRegex(RegexOption.IGNORE_CASE), "\n")

        // 5. Remove any other XML markup
        processedText = processedText.replace("<[^>]*>".toRegex(), "")

        // 6. Format whitespace and consecutive empty lines
        processedText = processedText.replace("\r", "")
        processedText = processedText.replace("\n{3,}".toRegex(), "\n\n")
        processedText = processedText.replace("\u000C\\s*\u000C".toRegex(), "\u000C")
        if (processedText.startsWith("\u000C")) {
            processedText = processedText.substring(1)
        }

        // Clean double spaces within each line while keeping indentation at the start
        val cleanContent = processedText.lines().joinToString("\n") { line ->
            if (line.trim().isEmpty()) ""
            else {
                val indent = line.takeWhile { it.isWhitespace() }
                val trimmed = line.substring(indent.length).replace("\\s+".toRegex(), " ")
                indent + trimmed
            }
        }.trim()
            
        return ParsedFb2(title, author, cleanContent, parsedSeries, parsedSeriesIndex, language)
    }

    /**
     * Extracts cover image bitmap from the FB2 text by scanning binary blocks.
     * Checks for <coverpage> image references or searches binary tags with cover ID.
     */
    fun extractCover(rawText: String): Bitmap? {
        try {
            // First find any <coverpage> image href to know exactly which binary ID to look for
            // Example: <coverpage><image linetype="image/jpeg" href="#cover.jpg"/></coverpage>
            val coverpageMatch = """<coverpage>[\s\S]*?<image[^>]+href\s*=\s*["']#([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(rawText)
            val targetedId = coverpageMatch?.groupValues?.get(1)?.trim()?.lowercase()

            val binaryBlockRegex = """<binary([^>]*)>([\s\S]*?)</binary>""".toRegex(RegexOption.IGNORE_CASE)
            val binaryDataMap = mutableMapOf<String, String>()
            for (match in binaryBlockRegex.findAll(rawText)) {
                val attrs = match.groupValues[1]
                val base64 = match.groupValues[2]
                val idMatch = """\bid\s*=\s*["']?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE).find(attrs)
                val id = idMatch?.groupValues?.get(1)
                if (id != null) {
                    binaryDataMap[id.lowercase()] = base64
                }
            }

            var base64Data: String? = null
            if (targetedId != null) {
                base64Data = binaryDataMap[targetedId]
            }
            if (base64Data == null) {
                base64Data = binaryDataMap["cover.jpg"] ?: binaryDataMap["cover"] ?: binaryDataMap["cover.png"]
            }
            if (base64Data == null) {
                val key = binaryDataMap.keys.find { it.contains("cover") }
                if (key != null) {
                    base64Data = binaryDataMap[key]
                }
            }
            if (base64Data == null && binaryDataMap.isNotEmpty()) {
                base64Data = binaryDataMap.values.first()
            }

            if (base64Data != null) {
                val cleanBase64 = base64Data.replace("\\s".toRegex(), "")
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            android.util.Log.e("Fb2Parser", "Error extracting cover from FB2", e)
        }
        return null
    }
}
