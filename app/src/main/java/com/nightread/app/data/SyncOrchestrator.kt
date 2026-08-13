package com.nightread.app.data

import android.content.Context
import android.os.Environment
import android.util.Log
import com.nightread.app.service.NewCoverExtractor
import com.nightread.app.service.Fb2Parser
import com.nightread.app.data.Sha1Helper.computeSha1Stream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.nio.charset.StandardCharsets
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Оркестратор синхронизации с Яндекс Диском.
 * Отвечает за координацию шагов: получение списка файлов, сопоставление хэшей, скачивание и загрузку книг.
 */
class SyncOrchestrator(
    private val context: Context,
    private val cloudService: CloudFileService,
    private val sha1Extractor: Sha1Extractor,
    private val cacheManager: SyncCacheManager,
    private val progressTracker: SyncProgressTracker
) {
    private val TAG = "SYNC_ORCHESTRATOR"
    
    var isCancelled: Boolean
        get() = SyncCancellationManager.isCancelled()
        set(value) { SyncCancellationManager.setCancelled(value) }

    suspend fun sync() {
        SyncCancellationManager.setCancelled(false)
        val syncFolder = try {
            YandexDiskManager.getSyncFolder(context)
        } catch (e: Exception) {
            SyncErrorHandler.logError("SyncOrchestrator", e, false)
            throw Exception("Не удалось получить путь папки синхронизации: ${e.localizedMessage}", e)
        }
        
        Log.d(TAG, "Starting sync in folder: $syncFolder")

        val syncManager = YandexSyncManager(context)
        if (!syncManager.hasInternetConnection()) {
            SyncErrorHandler.logError("SyncOrchestrator Internet", Exception("No internet connection during synchronization initialization."), false)
            throw Exception("Отсутствует подключение к интернету")
        }
        
        val token = YandexDiskManager.getToken(context)
        if (token.isNullOrEmpty()) {
            SyncErrorHandler.logError("SyncOrchestrator Token", Exception("Yandex Disk token is empty or null."), false)
            throw Exception("Ошибка авторизации Яндекс Диска: отсутствует токен")
        }

        val authHeader = "OAuth $token"
        val progressFolder = "$syncFolder/Progress"
        val cloudProgressMap = java.util.concurrent.ConcurrentHashMap<String, BookProgressPayload>()
        val progressAdapter = YandexDiskManager.moshi.adapter(BookProgressPayload::class.java)

        try {
            // Ensure sync and progress directories exist on Yandex Disk
            val pathsToCreate = listOf(syncFolder, progressFolder)
            for (path in pathsToCreate) {
                try {
                    YandexDiskManager.api.createDirectory(authHeader, path)
                } catch (e: Exception) {
                    Log.d(TAG, "Directory already exists: $path")
                }
            }

            // Obtain local BookDao
            val db = AppDatabase.getDatabase(context)
            val bookDao = db.bookDao()

            val cloudManifest = try { YandexDiskManager.fetchSyncManifestFromCloud(context) } catch (e: Exception) { null }
            val allLocalBooks = try { bookDao.getAllBooksSync() } catch (e: Exception) { emptyList() }
            val localBooksBySyncKey = HashMap<String, BookEntity>()
            val localBooksBySha1 = HashMap<String, BookEntity>()
            for (b in allLocalBooks) {
                val fn = b.filePath?.let { File(it).name } ?: "${b.title}.fb2"
                val syncKey = SyncKeyHelper.getSyncKey(fn, b.sha1)
                if (syncKey.isNotEmpty()) {
                    localBooksBySyncKey[syncKey] = b
                }
                if (!b.sha1.isNullOrEmpty()) {
                    localBooksBySha1[b.sha1] = b
                }
            }

            if (cloudManifest != null && cloudManifest.items.isNotEmpty()) {
                Log.d(TAG, "Fast sync: Loaded cloud sync manifest with ${cloudManifest.items.size} items in 1 HTTP request.")
                progressTracker.startStage("Синхронизация прогресса", cloudManifest.items.size, "Быстрая синхронизация прогресса...")
                for (cloudProgress in cloudManifest.items) {
                    cloudProgressMap[cloudProgress.sha1] = cloudProgress
                    val localBook = localBooksBySyncKey[cloudProgress.sha1] ?: localBooksBySha1[cloudProgress.sha1]
                    if (localBook != null) {
                        val isCloudZero = cloudProgress.page == 0 && cloudProgress.charOffset == 0
                        val isLocalNonZero = localBook.currentPageIndex > 0 || localBook.currentProgressChar > 0
                        if (cloudProgress.lastReadTime > localBook.lastReadTime && !(isCloudZero && isLocalNonZero)) {
                            bookDao.updateProgressAndPage(
                                sha1 = localBook.sha1,
                                charOffset = cloudProgress.charOffset,
                                pageIndex = cloudProgress.page,
                                totalChars = cloudProgress.totalChars,
                                timestamp = cloudProgress.lastReadTime
                            )
                            context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putInt("book_page_${cloudProgress.sha1}", cloudProgress.page)
                                .putInt("book_char_offset_${cloudProgress.sha1}", cloudProgress.charOffset)
                                .apply()
                            Log.d(TAG, "Updated local progress via manifest for: ${localBook.title}")
                        }
                    }
                }
            } else {
                // Fetch progress items from Yandex Disk
                val progressItems = try {
                    YandexDiskManager.getAllFilesFromFolder(context, authHeader, progressFolder)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch progress files list", e)
                    emptyList()
                }

                if (progressItems.isNotEmpty()) {
                    progressTracker.startStage("Синхронизация прогресса", progressItems.size, "Синхронизация прогресса чтения...")
                    val progressSemaphore = Semaphore(10)
                    coroutineScope {
                        val progressJobs = progressItems.map { item ->
                            async {
                                if (item.name.endsWith(".json")) {
                                    progressSemaphore.withPermit {
                                        if (isCancelled) return@async
                                        try {
                                            val cleanPath = YandexDiskManager.normalizePath(item.path ?: "$progressFolder/${item.name}")
                                            val linkResponse = YandexDiskManager.api.getDownloadLink(authHeader, cleanPath)
                                            val body = YandexDiskManager.api.downloadFile(linkResponse.href)
                                            val jsonStr = body.string()
                                            val cloudProgress = progressAdapter.fromJson(jsonStr)
                                             if (cloudProgress != null) {
                                                cloudProgressMap[cloudProgress.sha1] = cloudProgress
                                                
                                                val localBook = localBooksBySyncKey[cloudProgress.sha1] ?: localBooksBySha1[cloudProgress.sha1]

                                                if (localBook != null) {
                                                    val isCloudZero = cloudProgress.page == 0 && cloudProgress.charOffset == 0
                                                    val isLocalNonZero = localBook.currentPageIndex > 0 || localBook.currentProgressChar > 0
                                                    if (cloudProgress.lastReadTime > localBook.lastReadTime && !(isCloudZero && isLocalNonZero)) {
                                                        bookDao.updateProgressAndPage(
                                                            sha1 = localBook.sha1,
                                                            charOffset = cloudProgress.charOffset,
                                                            pageIndex = cloudProgress.page,
                                                            totalChars = cloudProgress.totalChars,
                                                            timestamp = cloudProgress.lastReadTime
                                                        )
                                                        context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
                                                            .edit()
                                                            .putInt("book_page_${cloudProgress.sha1}", cloudProgress.page)
                                                            .putInt("book_char_offset_${cloudProgress.sha1}", cloudProgress.charOffset)
                                                            .apply()
                                                        Log.d(TAG, "Updated local progress for: ${localBook.title} (offset: ${cloudProgress.charOffset}, page: ${cloudProgress.page})")
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error downloading or applying progress for ${item.name}", e)
                                        }
                                    }
                                }
                            }
                        }
                        progressJobs.awaitAll()
                    }
                }
            }

            // Stage 1: Получение списка файлов с диска
            progressTracker.startStage("Получение списка файлов", 0, "Получение списка файлов с диска...")
            if (isCancelled) return
            val cloudFiles = try {
                cloudService.getFileList(syncFolder)
            } catch (e: Exception) {
                Log.e(TAG, "Stage 1: Failed to retrieve file list from $syncFolder", e)
                throw Exception("Не удалось получить список файлов с Яндекс Диска: ${e.localizedMessage}", e)
            }
            Log.d(TAG, "Found ${cloudFiles.size} files in $syncFolder")

            // Filter for supported formats: ebooks and documents
            val filteredCloudFiles = cloudFiles.filter {
                val name = it.name.lowercase()
                com.nightread.app.data.BookFormatHelper.isSupported(name) || name.endsWith(".zip")
            }
            Log.d(TAG, "Filtered ${filteredCloudFiles.size} books to process")

            if (filteredCloudFiles.isEmpty()) {
                progressTracker.finishStage("Сравнение с библиотекой", "Книг на диске не найдено.")
            }

            // Stage 2: Анализ файлов с диска (вычисление или получение SHA-1 из кэша)
            progressTracker.startStage("Анализ файлов", filteredCloudFiles.size, "Анализ файлов на Яндекс Диске...")
            val cloudSha1Map = java.util.concurrent.ConcurrentHashMap<String, String>() // cloudPath -> sha1
            val cloudSha1ToPath = java.util.concurrent.ConcurrentHashMap<String, String>() // sha1 -> cloudPath
            
            val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val semaphore = Semaphore(8)

            // Предварительная загрузка локальных книг для сопоставления
            val localBooks = try { bookDao.getAllBooksSync() } catch (e: Exception) { emptyList() }
            val localBooksMapByName = HashMap<String, String>()
            for (b in localBooks) {
                val sha = b.sha1
                val fn = b.filePath?.let { File(it).name.lowercase(java.util.Locale.ROOT) } ?: ""
                if (fn.isNotEmpty() && !sha.isNullOrEmpty()) {
                    localBooksMapByName[fn] = sha
                    val fnNoExt = fn.substringBeforeLast(".")
                    localBooksMapByName[fnNoExt] = sha
                    if (fnNoExt.endsWith(".fb2")) {
                        localBooksMapByName[fnNoExt.substringBeforeLast(".fb2")] = sha
                    }
                }
            }

            coroutineScope {
                val jobs = filteredCloudFiles.map { file ->
                    async {
                        semaphore.withPermit {
                            if (isCancelled) return@async
                            
                            val normalizedPath = YandexDiskManager.normalizePath(file.path)
                            val fileLowerName = file.name.lowercase(java.util.Locale.ROOT)
                            val fileBaseName = fileLowerName.substringBeforeLast(".")
                            val fileBaseNoFb2 = if (fileBaseName.endsWith(".fb2")) fileBaseName.substringBeforeLast(".fb2") else fileBaseName

                            try {
                                var sha1: String? = null
                                
                                // 1. Проверяем кэш
                                val cachedEntry = cacheManager.getByPath(normalizedPath)
                                val cachedSha = cachedEntry?.sha1 ?: ""
                                val isValidSha = cachedSha.isNotEmpty() && cachedSha != fileLowerName && !cachedSha.endsWith(".epub", ignoreCase = true) && !cachedSha.contains("/")
                                if (cachedEntry != null && isValidSha) {
                                    val cachedSize = cachedEntry.size
                                    val cloudSize = file.size ?: 0L
                                    val sizeMatches = cachedSize == 0L || cloudSize == 0L || cachedSize == cloudSize
                                    val modMatches = cachedEntry.lastModified.isEmpty() || file.modified.isNullOrEmpty() || cachedEntry.lastModified == file.modified
                                    if (sizeMatches && modMatches) {
                                        sha1 = cachedEntry.sha1
                                    }
                                }

                                // 2. Проверяем локальные книги по совпадению имени
                                if (sha1 == null) {
                                    val matchedSha1 = localBooksMapByName[fileLowerName]
                                        ?: localBooksMapByName[fileBaseName]
                                        ?: localBooksMapByName[fileBaseNoFb2]
                                        
                                    if (matchedSha1 != null) {
                                        sha1 = matchedSha1
                                        cacheManager.save(sha1, normalizedPath, file.modified ?: "", file.size ?: 0L)
                                        Log.d(TAG, "Matched local book by filename for ${file.name}: $sha1")
                                    }
                                }

                                // 3. Скачиваем временно для вычисления SHA-1 только при отсутствии в кэше и локальной базе
                                if (sha1 == null) {
                                        Log.d(TAG, "Analyzing cloud file SHA-1: ${file.name}")
                                        val tempFile = File(context.cacheDir, "temp_sha_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}_${file.name}")
                                        try {
                                            val success = cloudService.downloadFile(normalizedPath, tempFile)
                                            if (success) {
                                                sha1 = sha1Extractor.extractSha1(tempFile)
                                                if (sha1 != null && sha1.isNotEmpty()) {
                                                    val finalSize = if ((file.size ?: 0L) > 0L) file.size!! else tempFile.length()
                                                    cacheManager.save(sha1, normalizedPath, file.modified ?: "", finalSize)
                                                    Log.d(TAG, "Calculated SHA-1 for ${file.name}: $sha1")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error calculating SHA-1 for ${file.name}", e)
                                        } finally {
                                            try {
                                                if (tempFile.exists()) {
                                                    tempFile.delete()
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Failed to delete temp SHA-1 file: ${tempFile.absolutePath}", e)
                                            }
                                        }
                                    }

                                if (!sha1.isNullOrEmpty()) {
                                    cloudSha1Map[normalizedPath] = sha1
                                    cloudSha1ToPath[sha1] = normalizedPath
                                }
                            } catch (e: Exception) {
                                SyncErrorHandler.logError("SyncOrchestrator Stage 2", e, true)
                            } finally {
                                val processed = processedCount.incrementAndGet()
                                synchronized(progressTracker) {
                                    progressTracker.updateProgress(processed, filteredCloudFiles.size, "Анализ файлов Яндекс Диска: $processed из ${filteredCloudFiles.size}")
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            // Stage 3: Получение локальных ключей и сравнение
            progressTracker.startStage("Сравнение с библиотекой", 0, "Сравнение с библиотекой...")
            if (isCancelled) return
            val repository = BookRepository(bookDao, db.noteDao())
            
            val localBooksList = try { bookDao.getAllBooksSync() } catch (e: Exception) { emptyList() }
            val toDownloadPaths = mutableListOf<String>()
            val cloudKeysSet = mutableSetOf<String>()

            for (cloudFile in filteredCloudFiles) {
                val normalizedPath = YandexDiskManager.normalizePath(cloudFile.path)
                val cloudSha1 = cloudSha1Map[normalizedPath]
                if (!cloudSha1.isNullOrEmpty()) cloudKeysSet.add(cloudSha1)

                val cloudName = cloudFile.name
                val cloudNameLower = cloudName.lowercase(java.util.Locale.ROOT)

                var matchedBy = "NONE"
                var matchedBook: BookEntity? = null

                // 1. SHA-1 match
                if (!cloudSha1.isNullOrEmpty()) {
                    matchedBook = localBooksList.firstOrNull { it.sha1 == cloudSha1 }
                    if (matchedBook != null) {
                        matchedBy = "SHA1"
                    }
                }

                // 2. Exact Filename match (case-insensitive)
                if (matchedBook == null) {
                    matchedBook = localBooksList.firstOrNull { b ->
                        val fn = b.filePath?.let { File(it).name.lowercase(java.util.Locale.ROOT) }
                        fn != null && fn == cloudNameLower
                    }
                    if (matchedBook != null) {
                        matchedBy = "NAME_EXACT"
                    }
                }

                // 3. Compatible FB2/FB3/ZIP match
                if (matchedBook == null) {
                    matchedBook = localBooksList.firstOrNull { b ->
                        val fn = b.filePath?.let { File(it).name } ?: ""
                        fn.isNotEmpty() && isFb2OrFb3ZipCompatible(cloudName, fn)
                    }
                    if (matchedBook != null) {
                        matchedBy = "FB2_ZIP_COMPATIBLE"
                    }
                }

                // 4. Normalized Title match
                if (matchedBook == null) {
                    val cloudTitleNorm = cloudName.substringBeforeLast(".").lowercase(java.util.Locale.ROOT).trim()
                    if (cloudTitleNorm.isNotEmpty()) {
                        matchedBook = localBooksList.firstOrNull { b ->
                            val bTitle = b.title.lowercase(java.util.Locale.ROOT).trim()
                            bTitle.isNotEmpty() && (bTitle == cloudTitleNorm || cloudTitleNorm.contains(bTitle) || bTitle.contains(cloudTitleNorm))
                        }
                        if (matchedBook != null) {
                            matchedBy = "TITLE_MATCH"
                        }
                    }
                }

                val action = if (matchedBook != null) "SKIP" else "DOWNLOAD"
                val ext = cloudName.substringAfterLast(".", "")

                Log.d("SYNC_DIAGNOSTIC", """
                    ================[ SYNC_DIAGNOSTIC ]================
                    CLOUD FILE:
                      name: $cloudName
                      extension: $ext
                      size: ${cloudFile.size ?: 0}
                      sha1: ${cloudSha1 ?: "NONE"}
                    LOCAL MATCH:
                      matchedBy: $matchedBy
                      localPath: ${matchedBook?.filePath ?: "NONE"}
                      localSha1: ${matchedBook?.sha1 ?: "NONE"}
                    ACTION: $action
                    ===================================================
                """.trimIndent())

                if (matchedBook != null) {
                    if (cloudSha1 != matchedBook.sha1) {
                        try {
                            cacheManager.save(matchedBook.sha1, normalizedPath, cloudFile.modified ?: "", cloudFile.size ?: 0L)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating cacheManager for matched book ${cloudName}", e)
                        }
                    }
                } else {
                    toDownloadPaths.add(normalizedPath)
                }
            }

            // Книги для загрузки: локальные книги, SHA-1 или имя которых отсутствует в облаке
            val toUploadBooks = localBooksList.filter { book ->
                val sha = book.sha1
                val fn = book.filePath?.let { File(it).name.lowercase(java.util.Locale.ROOT) } ?: ""
                val fnBase = fn.substringBeforeLast(".")
                !cloudKeysSet.contains(sha) && (fn.isEmpty() || !cloudKeysSet.contains(fn)) && (fnBase.isEmpty() || !cloudKeysSet.contains(fnBase))
            }
            
            toDownloadPaths.take(10).forEach { path ->
                Log.d(TAG, "Book to download: $path")
            }

            progressTracker.updateStats(
                toUpload = toUploadBooks.size,
                toDownload = toDownloadPaths.size,
                uploaded = 0,
                downloaded = 0
            )

            Log.d(TAG, "To upload: ${toUploadBooks.size}, To download: ${toDownloadPaths.size}")

            // Stage 4: Загрузка на диск (Upload)
            val uploadedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val uploadProcessedCount = java.util.concurrent.atomic.AtomicInteger(0)
            if (toUploadBooks.isNotEmpty()) {
                progressTracker.startStage("Загрузка на диск", toUploadBooks.size, "Загрузка книг на Яндекс Диск...")
                val uploadSemaphore = Semaphore(8)
                coroutineScope {
                    val jobs = toUploadBooks.map { book ->
                        async {
                            uploadSemaphore.withPermit {
                                if (isCancelled) return@async
                                try {
                                    val localFile = book.filePath?.let { File(it) }
                                    val originalName = localFile?.name ?: "${book.title}.fb2"
                                    if (localFile != null && localFile.exists()) {
                                        val remotePath = YandexDiskManager.normalizePath("$syncFolder/$originalName")
                                        
                                        YandexSyncState.update {
                                            it.copy(
                                                currentFileName = originalName,
                                                currentFileBytesTransferred = 0L,
                                                currentFileTotalBytes = localFile.length()
                                            )
                                        }

                                        val success = cloudService.uploadFile(localFile, remotePath)
                                        if (success) {
                                            uploadedCount.incrementAndGet()
                                            // Cache the SHA-1 for the uploaded file by querying Yandex Disk's metadata
                                            try {
                                                val tokenVal = YandexDiskManager.getToken(context)
                                                if (tokenVal != null) {
                                                    val response = YandexDiskManager.api.getResource("OAuth $tokenVal", remotePath, limit = 1)
                                                    val modified = response.modified ?: ""
                                                    cacheManager.save(book.sha1 ?: "", remotePath, modified, response.size ?: localFile.length())
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error caching metadata after upload", e)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    SyncErrorHandler.logError("SyncOrchestrator Upload", e, true)
                                } finally {
                                    val processed = uploadProcessedCount.incrementAndGet()
                                    val currentUploaded = uploadedCount.get()
                                    synchronized(progressTracker) {
                                        progressTracker.updateProgress(processed, toUploadBooks.size, "Загрузка на диск: $processed из ${toUploadBooks.size}")
                                        progressTracker.updateStats(
                                            toUpload = toUploadBooks.size,
                                            toDownload = toDownloadPaths.size,
                                            uploaded = currentUploaded,
                                            downloaded = 0
                                        )
                                    }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }

            // Clean file progress status in YandexSyncState
            YandexSyncState.update {
                it.copy(
                    currentFileName = null,
                    currentFileBytesTransferred = 0L,
                    currentFileTotalBytes = 0L
                )
            }

            // Stage 5: Скачивание с диска (Download)
            val downloadedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val downloadProcessedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val booksDirectory = getLocalBooksDirectory()
            Log.d(TAG, "Local download directory: ${booksDirectory.absolutePath}")

            if (toDownloadPaths.isNotEmpty()) {
                progressTracker.startStage("Скачивание с диска", toDownloadPaths.size, "Скачивание новых книг...")
                val downloadSemaphore = Semaphore(8)
                coroutineScope {
                    val jobs = toDownloadPaths.map { remotePath ->
                        async {
                            downloadSemaphore.withPermit {
                                if (isCancelled) return@async

                                val originalName = File(remotePath).name
                                val tempFile = File(context.cacheDir, "temp_down_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}_$originalName")
                                try {
                                    val success = cloudService.downloadFile(remotePath, tempFile)
                                    if (success && tempFile.exists()) {
                                        val isEpub = originalName.lowercase().endsWith(".epub") || EpubIdentifierHelper.isEpub(tempFile)
                                        
                                        var sha1: String? = null
                                        var titleText: String? = null
                                        var authorText: String? = null
                                        var seriesText: String? = null
                                        var seriesIdx: Int? = null
                                        var langText: String? = null
                                        var coverPath: String? = null
                                        var truncatedAnnotation: String? = null
                                        var processSuccess = false

                                        val extLower = originalName.lowercase(java.util.Locale.ROOT)
                                        val isFb3 = extLower.endsWith(".fb3") || extLower.endsWith(".fb3.zip") || com.nightread.app.service.Fb3Parser.isFb3(tempFile)
                                        val isMobi = extLower.endsWith(".mobi") || extLower.endsWith(".azw") || extLower.endsWith(".azw3")

                                        if (isFb3) {
                                            val parsed = com.nightread.app.service.Fb3Parser.parseFb3(tempFile, originalName.substringBeforeLast(".").removeSuffix(".fb3"))
                                            sha1 = computeSha1Stream(tempFile.inputStream())
                                            titleText = parsed.title
                                            authorText = parsed.author
                                            seriesText = parsed.series
                                            seriesIdx = parsed.seriesIndex
                                            langText = parsed.language
                                            truncatedAnnotation = parsed.annotation?.take(1000)
                                            if (parsed.coverBytes != null && parsed.coverBytes.isNotEmpty()) {
                                                coverPath = com.nightread.app.service.NewCoverExtractor.saveCoverBytes(parsed.coverBytes, sha1, context)
                                            }
                                            processSuccess = true
                                        } else if (isEpub) {
                                            val metadata = EpubIdentifierHelper.getEpubMetadata(tempFile)
                                            sha1 = metadata?.identifier?.takeIf { it.isNotBlank() } ?: computeSha1Stream(tempFile.inputStream())
                                            titleText = metadata?.title?.takeIf { it.isNotBlank() } ?: originalName.substringBeforeLast(".")
                                            authorText = metadata?.author ?: "Неизвестен"
                                            seriesText = null
                                            seriesIdx = null
                                            langText = "Unknown"
                                            truncatedAnnotation = metadata?.description?.take(1000)
                                            coverPath = metadata?.coverPath?.let { EpubIdentifierHelper.extractAndSaveEpubCover(tempFile, it, sha1!!, context) }
                                            processSuccess = true
                                        } else if (isMobi) {
                                            val parsed = com.nightread.app.service.MobiParser.parse(tempFile, originalName.substringBeforeLast("."))
                                            sha1 = computeSha1Stream(tempFile.inputStream())
                                            titleText = parsed.title
                                            authorText = parsed.author
                                            seriesText = null
                                            seriesIdx = null
                                            langText = "Unknown"
                                            truncatedAnnotation = null
                                            if (parsed.coverBytes != null && parsed.coverBytes.isNotEmpty()) {
                                                try {
                                                    val coversDir = File(context.filesDir, "covers")
                                                    if (!coversDir.exists()) coversDir.mkdirs()
                                                    val coverFile = File(coversDir, "$sha1.jpg")
                                                    coverFile.writeBytes(parsed.coverBytes)
                                                    coverPath = coverFile.absolutePath
                                                } catch (ce: Exception) {
                                                    Log.e(TAG, "Failed saving cover for mobi", ce)
                                                }
                                            }
                                            processSuccess = true
                                        } else {
                                            var innerName = originalName
                                            val fb2StreamAndDo = { action: (java.io.InputStream) -> Unit ->
                                                if (originalName.lowercase().endsWith(".zip") || originalName.lowercase().endsWith(".fb2.zip")) {
                                                    java.util.zip.ZipInputStream(tempFile.inputStream()).use { zis ->
                                                        var entry = zis.nextEntry
                                                        var found = false
                                                        while (entry != null) {
                                                            if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                                                                innerName = entry.name
                                                                action(zis)
                                                                found = true
                                                                break
                                                            }
                                                            entry = zis.nextEntry
                                                        }
                                                        if (!found) {
                                                            action(tempFile.inputStream())
                                                        }
                                                    }
                                                } else {
                                                    tempFile.inputStream().use { action(it) }
                                                }
                                            }

                                            fb2StreamAndDo { input ->
                                                val meta = com.nightread.app.service.Fb2Parser.parse(input, innerName)
                                                if (meta.title.isNotBlank()) {
                                                    titleText = meta.title
                                                    authorText = meta.author
                                                    seriesText = meta.series
                                                    seriesIdx = meta.seriesIndex
                                                    langText = meta.language
                                                    truncatedAnnotation = meta.annotation?.take(500)
                                                    processSuccess = true
                                                }
                                            }
                                            
                                            sha1 = computeSha1Stream(tempFile.inputStream())
                                            
                                            if (processSuccess) {
                                                fb2StreamAndDo { input ->
                                                    coverPath = com.nightread.app.service.Fb2CoverExtractor.extract(input, sha1!!, context)
                                                }
                                            }
                                        }

                                        val finalSha1 = sha1?.takeIf { it.isNotBlank() } ?: computeSha1Stream(tempFile.inputStream())
                                        val finalTitle = titleText?.takeIf { it.isNotBlank() } ?: originalName.substringBeforeLast(".")
                                        val finalAuthor = authorText?.takeIf { it.isNotBlank() } ?: "Неизвестный автор"

                                        val localFile = File(booksDirectory, originalName)
                                        tempFile.copyTo(localFile, overwrite = true)

                                        try {
                                            val cloudProgress = cloudProgressMap[finalSha1]
                                            val newBook = BookEntity(
                                                sha1 = finalSha1,
                                                title = finalTitle,
                                                author = finalAuthor,
                                                category = "Локальные",
                                                currentProgressChar = cloudProgress?.charOffset ?: 0,
                                                lastReadTime = cloudProgress?.lastReadTime ?: 0L,
                                                filePath = localFile.absolutePath,
                                                series = seriesText,
                                                seriesIndex = seriesIdx,
                                                language = langText ?: "ru",
                                                fileSize = tempFile.length(),
                                                review = null,
                                                isFavorite = false,
                                                coverPath = coverPath,
                                                annotation = truncatedAnnotation,
                                                currentPageIndex = cloudProgress?.page ?: 0,
                                                totalCharacters = cloudProgress?.totalChars ?: 0,
                                                coverGradientStart = getRandomGradientStartColor(),
                                                coverGradientEnd = getRandomGradientEndColor()
                                            )

                                            val inserted = bookDao.insertBookSafely(newBook)

                                            if (inserted) {
                                                Log.d(TAG, "Successfully inserted book: $finalTitle ($finalSha1)")
                                                downloadedCount.incrementAndGet()
                                                // Save directly to cache without extra API call
                                                try {
                                                    cacheManager.save(finalSha1, remotePath, "", tempFile.length())
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error caching after download", e)
                                                }
                                            } else {
                                                Log.e(TAG, "Failed to insert book: $finalTitle ($finalSha1)")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Exception creating or inserting BookEntity for $finalTitle", e)
                                        }
                                    }
                                } catch (e: Exception) {
                                    SyncErrorHandler.logError("SyncOrchestrator Download", e, true)
                                } finally {
                                    try {
                                        if (tempFile.exists()) {
                                            tempFile.delete()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to delete temp downloaded file: ${tempFile.absolutePath}", e)
                                    }
                                    val processed = downloadProcessedCount.incrementAndGet()
                                    val currentDownloaded = downloadedCount.get()
                                    val currentUploaded = uploadedCount.get()
                                    synchronized(progressTracker) {
                                        progressTracker.updateProgress(processed, toDownloadPaths.size, "Скачивание с диска: $processed из ${toDownloadPaths.size}")
                                        progressTracker.updateStats(
                                            toUpload = toUploadBooks.size,
                                            toDownload = toDownloadPaths.size,
                                            uploaded = currentUploaded,
                                            downloaded = currentDownloaded
                                        )
                                    }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }

            if (isCancelled) {
                progressTracker.finishStage("Отмена", "Синхронизация отменена пользователем")
                progressTracker.showFinalNotification(
                    "Синхронизация отменена",
                    "Операция была отменена пользователем.",
                    false
                )
                YandexSyncState.update {
                    it.copy(
                        isRunning = false,
                        stage = YandexSyncState.Stage.IDLE,
                        statusText = "Синхронизация отменена",
                        finished = true,
                        success = false,
                        error = "Синхронизация отменена"
                    )
                }
                return
            }

            // Upload local reading progress to Yandex Disk
            val updatedLocalBooks = try {
                bookDao.getAllBooksSync()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get local books for progress upload", e)
                emptyList()
            }

            if (updatedLocalBooks.isNotEmpty() && !isCancelled) {
                progressTracker.startStage("Отправка прогресса", updatedLocalBooks.size, "Отправка прогресса чтения...")
                val uploadProgressSemaphore = Semaphore(10)
                coroutineScope {
                    val progressUploadJobs = updatedLocalBooks.map { localBook ->
                        async {
                            val fileName = localBook.filePath?.let { File(it).name } ?: "${localBook.title}.fb2"
                            val syncKey = SyncKeyHelper.getSyncKey(fileName, localBook.sha1)
                            if (syncKey.isEmpty()) return@async
                            
                            val cloudProgressName = "$syncKey.json"
                            val cloudProgress = cloudProgressMap[syncKey]

                            val shouldUploadProgress = (cloudProgress == null && (localBook.currentProgressChar > 0 || localBook.lastReadTime > 0 || localBook.currentPageIndex > 0)) || 
                                                       (cloudProgress != null && localBook.lastReadTime > cloudProgress.lastReadTime)

                            if (shouldUploadProgress) {
                                uploadProgressSemaphore.withPermit {
                                    if (isCancelled) return@async
                                    try {
                                        val totalChars = localBook.totalCharacters
                                        val progressPercent = if (totalChars > 0) (localBook.currentProgressChar.toLong() * 100 / totalChars).toInt().coerceIn(0, 100) else 0
                                        
                                        val payload = BookProgressPayload(
                                            sha1 = syncKey,
                                            page = localBook.currentPageIndex,
                                            charOffset = localBook.currentProgressChar,
                                            progress = progressPercent,
                                            lastReadTime = localBook.lastReadTime,
                                            totalChars = totalChars
                                        )
                                        val json = progressAdapter.toJson(payload)
                                        val cleanPath = YandexDiskManager.normalizePath("$progressFolder/$cloudProgressName")
                                        val link = YandexDiskManager.api.getUploadLink(authHeader, cleanPath)
                                        
                                        val requestBody = json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType())
                                        YandexDiskManager.api.uploadFile(link.href, requestBody)
                                        Log.d(TAG, "Uploaded progress to cloud for book: ${localBook.title} (offset: ${localBook.currentProgressChar}, page: ${localBook.currentPageIndex})")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error uploading progress for '${localBook.title}'", e)
                                    }
                                }
                            }
                        }
                    }
                    progressUploadJobs.awaitAll()
                }
            }

            // Finish
            val finishMsg = "Синхронизация завершена! Загружено: ${uploadedCount.get()}, скачано: ${downloadedCount.get()}"
            progressTracker.finishStage("Завершено", finishMsg)
            progressTracker.showFinalNotification("Синхронизация завершена", finishMsg, true)

            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.COMPLETED,
                    statusText = finishMsg,
                    finished = true,
                    success = true,
                    downloadedCount = downloadedCount.get(),
                    uploadedCount = uploadedCount.get()
                )
            }
            try { YandexDiskManager.pushSyncManifestToCloud(context) } catch (e: Exception) { Log.e(TAG, "Failed pushing manifest", e) }
            YandexDiskManager.saveSyncTimestamp(context)

        } catch (e: Exception) {
            Log.e(TAG, "Sync orchestrator exception", e)
            val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
            progressTracker.finishStage("Ошибка", errorMsg)
            progressTracker.showFinalNotification("Синхронизация прервана", errorMsg, false)

            YandexSyncState.update {
                it.copy(
                    isRunning = false,
                    stage = YandexSyncState.Stage.ERROR,
                    statusText = errorMsg,
                    finished = true,
                    success = false,
                    error = errorMsg
                )
            }
        }
    }

    private fun getLocalBooksDirectory(): File {
        val customUriStr = SyncSettingsManager.getDownloadFolderUri(context)
        if (customUriStr != null) {
            try {
                val uri = android.net.Uri.parse(customUriStr)
                val path = SyncSettingsManager.resolveUriToPath(context, uri)
                if (path != null) {
                    val customDir = File(path)
                    if (!customDir.exists()) {
                        customDir.mkdirs()
                    }
                    if (customDir.exists() && customDir.canWrite()) {
                        return customDir
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot write to custom folder: ${e.message}")
            }
        }

        val externalBooksDir = File(Environment.getExternalStorageDirectory(), "Books")
        try {
            if (!externalBooksDir.exists()) {
                externalBooksDir.mkdirs()
            }
            if (externalBooksDir.exists() && externalBooksDir.canWrite()) {
                return externalBooksDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot write to public Books folder: ${e.message}")
        }

        val extFilesDir = context.getExternalFilesDir("Books")
        if (extFilesDir != null && (extFilesDir.exists() || extFilesDir.mkdirs())) {
            return extFilesDir
        }

        val internalBooksDir = File(context.filesDir, "books")
        if (!internalBooksDir.exists()) {
            internalBooksDir.mkdirs()
        }
        return internalBooksDir
    }

    private fun isFb2OrFb3ZipCompatible(cloudName: String, localName: String): Boolean {
        val cLower = cloudName.lowercase(java.util.Locale.ROOT)
        val lLower = localName.lowercase(java.util.Locale.ROOT)
        if (cLower == lLower) return true

        val cFb2Base = when {
            cLower.endsWith(".fb2.zip") -> cLower.removeSuffix(".fb2.zip")
            cLower.endsWith(".fb2") -> cLower.removeSuffix(".fb2")
            else -> null
        }
        val lFb2Base = when {
            lLower.endsWith(".fb2.zip") -> lLower.removeSuffix(".fb2.zip")
            lLower.endsWith(".fb2") -> lLower.removeSuffix(".fb2")
            else -> null
        }
        if (cFb2Base != null && lFb2Base != null && cFb2Base == lFb2Base) {
            return true
        }

        val cFb3Base = when {
            cLower.endsWith(".fb3.zip") -> cLower.removeSuffix(".fb3.zip")
            cLower.endsWith(".fb3") -> cLower.removeSuffix(".fb3")
            else -> null
        }
        val lFb3Base = when {
            lLower.endsWith(".fb3.zip") -> lLower.removeSuffix(".fb3.zip")
            lLower.endsWith(".fb3") -> lLower.removeSuffix(".fb3")
            else -> null
        }
        if (cFb3Base != null && lFb3Base != null && cFb3Base == lFb3Base) {
            return true
        }

        return false
    }

    private fun getRandomGradientStartColor(): String {
        val colors = listOf("#E0A96D", "#D4A373", "#CCA43B", "#C5A880", "#B5838D", "#E5989B")
        return colors.random()
    }

    private fun getRandomGradientEndColor(): String {
        val colors = listOf("#201A15", "#432818", "#3D348B", "#6F4E37", "#582F0E", "#6A4C93")
        return colors.random()
    }
}
