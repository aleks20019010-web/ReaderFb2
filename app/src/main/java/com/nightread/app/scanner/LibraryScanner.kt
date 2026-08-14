package com.nightread.app.scanner

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookCache
import com.nightread.app.data.BookDao
import com.nightread.app.data.BookEntity
import com.nightread.app.scanner.processors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LibraryScanner(
    private val context: Context,
    private val bookDao: BookDao
) {
    companion object {
        private const val TAG = "LibraryScanner"
        private const val BATCH_SIZE = 10
        private const val MAX_ZIP_SIZE_MB = 25
        private const val TIMEOUT_PER_BOOK_MS = 15_000L
        private const val MAX_FILES_TO_SCAN = 10000
        private const val SCAN_COOLDOWN_MS = 5000L
        private const val CACHE_CLEANUP_INTERVAL = 7 * 24 * 60 * 60 * 1000L // 7 дней
        private const val MAX_SCAN_DEPTH = 5
        private const val MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB
        
        @Volatile
        private var INSTANCE: LibraryScanner? = null
        
        fun getInstance(context: Context, bookDao: BookDao): LibraryScanner {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LibraryScanner(context.applicationContext, bookDao).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    // Процессоры
    private val fb2Processor = Fb2Processor()
    private val epubProcessor = EpubProcessor()
    private val fb3Processor = Fb3Processor()
    private val mobiProcessor = MobiProcessor()
    private val zipProcessor = ZipProcessor()
    
    // Состояние
    private val _isScanning = AtomicBoolean(false)
    val isScanning: Boolean get() = _isScanning.get()
    
    private var scanJob: Job? = null
    private var lastScanTime = 0L
    
    // Компоненты
    private val progressManager = ProgressManager()
    private val etaCalculator = EtaCalculator()
    private val memoryMonitor = MemoryMonitor()
    private val scanPrefs = ScannerPreferences(context)
    
    // Flow для прогресса
    val progress: StateFlow<ScanProgress> = progressManager.progress
    
    // Корутин скоуп
    private val scannerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Исключаемые папки
    private val excludePaths = setOf(
        "android", "data", "obb", "cache", "system",
        "proc", "sys", "root", ".thumbnails",
        "dcim", "pictures", "movies", "music",
        "notifications", "ringtones", "podcasts",
        "alarms", "audiobooks", "backup", "backups"
    )
    
    suspend fun checkForNewBooks(): Job {
        return scanBooks(force = false)
    }
    
    /**
     * Запуск сканирования с проверкой дубликатов
     */
    suspend fun scanBooks(force: Boolean = false): Job {
        if (_isScanning.get()) {
            Log.d(TAG, "Scan already in progress")
            return scanJob ?: Job()
        }
        
        if (!force) {
            val now = System.currentTimeMillis()
            if (now - lastScanTime < SCAN_COOLDOWN_MS) {
                Log.d(TAG, "Recent scan, skipping")
                return Job()
            }
            
            try {
                val currentHash = scanPrefs.calculateMediaStoreHash()
                if (!scanPrefs.isLibraryChanged(currentHash)) {
                    Log.d(TAG, "Library unchanged, skipping scan")
                    return Job()
                }
                scanPrefs.saveLibraryHash(currentHash)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking library hash", e)
                // Продолжаем сканирование даже если проверка не удалась
            }
        }
        
        _isScanning.set(true)
        etaCalculator.reset()
        
        scanJob = scannerScope.launch {
            try {
                performScan()
                lastScanTime = System.currentTimeMillis()
            } catch (e: CancellationException) {
                Log.d(TAG, "Scan cancelled")
                progressManager.forceUpdate {
                    it.copy(phase = ScanPhase.CANCELLED, overallProgress = 100)
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory during scan", e)
                System.gc()
                
                try {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Недостаточно памяти для сканирования",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (toastError: Exception) {
                    Log.e(TAG, "Cannot show toast", toastError)
                }
                
                progressManager.forceUpdate {
                    it.copy(
                        phase = ScanPhase.ERROR,
                        overallProgress = 100,
                        currentFile = "Ошибка памяти"
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Scan error", e)
                
                // Показываем ошибку пользователю
                try {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Ошибка сканирования: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (toastError: Exception) {
                    Log.e(TAG, "Cannot show toast", toastError)
                }
                
                progressManager.forceUpdate {
                    it.copy(
                        phase = ScanPhase.ERROR,
                        overallProgress = 100,
                        currentFile = "Ошибка: ${e.message}"
                    )
                }
            } finally {
                _isScanning.set(false)
                scanJob = null
                try {
                    cleanupCache()
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning cache", e)
                }
            }
        }
        
        return scanJob!!
    }
    
    /**
     * Основной процесс сканирования
     */
    private suspend fun performScan() {
        val startTime = System.currentTimeMillis()
        
        Log.d(TAG, "Starting scan")
        
        progressManager.forceUpdate {
            it.copy(phase = ScanPhase.INITIALIZING, overallProgress = 0)
        }
        
        // Проверка БД
        try {
            val testBooks = withContext(Dispatchers.IO) {
                bookDao.getAllBooksSync()
            }
            Log.d(TAG, "DB OK. Books in DB: ${testBooks.size}")
        } catch (e: Exception) {
            Log.e(TAG, "DB check failed", e)
            // Не прерываем сканирование
        }
        
        val bookFiles = scanFilesWithProgress()
        
        if (bookFiles.isEmpty()) {
            progressManager.forceUpdate {
                it.copy(
                    phase = ScanPhase.COMPLETED,
                    overallProgress = 100,
                    booksFound = 0,
                    booksAdded = 0
                )
            }
            return
        }
        
        progressManager.forceUpdate {
            it.copy(
                phase = ScanPhase.ANALYZING_CACHE,
                overallProgress = 20,
                booksFound = bookFiles.size
            )
        }
        
        val booksToProcess = analyzeCache(bookFiles)
        
        if (booksToProcess.isEmpty()) {
            progressManager.forceUpdate {
                it.copy(
                    phase = ScanPhase.COMPLETED,
                    overallProgress = 100,
                    booksFound = bookFiles.size,
                    booksAdded = 0,
                    booksSkipped = bookFiles.size
                )
            }
            try {
                scanPrefs.saveLastScanCount(0)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving scan count", e)
            }
            return
        }
        
        progressManager.forceUpdate {
            it.copy(
                phase = ScanPhase.PROCESSING_BOOKS,
                overallProgress = 30,
                booksFound = booksToProcess.size,
                booksProcessed = 0,
                booksAdded = 0
            )
        }
        
        val addedCount = processBooksInBatches(booksToProcess)
        
        val duration = (System.currentTimeMillis() - startTime) / 1000
        
        try {
            scanPrefs.saveLastScanCount(addedCount)
            scanPrefs.saveLastScanDuration(duration)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving scan stats", e)
        }
        
        progressManager.forceUpdate {
            it.copy(
                phase = ScanPhase.COMPLETED,
                overallProgress = 100,
                phaseProgress = 100,
                booksAdded = addedCount,
                eta = "Завершено за ${duration}с",
                speed = "Готово"
            )
        }
        
        Log.d(TAG, "Scan completed in ${duration}s. Added: $addedCount")
    }
    
    /**
     * Сканирование файлов с прогрессом
     */
    private suspend fun scanFilesWithProgress(): List<File> {
        val bookFiles = mutableListOf<File>()
        
        progressManager.forceUpdate {
            it.copy(phase = ScanPhase.SCANNING_FILES, overallProgress = 5)
        }
        
        val rootDirs = getDefaultScanDirectories()
        Log.d(TAG, "Found ${rootDirs.size} scan directories")
        
        for ((index, rootDir) in rootDirs.withIndex()) {
            if (bookFiles.size >= MAX_FILES_TO_SCAN) {
                Log.w(TAG, "Reached max files limit: $MAX_FILES_TO_SCAN")
                break
            }
            
            if (!rootDir.exists() || !rootDir.canRead()) {
                Log.d(TAG, "Cannot access directory: ${rootDir.absolutePath}")
                continue
            }
            
            val scanProgress = 5 + ((index.toFloat() / rootDirs.size) * 10).toInt()
            progressManager.update {
                it.copy(phaseProgress = scanProgress, currentFile = "Сканирование: ${rootDir.name}")
            }
            
            Log.d(TAG, "Scanning directory: ${rootDir.absolutePath}")
            
            try {
                scanDirectory(rootDir, bookFiles)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception scanning: ${rootDir.absolutePath}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning: ${rootDir.absolutePath}", e)
            }
        }
        
        val uniqueFiles = bookFiles.distinctBy { 
            try { 
                it.canonicalFile.absolutePath 
            } catch (e: Exception) { 
                it.absolutePath 
            } 
        }

        Log.d(TAG, "Found ${uniqueFiles.size} unique book files")
        
        progressManager.forceUpdate {
            it.copy(
                phase = ScanPhase.SCANNING_FILES,
                phaseProgress = 100,
                overallProgress = 15,
                booksFound = uniqueFiles.size,
                memoryUsed = memoryMonitor.getMemoryStatus()
            )
        }
        
        return uniqueFiles
    }
    
    /**
     * Рекурсивное сканирование директории
     */
    private fun scanDirectory(directory: File, result: MutableList<File>, depth: Int = 0) {
        if (result.size >= MAX_FILES_TO_SCAN) return
        if (depth > MAX_SCAN_DEPTH) return
        
        try {
            if (directory.canonicalFile != directory.absoluteFile) {
                return
            }
            
            if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
                return
            }
            
            val entries = try {
                directory.listFiles()
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception listing: ${directory.absolutePath}", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error listing: ${directory.absolutePath}", e)
                null
            }
            
            if (entries == null) return
            
            val (files, directories) = entries.partition { 
                try { it.isFile } catch (e: Exception) { false }
            }
            
            for (file in files) {
                if (result.size >= MAX_FILES_TO_SCAN) break
                
                try {
                    if (isBookFile(file) && file.canRead()) {
                        result.add(file)
                    }
                } catch (e: Exception) {
                    // Пропускаем проблемные файлы
                }
            }
            
            if (result.size < MAX_FILES_TO_SCAN) {
                for (dir in directories) {
                    if (result.size >= MAX_FILES_TO_SCAN) break
                    
                    val dirName = try {
                        dir.name.lowercase()
                    } catch (e: Exception) {
                        ""
                    }
                    
                    if (dirName.startsWith(".") || excludePaths.contains(dirName)) {
                        continue
                    }
                    
                    try {
                        if (dir.canonicalPath == directory.canonicalPath) {
                            continue
                        }
                    } catch (e: Exception) {
                        continue
                    }
                    
                    scanDirectory(dir, result, depth + 1)
                }
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${directory.absolutePath}", e)
        } catch (e: StackOverflowError) {
            Log.e(TAG, "Stack overflow: ${directory.absolutePath}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning: ${directory.absolutePath}", e)
        }
    }
    
    /**
     * Проверка, является ли файл книгой
     */
    private fun isBookFile(file: File): Boolean {
        return try {
            if (!file.isFile || !file.canRead()) return false
            
            val name = file.name.lowercase()
            val extension = file.extension.lowercase()
            
            when (extension) {
                "fb2", "epub", "fb3", "mobi", "azw", "azw3", "fbz" -> true
                "zip" -> name.endsWith(".fb2.zip") || 
                        name.endsWith(".fb3.zip") ||
                        name.endsWith(".epub.zip")
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Получение директорий для сканирования
     */
    private fun getDefaultScanDirectories(): List<File> {
        val dirs = mutableListOf<File>()
        val seenPaths = mutableSetOf<String>()
        
        fun addDirectory(dir: File) {
            try {
                val canonicalPath = dir.canonicalPath
                if (seenPaths.add(canonicalPath)) {
                    val isAccessible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        dir.exists() && dir.canRead() && 
                        !dir.absolutePath.contains("/Android/data") &&
                        !dir.absolutePath.contains("/Android/obb")
                    } else {
                        dir.exists() && dir.canRead()
                    }
                    
                    if (isAccessible) {
                        dirs.add(dir)
                    }
                }
            } catch (e: Exception) {
                // Пропускаем
            }
        }
        
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            addDirectory(externalStorage)
            
            val bookDirs = listOf(
                "Books", "books", "Книги", "книги",
                "Download", "Downloads", "Загрузки",
                "Documents", "Документы",
                "Ebooks", "eBooks", "Library", "library",
                "FB2", "EPUB", "MOBI", "FictionBook",
                "Read", "Reading", "Чтение"
            )
            
            for (dirName in bookDirs) {
                addDirectory(File(externalStorage, dirName))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting external storage", e)
        }

        try {
            val appDirs = context.getExternalFilesDirs(null)
            for (dir in appDirs) {
                if (dir != null) addDirectory(dir)
            }
            
            if (context.filesDir != null) addDirectory(context.filesDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app directories", e)
        }

        return dirs
    }
    
    /**
     * Анализ кеша и отбор книг для обработки (оптимизированная версия)
     */
    private suspend fun analyzeCache(bookFiles: List<File>): List<File> {
        return try {
            val existingPaths = withContext(Dispatchers.IO) {
                try {
                    bookDao.getAllBookPaths()
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting book paths from DB", e)
                    emptySet()
                }
            }
            
            bookFiles.filter { file ->
                val path = file.absolutePath
                val canonicalPath = try { file.canonicalFile.absolutePath } catch (e: Exception) { path }
                
                path !in existingPaths && canonicalPath !in existingPaths
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in analyzeCache", e)
            bookFiles // Если ошибка — пробуем обработать все файлы
        }
    }
    
    /**
     * Обработка книг пакетами
     */
    private suspend fun processBooksInBatches(books: List<File>): Int {
        val totalBooks = books.size
        var processedCount = 0
        var addedCount = 0
        
        val sortedBooks = books.sortedBy { it.length() }
        
        sortedBooks.chunked(BATCH_SIZE).forEachIndexed { _, batch ->
            if (!_isScanning.get()) {
                throw CancellationException()
            }
            
            val batchResults = processBookBatch(batch)
            
            batchResults.forEach { result ->
                if (result is ProcessResult.Success) {
                    addedCount++
                }
            }
            
            val entities = batchResults.mapNotNull { it.entity }
            if (entities.isNotEmpty()) {
                try {
                    if (entities.size > 1) {
                        bookDao.insertBooks(entities)
                    } else {
                        bookDao.insertBook(entities.first())
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Batch insert failed: ${e.message}")
                    for (entity in entities) {
                        try {
                            bookDao.insertBookSafely(entity)
                        } catch (e2: Throwable) {
                            Log.e(TAG, "Failed to insert: ${entity.title}", e2)
                        }
                    }
                }
                
                try {
                    updateCache(batchResults, batch)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to update cache", e)
                }
            }
            
            processedCount += batch.size
            
            val overallProgress = 30 + ((processedCount.toFloat() / totalBooks) * 65).toInt()
            
            progressManager.update { current ->
                current.copy(
                    phase = ScanPhase.PROCESSING_BOOKS,
                    phaseProgress = ((processedCount.toFloat() / totalBooks) * 100).toInt(),
                    overallProgress = overallProgress,
                    booksFound = totalBooks,
                    booksProcessed = processedCount,
                    booksAdded = addedCount,
                    currentFile = batch.lastOrNull()?.name ?: "",
                    eta = etaCalculator.calculate(processedCount, totalBooks),
                    speed = etaCalculator.getSpeed(),
                    memoryUsed = memoryMonitor.getMemoryStatus()
                )
            }
            
            if (memoryMonitor.isMemoryLow()) {
                delay(500)
                System.gc()
            }
        }
        
        return addedCount
    }
    
    /**
     * Обработка одного батча книг (с защитой от ошибок)
     */
    private suspend fun processBookBatch(batch: List<File>): List<ProcessResult> {
        return withContext(Dispatchers.IO) {
            batch.mapNotNull { file ->
                if (!_isScanning.get()) {
                    return@mapNotNull null
                }
                
                try {
                    val result = withTimeoutOrNull(TIMEOUT_PER_BOOK_MS) {
                        processBook(file)
                    }
                    
                    result ?: ProcessResult.Skipped
                } catch (e: Throwable) {
                    Log.e(TAG, "Error processing ${file.name}", e)
                    ProcessResult.Error(null, Exception(e))
                }
            }
        }
    }
    
    /**
     * Обработка одной книги
     */
    private suspend fun processBook(file: File): ProcessResult {
        return try {
            if (!file.exists() || !file.canRead()) {
                return ProcessResult.Skipped
            }
            
            val fileSize = file.length()
            if (fileSize <= 0 || fileSize > MAX_FILE_SIZE_BYTES) {
                return ProcessResult.Skipped
            }
            
            if (file.name.lowercase().endsWith(".zip")) {
                val sizeMB = fileSize / (1024 * 1024)
                if (sizeMB > MAX_ZIP_SIZE_MB) {
                    return ProcessResult.Skipped
                }
            }
            
            val processor = getProcessorForFile(file) ?: return ProcessResult.Skipped
            
            val bookSource = BookSource(
                uri = Uri.fromFile(file),
                name = file.name,
                size = fileSize,
                modified = file.lastModified(),
                realPath = file.absolutePath
            )
            
            val entity = processor.process(bookSource, context)
            if (entity != null) {
                ProcessResult.Success(entity)
            } else {
                ProcessResult.Skipped
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${file.name}", e)
            ProcessResult.Error(exception = e)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM: ${file.name}", e)
            System.gc()
            ProcessResult.Error(exception = Exception(e))
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${file.name}", e)
            ProcessResult.Error(exception = e)
        }
    }
    
    /**
     * Получение процессора для файла
     */
    private fun getProcessorForFile(file: File): BookProcessor? {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".fb2") || name.endsWith(".fb2.zip") || name.endsWith(".fbz") -> fb2Processor
            name.endsWith(".epub") -> epubProcessor
            name.endsWith(".fb3") || name.endsWith(".fb3.zip") -> fb3Processor
            name.endsWith(".mobi") || name.endsWith(".azw") || name.endsWith(".azw3") -> mobiProcessor
            name.endsWith(".zip") -> zipProcessor
            else -> null
        }
    }
    
    /**
     * Обновление кеша
     */
    private suspend fun updateCache(results: List<ProcessResult>, files: List<File>) {
        try {
            val cacheList = results.zip(files).mapNotNull { (result, file) ->
                val entity = result.entity ?: return@mapNotNull null
                BookCache(
                    path = file.absolutePath,
                    fingerprint = entity.sha1,
                    textHash = null,
                    author = entity.author ?: "Неизвестен",
                    title = entity.title,
                    fileSize = file.length(),
                    lastScanned = System.currentTimeMillis(),
                    format = file.extension.lowercase()
                )
            }
            
            if (cacheList.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        db.bookCacheDao().insertAll(cacheList)
                    } catch (e: Exception) {
                        Log.e(TAG, "DB insert cache failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update cache", e)
        }
    }
    
    /**
     * Отмена сканирования
     */
    fun cancelScanning() {
        _isScanning.set(false)
        scanJob?.cancel()
        scanJob = null
    }
    
    /**
     * Очистка кеша
     */
    private suspend fun cleanupCache() {
        try {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val oldCache = db.bookCacheDao().getAll().filter {
                    System.currentTimeMillis() - it.lastScanned > CACHE_CLEANUP_INTERVAL
                }
                
                if (oldCache.isNotEmpty()) {
                    val paths = oldCache.map { it.path }
                    paths.chunked(500).forEach { chunk ->
                        db.bookCacheDao().deleteByPaths(chunk)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache cleanup failed", e)
        }
    }
    
    fun getState(): ScannerState {
        return progress.value.toScannerState()
    }
    
    suspend fun needsScan(): Boolean {
        if (_isScanning.get()) return false
        
        val now = System.currentTimeMillis()
        if (now - lastScanTime < SCAN_COOLDOWN_MS) return false
        
        return try {
            val currentHash = scanPrefs.calculateMediaStoreHash()
            scanPrefs.isLibraryChanged(currentHash)
        } catch (e: Exception) {
            Log.e(TAG, "Error in needsScan", e)
            true // Если ошибка — лучше просканировать
        }
    }
    
    sealed interface ProcessResult {
        val entity: BookEntity?
        
        data class Success(override val entity: BookEntity) : ProcessResult
        data object Skipped : ProcessResult {
            override val entity: BookEntity? = null
        }
        data class Error(override val entity: BookEntity? = null, val exception: Exception? = null) : ProcessResult
    }
}
