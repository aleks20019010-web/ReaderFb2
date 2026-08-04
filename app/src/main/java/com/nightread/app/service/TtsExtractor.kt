package com.nightread.app.service

object TtsExtractor {
    private val tagRegex = Regex("<(p|h1|h2|h3|h4|h5|h6)[^>]*id=['\"](p_\\d+)['\"][^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val htmlTagPattern = Regex("<[^>]+>")

    fun extractParagraphs(html: String): List<TtsParagraph> {
        val result = mutableListOf<TtsParagraph>()
        val matches = tagRegex.findAll(html)
        for (match in matches) {
            val id = match.groupValues[2]
            val content = match.groupValues[3]
            val cleanText = stripHtml(content)
            if (cleanText.isNotEmpty()) {
                result.add(TtsParagraph(id, cleanText))
            }
        }
        return result
    }

    private fun stripHtml(input: String): String {
        return input.replace(htmlTagPattern, "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
