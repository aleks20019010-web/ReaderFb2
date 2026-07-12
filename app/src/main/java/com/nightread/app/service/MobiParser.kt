package com.nightread.app.service

import android.util.Log
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object MobiParser {
    private const val TAG = "MobiParser"

    data class MobiMetadata(
        val title: String,
        val author: String,
        val content: String,
        val language: String?,
        val annotation: String?,
        val coverBytes: ByteArray?
    )

    fun parseMobi(file: File, defaultTitle: String): MobiMetadata {
        try {
            val bytes = file.readBytes()
            if (bytes.size < 78) throw Exception("Invalid MOBI file: too short")

            // Read number of PDB records
            val numRecords = ((bytes[76].toInt() and 0xFF) shl 8) or (bytes[77].toInt() and 0xFF)
            val recordOffsets = IntArray(numRecords)
            for (k in 0 until numRecords) {
                val entryOffset = 78 + k * 8
                val offset = ((bytes[entryOffset].toInt() and 0xFF) shl 24) or
                             ((bytes[entryOffset + 1].toInt() and 0xFF) shl 16) or
                             ((bytes[entryOffset + 2].toInt() and 0xFF) shl 8) or
                             (bytes[entryOffset + 3].toInt() and 0xFF)
                recordOffsets[k] = offset
            }

            if (numRecords == 0) throw Exception("MOBI file has 0 records")

            // Record 0 is the MOBI Header Record
            val record0Start = recordOffsets[0]
            val record0End = if (numRecords > 1) recordOffsets[1] else bytes.size

            val compression = ((bytes[record0Start].toInt() and 0xFF) shl 8) or (bytes[record0Start + 1].toInt() and 0xFF)
            val textLength = ((bytes[record0Start + 4].toInt() and 0xFF) shl 24) or
                             ((bytes[record0Start + 5].toInt() and 0xFF) shl 16) or
                             ((bytes[record0Start + 6].toInt() and 0xFF) shl 8) or
                             (bytes[record0Start + 7].toInt() and 0xFF)
            val recordCount = ((bytes[record0Start + 8].toInt() and 0xFF) shl 8) or (bytes[record0Start + 9].toInt() and 0xFF)

            // Check MOBI identifier at record0Start + 16
            var hasMobiHeader = false
            var mobiHeaderLength = 0
            var textEncoding = 1252 // default CP1252
            var firstImageIndex = -1
            var exthFlags = 0
            var extraRecordDataFlags = 0
            var fullNameOffset = 0
            var fullNameLength = 0

            if (record0Start + 19 < record0End) {
                val id = String(bytes, record0Start + 16, 4, StandardCharsets.US_ASCII)
                if (id == "MOBI") {
                    hasMobiHeader = true
                    mobiHeaderLength = readIntBigEndian(bytes, record0Start + 20)
                    textEncoding = readIntBigEndian(bytes, record0Start + 28)
                    fullNameOffset = readIntBigEndian(bytes, record0Start + 84)
                    fullNameLength = readIntBigEndian(bytes, record0Start + 88)
                    firstImageIndex = readIntBigEndian(bytes, record0Start + 108)
                    exthFlags = readIntBigEndian(bytes, record0Start + 128)
                    if (record0Start + 243 < record0End) {
                        extraRecordDataFlags = ((bytes[record0Start + 242].toInt() and 0xFF) shl 8) or
                                               (bytes[record0Start + 243].toInt() and 0xFF)
                    }
                }
            }

            val charset = if (textEncoding == 65001) StandardCharsets.UTF_8 else Charset.forName("CP1252")

            var title = defaultTitle
            if (hasMobiHeader && fullNameLength > 0 && record0Start + fullNameOffset + fullNameLength <= record0End) {
                title = String(bytes, record0Start + fullNameOffset, fullNameLength, charset).trim()
            }

            var author = "Неизвестен"
            var annotation: String? = null
            var language: String? = "ru"
            var coverBytes: ByteArray? = null

            // EXTH parsing
            val hasExth = (exthFlags and 0x40) != 0
            var coverOffset = -1
            if (hasExth && hasMobiHeader) {
                val exthStart = record0Start + 16 + mobiHeaderLength
                if (exthStart + 11 < record0End && String(bytes, exthStart, 4, StandardCharsets.US_ASCII) == "EXTH") {
                    val exthLength = readIntBigEndian(bytes, exthStart + 4)
                    val exthRecordCount = readIntBigEndian(bytes, exthStart + 8)
                    var curr = exthStart + 12
                    for (r in 0 until exthRecordCount) {
                        if (curr + 7 >= record0End || curr + 7 >= exthStart + exthLength) break
                        val recType = readIntBigEndian(bytes, curr)
                        val recLen = readIntBigEndian(bytes, curr + 4)
                        if (recLen < 8 || curr + recLen > record0End || curr + recLen > exthStart + exthLength) break
                        val recDataStart = curr + 8
                        val recDataLen = recLen - 8
                        when (recType) {
                            100 -> author = String(bytes, recDataStart, recDataLen, charset).trim()
                            103 -> annotation = String(bytes, recDataStart, recDataLen, charset).trim()
                            150 -> language = String(bytes, recDataStart, recDataLen, charset).trim()
                            201 -> {
                                if (recDataLen >= 4) {
                                    coverOffset = readIntBigEndian(bytes, recDataStart)
                                }
                            }
                        }
                        curr += recLen
                    }
                }
            }

            // Extract cover image bytes
            if (firstImageIndex in 0 until numRecords && coverOffset >= 0) {
                val coverRecIdx = firstImageIndex + coverOffset
                if (coverRecIdx in 0 until numRecords) {
                    val start = recordOffsets[coverRecIdx]
                    val end = if (coverRecIdx + 1 < numRecords) recordOffsets[coverRecIdx + 1] else bytes.size
                    if (start < end) {
                        coverBytes = bytes.sliceArray(start until end)
                    }
                }
            }

            // Read and decompress text content
            val contentBuilder = StringBuilder()
            val limit = if (recordCount < numRecords) recordCount else numRecords - 1
            for (idx in 1..limit) {
                val start = recordOffsets[idx]
                val end = if (idx + 1 < numRecords) recordOffsets[idx + 1] else bytes.size
                if (start >= end) continue

                val recordData = bytes.sliceArray(start until end)
                val trailingBytes = getTrailingBytesCount(recordData, extraRecordDataFlags)
                val cleanBytes = if (trailingBytes in 1 until recordData.size) {
                    recordData.sliceArray(0 until (recordData.size - trailingBytes))
                } else {
                    recordData
                }

                val text = when (compression) {
                    2 -> {
                        val decompressed = decompressPalmDoc(cleanBytes)
                        String(decompressed, charset)
                    }
                    1 -> String(cleanBytes, charset)
                    else -> ""
                }
                contentBuilder.append(text)
            }

            // Simple HTML-to-text cleaning
            val rawContent = contentBuilder.toString()
            val cleanContent = parseHtmlToText(rawContent)

            return MobiMetadata(
                title = title.ifEmpty { defaultTitle },
                author = author.ifEmpty { "Неизвестен" },
                content = cleanContent,
                language = language,
                annotation = annotation,
                coverBytes = coverBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MOBI file: ${file.absolutePath}", e)
            return MobiMetadata(
                title = defaultTitle,
                author = "Неизвестен",
                content = "Не удалось открыть книгу MOBI: ${e.message}",
                language = "ru",
                annotation = null,
                coverBytes = null
            )
        }
    }

    private fun readIntBigEndian(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
               (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun getTrailingBytesCount(bytes: ByteArray, flags: Int): Int {
        if (bytes.isEmpty() || flags == 0) return 0
        var totalSize = 0
        var tempEnd = bytes.size

        for (i in 15 downTo 1) {
            if ((flags and (1 shl i)) != 0) {
                val (vlintVal, vlintSize) = readVLIntBackwards(bytes, tempEnd)
                if (vlintSize > 0) {
                    totalSize += vlintVal
                    tempEnd -= vlintSize
                }
            }
        }

        if ((flags and 1) != 0) {
            if (tempEnd > 0) {
                val lastByte = bytes[tempEnd - 1].toInt() and 0xFF
                val overlapSize = (lastByte and 3) + 1
                totalSize += overlapSize
            }
        }

        return totalSize
    }

    private fun readVLIntBackwards(bytes: ByteArray, endIndex: Int): Pair<Int, Int> {
        if (endIndex <= 0) return Pair(0, 0)
        var value = 0
        var size = 0
        for (i in 0 until 4) {
            val idx = endIndex - 1 - i
            if (idx < 0) break
            val b = bytes[idx].toInt() and 0xFF
            if (i == 0) {
                value = b and 0x7F
                size = 1
            } else {
                if ((b and 0x80) != 0) {
                    break
                }
                value = value or ((b and 0x7F) shl (7 * i))
                size = i + 1
            }
        }
        return Pair(value, size)
    }

    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        var outBytes = ByteArray(data.size * 2 + 4096)
        var outSize = 0

        fun ensureCapacity(extra: Int) {
            if (outSize + extra > outBytes.size) {
                outBytes = outBytes.copyOf(outBytes.size * 2 + extra)
            }
        }

        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            i++
            if (b == 0) {
                ensureCapacity(1)
                outBytes[outSize++] = 0
            } else if (b in 1..8) {
                val count = b
                ensureCapacity(count)
                for (k in 0 until count) {
                    if (i < data.size) {
                        outBytes[outSize++] = data[i++]
                    }
                }
            } else if (b in 9..127) {
                ensureCapacity(1)
                outBytes[outSize++] = b.toByte()
            } else if (b >= 192) {
                ensureCapacity(2)
                outBytes[outSize++] = ' '.code.toByte()
                outBytes[outSize++] = (b xor 0x80).toByte()
            } else { // 128..191
                if (i < data.size) {
                    val b2 = data[i].toInt() and 0xFF
                    i++
                    val word = ((b and 0x3F) shl 8) or b2
                    val distance = (word ushr 3) + 1
                    val length = (word and 0x07) + 3

                    ensureCapacity(length)
                    for (k in 0 until length) {
                        val idx = outSize - distance
                        if (idx >= 0) {
                            outBytes[outSize] = outBytes[idx]
                            outSize++
                        }
                    }
                }
            }
        }
        return outBytes.copyOf(outSize)
    }

    private fun parseHtmlToText(html: String): String {
        // Strip comments
        var text = html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        // Strip scripts/styles
        text = text.replace(Regex("<script.*?>.*?</script>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<style.*?>.*?</style>", RegexOption.IGNORE_CASE), "")
        
        // Convert paragraph and break tags to lines
        text = text.replace(Regex("<p.*?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<div.*?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")

        // Strip remaining HTML tags
        text = text.replace(Regex("<.*?>", RegexOption.DOT_MATCHES_ALL), "")

        // Unescape standard entities
        text = text.replace("&nbsp;", " ")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&apos;", "'")
                   .replace("&#39;", "'")

        // Clean up multiple empty lines
        text = text.replace(Regex("\n{3,}"), "\n\n")
        return text.trim()
    }
}
