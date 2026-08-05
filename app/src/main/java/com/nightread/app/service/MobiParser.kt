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

        var headerFullName: String? = null
        var exthTitle: String? = null
        var extractedAuthor: String? = null
        var extractedDescription: String? = null
        var coverImageBytes: ByteArray? = null
        var coverRecordIndex: Int = -1

        var mobiTextEncoding = 65001 // Default UTF-8
        var extraRecordDataFlags = 0

        val mobiMagicOffset = rec0Offset + 16
        if (mobiMagicOffset + 12 <= data.size) {
            val magic = String(data, mobiMagicOffset, 4, Charsets.US_ASCII)
            if (magic == "MOBI") {
                val mobiHeaderLen = readUInt32(data, mobiMagicOffset + 4).toInt()

                // Read text encoding
                if (mobiMagicOffset + 32 <= data.size) {
                    val enc = readUInt32(data, mobiMagicOffset + 28).toInt()
                    if (enc != 0) mobiTextEncoding = enc
                }

                // Read extra record data flags (at mobiMagicOffset + 242)
                if (mobiHeaderLen >= 228 && mobiMagicOffset + 244 <= data.size) {
                    extraRecordDataFlags = readUInt16(data, mobiMagicOffset + 242)
                }
                
                // Read full name offset/length from MOBI header
                if (mobiMagicOffset + 88 <= data.size) {
                    val fullNameOffset = readUInt32(data, mobiMagicOffset + 84).toInt()
                    val fullNameLength = readUInt32(data, mobiMagicOffset + 88).toInt()
                    val nameAbsOffset = rec0Offset + fullNameOffset
                    if (fullNameOffset > 0 && fullNameLength > 0 && nameAbsOffset + fullNameLength <= data.size) {
                        headerFullName = decodeString(data, nameAbsOffset, fullNameLength, mobiTextEncoding).trim()
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
                                    extractedAuthor = decodeString(data, valOffset, valDataLen, mobiTextEncoding).trim()
                                }
                                503 -> { // Updated Title
                                    val t = decodeString(data, valOffset, valDataLen, mobiTextEncoding).trim()
                                    if (t.isNotBlank()) exthTitle = t
                                }
                                103 -> { // Description / Annotation
                                    extractedDescription = decodeString(data, valOffset, valDataLen, mobiTextEncoding).trim()
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
                    if (size in 512..(2 * 1024 * 1024)) {
                        val candidateBytes = data.copyOfRange(cStart, cEnd)
                        if (isImage(candidateBytes)) {
                            coverImageBytes = candidateBytes
                            break
                        }
                    }
                }
            }
        }

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
            var decompressedChunk = when (compression) {
                1 -> chunk
                2 -> decompressPalmDoc(chunk)
                else -> chunk
            }

            // Trim MOBI trailing extra data entries (line offsets, multibyte overlap)
            if (extraRecordDataFlags != 0 && decompressedChunk.isNotEmpty()) {
                decompressedChunk = trimExtraRecordData(decompressedChunk, extraRecordDataFlags)
            }

            textBytesStream.write(decompressedChunk)
        }

        val decompressedBytes = textBytesStream.toByteArray()
        var textContent = decodeBytes(decompressedBytes, mobiTextEncoding)

        // Clean up control characters and replacement chars
        textContent = textContent
            .replace("\u0000", "")
            .replace("\uFFFD", "")
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            .replace(Regex("<mbp:pagebreak[^>]*/>", RegexOption.IGNORE_CASE), "<br/>")

        // Look for Title inside text tags if metadata titles look like slugs/filenames
        var textTitle: String? = null
        val titleMatch = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(textContent)
            ?: Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.IGNORE_CASE).find(textContent)
        if (titleMatch != null) {
            val candidate = unescapeHtml(titleMatch.groupValues[1].trim())
            if (candidate.isNotBlank() && !isSlugTitle(candidate)) {
                textTitle = candidate
            }
        }

        // Also look for Author inside text if missing
        if (extractedAuthor.isNullOrBlank() || extractedAuthor == "Неизвестен") {
            val creatorMatch = Regex("<dc:creator[^>]*>(.*?)</dc:creator>", RegexOption.IGNORE_CASE).find(textContent)
                ?: Regex("<p[^>]*class=\"[^\"]*author[^\"]*\"[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE).find(textContent)
            if (creatorMatch != null) {
                val candidate = stripTags(unescapeHtml(creatorMatch.groupValues[1].trim()))
                if (candidate.isNotBlank()) {
                    extractedAuthor = candidate
                }
            }
        }

        // Also look for Description/Annotation inside text if missing
        if (extractedDescription.isNullOrBlank()) {
            val descMatch = Regex("<dc:description[^>]*>(.*?)</dc:description>", RegexOption.IGNORE_CASE).find(textContent)
                ?: Regex("<div[^>]*class=\"[^\"]*annotation[^\"]*\"[^>]*>(.*?)</div>", RegexOption.IGNORE_CASE).find(textContent)
            if (descMatch != null) {
                val candidate = stripTags(unescapeHtml(descMatch.groupValues[1].trim()))
                if (candidate.isNotBlank()) {
                    extractedDescription = candidate
                }
            }
        }

        // Final title determination prioritizing clean human-readable titles over slugs
        val finalTitle = when {
            !headerFullName.isNullOrBlank() && !isSlugTitle(headerFullName) -> headerFullName
            !textTitle.isNullOrBlank() -> textTitle
            !exthTitle.isNullOrBlank() && !isSlugTitle(exthTitle) -> exthTitle
            !headerFullName.isNullOrBlank() -> headerFullName
            !exthTitle.isNullOrBlank() -> exthTitle
            else -> fallbackTitle
        }

        val finalAuthor = extractedAuthor.takeIf { !it.isNullOrBlank() } ?: "Неизвестен"

        val isHtml = textContent.contains(Regex("<(?i)[a-z]+[>\\s]"))

        val formattedContent = if (isHtml) {
            textContent
        } else {
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

    private fun trimExtraRecordData(buf: ByteArray, flags: Int): ByteArray {
        var current = buf

        // Process trailing entry sizes (bits 1..15)
        for (b in 1..15) {
            if ((flags and (1 shl b)) != 0) {
                if (current.isEmpty()) break
                var bitpos = 0
                var extraLen = 0
                var curSize = current.size
                while (curSize > 0) {
                    val v = current[curSize - 1].toInt() and 0xFF
                    extraLen = extraLen or ((v and 0x7F) shl bitpos)
                    bitpos += 7
                    curSize--
                    if ((v and 0x80) != 0 || curSize == 0 || bitpos >= 28) break
                }
                if (extraLen in 1..current.size) {
                    current = current.copyOfRange(0, current.size - extraLen)
                }
            }
        }

        // Process bit 0: extra multibyte bytes overlap
        if ((flags and 1) != 0 && current.isNotEmpty()) {
            val extraMB = (current.last().toInt() and 0x03) + 1
            if (extraMB in 1..current.size) {
                current = current.copyOfRange(0, current.size - extraMB)
            }
        }

        return current
    }

    private fun decodeString(data: ByteArray, offset: Int, length: Int, preferredEncoding: Int = 65001): String {
        return decodeBytes(data.copyOfRange(offset, offset + length), preferredEncoding)
    }

    private fun decodeBytes(bytes: ByteArray, preferredEncoding: Int = 65001): String {
        if (bytes.isEmpty()) return ""

        if (preferredEncoding == 65001) {
            try {
                val utf8 = String(bytes, Charsets.UTF_8)
                val replacementCount = utf8.count { it == '\uFFFD' }
                if (replacementCount <= bytes.size / 20) {
                    return utf8
                }
            } catch (_: Exception) {}
        }

        try {
            val win1251 = String(bytes, Charset.forName("windows-1251"))
            if (win1251.any { it in '\u0400'..'\u04FF' } || preferredEncoding == 1252) {
                return win1251
            }
        } catch (_: Exception) {}

        return try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            String(bytes, Charset.defaultCharset())
        }
    }

    private fun isSlugTitle(title: String): Boolean {
        val t = title.trim()
        if (t.matches(Regex("^[0-9]{4,}-[a-zA-Z0-9_-]+$"))) return true
        if (t.matches(Regex("^[a-zA-Z0-9_-]+$")) && (t.contains("-") || t.contains("_")) && !t.contains(" ")) return true
        return false
    }

    private fun isImage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8 && (bytes[2].toInt() and 0xFF) == 0xFF) return true
        if ((bytes[0].toInt() and 0xFF) == 0x89 && bytes[1] == 'P'.toByte() && bytes[2] == 'N'.toByte() && bytes[3] == 'G'.toByte()) return true
        return false
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    private fun stripTags(text: String): String {
        return text.replace(Regex("<[^>]*>"), "").trim()
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
