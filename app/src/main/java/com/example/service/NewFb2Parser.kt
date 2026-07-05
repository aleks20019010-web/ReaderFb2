package com.example.service

data class BookMetadata(
    val title: String,
    val author: String,
    val content: String,
    val series: String?,
    val seriesIndex: Int?,
    val language: String?
)

object NewFb2Parser {
    fun parse(fb2Content: String, defaultTitle: String): BookMetadata {
        return try {
            var title = ""
            var firstName = ""
            var lastName = ""
            var series: String? = null
            var seriesIndex: Int? = null
            var lang: String? = null

            // Use regex for extremely fast and robust parsing of metadata (avoids XML parsing exceptions on huge/malformed files)
            val titleMatch = Regex("<book-title>\\s*([^<]+?)\\s*</book-title>", RegexOption.IGNORE_CASE).find(fb2Content)
            if (titleMatch != null) {
                title = titleMatch.groupValues[1].trim()
            }

            val authorMatch = Regex("<author>\\s*(.*?)\\s*</author>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(fb2Content)
            if (authorMatch != null) {
                val authorBlock = authorMatch.groupValues[1]
                val fnMatch = Regex("<first-name>\\s*([^<]+?)\\s*</first-name>", RegexOption.IGNORE_CASE).find(authorBlock)
                val lnMatch = Regex("<last-name>\\s*([^<]+?)\\s*</last-name>", RegexOption.IGNORE_CASE).find(authorBlock)
                if (fnMatch != null) firstName = fnMatch.groupValues[1].trim()
                if (lnMatch != null) lastName = lnMatch.groupValues[1].trim()
            }

            val seriesMatch = Regex("<sequence[^>]*?name\\s*=\\s*\"([^\"]+?)\"", RegexOption.IGNORE_CASE).find(fb2Content)
            if (seriesMatch != null) {
                series = seriesMatch.groupValues[1].trim()
                val indexMatch = Regex("number\\s*=\\s*\"(\\d+?)\"", RegexOption.IGNORE_CASE).find(seriesMatch.value)
                if (indexMatch != null) {
                    seriesIndex = indexMatch.groupValues[1].toIntOrNull()
                }
            }

            val langMatch = Regex("<lang>\\s*([^<]+?)\\s*</lang>", RegexOption.IGNORE_CASE).find(fb2Content)
            if (langMatch != null) {
                lang = langMatch.groupValues[1].trim()
            }

            val finalTitle = title.ifBlank { defaultTitle }
            val authorList = listOf(firstName, lastName).filter { it.isNotBlank() }
            val finalAuthor = if (authorList.isNotEmpty()) authorList.joinToString(" ") else "Unknown Author"

            BookMetadata(finalTitle, finalAuthor, fb2Content, series, seriesIndex, lang)
        } catch (e: Exception) {
            BookMetadata(defaultTitle, "Unknown Author", fb2Content, null, null, null)
        }
    }
}
