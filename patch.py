import re

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

# We want to replace the `syncWithCloud` method completely.
# It starts at: suspend fun syncWithCloud(
# and ends right before: suspend fun pushProgressToCloud(

start_idx = content.find("    suspend fun syncWithCloud(")
end_idx = content.find("    /**\n     * Directly pushes reading progress")

if start_idx == -1 or end_idx == -1:
    print("Could not find start or end index.")
    exit(1)

new_func = """    private fun calculateMD5(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeSha1(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        return try {
            val prefixLen = minOf(bytes.size, 2048)
            val prefix = String(bytes, 0, prefixLen, Charsets.UTF_8)
            if (prefix.contains("<?xml", ignoreCase = true) || prefix.contains("<fictionbook", ignoreCase = true)) {
                String(bytes, Charsets.UTF_8)
            } else {
                String(bytes, java.nio.charset.Charset.forName("windows-1251"))
            }
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    suspend fun syncWithCloud(
        context: Context,
        onProgress: (status: String, completed: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val token = getToken(context) ?: return@withContext false
        val authHeader = "OAuth $token"

        try {
            onProgress("Инициализация папок в облаке...", 0, 1)
            initDirectories(authHeader)

            val database = AppDatabase.getDatabase(context)
            val repository = BookRepository(database.bookDao(), database.noteDao())
            val localBooks = repository.allBooks.first()

            val localMd5ToSha1 = mutableMapOf<String, String>()
            for (book in localBooks) {
                val file = book.filePath?.let { File(it) }
                if (file != null && file.exists()) {
                    val md5 = calculateMD5(file)
                    localMd5ToSha1[md5] = book.sha1
                }
            }

            onProgress("Получение списка книг из облака...", 0, 1)
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

            val cloudSha1s = mutableSetOf<String>()
            var downloadedCount = 0
            var skippedCount = 0

            var downloadIndex = 0
            val totalCloudItems = cloudItems.size
            for (cloudItem in cloudItems) {
                onProgress("Проверка книги: ${cloudItem.name}", downloadIndex, totalCloudItems)
                val cloudMd5 = cloudItem.md5
                if (cloudMd5 != null && localMd5ToSha1.containsKey(cloudMd5)) {
                    cloudSha1s.add(localMd5ToSha1[cloudMd5]!!)
                } else {
                    onProgress("Скачивание новой книги: ${cloudItem.name}", downloadIndex, totalCloudItems)
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
                        var sha1: String? = null
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
                                        sha1 = computeSha1(fb2Bytes)
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
                            sha1 = computeSha1(fb2Bytes)
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

                        if (sha1 != null) {
                            if (localBooks.any { it.sha1 == sha1 }) {
                                skippedCount++
                                localFile.delete()
                                cloudSha1s.add(sha1)
                            } else {
                                val coverPath = NewCoverExtractor.extractAndSaveCover(content, sha1!!, context)
                                val strippedContent = NewCoverExtractor.stripBinarySections(content)
                                
                                val newBook = BookEntity(
                                    sha1 = sha1!!,
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
                                cloudSha1s.add(sha1!!)
                                Log.d(TAG, "Downloaded and saved new book: $title")
                            }
                        } else {
                            localFile.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing cloud book: ${cloudItem.name}", e)
                    }
                }
                downloadIndex++
            }

            val booksToUpload = localBooks.filter { !cloudSha1s.contains(it.sha1) }
            var uploadCount = 0
            val totalUploads = booksToUpload.size
            for (localBook in booksToUpload) {
                val localFile = localBook.filePath?.let { File(it) }
                if (localFile != null && localFile.exists()) {
                    val filename = localFile.name
                    onProgress("Загрузка книги: $filename", uploadCount, totalUploads)
                    try {
                        val linkResponse = api.getUploadLink(authHeader, "disk:/SmartReader/Books/$filename")
                        val fileBytes = localFile.readBytes()
                        api.uploadFile(linkResponse.href, fileBytes.toRequestBody("application/octet-stream".toMediaType()))
                        Log.d(TAG, "Uploaded book: $filename")
                        uploadCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading book: $filename", e)
                    }
                }
            }

            onProgress("Синхронизация прогресса чтения...", 0, 1)
            val progressResponse = try {
                api.getResource(authHeader, "disk:/SmartReader/Progress", limit = 500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get progress folder items", e)
                null
            }

            val progressItems = cloudResourceItems(progressResponse)
            val progressAdapter = moshi.adapter(BookProgressPayload::class.java)

            for (progressItem in progressItems) {
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
                val matchingCloudProgress = progressItems.find { it.name == cloudProgressName }

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

            onProgress("Загружено $uploadCount, скачано $downloadedCount, пропущено $skippedCount", 1, 1)
            saveSyncTimestamp(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during full sync", e)
            onProgress("Ошибка синхронизации: ${e.localizedMessage}", 0, 1)
            false
        }
    }
"""

new_content = content[:start_idx] + new_func + content[end_idx:]

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(new_content)

print("Patched YandexDiskManager.kt successfully!")
