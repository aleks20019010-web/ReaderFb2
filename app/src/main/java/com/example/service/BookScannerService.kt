package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.data.AppDatabase
import com.example.data.BookEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

class BookScannerService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rootPath = intent?.getStringExtra("ROOT_PATH") ?: "/storage/emulated/0"
        
        if (BookScannerState.isScanning.value) {
            android.util.Log.d("BookScannerService", "Scan already in progress, skipping")
            return START_NOT_STICKY
        }

        BookScannerState.updateScanning(this, true, "Подготовка к сканированию...")

        serviceScope.launch {
            try {
                performScan(rootPath)
            } catch (e: Exception) {
                android.util.Log.e("BookScannerService", "Error during scan", e)
                BookScannerState.updateScanning(this@BookScannerService, false, "Ошибка сканирования: ${e.localizedMessage}")
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun performScan(rootPath: String) {
        val database = AppDatabase.getDatabase(applicationContext)
        val bookDao = database.bookDao()
        
        val existingSha1s = try {
            bookDao.getAllBooks().first().mapNotNull { it.sha1 }.toMutableSet()
        } catch (e: Exception) {
            android.util.Log.e("BookScannerService", "Failed to load existing SHA1 list", e)
            mutableSetOf<String>()
        }

        val existingTitles = try {
            bookDao.getAllBooks().first().map { it.title.lowercase() }.toMutableSet()
        } catch (e: Exception) {
            android.util.Log.e("BookScannerService", "Failed to load existing titles", e)
            mutableSetOf<String>()
        }

        val rootDir = File(rootPath)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            BookScannerState.updateScanning(this, false, "Папка не найдена: $rootPath")
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

        BookScannerState.updateScanning(this, true, "Поиск файлов в $rootPath...")
        traverse(rootDir)

        if (filesToProcess.isEmpty()) {
            BookScannerState.updateScanning(this, false, "Книг не найдено в $rootPath")
            return
        }

        var importedCount = 0
        for ((index, file) in filesToProcess.withIndex()) {
            val progressText = "Чтение (${index + 1}/${filesToProcess.size}): ${file.name}"
            BookScannerState.updateScanning(this, true, progressText)

            runCatching {
                val ext = file.extension.lowercase()
                
                val computedSha1 = computeSha1(file)
                if (existingSha1s.contains(computedSha1)) {
                    return@runCatching
                }

                var parsedTitle = file.nameWithoutExtension
                var parsedAuthor = "Неизвестен"
                var parsedContent = ""
                var parsedSeries: String? = null
                var parsedLanguage: String? = "ru"

                fun decodeBytesToString(bytes: ByteArray): String {
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

                fun parseFb2Text(rawText: String, fallback: String): ParsedBookInfo {
                    val titleRegex = "<book-title>(.*?)</book-title>".toRegex(RegexOption.IGNORE_CASE)
                    val authorFirstRegex = "<first-name>(.*?)</first-name>".toRegex(RegexOption.IGNORE_CASE)
                    val authorLastRegex = "<last-name>(.*?)</last-name>".toRegex(RegexOption.IGNORE_CASE)
                    val langRegex = "<lang>(.*?)</lang>".toRegex(RegexOption.IGNORE_CASE)
                    
                    val titleMatch = titleRegex.find(rawText)
                    val title = titleMatch?.groupValues?.get(1)?.trim() ?: fallback
                    
                    val first = authorFirstRegex.find(rawText)?.groupValues?.get(1)?.trim() ?: ""
                    val last = authorLastRegex.find(rawText)?.groupValues?.get(1)?.trim() ?: ""
                    val author = if (first.isNotEmpty() || last.isNotEmpty()) "$first $last".trim() else "Неизвестен"
                    
                    val bodyStart = rawText.indexOf("<body>", ignoreCase = true)
                    val bodyEnd = rawText.lastIndexOf("</body>", ignoreCase = true)
                    val bodyContent = if (bodyStart != -1 && bodyEnd > bodyStart) {
                        rawText.substring(bodyStart + 6, bodyEnd)
                    } else {
                        rawText
                    }

                    var processedText = bodyContent.replace("""<binary[^>]*>[\s\S]*?</binary>""".toRegex(RegexOption.IGNORE_CASE), "")
                    processedText = processedText.replace("""<title[^>]*>""".toRegex(RegexOption.IGNORE_CASE), "\u000C\n")
                    processedText = processedText.replace("""</title>""".toRegex(RegexOption.IGNORE_CASE), "\n\n")
                    processedText = processedText.replace("""<p[^>]*>""".toRegex(RegexOption.IGNORE_CASE), "    ")
                    processedText = processedText.replace("""</p>""".toRegex(RegexOption.IGNORE_CASE), "\n")
                    processedText = processedText.replace("<[^>]*>".toRegex(), "")
                    processedText = processedText.replace("\r", "")
                    processedText = processedText.replace("\n{3,}".toRegex(), "\n\n")
                    processedText = processedText.replace("\u000C\\s*\u000C".toRegex(), "\u000C")
                    if (processedText.startsWith("\u000C")) {
                        processedText = processedText.substring(1)
                    }

                    val cleanContent = processedText.lines().joinToString("\n") { line ->
                        if (line.trim().isEmpty()) ""
                        else {
                            val indent = line.takeWhile { it.isWhitespace() }
                            val trimmed = line.substring(indent.length).replace("\\s+".toRegex(), " ")
                            indent + trimmed
                        }
                    }.trim()
                    
                    return ParsedBookInfo(title, author, cleanContent, null, "ru")
                }

                if (ext == "fb2") {
                    val rawText = decodeBytesToString(file.readBytes())
                    val parsed = parseFb2Text(rawText, parsedTitle)
                    parsedTitle = parsed.title
                    parsedAuthor = parsed.author
                    parsedContent = parsed.content
                } else if (ext == "zip") {
                    file.inputStream().use { fis ->
                        java.util.zip.ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val entryName = entry.name.lowercase()
                                if (!entry.isDirectory && entryName.endsWith(".fb2")) {
                                    val bytes = zis.readBytes()
                                    val rawText = decodeBytesToString(bytes)
                                    val parsed = parseFb2Text(rawText, entryName.removeSuffix(".fb2"))
                                    parsedTitle = parsed.title
                                    parsedAuthor = parsed.author
                                    parsedContent = parsed.content
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

                if (existingTitles.contains(parsedTitle.lowercase())) {
                    return@runCatching
                }

                if (parsedContent.isNotBlank()) {
                    val coverPath = BookScannerState.extractCover(file, computedSha1, applicationContext)

                    val newBook = BookEntity(
                        title = parsedTitle,
                        author = parsedAuthor,
                        content = parsedContent,
                        category = "Локальные",
                        totalCharacters = parsedContent.length,
                        filePath = file.absolutePath,
                        sha1 = computedSha1,
                        fileSize = file.length(),
                        coverPath = coverPath
                    )
                    bookDao.insertBook(newBook)
                    existingSha1s.add(computedSha1)
                    existingTitles.add(parsedTitle.lowercase())
                    importedCount++
                }
            }.onFailure { t ->
                android.util.Log.e("BookScannerService", "Failed to scan book: ${file.name}", t)
            }
        }

        val resultMsg = if (importedCount > 0) {
            "Успешно импортировано новых книг: $importedCount"
        } else {
            "Все найденные книги уже есть в библиотеке (${filesToProcess.size} файлов)"
        }
        BookScannerState.updateScanning(this, false, resultMsg)
    }

    private fun computeSha1(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { String.format("%02x", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    data class ParsedBookInfo(
        val title: String,
        val author: String,
        val content: String,
        val series: String?,
        val language: String?
    )
}
