package com.nightread.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookEntity
import com.nightread.app.data.SyncManager
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.nightread.app.service.TextCleaner
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val bookDao = AppDatabase.getDatabase(application).bookDao()
    private val noteDao = AppDatabase.getDatabase(application).noteDao()
    private val repository = com.nightread.app.data.BookRepository(bookDao, noteDao)
    private val syncManager = SyncManager(application)
    private val sharedPrefs = application.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
    private val appContext = application

    private val _bookState = MutableStateFlow<BookEntity?>(null)
    val bookState: StateFlow<BookEntity?> = _bookState.asStateFlow()

    private val _pagesState = MutableStateFlow<List<CharSequence>>(emptyList())
    val pagesState: StateFlow<List<CharSequence>> = _pagesState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    // Theme state: "day", "night", or "sepia"
    private val _themeState = MutableStateFlow("day")
    val themeState: StateFlow<String> = _themeState.asStateFlow()

    private var content: String = ""

    // Font size in SP
    private val _fontSizeState = MutableStateFlow(18f)
    val fontSizeState: StateFlow<Float> = _fontSizeState.asStateFlow()

    // Line spacing multiplier
    private val _lineSpacingState = MutableStateFlow(1.2f)
    val lineSpacingState: StateFlow<Float> = _lineSpacingState.asStateFlow()

    // Font family state: "Merriweather", "Roboto", "Sans Serif", "Serif", "Monospace"
    private val _fontFamilyState = MutableStateFlow("Merriweather")
    val fontFamilyState: StateFlow<String> = _fontFamilyState.asStateFlow()

    // Font weight state: 0 = Normal, 1 = Bold
    private val _fontWeightState = MutableStateFlow(0)
    val fontWeightState: StateFlow<Int> = _fontWeightState.asStateFlow()

    // Font alignment state: "justify", "left", "right", "center"
    private val _fontAlignmentState = MutableStateFlow("left")
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
        loadSettings()
        viewModelScope.launch {
            SettingsManager.settingsChanged.collect {
                loadSettings()
            }
        }
    }

    private fun loadSettings() {
        val context = appContext
        _fontSizeState.value = SettingsManager.getFontSize(context)
        _lineSpacingState.value = SettingsManager.getLineSpacing(context)
        _themeState.value = SettingsManager.getReadingTheme(context)
        _fontFamilyState.value = SettingsManager.getFontFamily(context)
        
        val weightInt = SettingsManager.getFontWeightAsInt(context)
        _fontWeightState.value = if (weightInt >= 600) 1 else 0
        
        val savedAlign = sharedPrefs.getString("saved_font_alignment", "left") ?: "left"
        _fontAlignmentState.value = if (savedAlign == "justify") "left" else savedAlign
        _pageMarginsState.value = sharedPrefs.getBoolean("saved_page_margins", true)
        _scrollDirectionState.value = SettingsManager.getPageAnimation(context)
        _twoPagesLandscapeState.value = sharedPrefs.getBoolean("saved_two_pages_landscape", false)
        
        repaginate()
    }

    fun loadBook(bookSha1: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = bookDao.getBookBySha1(bookSha1)
            if (book != null) {
                _bookState.value = book
                
                if (BookCache.sha1 == bookSha1 && BookCache.content.isNotEmpty()) {
                    content = BookCache.content
                } else {
                    val file = java.io.File(book.filePath ?: "")
                    if (file.exists()) {
                        val rawContent = if (file.extension.lowercase() == "zip") {
                            readZipFile(file)
                        } else {
                            file.readText(java.nio.charset.StandardCharsets.UTF_8)
                        }
                        content = TextCleaner.cleanText(rawContent) as String
                    } else {
                        content = ""
                    }
                    BookCache.clear()
                    BookCache.sha1 = bookSha1
                    BookCache.content = content
                }
                
                if (availableWidth > 0 && availableHeight > 0) {
                    repaginate()
                } else {
                    fallbackPagination(book)
                }
            }
        }
    }

    private fun fallbackPagination(book: BookEntity) {
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

        val savedPage = sharedPrefs.getInt("book_page_${book.sha1}", book.currentPageIndex)
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
        val newSize = (_fontSizeState.value + delta).coerceIn(12f, 48f)
        SettingsManager.setFontSize(appContext, newSize)
    }

    fun changeLineSpacing(delta: Float) {
        val newSpacing = (_lineSpacingState.value + delta).coerceIn(0.8f, 2.5f)
        SettingsManager.setLineSpacing(appContext, newSpacing)
    }

    fun setFontFamily(family: String) {
        SettingsManager.setFontFamily(appContext, family)
    }

    fun setFontWeight(weight: Int) {
        SettingsManager.setFontWeight(appContext, weight.toString())
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
        SettingsManager.setPageAnimation(appContext, direction)
    }

    fun setTwoPagesLandscape(enabled: Boolean) {
        _twoPagesLandscapeState.value = enabled
        sharedPrefs.edit().putBoolean("saved_two_pages_landscape", enabled).apply()
    }

    fun setTheme(theme: String) {
        SettingsManager.setReadingTheme(appContext, theme)
    }

    fun toggleTheme() {
        val current = SettingsManager.getReadingTheme(appContext)
        val newTheme = when (current) {
            "light", "beige" -> "sepia"
            "sepia", "sepia_contrast" -> "dark"
            "dark", "contrast" -> "amoled"
            "amoled" -> "light"
            else -> "light"
        }
        SettingsManager.setReadingTheme(appContext, newTheme)
    }

    fun repaginate() {
        if (content.isEmpty() || availableWidth <= 0 || availableHeight <= 0) return
        val book = _bookState.value ?: return

        viewModelScope.launch(Dispatchers.Default) {
            // 1. Determine current reading position (character offset)
            val currentOffset: Int = if (pageStartOffsets.isNotEmpty() && _currentPage.value < pageStartOffsets.size) {
                pageStartOffsets[_currentPage.value]
            } else {
                sharedPrefs.getInt("book_char_offset_${book.sha1}", book.currentProgressChar)
            }

            // 2. Measure and slice text into pages based on actual font parameters using PageSplitter
            val paint = android.text.TextPaint().apply {
                textSize = _fontSizeState.value * displayDensity
                
                val weightVal = SettingsManager.getFontWeightAsInt(appContext)
                val style = if (weightVal >= 600) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                val family = _fontFamilyState.value
                val fontResId = when (family) {
                    "EB Garamond" -> com.nightread.app.R.font.eb_garamond
                    "Literata" -> com.nightread.app.R.font.literata
                    "Lora" -> com.nightread.app.R.font.lora
                    else -> null
                }
                val baseTypeface = if (fontResId != null) {
                    try {
                        androidx.core.content.res.ResourcesCompat.getFont(appContext, fontResId) ?: android.graphics.Typeface.DEFAULT
                    } catch (e: Exception) {
                        android.graphics.Typeface.DEFAULT
                    }
                } else {
                    when (family) {
                        "Roboto", "Sans Serif", "OpenDyslexic" -> android.graphics.Typeface.SANS_SERIF
                        "Serif", "Times New Roman", "Georgia", "Merriweather" -> android.graphics.Typeface.SERIF
                        "Monospace" -> android.graphics.Typeface.MONOSPACE
                        else -> android.graphics.Typeface.DEFAULT
                    }
                }
                
                typeface = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    android.graphics.Typeface.create(baseTypeface, weightVal, false)
                } else {
                    android.graphics.Typeface.create(baseTypeface, style)
                }
            }

            val alignment = _fontAlignmentState.value
            val lineSpacing = _lineSpacingState.value
            val family = _fontFamilyState.value
            val weightVal = SettingsManager.getFontWeightAsInt(appContext)
            val hyphenationEnabled = SettingsManager.isHyphenationEnabled(appContext)
            
            val currentKey = "${availableWidth}_${availableHeight}_${paint.textSize}_${family}_${weightVal}_${lineSpacing}_hyphen=$hyphenationEnabled"
            
            // Try in-memory cache first
            if (BookCache.sha1 == book.sha1 && BookCache.layoutKey == currentKey && BookCache.splitResult != null) {
                val splitResult = BookCache.splitResult!!
                val offsets = splitResult.offsets
                val pages = splitResult.pages
                
                var newPageIndex = 0
                for (i in 0 until offsets.size) {
                    if (offsets[i] <= currentOffset) {
                        newPageIndex = i
                    } else {
                        break
                    }
                }
                val clampedPageIndex = newPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

                viewModelScope.launch(Dispatchers.Main) {
                    _pagesState.value = pages
                    pageStartOffsets = offsets
                    _currentPage.value = clampedPageIndex
                }
                android.util.Log.d("ReaderViewModel", "Successfully reused BookCache splitResult! Total pages: ${pages.size}")
                return@launch
            }
            
            // Try disk cache next
            val cachedOffsets = PaginationDiskCache.getOffsets(appContext, book.sha1, currentKey)
            val formattedText = TextFormatter.formatChapterSpans(appContext, content, paint.textSize)
            
            if (cachedOffsets != null) {
                val pages = ArrayList<CharSequence>()
                for (i in cachedOffsets.indices) {
                    val startIdx = cachedOffsets[i]
                    val endIdx = if (i < cachedOffsets.size - 1) cachedOffsets[i + 1] else formattedText.length
                    pages.add(formattedText.subSequence(startIdx, endIdx))
                }
                
                var newPageIndex = 0
                for (i in 0 until cachedOffsets.size) {
                    if (cachedOffsets[i] <= currentOffset) {
                        newPageIndex = i
                    } else {
                        break
                    }
                }
                val clampedPageIndex = newPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

                viewModelScope.launch(Dispatchers.Main) {
                    _pagesState.value = pages
                    pageStartOffsets = cachedOffsets
                    _currentPage.value = clampedPageIndex
                    // Warm up in-memory cache
                    BookCache.sha1 = book.sha1
                    BookCache.content = content
                    BookCache.layoutKey = currentKey
                    BookCache.splitResult = TextFormatter.PageResult(pages, ArrayList(cachedOffsets), true)
                }
                android.util.Log.d("ReaderViewModel", "Successfully loaded pagination from disk cache! Total pages: ${pages.size}")
                return@launch
            }

            val builder = com.nightread.app.ui.customlayout.TextLayoutBuilder()
                .setText(formattedText)
                .setWidth(availableWidth)
                .setHeight(availableHeight)
                .setPaint(paint)
                .setLetterSpacing(-0.02f)
                .setLineSpacing(0f, lineSpacing)
                .setFontFamily(family)
                .setFontWeight(weightVal)
                .setAlignment(when (alignment.lowercase()) {
                    "left" -> android.text.Layout.Alignment.ALIGN_NORMAL
                    "right" -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                    "center" -> android.text.Layout.Alignment.ALIGN_CENTER
                    else -> android.text.Layout.Alignment.ALIGN_NORMAL
                })
                .setJustify(alignment.lowercase() == "justify")
                
            builder.buildPagination { offsets, finished ->
                val pages = ArrayList<CharSequence>()
                for (i in offsets.indices) {
                    val startIdx = offsets[i]
                    val endIdx = if (i < offsets.size - 1) offsets[i + 1] else formattedText.length
                    pages.add(formattedText.subSequence(startIdx, endIdx))
                }
                
                // 3. Find the best matching page in the new layout
                var newPageIndex = 0
                for (i in 0 until offsets.size) {
                    if (offsets[i] <= currentOffset) {
                        newPageIndex = i
                    } else {
                        break
                    }
                }
                val clampedPageIndex = newPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

                viewModelScope.launch(Dispatchers.Main) {
                    _pagesState.value = pages
                    pageStartOffsets = offsets
                    _currentPage.value = clampedPageIndex
                    if (finished) {
                        saveProgress()
                        // Save to disk cache
                        viewModelScope.launch(Dispatchers.IO) {
                            PaginationDiskCache.saveOffsets(appContext, book.sha1, currentKey, offsets)
                        }
                        // Save to in-memory cache
                        BookCache.sha1 = book.sha1
                        BookCache.content = content
                        BookCache.layoutKey = currentKey
                        BookCache.splitResult = TextFormatter.PageResult(pages, ArrayList(offsets), true)
                    }
                }
            }
        }
    }

    fun setCurrentPage(page: Int) {
        val maxPage = (_pagesState.value.size - 1).coerceAtLeast(0)
        val clamped = page.coerceIn(0, maxPage)
        _currentPage.value = clamped
        saveProgress()
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
                bookDao.updateProgressAndPage(book.sha1, charOffset, pageIdx, book.totalCharacters, System.currentTimeMillis())
            }
        }
    }

    private fun readZipFile(file: java.io.File): String {
        java.io.FileInputStream(file).use { fis ->
            java.util.zip.ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (!entry.isDirectory && (entryName.endsWith(".fb2") || entryName.endsWith(".txt"))) {
                        return zis.bufferedReader().use { it.readText() }
                    }
                    entry = zis.nextEntry
                }
            }
        }
        return ""
    }
}
