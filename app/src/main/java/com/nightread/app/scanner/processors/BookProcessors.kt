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
            
            val metadata = withContext(Dispatchers.IO) {
                try {
                    openBookInputStream(book, context)?.use { input ->
                        Fb2Parser.parse(input, book.name.substringBeforeLast('.'))
                    }
                } catch (e: Throwable) {
                    Log.e("Fb2Processor", "Parse error: ${book.name}", e)
                    null
                }
            } ?: return null
            
            val coverPath = withContext(Dispatchers.IO) {
                try {
                    openBookInputStream(book, context)?.use { input ->
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
            Log.e("Fb2Processor", "Error: ${book.name}", e)
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
            Log.e("Fb3Processor", "Error: ${book.name}", e)
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
            Log.e("EpubProcessor", "Error: ${book.name}", e)
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
                filePath = resolveBookPath(book, context),
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

class TxtProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val sha1 = computeBookSha1(book, context)
            val title = book.name.substringBeforeLast('.').ifBlank { "Текстовый документ" }
            BookEntity(
                sha1 = sha1,
                title = title,
                author = "Неизвестен",
                annotation = null,
                category = "Local",
                filePath = resolveBookPath(book, context),
                fileSize = book.size,
                coverPath = null,
                language = "ru",
                isNew = true,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
        } catch (e: Throwable) {
            Log.e("TxtProcessor", "Error: ${book.name}", e)
            null
        }
    }
}

class ZipProcessor : BookProcessor {
    companion object {
        private val SUPPORTED_INNER_EXTS = setOf("fb2", "epub", "fb3", "mobi", "azw", "azw3")
        private const val MAX_INNER_EXTRACT_BYTES = 60L * 1024 * 1024 // 60 MB so large FB2s keep <binary> covers

        private fun isIgnoredZipEntry(entryName: String): Boolean {
            val lower = entryName.replace("\\", "/").lowercase()
            val fileName = lower.substringAfterLast("/")
            return lower.contains("__macosx/") || fileName.startsWith("._") || fileName == ".ds_store"
        }

        private fun isSupportedInnerBook(entryName: String): Boolean {
            if (isIgnoredZipEntry(entryName)) return false
            val ext = entryName.substringAfterLast('.', "").lowercase()
            return ext in SUPPORTED_INNER_EXTS
        }

        /**
         * Fast check (reads only ZIP central directory via ZipFile when possible) to verify
         * whether a .zip file contains an actual supported e-book (or is itself an EPUB/FB3 container).
         * Never accepts .txt files.
         */
        fun hasSupportedBookInside(file: File): Boolean {
            if (!file.exists() || !file.isFile || !file.canRead() || file.length() < 22L) return false
            return try {
                java.util.zip.ZipFile(file).use { zf ->
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        val norm = entry.name.replace("\\", "/").lowercase()
                        if (isSupportedInnerBook(norm)) return true
                        if (norm == "meta-inf/container.xml" || norm == "fb3/description.xml") return true
                    }
                    false
                }
            } catch (_: Throwable) {
                // Fallback to ZipInputStream if ZipFile cannot parse encoding/structure
                try {
                    file.inputStream().buffered().use { fis ->
                        java.util.zip.ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            var checked = 0
                            while (entry != null && checked < 500) {
                                checked++
                                if (!entry.isDirectory) {
                                    val norm = entry.name.replace("\\", "/").lowercase()
                                    if (isSupportedInnerBook(norm)) return true
                                    if (norm == "meta-inf/container.xml" || norm == "fb3/description.xml") return true
                                }
                                entry = zis.nextEntry
                            }
                            false
                        }
                    }
                } catch (_: Throwable) {
                    false
                }
            }
        }
    }

    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        var tempFile: File? = null
        return try {
            val sha1 = computeBookSha1(book, context)

            var innerName: String? = null
            var innerExt: String? = null
            var isDirectEpub = false
            var isDirectFb3 = false

            withContext(Dispatchers.IO) {
                try {
                    openBookInputStream(book, context)?.use { input ->
                        java.util.zip.ZipInputStream(input.buffered()).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    val norm = entry.name.replace("\\", "/").lowercase()
                                    if (norm == "meta-inf/container.xml" || norm.endsWith(".opf")) {
                                        isDirectEpub = true
                                    } else if (norm == "fb3/description.xml") {
                                        isDirectFb3 = true
                                    } else if (isSupportedInnerBook(norm)) {
                                        innerName = entry.name
                                        innerExt = norm.substringAfterLast('.', "")
                                        val tFile = File.createTempFile("inner_book_", ".$innerExt", context.cacheDir)
                                        tempFile = tFile
                                        tFile.outputStream().buffered().use { fos ->
                                            val buffer = ByteArray(16384)
                                            var count = zis.read(buffer)
                                            var written = 0L
                                            while (count != -1 && written < MAX_INNER_EXTRACT_BYTES) {
                                                fos.write(buffer, 0, count)
                                                written += count
                                                count = zis.read(buffer)
                                            }
                                        }
                                        break
                                    }
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w("ZipProcessor", "ZIP read error for ${book.name}: ${e.message}")
                }
            }

            // If the .zip itself is actually a renamed EPUB or FB3 container
            if (innerName == null) {
                return when {
                    isDirectEpub -> EpubProcessor().process(book, context)
                    isDirectFb3 -> Fb3Processor().process(book, context)
                    else -> null // Archive contains no supported e-books — do NOT add to library
                }
            }

            val validTempFile = tempFile
            if (validTempFile == null || !validTempFile.exists() || validTempFile.length() == 0L) {
                return null
            }

            val innerBookSource = BookSource(
                uri = Uri.fromFile(validTempFile),
                name = innerName!!,
                size = validTempFile.length(),
                modified = book.modified,
                realPath = validTempFile.absolutePath
            )

            val processor = when (innerExt) {
                "fb2" -> Fb2Processor()
                "epub" -> EpubProcessor()
                "fb3" -> Fb3Processor()
                "mobi", "azw", "azw3" -> MobiProcessor()
                else -> null
            }

            val entity = processor?.process(innerBookSource, context)

            if (entity != null) {
                entity.copy(
                    sha1 = sha1,
                    filePath = resolveBookPath(book, context),
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
            } catch (_: Throwable) {}
        }
    }
}

