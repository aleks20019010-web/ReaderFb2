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
        if (context == null) return null
        try {
            // Use fast non-regex substring search to prevent OutOfMemory and regex backtracking
            var base64Data: String? = null
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
                        // Check if this block is the cover (has id containing "cover" or "thumb")
                        val header = block.substring(0, contentStart)
                        val isCoverBlock = header.contains("cover", ignoreCase = true) ||
                                           header.contains("thumb", ignoreCase = true) ||
                                           header.contains("image", ignoreCase = true)
                        if (isCoverBlock) {
                            base64Data = content
                            break // Found the best cover block, stop searching!
                        } else if (base64Data == null) {
                            base64Data = content // Fallback to first binary block found
                        }
                    }
                }
                searchStart = end + "</binary>".length
            }

            if (base64Data.isNullOrBlank()) {
                Log.d("NewCoverExtractor", "No binary cover data found for book SHA1: $sha1")
                return null
            }

            // Clean up any potential spaces/newlines in base64 block
            val cleanBase64 = base64Data.replace(Regex("\\s+"), "")

            // Decode base64
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
