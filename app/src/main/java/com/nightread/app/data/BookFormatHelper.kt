package com.nightread.app.data

import java.io.File

object BookFormatHelper {
    // Ebook formats displayed in the main Library screen
    val EBOOK_EXTENSIONS = setOf(
        "fb2", "zip", "epub", "mobi", "azw", "azw3"
    )

    // Document formats displayed ONLY in the Documents screen
    val DOCUMENT_EXTENSIONS = setOf(
        "md", "docx", "doc", "pdf", "html", "htm", "txt"
    )

    fun isEbook(filePath: String?): Boolean {
        if (filePath == null) return false
        val ext = File(filePath).extension.lowercase()
        // Special case: fb2.zip is an ebook
        if (filePath.lowercase().endsWith(".fb2.zip")) return true
        return EBOOK_EXTENSIONS.contains(ext)
    }

    fun isDocument(filePath: String?): Boolean {
        if (filePath == null) return false
        val ext = File(filePath).extension.lowercase()
        return DOCUMENT_EXTENSIONS.contains(ext)
    }

    fun isWebViewBook(filePath: String?): Boolean {
        if (filePath == null) return false
        val ext = File(filePath).extension.lowercase()
        if (filePath.lowercase().endsWith(".fb2.zip")) return true
        // Every supported format except plain .txt is rendered via WebView
        return (EBOOK_EXTENSIONS.contains(ext) || DOCUMENT_EXTENSIONS.contains(ext)) && ext != "txt"
    }

    fun isSupported(filePath: String?): Boolean {
        return isEbook(filePath) || isDocument(filePath)
    }
}
