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
import java.io.InputStream

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

// Вспомогательные функции
internal fun computeSha1(book: BookSource, context: Context): String {
    return try {
        openStream(book, context)?.use { input ->
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } ?: book.name.hashCode().toString()
    } catch (e: Throwable) {
        book.name.hashCode().toString()
    }
}

internal fun openStream(book: BookSource, context: Context): InputStream? {
    return try {
        if (book.realPath != null && File(book.realPath).exists()) {
            File(book.realPath).inputStream()
        } else {
            context.contentResolver.openInputStream(book.uri)
        }
    } catch (e: Throwable) {
        null
    }
}

internal fun getFilePath(book: BookSource): String {
    return book.realPath ?: book.uri.path ?: ""
}

class Fb2Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val sha1 = computeSha1(book, context)
            
            val metadata = withContext(Dispatchers.IO) {
                try {
                    openStream(book, context)?.use { input ->
                        Fb2Parser.parse(input, book.name.substringBeforeLast('.'))
                    }
                } catch (e: Throwable) {
                    null
                }
            } ?: return null
            
            val coverPath = withContext(Dispatchers.IO) {
                try {
                    openStream(book, context)?.use { input ->
                        com.nightread.app.service.Fb2CoverExtractor.extract(input, sha1, context)
                    }
                } catch (e: Throwable) {
                    null
                }
            }
            
            BookEntity(
                sha1 = sha1,
                title = metadata.title.ifBlank { book.name.substringBeforeLast('.') },
                author = metadata.author,
                annotation = metadata.annotation,
                category = "Local",
                filePath = getFilePath(book),
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
            Log.e("Fb2Processor", "Error: ${book.name}", e)
            null
        }
    }
}

class Fb3Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val sha1 = computeSha1(book, context)
            
            val result = withContext(Dispatchers.Default) {
                try {
                    openStream(book, context)?.use { input ->
                        Fb3Parser.parseFb3(input, book.name.substringBeforeLast('.'), false)
                    }
                } catch (e: Throwable) {
                    null
                }
            } ?: return null
            
            val coverPath = if (result.coverBytes != null && result.coverBytes.isNotEmpty()) {
                try {
                    NewCoverExtractor.saveCoverBytes(result.coverBytes, sha1, context)
                } catch (e: Throwable) {
                    null
                }
            } else null
            
            BookEntity(
                sha1 = sha1,
                title = result.title,
                author = result.author,
                annotation = result.annotation,
                category = "Local",
                filePath = getFilePath(book),
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
            Log.e("Fb3Processor", "Error: ${book.name}", e)
            null
        }
    }
}

class EpubProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val sha1 = computeSha1(book, context)
            
            val meta = withContext(Dispatchers.Default) {
                try {
                    EpubIdentifierHelper.getEpubMetadata { openStream(book, context) }
                } catch (e: Throwable) {
                    null
                }
            } ?: return null
            
            val savedCover = try {
                EpubIdentifierHelper.extractAndSaveEpubCover(
                    { openStream(book, context) },
                    meta.coverPath,
                    sha1,
                    context
                )
            } catch (e: Throwable) {
                null
            }
            
            BookEntity(
                sha1 = sha1,
                title = meta.title,
                author = meta.author,
                annotation = meta.description,
                category = "Local",
                filePath = getFilePath(book),
                fileSize = book.size,
                coverPath = savedCover,
                language = "unknown",
                isNew = true,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
        } catch (e: Throwable) {
            Log.e("EpubProcessor", "Error: ${book.name}", e)
            null
        }
    }
}

class MobiProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val sha1 = computeSha1(book, context)
            
            val bytes = withContext(Dispatchers.IO) {
                try {
                    openStream(book, context)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0L
                        var read = input.read(buffer)
                        while (read != -1 && total < 2 * 1024 * 1024) {
                            total += read
                            output.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                        output.toByteArray()
                    }
                } catch (e: Throwable) {
                    null
                }
            } ?: return null
            
            val meta = withContext(Dispatchers.Default) {
                try {
                    MobiParser.parseBytes(bytes, book.name.substringBeforeLast('.'))
                } catch (e: Throwable) {
                    null
                }
            } ?: return null
            
            val coverPath = try {
                if (meta.coverBytes != null && meta.coverBytes.isNotEmpty()) {
                    NewCoverExtractor.saveCoverBytes(meta.coverBytes, sha1, context)
                } else null
            } catch (e: Throwable) {
                null
            }
            
            BookEntity(
                sha1 = sha1,
                title = meta.title,
                author = meta.author,
                annotation = meta.annotation,
                category = "Local",
                filePath = getFilePath(book),
                fileSize = book.size,
                coverPath = coverPath,
                isNew = true,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
        } catch (e: Throwable) {
            Log.e("MobiProcessor", "Error: ${book.name}", e)
            null
        }
    }
}

class ZipProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        var tempFile: File? = null
        return try {
            val sha1 = computeSha1(book, context)
            
            var innerName: String? = null
            var innerExt: String? = null
            
            withContext(Dispatchers.IO) {
                try {
                    openStream(book, context)?.use { input ->
                        java.util.zip.ZipInputStream(input).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val entryName = entry.name.lowercase()
                                if (!entry.isDirectory && (
                                    entryName.endsWith(".fb2") || 
                                    entryName.endsWith(".epub") || 
                                    entryName.endsWith(".fb3") || 
                                    entryName.endsWith(".mobi")
                                )) {
                                    innerName = entry.name
                                    innerExt = innerName!!.substringAfterLast('.', "").lowercase()
                                    val tFile = File.createTempFile("inner_book_", ".$innerExt", context.cacheDir)
                                    tempFile = tFile
                                    tFile.outputStream().use { fos ->
                                        val buffer = ByteArray(8192)
                                        var count = zis.read(buffer)
                                        var written = 0L
                                        while (count != -1 && written < 15L * 1024 * 1024) {
                                            fos.write(buffer, 0, count)
                                            written += count
                                            count = zis.read(buffer)
                                        }
                                    }
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // Ignore
                }
            }
            
            val validTempFile = tempFile
            if (innerName == null || validTempFile == null || !validTempFile.exists()) {
                return null
            }
            
            val innerBookSource = BookSource(
                uri = Uri.fromFile(validTempFile),
                name = innerName!!,
                size = validTempFile.length(),
                modified = book.modified,
                realPath = validTempFile.absolutePath
            )

            val processor = when {
                innerExt == "fb2" -> Fb2Processor()
                innerExt == "epub" -> EpubProcessor()
                innerExt == "fb3" -> Fb3Processor()
                innerExt == "mobi" -> MobiProcessor()
                else -> null
            }

            val entity = processor?.process(innerBookSource, context)

            if (entity != null) {
                entity.copy(
                    sha1 = sha1,
                    filePath = getFilePath(book),
                    fileSize = book.size
                )
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e("ZipProcessor", "Error: ${book.name}", e)
            null
        } finally {
            try {
                tempFile?.delete()
            } catch (e: Throwable) {}
        }
    }
}
