package com.nightread.app.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nightread.app.BuildConfig
import com.nightread.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = BookRepository(database.bookDao(), database.noteDao())
    val syncManager = SyncManager(application)

    // Observables from Database with robust error catching
    val allBooks: StateFlow<List<BookEntity>> = repository.allBooks
        .catch { e ->
            if (e is CancellationException) throw e
            Log.e("BookViewModel", "Exception loading books from database", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val searchedBooks: StateFlow<List<BookEntity>> = searchQuery
                .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allBooks.debounce(500)
            } else {
                repository.searchBooks(query).debounce(500)
            }
        }
        .catch { e ->
            Log.e("BookViewModel", "Exception searching books from database", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<NoteEntity>> = repository.allNotes
        .catch { e ->
            Log.e("BookViewModel", "Exception loading notes from database", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Explicit helper to load books from database safely with try-catch.
     */
    fun loadBooks() {
        viewModelScope.launch {
            if (!isActive) return@launch
            try {
                Log.d("BookViewModel", "loadBooks() called: Triggering fetch of books.")
                repository.allBooks.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BookViewModel", "Critical error in loadBooks() during database fetch", e)
            }
        }
    }

    fun loadReadingBooks(): Flow<List<BookEntity>> {
        return repository.getReadingBooks()
    }

    fun loadFavoriteBooks(): Flow<List<BookEntity>> {
        return repository.getFavoriteBooks()
    }

    // UI Navigation & Preferences State
    var currentTab by mutableStateOf(0) // 0 = Shelf, 1 = Reader, 2 = Notes, 3 = Sync/Settings
    var selectedBook by mutableStateOf<BookEntity?>(null)
    var detailedBook by mutableStateOf<BookEntity?>(null)

    // Scanning Device for Local Books
    val scanState: StateFlow<com.nightread.app.service.ScanState> = com.nightread.app.service.NewBookScanState.state
    var isScanning by mutableStateOf(false)
    var scanProgressText by mutableStateOf("")

    private val prefs = application.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)

    private var _readerFontSize = mutableStateOf(prefs.getFloat("reader_font_size", 18f))
    var readerFontSize: Float
        get() = _readerFontSize.value
        set(value) {
            _readerFontSize.value = value
            prefs.edit().putFloat("reader_font_size", value).apply()
        }

    private var _readerTheme = mutableStateOf(prefs.getString("reader_theme", "light") ?: "light")
    var readerTheme: String
        get() = _readerTheme.value
        set(value) {
            _readerTheme.value = value
            prefs.edit().putString("reader_theme", value).apply()
        }

    private var _appThemeMode = mutableStateOf(prefs.getString("app_theme_mode", "system") ?: "system")
    var appThemeMode: String
        get() = _appThemeMode.value
        set(value) {
            _appThemeMode.value = value
            prefs.edit().putString("app_theme_mode", value).apply()
        }

    var readerPage by mutableStateOf(0) // Page size is 1000 characters

    // Filter/Search States
    var bookSearchQuery by mutableStateOf("")
    var selectedCategory by mutableStateOf("Все")
    var noteSearchQuery by mutableStateOf("")

    // Gemini API States
    var aiLoading by mutableStateOf(false)
    var aiResult by mutableStateOf("")
    var aiError by mutableStateOf<String?>(null)

    // Sync States
    var isServerRunning by mutableStateOf(false)
    var syncServerLogs = mutableStateListOf<String>()
    var localIpAddress by mutableStateOf("127.0.0.1")
    var remoteServerIp by mutableStateOf("")
    var cloudSyncUrl by mutableStateOf("")
    var exportJsonString by mutableStateOf("")
    var isSyncLoading by mutableStateOf(false)

    init {
        // Prepare initial content and IP info
        refreshIpAddress()
        
        // Initialize and observe background scanning state
        viewModelScope.launch {
            if (!isActive) return@launch
            com.nightread.app.service.NewBookScanState.state.collect { state ->
                isScanning = state.isScanning
                scanProgressText = state.status
            }
        }

        // Automatically enable and schedule auto-discovery when the first book is added
        viewModelScope.launch(Dispatchers.IO) {
            var wasEmpty = true
            allBooks.collect { books ->
                if (books.isNotEmpty()) {
                    if (wasEmpty) {
                        wasEmpty = false
                        val context = getApplication<Application>().applicationContext
                        if (!SettingsManager.isAutoDiscoveryEnabled(context)) {
                            SettingsManager.setAutoDiscoveryEnabled(context, true)
                        }
                        com.nightread.app.service.AutoDiscoveryWorker.schedule(context)
                        try {
                            com.nightread.app.service.AutoDiscoveryService.start(context)
                        } catch (e: Exception) {
                            Log.e("BookViewModel", "Failed to start AutoDiscoveryService", e)
                        }
                    }
                } else {
                    wasEmpty = true
                }
            }
        }
    }

    // Book Interactions
    fun openBook(book: BookEntity) {
        selectedBook = book
        readerPage = book.currentProgressChar / 1000
        viewModelScope.launch {
            if (!isActive) return@launch
            repository.updateProgress(book.sha1, book.currentProgressChar)
        }
    }

    fun updateReadingProgress(charOffset: Int) {
        val book = selectedBook ?: return
        val clampedOffset = charOffset.coerceIn(0, book.totalCharacters)
        selectedBook = book.copy(currentProgressChar = clampedOffset)
        readerPage = clampedOffset / 1000
        viewModelScope.launch {
            if (!isActive) return@launch
            repository.updateProgress(book.sha1, clampedOffset)
        }
    }

    fun nextPage() {
        val book = selectedBook ?: return
        val nextPageChar = (readerPage + 1) * 1000
        if (nextPageChar < book.totalCharacters) {
            updateReadingProgress(nextPageChar)
        }
    }

    fun previousPage() {
        if (readerPage > 0) {
            updateReadingProgress((readerPage - 1) * 1000)
        }
    }

    fun deleteBook(bookSha1: String) {
        viewModelScope.launch {
            if (!isActive) return@launch
            repository.deleteBookBySha1(bookSha1)
            if (selectedBook?.sha1 == bookSha1) {
                selectedBook = null
            }
        }
    }

    fun addNewBook(title: String, author: String, content: String, category: String) {
        viewModelScope.launch {
            if (!isActive) return@launch
            val totalChars = content.length
            val rawBytes = (title + author + content).toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            val sha1String = try {
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                val result = digest.digest(rawBytes)
                result.joinToString("") { "%02x".format(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                java.util.UUID.randomUUID().toString()
            }
            val importedFolder = java.io.File(getApplication<Application>().filesDir, "imported_books")
            if (!importedFolder.exists()) {
                importedFolder.mkdirs()
            }
            val localFile = java.io.File(importedFolder, "$sha1String.txt")
            localFile.writeText(content)

            val newBook = BookEntity(
                sha1 = sha1String,
                title = title,
                author = author,
                category = category.ifEmpty { "Классика" },
                totalCharacters = totalChars,
                coverGradientStart = getRandomGradientStartColor(),
                coverGradientEnd = getRandomGradientEndColor(),
                filePath = localFile.absolutePath
            )
            repository.insertBook(newBook)
        }
    }

    fun importBookFromUri(uri: android.net.Uri, context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            try {
                val contentResolver = context.contentResolver
                var fileName = "imported_book.fb2"
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = it.getString(nameIndex)
                        }
                    }
                }
                
                val ext = fileName.substringAfterLast(".", "").lowercase()
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Не удалось прочитать файл (файл пуст).")
                    }
                    return@launch
                }
                
                // Align ZIP SHA-1 calculation with uncompressed FB2 bytes just like in scanning
                var bytesForSha1 = bytes
                if (ext == "epub") {
                    try {
                        val tempFile = java.io.File.createTempFile("epub_import", ".epub", context.cacheDir)
                        tempFile.writeBytes(bytes)
                        val metadata = com.nightread.app.service.EpubParser.parseEpub(tempFile, fileName.substringBeforeLast("."))
                        bytesForSha1 = metadata.content.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                        tempFile.delete()
                    } catch (e: Exception) {
                        Log.e("BookScanner", "Error calculating EPUB bytes for SHA-1: ", e)
                    }
                } else if (ext == "mobi" || ext == "azw3") {
                    try {
                        val tempFile = java.io.File.createTempFile("mobi_import", ".$ext", context.cacheDir)
                        tempFile.writeBytes(bytes)
                        val metadata = com.nightread.app.service.MobiParser.parseMobi(tempFile, fileName.substringBeforeLast("."))
                        bytesForSha1 = metadata.content.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                        tempFile.delete()
                    } catch (e: Exception) {
                        Log.e("BookScanner", "Error calculating MOBI bytes for SHA-1: ", e)
                    }
                } else if (ext == "zip") {
                    try {
                        java.io.ByteArrayInputStream(bytes).use { bais ->
                            java.util.zip.ZipInputStream(bais).use { zis ->
                                var entry = zis.nextEntry
                                var foundFb2 = false
                                while (entry != null) {
                                    val entryName = entry.name.lowercase()
                                    if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                        foundFb2 = true
                                        val buffer = java.io.ByteArrayOutputStream()
                                        val data = ByteArray(8192)
                                        var nRead: Int
                                        while (zis.read(data, 0, data.size).also { nRead = it } != -1) {
                                            buffer.write(data, 0, nRead)
                                        }
                                        bytesForSha1 = buffer.toByteArray()
                                        break
                                    }
                                    entry = zis.nextEntry
                                }
                                if (!foundFb2) {
                                    withContext(Dispatchers.Main) {
                                        onResult(false, "Внутри ZIP-архива не найден файл .fb2")
                                    }
                                    return@launch
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onResult(false, "Ошибка чтения ZIP-архива: ${e.localizedMessage}")
                        }
                        return@launch
                    }
                }
                
                val finalBytesForSha1: ByteArray = bytesForSha1!!
                
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                digest.update(finalBytesForSha1)
                val computedSha1 = digest.digest().joinToString("") { String.format("%02x", it) }
                


                Log.d("BookScanner", "[MANUAL-SHA1] Calculated SHA-1: $computedSha1 for manual imported file: $fileName")
                
                // Query database directly to avoid any flow latency/cache duplication issues
                val duplicate = repository.getBookBySha1(computedSha1)
                
                val importedFolder = java.io.File(context.filesDir, "imported_books")
                if (!importedFolder.exists()) {
                    importedFolder.mkdirs()
                }
                val localFile = java.io.File(importedFolder, "$computedSha1.$ext")
                localFile.writeBytes(bytes)

                if (duplicate != null) {
                    Log.d("BookScanner", "[MANUAL-DUPLICATE] Book with SHA-1 $computedSha1 already exists in database. Updating its file path to ${localFile.absolutePath}")
                    val updatedBook = duplicate.copy(filePath = localFile.absolutePath)
                    repository.insertBook(updatedBook)
                    withContext(Dispatchers.Main) {
                        onResult(true, "Книга \"${duplicate.title}\" успешно обновлена (путь изменен)!")
                    }
                    return@launch
                }
                
                var parsedTitle = fileName.substringBeforeLast(".")
                var parsedAuthor = "Неизвестен"
                var parsedContent = ""
                var parsedSeries: String? = null
                var parsedSeriesIndex: Int? = null
                var parsedLanguage: String? = "ru"
                var parsedAnnotation: String? = null
                
                if (ext == "fb2") {
                    val rawText = decodeBytesToString(bytes)
                    val parsed = parseFb2DetailedText(rawText, parsedTitle)
                    parsedTitle = parsed.title
                    parsedAuthor = parsed.author
                    parsedContent = parsed.content
                    parsedSeries = parsed.series
                    parsedSeriesIndex = parsed.seriesIndex
                    parsedLanguage = parsed.language
                    parsedAnnotation = parsed.annotation
                } else if (ext == "epub") {
                    try {
                        val tempFile = java.io.File.createTempFile("epub_import", ".epub", context.cacheDir)
                        tempFile.writeBytes(bytes)
                        val metadata = com.nightread.app.service.EpubParser.parseEpub(tempFile, parsedTitle)
                        parsedTitle = metadata.title
                        parsedAuthor = metadata.author
                        parsedContent = metadata.content
                        parsedLanguage = metadata.language
                        parsedAnnotation = metadata.annotation
                        tempFile.delete()
                    } catch (e: Exception) {
                        Log.e("BookScanner", "Error parsing manual imported EPUB: ", e)
                    }
                } else if (ext == "mobi" || ext == "azw3") {
                    try {
                        val tempFile = java.io.File.createTempFile("mobi_import", ".$ext", context.cacheDir)
                        tempFile.writeBytes(bytes)
                        val metadata = com.nightread.app.service.MobiParser.parseMobi(tempFile, parsedTitle)
                        parsedTitle = metadata.title
                        parsedAuthor = metadata.author
                        parsedContent = metadata.content
                        parsedLanguage = metadata.language
                        parsedAnnotation = metadata.annotation
                        tempFile.delete()
                    } catch (e: Exception) {
                        Log.e("BookScanner", "Error parsing manual imported MOBI: ", e)
                    }
                } else if (ext == "zip") {
                    // Extract data from the uncompressed bytes we found earlier
                    val rawText = decodeBytesToString(finalBytesForSha1)
                    val parsed = parseFb2DetailedText(rawText, fileName.substringBeforeLast(".").removeSuffix(".fb2"))
                    parsedTitle = parsed.title
                    parsedAuthor = parsed.author
                    parsedContent = parsed.content
                    parsedSeries = parsed.series
                    parsedSeriesIndex = parsed.seriesIndex
                    parsedLanguage = parsed.language
                    parsedAnnotation = parsed.annotation
                } else {
                    parsedContent = decodeBytesToString(bytes)
                    parsedAuthor = "Локальный TXT"
                }
                
                if (parsedContent.isBlank()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Файл не содержит читаемого текста.")
                    }
                    return@launch
                }
                
                val coverPath = extractAndSaveCover(localFile, computedSha1)
                
                val strippedContent = if (localFile.extension.lowercase() == "fb2") {
                    com.nightread.app.service.NewCoverExtractor.stripBinarySections(parsedContent)
                } else {
                    parsedContent
                }
                
                val newBook = BookEntity(
                    title = parsedTitle,
                    author = parsedAuthor,
                    category = "Локальные",
                    totalCharacters = strippedContent.length,
                    coverGradientStart = getRandomGradientStartColor(),
                    coverGradientEnd = getRandomGradientEndColor(),
                    filePath = localFile.absolutePath,
                    sha1 = computedSha1,
                    series = parsedSeries,
                    language = parsedLanguage,
                    fileSize = bytes.size.toLong(),
                    coverPath = coverPath,
                    seriesIndex = parsedSeriesIndex,
                    annotation = parsedAnnotation
                )
                
                Log.d("BookScanner", "[MANUAL-DB-INSERT] Saving newly imported book: ${newBook.title} (SHA-1: ${newBook.sha1})")
                repository.insertBook(newBook)
                
                withContext(Dispatchers.Main) {
                    onResult(true, "Книга \"$parsedTitle\" успешно импортирована!")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BookScanner", "Error importing from SAF: ", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Ошибка импорта: ${e.localizedMessage}")
                }
            }
        }
    }

    fun toggleFavorite(bookSha1: String) {
        viewModelScope.launch {
            if (!isActive) return@launch
            val book = allBooks.value.find { it.sha1 == bookSha1 }
            if (book != null) {
                val updated = book.copy(isFavorite = !book.isFavorite)
                repository.updateBook(updated)
                if (selectedBook?.sha1 == bookSha1) {
                    selectedBook = updated
                }
                if (detailedBook?.sha1 == bookSha1) {
                    detailedBook = updated
                }
            }
        }
    }

    fun updateBookReview(bookSha1: String, reviewText: String) {
        viewModelScope.launch {
            if (!isActive) return@launch
            val book = allBooks.value.find { it.sha1 == bookSha1 }
            if (book != null) {
                val updated = book.copy(review = reviewText)
                repository.updateBook(updated)
                if (selectedBook?.sha1 == bookSha1) {
                    selectedBook = updated
                }
                if (detailedBook?.sha1 == bookSha1) {
                    detailedBook = updated
                }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            val importedFolder = java.io.File(getApplication<android.app.Application>().filesDir, "imported_books")
            if (importedFolder.exists()) {
                importedFolder.deleteRecursively()
            }
            // Also optionally clear any other cache, like Yandex sync cache
            repository.deleteAllBooks()
        }
    }

    fun clearScanCache() {
        viewModelScope.launch {
            repository.clearScanCache(getApplication())
        }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            repository.clearLibrary()
        }
    }

    fun resetLibrary() {
        viewModelScope.launch {
            repository.resetDatabase()
        }
    }

    fun cancelAllScanningTasks() {
        val context = getApplication<Application>()
        try {
            val workManager = androidx.work.WorkManager.getInstance(context)
            workManager.cancelUniqueWork("AutoDiscoveryWorker")
            workManager.cancelUniqueWork("AutoDiscoveryOnce")
            workManager.cancelAllWorkByTag("AutoDiscoveryOnce")
            workManager.cancelAllWorkByTag("BookScanWorker")
            workManager.cancelAllWorkByTag("com.nightread.app.service.BookScanWorker")
        } catch (e: Exception) {
            Log.e("BookViewModel", "Failed to cancel WorkManager tasks", e)
        }
        com.nightread.app.service.NewBookScanState.updateState(
            com.nightread.app.service.ScannerState(isScanning = false, status = "Сканирование отменено")
        )
    }

    private fun getRandomGradientStartColor(): String {
        val colors = listOf("#FF6B6B", "#4D96FF", "#6BCB77", "#FFD93D", "#9B5DE5", "#00F5D4")
        return colors.random()
    }

    private fun getRandomGradientEndColor(): String {
        val colors = listOf("#2B2E4A", "#1A1A2E", "#0F3460", "#2D4263", "#3F3B6C", "#1E5128")
        return colors.random()
    }

    // Note Interactions
    fun createNote(selectedText: String, noteText: String) {
        val book = selectedBook ?: return
        val offset = readerPage * 1000
        viewModelScope.launch {
            if (!isActive) return@launch
            val note = NoteEntity(
                bookId = book.sha1,
                bookTitle = book.title,
                selectedText = selectedText,
                noteText = noteText,
                charOffset = offset
            )
            repository.insertNote(note)
        }
    }

    fun deleteNote(noteId: Int) {
        viewModelScope.launch {
            if (!isActive) return@launch
            repository.deleteNoteById(noteId)
        }
    }

    // Sync Commands
    fun refreshIpAddress() {
        localIpAddress = syncManager.getLocalIpAddress()
    }

    fun startSyncServer() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            isServerRunning = true
            syncServerLogs.clear()
            syncServerLogs.add("Запуск сервера...")
            syncManager.startSyncServer(
                repository = repository,
                port = 8080,
                onLog = { logMsg ->
                    viewModelScope.launch(Dispatchers.Main) {
            if (!isActive) return@launch
                        syncServerLogs.add(logMsg)
                    }
                }
            )
        }
    }

    fun stopSyncServer() {
        syncManager.stopSyncServer()
        isServerRunning = false
        syncServerLogs.add("Сервер остановлен.")
    }

    fun startClientSync() {
        val ip = remoteServerIp.trim()
        if (ip.isEmpty()) {
            syncServerLogs.add("Ошибка: Не указан IP адрес сервера.")
            return
        }
        isSyncLoading = true
        viewModelScope.launch {
            if (!isActive) return@launch
            val success = syncManager.clientSync(
                repository = repository,
                ipAddress = ip,
                port = 8080,
                onLog = { logMsg ->
                    viewModelScope.launch(Dispatchers.Main) {
            if (!isActive) return@launch
                        syncServerLogs.add(logMsg)
                    }
                }
            )
            isSyncLoading = false
        }
    }

    fun performCloudSync() {
        val url = cloudSyncUrl.trim()
        if (url.isEmpty()) {
            syncServerLogs.add("Ошибка: Не указана ссылка на облачный сервер.")
            return
        }
        isSyncLoading = true
        viewModelScope.launch {
            if (!isActive) return@launch
            syncManager.syncWithCloud(
                repository = repository,
                url = url,
                onLog = { logMsg ->
                    viewModelScope.launch(Dispatchers.Main) {
            if (!isActive) return@launch
                        syncServerLogs.add(logMsg)
                    }
                }
            )
            isSyncLoading = false
        }
    }

    fun exportDatabaseState() {
        viewModelScope.launch {
            if (!isActive) return@launch
            exportJsonString = syncManager.exportToJson(repository)
            syncServerLogs.add("Данные успешно экспортированы в текстовый буфер.")
        }
    }

    fun importDatabaseState(json: String) {
        viewModelScope.launch {
            if (!isActive) return@launch
            isSyncLoading = true
            val success = syncManager.importFromJson(repository, json)
            isSyncLoading = false
            if (success) {
                syncServerLogs.add("Импорт завершен успешно! База данных обновлена.")
            } else {
                syncServerLogs.add("Ошибка: Не удалось распознать формат JSON.")
            }
        }
    }

    // Gemini API integration
    fun askGemini(prompt: String) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            aiError = "Ключ API Gemini не настроен. Настройте его через панель Secrets в AI Studio, чтобы использовать ИИ-ассистента."
            aiResult = ""
            return
        }

        aiLoading = true
        aiError = null
        aiResult = "ИИ думает..."

        viewModelScope.launch(Dispatchers.IO) {
            if (!isActive) return@launch
            try {
                val finalPrompt = "Вы — профессиональный литературный критик и ассистент по чтению. Ответьте на вопрос по книге или заметке кратко и ёмко на русском языке.\n\n$prompt"
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = finalPrompt))
                        )
                    )
                )
                val response = GeminiClient.service.generateContent(apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                viewModelScope.launch(Dispatchers.Main) {
                    if (!isActive) return@launch
                    aiLoading = false
                    if (textResponse != null) {
                        aiResult = textResponse
                    } else {
                        aiError = "Не удалось получить ответ от ИИ."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    if (!isActive) return@launch
                    aiLoading = false
                    aiError = "Ошибка соединения: ${e.localizedMessage}"
                }
            }
        }
    }

    fun startLocalBookScan(rootPath: String = "/storage/emulated/0") {
        if (isScanning) return
        
        isScanning = true
        com.nightread.app.service.AutoDiscoveryService.isManualScanning = true
        
        val context = getApplication<android.app.Application>()
        if (context == null) {
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
            android.util.Log.e("BookViewModel", "startLocalBookScan: Application Context is null")
            return
        }
        
        val db = database
        if (db == null) {
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
            android.util.Log.e("BookViewModel", "startLocalBookScan: AppDatabase is null")
            return
        }
        
        val dao = try {
            db.bookDao()
        } catch (e: Exception) {
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
            android.util.Log.e("BookViewModel", "startLocalBookScan: Failed to obtain BookDao", e)
            null
        }
        
        if (dao == null) {
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
            android.util.Log.e("BookViewModel", "startLocalBookScan: BookDao is null")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!isActive) {
                withContext(Dispatchers.Main) {
                    isScanning = false
                    com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                }
                return@launch
            }
            try {
                val app = context as? com.nightread.app.MainApplication
                val scanner = app?.bookScanner ?: com.nightread.app.service.NewBookScanner(context, dao).also { app?.bookScanner = it }
                scanner.scanBooks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BookViewModel", "Failed to scan books locally", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isScanning = false
                    com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                }
            }
        }
    }

    fun startIncrementalBookScan() {
        if (isScanning) return
        
        isScanning = true
        com.nightread.app.service.AutoDiscoveryService.isManualScanning = true
        
        val context = getApplication<android.app.Application>()
        if (context == null) {
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
            android.util.Log.e("BookViewModel", "startIncrementalBookScan: Application Context is null")
            return
        }
        
        val db = database
        if (db == null) {
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
            android.util.Log.e("BookViewModel", "startIncrementalBookScan: AppDatabase is null")
            return
        }
        
        val dao = try {
            db.bookDao()
        } catch (e: Exception) {
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
            android.util.Log.e("BookViewModel", "startIncrementalBookScan: Failed to obtain BookDao", e)
            null
        }
        
        if (dao == null) {
            isScanning = false
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!isActive) {
                withContext(Dispatchers.Main) {
                    isScanning = false
                    com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                }
                return@launch
            }
            try {
                val app = context as? com.nightread.app.MainApplication
                val scanner = app?.bookScanner ?: com.nightread.app.service.NewBookScanner(context, dao).also { app?.bookScanner = it }
                scanner.checkForNewBooks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BookViewModel", "Failed to scan books incrementally", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isScanning = false
                    com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                }
            }
        }
    }

    private suspend fun scanDirectoryForBooks(rootPath: String) {
        withContext(Dispatchers.Main) {
            isScanning = true
            scanProgressText = "Запуск сканирования..."
        }
        
        try {
            withContext(Dispatchers.IO) {
                val existingSha1s = try {
                    repository.allBooks.first().mapNotNull { it.sha1 }.toMutableSet()
                } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Failed to load existing SHA1 list", e)
                    mutableSetOf<String>()
                }

                val existingTitles = try {
                    repository.allBooks.first().map { it.title.lowercase() }.toMutableSet()
                } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Failed to load existing titles", e)
                    mutableSetOf<String>()
                }
                
                val rootDir = java.io.File(rootPath)
                if (!rootDir.exists() || !rootDir.isDirectory) {
                    withContext(Dispatchers.Main) {
                        scanProgressText = "Папка не найдена или недоступна: $rootPath"
                    }
                    return@withContext
                }
                
                val filesToProcess = mutableListOf<java.io.File>()
                
                fun traverse(dir: java.io.File) {
                    try {
                        val list = dir.listFiles() ?: return
                        for (file in list) {
                            if (file.isDirectory) {
                                val name = file.name.lowercase()
                                if (name.startsWith(".") || name == "android" || name == "cache" || name == "temp" || name == "tmp" || name == "thumbnails" || name == "thumbnail") {
                                    continue
                                }
                                traverse(file)
                            } else {
                                val ext = file.extension.lowercase()
                                if (ext == "txt" || ext == "fb2" || ext == "epub" || ext == "zip" || ext == "mobi" || ext == "azw3" || ext == "pdf") {
                                    // Exclude files >= 30MB to prevent memory crashes
                                    if (file.length() < 30 * 1024 * 1024 && file.length() > 0) {
                                        filesToProcess.add(file)
                                    }
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                        android.util.Log.e("BookScanner", "Error traversing directory: ${dir.absolutePath}", e)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    scanProgressText = "Поиск файлов в $rootPath..."
                }
                traverse(rootDir)
                
                if (filesToProcess.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        scanProgressText = "Книг (*.txt, *.fb2, *.epub, *.zip, *.mobi, *.azw3, *.pdf) не найдено в $rootPath"
                    }
                    return@withContext
                }

                var importedCount = 0
                for ((index, file) in filesToProcess.withIndex()) {
                    val progressText = "Чтение (${index + 1}/${filesToProcess.size}): ${file.name}"
                    withContext(Dispatchers.Main) {
                        scanProgressText = progressText
                    }
                    
                    kotlin.runCatching {
                        val ext = file.extension.lowercase()
                        val computedSha1 = calculateSha1(file.absolutePath)
                        
                        // Check duplicates by SHA1
                        if (existingSha1s.contains(computedSha1)) {
                            return@runCatching
                        }

                        var parsedTitle = file.nameWithoutExtension
                        var parsedAuthor = "Неизвестен"
                        var parsedContent = ""
                        var parsedSeries: String? = null
                        var parsedLanguage: String? = "ru"
                        
                        if (ext == "fb2") {
                            val parsed = parseFb2Detailed(file)
                            parsedTitle = parsed.title
                            parsedAuthor = parsed.author
                            parsedContent = parsed.content
                            parsedSeries = parsed.series
                            parsedLanguage = parsed.language
                        } else if (ext == "epub") {
                            val metadata = com.nightread.app.service.EpubParser.parseEpub(file, parsedTitle)
                            parsedTitle = metadata.title
                            parsedAuthor = metadata.author
                            parsedContent = metadata.content
                            parsedLanguage = metadata.language
                        } else if (ext == "mobi" || ext == "azw3") {
                            val metadata = com.nightread.app.service.MobiParser.parseMobi(file, parsedTitle)
                            parsedTitle = metadata.title
                            parsedAuthor = metadata.author
                            parsedContent = metadata.content
                            parsedLanguage = metadata.language
                        } else if (ext == "pdf") {
                            val metadata = com.nightread.app.service.PdfParser.parse(file, parsedTitle)
                            parsedTitle = metadata.title
                            parsedAuthor = metadata.author
                            parsedContent = metadata.content
                            parsedLanguage = "ru"
                        } else if (ext == "zip") {
                            val parsed = parseFb2FromZip(file)
                            parsedTitle = parsed.title
                            parsedAuthor = parsed.author
                            parsedContent = parsed.content
                            parsedSeries = parsed.series
                            parsedLanguage = parsed.language
                        } else {
                            parsedContent = readTextFile(file)
                            parsedAuthor = "Локальный TXT"
                        }
                        
                        if (existingTitles.contains(parsedTitle.lowercase())) {
                            return@runCatching
                        }

                        if (parsedContent.isNotBlank()) {
                            // Extract cover
                            val coverPath = extractAndSaveCover(file, computedSha1)
                            
                            val strippedContent = if (file.extension.lowercase() == "fb2") {
                                com.nightread.app.service.NewCoverExtractor.stripBinarySections(parsedContent)
                            } else {
                                parsedContent
                            }
                            
                            val newBook = BookEntity(
                                title = parsedTitle,
                                author = parsedAuthor,
                                category = "Локальные",
                                totalCharacters = strippedContent.length,
                                coverGradientStart = getRandomGradientStartColor(),
                                coverGradientEnd = getRandomGradientEndColor(),
                                filePath = file.absolutePath,
                                sha1 = computedSha1,
                                series = parsedSeries,
                                language = parsedLanguage,
                                fileSize = file.length(),
                                coverPath = coverPath
                            )
                            repository.insertBook(newBook)
                            existingSha1s.add(computedSha1)
                            existingTitles.add(parsedTitle.lowercase())
                            importedCount++
                        }
                    }.onFailure { t ->
                        android.util.Log.e("BookScanner", "Failed to import book: ${file.name}", t)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (importedCount > 0) {
                        scanProgressText = "Успешно импортировано новых книг: $importedCount"
                    } else {
                        scanProgressText = "Все найденные книги уже есть в библиотеке (${filesToProcess.size} файлов)"
                    }
                }
            }
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Critical error in scanDirectoryForBooks", e)
            withContext(Dispatchers.Main) {
                scanProgressText = "Ошибка сканирования: ${e.localizedMessage}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                isScanning = false
            }
        }
    }

    suspend fun calculateSha1(filePath: String): String = withContext(Dispatchers.IO) {
        val TAG = "SHA1Calculator"
        android.util.Log.d(TAG, "Starting SHA-1 calculation for path: $filePath")
        
        val file = java.io.File(filePath)
        
        // Check if file exists and is readable
        if (!file.exists()) {
            android.util.Log.e(TAG, "Error: File does not exist at path: $filePath")
            return@withContext generateFallbackId(filePath, null)
        }
        if (!file.canRead()) {
            android.util.Log.e(TAG, "Error: File is not readable (insufficient permissions): $filePath")
            return@withContext generateFallbackId(filePath, file)
        }
        
        val fileSize = file.length()
        if (fileSize == 0L) {
            android.util.Log.e(TAG, "Error: File is empty (size is 0 bytes): $filePath")
            return@withContext generateFallbackId(filePath, file)
        }

        kotlin.runCatching {
            val ext = file.extension.lowercase()
            android.util.Log.d(TAG, "Detected file extension: $ext")
            
            when (ext) {
                "fb2" -> {
                    android.util.Log.d(TAG, "Reading directly from FB2 file")
                    java.io.FileInputStream(file).use { fis ->
                        computeSha1FromStream(fis)
                    }
                }
                "epub" -> {
                    android.util.Log.d(TAG, "Reading text from EPUB to compute SHA-1")
                    val metadata = com.nightread.app.service.EpubParser.parseEpub(file, "")
                    val digest = java.security.MessageDigest.getInstance("SHA-1")
                    val hash = digest.digest(metadata.content.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                    hash.joinToString("") { String.format("%02x", it) }
                }
                "zip" -> {
                    android.util.Log.d(TAG, "Decompressing ZIP to find FB2 contents")
                    var fb2Found = false
                    var sha1Result: String? = null
                    
                    // Attempt to parse/decompress ZIP
                    java.io.FileInputStream(file).use { fis ->
                        java.util.zip.ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val entryName = entry.name.lowercase()
                                if (!entry.isDirectory && (entryName.endsWith(".fb2") || entryName.endsWith(".fb2.xml"))) {
                                    android.util.Log.d(TAG, "Found FB2 entry in ZIP: ${entry.name} (size: ${entry.size})")
                                    fb2Found = true
                                    
                                    // Make sure entry is not empty or invalid
                                    if (entry.size == 0L) {
                                        android.util.Log.e(TAG, "FB2 entry inside ZIP is empty: ${entry.name}")
                                    } else {
                                        sha1Result = computeSha1FromStream(zis)
                                        android.util.Log.d(TAG, "Successfully computed SHA-1 for ZIP entry: $sha1Result")
                                    }
                                    break // Grab only the first one as requested
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                    
                    if (!fb2Found) {
                        android.util.Log.e(TAG, "Error: No FB2 file found inside the ZIP archive: $filePath")
                        generateFallbackId(filePath, file)
                    } else if (sha1Result == null) {
                        android.util.Log.e(TAG, "Error: Failed to compute SHA-1 for the first FB2 entry inside the ZIP archive: $filePath")
                        generateFallbackId(filePath, file)
                    } else {
                        sha1Result!!
                    }
                }
                else -> {
                    // Treat other files (txt, etc.) or unknown types by reading directly
                    android.util.Log.d(TAG, "Reading directly from file format: $ext")
                    java.io.FileInputStream(file).use { fis ->
                        computeSha1FromStream(fis)
                    }
                }
            }
        }.getOrElse { throwable ->
            android.util.Log.e(TAG, "Exception caught during SHA-1 computation for: $filePath", throwable)
            generateFallbackId(filePath, file)
        }
    }

    private fun generateFallbackId(filePath: String, file: java.io.File?): String {
        val size = file?.length() ?: 0L
        val lastModified = file?.lastModified() ?: System.currentTimeMillis()
        val uniqueString = "${filePath}_${size}_${lastModified}"
        val fallback = "fallback_" + Math.abs(uniqueString.hashCode().toLong()).toString(16)
        android.util.Log.w("SHA1Calculator", "Generated fallback ID for $filePath: $fallback")
        return fallback
    }

    private fun computeSha1FromStream(inputStream: java.io.InputStream): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { String.format("%02x", it) }
    }

    private fun readTextFile(file: java.io.File): String {
        val bytes = file.readBytes()
        return decodeBytesToString(bytes)
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        try {
            // Safe detection from XML prolog first
            val headerSize = if (bytes.size > 1024) 1024 else bytes.size
            val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val encName = match.groupValues[1].trim()
                try {
                    return String(bytes, java.nio.charset.Charset.forName(encName))
                } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                    // fall back if charset name is invalid or unsupported
                }
            }
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            // ignore and fallback
        }

        try {
            val utf8Decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            utf8Decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            try {
                return String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                return String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }

    private fun parseFb2FromZip(file: java.io.File): ParsedBook {
        java.io.FileInputStream(file).use { fis ->
            java.util.zip.ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                        val bytes = zis.readBytes()
                        val rawText = decodeBytesToString(bytes)
                        return parseFb2DetailedText(rawText, entryName.removeSuffix(".fb2"))
                    }
                    entry = zis.nextEntry
                }
            }
        }
        throw java.io.IOException("No fb2 file found inside zip")
    }

    data class ParsedBook(
        val title: String,
        val author: String,
        val content: String,
        val series: String? = null,
        val language: String? = "ru",
        val seriesIndex: Int? = null,
        val annotation: String? = null
    )

    private fun parseFb2DetailedText(rawText: String, fallbackName: String): ParsedBook {
        val parsed = com.nightread.app.service.NewFb2Parser.parse(rawText, fallbackName)
        return ParsedBook(
            title = parsed.title,
            author = parsed.author,
            content = com.nightread.app.service.TextCleaner.cleanText(parsed.content) as String,
            series = parsed.series,
            language = parsed.language,
            seriesIndex = parsed.seriesIndex,
            annotation = parsed.annotation
        )
    }

    private fun parseFb2Detailed(file: java.io.File): ParsedBook {
        val rawText = readTextFile(file)
        return parseFb2DetailedText(rawText, file.nameWithoutExtension)
    }



    fun extractCoverFromFb2(fb2Content: String): android.graphics.Bitmap? {
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(java.io.StringReader(fb2Content))

            var eventType = parser.eventType
            var coverId: String? = null
            val binaryDataMap = mutableMapOf<String, String>()

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase()
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if (tagName == "image") {
                        for (i in 0 until parser.attributeCount) {
                            val attrName = parser.getAttributeName(i).lowercase()
                            if (attrName == "href" || attrName.endsWith("href")) {
                                val href = parser.getAttributeValue(i)
                                if (coverId == null) {
                                    coverId = href.removePrefix("#")
                                }
                            }
                        }
                    } else if (tagName == "binary") {
                        var id: String? = null
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).lowercase() == "id") {
                                id = parser.getAttributeValue(i)
                            }
                        }
                        if (id != null) {
                            val base64Text = parser.nextText()
                            binaryDataMap[id] = base64Text
                        }
                    }
                }
                eventType = parser.next()
            }

            val targetId = coverId ?: "cover"
            var base64Data = binaryDataMap[targetId] ?: binaryDataMap[coverId]
            
            if (base64Data == null) {
                val key = binaryDataMap.keys.find { it.lowercase().contains("cover") }
                if (key != null) {
                    base64Data = binaryDataMap[key]
                }
            }
            
            if (base64Data == null && binaryDataMap.isNotEmpty()) {
                base64Data = binaryDataMap.values.first()
            }

            if (base64Data != null) {
                val cleanBase64 = base64Data.replace("\\s".toRegex(), "")
                val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                return android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Error parsing FB2 cover with XmlPullParser", e)
        }
        return null
    }

    fun extractCoverUsingRegex(fb2Content: String): android.graphics.Bitmap? {
        try {
            // 1. Parse all binaries. Robust to attribute order, spaces, single/double quotes, and namespace tags
            val binaryDataMap = mutableMapOf<String, String>()
            val binaryBlockRegex = """<binary([^>]*)>([\s\S]*?)</binary>""".toRegex(RegexOption.IGNORE_CASE)
            for (match in binaryBlockRegex.findAll(fb2Content)) {
                val attrs = match.groups[1]?.value ?: ""
                val base64 = match.groups[2]?.value ?: ""
                val idMatch = """\bid\s*=\s*["']?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE).find(attrs)
                val id = idMatch?.groups[1]?.value
                if (id != null) {
                    binaryDataMap[id] = base64
                    binaryDataMap[id.lowercase()] = base64
                }
            }

            // 2. Find the coverpage image ID
            // First try specifically within <coverpage>...</coverpage>
            val coverpageRegex = """<coverpage>([\s\S]*?)</coverpage>""".toRegex(RegexOption.IGNORE_CASE)
            val coverpageMatch = coverpageRegex.find(fb2Content)
            var coverId: String? = null
            if (coverpageMatch != null) {
                val coverpageContent = coverpageMatch.groups[1]?.value ?: ""
                val imageRegex = """<image[^>]*(?:href|l:href)\s*=\s*["']?#?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
                val imageMatch = imageRegex.find(coverpageContent)
                coverId = imageMatch?.groups[1]?.value
            }

            // Fallback: look for the first <image> tag in the document
            if (coverId == null) {
                val imageRegex = """<image[^>]*(?:href|l:href)\s*=\s*["']?#?([^"'\s>]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
                val imageMatch = imageRegex.find(fb2Content)
                coverId = imageMatch?.groups[1]?.value
            }

            // 3. Retrieve base64 data
            var base64Data: String? = null
            if (coverId != null) {
                base64Data = binaryDataMap[coverId] ?: binaryDataMap[coverId.lowercase()] ?: binaryDataMap[coverId.removePrefix("#")] ?: binaryDataMap[coverId.removePrefix("#").lowercase()]
            }

            // Fallback: look for keys containing "cover" or "front"
            if (base64Data == null) {
                val coverKey = binaryDataMap.keys.find { it.lowercase().contains("cover") || it.lowercase().contains("front") }
                if (coverKey != null) {
                    base64Data = binaryDataMap[coverKey]
                }
            }

            // Fallback 2: first binary if it looks like an image
            if (base64Data == null && binaryDataMap.isNotEmpty()) {
                base64Data = binaryDataMap.values.first()
            }

            if (base64Data != null) {
                val cleanBase64 = base64Data.replace("\\s".toRegex(), "")
                val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                return android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Error in regex cover extraction", e)
        }
        return null
    }


    fun saveCoverToCache(context: android.content.Context, sha1: String, bitmap: android.graphics.Bitmap): String? {
        return try {
            val cacheDir = java.io.File(context.cacheDir, "book_covers")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = java.io.File(cacheDir, "$sha1.jpg")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Failed to save cover to cache", e)
            null
        }
    }

    fun extractAndSaveCover(file: java.io.File, sha1: String): String? {
        val ext = file.extension.lowercase()
        var bitmap: android.graphics.Bitmap? = null
        try {
            if (ext == "fb2") {
                val fb2Content = readTextFile(file)
                bitmap = extractCoverFromFb2(fb2Content) ?: extractCoverUsingRegex(fb2Content)
            } else if (ext == "epub") {
                try {
                    val metadata = com.nightread.app.service.EpubParser.parseEpub(file, "")
                    val coverBytes = metadata.coverBytes
                    if (coverBytes != null && coverBytes.isNotEmpty()) {
                        bitmap = android.graphics.BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Error extracting cover from EPUB file", e)
                }
            } else if (ext == "mobi" || ext == "azw3") {
                try {
                    val metadata = com.nightread.app.service.MobiParser.parseMobi(file, "")
                    val coverBytes = metadata.coverBytes
                    if (coverBytes != null && coverBytes.isNotEmpty()) {
                        bitmap = android.graphics.BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Error extracting cover from MOBI file", e)
                }
            } else if (ext == "pdf") {
                try {
                    val coverBytes = com.nightread.app.service.PdfParser.extractCoverBytes(file)
                    if (coverBytes != null && coverBytes.isNotEmpty()) {
                        bitmap = android.graphics.BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Error extracting cover from PDF file", e)
                }
            } else if (ext == "zip") {
                java.io.FileInputStream(file).use { fis ->
                    java.util.zip.ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                val bytes = zis.readBytes()
                                val fb2Content = decodeBytesToString(bytes)
                                bitmap = extractCoverFromFb2(fb2Content) ?: extractCoverUsingRegex(fb2Content)
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            }
            
            if (bitmap != null) {
                return saveCoverToCache(getApplication<Application>(), sha1, bitmap)
            }
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            android.util.Log.e("BookScanner", "Error in extractAndSaveCover for file ${file.name}", e)
        }
        return null
    }

    /**
     * Determines the encoding of the FB2 file from the XML prolog,
     * reads the file, and extracts the contents of the <annotation> tag in the correct encoding.
     */
    suspend fun detectAndReadFile(filePath: String): String? = withContext(Dispatchers.IO) {
        val TAG = "FB2Annotation"
        android.util.Log.d(TAG, "Detecting encoding and extracting annotation for file: $filePath")
        
        val bytes = readFirstBytesOfFb2(filePath)
        if (bytes == null || bytes.isEmpty()) {
            android.util.Log.e(TAG, "Failed to read bytes from file: $filePath")
            return@withContext null
        }
        
        // 1. Detect encoding from XML prolog
        val detectedEncoding = parseEncodingFromProlog(bytes)
        val charsetsToTry = mutableListOf<String>()
        
        if (detectedEncoding != null) {
            charsetsToTry.add(detectedEncoding)
        }
        
        // Add fallback candidate charsets
        val fallbacks = listOf("UTF-8", "windows-1251", "KOI8-R", "ISO-8859-1")
        for (fallback in fallbacks) {
            if (!charsetsToTry.contains(fallback)) {
                charsetsToTry.add(fallback)
            }
        }
        
        android.util.Log.d(TAG, "Will attempt charsets in order: $charsetsToTry")
        
        for (charset in charsetsToTry) {
            try {
                if (!java.nio.charset.Charset.isSupported(charset)) {
                    android.util.Log.w(TAG, "Charset not supported by JVM: $charset")
                    continue
                }
                
                android.util.Log.d(TAG, "Trying to extract annotation using charset: $charset")
                val bais = java.io.ByteArrayInputStream(bytes)
                val annotation = extractAnnotationFromStream(bais, charset)
                
                if (annotation != null) {
                    // Check if the decoded text looks malformed (e.g., replacement character or only unreadable characters)
                    if (charset.lowercase() == "utf-8" && hasMalformedCharacters(annotation)) {
                        android.util.Log.w(TAG, "Parsed UTF-8 string contains malformed characters, trying next charset.")
                        continue
                    }
                    
                    android.util.Log.i(TAG, "Successfully extracted annotation in encoding: $charset")
                    return@withContext annotation
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error extracting annotation with charset $charset", e)
            }
        }
        
        android.util.Log.w(TAG, "Failed to extract annotation with any of the candidate charsets")
        return@withContext null
    }

    /**
     * Helper to read the first 256KB of the FB2 file (from a direct file or a ZIP).
     * This holds enough content to cover the XML prolog and the <annotation> block.
     */
    private fun readFirstBytesOfFb2(filePath: String, limit: Int = 256 * 1024): ByteArray? {
        val file = java.io.File(filePath)
        if (!file.exists()) return null
        
        val ext = file.extension.lowercase()
        if (ext == "zip") {
            try {
                java.io.FileInputStream(file).use { fis ->
                    java.util.zip.ZipInputStream(fis).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && (entryName.endsWith(".fb2") || entryName.endsWith(".fb2.xml"))) {
                                val bos = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                var totalRead = 0
                                var read: Int = 0
                                while (totalRead < limit && zis.read(buffer).also { read = it } != -1) {
                                    val toWrite = minOf(read, limit - totalRead)
                                    bos.write(buffer, 0, toWrite)
                                    totalRead += toWrite
                                }
                                return bos.toByteArray()
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FB2Annotation", "Error reading ZIP file: $filePath", e)
            }
        } else {
            try {
                java.io.FileInputStream(file).use { fis ->
                    val bos = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var totalRead = 0
                    var read: Int = 0
                    while (totalRead < limit && fis.read(buffer).also { read = it } != -1) {
                        val toWrite = minOf(read, limit - totalRead)
                        bos.write(buffer, 0, toWrite)
                        totalRead += toWrite
                    }
                    return bos.toByteArray()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FB2Annotation", "Error reading FB2 file: $filePath", e)
            }
        }
        return null
    }

    /**
     * Looks at the XML prolog in the header bytes to find the encoding attribute.
     */
    private fun parseEncodingFromProlog(bytes: ByteArray): String? {
        try {
            val size = minOf(bytes.size, 1024)
            val header = String(bytes, 0, size, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """<\?xml[^>]*encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val enc = match.groupValues[1].trim()
                android.util.Log.d("FB2Annotation", "Parsed encoding from XML prolog: $enc")
                return enc
            }
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            android.util.Log.e("FB2Annotation", "Error parsing prolog encoding", e)
        }
        return null
    }

    /**
     * Parses the annotation from an input stream using the specified charset.
     */
    private fun extractAnnotationFromStream(inputStream: java.io.InputStream, charsetName: String): String? {
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            
            val reader = java.io.InputStreamReader(inputStream, charsetName)
            parser.setInput(reader)
            
            var eventType = parser.eventType
            var inAnnotation = false
            val annotationText = java.lang.StringBuilder()
            
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name?.lowercase()
                
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (tagName == "annotation" || tagName == "description") {
                            // If we enter annotation or description (some files use description directly)
                            inAnnotation = true
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inAnnotation) {
                            val txt = parser.text
                            if (txt != null) {
                                annotationText.append(txt)
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (tagName == "p" && inAnnotation) {
                            annotationText.append("\n")
                        } else if (tagName == "annotation" || tagName == "description") {
                            inAnnotation = false
                            break
                        }
                    }
                }
                eventType = parser.next()
            }
            
            val result = annotationText.toString().trim()
            return if (result.isNotEmpty()) result else null
        } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            android.util.Log.e("FB2Annotation", "Failed to parse XML using charset $charsetName", e)
            return null
        }
    }

    /**
     * Validates whether a string has been incorrectly decoded (i.e. contains replacement characters).
     */
    private fun hasMalformedCharacters(str: String): Boolean {
        return str.contains('\uFFFD')
    }

    override fun onCleared() {
        super.onCleared()
        syncManager.stopSyncServer()
    }
}
