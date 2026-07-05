import re

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

replacement = """
                var processedCount = 0
                val totalToProcess = needsSha1.size
                val startTime = System.currentTimeMillis()
                
                // Parallel processing with limited concurrency
                val concurrencyLimit = 5
                val jobs = needsSha1.chunked(concurrencyLimit)
                
                for (chunk in jobs) {
                    kotlinx.coroutines.awaitAll(
                        *chunk.map { item ->
                            kotlinx.coroutines.async {
                                try {
                                    val linkResponse = api.getDownloadLink(authHeader, item.path ?: "$syncFolder/${item.name}")
                                    val responseBody = api.downloadFile(linkResponse.href)
                                    
                                    val tempFile = File(context.cacheDir, "temp_${item.name}")
                                    try {
                                        tempFile.outputStream().use { output ->
                                            responseBody.byteStream().use { input ->
                                                input.copyTo(output)
                                            }
                                        }
                                        val bytes = tempFile.readBytes()
                                        val sha1 = computeSha1(bytes)
                                        val newEntry = CloudFileEntry(
                                            name = item.name,
                                            size = item.size ?: 0L,
                                            modified = item.modified ?: "",
                                            sha1 = sha1
                                        )
                                        synchronized(cloudCache.entries) {
                                            cloudCache.entries[item.name] = newEntry
                                            updatedCloudBooks.add(newEntry)
                                        }
                                    } finally {
                                        if (tempFile.exists()) {
                                            tempFile.delete() // Guarantee deletion of temp file
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing cloud file: ${item.name}", e)
                                } finally {
                                    synchronized(this@YandexDiskManager) {
                                        processedCount++
                                        if (processedCount % 5 == 0 || processedCount == totalToProcess) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            val avgTimePerFile = elapsed / processedCount
                                            val remaining = totalToProcess - processedCount
                                            val remainingMs = remaining * avgTimePerFile
                                            val remainingMinutes = remainingMs / 60000
                                            val remainingSeconds = (remainingMs % 60000) / 1000
                                            val timeStr = if (remainingMinutes > 0) "$remainingMinutes мин" else "$remainingSeconds сек"
                                            onProgress("Анализ диска: $processedCount из $totalToProcess файлов обработано. Осталось примерно: $timeStr")
                                        }
                                    }
                                }
                            }
                        }.toTypedArray()
                    )
                }
"""

content = re.sub(
    r'\s*var processedCount = 0\s*val totalToProcess = needsSha1\.size\s*// Parallel processing with limited concurrency.*?(?=CloudFileCache\.saveCache)',
    replacement,
    content,
    flags=re.DOTALL
)

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
