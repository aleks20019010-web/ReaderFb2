package com.nightread.app.service

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
                .replace(Regex("<empty-line[^>]*>"), "\n")
                .replace(Regex("<p[^>]*>"), "\n    ")
                .replace(Regex("</p>"), "")
                .replace(Regex("<v[^>]*>"), "\n    ")
                .replace(Regex("</v>"), "")
                .replace(Regex("<title[^>]*>"), "\n")
                .replace(Regex("</title>"), "\n")
                .replace(Regex("<subtitle[^>]*>"), "\n")
                .replace(Regex("</subtitle>"), "\n")
            
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
                
            // Clean up multiple newlines to just one newline
            // Replace multiple newlines (with optional whitespace) with a single newline
            text = text.replace(Regex("(\\s*\\n\\s*)+"), "\n    ")
            return text.trim()
        } catch (e: Exception) {
            xmlContent
        }
    }
}
