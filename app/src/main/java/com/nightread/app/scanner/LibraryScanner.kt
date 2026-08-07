package com.nightread.app.scanner

import android.content.Context
import android.os.Environment
import android.util.Log
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookCache
import com.nightread.app.data.BookDao
import com.nightread.app.data.BookEntity
import com.nightread.app.scanner.processors.*
import com.nightread.app.service.NewBookScanState
import com.nightread.app.service.ScannerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

// ==================== CONSTANTS ====================

private const val TAG = "LibraryScanner"
private const val BATCH_SIZE = 25
private const val MAX_ZIP_SIZE_MB = 25
private const val TIMEOUT_PER_BOOK_MS = 30_000L
private const val MAX_FILES_TO_SCAN = 10000
private const val MEMORY_LOW_THRESHOLD = 0.15f

// Прогресс-константы
private const val PROGRESS_INIT = 0
private const val PROGRESS_SCANNING_START = 5
private const val PROGRESS_SCANNING_END = 15
private const val PROGRESS_ANALYZING_START = 20
private const val PROGRESS_ANALYZING_END = 25
private const val PROGRESS_PROCESSING_START = 30
private const val PROGRESS_PROCESSING_END = 95
private const val PROGRESS_FINALIZING = 100

// ==================== SCAN PHASE & PROGRESS ====================

enum class ScanPhase {
    INITIALIZING,
    SCANNING_FILES,
    ANALYZING_CACHE,
    PROCESSING_BOOKS,
    COMPLETED,
    CANCELLED,
    ERROR
}

data class BookScanProgress(
    val phase: ScanPhase = ScanPhase.INITIALIZING,
    val phaseProgress: Int = 0,
    val overallProgress: Int = 0,
    val booksFound: Int = 0,
    val booksProcessed: Int = 0,
    val booksAdded: Int = 0,
    val booksSkipped: Int = 0,
    val currentFile: String = "",
    val eta: String = "Вычисляется...",
    val speed: String = "Вычисляется...",
    val memoryUsed: String = ""
) {
    fun toScannerState(): ScannerState {
        return ScannerState(
            isScanning = phase != ScanPhase.COMPLETED && phase != ScanPhase.CANCELLED && phase != ScanPhase.ERROR,
            status = when (phase) {
                ScanPhase.INITIALIZING -> "Инициализация..."
                ScanPhase.SCANNING_FILES -> "Поиск файлов: $booksFound"
                ScanPhase.ANALYZING_CACHE -> "Анализ кэша..."
                ScanPhase.PROCESSING_BOOKS -> "Обработка: $booksProcessed / $booksFound"
                ScanPhase.COMPLETED -> "Готово. Добавлено: $booksAdded"
                ScanPhase.CANCELLED -> "Сканирование отменено"
                ScanPhase.ERROR -> currentFile
            },
            totalFiles = booksFound,
            processedFiles = booksProcessed,
            addedBooks = booksAdded,
            skippedBooks = booksSkipped,
            progress = overallProgress
        )
    }
}

// ==================== SEALED RESULT ====================

sealed interface ProcessResult {
    val entity: BookEntity?
    
    data class Success(override val entity: BookEntity) : ProcessResult
    data object Skipped : ProcessResult {
        override val entity: BookEntity? = null
    }
    data class Error(override val entity: BookEntity? = null, val exception: Exception? = null) : ProcessResult
}

// ==================== PROGRESS MANAGER (FIXED) ====================

class ProgressManager(
    private val updateIntervalMs: Long = 500L
) {
    private val _progress = MutableStateFlow(BookScanProgress())
    val progress: StateFlow<BookScanProgress> = _progress.asStateFlow()
    
    private var lastUpdate = 0L
    private val mutex = Mutex()
    
    suspend fun update(block: suspend (BookScanProgress) -> BookScanProgress) {
        mutex.withLock {
            val current = _progress.value
            val newProgress = block(current)
            val now = System.currentTimeMillis()
            
            val timePassed = now - lastUpdate
            val phaseChanged = newProgress.phase != current.phase
            val importantFieldsChanged = newProgress.booksAdded != current.booksAdded ||
                    newProgress.booksProcessed != current.booksProcessed
            
            if (timePassed >= updateIntervalMs || phaseChanged || importantFieldsChanged) {
                _progress.value = newProgress
                lastUpdate = now
                NewBookScanState.updateState(newProgress.toScannerState())
            }
        }
    }
    
    suspend fun forceUpdate(block: suspend (BookScanProgress) -> BookScanProgress) {
        mutex.withLock {
            val newProgress = block(_progress.value)
            _progress.value = newProgress
            NewBookScanState.updateState(newProgress.toScannerState())
            lastUpdate = System.currentTimeMillis()
        }
    }
}

