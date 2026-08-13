package com.nightread.app.scanner

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.sync.Mutex
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
    private val scannerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dbMutex = Mutex()
    
    // Исключаемые папки
    private val excludePaths = setOf(
        "Android", "data", "obb", "cache", "system",
        "proc", "sys", "root", ".thumbnails",
        "dcim", "pictures", "movies", "music",
        "notifications", "ringtones", "podcasts"
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
            
            val currentHash = scanPrefs.calculateMediaStoreHash()
            if (!scanPrefs.isLibraryChanged(currentHash)) {
                Log.d(TAG, "Library unchanged, skipping scan")
                return Job()
            }
            scanPrefs.saveLibraryHash(currentHash)
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
            } catch (e: Throwable) {
                Log.e(TAG, "Scan error", e)
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
                cleanupCache()
            }
        }
        
        return scanJob!!
    }
    
    /**
     * Основной процесс сканирования
     */
    private suspend fun performScan() {
        val startTime = System.currentTimeMillis()
        
        progressManager.forceUpdate {
            it.copy(phase = ScanPhase.INITIALIZING, overallProgress = 0)
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
            scanPrefs.saveLastScanCount(0)
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

        try {
            val db = AppDatabase.getDatabase(context)
            val allBooks = db.bookDao().getAllBooksSync()
            val booksToDelete = mutableSetOf<String>()
            val bySha1 = allBooks.groupBy { it.sha1 }
            for ((_, group) in bySha1) {
                if (group.size > 1) {
                    for (i in 1 until group.size) {
                        booksToDelete.add(group[i].sha1)
                    }
                }
            }
            val remaining = allBooks.filter { !booksToDelete.contains(it.sha1) }
            val byTitleAuthor = remaining.groupBy { "${it.title.trim().lowercase()}_${(it.author ?: "").trim().lowercase()}" }
            for ((key, group) in byTitleAuthor) {
                if (key.isNotBlank() && !key.startsWith("неизвестен") && group.size > 1) {
                    for (i in 1 until group.size) {
                        booksToDelete.add(group[i].sha1)
                    }
                }
            }
            if (booksToDelete.isNotEmpty()) {
                booksToDelete.chunked(500).forEach { chunk ->
                    db.bookDao().deleteBooksBySha1s(chunk)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning duplicates in performScan", e)
        }
        
        val duration = (System.currentTimeMillis() - startTime) / 1000
        scanPrefs.saveLastScanCount(addedCount)
        scanPrefs.saveLastScanDuration(duration)
        
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
        
        for ((index, rootDir) in rootDirs.withIndex()) {
            if (!rootDir.exists() || !rootDir.canRead()) continue
            
            val progress = 5 + ((index.toFloat() / rootDirs.size) * 10).toInt()
            progressManager.update {
                it.copy(phaseProgress = progress, currentFile = "Сканирование: ${rootDir.name}")
            }
            
            scanDirectory(rootDir, bookFiles)
        }
        
        val uniqueFiles = bookFiles.distinctBy { 
            try { 
                it.canonicalFile.absolutePath 
            } catch (e: Exception) { 
                it.absolutePath 
            } 
        }

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
    private fun scanDirectory(directory: File, result: MutableList<File>) {
        try {
            if (!directory.exists() || !directory.isDirectory) return
            
            directory.walkTopDown()
                .onEnter { dir ->
                    try {
                        val dirName = dir.name.lowercase()
                        !excludePaths.contains(dirName) && !dirName.startsWith(".")
                    } catch (e: Throwable) {
                        false
                    }
                }
                .onFail { _, _ -> /* Ignore permission and I/O failures gracefully */ }
                .filter { file ->
                    try {
                        file.isFile && isBookFile(file)
                    } catch (e: Throwable) {
                        false
                    }
                }
                .take((MAX_FILES_TO_SCAN - result.size).coerceAtLeast(0))
                .forEach { file ->
                    if (result.size < MAX_FILES_TO_SCAN) {
                        result.add(file)
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "Error scanning directory", e)
        }
    }
    
    /**
     * Проверка, является ли файл книгой
     */
    private fun isBookFile(file: File): Boolean {
        return try {
            val name = file.name.lowercase()
            name.endsWith(".fb2") || name.endsWith(".fb2.zip") || name.endsWith(".fbz") ||
                    name.endsWith(".epub") || name.endsWith(".fb3") || name.endsWith(".fb3.zip") ||
                    name.endsWith(".mobi") || name.endsWith(".azw") || name.endsWith(".azw3") ||
                    name.endsWith(".zip")
        } catch (e: Throwable) {
            false
        }
    }
    
    /**
     * Получение директорий для сканирования
     */
    private fun getDefaultScanDirectories(): List<File> {
        val dirs = mutableListOf<File>()
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            val bookDirs = listOf(
                "Books", "books", "Книги", "книги",
                "Download", "Downloads", "Загрузки",
                "Documents", "Документы",
                "Ebooks", "eBooks", "Library", "library"
            )
            
            for (dirName in bookDirs) {
                try {
                    val dir = File(externalStorage, dirName)
                    if (dir.exists() && dir.canRead()) {
                        dirs.add(dir)
                    }
                } catch (e: Throwable) {}
            }
            
            if (dirs.isEmpty() && externalStorage.exists() && externalStorage.canRead()) {
                dirs.add(externalStorage)
            }
        } catch (e: Throwable) {}

        try {
            val appDirs = context.getExternalFilesDirs(null)
            for (dir in appDirs) {
                if (dir != null && dir.exists() && dir.canRead()) {
                    dirs.add(dir)
                }
            }
            if (context.filesDir != null && context.filesDir.exists()) {
                dirs.add(context.filesDir)
            }
        } catch (e: Throwable) {}

        return dirs
    }
    
    /**
     * Анализ кеша и отбор книг для обработки
     */
    private suspend fun analyzeCache(bookFiles: List<File>): List<File> {
        val existingPaths = getExistingPaths(bookFiles)
        
        val db = AppDatabase.getDatabase(context)
        val allBooks = try { db.bookDao().getAllBooksSync() } catch (e: Exception) { emptyList() }
        val canonicalExistingPaths = allBooks.mapNotNull { it.filePath?.let { p -> try { File(p).canonicalPath } catch (e: Exception) { p } } }.toSet()

        return bookFiles.filter { file ->
            val path = file.absolutePath
            val canonicalPath = try { file.canonicalFile.absolutePath } catch (e: Exception) { path }
            !existingPaths.contains(path) && 
            !canonicalExistingPaths.contains(canonicalPath)
        }
    }
    
    /**
     * Получение существующих путей из БД
     */
    private suspend fun getExistingPaths(bookFiles: List<File>): Set<String> {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val batchSize = 100
                val existingSet = mutableSetOf<String>()
                
                bookFiles.chunked(batchSize).forEach { batch ->
                    val paths = batch.map { it.absolutePath }
                    val existing = db.bookDao().getBooksByPaths(paths)
                    existingSet.addAll(existing.mapNotNull { it.filePath })
                }
                
                existingSet
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get existing books", e)
                emptySet()
            }
        }
    }
    
    /**
     * Получение закешированных путей
     */
    private suspend fun getCachedPaths(bookFiles: List<File>): Set<String> {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val batchSize = 100
                val cacheSet = mutableSetOf<String>()
                
                bookFiles.chunked(batchSize).forEach { batch ->
                    val paths = batch.map { it.absolutePath }
                    val cached = db.bookCacheDao().getByPaths(paths)
                    cacheSet.addAll(cached.map { it.path })
                }
                
                cacheSet
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cache", e)
                emptySet()
            }
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
                Log.d(TAG, "Scan cancelled")
                throw CancellationException()
            }
            
            val batchResults = processBookBatch(batch)
            
            batchResults.zip(batch).forEach { (result, file) ->
                if (result is ProcessResult.Success) {
                    addedCount++
                }
            }
            
            val entities = batchResults.mapNotNull { it.entity }
            if (entities.isNotEmpty()) {
                try {
                    bookDao.insertBooks(entities)
                } catch (e: Throwable) {
                    Log.w(TAG, "Batch insert failed, falling back to individual inserts: ${e.message}")
                    for (entity in entities) {
                        try {
                            bookDao.insertBook(entity)
                        } catch (e2: Throwable) {
                            Log.e(TAG, "Failed to insert single book ${entity.title}", e2)
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
            
            val progress = 30 + ((processedCount.toFloat() / totalBooks) * 65).toInt()
            
            progressManager.update { current ->
                current.copy(
                    phase = ScanPhase.PROCESSING_BOOKS,
                    phaseProgress = ((processedCount.toFloat() / totalBooks) * 100).toInt(),
                    overallProgress = progress,
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
                Log.w(TAG, "Memory low, pausing...")
                delay(500)
                System.gc()
            }
        }
        
        return addedCount
    }
    
    /**
     * Обработка одного батча книг
     */
    private suspend fun processBookBatch(batch: List<File>): List<ProcessResult> {
        return withContext(Dispatchers.IO) {
            batch.map { file ->
                if (!_isScanning.get()) {
                    return@map ProcessResult.Error(null, null)
                }
                
                try {
                    val result = withTimeoutOrNull(TIMEOUT_PER_BOOK_MS) {
                        processBook(file)
                    }
                    
                    result ?: run {
                        Log.w(TAG, "Timeout processing: ${file.name}")
                        ProcessResult.Error(null, null)
                    }
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
        if (!file.exists() || !file.canRead()) {
            return ProcessResult.Skipped
        }
        
        if (file.name.lowercase().endsWith(".zip")) {
            val sizeMB = file.length() / (1024 * 1024)
            if (sizeMB > MAX_ZIP_SIZE_MB) {
                Log.w(TAG, "Skipping large ZIP: ${file.name} ($sizeMB MB)")
                return ProcessResult.Skipped
            }
        }
        
        val processor = getProcessorForFile(file) ?: return ProcessResult.Skipped
        
        val bookSource = BookSource(
            uri = Uri.fromFile(file),
            name = file.name,
            size = file.length(),
            modified = file.lastModified(),
            realPath = file.absolutePath
        )
        
        val entity = processor.process(bookSource, context)
        return if (entity != null) {
            ProcessResult.Success(entity)
        } else {
            ProcessResult.Error()
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
     * Обновление кеша после обработки
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
            
            val db = AppDatabase.getDatabase(context)
            db.bookCacheDao().insertAll(cacheList)
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
        Log.d(TAG, "Scan cancelled")
    }
    
    /**
     * Очистка старого кеша
     */
    private suspend fun cleanupCache() {
        try {
            val db = AppDatabase.getDatabase(context)
            val oldCache = db.bookCacheDao().getAll().filter {
                System.currentTimeMillis() - it.lastScanned > CACHE_CLEANUP_INTERVAL
            }
            
            if (oldCache.isNotEmpty()) {
                val paths = oldCache.map { it.path }
                db.bookCacheDao().deleteByPaths(paths)
                Log.d(TAG, "Cleaned ${paths.size} old cache entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache cleanup failed", e)
        }
    }
    
    /**
     * Получение состояния сканирования
     */
    fun getState(): ScannerState {
        return progress.value.toScannerState()
    }
    
    /**
     * Проверка, нужно ли сканирование
     */
    suspend fun needsScan(): Boolean {
        if (_isScanning.get()) return false
        
        val now = System.currentTimeMillis()
        if (now - lastScanTime < SCAN_COOLDOWN_MS) return false
        
        val currentHash = scanPrefs.calculateMediaStoreHash()
        return scanPrefs.isLibraryChanged(currentHash)
    }
    
    /**
     * Результат обработки
     */
    sealed interface ProcessResult {
        val entity: BookEntity?
        
        data class Success(override val entity: BookEntity) : ProcessResult
        data object Skipped : ProcessResult {
            override val entity: BookEntity? = null
        }
        data class Error(override val entity: BookEntity? = null, val exception: Exception? = null) : ProcessResult
    }
}
