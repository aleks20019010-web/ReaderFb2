package com.nightread.app.data

import java.io.File

object BookFormatHelper {
    // Ebook formats displayed in the main Library screen
    val EBOOK_EXTENSIONS = setOf(
        "fb2", "zip", "epub", "mobi", "azw", "azw3"
    )

    // Document formats displayed ONLY in the Documents screen
    val DOCUMENT_EXTENSIONS = setOf(
        "md", "docx", "doc", "pdf", "html", "htm"
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
        var ext = File(filePath).extension.lowercase()
        
        if (ext.isEmpty() || ext == "bin") {
            try {
                val file = File(filePath)
                if (file.exists() && file.length() > 4) {
                    val stream = java.io.FileInputStream(file)
                    val header = ByteArray(4)
                    stream.read(header)
                    stream.close()
                    if (header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()) {
                        ext = "pdf"
                    } else if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
                        ext = "jpg"
                    } else if (header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() && header[2] == 3.toByte() && header[3] == 4.toByte()) {
                        ext = "zip"
                    }
                }
            } catch (e: Exception) {}
        }
        
        if (filePath.lowercase().endsWith(".fb2.zip")) return true
        if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "gif") return false
        // All supported formats are rendered via WebView
        return EBOOK_EXTENSIONS.contains(ext) || DOCUMENT_EXTENSIONS.contains(ext)
    }

    fun isSupported(filePath: String?): Boolean {
        return isEbook(filePath) || isDocument(filePath)
    }
}