// ==================== ETA CALCULATOR (FIXED) ====================

class EtaCalculator {
    private var startTime = 0L
    private var lastProgress = 0
    private var lastTime = 0L
    private var emaSpeed = 0.0
    private val smoothingFactor = 0.3
    
    fun calculate(processed: Int, total: Int): String {
        if (processed <= 0 || total <= 0) return "Вычисляется..."
        
        val now = System.currentTimeMillis()
        
        if (lastProgress == 0) {
            startTime = now
            lastProgress = processed
            lastTime = now
            return "Вычисляется..."
        }
        
        val timeDiff = (now - lastTime) / 1000.0
        if (timeDiff < 1.0) return "Вычисляется..."
        
        val instantSpeed = (processed - lastProgress) / timeDiff
        
        if (instantSpeed > 0) {
            if (emaSpeed == 0.0) {
                emaSpeed = instantSpeed
            } else {
                emaSpeed = smoothingFactor * instantSpeed + (1 - smoothingFactor) * emaSpeed
            }
        }
        
        val remaining = total - processed
        val etaSeconds = if (emaSpeed > 0) (remaining / emaSpeed).toLong() else 0L
        
        lastProgress = processed
        lastTime = now
        
        return when {
            etaSeconds <= 0 -> "Вычисляется..."
            etaSeconds < 60 -> "~${etaSeconds} сек"
            etaSeconds < 3600 -> "~${etaSeconds / 60} мин"
            else -> {
                val hours = etaSeconds / 3600
                val minutes = (etaSeconds % 3600) / 60
                "~${hours}ч ${minutes}м"
            }
        }
    }
    
    fun getSpeed(): String {
        return if (emaSpeed > 0) {
            val booksPerMin = emaSpeed * 60
            "${String.format("%.1f", booksPerMin)} книг/мин"
        } else {
            "Вычисляется..."
        }
    }
    
    fun reset() {
        startTime = 0L
        lastProgress = 0
        lastTime = 0L
        emaSpeed = 0.0
    }
}

// ==================== MEMORY MONITOR (FIXED) ====================

class MemoryMonitor {
    fun getMemoryStatus(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val freeMemory = runtime.freeMemory()
        
        val maxMB = maxMemory / (1024 * 1024)
        val usedMB = usedMemory / (1024 * 1024)
        val freeMB = freeMemory / (1024 * 1024)
        
        return "$usedMB MB / $maxMB MB (свободно $freeMB MB)"
    }
    
    fun isMemoryLow(): Boolean {
        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        return freeMemory < maxMemory * MEMORY_LOW_THRESHOLD
    }
    
    fun getFreeMemoryMB(): Long {
        return Runtime.getRuntime().freeMemory() / (1024 * 1024)
    }
}

// ==================== MAIN SCANNER (FIXED) ====================

