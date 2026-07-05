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
    private val sharedPrefs = application.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)

    private val _bookState = MutableStateFlow<BookEntity?>(null)
    val bookState: StateFlow<BookEntity?> = _bookState.asStateFlow()

    private val _pagesState = MutableStateFlow<List<String>>(emptyList())
    val pagesState: StateFlow<List<String>> = _pagesState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // Theme state: "day", "night", or "sepia"
    private val _themeState = MutableStateFlow("day")
    val themeState: StateFlow<String> = _themeState.asStateFlow()

    // Font size in SP
    private val _fontSizeState = MutableStateFlow(18f)
    val fontSizeState: StateFlow<Float> = _fontSizeState.asStateFlow()

    // Line spacing multiplier
    private val _lineSpacingState = MutableStateFlow(1.4f)
    val lineSpacingState: StateFlow<Float> = _lineSpacingState.asStateFlow()

    // Font family state: "Merriweather", "Roboto", "Sans Serif", "Serif", "Monospace"
    private val _fontFamilyState = MutableStateFlow("Merriweather")
    val fontFamilyState: StateFlow<String> = _fontFamilyState.asStateFlow()

    // Font weight state: 0 = Normal, 1 = Bold
    private val _fontWeightState = MutableStateFlow(0)
    val fontWeightState: StateFlow<Int> = _fontWeightState.asStateFlow()

    // Font alignment state: "justify", "left", "right", "center"
    private val _fontAlignmentState = MutableStateFlow("justify")
    val fontAlignmentState: StateFlow<String> = _fontAlignmentState.asStateFlow()

    // Page margins state: true / false
    private val _pageMarginsState = MutableStateFlow(true)
    val pageMarginsState: StateFlow<Boolean> = _pageMarginsState.asStateFlow()

    // Scroll direction: "Горизонтально" / "Вертикально"
    private val _scrollDirectionState = MutableStateFlow("Горизонтально")
    val scrollDirectionState: StateFlow<String> = _scrollDirectionState.asStateFlow()

    // Two pages in landscape: true / false
    private val _twoPagesLandscapeState = MutableStateFlow(false)
    val twoPagesLandscapeState: StateFlow<Boolean> = _twoPagesLandscapeState.asStateFlow()

    // Local dimension tracking
    private var availableWidth = 0
    private var availableHeight = 0
    private var displayDensity = 1.0f

    // Character start offsets of all pages
    private var pageStartOffsets = listOf<Int>()

    init {
        // Restore saved settings
        _fontSizeState.value = sharedPrefs.getFloat("saved_font_size", 18f)
        _lineSpacingState.value = sharedPrefs.getFloat("saved_line_spacing", 1.4f)
        _themeState.value = sharedPrefs.getString("reader_theme", "day") ?: "day"
        _fontFamilyState.value = sharedPrefs.getString("saved_font_family", "Merriweather") ?: "Merriweather"
        _fontWeightState.value = sharedPrefs.getInt("saved_font_weight", 0)
        _fontAlignmentState.value = sharedPrefs.getString("saved_font_alignment", "justify") ?: "justify"
        _pageMarginsState.value = sharedPrefs.getBoolean("saved_page_margins", true)
        _scrollDirectionState.value = sharedPrefs.getString("saved_scroll_direction", "Горизонтально") ?: "Горизонтально"
        _twoPagesLandscapeState.value = sharedPrefs.getBoolean("saved_two_pages_landscape", false)
    }

    fun loadBook(bookSha1: String) {
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) {
                bookDao.getBookBySha1(bookSha1)
            }
            if (book != null) {
                _bookState.value = book
                
                // If dimensions are already measured, trigger initial pagination
                if (availableWidth > 0 && availableHeight > 0) {
                    repaginate()
                } else {
                    // Fallback to static 1200 chars/page until first layout measure
                    fallbackPagination(book)
                }
            }
        }
    }

    private fun fallbackPagination(book: BookEntity) {
        val content = book.content ?: ""
        if (content.isEmpty()) {
            _pagesState.value = listOf("Документ пуст.")
            pageStartOffsets = listOf(0)
            return
        }

        val pages = mutableListOf<String>()
        val offsets = mutableListOf<Int>()
        val pageSize = 1200
        var index = 0
        while (index < content.length) {
            offsets.add(index)
            val end = (index + pageSize).coerceAtMost(content.length)
            pages.add(content.substring(index, end))
            index += pageSize
        }

        _pagesState.value = pages
        pageStartOffsets = offsets

        val savedPage = sharedPrefs.getInt("book_page_${book.sha1}", 0)
        _currentPage.value = savedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    }

    fun updateDimensions(width: Int, height: Int, density: Float) {
        if (availableWidth != width || availableHeight != height || displayDensity != density) {
            availableWidth = width
            availableHeight = height
            displayDensity = density
            repaginate()
        }
    }

    fun changeFontSize(delta: Float) {
        val newSize = (_fontSizeState.value + delta).coerceIn(12f, 48f) // Extend limit to 48 as in screenshot
        _fontSizeState.value = newSize
        sharedPrefs.edit().putFloat("saved_font_size", newSize).apply()
        repaginate()
    }

    fun changeLineSpacing(delta: Float) {
        val newSpacing = (_lineSpacingState.value + delta).coerceIn(0.8f, 2.5f) // Wider spacing limits
        _lineSpacingState.value = newSpacing
        sharedPrefs.edit().putFloat("saved_line_spacing", newSpacing).apply()
        repaginate()
    }

    fun setFontFamily(family: String) {
        _fontFamilyState.value = family
        sharedPrefs.edit().putString("saved_font_family", family).apply()
        repaginate()
    }

    fun setFontWeight(weight: Int) {
        _fontWeightState.value = weight
        sharedPrefs.edit().putInt("saved_font_weight", weight).apply()
        repaginate()
    }

    fun setFontAlignment(alignment: String) {
        _fontAlignmentState.value = alignment
        sharedPrefs.edit().putString("saved_font_alignment", alignment).apply()
        repaginate()
    }

    fun setPageMargins(enabled: Boolean) {
        _pageMarginsState.value = enabled
        sharedPrefs.edit().putBoolean("saved_page_margins", enabled).apply()
        repaginate()
    }

    fun setScrollDirection(direction: String) {
        _scrollDirectionState.value = direction
        sharedPrefs.edit().putString("saved_scroll_direction", direction).apply()
    }

    fun setTwoPagesLandscape(enabled: Boolean) {
        _twoPagesLandscapeState.value = enabled
        sharedPrefs.edit().putBoolean("saved_two_pages_landscape", enabled).apply()
    }

    fun setTheme(theme: String) {
        _themeState.value = theme
        sharedPrefs.edit().putString("reader_theme", theme).apply()
    }

    fun repaginate() {
        val book = _bookState.value ?: return
        val text = book.content ?: ""
        if (text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) return

        viewModelScope.launch(Dispatchers.Default) {
            // 1. Determine current reading position (character offset)
            val currentOffset = if (pageStartOffsets.isNotEmpty() && _currentPage.value < pageStartOffsets.size) {
                pageStartOffsets[_currentPage.value]
            } else {
                sharedPrefs.getInt("book_char_offset_${book.sha1}", book.currentProgressChar)
            }

            // 2. Measure and slice text into pages based on actual font parameters using PageSplitter
            val paint = android.text.TextPaint().apply {
                textSize = _fontSizeState.value * displayDensity
                
                val style = if (_fontWeightState.value == 1) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                typeface = when (_fontFamilyState.value) {
                    "Roboto", "Sans Serif" -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, style)
                    "Serif" -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, style)
                    "Monospace" -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, style)
                    "Merriweather" -> android.graphics.Typeface.create("serif", style)
                    else -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, style)
                }
            }

            val alignment = _fontAlignmentState.value
            val lineSpacing = _lineSpacingState.value
            
            val result = PageSplitter.splitText(
                text = text,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                paint = paint,
                lineSpacing = lineSpacing,
                alignment = alignment
            )

            val pages = result.pages
            val offsets = result.offsets

            // 3. Find the best matching page in the new layout
            var newPageIndex = 0
            for (i in offsets.indices) {
                if (offsets[i] <= currentOffset) {
                    newPageIndex = i
                } else {
                    break
                }
            }

            val clampedPageIndex = newPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

            // 4. Update state safely on Main Thread
            withContext(Dispatchers.Main) {
                _pagesState.value = pages
                pageStartOffsets = offsets
                _currentPage.value = clampedPageIndex
                saveProgress()
            }
        }
    }

    fun setCurrentPage(page: Int) {
        val maxPage = (_pagesState.value.size - 1).coerceAtLeast(0)
        val clamped = page.coerceIn(0, maxPage)
        _currentPage.value = clamped
        saveProgress()
    }

    fun toggleTheme() {
        val newTheme = when (_themeState.value) {
            "day" -> "night"
            "night" -> "sepia"
            else -> "day"
        }
        _themeState.value = newTheme
        sharedPrefs.edit().putString("reader_theme", newTheme).apply()
    }

    fun saveProgress() {
        val book = _bookState.value ?: return
        val pageIdx = _currentPage.value
        val charOffset = if (pageStartOffsets.isNotEmpty() && pageIdx < pageStartOffsets.size) {
            pageStartOffsets[pageIdx]
        } else {
            book.currentProgressChar
        }

        sharedPrefs.edit()
            .putInt("book_page_${book.sha1}", pageIdx)
            .putInt("book_char_offset_${book.sha1}", charOffset)
            .apply()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookDao.updateProgress(book.sha1, charOffset, System.currentTimeMillis())
            }
        }
    }
}
