package com.nightread.app.service

import android.text.Html

object TtsExtractor {
    fun extractParagraphs(html: String): List<TtsParagraph> {
        val result = mutableListOf<TtsParagraph>()
        val regex = Regex("<(p|h1|h2|h3|h4|h5|h6)[^>]*id=['\"](p_\\d+)['\"][^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val matches = regex.findAll(html)
        for (match in matches) {
            val id = match.groupValues[2]
            val content = match.groupValues[3]
            val cleanText = Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            if (cleanText.isNotEmpty()) {
                result.add(TtsParagraph(id, cleanText))
            }
        }
        return result
    }
}
