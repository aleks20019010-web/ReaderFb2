package com.nightread.app.service

import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object HtmlParser : BookParser {
    private const val TAG = "HtmlParser"

    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        try {
            if (!file.exists() || !file.canRead()) {
                return BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
            }
            val rawText = file.readText(StandardCharsets.UTF_8)
            return parseString(rawText, file.nameWithoutExtension.ifBlank { defaultTitle })
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing HTML/HTM file: ${file.absolutePath}", e)
            return BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    fun parseString(htmlContent: String, fallbackTitle: String): BookParser.ParsedBook {
        var title = fallbackTitle
        var author = "Неизвестен"

        // Extract title: <title>Text</title> (case-insensitive, optional whitespace, dotall)
        val titleMatcher = Pattern.compile("<title>\\s*(.*?)\\s*</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(htmlContent)
        if (titleMatcher.find()) {
            val matchedTitle = titleMatcher.group(1)?.trim()
            if (!matchedTitle.isNullOrBlank()) {
                title = matchedTitle
            }
        }

        // Extract author from meta: <meta name="author" content="Text" /> or <meta content="Text" name="author" />
        val authorMatcher1 = Pattern.compile("<meta\\s+name=\"author\"\\s+content=\"(.*?)\"", Pattern.CASE_INSENSITIVE).matcher(htmlContent)
        if (authorMatcher1.find()) {
            val matchedAuthor = authorMatcher1.group(1)?.trim()
            if (!matchedAuthor.isNullOrBlank()) {
                author = matchedAuthor
            }
        } else {
            val authorMatcher2 = Pattern.compile("<meta\\s+content=\"(.*?)\"\\s+name=\"author\"", Pattern.CASE_INSENSITIVE).matcher(htmlContent)
            if (authorMatcher2.find()) {
                val matchedAuthor = authorMatcher2.group(1)?.trim()
                if (!matchedAuthor.isNullOrBlank()) {
                    author = matchedAuthor
                }
            }
        }

        return BookParser.ParsedBook(
            title = title,
            author = author,
            content = htmlContent
        )
    }
}
