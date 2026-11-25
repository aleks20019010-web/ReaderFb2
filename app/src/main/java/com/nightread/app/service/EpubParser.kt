package com.nightread.app.service

import android.util.Log
import com.nightread.app.data.EpubIdentifierHelper
import com.nightread.app.data.epub.EpubPathResolver
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.util.zip.ZipFile

object EpubParser : BookParser {
    private const val TAG = "EpubParser"

    private val parserFactory by lazy {
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
    }

    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        if (!file.exists()) {
            return BookParser.ParsedBook(defaultTitle, "Unknown", "")
        }

        try {
            ZipFile(file).use { zip ->
                var opfPath: String? = null
                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals("meta-inf/container.xml", ignoreCase = true) }

                if (containerEntry != null) {
                    val containerXml = zip.getInputStream(containerEntry).bufferedReader(Charsets.UTF_8).readText()
                    val match = Regex("<rootfile\\s+[^>]*full-path\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(containerXml)
                    opfPath = match?.groupValues?.get(1)
                }

                if (opfPath == null) {
                    val opfEntry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
                    opfPath = opfEntry?.name
                }

                if (opfPath == null) {
                    val fallbackMeta = EpubIdentifierHelper.getEpubMetadata(file)
                    return BookParser.ParsedBook(
                        title = fallbackMeta?.title ?: defaultTitle,
                        author = fallbackMeta?.author ?: "Unknown",
                        content = ""
                    )
                }

                val opfEntry = zip.getEntry(opfPath)
                    ?: zip.entries().asSequence().firstOrNull { it.name.equals(opfPath, ignoreCase = true) }
                    ?: return BookParser.ParsedBook(defaultTitle, "Unknown", "")

                val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") else ""
                val opfContent = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).readText()

                val titleMatch = Regex("<(?:\\w+:)?title[^>]*>([^<]+)</(?:\\w+:)?title>", RegexOption.IGNORE_CASE).find(opfContent)
                val authorMatch = Regex("<(?:\\w+:)?creator[^>]*>([^<]+)</(?:\\w+:)?creator>", RegexOption.IGNORE_CASE).find(opfContent)

                val bookTitle = titleMatch?.groupValues?.get(1)?.trim() ?: defaultTitle
                val bookAuthor = authorMatch?.groupValues?.get(1)?.trim() ?: "Unknown"

                val manifestMap = mutableMapOf<String, String>()
                val itemMatches = Regex("<item\\s+[^>]*id\\s*=\\s*[\"']([^\"']+)[\"'][^>]*href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(opfContent)
                for (item in itemMatches) {
                    manifestMap[item.groupValues[1]] = item.groupValues[2]
                }
                val itemMatchesReverse = Regex("<item\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*id\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(opfContent)
                for (item in itemMatchesReverse) {
                    val href = item.groupValues[1]
                    val id = item.groupValues[2]
                    if (!manifestMap.containsKey(id)) {
                        manifestMap[id] = href
                    }
                }

                val spineIds = mutableListOf<String>()
                val itemrefMatches = Regex("<itemref\\s+[^>]*idref\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(opfContent)
                for (itemref in itemrefMatches) {
                    spineIds.add(itemref.groupValues[1])
                }

                val contentBuilder = StringBuilder(100_000)

                for (id in spineIds) {
                    val rawHref = manifestMap[id] ?: continue
                    val resolvedHref = EpubPathResolver.resolvePath(opfDir, rawHref)
                    val chapterEntry = zip.getEntry(resolvedHref)
                        ?: zip.entries().asSequence().firstOrNull { it.name.equals(resolvedHref, ignoreCase = true) }
                        ?: continue

                    try {
                        val chapterRaw = zip.getInputStream(chapterEntry).bufferedReader(Charsets.UTF_8).readText()
                        val chapterCleaned = extractReadableHtmlText(chapterRaw)
                        if (chapterCleaned.isNotBlank()) {
                            contentBuilder.append(chapterCleaned).append("\n\n")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read chapter $resolvedHref", e)
                    }
                }

                return BookParser.ParsedBook(
                    title = bookTitle,
                    author = bookAuthor,
                    content = contentBuilder.toString().trim()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EPUB file: ${file.name}", e)
            val fallback = EpubIdentifierHelper.getEpubMetadata(file)
            return BookParser.ParsedBook(
                title = fallback?.title ?: defaultTitle,
                author = fallback?.author ?: "Unknown",
                content = ""
            )
        }
    }

    fun extractReadableHtmlText(html: String): String {
        val sb = StringBuilder(html.length / 2)
        val titleMatch = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)
        val h1Match = Regex("<h1[^>]*>([^<]+)</h1>", RegexOption.IGNORE_CASE).find(html)

        val heading = (h1Match?.groupValues?.get(1) ?: titleMatch?.groupValues?.get(1))?.trim()
        if (!heading.isNullOrBlank() && !heading.equals("untitled", ignoreCase = true)) {
            sb.append("[CHAPTER]").append(heading).append("[/CHAPTER]\n\n")
        }

        val noScripts = html
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")

        val formattedBlocks = noScripts
            .replace(Regex("<(p|div|section|article|h1|h2|h3|h4|h5|h6)[^>]*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<(br|hr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|section|article|h1|h2|h3|h4|h5|h6)>", RegexOption.IGNORE_CASE), "\n")

        // Нормализуем теги форматирования и удаляем только остальные HTML-теги
        val withInlineFormatting = formattedBlocks
            .replace(Regex("<(?:strong|b)\\b[^>]*>", RegexOption.IGNORE_CASE), "<b>")
            .replace(Regex("</(?:strong|b)>", RegexOption.IGNORE_CASE), "</b>")
            .replace(Regex("<(?:em|i)\\b[^>]*>", RegexOption.IGNORE_CASE), "<i>")
            .replace(Regex("</(?:em|i)>", RegexOption.IGNORE_CASE), "</i>")
            .replace(Regex("<u\\b[^>]*>", RegexOption.IGNORE_CASE), "<u>")
            .replace(Regex("</u>", RegexOption.IGNORE_CASE), "</u>")
            .replace(Regex("<(?:s|del|strike)\\b[^>]*>", RegexOption.IGNORE_CASE), "<s>")
            .replace(Regex("</(?:s|del|strike)>", RegexOption.IGNORE_CASE), "</s>")
            .replace(Regex("<(sup|sub|code)\\b[^>]*>", RegexOption.IGNORE_CASE), "<$1>")
            .replace(Regex("<(?!/?(?:b|i|u|s|sup|sub|code)\\b)[^>]*>", RegexOption.IGNORE_CASE), "")

        val decoded = withInlineFormatting
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")

        val lines = decoded.split('\n')
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                sb.append(trimmed).append("\n\n")
            }
        }

        return sb.toString().trim()
    }
}