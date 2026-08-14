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

// Вспомогательные функции (только один раз!)
private fun computeBookSha1(book: BookSource, context: Context): String {
    return try {
        openBookInputStream(book, context)?.use { input ->
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } ?: book.name.hashCode().toString()
    } catch (e: Throwable) {
        Log.e("BookProcessor", "SHA1 calculation error for ${book.name}", e)
        book.name.hashCode().toString()
    }
}

private fun openBookInputStream(book: BookSource, context: Context): InputStream? {
    return try {
        if (book.realPath != null && File(book.realPath).exists()) {
            File(book.realPath).inputStream()
        } else {
            context.contentResolver.openInputStream(book.uri)
        }
    } catch (e: Throwable) {
        Log.e("BookProcessor", "Cannot open input stream for ${book.name}", e)
        null
    }
}

private fun resolveBookPath(book: BookSource, context: Context): String? {
    return try {
        book.realPath ?: book.uri.path
    } catch (e: Throwable) {
        Log.e("BookProcessor", "Cannot resolve path for ${book.name}", e)
        null
    }
}

class Fb2Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val sha1 = computeBookSha1(book, context)
            
            val metadata = withContext(Dispatchers.IO) {
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
                            Fb2Parser.parse(actualInput, book.name.substringBeforeLast('.'))
                        } else null
                    }
                } catch (e: Throwable) {
                    Log.e("Fb2Processor", "Metadata parsing error ${book.name}", e)
                    null
                }
            } ?: return null
            
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
            
            val filePath = try {
                resolveBookPath(book, context) ?: book.realPath ?: book.uri.path
            } catch (e: Exception) {
                book.realPath ?: book.uri.path
            }
            
            BookEntity(
                sha1 = sha1,
                title = metadata.title.ifBlank { book.name.substringBeforeLast('.') },
                author = metadata.author,
                annotation = metadata.annotation,
                category = "Local",
                filePath = filePath,
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
}

class Fb3Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val sha1 = computeBookSha1(book, context)
            
            val result = withContext(Dispatchers.Default) {
                try {
                    openBookInputStream(book, context)?.use { input ->
                        Fb3Parser.parseFb3(input, book.name.substringBeforeLast('.'), false)
                    }
                } catch (e: Throwable) {
                    Log.e("Fb3Processor", "Parsing error ${book.name}", e)
                    null
                }
            } ?: return null
            
            val coverPath = if (result.coverBytes != null && result.coverBytes.isNotEmpty()) {
                try {
                    NewCoverExtractor.saveCoverBytes(result.coverBytes, sha1, context)
                } catch (e: Throwable) {
                    Log.e("Fb3Processor", "Cover save error ${book.name}", e)
                    null
                }
            } else null
            
            val filePath = try {
                resolveBookPath(book, context) ?: book.realPath ?: book.uri.path
            } catch (e: Exception) {
                book.realPath ?: book.uri.path
            }
            
            BookEntity(
                sha1 = sha1,
                title = result.title,
                author = result.author,
                annotation = result.annotation,
                category = "Local",
                filePath = filePath,
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
                try {
                    EpubIdentifierHelper.getEpubMetadata { openBookInputStream(book, context) }
                } catch (e: Throwable) {
                    Log.e("EpubProcessor", "Metadata error ${book.name}", e)
                    null
                }
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
            
            val filePath = try {
                resolveBookPath(book, context) ?: book.realPath ?: book.uri.path
            } catch (e: Exception) {
                book.realPath ?: book.uri.path
            }
            
            BookEntity(
                sha1 = sha1,
                title = meta.title,
                author = meta.author,
                annotation = meta.description,
                category = "Local",
                filePath = filePath,
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
                try {
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
                } catch (e: Throwable) {
                    Log.e("MobiProcessor", "Reading error ${book.name}", e)
                    null
                }
            } ?: return null
            
            val meta = withContext(Dispatchers.Default) {
                try {
                    MobiParser.parseBytes(bytes, book.name.substringBeforeLast('.'))
                } catch (e: Throwable) {
                    Log.e("MobiProcessor", "Parsing error ${book.name}", e)
                    null
                }
            } ?: return null
            
            val coverPath = try {
                if (meta.coverBytes != null && meta.coverBytes.isNotEmpty()) {
                    NewCoverExtractor.saveCoverBytes(meta.coverBytes, sha1, context)
                } else null
            } catch (e: Throwable) {
                Log.w("MobiProcessor", "Cover error ${book.name}", e)
                null
            }
            
            val filePath = try {
                resolveBookPath(book, context) ?: book.realPath ?: book.uri.path
            } catch (e: Exception) {
                book.realPath ?: book.uri.path
            }
            
            BookEntity(
                sha1 = sha1,
                title = meta.title,
                author = meta.author,
                annotation = meta.annotation,
                category = "Local",
                filePath = filePath,
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
                try {
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
                                    
                                    if (innerExt == "zip") {
                                        Log.w("ZipProcessor", "Nested ZIP not supported: ${book.name}")
                                        break
                                    }
                                    
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
                } catch (e: Throwable) {
                    Log.e("ZipProcessor", "ZIP reading error ${book.name}", e)
                }
            }
            
            val validTempFile = tempFile
            if (innerName == null || validTempFile == null || !validTempFile.exists() || validTempFile.length() == 0L) {
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
                innerExt == "fb2" || innerName!!.endsWith(".fb2.xml") -> Fb2Processor()
                innerExt == "epub" -> EpubProcessor()
                innerExt == "fb3" -> Fb3Processor()
                innerExt == "mobi" || innerExt == "azw" || innerExt == "azw3" -> MobiProcessor()
                else -> null
            }

            val entity = processor?.process(innerBookSource, context)

            if (entity != null) {
                val filePath = try {
                    resolveBookPath(book, context) ?: book.realPath ?: book.uri.path
                } catch (e: Exception) {
                    book.realPath ?: book.uri.path
                }
                
                entity.copy(
                    sha1 = sha1,
                    filePath = filePath,
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
