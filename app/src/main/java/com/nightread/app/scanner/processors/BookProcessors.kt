package com.nightread.app.scanner.processors

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nightread.app.data.BookEntity
import com.nightread.app.data.EpubIdentifierHelper
import com.nightread.app.data.getRandomGradientEndColor
import com.nightread.app.data.getRandomGradientStartColor
import com.nightread.app.service.Fb2Parser
import com.nightread.app.service.Fb3Parser
import com.nightread.app.service.MobiParser
import com.nightread.app.service.NewCoverExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class BookSource(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
    val mimeType: String? = null,
    val realPath: String? = null
)

interface BookProcessor {
    suspend fun process(book: BookSource, context: Context): BookEntity?
}

class Fb2Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            val sha1 = computeBookSha1(book, context)
            
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
                filePath = resolveBookPath(book, context),
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

class Fb3Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            val sha1 = computeBookSha1(book, context)
            
            val result = withContext(Dispatchers.Default) {
                contentResolver.openInputStream(book.uri)?.use { input ->
                    Fb3Parser.parseFb3(input, book.name.substringBeforeLast('.'), false)
                }
            } ?: return null
            
            val coverPath = if (result.coverBytes != null && result.coverBytes.isNotEmpty()) {
                NewCoverExtractor.saveCoverBytes(result.coverBytes, sha1, context)
            } else null
            
            BookEntity(
                sha1 = sha1,
                title = result.title,
                author = result.author,
                annotation = result.annotation,
                category = "Local",
                filePath = resolveBookPath(book, context),
                fileSize = book.size,
                coverPath = coverPath,
                series = result.series,
                seriesIndex = result.seriesIndex,
                language = result.language,
                isNew = true,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
        } catch (e: Throwable) {
            Log.e("Fb3Processor", "Processing error ${book.name}", e)
            null
        }
    }
}

class EpubProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            val sha1 = computeBookSha1(book, context)
            
            val meta = withContext(Dispatchers.Default) {
                EpubIdentifierHelper.getEpubMetadata { contentResolver.openInputStream(book.uri) }
            } ?: return null
            
            val savedCover = try {
                EpubIdentifierHelper.extractAndSaveEpubCover(
                    { contentResolver.openInputStream(book.uri) },
                    meta.coverPath,
                    sha1,
                    context
                )
            } catch (e: Throwable) {
                Log.w("EpubProcessor", "Cover error ${book.name}", e)
                null
            }
            
            BookEntity(
                sha1 = sha1,
                title = meta.title,
                author = meta.author,
                annotation = meta.description,
                category = "Local",
                filePath = resolveBookPath(book, context),
                fileSize = book.size,
                coverPath = savedCover,
                language = "unknown",
                isNew = true,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
        } catch (e: Throwable) {
            Log.e("EpubProcessor", "Processing error ${book.name}", e)
            null
        }
    }
}

class MobiProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            val sha1 = computeBookSha1(book, context)
            
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(book.uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        total += read
                        if (total > 2 * 1024 * 1024) break
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            } ?: return null
            
            val meta = withContext(Dispatchers.Default) {
                MobiParser.parseBytes(bytes, book.name.substringBeforeLast('.'))
            }
            
            BookEntity(
                sha1 = sha1,
                title = meta.title,
                author = meta.author,
                annotation = meta.annotation,
                category = "Local",
                filePath = resolveBookPath(book, context),
                fileSize = book.size,
                coverPath = null,
                isNew = true,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
        } catch (e: Throwable) {
            Log.e("MobiProcessor", "Processing error ${book.name}", e)
            null
        }
    }
}
