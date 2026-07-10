package com.nightread.app.service

import android.util.Log
import java.io.File
import java.util.zip.ZipFile

object NewEpubParser {
    private const val TAG = "NewEpubParser"

    fun parse(file: File, fallbackTitle: String): BookMetadata? {
        try {
            ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val rootfileMatch = Regex("<rootfile[^>]+full-path=[\"']([^\"']+)[\"']").find(containerXml)
                val opfPath = rootfileMatch?.groupValues?.get(1) ?: return null

                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()

                val titleMatch = Regex("<dc:title[^>]*>([^<]+)</dc:title>").find(opfXml)
                val title = titleMatch?.groupValues?.get(1)?.trim() ?: fallbackTitle

                val authorMatch = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>").find(opfXml)
                val author = authorMatch?.groupValues?.get(1)?.trim() ?: "Unknown Author"

                val langMatch = Regex("<dc:language[^>]*>([^<]+)</dc:language>").find(opfXml)
                val lang = langMatch?.groupValues?.get(1)?.trim()

                var series: String? = null
                var seriesIndex: Int? = null

                val seriesMatch = Regex("<meta[^>]+name=[\"']calibre:series[\"'][^>]+content=[\"']([^\"']+)[\"']").find(opfXml)
                if (seriesMatch != null) {
                    series = seriesMatch.groupValues[1]
                }
                val seriesIndexMatch = Regex("<meta[^>]+name=[\"']calibre:series_index[\"'][^>]+content=[\"']([^\"']+)[\"']").find(opfXml)
                if (seriesIndexMatch != null) {
                    seriesIndex = seriesIndexMatch.groupValues[1].toDoubleOrNull()?.toInt()
                }

                val descMatch = Regex("<dc:description[^>]*>([\\s\\S]*?)</dc:description>").find(opfXml)
                var annotation = descMatch?.groupValues?.get(1)?.trim()
                if (annotation != null) {
                    annotation = annotation.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                }

                return BookMetadata(title, author, "", series, seriesIndex, lang, annotation)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing epub: ${file.absolutePath}", e)
        }
        return null
    }

    fun extractText(file: File): String {
        try {
            ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return ""
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val rootfileMatch = Regex("<rootfile[^>]+full-path=[\"']([^\"']+)[\"']").find(containerXml)
                val opfPath = rootfileMatch?.groupValues?.get(1) ?: return ""

                val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

                val opfEntry = zip.getEntry(opfPath) ?: return ""
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()

                val manifestRegex = Regex("<item[^>]+id=[\"']([^\"']+)[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>")
                val manifestMatches = manifestRegex.findAll(opfXml)
                val manifestMap = mutableMapOf<String, String>()
                for (match in manifestMatches) {
                    val id = match.groupValues[1]
                    val href = match.groupValues[2]
                    manifestMap[id] = href
                }

                val manifestRegexAlt = Regex("<item[^>]+href=[\"']([^\"']+)[\"'][^>]+id=[\"']([^\"']+)[\"'][^>]*>")
                for (match in manifestRegexAlt.findAll(opfXml)) {
                    val href = match.groupValues[1]
                    val id = match.groupValues[2]
                    manifestMap[id] = href
                }

                val spineMatch = Regex("<spine[^>]*>([\\s\\S]*?)</spine>").find(opfXml)
                val spineXml = spineMatch?.groupValues?.get(1) ?: return ""

                val itemrefRegex = Regex("<itemref[^>]+idref=[\"']([^\"']+)[\"']")
                val spineRefs = itemrefRegex.findAll(spineXml).map { it.groupValues[1] }.toList()

                val textBuilder = StringBuilder()

                for (idref in spineRefs) {
                    val href = manifestMap[idref] ?: continue
                    val fullPath = opfDir + href.replace("%20", " ")
                    val htmlEntry = zip.getEntry(fullPath) ?: continue
                    val htmlXml = zip.getInputStream(htmlEntry).bufferedReader().readText()
                    textBuilder.append(parseHtmlToText(htmlXml))
                    textBuilder.append("\n\u000C")
                }

                return cleanUpText(textBuilder.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from epub: ${file.absolutePath}", e)
        }
        return ""
    }

    private fun parseHtmlToText(html: String): String {
        var text = html
        text = text.replace(Regex("<head[^>]*>.*?</head>", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
        text = text.replace(Regex("<h[1-3][^>]*>"), "\n\u000C[CHAPTER]")
        text = text.replace(Regex("</h[1-3]>"), "[/CHAPTER]\n")
        text = text.replace(Regex("<p[^>]*>"), "\n    ")
        text = text.replace(Regex("</p>"), "")
        text = text.replace(Regex("<div[^>]*>"), "\n")
        text = text.replace(Regex("</div>"), "\n")
        text = text.replace(Regex("<br\\s*/?>"), "\n")
        text = text.replace(Regex("<[^>]+>"), "")
        text = text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
        return text
    }
    
    private fun cleanUpText(text: String): String {
        var result = text
        result = result.replace(Regex("([ \\t\\r\\n]*\\n[ \\t\\r\\n]*)+"), "\n    ")
        result = result.replace(Regex("\\u000C+"), "\u000C")
        return result.trim().trim('\u000C').trim()
    }
    
    fun extractCover(file: File): ByteArray? {
        try {
            ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
                val rootfileMatch = Regex("<rootfile[^>]+full-path=[\"']([^\"']+)[\"']").find(containerXml)
                val opfPath = rootfileMatch?.groupValues?.get(1) ?: return null

                val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()

                val manifestRegex = Regex("<item[^>]+id=[\"']([^\"']+)[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>")
                val manifestMatches = manifestRegex.findAll(opfXml)
                val manifestMap = mutableMapOf<String, String>()
                for (match in manifestMatches) {
                    val id = match.groupValues[1]
                    val href = match.groupValues[2]
                    manifestMap[id] = href
                }

                val manifestRegexAlt = Regex("<item[^>]+href=[\"']([^\"']+)[\"'][^>]+id=[\"']([^\"']+)[\"'][^>]*>")
                for (match in manifestRegexAlt.findAll(opfXml)) {
                    val href = match.groupValues[1]
                    val id = match.groupValues[2]
                    manifestMap[id] = href
                }
                
                var coverId: String? = null
                val metaCoverMatch = Regex("<meta[^>]+name=[\"']cover[\"'][^>]+content=[\"']([^\"']+)[\"']").find(opfXml)
                if (metaCoverMatch != null) {
                    coverId = metaCoverMatch.groupValues[1]
                }
                
                if (coverId == null) {
                    val propCoverMatch = Regex("<item[^>]+id=[\"']([^\"']+)[\"'][^>]+properties=[\"']cover-image[\"']").find(opfXml)
                    if (propCoverMatch != null) {
                        coverId = propCoverMatch.groupValues[1]
                    }
                }
                
                if (coverId == null) {
                    coverId = "cover"
                }

                val href = manifestMap[coverId] ?: manifestMap.values.find { it.contains("cover", ignoreCase = true) && (it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg")) }
                if (href != null) {
                    val fullPath = opfDir + href.replace("%20", " ")
                    val coverEntry = zip.getEntry(fullPath) ?: return null
                    return zip.getInputStream(coverEntry).readBytes()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cover from epub: ${file.absolutePath}", e)
        }
        return null
    }
}
