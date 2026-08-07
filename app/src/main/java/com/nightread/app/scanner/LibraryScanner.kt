package com.nightread.app.scanner

import android.content.Context
import android.os.Environment
import android.util.Log
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookDao
import com.nightread.app.data.BookEntity
import com.nightread.app.scanner.processors.*
import com.nightread.app.service.ScannerState
import com.nightread.app.service.NewBookScanState
import kotlinx.coroutines.*
import java.io.File
import android.net.Uri

class LibraryScanner(
    private val context: Context,
    private val bookDao: BookDao
) {
    companion object {
        private const val TAG = "LibraryScanner"
    }

    suspend fun scanBooks(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                NewBookScanState.updateState(ScannerState(isScanning = true, status = "Инициализация нового сканера..."))
                
                val db = AppDatabase.getDatabase(context)
                val bookCacheDao = db.bookCacheDao()
                val scanner = BookScanner(bookCacheDao)
                
                val scanDir = Environment.getExternalStorageDirectory()
                NewBookScanState.updateState(ScannerState(isScanning = true, status = "Поиск файлов (может занять время)..."))
                
                val scanResult = scanner.scanDirectory(scanDir)
                Log.d(TAG, "BookScanner scan finished: $scanResult")
                
                NewBookScanState.updateState(ScannerState(isScanning = true, status = "Подготовка новых книг..."))
                
                val cachedBooks = bookCacheDao.getAll()
                val existingEntities = bookDao.getAllBooksSync().map { it.filePath }.toSet()
                
                val newBooksToProcess = cachedBooks.filter { cache -> 
                    !existingEntities.contains(cache.path) && cache.path.isNotBlank()
                }
                
                var processed = 0
                var added = 0
                var skipped = 0
                val total = newBooksToProcess.size
                
                for (cache in newBooksToProcess) {
                    val file = File(cache.path)
                    if (file.exists()) {
                        val bookSource = BookSource(
                            uri = Uri.fromFile(file),
                            name = file.name,
                            size = file.length(),
                            modified = file.lastModified(),
                            realPath = file.absolutePath
                        )
                        
                        val processor = getProcessorForFile(file.name)
                        if (processor != null) {
                            val entity = processor.process(bookSource, context)
                            if (entity != null) {
                                val rowId = bookDao.insertBook(entity)
                                if (rowId > 0) added++ else skipped++
                            } else {
                                skipped++
                            }
                        } else {
                            skipped++
                        }
                    } else {
                        skipped++
                    }
                    processed++
                    NewBookScanState.updateState(ScannerState(
                        isScanning = true, 
                        status = "Обработка: $processed / $total",
                        progress = if (total > 0) (processed * 100) / total else 100,
                        totalFiles = total,
                        processedFiles = processed,
                        addedBooks = added,
                        skippedBooks = skipped
                    ))
                }
                
                NewBookScanState.updateState(ScannerState(
                    isScanning = false, 
                    status = "Готово. Добавлено: $added", 
                    progress = 100,
                    totalFiles = total,
                    processedFiles = processed,
                    addedBooks = added,
                    skippedBooks = skipped
                ))
                
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                NewBookScanState.updateState(ScannerState(isScanning = false, status = "Ошибка: ${e.message}"))
            }
        }
    }
    
    suspend fun checkForNewBooks(): Job {
        return scanBooks() // Delegate for simplicity
    }
    
    private fun getProcessorForFile(name: String): BookProcessor? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".fb2") || lower.endsWith(".fb2.zip") || lower.endsWith(".fbz") -> Fb2Processor()
            lower.endsWith(".epub") -> EpubProcessor()
            lower.endsWith(".fb3") -> Fb3Processor()
            lower.endsWith(".mobi") || lower.endsWith(".azw") || lower.endsWith(".azw3") -> MobiProcessor()
            else -> null
        }
    }
}
