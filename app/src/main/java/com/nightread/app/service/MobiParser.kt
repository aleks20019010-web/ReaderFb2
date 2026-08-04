package com.nightread.app.service

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

object MobiParser : BookParser {
    private const val TAG = "MobiParser"

    override fun parse(file: File, defaultTitle: String): BookParser.ParsedBook {
        try {
            if (!file.exists() || !file.canRead() || file.length() < 78) {
                return BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
            }
            val data = file.readBytes()
            return parseBytes(data, file.nameWithoutExtension.ifBlank { defaultTitle })
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MOBI/AZW file: ${file.absolutePath}", e)
            return BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
        }
    }

    fun parseBytes(data: ByteArray, fallbackTitle: String): BookParser.ParsedBook {
        if (data.size < 78) {
            return BookParser.ParsedBook(fallbackTitle, "Неизвестен", "")
        }

        val numRecords = readUInt16(data, 76)
        if (numRecords < 1) {
            return BookParser.ParsedBook(fallbackTitle, "Неизвестен", "")
        }

        val rec0Offset = readUInt32(data, 78).toInt()
        if (rec0Offset < 78 || rec0Offset + 16 > data.size) {
            return BookParser.ParsedBook(fallbackTitle, "Неизвестен", "")
        }

        val compression = readUInt16(data, rec0Offset)
        val textLength = readUInt32(data, rec0Offset + 4).toInt()
        val recordCount = readUInt16(data, rec0Offset + 8)

        var extractedTitle: String? = null
        var extractedAuthor: String? = null
        var extractedDescription: String? = null
        var coverImageBytes: ByteArray? = null
        var coverRecordIndex: Int = -1

        val mobiMagicOffset = rec0Offset + 16
        if (mobiMagicOffset + 12 <= data.size) {
            val magic = String(data, mobiMagicOffset, 4, Charsets.US_ASCII)
            if (magic == "MOBI") {
                val mobiHeaderLen = readUInt32(data, mobiMagicOffset + 4).toInt()
                
                // Read full name offset/length
                if (mobiMagicOffset + 88 <= data.size) {
                    val fullNameOffset = readUInt32(data, mobiMagicOffset + 84).toInt()
                    val fullNameLength = readUInt32(data, mobiMagicOffset + 88).toInt()
                    val nameAbsOffset = rec0Offset + fullNameOffset
                    if (fullNameOffset > 0 && fullNameLength > 0 && nameAbsOffset + fullNameLength <= data.size) {
                        extractedTitle = String(data, nameAbsOffset, fullNameLength, Charsets.UTF_8).trim()
                    }
                }

                // Check EXTH header if flags permit
                val exthFlagsOffset = mobiMagicOffset + 112
                val hasExth = if (exthFlagsOffset + 4 <= data.size) {
                    (readUInt32(data, exthFlagsOffset).toInt() and 0x40) != 0
                } else false

                val exthOffset = mobiMagicOffset + mobiHeaderLen
                if (hasExth && exthOffset + 12 <= data.size) {
                    val exthMagic = String(data, exthOffset, 4, Charsets.US_ASCII)
                    if (exthMagic == "EXTH") {
                        val exthCount = readUInt32(data, exthOffset + 8).toInt()
                        var curr = exthOffset + 12
                        for (i in 0 until exthCount) {
                            if (curr + 8 > data.size) break
                            val recType = readUInt32(data, curr).toInt()
                            val recLen = readUInt32(data, curr + 4).toInt()
                            if (recLen < 8 || curr + recLen > data.size) break

                            val valDataLen = recLen - 8
                            val valOffset = curr + 8
                            when (recType) {
                                100 -> { // Author
                                    extractedAuthor = String(data, valOffset, valDataLen, Charsets.UTF_8).trim()
                                }
                                503 -> { // Updated Title
                                    val t = String(data, valOffset, valDataLen, Charsets.UTF_8).trim()
                                    if (t.isNotBlank()) extractedTitle = t
                                }
                                103 -> { // Description / Annotation
                                    extractedDescription = String(data, valOffset, valDataLen, Charsets.UTF_8).trim()
                                }
                                201 -> { // Cover record index
                                    if (valDataLen >= 4) {
                                        coverRecordIndex = readUInt32(data, valOffset).toInt()
                                    }
                                }
                            }
                            curr += recLen
                        }
                    }
                }
            }
        }

        // Try extracting cover image if index found
        val candidateIndices = mutableListOf<Int>()
        if (coverRecordIndex in 0 until numRecords) {
            candidateIndices.add(coverRecordIndex)
            // also try relative to firstImageIndex
            var firstImageIndex = -1
            if (mobiMagicOffset + 112 <= data.size) {
                firstImageIndex = readUInt32(data, mobiMagicOffset + 108).toInt()
                if (firstImageIndex in 0 until numRecords) {
                    candidateIndices.add(firstImageIndex + coverRecordIndex)
                }
            }
        }

        for (idx in candidateIndices) {
            if (idx in 0 until numRecords) {
                val cStart = readUInt32(data, 78 + idx * 8).toInt()
                val cEnd = if (idx + 1 < numRecords) readUInt32(data, 78 + (idx + 1) * 8).toInt() else data.size
                if (cStart in 0 until data.size && cEnd in (cStart + 1)..data.size) {
                    val candidateBytes = data.copyOfRange(cStart, cEnd)
                    if (isImage(candidateBytes)) {
                        coverImageBytes = candidateBytes
                        break
                    }
                }
            }
        }

        // Fallback: scan starting from firstImageIndex to find the first valid image record
        if (coverImageBytes == null) {
            var startScanIdx = 1
            if (mobiMagicOffset + 112 <= data.size) {
                val firstImg = readUInt32(data, mobiMagicOffset + 108).toInt()
                if (firstImg in 1 until numRecords) {
                    startScanIdx = firstImg
                }
            }
            for (idx in startScanIdx until numRecords) {
                val cStart = readUInt32(data, 78 + idx * 8).toInt()
                val cEnd = if (idx + 1 < numRecords) readUInt32(data, 78 + (idx + 1) * 8).toInt() else data.size
                if (cStart in 0 until data.size && cEnd in (cStart + 1)..data.size) {
                    val size = cEnd - cStart
                    if (size in 512..(2 * 1024 * 1024)) { // 512B to 2MB is normal for cover image
                        val candidateBytes = data.copyOfRange(cStart, cEnd)
                        if (isImage(candidateBytes)) {
                            coverImageBytes = candidateBytes
                            break
                        }
                    }
                }
            }
        }

        val finalTitle = extractedTitle.takeIf { !it.isNullOrBlank() } ?: fallbackTitle
        val finalAuthor = extractedAuthor.takeIf { !it.isNullOrBlank() } ?: "Неизвестен"

        // Extract and decompress text records
        val textBytesStream = ByteArrayOutputStream()
        val numTextRecords = minOf(recordCount, numRecords - 1)

        for (r in 1..numTextRecords) {
            val recStart = readUInt32(data, 78 + r * 8).toInt()
            val recEnd = if (r + 1 < numRecords) readUInt32(data, 78 + (r + 1) * 8).toInt() else data.size

            if (recStart !in 0 until data.size || recEnd !in (recStart + 1)..data.size) {
                continue
            }

            val chunk = data.copyOfRange(recStart, recEnd)
            when (compression) {
                1 -> textBytesStream.write(chunk)
                2 -> {
                    val decompressed = decompressPalmDoc(chunk)
                    textBytesStream.write(decompressed)
                }
                else -> {
                    // Fallback to raw bytes if unknown compression
                    textBytesStream.write(chunk)
                }
            }
        }

        val decompressedBytes = textBytesStream.toByteArray()
        val textContent = decodeText(decompressedBytes)

        val formattedContent = if (textContent.contains("<html", ignoreCase = true) || 
                                    textContent.contains("<p>", ignoreCase = true) || 
                                    textContent.contains("<div>", ignoreCase = true)) {
            textContent
        } else {
            // Convert plain text to HTML paragraphs
            val paragraphs = textContent.split(Regex("\\r?\\n\\s*\\r?\\n|\\r?\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (paragraphs.isNotEmpty()) {
                paragraphs.joinToString("\n") { "<p>${escapeHtml(it)}</p>" }
            } else {
                textContent
            }
        }

        return BookParser.ParsedBook(
            title = finalTitle,
            author = finalAuthor,
            content = formattedContent,
            coverBytes = coverImageBytes,
            annotation = extractedDescription
        )
    }

    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        val len = data.size
        while (i < len) {
            val b = data[i].toInt() and 0xFF
            i++
            when {
                b == 0 -> out.write(0)
                b in 1..8 -> {
                    val count = minOf(b, len - i)
                    out.write(data, i, count)
                    i += count
                }
                b <= 0x7F -> out.write(b)
                b >= 0xC0 -> {
                    out.write(' '.code)
                    out.write(b xor 0x80)
                }
                else -> { // 0x80..0xBF
                    if (i < len) {
                        val b2 = data[i].toInt() and 0xFF
                        i++
                        val distance = ((b and 0x3F) shl 5) or (b2 shr 3)
                        val length = (b2 and 0x07) + 3
                        val currentBuf = out.toByteArray()
                        val start = currentBuf.size - distance
                        if (start >= 0) {
                            for (k in 0 until length) {
                                out.write(currentBuf[(start + k) % currentBuf.size].toInt() and 0xFF)
                            }
                        }
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private fun decodeText(bytes: ByteArray): String {
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(bytes, Charset.forName("windows-1251"))
            } catch (ex: Exception) {
                String(bytes, Charset.defaultCharset())
            }
        }
    }

    private fun isImage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // JPEG magic FF D8 FF
        if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8 && (bytes[2].toInt() and 0xFF) == 0xFF) return true
        // PNG magic 89 50 4E 47
        if ((bytes[0].toInt() and 0xFF) == 0x89 && bytes[1] == 'P'.toByte() && bytes[2] == 'N'.toByte() && bytes[3] == 'G'.toByte()) return true
        return false
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int {
        if (offset + 1 >= data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readUInt32(data: ByteArray, offset: Int): Long {
        if (offset + 3 >= data.size) return 0L
        return ((data[offset].toLong() and 0xFF) shl 24) or
                ((data[offset + 1].toLong() and 0xFF) shl 16) or
                ((data[offset + 2].toLong() and 0xFF) shl 8) or
                (data[offset + 3].toLong() and 0xFF)
    }
}
