package com.nightread.app.service

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object NewCoverExtractor {

    /**
     * Extracts cover from file, fallback implementation.
     */
    fun extractCover(file: File, sha1: String, context: Context?): String? {
        if (context == null) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            extractAndSaveCover(content, sha1, context)
        } catch (e: Exception) {
            Log.e("NewCoverExtractor", "Failed to extract cover from file directly", e)
            null
        }
    }

    /**
     * Extracts cover image from FB2 content (xml/string), decodes the base64 data,
     * and saves it to internal files directory so it persists and is fast to load.
     */
    fun extractAndSaveCover(fb2Content: String, sha1: String, context: Context?): String? {
        if (context == null || fb2Content.isBlank()) return null
        try {
            // First try to find coverpage image ID reference in <coverpage> tag
            var targetCoverId: String? = null
            val coverpageStart = fb2Content.indexOf("<coverpage", ignoreCase = true)
            if (coverpageStart != -1) {
                val coverpageEnd = fb2Content.indexOf("</coverpage>", coverpageStart, ignoreCase = true)
                if (coverpageEnd != -1) {
                    val coverpageXml = fb2Content.substring(coverpageStart, coverpageEnd + "</coverpage>".length)
                    val hrefIndex = coverpageXml.indexOf("href", ignoreCase = true)
                    if (hrefIndex != -1) {
                        val equalsIndex = coverpageXml.indexOf("=", hrefIndex)
                        if (equalsIndex != -1) {
                            val sub = coverpageXml.substring(equalsIndex + 1).trimStart()
                            val quoteChar = sub.getOrNull(0)
                            if (quoteChar == '"' || quoteChar == '\'') {
                                val endQuote = sub.indexOf(quoteChar, 1)
                                if (endQuote != -1) {
                                    targetCoverId = sub.substring(1, endQuote).removePrefix("#").trim()
                                }
                            }
                        }
                    }
                }
            }

            // Fast non-regex search for <binary> blocks
            var base64Data: String? = null
            var fallbackFirstBinary: String? = null
            var searchStart = 0

            while (true) {
                val start = fb2Content.indexOf("<binary", searchStart, ignoreCase = true)
                if (start == -1) break
                val end = fb2Content.indexOf("</binary>", start, ignoreCase = true)
                if (end == -1) break
                
                val block = fb2Content.substring(start, end + "</binary>".length)
                val contentStart = block.indexOf(">")
                if (contentStart != -1) {
                    val content = block.substring(contentStart + 1, block.length - "</binary>".length).trim()
                    if (content.isNotEmpty()) {
                        val header = block.substring(0, contentStart)
                        
                        // Exact match with targetCoverId from coverpage
                        if (!targetCoverId.isNullOrBlank() && header.contains(targetCoverId, ignoreCase = true)) {
                            base64Data = content
                            break
                        }
                        
                        // Check if header contains cover/thumb/front/image keywords
                        val isCoverKeyword = header.contains("cover", ignoreCase = true) ||
                                           header.contains("thumb", ignoreCase = true) ||
                                           header.contains("front", ignoreCase = true) ||
                                           header.contains("image", ignoreCase = true)
                        
                        if (isCoverKeyword && base64Data == null) {
                            base64Data = content
                        } else if (fallbackFirstBinary == null) {
                            fallbackFirstBinary = content
                        }
                    }
                }
                searchStart = end + "</binary>".length
            }

            val finalBase64 = base64Data ?: fallbackFirstBinary

            if (finalBase64.isNullOrBlank()) {
                Log.d("NewCoverExtractor", "No binary cover data found for book SHA1: $sha1")
                return null
            }

            val cleanBase64 = finalBase64.replace("\\s".toRegex(), "")
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            if (imageBytes.isEmpty()) {
                Log.w("NewCoverExtractor", "Decoded image bytes are empty for book SHA1: $sha1")
                return null
            }

            // Ensure the directory exists
            val coverDir = File(context.filesDir, "covers")
            if (!coverDir.exists()) {
                coverDir.mkdirs()
            }

            val coverFile = File(coverDir, "cover_$sha1.jpg")
            FileOutputStream(coverFile).use { fos ->
                fos.write(imageBytes)
            }
            
            Log.d("NewCoverExtractor", "Cover successfully saved to: ${coverFile.absolutePath}")
            return coverFile.absolutePath
        } catch (e: OutOfMemoryError) {
            Log.e("NewCoverExtractor", "OutOfMemoryError while extracting cover for $sha1", e)
            System.gc()
            return null
        } catch (e: Exception) {
            Log.e("NewCoverExtractor", "Error extracting cover for $sha1", e)
            return null
        }
    }

    fun saveCoverBytes(imageBytes: ByteArray, sha1: String, context: Context?): String? {
        if (context == null) return null
        try {
            if (imageBytes.isEmpty()) return null
            
            val coverDir = File(context.filesDir, "covers")
            if (!coverDir.exists()) coverDir.mkdirs()
            
            val coverFile = File(coverDir, "cover_$sha1.jpg")
            FileOutputStream(coverFile).use { fos ->
                fos.write(imageBytes)
            }
            return coverFile.absolutePath
        } catch (e: Exception) {
            Log.e("NewCoverExtractor", "Error saving cover bytes for $sha1", e)
            return null
        }
    }

    /**
     * Fast non-regex removal of binary sections from FB2 content.
     */
    fun stripBinarySections(content: String): String {
        val sb = java.lang.StringBuilder()
        var lastIdx = 0
        while (true) {
            val start = content.indexOf("<binary", lastIdx, ignoreCase = true)
            if (start == -1) {
                sb.append(content.substring(lastIdx))
                break
            }
            sb.append(content.substring(lastIdx, start))
            val end = content.indexOf("</binary>", start, ignoreCase = true)
            if (end == -1) {
                break
            }
            lastIdx = end + "</binary>".length
        }
        return sb.toString()
    }
}
