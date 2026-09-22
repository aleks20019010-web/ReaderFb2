package com.nightread.app.service

import android.util.Log
import com.nightread.app.data.EpubIdentifierHelper
import com.nightread.app.data.epub.EpubPathResolver
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

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

                // Extract Manifest items: id -> href
                val manifestMap = mutableMapOf<String, String>()
                val itemMatches = Regex("<item\\s+[^>]*id\\s*=\\s*[\"']([^\"']+)[\"'][^>]*href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(opfContent)
                for (item in itemMatches) {
                    manifestMap[item.groupValues[1]] = item.groupValues[2]
                }
                // Check reverse attribute order in manifest item: href first then id
                val itemMatchesReverse = Regex("<item\\s+[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*id\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(opfContent)
                for (item in itemMatchesReverse) {
                    val href = item.groupValues[1]
                    val id = item.groupValues[2]
                    if (!manifestMap.containsKey(id)) {
                        manifestMap[id] = href
                    }
                }

                // Extract Spine order
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
            sb.append("[CHAPTER]\n").append(heading).append("\n[/CHAPTER]\n\n")
        }

        // Clean out styles and scripts
        val noScripts = html
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")

        // Replace block tags with newline markers
        val formattedBlocks = noScripts
            .replace(Regex("<(p|div|section|article|h1|h2|h3|h4|h5|h6)[^>]*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<(br|hr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|section|article|h1|h2|h3|h4|h5|h6)>", RegexOption.IGNORE_CASE), "\n")

        // Strip remaining HTML tags
        var inTag = false
        val cleanSb = StringBuilder(formattedBlocks.length)
        for (i in formattedBlocks.indices) {
            val c = formattedBlocks[i]
            if (c == '<') {
                inTag = true
            } else if (c == '>') {
                inTag = false
            } else if (!inTag) {
                cleanSb.append(c)
            }
        }

        val decoded = cleanSb.toString()
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")

        // Normalize multiple newlines
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
