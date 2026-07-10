package com.nightread.app.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import android.content.Context


import android.os.Environment
import android.util.Log
import com.nightread.app.data.BookDao
import com.nightread.app.data.BookEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private var isBgScan: Boolean = false

    private fun updateLocalAndGlobalState(newState: ScannerState) {
        state.value = newState
        if (!isBgScan) {
            NewBookScanState.updateState(newState)
        }
    }

    suspend fun scan(isBackground: Boolean = false) {
        scanBooks(isBackground)
    }

    suspend fun scanBooks(isBackground: Boolean = false) {
        this.isBgScan = isBackground
        Log.d(TAG, "scanBooks: Starting auto-scanning sequence. isBackground=$isBackground")
        
        if ((context as? Context) == null) {
            Log.e(TAG, "scanBooks: Context is null, cannot proceed.")
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Критическая ошибка: Context is null"))
            return
        }
        if ((bookDao as? BookDao) == null) {
            Log.e(TAG, "scanBooks: BookDao is null, cannot proceed.")
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Критическая ошибка: BookDao is null"))
            return
        }

        updateLocalAndGlobalState(ScannerState(isScanning = true, status = "Сканирование запущено..."))

        try {
            val booksCount = try {
                bookDao.getBooksCount()
            } catch (e: Exception) {
                0
            }
            if (booksCount == 0) {
                Log.d(TAG, "Database is empty. Automatically clearing scanner cache to ensure full re-indexing.")
                try {
                    bookDao.deleteAllScannedFiles()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting cloud file cache", e)
                }
                val prefs = context.getSharedPreferences("book_scanner_cache", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            }

            val paths = listOf(
                Environment.getExternalStorageDirectory(),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "Books")
            )

            val filesToProcess = mutableListOf<File>()
            val gatheredPaths = HashSet<String>()
            for (path in paths) {
                if (!kotlin.coroutines.coroutineContext.isActive) return
                try {
                    if (path.exists() && path.isDirectory && path.canRead()) {
                        Log.d(TAG, "Checking path for gathering: ${path.absolutePath}")
                        val tempFileList = mutableListOf<File>()
                        gatherFilesRecursive(path, tempFileList, 0)
                        for (file in tempFileList) {
                            val canonical = file.canonicalPath
                            if (!gatheredPaths.contains(canonical)) {
                                gatheredPaths.add(canonical)
                                filesToProcess.add(file)
                            } else {
                                Log.d(TAG, "[SCAN-DUPLICATE-PATH] Skipped already gathered file path: $canonical")
                            }
                        }
                    } else {
                        Log.w(TAG, "Path is not accessible: ${path.absolutePath} (exists=${path.exists()}, isDir=${path.isDirectory()}, canRead=${path.canRead()})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking root path: ${path.absolutePath}", e)
                }
            }

            val total = filesToProcess.size
            Log.d(TAG, "Total FB2/ZIP files gathered after path de-duplication: $total")
            
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
                val dbMap = bookDao.getSha1ToPathMap().associate { it.sha1 to it.filePath }.toMutableMap()
                Log.d(TAG, "[SCAN-DB-STATE] Loaded ${dbMap.size} existing SHA-1 values from database for comparison.")
                dbMap
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException fetching SHA1 map from DB", e)
                mutableMapOf()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching SHA1 map from DB", e)
                mutableMapOf()
            }

            val batchList = mutableListOf<BookEntity>()
            var addedCount = 0
            var skippedCount = 0
            val batchSize = 10 // Quick saves of 10 to keep the UI refreshed and avoid large OOMs

            val existingPaths = sha1ToPathMap.values.filterNotNull().toSet()

            for ((index, file) in filesToProcess.withIndex()) {
                if (!kotlin.coroutines.coroutineContext.isActive) return
                val fileIndex = index + 1
                
                if (existingPaths.contains(file.absolutePath)) {
                    skippedCount++
                    if (!isBgScan && (fileIndex % 50 == 0 || fileIndex == total)) {
                        updateLocalAndGlobalState(ScannerState(
                            isScanning = true,
                            status = "Поиск новых книг... ($fileIndex/$total)",
                            totalFiles = total,
                            processedFiles = fileIndex,
                            addedBooks = addedCount,
                            skippedBooks = skippedCount
                        ))
                    }
                    continue
                }

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
                        Log.d(TAG, "[DB-INSERT] Batch writing ${batchList.size} books to database: " + batchList.map { "${it.title} (SHA-1: ${it.sha1})" }.joinToString(", "))
                        bookDao.insertBooks(batchList)
                        Log.d(TAG, "Inserted batch of ${batchList.size} books to database successfully.")
                    } catch (dbEx: Throwable) {
                        Log.e(TAG, "Batch insert failed, falling back to safe one-by-one insert to prevent discard", dbEx)
                        for (book in batchList) {
                            try {
                                Log.d(TAG, "[DB-INSERT] Safe fallback inserting book: ${book.title} (SHA-1: ${book.sha1})")
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
                    Log.d(TAG, "[DB-INSERT] Final writing ${batchList.size} books to database: " + batchList.map { "${it.title} (SHA-1: ${it.sha1})" }.joinToString(", "))
                    bookDao.insertBooks(batchList)
                    Log.d(TAG, "Inserted final batch of ${batchList.size} books to database successfully.")
                } catch (dbEx: Throwable) {
                    Log.e(TAG, "Final batch insert failed, falling back to safe one-by-one insert", dbEx)
                    for (book in batchList) {
                        try {
                            Log.d(TAG, "[DB-INSERT] Safe final fallback inserting book: ${book.title} (SHA-1: ${book.sha1})")
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
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during scanBooks", e)
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Критическая ошибка сканирования: ${e.localizedMessage}"))
        }
    }

    suspend fun checkForNewBooks() = withContext(Dispatchers.IO) {
        isBgScan = false
        Log.d(TAG, "checkForNewBooks: Starting incremental book scanning sequence.")
        
        updateLocalAndGlobalState(ScannerState(isScanning = true, status = "Быстрое сканирование..."))

        try {
            val booksCount = try {
                bookDao.getBooksCount()
            } catch (e: Exception) {
                0
            }
            if (booksCount == 0) {
                Log.d(TAG, "Database is empty. Automatically clearing scanner cache to ensure full re-indexing.")
                try {
                    bookDao.deleteAllScannedFiles()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting cloud file cache", e)
                }
                val prefs = context.getSharedPreferences("book_scanner_cache", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
            }

            val paths = listOf(
                Environment.getExternalStorageDirectory(),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "Books")
            )

            val filesToProcess = mutableListOf<File>()
            val gatheredPaths = HashSet<String>()
            for (path in paths) {
                if (!kotlin.coroutines.coroutineContext.isActive) return@withContext
                try {
                    if (path.exists() && path.isDirectory && path.canRead()) {
                        Log.d(TAG, "Checking path for gathering: ${path.absolutePath}")
                        val tempFileList = mutableListOf<File>()
                        gatherFilesRecursive(path, tempFileList, 0)
                        for (file in tempFileList) {
                            val canonical = file.canonicalPath
                            if (!gatheredPaths.contains(canonical)) {
                                gatheredPaths.add(canonical)
                                filesToProcess.add(file)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking root path: ${path.absolutePath}", e)
                }
            }

            val total = filesToProcess.size
            Log.d(TAG, "checkForNewBooks: Total FB2/ZIP files gathered: $total")
            
            if (total == 0) {
                updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Новых книг не найдено."))
                return@withContext
            }

            // Get all books in the DB
            val allBooksList = try {
                bookDao.getAllBooks().first()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching books from DB", e)
                emptyList<BookEntity>()
            }

            val booksByPath = allBooksList.filter { !it.filePath.isNullOrBlank() }.associateBy { it.filePath!! }
            val sha1ToPathMap = allBooksList.filter { !it.filePath.isNullOrBlank() }.associate { it.sha1 to it.filePath!! }.toMutableMap()
            
            val prefs = context.getSharedPreferences("book_scanner_cache", Context.MODE_PRIVATE)

            val filesToScan = mutableListOf<File>()
            var skippedCount = 0

            for (file in filesToProcess) {
                val absolutePath = file.absolutePath
                val lastModifiedOnDisk = file.lastModified()
                val sizeOnDisk = file.length()

                val existingBook = booksByPath[absolutePath]
                if (existingBook != null) {
                    // Check if file size on disk is the same as stored in DB, and lastModified matches cache
                    val cachedMod = prefs.getLong("mod_$absolutePath", 0L)
                    val cachedSize = prefs.getLong("size_$absolutePath", 0L)

                    if (cachedMod == lastModifiedOnDisk && cachedSize == sizeOnDisk && existingBook.fileSize == sizeOnDisk) {
                        // File has not changed, skip it!
                        skippedCount++
                        continue
                    } else if (cachedMod == 0L && existingBook.fileSize == sizeOnDisk) {
                        // No cache yet, but size matches. Save cache and skip!
                        prefs.edit().apply {
                            putLong("mod_$absolutePath", lastModifiedOnDisk)
                            putLong("size_$absolutePath", sizeOnDisk)
                        }.apply()
                        skippedCount++
                        continue
                    }
                }
                
                // File is new or has been modified!
                filesToScan.add(file)
            }

            Log.d(TAG, "checkForNewBooks: Filtered down from $total to ${filesToScan.size} files that need scanning. Skipped unmodified: $skippedCount")

            if (filesToScan.isEmpty()) {
                updateLocalAndGlobalState(ScannerState(
                    isScanning = false,
                    status = "Новых книг не найдено.",
                    totalFiles = total,
                    processedFiles = total,
                    addedBooks = 0,
                    skippedBooks = skippedCount
                ))
                return@withContext
            }

            updateLocalAndGlobalState(ScannerState(
                isScanning = true,
                status = "Найдено новых/измененных файлов: ${filesToScan.size}",
                totalFiles = filesToScan.size,
                processedFiles = 0,
                addedBooks = 0,
                skippedBooks = skippedCount
            ))

            val batchList = mutableListOf<BookEntity>()
            var addedCount = 0
            val batchSize = 5

            for ((index, file) in filesToScan.withIndex()) {
                if (!kotlin.coroutines.coroutineContext.isActive) return@withContext
                val fileIndex = index + 1
                
                Log.d(TAG, "Incremental processing file [$fileIndex/${filesToScan.size}]: ${file.name}")
                
                updateLocalAndGlobalState(ScannerState(
                    isScanning = true,
                    status = "Обработка: ${file.name} ($fileIndex/${filesToScan.size})",
                    totalFiles = filesToScan.size,
                    processedFiles = fileIndex,
                    addedBooks = addedCount,
                    skippedBooks = skippedCount
                ))

                val success = withTimeoutOrNull(5000) {
                    try {
                        processFile(file, sha1ToPathMap, batchList) { added, skipped ->
                            addedCount += added
                            skippedCount += skipped
                        }
                        
                        // Update cache
                        prefs.edit().apply {
                            putLong("mod_${file.absolutePath}", file.lastModified())
                            putLong("size_${file.absolutePath}", file.length())
                        }.apply()
                        
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error incremental processing for ${file.absolutePath}", e)
                        false
                    }
                }

                if (batchList.size >= batchSize) {
                    bookDao.insertBooks(batchList)
                    batchList.clear()
                    
                    updateLocalAndGlobalState(ScannerState(
                        isScanning = true,
                        status = "Сохранение книг... ($fileIndex/${filesToScan.size})",
                        totalFiles = filesToScan.size,
                        processedFiles = fileIndex,
                        addedBooks = addedCount,
                        skippedBooks = skippedCount
                    ))
                }
            }

            if (batchList.isNotEmpty()) {
                bookDao.insertBooks(batchList)
                batchList.clear()
            }

            val finalStatus = if (addedCount > 0) {
                "Найдено новых книг: $addedCount."
            } else {
                "Новых книг не найдено."
            }
            Log.d(TAG, "checkForNewBooks completed: added=$addedCount, skipped=$skippedCount")

            updateLocalAndGlobalState(ScannerState(
                isScanning = false,
                status = finalStatus,
                totalFiles = total,
                processedFiles = total,
                addedBooks = addedCount,
                skippedBooks = skippedCount
            ))

        } catch (e: Exception) {
            Log.e(TAG, "Critical error during checkForNewBooks", e)
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Ошибка обновления: ${e.localizedMessage}"))
        }
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
                        fis.readBytes()
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
                Log.d(TAG, "[SCAN-SHA1] Calculated SHA-1: $sha1 for FB2 file: ${file.name} (path: ${file.absolutePath})")
                
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
                    coverPath = coverPath,
                    annotation = metadata.annotation,
                    fileSize = file.length(),
                    series = metadata.series,
                    seriesIndex = metadata.seriesIndex,
                    language = metadata.language,
                    isNew = true
                )
                batchList.add(book)
                sha1ToPathMap[sha1] = file.absolutePath
                onStatsUpdated(1, 0)
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling fb2 file: ${file.absolutePath}", e)
            }
        } else if (ext == "epub") {
            try {
                if (!file.exists() || !file.canRead()) {
                    Log.w(TAG, "File does not exist or is not readable: ${file.absolutePath}")
                    return
                }

                val bytes = try {
                    file.inputStream().buffered().use { fis ->
                        fis.readBytes()
                    }
                } catch (e: Exception) { return }

                if (bytes.isEmpty()) return
                
                val sha1 = computeSha1(bytes)
                if (sha1ToPathMap.containsKey(sha1)) {
                    val existingPath = sha1ToPathMap[sha1]
                    if (existingPath != file.absolutePath) {
                        try {
                            kotlinx.coroutines.runBlocking {
                                bookDao.updateFilePath(sha1, file.absolutePath)
                            }
                            sha1ToPathMap[sha1] = file.absolutePath
                        } catch (ex: Exception) { }
                    }
                    onStatsUpdated(0, 1)
                    return
                }

                val metadata = NewEpubParser.parse(file, file.nameWithoutExtension) ?: return
                val resolvedTitle = resolveRussianTitle(metadata.title, file.nameWithoutExtension)
                
                val coverBytes = NewEpubParser.extractCover(file)
                val coverPath = if (coverBytes != null) NewCoverExtractor.saveCoverBytes(coverBytes, sha1, context) else null

                val book = BookEntity(
                    sha1 = sha1,
                    title = resolvedTitle,
                    author = metadata.author,
                    coverGradientStart = getRandomGradientStartColor(),
                    coverGradientEnd = getRandomGradientEndColor(),
                    category = "Local",
                    filePath = file.absolutePath,
                    coverPath = coverPath,
                    annotation = metadata.annotation,
                    fileSize = file.length(),
                    series = metadata.series,
                    seriesIndex = metadata.seriesIndex,
                    language = metadata.language,
                    isNew = true
                )
                batchList.add(book)
                sha1ToPathMap[sha1] = file.absolutePath
                onStatsUpdated(1, 0)
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling epub file: ${file.absolutePath}", e)
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
                                    val buffer = java.io.ByteArrayOutputStream()
                                    val data = ByteArray(8192)
                                    var nRead: Int
                                    while (zis.read(data, 0, data.size).also { nRead = it } != -1) {
                                        buffer.write(data, 0, nRead)
                                    }
                                    buffer.toByteArray()
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
                                    Log.d(TAG, "[SCAN-SHA1] Calculated SHA-1: $sha1 for ZIP-entry: $entryName inside: ${file.name}")
                                    
                                    if (sha1ToPathMap.containsKey(sha1)) {
                                        val existingPath = sha1ToPathMap[sha1]
                                        if (existingPath != file.absolutePath) {
                                            Log.d(TAG, "Book with SHA-1 $sha1 already exists but path changed from '$existingPath' to '${file.absolutePath}'. Updating path in database.")
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
                                            coverPath = coverPath,
                                            annotation = metadata.annotation,
                                            fileSize = file.length(),
                                            series = metadata.series,
                                            seriesIndex = metadata.seriesIndex,
                                            language = metadata.language,
                    isNew = true
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
        } catch (e: CancellationException) {
                throw e
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
                    if (ext == "fb2" || ext == "zip" || ext == "epub") {
                        if (file.length() > 0 && file.length() < 30 * 1024 * 1024) {
                            list.add(file)
                        } else {
                            Log.d(TAG, "Ignoring size-restricted/empty file: ${file.name} (${file.length()} bytes)")
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException checking file object: ${file.absolutePath}", e)
            } catch (e: CancellationException) {
                throw e
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
        } catch (e: CancellationException) {
                throw e
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
