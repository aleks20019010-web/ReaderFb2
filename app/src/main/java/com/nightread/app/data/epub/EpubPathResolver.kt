package com.nightread.app.data.epub

import java.net.URLDecoder

object EpubPathResolver {

    fun cleanZipPath(path: String): String {
        var p = path.replace("\\", "/").replace("//", "/").trim().lowercase()
        while (p.startsWith("./")) {
            p = p.substring(2)
        }
        while (p.startsWith("/")) {
            p = p.substring(1)
        }
        return p
    }

    fun resolvePath(baseDir: String, relativePath: String): String {
        val decoded = try { URLDecoder.decode(relativePath, "UTF-8") } catch (e: Exception) { relativePath }
        val cleanRelative = cleanZipPath(decoded)
        val cleanBase = cleanZipPath(baseDir)

        if (cleanBase.isEmpty()) return cleanRelative
        if (cleanRelative.startsWith("$cleanBase/")) return cleanRelative

        val segments = mutableListOf<String>()
        if (cleanBase.isNotEmpty()) {
            segments.addAll(cleanBase.split("/"))
        }
        for (part in cleanRelative.split("/")) {
            if (part == "..") {
                if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
            } else if (part != "." && part.isNotEmpty()) {
                segments.add(part)
            }
        }
        return segments.joinToString("/")
    }

    fun cleanHtmlAndEntities(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        var text = rawText.trim()

        val cdataMatch = Regex("<!\\[CDATA\\[(.*?)\\]\\]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(text)
        if (cdataMatch != null) {
            text = cdataMatch.groupValues[1]
        }

        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<div[^>]*>", RegexOption.IGNORE_CASE), "")

        text = text.replace(Regex("<[^>]+>"), "")

        text = text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&copy;", "©")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")

        text = text.replace(Regex("&#(\\d+);")) { match ->
            try {
                val code = match.groupValues[1].toInt()
                code.toChar().toString()
            } catch (e: Exception) {
                " "
            }
        }

        text = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

        return if (text.isBlank()) null else text
    }
}
