package com.nightread.app.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.nightread.app.data.BookDao
import com.nightread.app.data.BookEntity
import com.nightread.app.data.EpubIdentifierHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.room.withTransaction
import com.nightread.app.data.AppDatabase
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipException

class NewBookScanner(
    private val context: Context,
    private val bookDao: BookDao
) {
    companion object {
        val isScanningGlobally = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    val state = MutableStateFlow(ScannerState())
    private val TAG = "NewBookScanner"
    private val dbMutex = Mutex()

    private var isBgScan: Boolean = false
    private var lastStateUpdateMs: Long = 0L

    private fun updateLocalAndGlobalState(newState: ScannerState) {
        state.value = newState
        if (!isBgScan) {
            NewBookScanState.updateState(newState)
        }
    }

    private fun updateStateThrottled(newState: ScannerState, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastStateUpdateMs >= 200L) {
            lastStateUpdateMs = now
            updateLocalAndGlobalState(newState)
        }
    }

    suspend fun scan(isBackground: Boolean = false) {
        scanBooks(isBackground)
    }

    suspend fun scanBooks(isBackground: Boolean = false) = withContext(Dispatchers.IO) {
        this@NewBookScanner.isBgScan = isBackground
        Log.d(TAG, "scanBooks: Starting auto-scanning sequence. isBackground=$isBackground")

        if (!isScanningGlobally.compareAndSet(false, true)) {
            Log.d(TAG, "scanBooks: Scan already in progress, aborting.")
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Сканирование уже запущено"))
            return@withContext
        }

        try {
            if ((context as? Context) == null) {
                Log.e(TAG, "scanBooks: Context is null, cannot proceed.")
                updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Критическая ошибка: Context is null"))
                return@withContext
            }
            if ((bookDao as? BookDao) == null) {
                Log.e(TAG, "scanBooks: BookDao is null, cannot proceed.")
                updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Критическая ошибка: BookDao is null"))
                return@withContext
            }

            updateLocalAndGlobalState(ScannerState(isScanning = true, status = "Сканирование запущено..."))

            val booksCount = try {
                bookDao.getBooksCount()
            } catch (e: Exception) {
                0
            }

            val prefs = context.getSharedPreferences("book_scanner_cache", Context.MODE_PRIVATE)

            if (booksCount == 0) {
                Log.d(TAG, "Database is empty. Automatically clearing scanner cache to ensure full re-indexing.")
                try {
                    bookDao.deleteAllScannedFiles()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting cloud file cache", e)
                }
                prefs.edit().clear().apply()
            }

            val paths = listOfNotNull(
                Environment.getExternalStorageDirectory(),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Downloads"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "Books"),
                com.nightread.app.data.AppDatabase.getAppDir(context),
                context.getExternalFilesDir(null),
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                context.filesDir
            ).distinct()

            val filesToProcess = mutableListOf<File>()
            val gatheredPaths = HashSet<String>()
            val visitedDirs = HashSet<String>()

            // 1. Android MediaStore API Query for instant indexed discovery
            val mediaStoreFiles = queryMediaStoreBooks(context)
            for (file in mediaStoreFiles) {
                val canonical = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                if (gatheredPaths.add(canonical)) {
                    filesToProcess.add(file)
                }
            }
            Log.d(TAG, "MediaStore API returned ${mediaStoreFiles.size} candidate files.")

            for (path in paths) {
                if (!kotlin.coroutines.coroutineContext.isActive) return@withContext
                try {
                    if (path.exists() && path.isDirectory && path.canRead()) {
                        Log.d(TAG, "Checking path for gathering: ${path.absolutePath}")
                        val tempFileList = mutableListOf<File>()
                        gatherFilesRecursive(path, tempFileList, 0, visitedDirs)
                        for (file in tempFileList) {
                            val canonical = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                            if (gatheredPaths.add(canonical)) {
                                filesToProcess.add(file)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking root path: ${path.absolutePath}", e)
                }
            }

            val total = filesToProcess.size
            Log.d(TAG, "Total FB2/ZIP/EPUB files gathered after path de-duplication: $total")

            if (total == 0) {
                Log.d(TAG, "Finished scanning: no supported books found.")
                updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Книги не найдены. Проверьте папки Download, Documents или Books."))
                return@withContext
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
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching SHA1 map from DB", e)
                mutableMapOf()
            }

            val allBooksList = try {
                bookDao.getAllBooks().first()
            } catch (e: Exception) {
                emptyList<BookEntity>()
            }
            val booksByPath = allBooksList.filter { !it.filePath.isNullOrBlank() }.associateBy { it.filePath!! }

            var skippedCount = 0
            val batchSize = 10

            val filesToScan = mutableListOf<File>()

            for (file in filesToProcess) {
                val absolutePath = file.absolutePath
                val lastMod = file.lastModified()
                val sizeOnDisk = file.length()

                val existingBook = booksByPath[absolutePath]
                val cachedMod = prefs.getLong("mod_$absolutePath", 0L)
                val cachedSize = prefs.getLong("size_$absolutePath", 0L)

                if (existingBook != null) {
                    if (existingBook.fileSize == sizeOnDisk || (cachedMod == lastMod && cachedSize == sizeOnDisk)) {
                        skippedCount++
                        if (cachedMod == 0L) {
                            prefs.edit().putLong("mod_$absolutePath", lastMod).putLong("size_$absolutePath", sizeOnDisk).apply()
                        }
                        continue
                    }
                } else if (cachedMod == lastMod && cachedSize == sizeOnDisk && sha1ToPathMap.values.contains(absolutePath)) {
                    skippedCount++
                    continue
                }

                filesToScan.add(file)
            }

            Log.d(TAG, "scanBooks: Filtered $total files down to ${filesToScan.size} that need full parsing. Skipped: $skippedCount")

            if (filesToScan.isEmpty()) {
                updateLocalAndGlobalState(ScannerState(
                    isScanning = false,
                    status = "Сканирование завершено. Новых книг: 0, уже в библиотеке: $skippedCount",
                    totalFiles = total,
                    processedFiles = total,
                    addedBooks = 0,
                    skippedBooks = skippedCount
                ))
                return@withContext
            }

            val totalToScan = filesToScan.size
            val maxConcurrency = 2
            val addedCountAtomic = AtomicInteger(0)
            val skippedCountAtomic = AtomicInteger(0)
            val processedCountAtomic = AtomicInteger(0)
            val batchList = java.util.Collections.synchronizedList(mutableListOf<BookEntity>())
            val sha1ConcurrentMap = ConcurrentHashMap(sha1ToPathMap)

            supervisorScope {
                val channel = kotlinx.coroutines.channels.Channel<File>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                filesToScan.forEach { channel.trySend(it) }
                channel.close()

                val workers: List<Job> = List(maxConcurrency) {
                    launch(Dispatchers.IO) {
                        for (file in channel) {
                            if (!kotlin.coroutines.coroutineContext.isActive) break

                            val fileIndex = processedCountAtomic.incrementAndGet()

                            updateStateThrottled(ScannerState(
                                isScanning = true,
                                status = "Обработка: ${file.name} ($fileIndex/$totalToScan)",
                                totalFiles = totalToScan,
                                processedFiles = fileIndex,
                                addedBooks = addedCountAtomic.get(),
                                skippedBooks = skippedCount
                            ))

                            try {
                                if (fileIndex % 20 == 0) {
                                    System.gc()
                                }
                                withTimeoutOrNull(45000) {
                                    processFile(file, sha1ConcurrentMap, batchList) { added, skipped ->
                                        if (added > 0) addedCountAtomic.addAndGet(added)
                                        if (skipped > 0) skippedCountAtomic.addAndGet(skipped)
                                    }
                                    prefs.edit().putLong("mod_${file.absolutePath}", file.lastModified()).putLong("size_${file.absolutePath}", file.length()).apply()
                                }
                            } catch (ce: CancellationException) {
                                if (!kotlin.coroutines.coroutineContext.isActive) throw ce
                                Log.w(TAG, "Processing timed out for ${file.name}")
                            } catch (e: Throwable) {
                                Log.e(TAG, "Error processing ${file.absolutePath}", e)
                            }

                            flushBatchToDb(batchList)
                        }
                    }
                }
                workers.forEach { it.join() }
            }

            flushBatchToDb(batchList, forceAll = true)

            val addedCountFinal = addedCountAtomic.get()
            val skippedCountFinal = skippedCount + skippedCountAtomic.get()

            val finalStatus = "Сканирование завершено. Добавлено книг: $addedCountFinal, пропущено: $skippedCountFinal."
            Log.d(TAG, "Scan sequence completed successfully: totalFiles=$total, added=$addedCountFinal, skipped=$skippedCountFinal")

            updateLocalAndGlobalState(ScannerState(
                isScanning = false,
                status = finalStatus,
                totalFiles = total,
                processedFiles = total,
                addedBooks = addedCountFinal,
                skippedBooks = skippedCountFinal
            ))
        } catch (e: Throwable) {
            Log.e(TAG, "Critical error during scanBooks", e)
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Критическая ошибка сканирования: ${e.localizedMessage}"))
        } finally {
            isScanningGlobally.set(false)
        }
    }

    suspend fun checkForNewBooks() = withContext(Dispatchers.IO) {
        isBgScan = false
        Log.d(TAG, "checkForNewBooks: Starting incremental book scanning sequence.")

        if (!isScanningGlobally.compareAndSet(false, true)) {
            Log.d(TAG, "checkForNewBooks: Scan already in progress, aborting.")
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Сканирование уже запущено"))
            return@withContext
        }

        try {
            updateLocalAndGlobalState(ScannerState(isScanning = true, status = "Быстрое сканирование..."))

            val booksCount = try {
                bookDao.getBooksCount()
            } catch (e: Exception) {
                0
            }
            val prefs = context.getSharedPreferences("book_scanner_cache", Context.MODE_PRIVATE)

            if (booksCount == 0) {
                Log.d(TAG, "Database is empty. Automatically clearing scanner cache to ensure full re-indexing.")
                try {
                    bookDao.deleteAllScannedFiles()
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting cloud file cache", e)
                }
                prefs.edit().clear().apply()
            }

            val paths = listOfNotNull(
                Environment.getExternalStorageDirectory(),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Downloads"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "Books"),
                com.nightread.app.data.AppDatabase.getAppDir(context),
                context.getExternalFilesDir(null),
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                context.filesDir
            ).distinct()

            val filesToProcess = mutableListOf<File>()
            val gatheredPaths = HashSet<String>()
            val visitedDirs = HashSet<String>()

            for (path in paths) {
                if (!kotlin.coroutines.coroutineContext.isActive) return@withContext
                try {
                    if (path.exists() && path.isDirectory && path.canRead()) {
                        val tempFileList = mutableListOf<File>()
                        gatherFilesRecursive(path, tempFileList, 0, visitedDirs)
                        for (file in tempFileList) {
                            val canonical = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                            if (gatheredPaths.add(canonical)) {
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

            val allBooksList = try {
                bookDao.getAllBooks().first()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching books from DB", e)
                emptyList<BookEntity>()
            }

            val booksByPath = allBooksList.filter { !it.filePath.isNullOrBlank() }.associateBy { it.filePath!! }
            val sha1ToPathMap = allBooksList.associate { it.sha1 to it.filePath }.toMutableMap()

            val filesToScan = mutableListOf<File>()
            var skippedCount = 0

            for (file in filesToProcess) {
                val absolutePath = file.absolutePath
                val lastModifiedOnDisk = file.lastModified()
                val sizeOnDisk = file.length()

                val existingBook = booksByPath[absolutePath]
                val cachedMod = prefs.getLong("mod_$absolutePath", 0L)
                val cachedSize = prefs.getLong("size_$absolutePath", 0L)

                if (existingBook != null) {
                    if ((cachedMod == lastModifiedOnDisk && cachedSize == sizeOnDisk) || existingBook.fileSize == sizeOnDisk) {
                        if (cachedMod == 0L) {
                            prefs.edit().putLong("mod_$absolutePath", lastModifiedOnDisk).putLong("size_$absolutePath", sizeOnDisk).apply()
                        }
                        skippedCount++
                        continue
                    }
                } else if (cachedMod == lastModifiedOnDisk && cachedSize == sizeOnDisk && sha1ToPathMap.values.contains(absolutePath)) {
                    skippedCount++
                    continue
                }

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

            val totalToScan = filesToScan.size
            val maxConcurrency = 2
            val addedCountAtomic = AtomicInteger(0)
            val skippedCountAtomic = AtomicInteger(0)
            val processedCountAtomic = AtomicInteger(0)
            val batchList = java.util.Collections.synchronizedList(mutableListOf<BookEntity>())
            val sha1ConcurrentMap = ConcurrentHashMap(sha1ToPathMap)

            supervisorScope {
                val channel = kotlinx.coroutines.channels.Channel<File>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                filesToScan.forEach { channel.trySend(it) }
                channel.close()

                val workers: List<Job> = List(maxConcurrency) {
                    launch(Dispatchers.IO) {
                        for (file in channel) {
                            if (!kotlin.coroutines.coroutineContext.isActive) break

                            val fileIndex = processedCountAtomic.incrementAndGet()

                            updateStateThrottled(ScannerState(
                                isScanning = true,
                                status = "Обработка: ${file.name} ($fileIndex/$totalToScan)",
                                totalFiles = totalToScan,
                                processedFiles = fileIndex,
                                addedBooks = addedCountAtomic.get(),
                                skippedBooks = skippedCount
                            ))

                            try {
                                if (fileIndex % 20 == 0) {
                                    System.gc()
                                }
                                withTimeoutOrNull(45000) {
                                    processFile(file, sha1ConcurrentMap, batchList) { added, skipped ->
                                        if (added > 0) addedCountAtomic.addAndGet(added)
                                        if (skipped > 0) skippedCountAtomic.addAndGet(skipped)
                                    }

                                    prefs.edit().apply {
                                        putLong("mod_${file.absolutePath}", file.lastModified())
                                        putLong("size_${file.absolutePath}", file.length())
                                    }.apply()
                                }
                            } catch (ce: CancellationException) {
                                if (!kotlin.coroutines.coroutineContext.isActive) throw ce
                                Log.w(TAG, "Incremental processing timed out for ${file.name}")
                            } catch (e: Throwable) {
                                Log.e(TAG, "Error incremental processing for ${file.absolutePath}", e)
                            }

                            flushBatchToDb(batchList)
                        }
                    }
                }
                workers.forEach { it.join() }
            }

            flushBatchToDb(batchList, forceAll = true)

            val addedCountFinal = addedCountAtomic.get()
            val skippedCountFinal = skippedCount + skippedCountAtomic.get()

            val finalStatus = if (addedCountFinal > 0) {
                "Найдено новых книг: $addedCountFinal."
            } else {
                "Новых книг не найдено."
            }
            Log.d(TAG, "checkForNewBooks completed: added=$addedCountFinal, skipped=$skippedCountFinal")

            updateLocalAndGlobalState(ScannerState(
                isScanning = false,
                status = finalStatus,
                totalFiles = total,
                processedFiles = total,
                addedBooks = addedCountFinal,
                skippedBooks = skippedCountFinal
            ))

        } catch (e: Throwable) {
            Log.e(TAG, "Critical error during checkForNewBooks", e)
            updateLocalAndGlobalState(ScannerState(isScanning = false, status = "Ошибка обновления: ${e.localizedMessage}"))
        } finally {
            isScanningGlobally.set(false)
        }
    }

    private suspend fun flushBatchToDb(batchList: MutableList<BookEntity>, forceAll: Boolean = false) {
        val itemsToInsert = synchronized(batchList) {
            if (forceAll || batchList.size >= 10) {
                val copy = ArrayList(batchList)
                batchList.clear()
                copy
            } else {
                emptyList()
            }
        }
        if (itemsToInsert.isNotEmpty()) {
            dbMutex.withLock {
                try {
                    AppDatabase.getDatabase(context).withTransaction {
                        bookDao.insertBooks(itemsToInsert)
                    }
                } catch (dbEx: Throwable) {
                    for (book in itemsToInsert) {
                        try { bookDao.insertBooks(listOf(book)) } catch (sEx: Throwable) {}
                    }
                }
            }
        }
    }

    private fun queryMediaStoreBooks(context: Context): List<File> {
        val result = mutableListOf<File>()
        val supportedExtensions = setOf("fb2", "epub", "zip", "fb3", "mobi", "azw", "azw3")
        try {
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val projection = arrayOf(android.provider.MediaStore.Files.FileColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataIndex = cursor.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA)
                if (dataIndex != -1) {
                    while (cursor.moveToNext()) {
                        try {
                            val path = cursor.getString(dataIndex)
                            if (!path.isNullOrEmpty()) {
                                val ext = path.substringAfterLast('.', "").lowercase()
                                val fileName = path.substringAfterLast('/')
                                val fileNameLower = fileName.lowercase()

                                if (fileName.startsWith("._") || 
                                    fileName.startsWith(".") || 
                                    fileNameLower.contains("__macosx") || 
                                    ext == "txt" || 
                                    ext == "pdf"
                                ) {
                                    continue
                                }

                                if (supportedExtensions.contains(ext)) {
                                    val file = File(path)
                                    if (file.exists() && file.isFile && file.canRead()) {
                                        result.add(file)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            // ignore row error
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "MediaStore query error: ${e.message}")
        }
        return result
    }

    private suspend fun processFile(
        file: File,
        sha1ToPathMap: MutableMap<String, String?>,
        batchList: MutableList<BookEntity>,
        onStatsUpdated: (added: Int, skipped: Int) -> Unit
    ) {
        val fileName = file.name
        val fileNameLower = fileName.lowercase()
        val ext = file.extension.lowercase()

        if (fileName.startsWith("._") || 
            fileName.startsWith(".") || 
            fileNameLower.contains("__macosx") || 
            ext == "txt" || 
            ext == "pdf"
        ) {
            return
        }

        if (ext == "fb3" || file.name.endsWith(".fb3.zip", true)) {
            try {
                if (!file.exists() || !file.canRead()) return
                val sha1 = computeSha1ForFile(file)
                val existingPath = sha1ToPathMap.putIfAbsent(sha1, file.absolutePath)
                if (existingPath != null) {
                    if (existingPath != file.absolutePath) {
                        try {
                            bookDao.updateFilePath(sha1, file.absolutePath)
                            sha1ToPathMap[sha1] = file.absolutePath
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to update FB3 file path in DB for SHA-1: $sha1", ex)
                        }
                    }
                    onStatsUpdated(0, 1)
                    return
                }

                val parsed = com.nightread.app.service.Fb3Parser.parseFb3(file, file.nameWithoutExtension.removeSuffix(".fb3"), extractContent = false)
                var coverPath: String? = null
                if (parsed.coverBytes != null && parsed.coverBytes.isNotEmpty()) {
                    coverPath = com.nightread.app.service.NewCoverExtractor.saveCoverBytes(parsed.coverBytes, sha1, context)
                }
                val book = BookEntity(
                    sha1 = sha1,
                    title = parsed.title,
                    author = parsed.author,
                    coverGradientStart = getRandomGradientStartColor(),
                    coverGradientEnd = getRandomGradientEndColor(),
                    category = "Local",
                    filePath = file.absolutePath,
                    coverPath = coverPath,
                    annotation = parsed.annotation,
                    fileSize = file.length(),
                    series = parsed.series,
                    seriesIndex = parsed.seriesIndex,
                    language = parsed.language,
                    isNew = true
                )
                batchList.add(book)
                onStatsUpdated(1, 0)
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling fb3 file: ${file.absolutePath}", e)
            }
        } else if (ext == "fb2") {
            try {
                if (!file.exists() || !file.canRead()) return

                val sha1 = computeSha1ForFile(file)
                val existingPath = sha1ToPathMap.putIfAbsent(sha1, file.absolutePath)
                if (existingPath != null) {
                    if (existingPath != file.absolutePath) {
                        try {
                            bookDao.updateFilePath(sha1, file.absolutePath)
                            sha1ToPathMap[sha1] = file.absolutePath
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to update file path in DB for SHA-1: $sha1", ex)
                        }
                    }
                    onStatsUpdated(0, 1)
                    return
                }

                val metadata = file.inputStream().buffered().use { stream ->
                    Fb2Parser.parse(stream, file.nameWithoutExtension)
                }
                val resolvedTitle = resolveRussianTitle(metadata.title, file.nameWithoutExtension)
                
                val coverPath = try {
                    if (file.length() < 3 * 1024 * 1024) {
                        val bytes = file.inputStream().buffered().use { it.readBytes() }
                        val rawText = decodeBytesToString(bytes)
                        NewCoverExtractor.extractAndSaveCover(rawText, sha1, context)
                    } else {
                        val buffer = ByteArray(2 * 1024 * 1024)
                        val bytesRead = file.inputStream().buffered().use { it.read(buffer) }
                        if (bytesRead > 0) {
                            val rawTextHeader = String(buffer, 0, bytesRead, Charsets.UTF_8)
                            NewCoverExtractor.extractAndSaveCover(rawTextHeader, sha1, context)
                        } else null
                    }
                } catch (e: OutOfMemoryError) {
                    System.gc()
                    null
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to extract cover for FB2 file ${file.name}", e)
                    null
                }

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
                onStatsUpdated(1, 0)
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling fb2 file: ${file.absolutePath}", e)
            }
        } else if (ext == "zip") {
            try {
                if (!file.exists() || !file.canRead()) return

                java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!kotlin.coroutines.coroutineContext.isActive) return
                        try {
                            val entryName = entry.name.lowercase()
                            val entryFileName = entryName.substringAfterLast('/')
                            
                            if (!entry.isDirectory && 
                                !entryName.contains("__macosx") && 
                                !entryFileName.startsWith("._") && 
                                !entryFileName.startsWith(".") &&
                                !entryName.endsWith(".txt") &&
                                !entryName.endsWith(".pdf") &&
                                (entryName.endsWith(".fb2") || entryName.endsWith(".fb3") || entryName.endsWith(".epub") || entryName.endsWith(".mobi") || entryName.endsWith(".azw") || entryName.endsWith(".azw3"))
                            ) {
                                val tempBytes = try {
                                    val buffer = java.io.ByteArrayOutputStream()
                                    val data = ByteArray(8192)
                                    var nRead: Int
                                    var totalRead = 0
                                    while (zis.read(data, 0, data.size).also { nRead = it } != -1 && totalRead < 3 * 1024 * 1024) {
                                        if (!kotlin.coroutines.coroutineContext.isActive) return
                                        buffer.write(data, 0, nRead)
                                        totalRead += nRead
                                    }
                                    buffer.toByteArray()
                                } catch (e: Throwable) {
                                    byteArrayOf()
                                }

                                if (tempBytes.isNotEmpty()) {
                                    val sha1 = computeSha1(tempBytes)
                                    val existingPath = sha1ToPathMap.putIfAbsent(sha1, file.absolutePath)

                                    if (existingPath != null) {
                                        if (existingPath != file.absolutePath) {
                                            try {
                                                bookDao.updateFilePath(sha1, file.absolutePath)
                                                sha1ToPathMap[sha1] = file.absolutePath
                                            } catch (ex: Exception) {
                                                Log.e(TAG, "Failed to update file path in DB for ZIP entry SHA-1: $sha1", ex)
                                            }
                                        }
                                        onStatsUpdated(0, 1)
                                    } else {
                                        val isFb3 = entryName.endsWith(".fb3")
                                        val entryFallback = entryFileName.substringBeforeLast('.')
                                        val (metadata, coverPath) = if (isFb3) {
                                            val parsedFb3 = com.nightread.app.service.Fb3Parser.parseBytes(tempBytes, entryFallback, extractContent = false)
                                            val covPath = if (parsedFb3.coverBytes != null && parsedFb3.coverBytes.isNotEmpty()) {
                                                com.nightread.app.service.NewCoverExtractor.saveCoverBytes(parsedFb3.coverBytes, sha1, context)
                                            } else null
                                            BookMetadata(
                                                title = parsedFb3.title,
                                                author = parsedFb3.author,
                                                content = parsedFb3.content,
                                                series = parsedFb3.series,
                                                seriesIndex = parsedFb3.seriesIndex,
                                                language = parsedFb3.language,
                                                annotation = parsedFb3.annotation
                                            ) to covPath
                                        } else {
                                            val fb2Meta = tempBytes.inputStream().use { Fb2Parser.parse(it, entryFallback) }
                                            val covPath = try {
                                                val rawText = decodeBytesToString(tempBytes)
                                                NewCoverExtractor.extractAndSaveCover(rawText, sha1, context)
                                            } catch (e: OutOfMemoryError) {
                                                System.gc()
                                                null
                                            } catch (e: Exception) {
                                                null
                                            }
                                            fb2Meta to covPath
                                        }

                                        val resolvedTitle = resolveRussianTitle(metadata.title, entryFallback)

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
            } catch (e: Throwable) {
                Log.e(TAG, "Error opening zip archive: ${file.absolutePath}", e)
            }
        } else if (ext == "epub") {
            try {
                if (!file.exists() || !file.canRead()) return

                val metadata = EpubIdentifierHelper.getEpubMetadata(file)
                if (metadata == null) return

                val identifier = metadata.identifier
                val existingPath = sha1ToPathMap.putIfAbsent(identifier, file.absolutePath)

                if (existingPath != null) {
                    if (existingPath != file.absolutePath) {
                        try {
                            bookDao.updateFilePath(identifier, file.absolutePath)
                            sha1ToPathMap[identifier] = file.absolutePath
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to update file path in DB for ID: $identifier", ex)
                        }
                    }
                    onStatsUpdated(0, 1)
                    return
                }

                val savedCoverPath = EpubIdentifierHelper.extractAndSaveEpubCover(file, metadata.coverPath, identifier, context)
                val book = BookEntity(
                    sha1 = identifier,
                    title = metadata.title,
                    author = metadata.author,
                    coverGradientStart = getRandomGradientStartColor(),
                    coverGradientEnd = getRandomGradientEndColor(),
                    category = "Local",
                    filePath = file.absolutePath,
                    coverPath = savedCoverPath,
                    annotation = metadata.description,
                    fileSize = file.length(),
                    seriesIndex = null,
                    language = "Unknown",
                    isNew = true
                )
                batchList.add(book)
                onStatsUpdated(1, 0)
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling epub file: ${file.absolutePath}", e)
            }
        } else if (ext in listOf("mobi", "azw", "azw3")) {
            try {
                if (!file.exists() || !file.canRead()) return
                val sha1 = computeSha1ForFile(file)
                val existingPath = sha1ToPathMap.putIfAbsent(sha1, file.absolutePath)

                if (existingPath != null) {
                    try {
                        val existingBook = bookDao.getBookBySha1(sha1)
                        if (existingBook != null) {
                            val needTitleFix = com.nightread.app.service.MobiParser.isSlugTitle(existingBook.title)
                            val needAuthorFix = existingBook.author == "Неизвестен"
                            val needAnnotFix = existingBook.annotation.isNullOrBlank()
                            if (needTitleFix || needAuthorFix || needAnnotFix) {
                                val buffer = ByteArray(2 * 1024 * 1024)
                                val len = file.inputStream().buffered().use { it.read(buffer) }
                                val bytes = if (len == buffer.size) buffer else buffer.copyOf(len.coerceAtLeast(0))
                                val parsed = com.nightread.app.service.MobiParser.parseBytes(bytes, file.nameWithoutExtension)
                                val updatedBook = existingBook.copy(
                                    title = if (needTitleFix && parsed.title.isNotBlank()) parsed.title else existingBook.title,
                                    author = if (needAuthorFix && parsed.author != "Неизвестен") parsed.author else existingBook.author,
                                    annotation = if (needAnnotFix && !parsed.annotation.isNullOrBlank()) parsed.annotation else existingBook.annotation,
                                    filePath = file.absolutePath
                                )
                                bookDao.updateBook(updatedBook)
                            } else if (existingPath != file.absolutePath) {
                                bookDao.updateFilePath(sha1, file.absolutePath)
                            }
                        } else if (existingPath != file.absolutePath) {
                            bookDao.updateFilePath(sha1, file.absolutePath)
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Failed to update existing MOBI book in DB for SHA-1: $sha1", ex)
                    }
                    onStatsUpdated(0, 1)
                    return
                }

                val buffer = ByteArray(2 * 1024 * 1024)
                val len = file.inputStream().buffered().use { it.read(buffer) }
                val bytes = if (len == buffer.size) buffer else buffer.copyOf(len.coerceAtLeast(0))
                if (bytes.isEmpty()) return
                val parsed = com.nightread.app.service.MobiParser.parseBytes(bytes, file.nameWithoutExtension)
                var coverPath: String? = null
                if (parsed.coverBytes != null && parsed.coverBytes.isNotEmpty()) {
                    try {
                        val coversDir = File(context.filesDir, "covers")
                        if (!coversDir.exists()) coversDir.mkdirs()
                        val coverFile = File(coversDir, "$sha1.jpg")
                        coverFile.writeBytes(parsed.coverBytes)
                        coverPath = coverFile.absolutePath
                    } catch (ce: Exception) {
                        Log.e(TAG, "Failed saving cover for mobi: $sha1", ce)
                    }
                }
                val book = BookEntity(
                    sha1 = sha1,
                    title = parsed.title,
                    author = parsed.author,
                    coverGradientStart = getRandomGradientStartColor(),
                    coverGradientEnd = getRandomGradientEndColor(),
                    coverPath = coverPath,
                    annotation = parsed.annotation,
                    category = "Local",
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    isNew = true
                )
                batchList.add(book)
                onStatsUpdated(1, 0)
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling mobi/azw file: ${file.absolutePath}", e)
            }
        }
    }

    private fun gatherFilesRecursive(dir: File, list: MutableList<File>, depth: Int, visitedDirs: HashSet<String>) {
        if (depth > 6) return
        val canonicalDir = try { dir.canonicalPath } catch (e: Exception) { dir.absolutePath }
        if (!visitedDirs.add(canonicalDir)) return
        if (!dir.exists() || !dir.isDirectory() || !dir.canRead()) return

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
                        name == "__macosx" ||
                        name.contains("__macosx") ||
                        name == "android" || 
                        name == "data" || 
                        name == "obb" || 
                        name == "system" || 
                        name == "vendor" || 
                        name == "cache" || 
                        name == "temp" || 
                        name == "tmp" ||
                        name == "dcim" || 
                        name == "pictures" || 
                        name == "camera" ||
                        name == "photos" ||
                        name == "screenshots" ||
                        name == "alarms" || 
                        name == "notifications" || 
                        name == "ringtones" || 
                        name == "podcasts" ||
                        name == "movies" ||
                        name == "music" ||
                        name == "audiobooks" ||
                        name == "media" ||
                        name == "video" ||
                        name == "videos" ||
                        name == "whatsapp" ||
                        name == "telegram" ||
                        name == "viber" ||
                        name == "backups"
                    ) {
                        continue
                    }
                    gatherFilesRecursive(file, list, depth + 1, visitedDirs)
                } else {
                    val fileName = file.name
                    val fileNameLower = fileName.lowercase()
                    val ext = file.extension.lowercase()

                    if (fileName.startsWith("._") || 
                        fileName.startsWith(".") || 
                        fileNameLower.contains("__macosx") || 
                        ext == "txt" || 
                        ext == "pdf"
                    ) {
                        continue
                    }

                    if ((com.nightread.app.data.BookFormatHelper.isSupported(file.absolutePath) || ext == "zip") && ext != "txt" && ext != "pdf") {
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
        try {
            val headerSize = if (bytes.size > 2048) 2048 else bytes.size
            val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val encName = match.groupValues[1].trim()
                try {
                    return String(bytes, java.nio.charset.Charset.forName(encName))
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }

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

    private fun computeSha1ForFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered(65536).use { fis ->
            val buffer = ByteArray(65536)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
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

        if (cleanMetadataTitle.isNotEmpty() && containsCyrillic(cleanMetadataTitle)) {
            return cleanMetadataTitle
        }

        if (containsCyrillic(cleanFilename)) {
            return cleanFilename
        }

        if (cleanMetadataTitle.isNotEmpty()) {
            return TitleHelper.transliterate(cleanMetadataTitle)
        }

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
}
