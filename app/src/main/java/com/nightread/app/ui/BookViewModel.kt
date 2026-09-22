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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SettingsManager.SORT_DATE_DESC)
    val sortOption: StateFlow<String> = _sortOption.asStateFlow()

    var isScanning: Boolean = false
        private set

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

    fun importBookFromUri(uri: Uri, context: Context, callback: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val tempFile = File.createTempFile("import_", ".tmp", context.cacheDir)
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val scanner = LibraryScanner.getInstance(context, bookDao)
                val book = scanner.processSingleFile(tempFile)
                tempFile.delete()
                if (book != null) {
                    bookDao.insertBook(book)
                    withContext(Dispatchers.Main) { callback?.invoke(true, "Книга добавлена") }
                } else {
                    withContext(Dispatchers.Main) { callback?.invoke(false, "Не удалось распознать формат книги") }
                }
            } catch (e: Exception) {
                Log.e("BookViewModel", "Error importing book from URI", e)
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
        isScanning = false
        com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
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
                isScanning = false
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            val db = try {
                database
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get database", e)
                isScanning = false
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            val dao = try {
                db.bookDao()
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to obtain BookDao", e)
                isScanning = false
                com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                return
            }
            
            isScanning = true
            com.nightread.app.service.AutoDiscoveryService.isManualScanning = true
            
            viewModelScope.launch(Dispatchers.IO) {
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
                } catch (e: Exception) {
                    Log.e("BookViewModel", "Error during book scan", e)
                    withContext(Dispatchers.Main) {
                        try {
                            android.widget.Toast.makeText(
                                context,
                                "Ошибка при сканировании: ${e.localizedMessage ?: "неизвестная ошибка"}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } catch (ignored: Exception) {}
                    }
                } finally {
                    isScanning = false
                    com.nightread.app.service.AutoDiscoveryService.isManualScanning = false
                }
            }
        } catch (e: Exception) {
            Log.e("BookViewModel", "Unexpected error in startLocalBookScan", e)
            isScanning = false
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
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get application context", e)
                return
            }
            
            val db = try {
                database
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to get database", e)
                return
            }
            
            val dao = try {
                db.bookDao()
            } catch (e: Exception) {
                Log.e("BookViewModel", "Failed to obtain BookDao", e)
                return
            }
            
            isScanning = true
            
            viewModelScope.launch(Dispatchers.IO) {
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
                } catch (e: Exception) {
                    Log.e("BookViewModel", "Error during incremental scan", e)
                } finally {
                    isScanning = false
                }
            }
        } catch (e: Exception) {
            Log.e("BookViewModel", "Unexpected error in startIncrementalBookScan", e)
            isScanning = false
        }
    }
}
