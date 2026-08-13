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
import java.io.File

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
            val sha1 = computeBookSha1(book, context)
            
            // 1. Parse Metadata
            val metadata = withContext(Dispatchers.IO) {
                openBookInputStream(book, context)?.use { input ->
                    val actualInput = if (book.name.lowercase().endsWith(".zip") || book.name.lowercase().endsWith(".fb2.zip")) {
                        val zis = java.util.zip.ZipInputStream(input)
                        var entry = zis.nextEntry
                        while (entry != null && !entry.name.lowercase().endsWith(".fb2") && !entry.name.lowercase().endsWith(".fb2.xml") && !entry.name.lowercase().endsWith(".xml")) {
                            entry = zis.nextEntry
                        }
                        if (entry != null) zis else null
                    } else {
                        input
                    }
                    if (actualInput != null) {
                        com.nightread.app.service.Fb2Parser.parse(actualInput, book.name.substringBeforeLast('.'))
                    } else null
                }
            } ?: return null
            
            // 2. Extract Cover
            val coverPath = withContext(Dispatchers.IO) {
                try {
                    openBookInputStream(book, context)?.use { input ->
                        val actualInput = if (book.name.lowercase().endsWith(".zip") || book.name.lowercase().endsWith(".fb2.zip")) {
                            val zis = java.util.zip.ZipInputStream(input)
                            var entry = zis.nextEntry
                            while (entry != null && !entry.name.lowercase().endsWith(".fb2") && !entry.name.lowercase().endsWith(".fb2.xml") && !entry.name.lowercase().endsWith(".xml")) {
                                entry = zis.nextEntry
                            }
                            if (entry != null) zis else null
                        } else {
                            input
                        }
                        if (actualInput != null) {
                            com.nightread.app.service.Fb2CoverExtractor.extract(actualInput, sha1, context)
                        } else null
                    }
                } catch (e: Throwable) {
                    Log.w("Fb2Processor", "Cover error ${book.name}", e)
                    null
                }
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
        } catch (e: Throwable) {
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
                } catch (e: Throwable) { /* ignore */ }
            }
        } catch (e: Throwable) { /* ignore */ }
        return try {
            String(bytes, java.nio.charset.StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            try {
                String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Throwable) {
                String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }
}

class Fb3Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val sha1 = computeBookSha1(book, context)
            
            val result = withContext(Dispatchers.Default) {
                openBookInputStream(book, context)?.use { input ->
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
            val sha1 = computeBookSha1(book, context)
            
            val meta = withContext(Dispatchers.Default) {
                EpubIdentifierHelper.getEpubMetadata { openBookInputStream(book, context) }
            } ?: return null
            
            val savedCover = try {
                EpubIdentifierHelper.extractAndSaveEpubCover(
                    { openBookInputStream(book, context) },
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
            val sha1 = computeBookSha1(book, context)
            
            val bytes = withContext(Dispatchers.IO) {
                openBookInputStream(book, context)?.use { input ->
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
            
            val coverPath = try {
                if (meta.coverBytes != null && meta.coverBytes.isNotEmpty()) {
                    NewCoverExtractor.saveCoverBytes(meta.coverBytes, sha1, context)
                } else null
            } catch (e: Throwable) {
                Log.w("MobiProcessor", "Cover error ${book.name}", e)
                null
            }
            
            BookEntity(
                sha1 = sha1,
                title = meta.title,
                author = meta.author,
                annotation = meta.annotation,
                category = "Local",
                filePath = resolveBookPath(book, context),
                fileSize = book.size,
                coverPath = coverPath,
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

class ZipProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        var tempFile: File? = null
        return try {
            val sha1 = computeBookSha1(book, context)
            
            var innerName: String? = null
            var innerExt: String? = null
            
            withContext(Dispatchers.IO) {
                openBookInputStream(book, context)?.use { input ->
                    java.util.zip.ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && (
                                entryName.endsWith(".fb2") || 
                                entryName.endsWith(".fb2.xml") || 
                                entryName.endsWith(".epub") || 
                                entryName.endsWith(".fb3") || 
                                entryName.endsWith(".mobi") || 
                                entryName.endsWith(".azw") || 
                                entryName.endsWith(".azw3")
                            )) {
                                innerName = entry.name
                                innerExt = innerName!!.substringAfterLast('.', "").lowercase()
                                val tFile = File.createTempFile("inner_book_", ".$innerExt", context.cacheDir)
                                tempFile = tFile
                                tFile.outputStream().use { fos ->
                                    val buffer = ByteArray(8192)
                                    var count: Int
                                    var written = 0L
                                    while (zis.read(buffer).also { count = it } != -1 && written < 15L * 1024 * 1024) {
                                        fos.write(buffer, 0, count)
                                        written += count
                                    }
                                }
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            }
            
            val validTempFile = tempFile
            if (innerName == null || validTempFile == null || !validTempFile.exists() || validTempFile.length() == 0L) {
                return null
            }
            
            val innerBookSource = BookSource(
                uri = android.net.Uri.fromFile(validTempFile),
                name = innerName!!,
                size = validTempFile.length(),
                modified = book.modified,
                realPath = validTempFile.absolutePath
            )

            val processor = when {
                innerExt == "fb2" || innerName!!.endsWith(".fb2.xml") -> Fb2Processor()
                innerExt == "epub" -> EpubProcessor()
                innerExt == "fb3" -> Fb3Processor()
                innerExt == "mobi" || innerExt == "azw" || innerExt == "azw3" -> MobiProcessor()
                else -> null
            }

            val entity = processor?.process(innerBookSource, context)

            if (entity != null) {
                entity.copy(
                    sha1 = sha1,
                    filePath = resolveBookPath(book, context),
                    fileSize = book.size,
                    title = if (entity.title.isBlank() || entity.title == innerName!!.substringBeforeLast('.')) book.name.substringBeforeLast('.') else entity.title
                )
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e("ZipProcessor", "Processing error ${book.name}", e)
            null
        } finally {
            try {
                tempFile?.delete()
            } catch (e: Throwable) {}
        }
    }
}
