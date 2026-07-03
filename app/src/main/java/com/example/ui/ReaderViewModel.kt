package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val bookDao = AppDatabase.getDatabase(application).bookDao()
    
    private val _bookState = MutableStateFlow<BookEntity?>(null)
    val bookState: StateFlow<BookEntity?> = _bookState.asStateFlow()
    
    private val _pagesState = MutableStateFlow<List<String>>(emptyList())
    val pagesState: StateFlow<List<String>> = _pagesState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // Theme state: "day" or "night"
    private val _themeState = MutableStateFlow("day")
    val themeState: StateFlow<String> = _themeState.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)

    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) {
                bookDao.getBookById(bookId)
            }
            if (book != null) {
                _bookState.value = book
                
                // Split text into pages
                val pages = splitTextIntoPages(book.content)
                _pagesState.value = pages

                // Restore saved page for this book
                val savedPage = sharedPrefs.getInt("book_page_${bookId}", 0)
                _currentPage.value = savedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            }
        }
        
        // Restore saved theme
        val savedTheme = sharedPrefs.getString("reader_theme", "day") ?: "day"
        _themeState.value = savedTheme
    }

    private fun splitTextIntoPages(content: String): List<String> {
        if (content.isEmpty()) return listOf("Документ пуст.")
        val pages = mutableListOf<String>()
        val pageSize = 1200 // standard readable chunk size
        var index = 0
        while (index < content.length) {
            val end = (index + pageSize).coerceAtMost(content.length)
            pages.add(content.substring(index, end))
            index += pageSize
        }
        return pages
    }

    fun setCurrentPage(page: Int) {
        val maxPage = (_pagesState.value.size - 1).coerceAtLeast(0)
        val clamped = page.coerceIn(0, maxPage)
        _currentPage.value = clamped
        
        // Save progress back to database as character offset
        val book = _bookState.value ?: return
        val charOffset = clamped * 1200
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookDao.updateProgress(book.id, charOffset, System.currentTimeMillis())
            }
        }
    }

    fun toggleTheme() {
        val newTheme = if (_themeState.value == "day") "night" else "day"
        _themeState.value = newTheme
        sharedPrefs.edit().putString("reader_theme", newTheme).apply()
    }

    fun saveProgress() {
        val book = _bookState.value ?: return
        sharedPrefs.edit()
            .putInt("book_page_${book.id}", _currentPage.value)
            .apply()
    }
}