class LibraryScanner(
    private val context: Context,
    private val bookDao: BookDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    constructor(context: Context, bookDao: BookDao) : this(
        context = context,
        bookDao = bookDao,
        coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )

    private val fb2Processor = Fb2Processor()
    private val epubProcessor = EpubProcessor()
    private val fb3Processor = Fb3Processor()
    private val mobiProcessor = MobiProcessor()
    private val zipProcessor = ZipProcessor()
    
    private var scanJob: Job? = null
    private val isCancelled = AtomicBoolean(false)
    private val progressManager = ProgressManager()
    private val etaCalculator = EtaCalculator()
    private val memoryMonitor = MemoryMonitor()
    
    val progress: StateFlow<BookScanProgress> = progressManager.progress
    
    suspend fun scanBooks(): Job {
        scanJob?.cancel()
        scanJob = coroutineScope.launch(dispatcher + SupervisorJob()) {
            try {
                isCancelled.set(false)
                etaCalculator.reset()
                performScan()
            } catch (e: CancellationException) {
                Log.d(TAG, "Scan cancelled")
                progressManager.forceUpdate {
                    it.copy(phase = ScanPhase.CANCELLED, overallProgress = PROGRESS_FINALIZING)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                progressManager.forceUpdate {
                    it.copy(
                        phase = ScanPhase.ERROR,
                        overallProgress = PROGRESS_FINALIZING,
                        currentFile = "Ошибка: ${e.message}"
                    )
                }
            }
        }
        return scanJob!!
    }

    suspend fun checkForNewBooks(): Job {
        return scanBooks()
    }
    
    fun cancelScanning() {
        isCancelled.set(true)
        scanJob?.cancel()
        scanJob = null
    }
    
    private suspend fun performScan() {
        progressManager.forceUpdate {
            it.copy(phase = ScanPhase.INITIALIZING, phaseProgress = 0, overallProgress = PROGRESS_INIT)
        }
        
        val startTime = System.currentTimeMillis()
        
        val bookFiles = scanFilesWithProgress()
        
        if (isCancelled.get()) throw CancellationException()
        
        if (bookFiles.isEmpty()) {
            progressManager.forceUpdate {
                it.copy(
                    phase = ScanPhase.COMPLETED,
                    overallProgress = PROGRESS_FINALIZING,
                    booksFound = 0,
                    booksAdded = 0
                )
            }
            return
        }
        
        progressManager.forceUpdate {
            it.copy(
                phase = ScanPhase.ANALYZING_CACHE,
                phaseProgress = 0,
                overallProgress = PROGRESS_ANALYZING_START,
                booksFound = bookFiles.size
            )
        }
        
        val booksToProcess = analyzeCache(bookFiles)
        
        if (isCancelled.get()) throw CancellationException()
        
        if (booksToProcess.isEmpty()) {
            progressManager.forceUpdate {
                it.copy(
                    phase = ScanPhase.COMPLETED,
                    overallProgress = PROGRESS_FINALIZING,
                    booksFound = bookFiles.size,
                    booksAdded = 0,
                    booksSkipped = bookFiles.size
                )
            }
            return
        }
        
        processBooksInBatches(booksToProcess)
        
        if (isCancelled.get()) throw CancellationException()
        
        val finalProgress = progressManager.progress.value
        progressManager.forceUpdate {
            it.copy(
                phase = ScanPhase.COMPLETED,
                overallProgress = PROGRESS_FINALIZING,
                phaseProgress = 100,
                booksAdded = finalProgress.booksAdded,
                booksSkipped = finalProgress.booksSkipped,
                eta = "Завершено",
                speed = "Готово"
            )
        }
        
        val duration = (System.currentTimeMillis() - startTime) / 1000
        Log.d(TAG, "Scan completed in ${duration}s. Added: ${finalProgress.booksAdded}")
    }

    private suspend fun scanFilesWithProgress(
        rootDirs: List<File> = getDefaultScanDirectories()
    ): List<File> {
        val bookFiles = mutableListOf<File>()
        var count = 0
        
        val excludePaths = setOf(
            "Android", "data", "obb", "cache", "system", 
            "proc", "sys", "root", ".thumbnails"
        )
        
        fun scanDirectory(directory: File) {
            if (isCancelled.get()) throw CancellationException()
            
            val files = directory.listFiles() ?: return
            
            for (file in files) {
                if (isCancelled.get()) throw CancellationException()
                
                if (file.isDirectory) {
                    val dirName = file.name
                    if (excludePaths.contains(dirName)) continue
                    if (dirName.startsWith(".")) continue
                    
                    scanDirectory(file)
                } else if (file.isFile && isBookFile(file)) {
                    bookFiles.add(file)
                    count++
                    
                    if (count >= MAX_FILES_TO_SCAN) {
                        return
                    }
                }
            }
        }
        
        for (rootDir in rootDirs) {
            if (rootDir.exists() && rootDir.canRead()) {
                scanDirectory(rootDir)
            }
        }
        
        progressManager.forceUpdate {
            it.copy(
                phase = ScanPhase.SCANNING_FILES,
                phaseProgress = 100,
                overallProgress = PROGRESS_SCANNING_END,
                booksFound = bookFiles.size,
                memoryUsed = memoryMonitor.getMemoryStatus()
            )
        }
        
        return bookFiles
    }

    private fun isBookFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".fb2") || name.endsWith(".fb2.zip") || name.endsWith(".fbz") ||
                name.endsWith(".epub") || name.endsWith(".fb3") || name.endsWith(".fb3.zip") ||
                name.endsWith(".mobi") || name.endsWith(".azw") || name.endsWith(".azw3") ||
                name.endsWith(".zip")
    }

    private fun getDefaultScanDirectories(): List<File> {
        val dirs = mutableListOf<File>()
        val externalStorage = Environment.getExternalStorageDirectory()
        
        val bookDirs = listOf(
            "Books", "books", "Книги", "книги",
            "Download", "Downloads", "Загрузки",
            "Documents", "Документы",
            "Ebooks", "eBooks"
        )
        
        for (dirName in bookDirs) {
            val dir = File(externalStorage, dirName)
            if (dir.exists() && dir.canRead()) {
                dirs.add(dir)
            }
        }
        
        if (dirs.isEmpty()) {
            dirs.add(externalStorage)
        }
        
        return dirs
    }

    private suspend fun analyzeCache(bookFiles: List<File>): List<BookCache> {
        if (bookFiles.isEmpty()) return emptyList()
        
        val existingPaths = withContext(Dispatchers.IO) {
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
        
        val cachedPaths = withContext(Dispatchers.IO) {
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
        
        return bookFiles
            .filter { file ->
                val path = file.absolutePath
                !existingPaths.contains(path) && !cachedPaths.contains(path)
            }
            .map { file ->
                BookCache(
                    path = file.absolutePath,
                    fingerprint = file.absolutePath + "_" + file.length() + "_" + file.lastModified(),
                    textHash = null,
                    author = "",
                    title = file.nameWithoutExtension,
                    fileSize = file.length(),
                    lastScanned = System.currentTimeMillis(),
                    format = file.extension.lowercase()
                )
            }
    }

    private suspend fun processBooksInBatches(books: List<BookCache>) {
        val totalBooks = books.size
        var processedCount = 0
        var addedCount = 0
        var skippedCount = 0
        
        val sortedBooks = books.sortedBy { it.fileSize }
        
        sortedBooks.chunked(BATCH_SIZE).forEachIndexed { _, batch ->
            if (isCancelled.get()) throw CancellationException()
            
            val batchResults = processBookBatch(batch)
            
            batchResults.forEach { result ->
                when (result) {
                    is ProcessResult.Success -> addedCount++
                    is ProcessResult.Skipped, is ProcessResult.Error -> skippedCount++
                }
            }
            
            processedCount += batch.size
            
            val entities = batchResults.mapNotNull { it.entity }
            if (entities.isNotEmpty()) {
                try {
                    bookDao.insertBooks(entities)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert books", e)
                }
            }
            
            val progressRange = PROGRESS_PROCESSING_END - PROGRESS_PROCESSING_START
            val overallProgress = PROGRESS_PROCESSING_START + 
                ((processedCount.toFloat() / totalBooks) * progressRange).toInt()
            
            progressManager.update { current ->
                current.copy(
                    phase = ScanPhase.PROCESSING_BOOKS,
                    phaseProgress = ((processedCount.toFloat() / totalBooks) * 100).toInt(),
                    overallProgress = overallProgress,
                    booksFound = totalBooks,
                    booksProcessed = processedCount,
                    booksAdded = addedCount,
                    booksSkipped = skippedCount,
                    currentFile = batch.lastOrNull()?.title ?: "",
                    eta = etaCalculator.calculate(processedCount, totalBooks),
                    speed = etaCalculator.getSpeed(),
                    memoryUsed = memoryMonitor.getMemoryStatus()
                )
            }
            
            if (memoryMonitor.isMemoryLow()) {
                Log.w(TAG, "Memory low (${memoryMonitor.getFreeMemoryMB()} MB free), pausing...")
                delay(500)
            }
            
            delay(50)
        }
    }
    
    private suspend fun processBookBatch(batch: List<BookCache>): List<ProcessResult> {
        return withContext(Dispatchers.IO) {
            batch.map { cache ->
                if (isCancelled.get()) return@map ProcessResult.Error(null, null)
                
                try {
                    val result = withTimeoutOrNull(TIMEOUT_PER_BOOK_MS) {
                        processBookWithCache(cache)
                    }
                    
                    result ?: run {
                        Log.w(TAG, "Timeout processing: ${cache.title}")
                        ProcessResult.Error(null, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing ${cache.title}", e)
                    ProcessResult.Error(null, e)
                }
            }
        }
    }
    
    private suspend fun processBookWithCache(cache: BookCache): ProcessResult {
        val file = File(cache.path)
        
        if (!file.exists() || !file.canRead()) {
            return ProcessResult.Skipped
        }
        
        if (cache.title.lowercase().endsWith(".zip") || cache.path.lowercase().endsWith(".zip")) {
            val sizeMB = file.length() / (1024 * 1024)
            if (sizeMB > MAX_ZIP_SIZE_MB) {
                Log.w(TAG, "Skipping large ZIP: ${cache.title} ($sizeMB MB)")
                return ProcessResult.Skipped
            }
        }
        
        val processor = getProcessorForFile(cache.path) ?: return ProcessResult.Skipped
        
        val bookSource = BookSource(
            uri = android.net.Uri.fromFile(file),
            name = file.name,
            size = cache.fileSize,
            modified = cache.lastScanned,
            realPath = cache.path
        )
        
        val entity = processor.process(bookSource, context)
        return if (entity != null) {
            ProcessResult.Success(entity)
        } else {
            ProcessResult.Error()
        }
    }
    
    private fun getProcessorForFile(path: String): BookProcessor? {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".fb2") || lower.endsWith(".fb2.zip") || lower.endsWith(".fbz") -> fb2Processor
            lower.endsWith(".epub") -> epubProcessor
            lower.endsWith(".fb3") || lower.endsWith(".fb3.zip") -> fb3Processor
            lower.endsWith(".mobi") || lower.endsWith(".azw") || lower.endsWith(".azw3") -> mobiProcessor
            lower.endsWith(".zip") -> zipProcessor
            else -> null
        }
    }
}
