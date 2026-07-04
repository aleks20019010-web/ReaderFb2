package com.example.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class BookScanner(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val bookDao = database.bookDao()

    suspend fun scanFolders(onProgress: suspend (current: Int, total: Int, currentFileName: String) -> Unit): ScanResult {
        return withContext(Dispatchers.IO) {
            val rootDir = Environment.getExternalStorageDirectory()
            val filesToProcess = mutableListOf<File>()
            
            // Step 1: Scan directories and gather files
            if (rootDir.exists() && rootDir.isDirectory) {
                gatherFilesRecursive(rootDir, filesToProcess)
            }

            if (filesToProcess.isEmpty()) {
                return@withContext ScanResult.NoBooksFound
            }

            val total = filesToProcess.size
            var addedCount = 0
            var skippedCount = 0

            // Step 1.5: Cache existing SHA-1 hashes and paths
            val sha1Cache = bookDao.getAllSha1s().toMutableSet()
            val existingMap = bookDao.getSha1ToPathMap().associate { it.sha1 to it.filePath }.toMutableMap()
            val batchList = mutableListOf<BookEntity>()
            val BATCH_SIZE = 50
            var lastUpdateTime = System.currentTimeMillis()

            // Step 2: Process files one by one
            for ((index, file) in filesToProcess.withIndex()) {
                val currentIndex = index + 1
                
                val currentTime = System.currentTimeMillis()
                // Update UI every 200ms or on the last item
                if (currentTime - lastUpdateTime > 200 || currentIndex == total) {
                    onProgress(currentIndex, total, file.name)
                    lastUpdateTime = currentTime
                }

                try {
                    val ext = file.extension.lowercase()
                    
                    var parsedTitle = file.nameWithoutExtension
                    val unknownAuthor = try {
                        context.getString(com.example.R.string.unknown_author)
                    } catch (e: Exception) {
                        "Неизвестен"
                    }
                    var parsedAuthor = unknownAuthor
                    var parsedContent = ""
                    var parsedSeries: String? = null
                    var parsedSeriesIndex: Int? = null
                    var parsedLanguage: String? = "ru"
                    var computedSha1 = ""
                    var contentBytes: ByteArray? = null

                    if (ext == "fb2") {
                        contentBytes = file.readBytes()
                        computedSha1 = computeSha1(contentBytes)
                        
                        // Deduplication: check cache (Set<String>) first, then DB
                        var isDuplicate = false
                        if (sha1Cache.contains(computedSha1)) {
                            isDuplicate = true
                        } else {
                            val dbBook = bookDao.getBookBySha1(computedSha1)
                            if (dbBook != null) {
                                isDuplicate = true
                                sha1Cache.add(computedSha1)
                                existingMap[computedSha1] = dbBook.filePath ?: ""
                            }
                        }

                        if (isDuplicate) {
                            skippedCount++
                            val oldPath = existingMap[computedSha1]
                            if (oldPath != file.absolutePath) {
                                bookDao.updateFilePath(computedSha1, file.absolutePath)
                                existingMap[computedSha1] = file.absolutePath
                            }
                            continue
                        }
                        
                        val rawText = decodeBytesToString(contentBytes)
                        val parsed = Fb2Parser.parse(rawText, parsedTitle)
                        parsedTitle = parsed.title
                        parsedAuthor = parsed.author
                        parsedSeries = parsed.series
                        parsedSeriesIndex = parsed.seriesIndex
                        parsedLanguage = parsed.language
                        parsedContent = parsed.content

                    } else if (ext == "zip") {
                        file.inputStream().use { fis ->
                            ZipInputStream(fis).use { zis ->
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    val entryName = entry.name.lowercase()
                                    if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                        val tempBytes = zis.readBytes()
                                        contentBytes = tempBytes
                                        computedSha1 = computeSha1(tempBytes)
                                        
                                        // Deduplication: check cache (Set<String>) first, then DB
                                        var isDuplicate = false
                                        if (sha1Cache.contains(computedSha1)) {
                                            isDuplicate = true
                                        } else {
                                            val dbBook = bookDao.getBookBySha1(computedSha1)
                                            if (dbBook != null) {
                                                isDuplicate = true
                                                sha1Cache.add(computedSha1)
                                                existingMap[computedSha1] = dbBook.filePath ?: ""
                                            }
                                        }

                                        if (isDuplicate) {
                                            skippedCount++
                                            val oldPath = existingMap[computedSha1]
                                            if (oldPath != file.absolutePath) {
                                                bookDao.updateFilePath(computedSha1, file.absolutePath)
                                                existingMap[computedSha1] = file.absolutePath
                                            }
                                            return@use
                                        }

                                        val rawText = decodeBytesToString(tempBytes)
                                        val parsed = Fb2Parser.parse(rawText, entryName.removeSuffix(".fb2"))
                                        parsedTitle = parsed.title
                                        parsedAuthor = parsed.author
                                        parsedSeries = parsed.series
                                        parsedSeriesIndex = parsed.seriesIndex
                                        parsedLanguage = parsed.language
                                        parsedContent = parsed.content
                                        break
                                    }
                                    entry = zis.nextEntry
                                }
                            }
                        }
                        if (computedSha1.isEmpty()) continue // Not a valid zip or already skipped
                    } else if (ext == "txt") {
                        contentBytes = file.readBytes()
                        computedSha1 = computeSha1(contentBytes)
                        
                        // Deduplication: check cache (Set<String>) first, then DB
                        var isDuplicate = false
                        if (sha1Cache.contains(computedSha1)) {
                            isDuplicate = true
                        } else {
                            val dbBook = bookDao.getBookBySha1(computedSha1)
                            if (dbBook != null) {
                                isDuplicate = true
                                sha1Cache.add(computedSha1)
                                existingMap[computedSha1] = dbBook.filePath ?: ""
                            }
                        }

                        if (isDuplicate) {
                            skippedCount++
                            val oldPath = existingMap[computedSha1]
                            if (oldPath != file.absolutePath) {
                                bookDao.updateFilePath(computedSha1, file.absolutePath)
                                existingMap[computedSha1] = file.absolutePath
                            }
                            continue
                        }
                        
                        parsedContent = decodeBytesToString(contentBytes)
                        parsedAuthor = try {
                            context.getString(com.example.R.string.local_txt)
                        } catch (e: Exception) {
                            "Локальный TXT"
                        }
                    } else {
                        continue
                    }

                    if (parsedContent.isNotBlank()) {
                        Log.d("BookScanner", "Extracting cover for: ${file.name}, context: $context")
                        val coverPath = if (context != null) {
                            CoverExtractor.extractCover(file, computedSha1, context)
                        } else {
                            Log.e("BookScanner", "Context is null, skipping cover extraction")
                            null
                        }
                        val newBook = BookEntity(
                            title = parsedTitle,
                            author = parsedAuthor,
                            content = parsedContent,
                            category = "Локальные",
                            totalCharacters = parsedContent.length,
                            coverGradientStart = getRandomGradientStartColor(),
                            coverGradientEnd = getRandomGradientEndColor(),
                            filePath = file.absolutePath,
                            sha1 = computedSha1,
                            fileSize = file.length(),
                            coverPath = coverPath,
                            series = parsedSeries,
                            seriesIndex = parsedSeriesIndex,
                            language = parsedLanguage
                        )
                        batchList.add(newBook)
                        sha1Cache.add(computedSha1)
                        existingMap[computedSha1] = file.absolutePath
                        addedCount++

                        if (batchList.size >= BATCH_SIZE) {
                            bookDao.insertBooks(batchList)
                            batchList.clear()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BookScanner", "Error scanning file ${file.absolutePath}", e)
                }
            }

            // Insert remaining
            if (batchList.isNotEmpty()) {
                bookDao.insertBooks(batchList)
                batchList.clear()
            }

            return@withContext ScanResult.Success(addedCount, skippedCount)
        }
    }

    private fun gatherFilesRecursive(dir: File, resultList: MutableList<File>) {
        val list = dir.listFiles() ?: return
        for (file in list) {
            if (file.isDirectory) {
                val name = file.name.lowercase()
                // Skip system and cache folders
                if (name.startsWith(".") || name == "android" || name == "data" || name == "obb" || name == "system" || name == "vendor") {
                    continue
                }
                gatherFilesRecursive(file, resultList)
            } else {
                val ext = file.extension.lowercase()
                if (ext == "fb2" || ext == "zip" || ext == "txt") {
                    // Safeguard: omit files over 30MB
                    if (file.length() > 0 && file.length() < 30 * 1024 * 1024) {
                        resultList.add(file)
                    }
                }
            }
        }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { String.format("%02x", it) }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        try {
            val headerSize = if (bytes.size > 1024) 1024 else bytes.size
            val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val encName = match.groupValues[1].trim()
                try {
                    return String(bytes, java.nio.charset.Charset.forName(encName))
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}

        try {
            val utf8Decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            utf8Decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (e: Exception) {
            try {
                return String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                return String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }

    private fun getRandomGradientStartColor(): String {
        val list = listOf("#FF6B6B", "#4E65FF", "#11998e", "#FC466B", "#f12711", "#833ab4")
        return list.random()
    }

    private fun getRandomGradientEndColor(): String {
        val list = listOf("#4D96FF", "#92EFFD", "#38ef7d", "#3F5EFB", "#f5af19", "#fd1d1d")
        return list.random()
    }
}

sealed class ScanResult {
    data class Success(val addedCount: Int, val skippedCount: Int) : ScanResult()
    object NoBooksFound : ScanResult()
    data class Error(val message: String) : ScanResult()
}
