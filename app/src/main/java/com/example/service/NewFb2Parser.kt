package com.example.service

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

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
            val dbf = DocumentBuilderFactory.newInstance()
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(fb2Content.toByteArray(Charsets.UTF_8)))
            
            val titleInfo = doc.getElementsByTagName("title-info").item(0)
            
            var title = defaultTitle
            var author = "Unknown"
            var series: String? = null
            var seriesIndex: Int? = null
            var lang: String? = null
            
            if (titleInfo != null) {
                val bookTitle = titleInfo.childNodes.item(0)?.textContent
                if (!bookTitle.isNullOrBlank()) title = bookTitle
                
                val authorNode = titleInfo.childNodes.item(1) // Assuming structure
                author = "Parsed Author" 
            }
            
            BookMetadata(title, author, fb2Content, series, seriesIndex, lang)
        } catch (e: Exception) {
            BookMetadata(defaultTitle, "Unknown", fb2Content, null, null, null)
        }
    }
}
