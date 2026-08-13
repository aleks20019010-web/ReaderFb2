package com.nightread.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import com.nightread.app.service.NewCoverExtractor
import com.nightread.app.service.Fb2Parser
import com.nightread.app.data.Sha1Helper.computeSha1Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Отчет анализа синхронизации для подтверждения пользователем.
 */
data class SyncReport(
    val booksOnDisk: Int,               // Всего книг в папке на Яндекс Диске
    val booksLocal: Int,                // Всего книг в локальной БД
    val duplicatesCount: Int,           // Книги, которые есть и на диске, и локально (совпадают по SHA-1)
    val toDownload: List<CloudFileEntity>,  // Книги, которых нет локально (будут скачаны)
    val toUpload: List<BookEntity>,          // Книги, которых нет в облаке (будут загружены)
    val stats: SyncStats                // Ссылка на исходную статистику
)

/**
 * Менеджер синхронизации с Яндекс Диском.
 * Отвечает за:
 * 1. Получение статистики синхронизации (toDownload, toUpload) с дедупликацией по SHA-1 и имени.
 * 2. Пофайловое кэширование хэшей (SHA-1) файлов Яндекс Диска в локальной БД (CloudFileEntity / CloudFileDao).
 * 3. Загрузку новых книг на диск с сохранением ОРИГИНАЛЬНОГО имени файла (пропуск при совпадении имени или SHA-1).
 * 4. Скачивание новых книг с сохранением оригинальных названий файлов в папку Books.
 * 5. Синхронизацию прогресса чтения.
 */
class YandexSyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val repository = BookRepository(database.bookDao(), database.noteDao())
    private val cloudFileDao = database.cloudFileDao()
    private val cloudFileCache = CloudFileCache(cloudFileDao)

    companion object {
        private const val TAG = "YandexSyncManager"
    }

    /**
     * Проверяет наличие подключения к интернету.
     */
    fun hasInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Возвращает локальную папку для сохранения книг.
     * Приоритет отдается выбранной пользователем папке через SAF.
     * Если она недоступна, используется общедоступная папка '/storage/emulated/0/Books'.
     * Если запись невозможна или ограничена ОС, выполняется откат на безопасную папку приложения.
     */
    fun getLocalBooksDirectory(): File {
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
                Log.w(TAG, "Cannot write directly to custom folder: ${e.message}. Using default fallback.")
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
            Log.w(TAG, "Cannot write directly to public Books folder: ${e.message}. Using safe fallback.")
        }

        // Откат 1: Внешняя директория приложения
        val extFilesDir = context.getExternalFilesDir("Books")
        if (extFilesDir != null && (extFilesDir.exists() || extFilesDir.mkdirs())) {
            return extFilesDir
        }

        // Откат 2: Внутренний кэш приложения
        val internalBooksDir = File(context.filesDir, "books")
        if (!internalBooksDir.exists()) {
            internalBooksDir.mkdirs()
        }
        return internalBooksDir
    }

    /**
     * Ожидание подключения к интернету, если связь пропала во время работы.
     */
    private suspend fun ensureInternet(
        completed: Int,
        total: Int,
        stage: YandexSyncState.Stage,
        downloadedCount: Int,
        uploadedCount: Int,
        onProgress: (status: String, completed: Int, total: Int, stage: YandexSyncState.Stage, downloaded: Int, uploaded: Int, remainingSeconds: Long) -> Unit
    ) {
        while (!hasInternetConnection()) {
            currentCoroutineContext().ensureActive()
            onProgress(
                "Связь прервана. Ожидание подключения к сети...",
                completed,
                total,
                stage,
                downloadedCount,
                uploadedCount,
                -1L
            )
            delay(3000)
        }
    }

    /**
     * Расчет статистики синхронизации. Сканирует файлы на Яндекс Диске,
     * сопоставляет их с локальной БД, считает SHA-1 (с использованием кэша CloudFileDao)
     * и определяет, какие книги скачать, а какие — загрузить.
     */
    suspend fun calculateSyncStats(onProgress: (status: String) -> Unit): SyncStats? = withContext(Dispatchers.IO) {
        val originalFolder = YandexDiskManager.getSyncFolder(context)
        val token = YandexDiskManager.getToken(context) ?: return@withContext null
        val authHeader = "OAuth $token"

        try {
            onProgress("Поиск папки синхронизации на Яндекс Диске...")
            val syncFolder = YandexDiskManager.resolveCaseInsensitivePath(context, originalFolder)
            if (syncFolder != originalFolder) {
                YandexDiskManager.setSyncFolder(context, syncFolder)
            }

            Log.d(TAG, "calculateSyncStats: Папка синхронизации: $syncFolder")
            onProgress("Проверка директорий на диске...")
            val progressFolder = "$syncFolder/Progress"
            val pathsToCreate = listOf(syncFolder, progressFolder)
            for (path in pathsToCreate) {
                try {
                    YandexDiskManager.api.createDirectory(authHeader, path)
                } catch (e: Exception) {
                    Log.d(TAG, "Папка уже существует: $path")
                }
            }

            onProgress("Получение списка файлов из облака...")
            val cloudItems = YandexDiskManager.getAllFilesFromFolder(context, authHeader, syncFolder)
            
            // Фильтруем только поддерживаемые форматы книг
            val cloudBooks = cloudItems.filter {
                val name = it.name.lowercase()
                val isSupported = com.nightread.app.data.BookFormatHelper.isSupported(name) || name.endsWith(".zip")
                if (!isSupported) {
                    Log.d(TAG, "Файл не поддерживается: ${it.name}")
                }
                isSupported
            }
            val booksOnDisk = cloudBooks.size
            Log.d(TAG, "Найдено книг на диске: $booksOnDisk")

            onProgress("Анализ кэша SHA-1 файлов...")
            val cachedSha1s = cloudFileCache.getAllSha1s()
            Log.d(TAG, "Количество SHA-1 в кэше: ${cachedSha1s.size}")

            val updatedCloudBooks = mutableListOf<CloudFileEntity>()
            val needsSha1 = mutableListOf<ResourceItem>()

            // Прогружаем весь кэш из базы данных для быстрого сопоставления без лишних запросов в цикле
            val allCachedEntities = cloudFileDao.getAll()
            val cachedMap = allCachedEntities.associateBy { it.path }

            // Загружаем локальные книги для сопоставления по имени и названию
            val localBooksList = try { database.bookDao().getAllBooksSync() } catch (e: Exception) { emptyList() }
            val localBooksMapByName = HashMap<String, String>()
            for (b in localBooksList) {
                val sha = b.sha1
                val fn = b.filePath?.let { File(it).name.lowercase(java.util.Locale.ROOT) } ?: ""
                if (fn.isNotEmpty()) {
                    if (!sha.isNullOrEmpty()) localBooksMapByName[fn] = sha
                    val fnNoExt = fn.substringBeforeLast(".")
                    if (!sha.isNullOrEmpty()) localBooksMapByName[fnNoExt] = sha
                    if (fnNoExt.endsWith(".fb2") && !sha.isNullOrEmpty()) {
                        localBooksMapByName[fnNoExt.substringBeforeLast(".fb2")] = sha
                    }
                }
                val titleNorm = b.title.lowercase(java.util.Locale.ROOT).trim()
                if (titleNorm.isNotEmpty() && !sha.isNullOrEmpty()) {
                    localBooksMapByName[titleNorm] = sha
                }
            }

            // Проверка кэша и локальных совпадений в БД
            for (cloudBook in cloudBooks) {
                val cleanPath = YandexDiskManager.normalizePath(cloudBook.path ?: "$syncFolder/${cloudBook.name}")
                val cloudNameLower = cloudBook.name.lowercase(java.util.Locale.ROOT)
                val cloudBaseName = cloudNameLower.substringBeforeLast(".")
                val cloudBaseNoFb2 = if (cloudBaseName.endsWith(".fb2")) cloudBaseName.substringBeforeLast(".fb2") else cloudBaseName

                val cached = cachedMap[cleanPath]
                val cachedSha = cached?.sha1 ?: ""
                val isValidSha = cachedSha.isNotEmpty() && cachedSha != cloudNameLower && !cachedSha.endsWith(".epub", ignoreCase = true) && !cachedSha.contains("/")
                val cachedSize = cached?.size ?: 0L
                val cloudSize = cloudBook.size ?: 0L
                val sizeMatches = cachedSize == 0L || cloudSize == 0L || cachedSize == cloudSize
                val modMatches = cached?.lastModified.isNullOrEmpty() || cloudBook.modified.isNullOrEmpty() || cached?.lastModified == cloudBook.modified

                if (cached != null && isValidSha && sizeMatches && modMatches) {
                    updatedCloudBooks.add(cached)
                } else {
                    val matchedSha1 = localBooksMapByName[cloudNameLower]
                        ?: localBooksMapByName[cloudBaseName]
                        ?: localBooksMapByName[cloudBaseNoFb2]

                    if (matchedSha1 != null) {
                        val entity = CloudFileEntity(
                            path = cleanPath,
                            sha1 = matchedSha1,
                            size = cloudSize,
                            lastModified = cloudBook.modified ?: ""
                        )
                        updatedCloudBooks.add(entity)
                        cloudFileDao.insert(entity)
                        Log.d(TAG, "Matched cloud book for ${cloudBook.name}: $matchedSha1")
                    } else {
                        needsSha1.add(cloudBook)
                    }
                }
            }

            Log.d(TAG, "Хитов в кэше: ${updatedCloudBooks.size}, требуется рассчитать SHA-1: ${needsSha1.size}")

            // Если есть новые или изменившиеся файлы на диске — вычисляем их хэши в фоне
            if (needsSha1.isNotEmpty()) {
                var processedCount = 0
                val totalToProcess = needsSha1.size
                val startTime = System.currentTimeMillis()
                
                // Concurrency limit of 15 using Semaphore to prevent API throttling
                val semaphore = Semaphore(15)
                
                coroutineScope {
                    val deferreds = needsSha1.map { item ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val cleanItemPath = YandexDiskManager.normalizePath(item.path ?: "$syncFolder/${item.name}")
                                    val linkResponse = YandexDiskManager.api.getDownloadLink(authHeader, cleanItemPath)
                                    val responseBody = YandexDiskManager.api.downloadFile(linkResponse.href)
                                    
                                    // Use highly unique temp files for safety in parallel downloads
                                    val tempFile = File(context.cacheDir, "temp_stat_${System.nanoTime()}_${item.name}")
                                    try {
                                        synchronized(this@YandexSyncManager) {
                                            onProgress("Анализ файлов Яндекс Диска: ${processedCount + 1} из $totalToProcess")
                                        }
                                        tempFile.outputStream().use { output ->
                                            responseBody.byteStream().use { input ->
                                                input.copyTo(output)
                                            }
                                        }
                                        synchronized(this@YandexSyncManager) {
                                            onProgress("Анализ файлов Яндекс Диска: ${processedCount + 1} из $totalToProcess")
                                        }
                                        val sha1 = if (EpubIdentifierHelper.isEpub(tempFile)) {
                                            EpubIdentifierHelper.getEpubMetadata(tempFile)?.identifier
                                        } else {
                                            Sha1Helper.computeSha1FromContent(tempFile)
                                        }
                                        if (sha1 != null && sha1.isNotEmpty()) {
                                            cloudFileCache.save(sha1, cleanItemPath, item.modified ?: "", item.size ?: 0L)
                                            
                                            val entity = CloudFileEntity(
                                                path = cleanItemPath,
                                                sha1 = sha1,
                                                size = item.size ?: 0L,
                                                lastModified = item.modified ?: ""
                                            )
                                            synchronized(updatedCloudBooks) {
                                                updatedCloudBooks.add(entity)
                                            }
                                        } else {
                                            Log.e(TAG, "Empty or missing FB2 in cloud file: ${item.name}")
                                        }
                                    } finally {
                                        if (tempFile.exists()) tempFile.delete()
                                    }
                                } catch (e: retrofit2.HttpException) {
                                    if (e.code() == 401) {
                                        YandexDiskManager.clearToken(context)
                                        Log.e(TAG, "Token expired during SHA1 calculation. Clearing token.")
                                    }
                                    Log.e(TAG, "HTTP Error calculating SHA1 for cloud file: ${item.name}", e)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error calculating SHA1 for cloud file: ${item.name}", e)
                                } finally {
                                    synchronized(this@YandexSyncManager) {
                                        processedCount++
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val avgTime = if (processedCount > 0) elapsed / processedCount else 1L
                                        val remaining = totalToProcess - processedCount
                                        val remainingSecs = (remaining * avgTime) / 1000
                                        val timeStr = if (remainingSecs > 60) "${remainingSecs / 60} мин ${remainingSecs % 60} сек" else "$remainingSecs сек"
                                        onProgress("Анализ файлов Яндекс Диска: $processedCount из $totalToProcess (осталось ~ $timeStr)")
                                    }
                                }
                            }
                        }
                    }
                    deferreds.awaitAll()
                }
            }

            onProgress("Очистка временных файлов...")
            cleanupTempFiles()

            // ==========================================
            // ОБНАРУЖЕНИЕ ДУБЛИКАТОВ И ЗАПРОС У ПОЛЬЗОВАТЕЛЯ
            // ==========================================
            val duplicateGroups = mutableListOf<DuplicateGroup>()
            val groupsBySha1 = updatedCloudBooks.groupBy { it.sha1 }
            for ((sha1, entities) in groupsBySha1) {
                if (entities.size > 1) {
                    val localBook = database.bookDao().getBookBySha1(sha1)
                    val bookTitle = localBook?.title ?: File(entities.first().path).name.substringBeforeLast(".")
                    
                    val scoredEntities = entities.map { entity ->
                        val fileName = File(entity.path).name
                        val nameWithoutExt = fileName.substringBeforeLast(".")
                        var score = 0
                        if (localBook != null && nameWithoutExt.equals(localBook.title, ignoreCase = true)) {
                            score += 10
                        }
                        if (fileName.contains(sha1, ignoreCase = true)) {
                            score += 5
                        }
                        entity to score
                    }
                    val sorted = scoredEntities.sortedWith(compareByDescending<Pair<CloudFileEntity, Int>> { it.second }.thenBy { it.first.path.length })
                    val mainEntity = sorted.first().first
                    
                    val duplicateFiles = entities.map { entity ->
                        DuplicateFile(
                            filePath = entity.path,
                            size = entity.size,
                            isRecommended = (entity.path == mainEntity.path),
                            isSelected = !(entity.path == mainEntity.path)
                        )
                    }
                    duplicateGroups.add(DuplicateGroup(
                        sha1 = sha1,
                        title = bookTitle,
                        author = localBook?.author ?: "Неизвестен",
                        files = duplicateFiles
                    ))
                }
            }

            if (duplicateGroups.isNotEmpty()) {
                Log.d(TAG, "Найдены дубликаты на Яндекс Диске. Запрос разрешения у пользователя...")
                
                val deferred = kotlinx.coroutines.CompletableDeferred<List<String>>()
                YandexSyncState.duplicateResolution = deferred
                
                YandexSyncState.update {
                    it.copy(
                        duplicatesToResolve = duplicateGroups,
                        statusText = "Найдены дубликаты на диске. Ожидание выбора..."
                    )
                }
                
                // Ждём, пока пользователь выберет файлы на удаление
                val pathsToDelete = deferred.await()
                YandexSyncState.duplicateResolution = null
                
                YandexSyncState.update {
                    it.copy(duplicatesToResolve = null)
                }

                if (pathsToDelete.isNotEmpty()) {
                    onProgress("Удаление дубликатов: ${pathsToDelete.size} файлов")
                    var deletedCount = 0
                    for (path in pathsToDelete) {
                        onProgress("Удаление дубликата: ${File(path).name}...")
                        val success = YandexDiskManager.deleteFile(context, path)
                        if (success) {
                            deletedCount++
                            // Обновляем кэш
                            cloudFileDao.deleteByPath(path)
                            YandexSyncState.update { it.copy(deletedDuplicatesCount = deletedCount) }
                        }
                    }
                    Log.d(TAG, "Успешно удалено дубликатов: $deletedCount")
                    withContext(Dispatchers.Main) {
                        com.nightread.app.ui.CustomToast.show(context, "Удалено дубликатов: $deletedCount")
                    }
                    
                    // Обновляем список файлов в облаке, чтобы исключить удаленные
                    val deletedPathsSet = pathsToDelete.toSet()
                    updatedCloudBooks.removeAll { deletedPathsSet.contains(it.path) }
                }
            }

            onProgress("Сравнение с библиотекой...")

            // Получаем список всех локальных книг из базы данных
            val localBooks = database.bookDao().getAllBooks().first()
            val cloudKeysSet = mutableSetOf<String>()
            val toDownload = mutableListOf<CloudFileEntity>()

            for (cloudEntity in updatedCloudBooks) {
                val cloudSha1 = cloudEntity.sha1
                if (cloudSha1.isNotEmpty()) cloudKeysSet.add(cloudSha1)

                val cloudFileName = File(cloudEntity.path).name
                val cloudFileNameLower = cloudFileName.lowercase(java.util.Locale.ROOT)

                var matchedBy = "NONE"
                var matchedBook: BookEntity? = null

                // 1. SHA-1 match
                if (cloudSha1.isNotEmpty()) {
                    matchedBook = localBooks.firstOrNull { it.sha1 == cloudSha1 }
                    if (matchedBook != null) {
                        matchedBy = "SHA1"
                    }
                }

                // 2. Exact Filename match (case-insensitive)
                if (matchedBook == null) {
                    matchedBook = localBooks.firstOrNull { b ->
                        val fn = b.filePath?.let { File(it).name.lowercase(java.util.Locale.ROOT) }
                        fn != null && fn == cloudFileNameLower
                    }
                    if (matchedBook != null) {
                        matchedBy = "NAME_EXACT"
                    }
                }

                // 3. Compatible FB2/FB3/ZIP match
                if (matchedBook == null) {
                    matchedBook = localBooks.firstOrNull { b ->
                        val fn = b.filePath?.let { File(it).name } ?: ""
                        fn.isNotEmpty() && isFb2OrFb3ZipCompatible(cloudFileName, fn)
                    }
                    if (matchedBook != null) {
                        matchedBy = "FB2_ZIP_COMPATIBLE"
                    }
                }

                // 4. Normalized Title match
                if (matchedBook == null) {
                    val cloudTitleNorm = cloudFileName.substringBeforeLast(".").lowercase(java.util.Locale.ROOT).trim()
                    if (cloudTitleNorm.isNotEmpty()) {
                        matchedBook = localBooks.firstOrNull { b ->
                            val bTitle = b.title.lowercase(java.util.Locale.ROOT).trim()
                            bTitle.isNotEmpty() && (bTitle == cloudTitleNorm || cloudTitleNorm.contains(bTitle) || bTitle.contains(cloudTitleNorm))
                        }
                        if (matchedBook != null) {
                            matchedBy = "TITLE_MATCH"
                        }
                    }
                }

                val action = if (matchedBook != null) "SKIP" else "DOWNLOAD"
                val ext = cloudFileName.substringAfterLast(".", "")

                Log.d("SYNC_DIAGNOSTIC", """
                    ================[ SYNC_DIAGNOSTIC ]================
                    CLOUD FILE:
                      name: $cloudFileName
                      extension: $ext
                      size: ${cloudEntity.size}
                      sha1: ${if (cloudSha1.isNotEmpty()) cloudSha1 else "NONE"}
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
                            cloudFileCache.save(matchedBook.sha1, cloudEntity.path, cloudEntity.lastModified, cloudEntity.size)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating cloudFileCache for matched book ${cloudFileName}", e)
                        }
                    }
                } else {
                    toDownload.add(cloudEntity)
                }
            }

            // Книги для загрузки: локальные книги, SHA-1 или имя которых отсутствует в облаке
            val toUpload = localBooks.filter { book ->
                val sha = book.sha1
                val fn = book.filePath?.let { File(it).name.lowercase(java.util.Locale.ROOT) } ?: ""
                val fnBase = fn.substringBeforeLast(".")
                !cloudKeysSet.contains(sha) && (fn.isEmpty() || !cloudKeysSet.contains(fn)) && (fnBase.isEmpty() || !cloudKeysSet.contains(fnBase))
            }
            
            Log.d(TAG, "Книг для скачивания (разница cloud - local): ${toDownload.size}")
            Log.d(TAG, "Книг для загрузки (разница local - cloud): ${toUpload.size}")

            val duplicates = updatedCloudBooks.size - toDownload.size

            onProgress("К загрузке: ${toUpload.size} книг, к скачиванию: ${toDownload.size} книг")

            val cloudProgressItems = YandexDiskManager.getAllFilesFromFolder(context, authHeader, "$syncFolder/Progress")

            return@withContext SyncStats(
                booksOnDisk = booksOnDisk,
                booksLocal = localBooks.size,
                toDownload = toDownload,
                toUpload = toUpload,
                duplicates = duplicates,
                cloudProgressItems = cloudProgressItems
            )
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                YandexDiskManager.clearToken(context)
                Log.e(TAG, "Token expired during calculateSyncStats. Clearing token.")
            }
            Log.e(TAG, "HTTP error in calculateSyncStats", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error in calculateSyncStats", e)
            return@withContext null
        } finally {
            cleanupTempFiles()
        }
    }

    /**
     * Анализирует файлы на диске и локально и готовит сводный отчет для пользователя.
     */
    suspend fun analyzeAndReport(onProgress: (status: String) -> Unit): SyncReport? = withContext(Dispatchers.IO) {
        val stats = calculateSyncStats(onProgress) ?: return@withContext null
        
        val localSha1Set = database.bookDao().getAllSha1s().filter { it.isNotEmpty() }.toSet()
        val duplicates = maxOf(0, localSha1Set.size - stats.toUpload.size)
        
        return@withContext SyncReport(
            booksOnDisk = stats.booksOnDisk,
            booksLocal = stats.booksLocal,
            duplicatesCount = duplicates,
            toDownload = stats.toDownload,
            toUpload = stats.toUpload,
            stats = stats
        )
    }

    /**
     * Выполняет синхронизацию (скачивание, загрузка, синхронизация прогресса).
     */
    suspend fun performSync(
        stats: SyncStats,
        onProgress: (status: String, completed: Int, total: Int, stage: YandexSyncState.Stage, downloaded: Int, uploaded: Int, remainingSeconds: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val syncFolder = YandexDiskManager.getSyncFolder(context)
        val token = YandexDiskManager.getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"
        
        val progressAdapter = YandexDiskManager.moshi.adapter(BookProgressPayload::class.java)

        val totalDownloads = stats.toDownload.size
        val totalUploads = stats.toUpload.size
        val totalTasks = totalDownloads + totalUploads + 1
        var completedTasks = 0

        var downloadedCount = 0
        var uploadedCount = 0

        try {
            // ==========================================
            // 1. СИНХРОНИЗАЦИЯ ПРОГРЕССА ЧТЕНИЯ (ДО скачивания/загрузки книг)
            // ==========================================
            currentCoroutineContext().ensureActive()
            ensureInternet(completedTasks, totalTasks, YandexSyncState.Stage.PROGRESS_SYNC, downloadedCount, uploadedCount, onProgress)

            onProgress(
                "Синхронизация прогресса чтения...",
                completedTasks,
                totalTasks,
                YandexSyncState.Stage.PROGRESS_SYNC,
                downloadedCount,
                uploadedCount,
                -1L
            )

            // Map to store downloaded cloud progress payloads so they can also be applied to newly downloaded books
            val cloudProgressMap = mutableMapOf<String, BookProgressPayload>()

            // Скачиваем прогресс с облака
            for (progressItem in stats.cloudProgressItems) {
                currentCoroutineContext().ensureActive()
                if (progressItem.name.endsWith(".json")) {
                    try {
                        val cleanPath = YandexDiskManager.normalizePath(progressItem.path ?: "$syncFolder/Progress/${progressItem.name}")
                        val linkResponse = YandexDiskManager.api.getDownloadLink(authHeader, cleanPath)
                        val body = YandexDiskManager.api.downloadFile(linkResponse.href)
                        val jsonStr = body.string()
                        
                        // Сохраняем локальную копию JSON файла в папку ReaderFb2
                        try {
                            val localSyncDir = AppDatabase.getReaderFb2Dir(context)
                            File(localSyncDir, progressItem.name).writeText(jsonStr, Charsets.UTF_8)
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка сохранения файла прогресса в ReaderFb2", e)
                        }

                        val cloudProgress = progressAdapter.fromJson(jsonStr)
                        if (cloudProgress != null) {
                            cloudProgressMap[cloudProgress.sha1] = cloudProgress
                            val allBooksList = repository.allBooks.first()
                            val localBook = allBooksList.firstOrNull { b ->
                                val fn = b.filePath?.let { File(it).name } ?: "${b.title}.fb2"
                                SyncKeyHelper.getSyncKey(fn, b.sha1) == cloudProgress.sha1
                            } ?: repository.getBookBySha1(cloudProgress.sha1)
                            
                            if (localBook != null) {
                                val isCloudZero = cloudProgress.page == 0 && cloudProgress.charOffset == 0
                                val isLocalNonZero = localBook.currentPageIndex > 0 || localBook.currentProgressChar > 0
                                if (cloudProgress.lastReadTime > localBook.lastReadTime && !(isCloudZero && isLocalNonZero)) {
                                    database.bookDao().updateProgressAndPage(
                                        sha1 = localBook.sha1,
                                        charOffset = cloudProgress.charOffset,
                                        pageIndex = cloudProgress.page,
                                        totalChars = cloudProgress.totalChars,
                                        timestamp = cloudProgress.lastReadTime
                                    )
                                    context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putInt("book_page_${localBook.sha1}", cloudProgress.page)
                                        .putInt("book_char_offset_${localBook.sha1}", cloudProgress.charOffset)
                                        .apply()
                                    Log.d(TAG, "Обновлен локальный прогресс для книги: ${localBook.title} (смещение: ${cloudProgress.charOffset}, страница: ${cloudProgress.page})")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка синхронизации прогресса: ${progressItem.name}", e)
                    }
                }
            }

            // Загружаем наш локальный прогресс (для всех локальных книг)
            val updatedLocalBooks = repository.allBooks.first()
            for (localBook in updatedLocalBooks) {
                currentCoroutineContext().ensureActive()
                val fileName = localBook.filePath?.let { File(it).name } ?: "${localBook.title}.fb2"
                val syncKey = SyncKeyHelper.getSyncKey(fileName, localBook.sha1)
                if (syncKey.isEmpty()) continue

                val cloudProgressName = "$syncKey.json"
                val cloudProgress = cloudProgressMap[syncKey]

                // shouldUpload: if there's no cloud progress and we actually have some progress locally, OR if local is newer than cloud
                val shouldUploadProgress = (cloudProgress == null && (localBook.currentProgressChar > 0 || localBook.lastReadTime > 0 || localBook.currentPageIndex > 0)) || 
                                           (cloudProgress != null && localBook.lastReadTime > cloudProgress.lastReadTime)

                if (shouldUploadProgress) {
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
                        try {
                            val localSyncDir = AppDatabase.getReaderFb2Dir(context)
                            File(localSyncDir, cloudProgressName).writeText(json, Charsets.UTF_8)
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка сохранения загружаемого прогресса в ReaderFb2", e)
                        }
                        val cleanPath = YandexDiskManager.normalizePath("$syncFolder/Progress/$cloudProgressName")
                        val link = YandexDiskManager.api.getUploadLink(authHeader, cleanPath)
                        YandexDiskManager.api.uploadFile(
                            link.href,
                            json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType())
                        )
                        Log.d(TAG, "Загружен прогресс в облако для книги: ${localBook.title} (смещение: ${localBook.currentProgressChar}, страница: ${localBook.currentPageIndex})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка отправки прогресса для '${localBook.title}'", e)
                    }
                }
            }

            completedTasks++

            // ==========================================
            // 2. СКАЧИВАНИЕ КНИГ (DOWNLOAD)
            // ==========================================
            val downloadStartTime = System.currentTimeMillis()
            val booksDirectory = getLocalBooksDirectory()
            Log.d(TAG, "Локальная папка для скачивания книг: ${booksDirectory.absolutePath}")

            for ((index, cloudItem) in stats.toDownload.withIndex()) {
                currentCoroutineContext().ensureActive()
                ensureInternet(completedTasks, totalTasks, YandexSyncState.Stage.DOWNLOADING, downloadedCount, uploadedCount, onProgress)

                val elapsed = System.currentTimeMillis() - downloadStartTime
                val avgTimePerFile = if (index > 0) elapsed / index else 0L
                val remainingFiles = totalDownloads - index
                val remainingSeconds = if (avgTimePerFile > 0) (remainingFiles * avgTimePerFile) / 1000 else -1L

                val originalName = File(cloudItem.path).name
                onProgress(
                    "Скачивание с диска: ${index + 1} из $totalDownloads",
                    completedTasks,
                    totalTasks,
                    YandexSyncState.Stage.DOWNLOADING,
                    downloadedCount,
                    uploadedCount,
                    remainingSeconds
                )

                try {
                    val linkResponse = YandexDiskManager.api.getDownloadLink(authHeader, cloudItem.path)
                    val responseBody = YandexDiskManager.api.downloadFile(linkResponse.href)
                    
                    val tempFile = File(context.cacheDir, "temp_down_${originalName}")
                    try {
                        val totalBytes = cloudItem.size
                        YandexSyncState.update {
                            it.copy(
                                currentFileName = originalName,
                                currentFileBytesTransferred = 0L,
                                currentFileTotalBytes = totalBytes
                            )
                        }

                        tempFile.outputStream().use { output ->
                            responseBody.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var bytesTransferred = 0L
                                var lastUpdateBytes = 0L
                                val updateThreshold = 50 * 1024 // 50 KB
                                
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    bytesTransferred += bytesRead
                                    
                                    if (bytesTransferred - lastUpdateBytes >= updateThreshold || bytesTransferred == totalBytes) {
                                        lastUpdateBytes = bytesTransferred
                                        YandexSyncState.update {
                                            it.copy(
                                                currentFileBytesTransferred = bytesTransferred
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
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
                                coverPath = com.nightread.app.service.NewCoverExtractor.saveCoverBytes(parsed.coverBytes, sha1!!, context)
                            }
                            processSuccess = true
                        } else if (isEpub) {
                            val metadata = EpubIdentifierHelper.getEpubMetadata(tempFile)
                            sha1 = metadata?.identifier?.takeIf { it.isNotBlank() } ?: computeSha1Stream(tempFile.inputStream())
                            titleText = metadata?.title?.takeIf { it.isNotBlank() }
                            authorText = metadata?.author?.takeIf { it.isNotBlank() }
                            seriesText = null
                            seriesIdx = null
                            langText = "Unknown"
                            truncatedAnnotation = metadata?.description?.take(1000)
                            coverPath = metadata?.coverPath?.let { EpubIdentifierHelper.extractAndSaveEpubCover(tempFile, it, sha1!!, context) }
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
                                val meta = Fb2Parser.parse(input, innerName)
                                if (meta.title.isNotBlank()) {
                                    titleText = meta.title
                                    authorText = meta.author
                                    seriesText = meta.series
                                    seriesIdx = meta.seriesIndex
                                    langText = meta.language
                                    truncatedAnnotation = meta.annotation?.take(1000)
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
                        
                        val progressPayload = cloudProgressMap[finalSha1]

                        val newBook = BookEntity(
                            sha1 = finalSha1,
                            title = finalTitle,
                            author = finalAuthor,
                                category = "Локальные",
                                coverGradientStart = getRandomGradientStartColor(),
                                coverGradientEnd = getRandomGradientEndColor(),
                                filePath = localFile.absolutePath,
                                series = seriesText,
                                seriesIndex = seriesIdx,
                                language = langText ?: "ru",
                                annotation = truncatedAnnotation,
                                fileSize = tempFile.length(),
                                coverPath = coverPath,
                                currentPageIndex = progressPayload?.page ?: 0,
                                currentProgressChar = progressPayload?.charOffset ?: 0,
                                totalCharacters = progressPayload?.totalChars ?: 0,
                                lastReadTime = progressPayload?.lastReadTime ?: 0L
                            )
                            if (repository.insertBookSafely(newBook)) {
                                downloadedCount++
                                Log.d(TAG, "Успешно скачана и импортирована книга: $originalName (SHA-1: $finalSha1)")
                                try {
                                    cloudFileCache.save(finalSha1, cloudItem.path, cloudItem.lastModified, cloudItem.size)
                                    Log.d(TAG, "Кэш SHA-1 обновлен для скачанной книги: $originalName")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Ошибка сохранения SHA-1 в кэш для скачанной книги: $originalName", e)
                                }
                            } else {
                                Log.e(TAG, "Ошибка вставки книги '$originalName' в базу")
                            }
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                        YandexSyncState.update {
                            it.copy(
                                currentFileName = null,
                                currentFileBytesTransferred = 0L,
                                currentFileTotalBytes = 0L
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка скачивания книги '$originalName'", e)
                }
                completedTasks++
            }

            // ==========================================
            // 3. ЗАГРУЗКА КНИГ (UPLOAD)
            // ==========================================
            val uploadStartTime = System.currentTimeMillis()
            for ((index, localBook) in stats.toUpload.withIndex()) {
                currentCoroutineContext().ensureActive()
                ensureInternet(completedTasks, totalTasks, YandexSyncState.Stage.UPLOADING, downloadedCount, uploadedCount, onProgress)

                val elapsed = System.currentTimeMillis() - uploadStartTime
                val avgTimePerFile = if (index > 0) elapsed / index else 0L
                val remainingFiles = totalUploads - index
                val remainingSeconds = if (avgTimePerFile > 0) (remainingFiles * avgTimePerFile) / 1000 else -1L

                val localFile = localBook.filePath?.let { File(it) }
                val originalName = localFile?.name ?: "${localBook.title}.fb2"

                onProgress(
                    "Загрузка на диск: ${index + 1} из $totalUploads",
                    completedTasks,
                    totalTasks,
                    YandexSyncState.Stage.UPLOADING,
                    downloadedCount,
                    uploadedCount,
                    remainingSeconds
                )

                if (localFile != null && localFile.exists()) {
                    try {
                        val cleanPath = YandexDiskManager.normalizePath("$syncFolder/$originalName")
                        val success = YandexDiskManager.uploadBook(context, cleanPath, localFile)
                        if (success) {
                            uploadedCount++
                            Log.d(TAG, "Успешно загружена книга: $originalName")
                        } else {
                            Log.e(TAG, "Ошибка загрузки книги '$originalName'")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка загрузки книги '$originalName'", e)
                    }
                } else {
                    Log.w(TAG, "Файл книги не найден на устройстве для загрузки: ${localBook.title}")
                }
                completedTasks++
            }

            YandexDiskManager.saveSyncTimestamp(context)
            
            val deletedCount = YandexSyncState.state.value.deletedDuplicatesCount
            onProgress(
                "Синхронизация завершена! Загружено $uploadedCount, скачано $downloadedCount, удалено $deletedCount дубликатов",
                completedTasks,
                totalTasks,
                YandexSyncState.Stage.COMPLETED,
                downloadedCount,
                uploadedCount,
                0L
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка при выполнении performSync", e)
            false
        } finally {
            cleanupTempFiles()
        }
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

    /**
     * Очищает кэш временных файлов, оставшихся после операции синхронизации,
     * чтобы не переполнять память устройства.
     */
    private fun cleanupTempFiles() {
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                val tempFiles = cacheDir.listFiles { _, name ->
                    name.startsWith("temp_stat_") || name.startsWith("temp_down_")
                }
                tempFiles?.forEach { file ->
                    if (file.exists()) {
                        val deleted = file.delete()
                        Log.d(TAG, "Cleanup temp file: ${file.name}, success: $deleted")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temporary files from cache", e)
        }
    }
}
