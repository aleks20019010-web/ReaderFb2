package com.nightread.app.data

import android.util.Log
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class EpubMetadata(
    val identifier: String,
    val title: String,
    val author: String,
    val content: String,
    val coverPath: String? = null,
    val description: String? = null
)

object EpubIdentifierHelper {
    private const val TAG = "EpubIdentifierHelper"

    fun isEpub(file: File): Boolean {
        return try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "mimetype") {
                        val content = zip.readBytes().toString(Charsets.UTF_8).trim()
                        return content == "application/epub+zip"
                    }
                    entry = zip.nextEntry
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if file is EPUB: ${file.name}", e)
            false
        }
    }

    fun getEpubMetadata(file: File): EpubMetadata? {
        return try {
            var opfPath: String? = null
            var opfContent: String? = null
            val zipFiles = mutableMapOf<String, ByteArray>()
            var coverPath: String? = null
            
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val content = zip.readBytes()
                    if (entry.name == "META-INF/container.xml") {
                        val strContent = content.toString(Charsets.UTF_8)
                        val match = Regex("<rootfile\\s+[^>]*full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(strContent)
                        opfPath = match?.groupValues?.get(1)
                    } else {
                        zipFiles[entry.name] = content
                    }
                    entry = zip.nextEntry
                }
            }
            
            if (opfPath != null) {
                // OPF is relative to container, handle potential directory structures
                val opfDir = opfPath!!.substringBeforeLast("/", "")
                opfContent = zipFiles[opfPath]?.toString(Charsets.UTF_8)
                
                if (opfContent != null) {
                    val idMatch = Regex("<dc:identifier[^>]*>([^<]+)</dc:identifier>", RegexOption.IGNORE_CASE).find(opfContent)
                    val titleMatch = Regex("<dc:title[^>]*>([^<]+)</dc:title>", RegexOption.IGNORE_CASE).find(opfContent)
                    val authorMatch = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>", RegexOption.IGNORE_CASE).find(opfContent)
                    val descMatch = Regex("<dc:description[^>]*>([^<]+)</dc:description>", RegexOption.IGNORE_CASE).find(opfContent)
                    
                    // Identify cover
                    val metaCoverMatch = Regex("<meta\\s+[^>]*name\\s*=\\s*[\"']cover[\"']\\s+content\\s*=\\s*[\"']([^\"']+)[\"']").find(opfContent)
                    if (metaCoverMatch != null) {
                        val coverId = metaCoverMatch.groupValues[1]
                        val manifestMatches = Regex("<item\\s+[^>]*id\\s*=\\s*[\"']([^\"']+)[\"']\\s+href\\s*=\\s*[\"']([^\"']+)[\"']").findAll(opfContent)
                        val manifestMap = manifestMatches.associate { it.groupValues[1] to it.groupValues[2] }
                        coverPath = manifestMap[coverId]
                    }

                    val identifier = idMatch?.groupValues?.get(1)?.trim() ?: ""
                    
                    // Parse Spine
                    val spineMatch = Regex("<spine[^>]*>(.*?)</spine>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(opfContent)
                    val spine = spineMatch?.groupValues?.get(1) ?: ""
                    val itemrefMatches = Regex("<itemref\\s+[^>]*idref\\s*=\\s*[\"']([^\"']+)[\"']").findAll(spine)
                    
                    // Map IDs to file paths
                    val manifestMatches = Regex("<item\\s+[^>]*id\\s*=\\s*[\"']([^\"']+)[\"']\\s+href\\s*=\\s*[\"']([^\"']+)[\"']").findAll(opfContent)
                    val manifestMap = manifestMatches.associate { it.groupValues[1] to it.groupValues[2] }
                    
                    val contentBuilder = StringBuilder()
                    for (itemref in itemrefMatches) {
                        val idref = itemref.groupValues[1]
                        val href = manifestMap[idref]
                        if (href != null) {
                            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                            val bytes = zipFiles[fullPath]
                            if (bytes != null) {
                                // Improved encoding handling
                                val xhtmlContent = try {
                                    val str = String(bytes, Charsets.UTF_8)
                                    // Check if it's likely UTF-8 by looking for common replacement chars or encoding declaration
                                    if (str.contains("encoding=\"UTF-8\"") || str.contains("encoding='UTF-8'")) {
                                        str
                                    } else {
                                        // Try other common encodings
                                        String(bytes, Charset.forName("windows-1251"))
                                    }
                                } catch (e: Exception) {
                                    String(bytes, Charsets.ISO_8859_1)
                                }
                                
                                // Extract body content
                                val bodyMatch = Regex("<body[^>]*>(.*?)</body>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(xhtmlContent)
                                if (bodyMatch != null) {
                                    // Keep HTML tags for the converter to process
                                    contentBuilder.append(bodyMatch.groupValues[1])
                                    contentBuilder.append("\n\n")
                                }
                            }
                        }
                    }
                    
                    return EpubMetadata(
                        identifier = identifier,
                        title = titleMatch?.groupValues?.get(1)?.trim() ?: "Unknown",
                        author = authorMatch?.groupValues?.get(1)?.trim() ?: "Unknown",
                        content = contentBuilder.toString(),
                        coverPath = if (coverPath != null) {
                            if (opfDir.isNotEmpty()) "$opfDir/$coverPath" else coverPath
                        } else null,
                        description = descMatch?.groupValues?.get(1)?.trim()
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting EPUB metadata: ${file.name}", e)
            null
        }
    }
}
