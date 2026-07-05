package com.example.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.BookDao
import com.example.data.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipException

class NewBookScanner(
    private val context: Context,
    private val bookDao: BookDao
) {
    val state = MutableStateFlow(ScannerState())
    private val TAG = "NewBookScanner"

    private fun updateLocalAndGlobalState(newState: ScannerState) {
        state.value = newState
        NewBookScanState.updateState(newState)
    }

    suspend fun scan() {
        scanBooks()
    }

    suspend fun scanBooks() {
        Log.d(TAG, "scanBooks: Starting auto-scanning sequence.")
        updateLocalAndGlobalState(ScannerState(isScanning = true, status = "Сканирование запущено..."))

        val paths = listOf(
            Environment.getExternalStorageDirectory(),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Documents"),
            File(Environment.getExternalStorageDirectory(), "Books")
        )

        val filesToProcess = mutableListOf<File>()
        for (path in paths) {
            try {
                if (path.exists() && path.isDirectory && path.canRead()) {
                    Log.d(TAG, "Checking path for gathering: ${path.absolutePath}")
                    gatherFilesRecursive(path, filesToProcess, 0)
                } else {
                    Log.w(TAG, "Path is not accessible: ${path.absolutePath} (exists=${path.exists()}, isDir=${path.isDirectory()}, canRead=${path.canRead()})")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException checking root path: ${path.absolutePath}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking root path: ${path.absolutePath}", e)
            }
        }

        val total = filesToProcess.size
        Log.d(TAG, "Total FB2/ZIP files gathered: $total")
        
        if (total == 0) {
            Log.d(TAG, "Finished scanning: no supported books found.")
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Книги не найдены. Проверьте папки Download, Documents или Books."))
            return
        }

        updateLocalAndGlobalState(ScannerState(
            isScanning = true,
            status = "Найдено файлов для обработки: $total",
            totalFiles = total
        ))

        val sha1ToPathMap = try {
            bookDao.getSha1ToPathMap().associate { it.sha1 to it.filePath }.toMutableMap()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException fetching SHA1 map from DB", e)
            mutableMapOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SHA1 map from DB", e)
            mutableMapOf()
        }

        val batchList = mutableListOf<BookEntity>()
        var addedCount = 0
        var skippedCount = 0
        val batchSize = 10 // Quick saves of 10 to keep the UI refreshed and avoid large OOMs

        for ((index, file) in filesToProcess.withIndex()) {
            val fileIndex = index + 1
            Log.d(TAG, "Processing file [$fileIndex/$total]: ${file.name}")
            
            updateLocalAndGlobalState(ScannerState(
                isScanning = true,
                status = "Обработка: ${file.name} ($fileIndex/$total)",
                totalFiles = total,
                processedFiles = fileIndex,
                addedBooks = addedCount,
                skippedBooks = skippedCount
            ))

            // Timeout of 5 seconds per file to avoid hanging on massive or corrupted files
            val success = withTimeoutOrNull(5000) {
                try {
                    processFile(file, sha1ToPathMap, batchList) { added, skipped ->
                        addedCount += added
                        skippedCount += skipped
                    }
                    true
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException in processFile for: ${file.absolutePath}", e)
                    false
                } catch (e: ZipException) {
                    Log.e(TAG, "ZipException in processFile for: ${file.absolutePath}", e)
                    false
                } catch (e: IOException) {
                    Log.e(TAG, "IOException in processFile for: ${file.absolutePath}", e)
                    false
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OutOfMemoryError in processFile for: ${file.absolutePath}", e)
                    System.gc()
                    false
                } catch (e: Throwable) {
                    Log.e(TAG, "Unknown error in processFile for: ${file.absolutePath}", e)
                    false
                }
            }
            if (success == null) {
                Log.e(TAG, "Timeout of 5 seconds exceeded processing file: ${file.absolutePath}")
            }

            // Insert to DB in batches to maximize performance and ensure data is committed incrementally
            if (batchList.size >= batchSize) {
                try {
                    bookDao.insertBooks(batchList)
                    Log.d(TAG, "Inserted batch of ${batchList.size} books to database successfully.")
                } catch (dbEx: Throwable) {
                    Log.e(TAG, "Batch insert failed, falling back to safe one-by-one insert to prevent discard", dbEx)
                    for (book in batchList) {
                        try {
                            bookDao.insertBooks(listOf(book))
                            Log.d(TAG, "Successfully inserted single book after batch fallback: ${book.title}")
                        } catch (singleEx: Throwable) {
                            Log.e(TAG, "Failed to insert single book in fallback: ${book.title}", singleEx)
                        }
                    }
                }
                batchList.clear()
                
                // Immediately update status with successful DB save
                updateLocalAndGlobalState(ScannerState(
                    isScanning = true,
                    status = "Сохранение книг в библиотеку... ($fileIndex/$total)",
                    totalFiles = total,
                    processedFiles = fileIndex,
                    addedBooks = addedCount,
                    skippedBooks = skippedCount
                ))
            }
        }

        // Final insert of remaining books
        if (batchList.isNotEmpty()) {
            try {
                bookDao.insertBooks(batchList)
                Log.d(TAG, "Inserted final batch of ${batchList.size} books to database successfully.")
            } catch (dbEx: Throwable) {
                Log.e(TAG, "Final batch insert failed, falling back to safe one-by-one insert", dbEx)
                for (book in batchList) {
                    try {
                        bookDao.insertBooks(listOf(book))
                        Log.d(TAG, "Successfully inserted single book after final batch fallback: ${book.title}")
                    } catch (singleEx: Throwable) {
                        Log.e(TAG, "Failed to insert single book in final fallback: ${book.title}", singleEx)
                    }
                }
            }
            batchList.clear()
        }

        val finalStatus = "Сканирование завершено. Добавлено книг: $addedCount, дубликатов пропущено: $skippedCount."
        Log.d(TAG, "Scan sequence completed successfully: totalFiles=$total, added=$addedCount, skipped=$skippedCount")
        
        updateLocalAndGlobalState(ScannerState(
            isScanning = false,
            status = finalStatus,
            totalFiles = total,
            processedFiles = total,
            addedBooks = addedCount,
            skippedBooks = skippedCount
        ))
    }

    private fun processFile(
        file: File,
        sha1ToPathMap: MutableMap<String, String>,
        batchList: MutableList<BookEntity>,
        onStatsUpdated: (added: Int, skipped: Int) -> Unit
    ) {
        val ext = file.extension.lowercase()
        if (ext == "fb2") {
            try {
                if (!file.exists() || !file.canRead()) {
                    Log.w(TAG, "File does not exist or is not readable: ${file.absolutePath}")
                    return
                }

                val bytes = try {
                    file.inputStream().buffered().use { fis ->
                        readLimitedBytes(fis, 25 * 1024 * 1024)
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException reading file: ${file.absolutePath}", e)
                    return
                } catch (e: IOException) {
                    Log.e(TAG, "IOException reading file: ${file.absolutePath}", e)
                    return
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "OutOfMemoryError reading file: ${file.absolutePath}", e)
                    System.gc()
                    return
                } catch (e: Throwable) {
                    Log.e(TAG, "Unknown error reading file: ${file.absolutePath}", e)
                    return
                }

                if (bytes.isEmpty()) return
                
                val sha1 = computeSha1(bytes)
                Log.d(TAG, "Computed SHA-1 for FB2: $sha1 (file: ${file.name}, size: ${file.length()} bytes)")
                
                if (sha1ToPathMap.containsKey(sha1)) {
                    val existingPath = sha1ToPathMap[sha1]
                    if (existingPath != file.absolutePath) {
                        Log.d(TAG, "Book with SHA-1 $sha1 already exists but path changed from '$existingPath' to '${file.absolutePath}'. Updating path in database.")
                        try {
                            // Direct database update to prevent duplicate rows while resolving new paths
                            kotlinx.coroutines.runBlocking {
                                bookDao.updateFilePath(sha1, file.absolutePath)
                            }
                            sha1ToPathMap[sha1] = file.absolutePath
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to update file path in DB during scanning for SHA-1: $sha1", ex)
                        }
                    } else {
                        Log.d(TAG, "Skipped existing FB2 book by SHA-1 ($sha1): ${file.name}")
                    }
                    onStatsUpdated(0, 1)
                    return
                }
                
                val rawText = decodeBytesToString(bytes)
                val metadata = NewFb2Parser.parse(rawText, file.nameWithoutExtension)
                
                // Resolve correct Russian title with transliteration support
                val resolvedTitle = resolveRussianTitle(metadata.title, file.nameWithoutExtension)
                
                // Extract and save cover image to context.filesDir
                val coverPath = NewCoverExtractor.extractAndSaveCover(rawText, sha1, context)
                
                val book = BookEntity(
                    sha1 = sha1,
                    title = resolvedTitle,
                    author = metadata.author,
                    coverGradientStart = getRandomGradientStartColor(),
                    coverGradientEnd = getRandomGradientEndColor(),
                    category = "Local",
                    filePath = file.absolutePath,
                    coverPath = coverPath
                )
                batchList.add(book)
                sha1ToPathMap[sha1] = file.absolutePath
                onStatsUpdated(1, 0)
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling fb2 file: ${file.absolutePath}", e)
            }
        } else if (ext == "zip") {
            try {
                if (!file.exists() || !file.canRead()) {
                    Log.w(TAG, "Zip file does not exist or is not readable: ${file.absolutePath}")
                    return
                }

                java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        try {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                val tempBytes = try {
                                    // Protect against oversized files to avoid OutOfMemory
                                    readLimitedBytes(zis, 25 * 1024 * 1024)
                                } catch (e: SecurityException) {
                                    Log.e(TAG, "SecurityException reading zip entry: $entryName in ${file.absolutePath}", e)
                                    byteArrayOf()
                                } catch (e: ZipException) {
                                    Log.e(TAG, "ZipException reading zip entry: $entryName in ${file.absolutePath}", e)
                                    byteArrayOf()
                                } catch (e: IOException) {
                                    Log.e(TAG, "IOException reading zip entry: $entryName in ${file.absolutePath}", e)
                                    byteArrayOf()
                                } catch (e: OutOfMemoryError) {
                                    Log.e(TAG, "OutOfMemoryError reading zip entry: $entryName in ${file.absolutePath}", e)
                                    System.gc()
                                    byteArrayOf()
                                } catch (e: Throwable) {
                                    Log.e(TAG, "Throwable reading zip entry: $entryName in ${file.absolutePath}", e)
                                    byteArrayOf()
                                }

                                if (tempBytes.isNotEmpty()) {
                                    val sha1 = computeSha1(tempBytes)
                                    Log.d(TAG, "Computed SHA-1 for FB2 inside ZIP: $sha1 (entry: $entryName in ZIP: ${file.name})")
                                    
                                    if (sha1ToPathMap.containsKey(sha1)) {
                                        val existingPath = sha1ToPathMap[sha1]
                                        if (existingPath != file.absolutePath) {
                                            Log.d(TAG, "Book with SHA-1 $sha1 from ZIP entry already exists but path changed from '$existingPath' to '${file.absolutePath}'. Updating path.")
                                            try {
                                                kotlinx.coroutines.runBlocking {
                                                    bookDao.updateFilePath(sha1, file.absolutePath)
                                                }
                                                sha1ToPathMap[sha1] = file.absolutePath
                                            } catch (ex: Exception) {
                                                Log.e(TAG, "Failed to update file path in DB for ZIP entry SHA-1: $sha1", ex)
                                            }
                                        } else {
                                            Log.d(TAG, "Skipped existing ZIP-entry book by SHA-1 ($sha1): $entryName")
                                        }
                                        onStatsUpdated(0, 1)
                                    } else {
                                        val rawText = decodeBytesToString(tempBytes)
                                        val entryFallback = entryName.removeSuffix(".fb2")
                                        val metadata = NewFb2Parser.parse(rawText, entryFallback)
                                        
                                        // Resolve correct Russian title with transliteration support
                                        val resolvedTitle = resolveRussianTitle(metadata.title, entryFallback)
                                        
                                        // Extract and save cover image to context.filesDir
                                        val coverPath = NewCoverExtractor.extractAndSaveCover(rawText, sha1, context)
                                        
                                        val book = BookEntity(
                                            sha1 = sha1,
                                            title = resolvedTitle,
                                            author = metadata.author,
                                            coverGradientStart = getRandomGradientStartColor(),
                                            coverGradientEnd = getRandomGradientEndColor(),
                                            category = "Local",
                                            filePath = file.absolutePath,
                                            coverPath = coverPath
                                        )
                                        batchList.add(book)
                                        sha1ToPathMap[sha1] = file.absolutePath
                                        onStatsUpdated(1, 0)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error processing zip entry: ${entry.name} in file: ${file.absolutePath}", e)
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException opening zip archive: ${file.absolutePath}", e)
            } catch (e: ZipException) {
                Log.e(TAG, "ZipException opening zip archive: ${file.absolutePath}", e)
            } catch (e: IOException) {
                Log.e(TAG, "IOException opening zip archive: ${file.absolutePath}", e)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError opening zip archive: ${file.absolutePath}", e)
                System.gc()
            } catch (e: Throwable) {
                Log.e(TAG, "Error opening zip archive: ${file.absolutePath}", e)
            }
        }
    }

    private fun gatherFilesRecursive(dir: File, list: MutableList<File>, depth: Int) {
        if (depth > 6) {
            Log.d(TAG, "Max recursion depth (6) reached at: ${dir.absolutePath}")
            return
        }
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) {
            return
        }

        val files = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException listing files for directory: ${dir.absolutePath}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed listFiles for directory: ${dir.absolutePath}", e)
            null
        } ?: return

        for (file in files) {
            try {
                if (file.isDirectory) {
                    val name = file.name.lowercase()
                    if (name.startsWith(".") || 
                        name == "android" || 
                        name == "data" || 
                        name == "obb" || 
                        name == "system" || 
                        name == "vendor" || 
                        name == "cache" || 
                        name == "temp" || 
                        name == "dcim" || 
                        name == "pictures" || 
                        name == "alarms" || 
                        name == "notifications" || 
                        name == "ringtones" || 
                        name == "podcasts"
                    ) {
                        continue
                    }
                    gatherFilesRecursive(file, list, depth + 1)
                } else {
                    val ext = file.extension.lowercase()
                    if (ext == "fb2" || ext == "zip") {
                        if (file.length() > 0 && file.length() < 30 * 1024 * 1024) {
                            list.add(file)
                        } else {
                            Log.d(TAG, "Ignoring size-restricted/empty file: ${file.name} (${file.length()} bytes)")
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException checking file object: ${file.absolutePath}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking file object: ${file.absolutePath}", e)
            }
        }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        return try {
            // Determine encoding by scanning only the first 2048 bytes of the file, avoiding dual 25MB string allocations
            val prefixLen = minOf(bytes.size, 2048)
            val prefix = String(bytes, 0, prefixLen, Charsets.UTF_8)
            if (prefix.contains("<?xml", ignoreCase = true) || prefix.contains("<fictionbook", ignoreCase = true)) {
                String(bytes, Charsets.UTF_8)
            } else {
                String(bytes, java.nio.charset.Charset.forName("windows-1251"))
            }
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun containsCyrillic(str: String): Boolean {
        return str.any { it in '\u0400'..'\u04FF' }
    }

    private fun resolveRussianTitle(metadataTitle: String, filename: String): String {
        val cleanMetadataTitle = metadataTitle.trim()
        val cleanFilename = filename.trim()

        // 1. If metadata title has Cyrillic, use it directly (corresponds to: "Если в FB2 есть тег <title> — использовать его")
        if (cleanMetadataTitle.isNotEmpty() && containsCyrillic(cleanMetadataTitle)) {
            return cleanMetadataTitle
        }

        // 2. If the filename has Cyrillic, use the filename as the base title ("Если имя файла содержит русские буквы — использовать их как основу")
        if (containsCyrillic(cleanFilename)) {
            return cleanFilename
        }

        // 3. If there is a metadata title but it is in Latin, transliterate it ("Если заголовок на латинице или отсутствует — транслитерировать с помощью TitleHelper.transliterate()")
        if (cleanMetadataTitle.isNotEmpty()) {
            return TitleHelper.transliterate(cleanMetadataTitle)
        }

        // 4. Otherwise, transliterate the filename as fallback
        return TitleHelper.transliterate(cleanFilename)
    }

    private fun getRandomGradientStartColor(): String {
        val colors = listOf("#1A1A2E", "#16213E", "#0F3460", "#2E2528", "#3B0066")
        return colors.random()
    }

    private fun getRandomGradientEndColor(): String {
        val colors = listOf("#E94560", "#00ADB5", "#FF2E63", "#FF9F43", "#F35588")
        return colors.random()
    }

    private fun readLimitedBytes(inputStream: java.io.InputStream, limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val data = ByteArray(8192)
        var totalRead = 0
        var nRead: Int
        while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
            totalRead += nRead
            if (totalRead > limit) {
                Log.w(TAG, "File size limit exceeded while reading stream ($totalRead > $limit bytes), truncating.")
                break
            }
            buffer.write(data, 0, nRead)
        }
        return buffer.toByteArray()
    }
}
