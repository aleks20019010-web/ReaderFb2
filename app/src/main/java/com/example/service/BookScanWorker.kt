package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.BookEntity
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class BookScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val rootPath = inputData.getString("ROOT_PATH") ?: "/storage/emulated/0"
        
        BookScannerState.updateScanning(applicationContext, true, "Подготовка к сканированию...")

        try {
            performScan(rootPath)
        } catch (e: Exception) {
            android.util.Log.e("BookScanWorker", "Error during scan", e)
            BookScannerState.updateScanning(applicationContext, false, "Ошибка сканирования: ${e.localizedMessage}")
            return Result.failure()
        }

        return Result.success()
    }

    private suspend fun performScan(rootPath: String) {
        val database = AppDatabase.getDatabase(applicationContext)
        val bookDao = database.bookDao()

        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            BookScannerState.updateScanning(applicationContext, false, "Папка не найдена: $rootPath")
            return
        }

        val filesToProcess = mutableListOf<File>()
        
        fun traverse(dir: File) {
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
                    if (ext == "txt" || ext == "fb2" || ext == "epub" || ext == "zip") {
                        if (file.length() < 30 * 1024 * 1024 && file.length() > 0) {
                            filesToProcess.add(file)
                        }
                    }
                }
            }
        }

        BookScannerState.updateScanning(applicationContext, true, "Поиск файлов в $rootPath...")
        traverse(rootDir)

        if (filesToProcess.isEmpty()) {
            BookScannerState.updateScanning(applicationContext, false, "Книг не найдено в $rootPath")
            return
        }

        var importedCount = 0
        for ((index, file) in filesToProcess.withIndex()) {
            val progressText = "Чтение (${index + 1}/${filesToProcess.size}): ${file.name}"
            BookScannerState.updateScanning(applicationContext, true, progressText)

            runCatching {
                val ext = file.extension.lowercase()
                val computedSha1 = computeSha1(file)

                // Check duplicates first in a transaction
                val existingBook = bookDao.getBookBySha1(computedSha1)
                if (existingBook != null) {
                    return@runCatching
                }

                var parsedTitle = file.nameWithoutExtension
                var parsedAuthor = "Неизвестен"
                var parsedContent = ""
                var parsedSeries: String? = null
                var parsedSeriesIndex: Int? = null
                var parsedLanguage: String? = "ru"

                if (ext == "fb2") {
                    val rawText = decodeBytesToString(file.readBytes())
                    val parsed = Fb2Parser.parse(rawText, parsedTitle)
                    parsedTitle = parsed.title
                    parsedAuthor = parsed.author
                    parsedContent = parsed.content
                    parsedSeries = parsed.series
                    parsedSeriesIndex = parsed.seriesIndex
                    parsedLanguage = parsed.language
                } else if (ext == "zip") {
                    file.inputStream().use { fis ->
                        ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val entryName = entry.name.lowercase()
                                if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                    val bytes = zis.readBytes()
                                    val rawText = decodeBytesToString(bytes)
                                    val parsed = Fb2Parser.parse(rawText, entryName.removeSuffix(".fb2"))
                                    parsedTitle = parsed.title
                                    parsedAuthor = parsed.author
                                    parsedContent = parsed.content
                                    parsedSeries = parsed.series
                                    parsedSeriesIndex = parsed.seriesIndex
                                    parsedLanguage = parsed.language
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } else {
                    parsedContent = decodeBytesToString(file.readBytes())
                    parsedAuthor = if (ext == "epub") "Локальный EPUB" else "Локальный TXT"
                }

                if (parsedContent.isNotBlank()) {
                    val coverPath = BookScannerState.extractCover(file, computedSha1, applicationContext)

                    val newBook = BookEntity(
                        title = parsedTitle,
                        author = parsedAuthor,
                        content = parsedContent,
                        category = "Локальные",
                        totalCharacters = parsedContent.length,
                        coverGradientStart = getRandomGradientStartColor(),
                        coverGradientEnd = getRandomGradientEndColor(),
                        filePath = file.absolutePath,
                        sha1 = computedSha1,
                        fileSize = file.length(),
                        coverPath = coverPath,
                        series = parsedSeries,
                        seriesIndex = parsedSeriesIndex,
                        language = parsedLanguage
                    )
                    
                    // Prevent duplicate entries using Room transaction
                    val inserted = bookDao.insertBookIfUnique(newBook)
                    if (inserted) {
                        importedCount++
                    }
                }
            }.onFailure { t ->
                android.util.Log.e("BookScanWorker", "Failed to scan book: ${file.name}", t)
            }
        }

        val resultMsg = if (importedCount > 0) {
            "Успешно импортировано новых книг: $importedCount"
        } else {
            "Все найденные книги уже есть в библиотеке (${filesToProcess.size} файлов)"
        }
        BookScannerState.updateScanning(applicationContext, false, resultMsg)
    }

    private fun computeSha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { String.format("%02x", it) }
    }

    private fun decodeBytesToString(bytes: ByteArray): String {
        try {
            val headerSize = if (bytes.size > 1024) 1024 else bytes.size
            val header = String(bytes, 0, headerSize, java.nio.charset.StandardCharsets.ISO_8859_1)
            val match = """encoding=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val encName = match.groupValues[1].trim()
                try {
                    return String(bytes, java.nio.charset.Charset.forName(encName))
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}

        try {
            val utf8Decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            utf8Decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val charBuffer = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (e: Exception) {
            try {
                return String(bytes, java.nio.charset.Charset.forName("Windows-1251"))
            } catch (e2: Exception) {
                return String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)
            }
        }
    }

    private fun getRandomGradientStartColor(): String {
        val list = listOf("#FF6B6B", "#4E65FF", "#11998e", "#FC466B", "#f12711", "#833ab4")
        return list.random()
    }

    private fun getRandomGradientEndColor(): String {
        val list = listOf("#4D96FF", "#92EFFD", "#38ef7d", "#3F5EFB", "#f5af19", "#fd1d1d")
        return list.random()
    }
}
