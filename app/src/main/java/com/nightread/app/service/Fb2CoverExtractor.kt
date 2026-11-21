package com.nightread.app.service

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * Fault-tolerant streaming FB2 cover extractor.
 * Does NOT use strict XmlPullParser so malformed XML entities in <body> never abort extraction,
 * supports any file size with minimal memory, handles l:href / xlink:href / href / any namespace,
 * handles UTF-8 / Windows-1251 / KOI8-R / UTF-16, and falls back to best image <binary>.
 */
object Fb2CoverExtractor {
    private const val TAG = "Fb2CoverExtractor"
    private const val CHUNK_SIZE = 32 * 1024
    private const val MAX_BINARY_BASE64_CHARS = 12 * 1024 * 1024 // ~9 MB image

    private val COVER_HREF_REGEX = Regex(
        """(?:[a-zA-Z0-9_-]+:)?href\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val ID_ATTR_REGEX = Regex(
        """\bid\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val CONTENT_TYPE_REGEX = Regex(
        """content-type\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val ENCODING_REGEX = Regex(
        """encoding\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    fun extractFromFile(file: File, sha1: String, context: Context): String? {
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        return try {
            file.inputStream().buffered(CHUNK_SIZE).use { input ->
                extract(input, sha1, context)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "extractFromFile failed for ${file.name}: ${e.message}")
            null
        }
    }

    fun extract(inputStream: InputStream, sha1: String, context: Context): String? {
        return try {
            val buffered = inputStream.buffered(CHUNK_SIZE)
            buffered.mark(4)
            val bom = ByteArray(4)
            val bomRead = buffered.read(bom)
            buffered.reset()
            if (bomRead <= 0) return null

            val isUtf16Le = bomRead >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte()
            val isUtf16Be = bomRead >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte()
            val streamCharset = when {
                isUtf16Le -> Charsets.UTF_16LE
                isUtf16Be -> Charsets.UTF_16BE
                else -> Charsets.ISO_8859_1 // 1:1 byte-to-char preserves raw bytes and never throws
            }

            var declaredCharset: Charset? = null
            var targetCoverIdRaw: String? = null
            var targetCoverIdDecoded: String? = null
            var coverpageSearched = false
            var inCoverpage = false
            val coverpageBuffer = StringBuilder()

            var inBinaryHeader = false
            val binaryHeaderBuf = StringBuilder()
            var inBinaryBody = false
            var currentBinaryScore = -1
            var currentBinaryBuf: StringBuilder? = null

            var bestFallbackScore = -1
            var bestFallbackBytes: ByteArray? = null

            val reader = buffered.reader(streamCharset)
            val charBuf = CharArray(CHUNK_SIZE)
            var tail = ""

            while (true) {
                val read = reader.read(charBuf)
                if (read == -1) break

                val data = if (tail.isNotEmpty()) tail + String(charBuf, 0, read) else String(charBuf, 0, read)
                tail = ""

                // Detect XML encoding from initial header if single-byte
                if (declaredCharset == null && streamCharset == Charsets.ISO_8859_1) {
                    val xmlHead = data.take(512)
                    val encMatch = ENCODING_REGEX.find(xmlHead)
                    declaredCharset = try {
                        if (encMatch != null) Charset.forName(encMatch.groupValues[1].trim()) else Charsets.UTF_8
                    } catch (_: Throwable) {
                        Charsets.UTF_8
                    }
                }

                var pos = 0
                val len = data.length

                while (pos < len) {
                    if (inBinaryBody) {
                        val closeIdx = data.indexOf("</binary", pos, ignoreCase = true)
                        if (closeIdx != -1) {
                            val endTagClose = data.indexOf('>', closeIdx)
                            if (endTagClose == -1) {
                                // Tag split across chunk boundary
                                appendIfWithinLimit(currentBinaryBuf, data, pos, closeIdx)
                                tail = data.substring(closeIdx)
                                break
                            }
                            appendIfWithinLimit(currentBinaryBuf, data, pos, closeIdx)
                            inBinaryBody = false

                            val base64Str = currentBinaryBuf?.toString()
                            currentBinaryBuf = null
                            if (!base64Str.isNullOrBlank()) {
                                val decoded = decodeValidImage(base64Str)
                                if (decoded != null) {
                                    if (currentBinaryScore >= 1000) {
                                        val saved = NewCoverExtractor.saveCoverBytes(decoded, sha1, context)
                                        if (saved != null) return saved
                                    } else if (currentBinaryScore > bestFallbackScore) {
                                        bestFallbackScore = currentBinaryScore
                                        bestFallbackBytes = decoded
                                    }
                                }
                            }
                            pos = endTagClose + 1
                            continue
                        } else {
                            // Keep last 16 chars as tail in case "</binary>" is split
                            val safeEnd = (len - 16).coerceAtLeast(pos)
                            appendIfWithinLimit(currentBinaryBuf, data, pos, safeEnd)
                            tail = data.substring(safeEnd)
                            break
                        }
                    }

                    if (inBinaryHeader) {
                        val gtIdx = data.indexOf('>', pos)
                        if (gtIdx == -1) {
                            if (binaryHeaderBuf.length < 4096) {
                                binaryHeaderBuf.append(data, pos, len)
                            }
                            break
                        }
                        if (binaryHeaderBuf.length < 4096) {
                            binaryHeaderBuf.append(data, pos, gtIdx)
                        }
                        inBinaryHeader = false
                        val headerText = binaryHeaderBuf.toString()
                        binaryHeaderBuf.clear()

                        // Check if self-closing <binary .../>
                        if (headerText.trimEnd().endsWith("/")) {
                            pos = gtIdx + 1
                            continue
                        }

                        val score = scoreBinaryHeader(
                            headerText = headerText,
                            targetRaw = targetCoverIdRaw,
                            targetDecoded = targetCoverIdDecoded,
                            streamCharset = streamCharset,
                            declaredCharset = declaredCharset
                        )

                        inBinaryBody = true
                        currentBinaryScore = score
                        currentBinaryBuf = if (score >= 1000 || score > bestFallbackScore) {
                            StringBuilder(64 * 1024)
                        } else {
                            null // skip reading body bytes into memory
                        }
                        pos = gtIdx + 1
                        continue
                    }

                    // Search for <coverpage (only until we find it) and <binary
                    val nextCoverIdx = if (!coverpageSearched && !inCoverpage) {
                        data.indexOf("<coverpage", pos, ignoreCase = true)
                    } else -1

                    if (inCoverpage) {
                        val closeCoverIdx = data.indexOf("</coverpage", pos, ignoreCase = true)
                        if (closeCoverIdx != -1) {
                            val endGt = data.indexOf('>', closeCoverIdx)
                            if (endGt == -1) {
                                if (coverpageBuffer.length < 16384) {
                                    coverpageBuffer.append(data, pos, closeCoverIdx)
                                }
                                tail = data.substring(closeCoverIdx)
                                break
                            }
                            if (coverpageBuffer.length < 16384) {
                                coverpageBuffer.append(data, pos, closeCoverIdx)
                            }
                            inCoverpage = false
                            coverpageSearched = true
                            extractCoverIdFromXml(coverpageBuffer.toString(), streamCharset, declaredCharset)?.let { (raw, dec) ->
                                targetCoverIdRaw = raw
                                targetCoverIdDecoded = dec
                            }
                            coverpageBuffer.clear()
                            pos = endGt + 1
                            continue
                        } else {
                            val safeEnd = (len - 16).coerceAtLeast(pos)
                            if (coverpageBuffer.length < 16384) {
                                coverpageBuffer.append(data, pos, safeEnd)
                            }
                            tail = data.substring(safeEnd)
                            break
                        }
                    }

                    val nextBinaryIdx = data.indexOf("<binary", pos, ignoreCase = true)

                    if (nextCoverIdx != -1 && (nextBinaryIdx == -1 || nextCoverIdx < nextBinaryIdx)) {
                        inCoverpage = true
                        coverpageBuffer.clear()
                        pos = nextCoverIdx + "<coverpage".length
                        continue
                    }

                    if (nextBinaryIdx != -1) {
                        // Ensure it's "<binary" followed by whitespace or '>'
                        val afterTag = nextBinaryIdx + "<binary".length
                        if (afterTag >= len) {
                            tail = data.substring(nextBinaryIdx)
                            break
                        }
                        val nextCh = data[afterTag]
                        if (nextCh.isWhitespace() || nextCh == '>') {
                            inBinaryHeader = true
                            binaryHeaderBuf.clear()
                            pos = afterTag
                        } else {
                            pos = afterTag
                        }
                        continue
                    }

                    // Neither found in remaining chunk: keep last 16 chars in case "<coverpage" or "<binary" is split
                    val keepFrom = (len - 16).coerceAtLeast(pos)
                    tail = data.substring(keepFrom)
                    break
                }
            }

            // If we didn't hit an exact coverpage match, use the best fallback image found
            if (bestFallbackBytes != null) {
                return NewCoverExtractor.saveCoverBytes(bestFallbackBytes!!, sha1, context)
            }

            null
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM extracting FB2 cover for $sha1")
            System.gc()
            null
        } catch (e: Throwable) {
            Log.w(TAG, "Error extracting FB2 cover for $sha1: ${e.message}")
            null
        }
    }

    private fun appendIfWithinLimit(sb: StringBuilder?, data: String, start: Int, end: Int) {
        if (sb == null || end <= start) return
        val remaining = MAX_BINARY_BASE64_CHARS - sb.length
        if (remaining <= 0) return
        val actualEnd = (start + remaining).coerceAtMost(end)
        sb.append(data, start, actualEnd)
    }

    private fun extractCoverIdFromXml(
        coverpageXml: String,
        streamCharset: Charset,
        declaredCharset: Charset?
    ): Pair<String, String>? {
        val match = COVER_HREF_REGEX.find(coverpageXml) ?: return null
        val rawHref = match.groupValues[1].trim().removePrefix("#").trim()
        if (rawHref.isEmpty()) return null
        val decodedHref = normalizeId(rawHref, streamCharset, declaredCharset)
        return rawHref to decodedHref
    }

    private fun normalizeId(raw: String, streamCharset: Charset, declaredCharset: Charset?): String {
        val reencoded = if (streamCharset == Charsets.ISO_8859_1 && declaredCharset != null && declaredCharset != Charsets.ISO_8859_1) {
            try {
                String(raw.toByteArray(Charsets.ISO_8859_1), declaredCharset)
            } catch (_: Throwable) {
                raw
            }
        } else {
            raw
        }
        return try {
            URLDecoder.decode(reencoded, "UTF-8").trim()
        } catch (_: Throwable) {
            reencoded.trim()
        }
    }

    private fun scoreBinaryHeader(
        headerText: String,
        targetRaw: String?,
        targetDecoded: String?,
        streamCharset: Charset,
        declaredCharset: Charset?
    ): Int {
        val idRaw = ID_ATTR_REGEX.find(headerText)?.groupValues?.get(1)?.trim() ?: ""
        val idDecoded = if (idRaw.isNotEmpty()) normalizeId(idRaw, streamCharset, declaredCharset) else ""
        val contentType = CONTENT_TYPE_REGEX.find(headerText)?.groupValues?.get(1)?.trim()?.lowercase() ?: ""

        if (!targetRaw.isNullOrEmpty() && idRaw.isNotEmpty()) {
            if (idRaw.equals(targetRaw, ignoreCase = true) ||
                idDecoded.equals(targetDecoded, ignoreCase = true) ||
                idRaw.equals(targetDecoded, ignoreCase = true) ||
                idDecoded.equals(targetRaw, ignoreCase = true)
            ) {
                return 1000
            }
        }

        val lowerId = idDecoded.lowercase()
        val isImageType = contentType.startsWith("image/") ||
            lowerId.endsWith(".jpg") || lowerId.endsWith(".jpeg") ||
            lowerId.endsWith(".png") || lowerId.endsWith(".webp") ||
            lowerId.endsWith(".gif") || lowerId.endsWith(".bmp")

        if (contentType.isNotEmpty() && !contentType.startsWith("image/") && !isImageType) {
            return -1 // Skip fonts or non-image binaries
        }

        var score = if (isImageType) 50 else 10
        if (lowerId.contains("cover") || lowerId.contains("обложк")) score += 200
        else if (lowerId.contains("front") || lowerId.contains("jacket") || lowerId.contains("title")) score += 140
        else if (lowerId.contains("thumb")) score += 80

        return score
    }

    private fun decodeValidImage(base64Raw: String): ByteArray? {
        return try {
            // Filter out any non-base64 whitespace/control chars cleanly
            val cleaned = base64Raw.trim()
            if (cleaned.length < 32) return null

            val bytes = Base64.decode(cleaned, Base64.DEFAULT)
            if (bytes.size < 100) return null

            // Verify image header / dimensions without allocating full bitmap
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return null
            }
            bytes
        } catch (_: Throwable) {
            null
        }
    }
}