object BookCoverBackfillHelper {
    private const val TAG = "BookCoverBackfill"

    /**
     * Re-extracts a persistent cover for an existing book file on disk (FB2, EPUB, FB3, MOBI/AZW, ZIP/FBZ).
     */
    fun extractCoverForFile(file: File, sha1: String, context: Context): String? {
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        val lower = file.name.lowercase()
        return try {
            when {
                lower.endsWith(".fb2") -> {
                    com.nightread.app.service.Fb2CoverExtractor.extractFromFile(file, sha1, context)
                }
                lower.endsWith(".epub") -> {
                    val meta = EpubIdentifierHelper.getEpubMetadata(file)
                    EpubIdentifierHelper.extractAndSaveEpubCover(file, meta?.coverPath, sha1, context)
                }
                lower.endsWith(".fb3") || lower.endsWith(".fb3.zip") -> {
                    file.inputStream().buffered().use { input ->
                        val res = Fb3Parser.parseFb3(input, file.nameWithoutExtension, false)
                        if (res?.coverBytes != null && res.coverBytes.isNotEmpty()) {
                            NewCoverExtractor.saveCoverBytes(res.coverBytes, sha1, context)
                        } else null
                    }
                }
                lower.endsWith(".mobi") || lower.endsWith(".azw") || lower.endsWith(".azw3") -> {
                    val bytes = file.inputStream().buffered().use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0L
                        var read = input.read(buffer)
                        while (read != -1 && total < 4L * 1024 * 1024) {
                            total += read
                            output.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                        output.toByteArray()
                    }
                    val meta = MobiParser.parseBytes(bytes, file.nameWithoutExtension)
                    if (meta?.coverBytes != null && meta.coverBytes.isNotEmpty()) {
                        NewCoverExtractor.saveCoverBytes(meta.coverBytes, sha1, context)
                    } else null
                }
                lower.endsWith(".zip") || lower.endsWith(".fbz") -> {
                    extractCoverFromZip(file, sha1, context)
                }
                else -> null
            }
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM extracting cover for ${file.name}")
            System.gc()
            null
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to extract cover for ${file.name}: ${e.message}")
            null
        }
    }

    private fun extractCoverFromZip(zipFile: File, sha1: String, context: Context): String? {
        var tempFile: File? = null
        return try {
            var foundExt: String? = null
            var isDirectEpub = false
            var isDirectFb3 = false

            zipFile.inputStream().buffered().use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val norm = entry.name.replace("\\", "/").lowercase()
                            val fileName = norm.substringAfterLast("/")
                            if (!norm.contains("__macosx/") && !fileName.startsWith("._")) {
                                when {
                                    norm == "meta-inf/container.xml" || norm.endsWith(".opf") -> isDirectEpub = true
                                    norm == "fb3/description.xml" -> isDirectFb3 = true
                                    norm.endsWith(".fb2") -> {
                                        // Stream FB2 cover directly out of ZipInputStream without even writing a temp file!
                                        return com.nightread.app.service.Fb2CoverExtractor.extract(zis, sha1, context)
                                    }
                                    norm.endsWith(".epub") || norm.endsWith(".fb3") ||
                                        norm.endsWith(".mobi") || norm.endsWith(".azw") || norm.endsWith(".azw3") -> {
                                        foundExt = norm.substringAfterLast('.')
                                        val tFile = File.createTempFile("cover_zip_", ".$foundExt", context.cacheDir)
                                        tempFile = tFile
                                        tFile.outputStream().buffered().use { fos ->
                                            val buffer = ByteArray(16384)
                                            var count = zis.read(buffer)
                                            var written = 0L
                                            while (count != -1 && written < 60L * 1024 * 1024) {
                                                fos.write(buffer, 0, count)
                                                written += count
                                                count = zis.read(buffer)
                                            }
                                        }
                                        break
                                    }
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            val validTemp = tempFile
            if (validTemp != null && validTemp.exists() && validTemp.length() > 0L) {
                return extractCoverForFile(validTemp, sha1, context)
            }
            if (isDirectEpub) {
                val meta = EpubIdentifierHelper.getEpubMetadata(zipFile)
                return EpubIdentifierHelper.extractAndSaveEpubCover(zipFile, meta?.coverPath, sha1, context)
            }
            if (isDirectFb3) {
                return zipFile.inputStream().buffered().use { input ->
                    val res = Fb3Parser.parseFb3(input, zipFile.nameWithoutExtension, false)
                    if (res?.coverBytes != null && res.coverBytes.isNotEmpty()) {
                        NewCoverExtractor.saveCoverBytes(res.coverBytes, sha1, context)
                    } else null
                }
            }
            null
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to extract ZIP cover for ${zipFile.name}: ${e.message}")
            null
        } finally {
            try { tempFile?.delete() } catch (_: Throwable) {}
        }
    }
}
