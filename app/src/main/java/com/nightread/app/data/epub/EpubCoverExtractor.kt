package com.nightread.app.data.epub

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object EpubCoverExtractor {
    private const val TAG = "EpubCoverExtractor"

    fun extractAndSaveEpubCover(
        createInputStream: () -> InputStream?,
        coverPath: String?,
        sha1: String,
        context: Context
    ): String? {
        return try {
            var coverBytes: ByteArray? = null
            var targetPath = if (!coverPath.isNullOrEmpty()) EpubPathResolver.cleanZipPath(coverPath) else null

            // Pass 1: Resolve HTML target paths to image paths without loading all images
            if (targetPath != null && (targetPath.endsWith(".xhtml") || targetPath.endsWith(".html") || targetPath.endsWith(".htm"))) {
                ZipInputStream(createInputStream()!!.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = EpubPathResolver.cleanZipPath(entry.name)
                        if (!entry.isDirectory && (name == targetPath || name.endsWith("/$targetPath") || targetPath!!.endsWith("/$name"))) {
                            val buffer = ByteArrayOutputStream()
                            val data = ByteArray(8192)
                            var nRead: Int
                            var totalRead = 0
                            while (zip.read(data, 0, data.size).also { nRead = it } != -1 && totalRead < 256 * 1024) {
                                buffer.write(data, 0, nRead)
                                totalRead += nRead
                            }
                            val htmlStr = buffer.toString("UTF-8")
                            val imgMatch = Regex("<(?:img|image)[^>]+(?:src|href|xlink:href)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(htmlStr)
                                ?: Regex("url\\(['\"]?([^'\"]+)['\"]?\\)", RegexOption.IGNORE_CASE).find(htmlStr)
                            if (imgMatch != null) {
                                val imgSrc = imgMatch.groupValues[1]
                                val htmlDir = if (name.contains("/")) name.substringBeforeLast("/") else ""
                                targetPath = EpubPathResolver.resolvePath(htmlDir, imgSrc)
                            }
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
            }

            // Pass 2: Find the actual cover image
            val validExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
            var bestScore = -1

            ZipInputStream(createInputStream()!!.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = EpubPathResolver.cleanZipPath(entry.name)
                        val ext = name.substringAfterLast(".", "").lowercase()

                        if (ext in validExtensions) {
                            var isTarget = false
                            if (targetPath != null) {
                                val targetFilename = targetPath.substringAfterLast("/")
                                if (name == targetPath || name.endsWith("/$targetPath") || targetPath.endsWith("/$name") || name.substringAfterLast("/") == targetFilename) {
                                    isTarget = true
                                }
                            }

                            val lowerKey = name.lowercase()
                            val filename = lowerKey.substringAfterLast("/")
                            var score = 0

                            if (filename.contains("cover")) score += 120
                            else if (lowerKey.contains("cover")) score += 100

                            if (filename.contains("front")) score += 90
                            else if (lowerKey.contains("front")) score += 70

                            if (filename.contains("jacket") || filename.contains("poster") || filename.contains("title") || filename.contains("wrapper")) score += 60
                            if (lowerKey.contains("images/") || lowerKey.contains("media/") || lowerKey.contains("oebps/")) score += 15
                            if (filename.contains("01") || filename.contains("page1") || filename.contains("001")) score += 20

                            if (isTarget) score += 1000

                            if (score > bestScore) {
                                val buffer = ByteArrayOutputStream()
                                val data = ByteArray(8192)
                                var nRead: Int
                                var totalRead = 0
                                while (zip.read(data, 0, data.size).also { nRead = it } != -1 && totalRead < 10 * 1024 * 1024) {
                                    buffer.write(data, 0, nRead)
                                    totalRead += nRead
                                }
                                val bytes = buffer.toByteArray()
                                if (bytes.size > 300) {
                                    if (bytes.size > 10000) score += 20
                                    if (bytes.size > 30000) score += 20

                                    if (score > bestScore) {
                                        coverBytes = bytes
                                        bestScore = score
                                    }

                                    if (isTarget) {
                                        break
                                    }
                                }
                            }
                        }
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
                val sanitizedSha1 = sha1.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val coverFile = File(coversDir, "${sanitizedSha1}.jpg")
                coverFile.writeBytes(coverBytes!!)
                return coverFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting EPUB cover", e)
            null
        }
    }
}
