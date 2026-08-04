package com.nightread.app.service

import java.io.File

object DocParser : BookParser {
    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        return try {
            val title = file.nameWithoutExtension.ifBlank { defaultTitle }
            val bytes = file.readBytes()
            val text = extractReadableText(bytes)
            val htmlContent = "<html><body>" + text.split("\n").filter { it.isNotBlank() }.joinToString("") { "<p>${escapeHtml(it)}</p>" } + "</body></html>"
            BookParser.ParsedBook(title, "Word Документ (DOC)", htmlContent)
        } catch (e: Exception) {
            BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    private fun extractReadableText(bytes: ByteArray): String {
        val sb = java.lang.StringBuilder()
        var i = 0
        val temp = java.lang.StringBuilder()
        while (i < bytes.size - 1) {
            val b1 = bytes[i].toInt() and 0xFF
            val b2 = bytes[i + 1].toInt() and 0xFF
            val charCode = b1 or (b2 shl 8)
            if ((charCode in 0x0400..0x04FF) || (charCode in 0x0020..0x007E) || charCode == 10 || charCode == 13) {
                if (charCode == 10 || charCode == 13) {
                    temp.append("\n")
                } else {
                    temp.append(charCode.toChar())
                }
                i += 2
            } else {
                if (temp.length > 4) {
                    sb.append(temp.toString().trim()).append("\n")
                }
                temp.setLength(0)
                i++
            }
        }
        if (temp.length > 4) {
            sb.append(temp.toString().trim())
        }

        val lines = sb.toString().split("\n")
            .map { it.trim() }
            .filter { line ->
                val letters = line.count { it.isLetter() || it == ' ' }
                letters > 0.5 * line.length && line.length > 3
            }
        return lines.joinToString("\n")
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
