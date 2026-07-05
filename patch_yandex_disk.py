import re

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

# I will replace `suspend fun syncWithCloud` entirely.
start_idx = content.find("    suspend fun syncWithCloud(")
end_idx = content.find("    /**\n     * Directly pushes reading progress")

if start_idx == -1 or end_idx == -1:
    print("Could not find start or end index.")
    exit(1)

new_code = """    suspend fun calculateSyncStats(
        context: Context,
        onProgress: (status: String) -> Unit
    ): SyncStats? = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext null
        val authHeader = "OAuth $token"

        try {
            onProgress("Инициализация папок в облаке...")
            initDirectories(authHeader)

            val database = AppDatabase.getDatabase(context)
            val repository = BookRepository(database.bookDao(), database.noteDao())
            val localBooks = repository.allBooks.first()

            onProgress("Получение списка книг из облака...")
            val cloudBooksResponse = try {
                api.getResource(authHeader, "disk:/SmartReader/Books", limit = 500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cloud books resource", e)
                null
            }

            val cloudItems = cloudResourceItems(cloudBooksResponse).filter { 
                val name = it.name.lowercase()
                name.endsWith(".fb2") || name.endsWith(".fb2.zip") 
            }

            onProgress("Получение манифеста...")
            var manifest = SyncManifest()
            try {
                val manifestLink = api.getDownloadLink(authHeader, "disk:/SmartReader/Books/sync_manifest.json")
                val body = api.downloadFile(manifestLink.href)
                val jsonStr = body.string()
                val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(SyncManifest::class.java)
                manifest = adapter.fromJson(jsonStr) ?: SyncManifest()
            } catch (e: Exception) {
                Log.d(TAG, "Manifest not found or error, starting fresh")
            }

            var duplicates = 0
            val toDownload = mutableListOf<ResourceItem>()
            val toUpload = mutableListOf<BookEntity>()

            // Update manifest for files that don't have SHA1 yet by downloading them
            val totalCloudItems = cloudItems.size
            for ((index, cloudItem) in cloudItems.withIndex()) {
                if (!manifest.books.containsKey(cloudItem.name)) {
                    onProgress("Анализ файла ${cloudItem.name} (${index + 1}/$totalCloudItems)...")
                    try {
                        val linkResponse = api.getDownloadLink(authHeader, "disk:/SmartReader/Books/${cloudItem.name}")
                        val responseBody = api.downloadFile(linkResponse.href)
                        val bytes = responseBody.bytes()
                        
                        val ext = cloudItem.name.substringAfterLast(".").lowercase()
                        var sha1: String? = null
                        
                        if (ext == "zip") {
                            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                                        val fb2Bytes = zis.readBytes()
                                        sha1 = computeSha1(fb2Bytes)
                                        break
                                    }
                                    entry = zis.nextEntry
                                }
                            }
                        } else {
                            sha1 = computeSha1(bytes)
                        }
                        
                        if (sha1 != null) {
                            manifest.books[cloudItem.name] = sha1
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to analyze ${cloudItem.name}", e)
                    }
                }
            }

            // Now we know all cloud SHA1s
            val cloudSha1s = manifest.books.values.toSet()

            // Calculate download list
            for (cloudItem in cloudItems) {
                val sha1 = manifest.books[cloudItem.name]
                if (sha1 != null) {
                    if (localBooks.any { it.sha1 == sha1 }) {
                        duplicates++
                    } else {
                        toDownload.add(cloudItem)
                    }
                }
            }

            // Calculate upload list
            for (localBook in localBooks) {
                if (!cloudSha1s.contains(localBook.sha1)) {
                    toUpload.add(localBook)
                }
            }

            // Also get cloud progress items so we don't have to fetch them again
            val progressResponse = try {
                api.getResource(authHeader, "disk:/SmartReader/Progress", limit = 500)
            } catch (e: Exception) {
                null
            }
            val cloudProgressItems = cloudResourceItems(progressResponse)

            return@withContext SyncStats(
                booksOnDisk = cloudItems.size,
                booksLocal = localBooks.size,
                toDownload = toDownload,
                toUpload = toUpload,
                duplicates = duplicates,
                manifest = manifest,
                cloudProgressItems = cloudProgressItems
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating stats", e)
            return@withContext null
        }
    }

    suspend fun executeSync(
        context: Context,
        stats: SyncStats,
        onProgress: (status: String, completed: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"
        
        try {
            val database = AppDatabase.getDatabase(context)
            val repository = BookRepository(database.bookDao(), database.noteDao())
            
            // DOWNLOAD
            val totalDownloads = stats.toDownload.size
            var downloadedCount = 0
            for ((index, cloudItem) in stats.toDownload.withIndex()) {
                onProgress("Скачивание: ${index + 1} из $totalDownloads", index, totalDownloads)
                try {
                    val linkResponse = api.getDownloadLink(authHeader, "disk:/SmartReader/Books/${cloudItem.name}")
                    val responseBody = api.downloadFile(linkResponse.href)
                    val bytes = responseBody.bytes()

                    val importedFolder = File(context.filesDir, "imported_books")
                    if (!importedFolder.exists()) {
                        importedFolder.mkdirs()
                    }
                    val localFile = File(importedFolder, cloudItem.name)
                    localFile.writeBytes(bytes)

                    val ext = localFile.extension.lowercase()
                    val sha1 = stats.manifest.books[cloudItem.name] ?: continue
                    
                    var content = ""
                    var title = cloudItem.name.substringBeforeLast(".")
                    var author = "Неизвестен"
                    var series: String? = null
                    var seriesIndex: Int? = null
                    var language: String? = "ru"
                    var annotation: String? = null

                    if (ext == "zip") {
                        java.util.zip.ZipInputStream(localFile.inputStream().buffered()).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                                    val fb2Bytes = zis.readBytes()
                                    content = decodeBytesToString(fb2Bytes)
                                    val meta = NewFb2Parser.parse(content, entry.name.substringBeforeLast("."))
                                    title = meta.title
                                    author = meta.author
                                    content = meta.content
                                    series = meta.series
                                    seriesIndex = meta.seriesIndex
                                    language = meta.language
                                    annotation = meta.annotation
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    } else if (ext == "fb2") {
                        val fb2Bytes = bytes
                        content = decodeBytesToString(fb2Bytes)
                        val meta = NewFb2Parser.parse(content, title)
                        title = meta.title
                        author = meta.author
                        content = meta.content
                        series = meta.series
                        seriesIndex = meta.seriesIndex
                        language = meta.language
                        annotation = meta.annotation
                    }

                    val coverPath = NewCoverExtractor.extractAndSaveCover(content, sha1, context)
                    val strippedContent = NewCoverExtractor.stripBinarySections(content)
                    
                    val newBook = BookEntity(
                        sha1 = sha1,
                        title = title,
                        author = author,
                        content = strippedContent,
                        category = "Локальные",
                        totalCharacters = strippedContent.length,
                        coverGradientStart = getRandomGradientStartColor(),
                        coverGradientEnd = getRandomGradientEndColor(),
                        filePath = localFile.absolutePath,
                        series = series,
                        seriesIndex = seriesIndex,
                        language = language,
                        annotation = annotation,
                        fileSize = bytes.size.toLong(),
                        coverPath = coverPath
                    )
                    repository.insertBook(newBook)
                    downloadedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading book: ${cloudItem.name}", e)
                }
            }

            // UPLOAD
            val totalUploads = stats.toUpload.size
            var uploadedCount = 0
            for ((index, localBook) in stats.toUpload.withIndex()) {
                val localFile = localBook.filePath?.let { File(it) }
                if (localFile != null && localFile.exists()) {
                    val filename = localFile.name
                    onProgress("Загрузка: ${index + 1} из $totalUploads", index, totalUploads)
                    try {
                        val linkResponse = api.getUploadLink(authHeader, "disk:/SmartReader/Books/$filename")
                        val fileBytes = localFile.readBytes()
                        api.uploadFile(linkResponse.href, fileBytes.toRequestBody("application/octet-stream".toMediaType()))
                        
                        stats.manifest.books[filename] = localBook.sha1
                        uploadedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading book: $filename", e)
                    }
                }
            }

            // UPLOAD MANIFEST
            onProgress("Обновление манифеста...", 0, 1)
            try {
                val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(SyncManifest::class.java)
                val jsonStr = adapter.toJson(stats.manifest)
                val linkResponse = api.getUploadLink(authHeader, "disk:/SmartReader/Books/sync_manifest.json")
                api.uploadFile(linkResponse.href, jsonStr.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType()))
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading manifest", e)
            }

            // SYNC PROGRESS
            onProgress("Синхронизация прогресса чтения...", 0, 1)
            val progressAdapter = moshi.adapter(BookProgressPayload::class.java)

            for (progressItem in stats.cloudProgressItems) {
                if (progressItem.type == "file" && progressItem.name.endsWith(".json")) {
                    try {
                        val linkResponse = api.getDownloadLink(authHeader, "disk:/SmartReader/Progress/${progressItem.name}")
                        val body = api.downloadFile(linkResponse.href)
                        val jsonStr = body.string()
                        val cloudProgress = progressAdapter.fromJson(jsonStr)

                        if (cloudProgress != null) {
                            val localBook = repository.getBookBySha1(cloudProgress.sha1)
                            if (localBook != null) {
                                if (cloudProgress.lastReadTime > localBook.lastReadTime) {
                                    repository.updateBook(localBook.copy(
                                        currentProgressChar = cloudProgress.currentProgressChar,
                                        lastReadTime = cloudProgress.lastReadTime
                                    ))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing progress file: ${progressItem.name}", e)
                    }
                }
            }

            val updatedLocalBooks = repository.allBooks.first()
            for (localBook in updatedLocalBooks) {
                val cloudProgressName = "progress_${localBook.sha1}.json"
                val matchingCloudProgress = stats.cloudProgressItems.find { it.name == cloudProgressName }

                var shouldUploadProgress = false
                if (matchingCloudProgress == null) {
                    shouldUploadProgress = localBook.currentProgressChar > 0
                } else {
                    shouldUploadProgress = true
                }

                if (shouldUploadProgress) {
                    try {
                        val payload = BookProgressPayload(
                            sha1 = localBook.sha1,
                            title = localBook.title,
                            currentProgressChar = localBook.currentProgressChar,
                            lastReadTime = localBook.lastReadTime
                        )
                        val json = progressAdapter.toJson(payload)
                        val link = api.getUploadLink(authHeader, "disk:/SmartReader/Progress/$cloudProgressName")
                        api.uploadFile(link.href, json.toByteArray(StandardCharsets.UTF_8).toRequestBody("application/json".toMediaType()))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pushing progress: ${localBook.title}", e)
                    }
                }
            }

            saveSyncTimestamp(context)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error during execution", e)
            return@withContext false
        }
    }
"""

new_content = content[:start_idx] + new_code + content[end_idx:]

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(new_content)

print("Patched YandexDiskManager.kt successfully!")
