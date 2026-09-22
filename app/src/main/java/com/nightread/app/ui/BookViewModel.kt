package com.nightread.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookDao
import com.nightread.app.data.BookEntity
import com.nightread.app.data.SettingsManager
import com.nightread.app.scanner.LibraryScanner
import com.nightread.app.service.NewBookScanState
import com.nightread.app.service.ScannerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BookViewModel(application: Application) : AndroidViewModel(application) {
    private val database: AppDatabase by lazy { AppDatabase.getDatabase(application) }
    private val bookDao: BookDao by lazy { database.bookDao() }
    private var currentScanJob: kotlinx.coroutines.Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SettingsManager.SORT_DATE_DESC)
    val sortOption: StateFlow<String> = _sortOption.asStateFlow()

    val scanState: StateFlow<ScannerState> = NewBookScanState.state

    val isScanning: Boolean
        get() = NewBookScanState.state.value.isScanning

    val scanProgressText: String
        get() = NewBookScanState.state.value.status

    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()

    val searchedBooks: Flow<List<BookEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            bookDao.getAllBooks()
        } else {
            bookDao.searchBooks("%$query%")
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: String) {
        _sortOption.value = option
    }

    fun sortBooks(books: List<BookEntity>, sortOption: String): List<BookEntity> {
        return when (sortOption) {
            SettingsManager.SORT_TITLE_ASC -> books.sortedBy { it.title }
            SettingsManager.SORT_TITLE_DESC -> books.sortedByDescending { it.title }
            SettingsManager.SORT_AUTHOR_ASC -> books.sortedBy { it.author }
            SettingsManager.SORT_AUTHOR_DESC -> books.sortedByDescending { it.author }
            SettingsManager.SORT_DATE_ASC -> books.sortedBy { it.dateAdded }
            SettingsManager.SORT_DATE_DESC -> books.sortedByDescending { it.dateAdded }
            SettingsManager.SORT_PROGRESS_ASC -> books.sortedBy { it.currentProgressChar }
            SettingsManager.SORT_PROGRESS_DESC -> books.sortedByDescending { it.currentProgressChar }
            else -> books.sortedByDescending { it.lastReadTime }
        }
    }

    fun openBook(book: BookEntity, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateProgress(book.sha1, book.currentProgressChar, System.currentTimeMillis())
            val ctx = context ?: getApplication<Application>()
            withContext(Dispatchers.Main) {
                if (context != null) {
                    val intent = android.content.Intent(ctx, BookDetailActivity::class.java).apply {
                        putExtra("BOOK_SHA1", book.sha1)
                    }
                    ctx.startActivity(intent)
                }
            }
        }
    }

    fun importBooksFromUris(uris: List<Uri>, context: Context, callback: ((Int, Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            val total = uris.size
            val contentResolver = context.contentResolver
            val booksDir = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }
            val scanner = LibraryScanner.getInstance(context, bookDao)

            for (uri in uris) {
                var tempFile: File? = null
                try {
                    var displayName = "imported_book"
                    try {
                        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIdx >= 0) {
                                    displayName = cursor.getString(nameIdx) ?: displayName
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("BookViewModel", "Could not get display name from URI", e)
                    }

                    tempFile = File.createTempFile("import_", ".tmp", context.cacheDir)
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        tempFile.delete()
                        continue
                    }

                    val parsedBook = scanner.processSingleFile(tempFile)
                    if (parsedBook != null) {
                        val ext = if (displayName.contains(".")) displayName.substringAfterLast(".") else "book"
                        val permanentFile = File(booksDir, "${parsedBook.sha1}.$ext")
                        
                        if (!tempFile.renameTo(permanentFile)) {
                            tempFile.copyTo(permanentFile, overwrite = true)
                            tempFile.delete()
                        }
                        tempFile = null

                        val bookTitle = if (parsedBook.title.isBlank() || parsedBook.title.startsWith("import_")) {
                            if (displayName.contains(".")) displayName.substringBeforeLast(".") else displayName
                        } else {
                            parsedBook.title
                        }

                        val finalBook = parsedBook.copy(
                            filePath = permanentFile.absolutePath,
                            title = bookTitle
                        )
                        bookDao.importOrUpdateBook(finalBook)
                        successCount++
                    } else {
                        tempFile.delete()
                        tempFile = null
                    }
                } catch (e: Exception) {
                    Log.e("BookViewModel", "Error importing single URI: $uri", e)
                    tempFile?.delete()
                }
            }
            withContext(Dispatchers.Main) {
                callback?.invoke(successCount, total)
            }
        }
    }

    fun deleteBook(book: BookEntity, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bookDao.deleteBookBySha1(book.sha1)
                val ctx = context ?: getApplication<Application>()
                if (book.filePath != null) {
                    val f = File(book.filePath)
                    if (f.exists() && f.absolutePath.startsWith(ctx.filesDir.absolutePath)) {
                        f.delete()
                    }
                }
                val cover1 = File(ctx.filesDir, "covers/cover_${book.sha1}.jpg")
                if (cover1.exists()) cover1.delete()
                val cover2 = File(ctx.filesDir, "covers/${book.sha1}.jpg")
                if (cover2.exists()) cover2.delete()
            } catch (e: Exception) {
                Log.e("BookViewModel", "Error deleting book: ${book.title}", e)
            }
        }
    }

    fun importBookFromUri(uri: Uri, context: Context, callback: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val contentResolver = context.contentResolver
                val booksDir = File(context.filesDir, "books").apply { if (!exists()) mkdirs() }
                
                var displayName = "imported_book"
                try {
                    contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                displayName = cursor.getString(nameIdx) ?: displayName
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("BookViewModel", "Could not get display name from URI", e)
                }

                tempFile = File.createTempFile("import_", ".tmp", context.cacheDir)
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw java.io.IOException("Cannot open input stream for URI: $uri")

                val scanner = LibraryScanner.getInstance(context, bookDao)
                val parsedBook = scanner.processSingleFile(tempFile)
                if (parsedBook != null) {
                    val ext = if (displayName.contains(".")) displayName.substringAfterLast(".") else "book"
                    val permanentFile = File(booksDir, "${parsedBook.sha1}.$ext")
                    
                    if (!tempFile.renameTo(permanentFile)) {
                        tempFile.copyTo(permanentFile, overwrite = true)
                        tempFile.delete()
                    }
                    tempFile = null

                    val bookTitle = if (parsedBook.title.isBlank() || parsedBook.title.startsWith("import_")) {
                        if (displayName.contains(".")) displayName.substringBeforeLast(".") else displayName
                    } else {
                        parsedBook.title
                    }

                    val finalBook = parsedBook.copy(
                        filePath = permanentFile.absolutePath,
                        title = bookTitle
                    )
                    bookDao.importOrUpdateBook(finalBook)
                    withContext(Dispatchers.Main) { callback?.invoke(true, "Книга успешно добавлена") }
                } else {
                    tempFile.delete()
                    tempFile = null
                    withContext(Dispatchers.Main) { callback?.invoke(false, "Не удалось распознать формат книги") }
                }
            } catch (e: Exception) {
                Log.e("BookViewModel", "Error importing book from URI", e)
                tempFile?.delete()
                withContext(Dispatchers.Main) { callback?.invoke(false, e.localizedMessage ?: "Ошибка импорта") }
            }
        }
    }

    fun clearScanCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bookDao.deleteAllScannedFiles()
            } catch (e: Exception) {
                Log.e("BookViewModel", "Error clearing cache", e)
            }
        }
    }

    fun clearLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bookDao.deleteAllBooks()
            } catch (e: Exception) {
                Log.e("BookViewModel", "Error clearing library", e)
            }
        }
    }

    fun cancelAllScanningTasks() {
        try {
            val context = getApplication<Application>().applicationContext
            LibraryScanner.getInstance(context, bookDao).cancelScanning()
        } catch (e: Exception) {
            Log.e("BookViewModel", "Error stopping scanner instance", e)
        }
        currentScanJob?.cancel()
        currentScanJob = null
        com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
        NewBookScanState.reset()
    }

    fun startLocalBookScan(rootPath: String = "/storage/emulated/0") {
        if (isScanning) {
            Log.d("BookViewModel", "Scan already in progress")
            return
        }
        
        try {
            val context = try {
                getApplication<Application>().applicationContext
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get application context", e)
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            val db = try {
                database
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get database", e)
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            val dao = try {
                db.bookDao()
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to obtain BookDao", e)
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = true
            
            currentScanJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val scanner = LibraryScanner.getInstance(context, dao)
                    scanner.scanBooks(force = true).join()
                    
                    Log.d("BookViewModel", "Book scan completed successfully")
                } catch (e: OutOfMemoryError) {
                    Log.e("BookViewModel", "Out of memory during scan", e)
                    System.gc()
                    withContext(Dispatchers.Main) {
                        try {
                            android.widget.Toast.makeText(
                                context,
                                "Недостаточно памяти для сканирования",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (ignored: Exception) {}
                    }
                } catch (e: CancellationException) {
                    Log.d("BookViewModel", "Book scan cancelled")
                    throw e
                } catch (e: Throwable) {
                    Log.e("BookViewModel", "Error during book scan", e)
                    withContext(Dispatchers.Main) {
                        try {
                            android.widget.Toast.makeText(
                                context,
                                "Ошибка при сканировании: ${e.localizedMessage ?: "неизвестная ошибка"}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (ignored: Throwable) {}
                    }
                } finally {
                    com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                }
            }
        } catch (e: Throwable) {
            Log.e("BookViewModel", "Unexpected error in startLocalBookScan", e)
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
        }
    }

    fun startIncrementalBookScan() {
        if (isScanning) {
            Log.d("BookViewModel", "Incremental scan already in progress")
            return
        }
        
        try {
            val context = try {
                getApplication<Application>().applicationContext
            } catch (e: Throwable) {
                Log.e("BookViewModel", "Failed to get application context", e)
                return
            }
            
            val db = try {
                database
            } catch (e: Throwable) {
                Log.e("BookViewModel", "Failed to get database", e)
                return
            }
            
            val dao = try {
                db.bookDao()
            } catch (e: Throwable) {
                Log.e("BookViewModel", "Failed to obtain BookDao", e)
                return
            }
            
            currentScanJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val scanner = LibraryScanner.getInstance(context, dao)
                    scanner.scanBooks(force = false).join()
                    
                    Log.d("BookViewModel", "Incremental book scan completed")
                } catch (e: OutOfMemoryError) {
                    Log.e("BookViewModel", "Out of memory during incremental scan", e)
                    System.gc()
                } catch (e: CancellationException) {
                    Log.d("BookViewModel", "Incremental scan cancelled")
                    throw e
                } catch (e: Throwable) {
                    Log.e("BookViewModel", "Error during incremental scan", e)
                }
            }
        } catch (e: Throwable) {
            Log.e("BookViewModel", "Unexpected error in startIncrementalBookScan", e)
        }
    }
}
