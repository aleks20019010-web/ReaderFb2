package com.nightread.app.service

import java.io.File
import java.nio.charset.StandardCharsets

object MdParser : BookParser {
    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        return try {
            val text = file.readText(StandardCharsets.UTF_8)
            val title = file.nameWithoutExtension.ifBlank { defaultTitle }
            val htmlContent = convertMarkdownToHtml(text)
            val preview = makePreview(text)
            BookParser.ParsedBook(title, "Markdown Документ", htmlContent, annotation = preview)
        } catch (e: Exception) {
            BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    private fun makePreview(rawText: String): String {
        val clean = rawText.replace(Regex("<[^>]*>"), "")
            .replace(Regex("[#*`_\\-~]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (clean.length > 180) clean.take(180) + "..." else clean
    }

    fun convertMarkdownToHtml(mdText: String): String {
        val lines = mdText.split("\n")
        val sb = java.lang.StringBuilder()
        sb.append("<html><body>")
        var inList = false
        for (line in lines) {
            val l = line.trim()
            if (l.startsWith("#")) {
                if (inList) { sb.append("</ul>"); inList = false }
                val level = l.takeWhile { it == '#' }.length
                val text = l.substring(level).trim()
                sb.append("<h").append(level).append(">").append(escapeHtml(text)).append("</h").append(level).append(">")
            } else if (l.startsWith("* ") || l.startsWith("- ")) {
                if (!inList) { sb.append("<ul>"); inList = true }
                val text = l.substring(2).trim()
                sb.append("<li>").append(convertInlineMarkdown(text)).append("</li>")
            } else {
                if (inList) { sb.append("</ul>"); inList = false }
                if (l.isNotEmpty()) {
                    sb.append("<p>").append(convertInlineMarkdown(l)).append("</p>")
                } else {
                    sb.append("<br/>")
                }
            }
        }
        if (inList) sb.append("</ul>")
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun convertInlineMarkdown(text: String): String {
        var escaped = escapeHtml(text)
        escaped = escaped.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
        escaped = escaped.replace(Regex("__(.*?)__"), "<strong>$1</strong>")
        escaped = escaped.replace(Regex("\\*(.*?)\\*"), "<em>$1</em>")
        escaped = escaped.replace(Regex("_(.*?)_"), "<em>$1</em>")
        escaped = escaped.replace(Regex("`(.*?)`"), "<code>$1</code>")
        return escaped
    }
}
