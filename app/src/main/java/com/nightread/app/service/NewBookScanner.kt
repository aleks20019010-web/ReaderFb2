package com.nightread.app.service

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import com.nightread.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

// ===================== МОДЕЛИ ДАННЫХ =====================

data class BookSource(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
    val mimeType: String? = null,
    val realPath: String? = null
)

// ===================== ПРОЦЕССОРЫ ФОРМАТОВ =====================

interface BookProcessor {
    suspend fun process(book: BookSource, context: Context): BookEntity?
}

class Fb2Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            val sha1 = generateFastHash(book)
            
            val metadata = withContext(Dispatchers.Default) {
                contentResolver.openInputStream(book.uri)?.use { input ->
                    Fb2Parser.parse(input, book.name.substringBeforeLast('.'))
                }
            } ?: return null

            val coverPath = try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(book.uri)?.use { input ->
                        input.readBytes()
                    }
                }
                if (bytes != null && bytes.isNotEmpty()) {
                    val text = decodeBytesToString(bytes)
                    NewCoverExtractor.extractAndSaveCover(text, sha1, context)
                } else null
            } catch (e: Exception) {
                Log.w("Fb2Processor", "Cover error ${book.name}", e)
                null
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
        } catch (e: Exception) {
            Log.e("Fb2Processor", "Processing error ${book.name}", e)
            null
        }
    }

    private fun generateFastHash(book: BookSource): String {
        val input = "${book.uri}_${book.size}_${book.modified}"
        return MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
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
                } catch (e: Exception) { /* ignore */ }
            }
        } catch (e: Exception) { /* ignore */ }

        return try {
            String(bytes, java.nio.charset.StandardCharsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }

    private fun getRandomGradientStartColor(): String {
        return listOf("#1A1A2E", "#16213E", "#0F3460", "#3B0066").random()
    }

    private fun getRandomGradientEndColor(): String {
        return listOf("#E94560", "#00ADB5", "#FF2E63", "#FF9F43").random()
    }
}

class Fb3Processor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            val sha1 = generateFastHash(book)
            
            val result = withContext(Dispatchers.Default) {
                contentResolver.openInputStream(book.uri)?.use { input ->
                    Fb3Parser.parseFb3(input, book.name.substringBeforeLast('.'), false)
                }
            } ?: return null

            val coverPath = if (result.coverBytes != null && result.coverBytes.isNotEmpty()) {
                NewCoverExtractor.saveCoverBytes(result.coverBytes, sha1, context)
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
        } catch (e: Exception) {
            Log.e("Fb3Processor", "Processing error ${book.name}", e)
            null
        }
    }

    private fun generateFastHash(book: BookSource): String {
        val input = "${book.uri}_${book.size}_${book.modified}"
        return MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun getRandomGradientStartColor(): String {
        return listOf("#1A1A2E", "#16213E", "#0F3460", "#3B0066").random()
    }

    private fun getRandomGradientEndColor(): String {
        return listOf("#E94560", "#00ADB5", "#FF2E63", "#FF9F43").random()
    }
}

class EpubProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            
            val meta = withContext(Dispatchers.Default) {
                EpubIdentifierHelper.getEpubMetadata { contentResolver.openInputStream(book.uri) }
            } ?: return null

            val savedCover = try {
                EpubIdentifierHelper.extractAndSaveEpubCover(
                    { contentResolver.openInputStream(book.uri) },
                    meta.coverPath,
                    meta.identifier,
                    context
                )
            } catch (e: Exception) {
                Log.w("EpubProcessor", "Cover error ${book.name}", e)
                null
            }

            BookEntity(
                sha1 = meta.identifier,
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
        } catch (e: Exception) {
            Log.e("EpubProcessor", "Processing error ${book.name}", e)
            null
        }
    }

    private fun getRandomGradientStartColor(): String {
        return listOf("#1A1A2E", "#16213E", "#0F3460", "#3B0066").random()
    }

    private fun getRandomGradientEndColor(): String {
        return listOf("#E94560", "#00ADB5", "#FF2E63", "#FF9F43").random()
    }
}

