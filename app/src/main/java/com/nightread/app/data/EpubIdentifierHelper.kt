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
    val description: String? = null,
    val opfDir: String = ""
)

object EpubIdentifierHelper {
    private const val TAG = "EpubIdentifierHelper"

    fun isEpub(file: File): Boolean {
        if (file.extension.lowercase() == "epub") return true
        return try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val normalizedName = entry.name.replace("\\", "/").trim().lowercase()
                    if (normalizedName == "mimetype") {
                        val content = zip.readBytes().toString(Charsets.UTF_8).trim()
                        return content.contains("application/epub+zip")
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

    private fun normalizeZipPath(path: String): String {
        return path.replace("\\", "/").replace("//", "/").trim().lowercase()
    }

    fun computeFileSha1(file: File): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            java.io.FileInputStream(file).use { fis ->
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                val sha1Bytes = digest.digest()
                sha1Bytes.joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compute SHA-1 of local file: ${file.absolutePath}", e)
            null
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
                    val normalizedName = normalizeZipPath(entry.name)
                    if (normalizedName == "meta-inf/container.xml") {
                        val strContent = content.toString(Charsets.UTF_8)
                        val match = Regex("<rootfile\\s+[^>]*full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(strContent)
                        opfPath = match?.groupValues?.get(1)
                    } else {
                        zipFiles[normalizedName] = content
                    }
                    entry = zip.nextEntry
                }
            }
            
            if (opfPath != null) {
                val normalizedOpfPath = normalizeZipPath(opfPath!!)
                val opfDir = opfPath!!.substringBeforeLast("/", "")
                opfContent = zipFiles[normalizedOpfPath]?.toString(Charsets.UTF_8)
                
                if (opfContent != null) {
                    val idMatch = Regex("<(?:\\w+:)?identifier[^>]*>([^<]+)</(?:\\w+:)?identifier>", RegexOption.IGNORE_CASE).find(opfContent)
                    val titleMatch = Regex("<(?:\\w+:)?title[^>]*>([^<]+)</(?:\\w+:)?title>", RegexOption.IGNORE_CASE).find(opfContent)
                    val authorMatch = Regex("<(?:\\w+:)?creator[^>]*>([^<]+)</(?:\\w+:)?creator>", RegexOption.IGNORE_CASE).find(opfContent)
                    val descMatch = Regex("<(?:\\w+:)?description[^>]*>([^<]+)</(?:\\w+:)?description>", RegexOption.IGNORE_CASE).find(opfContent)
                    
                    // Identify cover
                    var coverId: String? = null
                    val metaMatches = Regex("<meta\\s+([^>]+)>", RegexOption.IGNORE_CASE).findAll(opfContent)
                    for (meta in metaMatches) {
                        val attrs = meta.groupValues[1]
                        if (attrs.contains("name=\"cover\"", ignoreCase = true) || attrs.contains("name='cover'", ignoreCase = true)) {
                            val contentMatch = Regex("content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                            if (contentMatch != null) {
                                coverId = contentMatch.groupValues[1]
                                break
                            }
                        }
                    }

                    // Map IDs to file paths robustly to handle attributes in arbitrary order
                    val manifestMap = mutableMapOf<String, String>()
                    val itemMatches = Regex("<item\\s+([^>]+)>", RegexOption.IGNORE_CASE).findAll(opfContent)
                    for (item in itemMatches) {
                        val attrs = item.groupValues[1]
                        val idM = Regex("id\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                        val hrefM = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                        if (idM != null && hrefM != null) {
                            manifestMap[idM.groupValues[1]] = hrefM.groupValues[1]
                        }
                    }

                    if (coverId != null) {
                        coverPath = manifestMap[coverId]
                    }

                    var identifier = idMatch?.groupValues?.get(1)?.trim() ?: ""
                    if (identifier.isEmpty()) {
                        identifier = computeFileSha1(file) ?: java.util.UUID.randomUUID().toString()
                    }
                    
                    // Parse Spine
                    val spineMatch = Regex("<spine[^>]*>(.*?)</spine>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(opfContent)
                    val spine = spineMatch?.groupValues?.get(1) ?: ""
                    val itemrefMatches = Regex("<itemref\\s+([^>]+)>", RegexOption.IGNORE_CASE).findAll(spine)
                    val idrefs = mutableListOf<String>()
                    for (itemref in itemrefMatches) {
                        val attrs = itemref.groupValues[1]
                        val idrefM = Regex("idref\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)
                        if (idrefM != null) {
                            idrefs.add(idrefM.groupValues[1])
                        }
                    }
                    
                    val contentBuilder = StringBuilder()
                    for (idref in idrefs) {
                        val href = manifestMap[idref]
                        if (href != null) {
                            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$href" else href
                            val bytes = zipFiles[normalizeZipPath(fullPath)]
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
                        description = descMatch?.groupValues?.get(1)?.trim(),
                        opfDir = opfDir
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting EPUB metadata: ${file.name}", e)
            null
        }
    }

    fun unzip(zipFile: File, targetDirectory: File) {
        if (targetDirectory.exists() && targetDirectory.list()?.isNotEmpty() == true) {
            return // Already extracted
        }
        targetDirectory.mkdirs()
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val file = File(targetDirectory, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unzipping file: ${zipFile.name}", e)
        }
    }

    fun extractAndSaveEpubCover(file: File, coverPath: String?, sha1: String, context: android.content.Context): String? {
        if (coverPath.isNullOrEmpty()) return null
        return try {
            val normalizedCoverPath = normalizeZipPath(coverPath)
            var coverBytes: ByteArray? = null
            
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val normalizedName = normalizeZipPath(entry.name)
                    if (normalizedName == normalizedCoverPath) {
                        coverBytes = zip.readBytes()
                        break
                    }
                    entry = zip.nextEntry
                }
            }
            
            if (coverBytes != null && coverBytes!!.isNotEmpty()) {
                val cacheDir = context.cacheDir
                val coversDir = File(cacheDir, "covers")
                if (!coversDir.exists()) {
                    coversDir.mkdirs()
                }
                val coverFile = File(coversDir, "${sha1}.jpg")
                coverFile.writeBytes(coverBytes!!)
                coverFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting EPUB cover: ${file.name}", e)
            null
        }
    }
}
