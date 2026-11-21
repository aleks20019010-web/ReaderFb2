package com.nightread.app.data.epub

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.nightread.app.service.NewCoverExtractor
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
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
            var targetPath = if (!coverPath.isNullOrEmpty()) {
                val decoded = try { URLDecoder.decode(coverPath, "UTF-8") } catch (_: Throwable) { coverPath }
                EpubPathResolver.cleanZipPath(decoded)
            } else null

            // Pass 1: Resolve HTML/XHTML/SVG cover wrapper to the actual image path
            if (targetPath != null && (
                targetPath.endsWith(".xhtml", ignoreCase = true) ||
                targetPath.endsWith(".html", ignoreCase = true) ||
                targetPath.endsWith(".htm", ignoreCase = true) ||
                targetPath.endsWith(".svg", ignoreCase = true)
            )) {
                val inStream = try { createInputStream()?.buffered() } catch (e: Throwable) { null }
                if (inStream != null) {
                    ZipInputStream(inStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            val name = EpubPathResolver.cleanZipPath(entry.name)
                            if (!entry.isDirectory && matchesPath(name, targetPath!!)) {
                                val buffer = ByteArrayOutputStream()
                                val data = ByteArray(8192)
                                var nRead: Int
                                var totalRead = 0
                                while (zip.read(data, 0, data.size).also { nRead = it } != -1 && totalRead < 256 * 1024) {
                                    buffer.write(data, 0, nRead)
                                    totalRead += nRead
                                }
                                val htmlStr = buffer.toString("UTF-8")
                                val imgMatch = Regex(
                                    """<(?:img|image)[^>]+(?:src|(?:[a-zA-Z0-9_-]+:)?href)\s*=\s*["']([^"']+)["']""",
                                    RegexOption.IGNORE_CASE
                                ).find(htmlStr)
                                    ?: Regex("""url\(['"]?([^'")]+)['"]?\)""", RegexOption.IGNORE_CASE).find(htmlStr)
                                if (imgMatch != null) {
                                    val imgSrcRaw = imgMatch.groupValues[1]
                                    val imgSrc = try { URLDecoder.decode(imgSrcRaw, "UTF-8") } catch (_: Throwable) { imgSrcRaw }
                                    val htmlDir = if (name.contains("/")) name.substringBeforeLast("/") else ""
                                    targetPath = EpubPathResolver.resolvePath(htmlDir, imgSrc)
                                }
                                break
                            }
                            entry = zip.nextEntry
                        }
                    }
                }
            }

            // Pass 2: Find the best cover image inside the EPUB
            val validExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
            var bestScore = -1

            val pass2Stream = try { createInputStream()?.buffered() } catch (e: Throwable) { null } ?: return null
            ZipInputStream(pass2Stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = EpubPathResolver.cleanZipPath(entry.name)
                        val ext = name.substringAfterLast(".", "").lowercase()

                        if (ext in validExtensions) {
                            val isTarget = targetPath != null && matchesPath(name, targetPath!!)

                            val lowerKey = name.lowercase()
                            val filename = lowerKey.substringAfterLast("/")
                            var baseScore = 0

                            if (filename.contains("cover")) baseScore += 120
                            else if (lowerKey.contains("cover")) baseScore += 100

                            if (filename.contains("front")) baseScore += 90
                            else if (lowerKey.contains("front")) baseScore += 70

                            if (filename.contains("jacket") || filename.contains("poster") || filename.contains("title") || filename.contains("wrapper")) baseScore += 60
                            if (lowerKey.contains("images/") || lowerKey.contains("media/") || lowerKey.contains("oebps/")) baseScore += 15
                            if (filename.contains("01") || filename.contains("page1") || filename.contains("001")) baseScore += 20

                            // Penalize tiny UI icons/logos/ornaments by name
                            if (filename.contains("logo") || filename.contains("icon") || filename.contains("ornament") || filename.contains("bullet")) {
                                baseScore -= 40
                            }

                            if (isTarget) baseScore += 1000

                            // Allow reading if potential size/dimension bonuses (+80) could beat current bestScore
                            if (isTarget || baseScore + 80 > bestScore) {
                                val buffer = ByteArrayOutputStream()
                                val data = ByteArray(8192)
                                var nRead: Int
                                var totalRead = 0
                                while (zip.read(data, 0, data.size).also { nRead = it } != -1 && totalRead < 10 * 1024 * 1024) {
                                    buffer.write(data, 0, nRead)
                                    totalRead += nRead
                                }
                                val bytes = buffer.toByteArray()
                                if (bytes.size > 200) {
                                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                                    val w = opts.outWidth
                                    val h = opts.outHeight
                                    if (w > 0 && h > 0) {
                                        var score = baseScore
                                        if (bytes.size > 10_000) score += 20
                                        if (bytes.size > 30_000) score += 20
                                        // Portrait book-like aspect ratio & decent resolution bonus
                                        if (w >= 150 && h >= 200) score += 25
                                        if (h > w) score += 15
                                        if (w < 60 || h < 60) score -= 50

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
                    }
                    entry = zip.nextEntry
                }
            }

            if (coverBytes != null && coverBytes!!.isNotEmpty()) {
                // Save to persistent filesDir/covers/ (not cacheDir which Android purges)
                NewCoverExtractor.saveCoverBytes(coverBytes!!, sha1, context)
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error extracting EPUB cover", e)
            null
        }
    }

    private fun matchesPath(entryPath: String, targetPath: String): Boolean {
        val eLower = entryPath.lowercase()
        val tLower = targetPath.lowercase()
        val tFile = tLower.substringAfterLast("/")
        val eFile = eLower.substringAfterLast("/")
        return eLower == tLower ||
            eLower.endsWith("/$tLower") ||
            tLower.endsWith("/$eLower") ||
            (tFile.isNotEmpty() && eFile == tFile)
    }
}
