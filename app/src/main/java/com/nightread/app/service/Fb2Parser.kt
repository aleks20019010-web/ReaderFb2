package com.nightread.app.service

import java.util.regex.Pattern

object Fb2Parser {
    fun extractText(xmlContent: String): String {
        return try {
            val bodyStart = xmlContent.indexOf("<body>")
            val bodyEnd = xmlContent.indexOf("</body>")
            val contentToParse = if (bodyStart != -1 && bodyEnd != -1 && bodyEnd > bodyStart) {
                xmlContent.substring(bodyStart, bodyEnd + "</body>".length)
            } else {
                xmlContent
            }

            var text = contentToParse
                .replace(Regex("<empty-line[^>]*>"), "\n\n")
                .replace(Regex("<p[^>]*>"), "\n    ")
                .replace(Regex("</p>"), "")
                .replace(Regex("<v[^>]*>"), "\n")
                .replace(Regex("</v>"), "")
                .replace(Regex("<title[^>]*>"), "\n\n")
                .replace(Regex("</title>"), "\n\n")
                .replace(Regex("<subtitle[^>]*>"), "\n\n")
                .replace(Regex("</subtitle>"), "\n\n")

            // Strip remaining tags
            text = text.replace(Regex("<[^>]+>"), "")

            // Decode entities
            text = text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")

            // Clean up multiple newlines
            text.replace(Regex("\n{3,}"), "\n\n").trim()
        } catch (e: Exception) {
            xmlContent
        }
    }
}