class MobiProcessor : BookProcessor {
    override suspend fun process(book: BookSource, context: Context): BookEntity? {
        return try {
            val contentResolver = context.contentResolver
            val sha1 = generateFastHash(book)
            
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(book.uri)?.use { input ->
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
            } ?: return null

            val meta = withContext(Dispatchers.Default) {
                MobiParser.parseBytes(bytes, book.name.substringBeforeLast('.'))
            }

            BookEntity(
                sha1 = sha1,
                title = meta.title,
                author = meta.author,
                annotation = meta.annotation,
                category = "Local",
                filePath = resolveBookPath(book, context),
                fileSize = book.size,
                coverPath = null,
                isNew = true,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor()
            )
        } catch (e: Exception) {
            Log.e("MobiProcessor", "Processing error ${book.name}", e)
            null
        }
    }

    private fun generateFastHash(book: BookSource): String {
        val input = "${book.uri}_${book.size}_${book.modified}"
        return MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun getRandomGradientStartColor(): String {
        return listOf("#1A1A2E", "#16213E", "#0F3460", "#3B0066").random()
    }

    private fun getRandomGradientEndColor(): String {
        return listOf("#E94560", "#00ADB5", "#FF2E63", "#FF9F43").random()
    }
}

// ===================== ОСНОВНОЙ КЛАСС =====================

@OptIn(ExperimentalCoroutinesApi::class)
class NewBookScanner(
    private val context: Context,
    private val bookDao: BookDao,
    private val cacheDao: ScannerCacheDao? = null,
    private val contentResolver: ContentResolver = context.contentResolver
) {

    companion object {
        private const val TAG = "NewBookScanner"
        private const val MAX_DEPTH = 10
        private const val MAX_FILE_SIZE = 50L * 1024 * 1024
        private const val MAX_ZIP_FILE_SIZE = 500L * 1024 * 1024
        private const val BATCH_SIZE = 10
        private const val MAX_BOOKS_FROM_ONE_ZIP = 300
        private const val ZIP_MAX_ENTRY_SIZE = 20 * 1024 * 1024
        private const val CHANNEL_BUFFER_SIZE = 100
        private const val MAX_FB2_FOR_COVER = 10 * 1024 * 1024

        val isScanningGlobally = AtomicBoolean(false)
    }


    val state = MutableStateFlow(
        ScannerState(
            isScanning = false,
            status = "Ожидание"
        )
    )

    private fun updateState(newState: ScannerState) {
        state.value = newState
        com.nightread.app.service.NewBookScanState.updateState(newState)
    }

    private val dbMutex = Mutex()
    private val scanJob = AtomicReference<Job?>(null)
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(4)

    // Процессоры форматов
    private val processors = mapOf(
        "fb2" to Fb2Processor(),
        "fb3" to Fb3Processor(),
        "epub" to EpubProcessor(),
        "mobi" to MobiProcessor(),
        "azw" to MobiProcessor(),
        "azw3" to MobiProcessor()
    )

    // ===================== ОСНОВНЫЕ МЕТОДЫ =====================

    suspend fun scan(isBackground: Boolean = false): Job {
        return scanBooks(isBackground)
    }

    fun stopScan() {
        scanJob.get()?.cancel()
        Log.d(TAG, "Scan stopped by user")
    }

    suspend fun scanBooks(isBackground: Boolean = false): Job {
        if (!isScanningGlobally.compareAndSet(false, true)) {
            updateState(ScannerState(isScanning = false, status = "Сканирование уже запущено"))
            return Job()
        }

        val job = CoroutineScope(ioDispatcher).launch {
            try {
                updateState(ScannerState(isScanning = true, status = "Подготовка сканирования"))

                val books = getBooksFromMediaStore()

                if (books.isEmpty()) {
                    updateState(ScannerState(isScanning = false, status = "Книги не найдены"))
                    return@launch
                }

                val sortedBooks = books.sortedBy { it.name.lowercase() }

                val booksToProcess = if (cacheDao != null) {
                    filterCachedBooks(sortedBooks)
                } else {
                    sortedBooks
                }

                if (booksToProcess.isEmpty()) {
                    updateState(
                        ScannerState(
                            isScanning = false,
                            status = "Новых книг не найдено",
                            totalFiles = books.size,
                            processedFiles = books.size
                        )
                    )
                    return@launch
                }

                processBooks(booksToProcess)

                if (cacheDao != null && booksToProcess.isNotEmpty()) {
                    updateCache(booksToProcess)
                }

                updateState(
                    ScannerState(
                        isScanning = false,
                        status = "Сканирование завершено",
                        totalFiles = books.size,
                        processedFiles = books.size
                    )
                )

            } catch (e: CancellationException) {
                Log.d(TAG, "Scan cancelled")
                updateState(ScannerState(isScanning = false, status = "Сканирование отменено"))
            } catch (e: Exception) {
                Log.e(TAG, "Scanner error", e)
                updateState(ScannerState(isScanning = false, status = "Ошибка: ${e.message}"))
            } finally {
                isScanningGlobally.set(false)
                scanJob.set(null)
            }
        }

        scanJob.set(job)
        return job
    }

    suspend fun checkForNewBooks(): Job {
        if (!isScanningGlobally.compareAndSet(false, true)) {
            updateState(ScannerState(isScanning = false, status = "Сканирование уже запущено"))
            return Job()
        }

        val job = CoroutineScope(ioDispatcher).launch {
            try {
                updateState(ScannerState(isScanning = true, status = "Быстрая проверка новых книг..."))

                val existingMap = bookDao.getSha1ToPathMap()
                val existingPaths = existingMap.mapNotNull { it.filePath }.toSet()

                val books = getBooksFromMediaStore()

                val booksToProcess = books.filter { !existingPaths.contains(it.uri.toString()) }

                if (booksToProcess.isEmpty()) {
                    updateState(
                        ScannerState(
                            isScanning = false,
                            status = "Новых книг не найдено",
                            totalFiles = books.size,
                            processedFiles = books.size
                        )
                    )
                    return@launch
                }

                updateState(
                    ScannerState(
                        isScanning = true,
                        status = "Найдено новых книг: ${booksToProcess.size}",
                        totalFiles = booksToProcess.size,
                        processedFiles = 0
                    )
                )

                processBooks(booksToProcess)

            } catch (e: CancellationException) {
                updateState(ScannerState(isScanning = false, status = "Сканирование отменено"))
            } catch (e: Exception) {
                Log.e(TAG, "checkForNewBooks error", e)
                updateState(ScannerState(isScanning = false, status = "Ошибка: ${e.message}"))
            } finally {
                isScanningGlobally.set(false)
                scanJob.set(null)
            }
        }

        scanJob.set(job)
        return job
    }

    // ===================== РАБОТА С MEDIASTORE =====================

    private suspend fun getBooksFromMediaStore(): List<BookSource> {
        val result = mutableListOf<BookSource>()
        val supportedExtensions = setOf("fb2", "fb3", "epub", "mobi", "azw", "azw3", "zip", "fbz")

        try {
            val collectionUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                MediaStore.Files.FileColumns.DATA
            )

            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IS NULL OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE ?"
            val selectionArgs = arrayOf("image/%")

            contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    try {
                        val name = cursor.getString(nameColumn) ?: continue
                        val ext = name.substringAfterLast('.', "").lowercase()
                        
                        if (!supportedExtensions.contains(ext)) continue
                        
                        val size = cursor.getLong(sizeColumn)
                        val maxSize = if (ext == "zip" || ext == "fbz") MAX_ZIP_FILE_SIZE else MAX_FILE_SIZE
                        if (size <= 0 || size > maxSize) continue

                        val id = cursor.getLong(idColumn)
                        val fileUri = ContentUris.withAppendedId(collectionUri, id)

                        val modified = cursor.getLong(modifiedColumn) * 1000
                        val mimeType = cursor.getString(mimeColumn)
                        val dataPath = if (dataColumn != -1) cursor.getString(dataColumn) else null

                        result.add(
                            BookSource(
                                uri = fileUri,
                                name = name,
                                size = size,
                                modified = modified,
                                mimeType = mimeType,
                                realPath = if (!dataPath.isNullOrBlank() && File(dataPath).exists()) dataPath else null
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing media store entry", e)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException accessing MediaStore, falling back to filesystem", e)
            return getBooksFromFileSystem()
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query error, falling back to legacy filesystem scan", e)
            return getBooksFromFileSystem()
        }

        val fsBooks = getBooksFromFileSystem()
        val combined = (result + fsBooks).distinctBy { it.uri.toString() }
        return combined
    }

    // ===================== FALLBACK ДЛЯ СТАРЫХ ANDROID =====================

    private fun getBooksFromFileSystem(): List<BookSource> {
        val result = mutableListOf<BookSource>()
        val roots = getLegacyFolders()

        roots.forEach { folder ->
            if (folder.exists()) {
                scanLegacyFolder(folder, result, 0)
            }
        }

        return result
    }

    private fun getLegacyFolders(): List<File> {
        val extStorage = Environment.getExternalStorageDirectory()
        return listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            extStorage,
            File(extStorage, "Books"),
            File(extStorage, "Book"),
            File(extStorage, "Download"),
            File(extStorage, "Downloads"),
            File(extStorage, "Documents"),
            File(extStorage, "Telegram"),
            File(extStorage, "FB2"),
            File(extStorage, "Library"),
            File(extStorage, "Android/media"),
            context.getExternalFilesDir(null),
            context.filesDir
        ).distinct()
    }

    private fun scanLegacyFolder(folder: File, result: MutableList<BookSource>, depth: Int) {
        if (depth > MAX_DEPTH) return
        if (!folder.exists() || !folder.canRead()) return

        val skipFolders = setOf(
            "android/data", "obb", "cache", "temp", "tmp",
            "dcim", "pictures", "movies", "music",
            "notifications", "ringtones", "podcasts", ".thumbnails",
            "__macosx"
        )

        val files = try {
            folder.listFiles()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException listing folder: ${folder.path}")
            null
        } catch (e: Exception) {
            null
        } ?: return

        for (file in files) {
            try {
                if (file.isDirectory) {
                    val name = file.name.lowercase()
                    if (!name.startsWith(".") && !skipFolders.contains(name)) {
                        scanLegacyFolder(file, result, depth + 1)
                    }
                } else {
                    val ext = file.extension.lowercase()
                    val maxSize = if (ext == "zip" || ext == "fbz" || file.name.lowercase().endsWith(".fb2.zip")) MAX_ZIP_FILE_SIZE else MAX_FILE_SIZE
                    if (isBookFile(file) && file.length() > 0 && file.length() <= maxSize) {
                        result.add(
                            BookSource(
                                uri = Uri.fromFile(file),
                                name = file.name,
                                size = file.length(),
                                modified = file.lastModified(),
                                realPath = file.absolutePath
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException accessing file: ${file.path}")
            } catch (e: Exception) {
                Log.e(TAG, "File scan error ${file.path}", e)
            }
        }
    }

    private fun isBookFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        val name = file.name.lowercase()
        return ext in setOf("fb2", "fb3", "epub", "mobi", "azw", "azw3", "zip", "fbz") ||
                name.endsWith(".fb2.zip") || name.endsWith(".fb3.zip")
    }

    // ===================== КЕШИРОВАНИЕ =====================

    private suspend fun filterCachedBooks(books: List<BookSource>): List<BookSource> {
        if (cacheDao == null) return books

        val cacheMap = cacheDao.getAll().associateBy { it.path }
        val result = books.filter { book ->
            val cached = cacheMap[book.uri.toString()]
            cached == null || 
            cached.lastModified != book.modified || 
            cached.fileSize != book.size
        }

        Log.d(TAG, "Filtered ${books.size} books, ${result.size} need processing")
        return result
    }

    private suspend fun updateCache(books: List<BookSource>) {
        if (cacheDao == null) return

        try {
            val entries = books.map { book ->
                ScannerCacheEntity(
                    path = book.uri.toString(),
                    lastModified = book.modified,
                    fileSize = book.size,
                    sha1 = generateFastHash(book) // Сохраняем SHA1 для быстрого поиска
                )
            }
            cacheDao.insertAll(entries)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update cache", e)
        }
    }

    private fun generateFastHash(book: BookSource): String {
        val input = "${book.uri}_${book.size}_${book.modified}"
        return MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    // ===================== ОБРАБОТКА КНИГ =====================

    private suspend fun processBooks(books: List<BookSource>) = coroutineScope {
        val total = books.size
        val processed = AtomicInteger(0)
        val added = AtomicInteger(0)

        val batch = Collections.synchronizedList(mutableListOf<BookEntity>())
        val batchMutex = Mutex()

        val channel = Channel<BookSource>(CHANNEL_BUFFER_SIZE)

        launch(ioDispatcher) {
            books.forEach { channel.send(it) }
            channel.close()
        }

        val workersCount = Runtime.getRuntime()
            .availableProcessors()
            .coerceIn(2, 4)

        Log.d(TAG, "Using $workersCount worker threads")

        val workers = List(workersCount) {
            launch(ioDispatcher) {
                for (book in channel) {
                    if (!isActive) break

                    val index = processed.incrementAndGet()
                    val progress = (index.toFloat() / total * 100).toInt()

                    updateState(
                        ScannerState(
                            isScanning = true,
                            status = "Обработка ${book.name} ($index/$total)",
                            totalFiles = total,
                            processedFiles = index,
                            addedBooks = added.get(),
                            progress = progress
                        )
                    )

                    try {
                        val localBooks = mutableListOf<BookEntity>()
                        processBook(book, localBooks)

                        if (localBooks.isNotEmpty()) {
                            var saveList: List<BookEntity> = emptyList()

                            batchMutex.withLock {
                                batch.addAll(localBooks)

                                if (batch.size >= BATCH_SIZE) {
                                    saveList = ArrayList(batch)
                                    batch.clear()
                                }
                            }

                            if (saveList.isNotEmpty()) {
                                saveBatch(saveList)
                                added.addAndGet(saveList.size)
                            }
                        }

                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing ${book.name}", e)
                    }
                }
            }
        }

        workers.joinAll()

        var saveList: List<BookEntity> = emptyList()
        batchMutex.withLock {
            if (batch.isNotEmpty()) {
                saveList = ArrayList(batch)
                batch.clear()
            }
        }

        if (saveList.isNotEmpty()) {
            saveBatch(saveList)
            added.addAndGet(saveList.size)
        }

        updateState(
            ScannerState(
                isScanning = false,
                status = "Сканирование завершено",
                totalFiles = total,
                processedFiles = total,
                addedBooks = added.get(),
                progress = 100
            )
        )
    }

    private suspend fun processBook(
        book: BookSource,
        batch: MutableList<BookEntity>
    ) {
        val ext = book.name.substringAfterLast('.', "").lowercase()

        if (!isUriAccessible(book.uri)) return

        when (ext) {
            "zip" -> {
                val zipBooks = mutableListOf<BookEntity>()
                parseZip(book, zipBooks)
                if (zipBooks.isNotEmpty()) {
                    batch.addAll(zipBooks)
                }
            }
            else -> {
                val processor = processors[ext]
                if (processor != null) {
                    val bookEntity = processor.process(book, context)
                    if (bookEntity != null) {
                        batch.add(bookEntity)
                    }
                }
            }
        }
    }

    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ===================== РАБОТА С ZIP =====================

    private suspend fun parseZip(book: BookSource, batch: MutableList<BookEntity>) {
        val maxEntries = 500
        var processedEntries = 0

        try {
            // Читаем ZIP потоково, без загрузки всего файла в память
            contentResolver.openInputStream(book.uri)?.use { inputStream ->
                ZipInputStream(inputStream.buffered(8192)).use { zip ->
                    var entry = zip.nextEntry

                    while (entry != null) {
                        kotlinx.coroutines.yield()

                        processedEntries++
                        if (processedEntries > maxEntries) {
                            Log.w(TAG, "ZIP limit reached: ${book.name}")
                            break
                        }

                        try {
                            if (!entry.isDirectory && isSupportedArchiveBook(entry.name)) {
                                // Проверка Zip Bomb
                                if (entry.compressedSize > 0 && entry.size > entry.compressedSize * 100) {
                                    Log.w(TAG, "Possible zip bomb: ${entry.name} in ${book.name}")
                                    zip.closeEntry()
                                    entry = zip.nextEntry
                                    continue
                                }

                                val bytes = readZipEntryLimited(zip, ZIP_MAX_ENTRY_SIZE)
                                if (bytes == null) {
                                    Log.w(TAG, "Skipped large entry ${entry.name}")
                                    zip.closeEntry()
                                    entry = zip.nextEntry
                                    continue
                                }

                                val bookEntity = withContext(Dispatchers.Default) {
                                    parseZipEntry(entry.name, bytes, book)
                                }

                                if (bookEntity != null) {
                                    batch.add(bookEntity)
                                    if (batch.size >= MAX_BOOKS_FROM_ONE_ZIP) {
                                        Log.w(TAG, "ZIP book limit reached: ${book.name}")
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "ZIP entry error ${entry.name}", e)
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZIP parsing error ${book.uri}", e)
        }
    }

    private suspend fun parseZipEntry(
        entryName: String,
        bytes: ByteArray,
        book: BookSource
    ): BookEntity? {
        val sha1 = calculateSha1(bytes)
        val fileName = entryName.substringAfterLast("/").substringBeforeLast(".")
        val uriString = resolveBookPath(book, context)

        return try {
            when {
                entryName.endsWith(".fb3", true) -> {
                    val meta = Fb3Parser.parseBytes(bytes, fileName, false)
                    val coverPath = if (meta.coverBytes != null && meta.coverBytes.isNotEmpty()) {
                        NewCoverExtractor.saveCoverBytes(meta.coverBytes, sha1, context)
                    } else null

                    BookEntity(
                        sha1 = sha1,
                        title = meta.title.ifBlank { fileName },
                        author = meta.author,
                        annotation = meta.annotation,
                        category = "Local",
                        filePath = uriString,
                        fileSize = book.size,
                        series = meta.series,
                        seriesIndex = meta.seriesIndex,
                        language = meta.language,
                        coverPath = coverPath,
                        isNew = true,
                        coverGradientStart = getRandomGradientStartColor(),
                        coverGradientEnd = getRandomGradientEndColor()
                    )
                }

                entryName.endsWith(".fb2", true) -> {
                    val meta = bytes.inputStream().use { Fb2Parser.parse(it, fileName) }
                    val coverPath = if (bytes.size <= MAX_FB2_FOR_COVER) {
                        try {
                            val text = decodeBytesToString(bytes)
                            NewCoverExtractor.extractAndSaveCover(text, sha1, context)
                        } catch (e: Exception) { null }
                    } else null

                    BookEntity(
                        sha1 = sha1,
                        title = meta.title.ifBlank { fileName },
                        author = meta.author,
                        annotation = meta.annotation,
                        category = "Local",
                        filePath = uriString,
                        fileSize = book.size,
                        series = meta.series,
                        seriesIndex = meta.seriesIndex,
                        language = meta.language,
                        coverPath = coverPath,
                        isNew = true,
                        coverGradientStart = getRandomGradientStartColor(),
                        coverGradientEnd = getRandomGradientEndColor()
                    )
                }

                entryName.endsWith(".epub", true) -> {
                    val meta = EpubIdentifierHelper.getEpubMetadata(bytes) ?: return null
                    val coverPath = try {
                        EpubIdentifierHelper.extractAndSaveEpubCover(bytes, meta.coverPath, sha1, context)
                    } catch (e: Exception) { null }

                    BookEntity(
                        sha1 = sha1,
                        title = meta.title,
                        author = meta.author,
                        annotation = meta.description,
                        category = "Local",
                        filePath = uriString,
                        fileSize = book.size,
                        coverPath = coverPath,
                        language = "unknown",
                        isNew = true,
                        coverGradientStart = getRandomGradientStartColor(),
                        coverGradientEnd = getRandomGradientEndColor()
                    )
                }

                entryName.endsWith(".mobi", true) ||
                entryName.endsWith(".azw", true) ||
                entryName.endsWith(".azw3", true) -> {
                    
                    val limitedBytes = if (bytes.size > 2 * 1024 * 1024) {
                        bytes.copyOf(2 * 1024 * 1024)
                    } else bytes

                    val meta = MobiParser.parseBytes(limitedBytes, fileName)
                    val coverPath = if (meta.coverBytes != null && meta.coverBytes.isNotEmpty()) {
                        val coverDir = File(context.filesDir, "covers")
                        if (!coverDir.exists()) coverDir.mkdirs()
                        val coverFile = File(coverDir, "$sha1.jpg")
                        coverFile.writeBytes(meta.coverBytes)
                        coverFile.absolutePath
                    } else null

                    BookEntity(
                        sha1 = sha1,
                        title = meta.title.ifBlank { fileName },
                        author = meta.author,
                        annotation = meta.annotation,
                        category = "Local",
                        filePath = uriString,
                        fileSize = book.size,
                        coverPath = coverPath,
                        isNew = true,
                        coverGradientStart = getRandomGradientStartColor(),
                        coverGradientEnd = getRandomGradientEndColor()
                    )
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZIP entry parse error: $entryName", e)
            null
        }
    }

    // ===================== РАБОТА С БАЗОЙ ДАННЫХ =====================

    private suspend fun saveBatch(batch: List<BookEntity>) {
        if (batch.isEmpty()) return

        dbMutex.withLock {
            try {
                AppDatabase.getDatabase(context).withTransaction {
                    bookDao.insertBooks(batch)
                }
            } catch (e: Exception) {
                // Проверяем, это дубликат или другая ошибка
                if (!e.message.orEmpty().contains("UNIQUE", ignoreCase = true)) {
                    Log.e(TAG, "Database batch insert error", e)
                }
                
                // Пробуем по одному
                batch.forEach { book ->
                    try {
                        bookDao.insertBooks(listOf(book))
                    } catch (e: Exception) {
                        if (!e.message.orEmpty().contains("UNIQUE", ignoreCase = true)) {
                            Log.e(TAG, "Database insert error for ${book.title}", e)
                        }
                    }
                }
            }
        }
    }

    // ===================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====================

    private fun isSupportedArchiveBook(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".fb2") || n.endsWith(".fb3") || n.endsWith(".epub") ||
                n.endsWith(".mobi") || n.endsWith(".azw") || n.endsWith(".azw3")
    }

    private fun readZipEntryLimited(zip: ZipInputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0

        while (true) {
            val read = zip.read(buffer)
            if (read == -1) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }

    private fun calculateSha1(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
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
                } catch (e: Exception) { /* ignore */ }
            }
        } catch (e: Exception) { /* ignore */ }

        return try {
            String(bytes, java.nio.charset.StandardCharsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }

    private fun getRandomGradientStartColor(): String {
        return listOf("#1A1A2E", "#16213E", "#0F3460", "#3B0066").random()
    }

    private fun getRandomGradientEndColor(): String {
        return listOf("#E94560", "#00ADB5", "#FF2E63", "#FF9F43").random()
    }
}

fun resolveBookPath(book: BookSource, context: Context): String {
    if (!book.realPath.isNullOrBlank() && File(book.realPath).exists()) {
        return File(book.realPath).absolutePath
    }
    if (book.uri.scheme == "file") {
        val path = book.uri.path ?: book.uri.toString().removePrefix("file://")
        if (File(path).exists()) return File(path).absolutePath
    }
    val uriPath = book.uri.path
    if (!uriPath.isNullOrBlank() && File(uriPath).exists()) {
        return File(uriPath).absolutePath
    }
    if (book.uri.scheme == "content") {
        try {
            context.contentResolver.query(book.uri, arrayOf(MediaStore.Files.FileColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    if (dataIdx != -1) {
                        val path = cursor.getString(dataIdx)
                        if (!path.isNullOrBlank() && File(path).exists()) {
                            return File(path).absolutePath
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("NewBookScanner", "Error resolving DATA column for ${book.uri}", e)
        }

        try {
            context.contentResolver.openInputStream(book.uri)?.use { input ->
                val importedDir = File(context.filesDir, "imported").apply { mkdirs() }
                val destFile = File(importedDir, "${book.name.hashCode()}_${book.name}")
                if (!destFile.exists() || destFile.length() == 0L) {
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    return destFile.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("NewBookScanner", "Error copying stream for ${book.name}", e)
        }
    }
    return book.realPath ?: book.uri.path ?: book.uri.toString()
}
