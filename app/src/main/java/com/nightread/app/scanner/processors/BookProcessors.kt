package com.nightread.app.scanner.processors

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nightread.app.data.BookEntity
import com.nightread.app.data.EpubIdentifierHelper
import com.nightread.app.data.getRandomGradientEndColor
import com.nightread.app.data.getRandomGradientStartColor
import com.nightread.app.service.Fb2Parser
import com.nightread.app.service.MobiParser
import com.nightread.app.service.NewCoverExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class BookSource(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
    val mimeType: String? = null
)

interface BookProcessor {
    suspend fun process(book: BookSource, context: Context): BookEntity?
}

class Fb2Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            val sha1 = generateFastHash(book)

            val metadata = withContext(Dispatchers.Default) {
                contentResolver.openInputStream(book.uri)?.use { input ->
                    Fb2Parser.parse(input, book.name.substringBeforeLast('.'))
                }
            } ?: return null

            val coverPath = try {
                val headerBytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(book.uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        val read = input.read(buffer)
                        if (read > 0) buffer.copyOf(read) else null
                    }
                }
                if (headerBytes != null && headerBytes.isNotEmpty()) {
                    val text = decodeBytesToString(headerBytes)
                    NewCoverExtractor.extractAndSaveCover(text, sha1, context)
                } else null
            } catch (e: Exception) {
                Log.w("Fb2Processor", "Cover error ${book.name}", e)
                null
            }

            BookEntity(
                sha1 = sha1,
                title = metadata.title.ifBlank { book.name.substringBeforeLast('.') },
                author = metadata.author,
                annotation = metadata.annotation,
                category = "Local",
                filePath = book.uri.toString(),
                fileSize = book.size,
                coverPath = coverPath,
                series = metadata.series,
                seriesIndex = metadata.seriesIndex,
                language = metadata.language,
                isNew = true,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
        } catch (e: Exception) {
            Log.e("Fb2Processor", "Processing error ${book.name}", e)
            null
        }
    }

    private fun generateFastHash(book: BookSource): String {
        val input = "${book.uri}_${book.size}_${book.modified}"
        return MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        try {
            val headerSize = if (bytes.size > 2048) 2048 else bytes.size
            val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val encName = match.groupValues[1].trim()
                try {
                    return String(bytes, java.nio.charset.Charset.forName(encName))
                } catch (e: Exception) { /* ignore */ }
            }
        } catch (e: Exception) { /* ignore */ }

        return try {
            String(bytes, java.nio.charset.StandardCharsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }
}
