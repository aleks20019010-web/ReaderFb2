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
import kotlinx.coroutines.flow.asSharedFlow
import com.nightread.app.service.TextCleaner
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce

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

    private val _fontSettingsChanged = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fontSettingsChanged = _fontSettingsChanged.asSharedFlow()

    // Local dimension tracking
    private var availableWidth = 0
    private var availableHeight = 0
    private var displayDensity = 1.0f

    // Character start offsets of all pages
    private var pageStartOffsets = listOf<Int>()
    private var repaginateJob: kotlinx.coroutines.Job? = null

    var paragraphOffsets: List<Int> = emptyList()
    var totalParagraphCount: Int = 1

    fun getParagraphIndexFromOffset(offset: Int): Int {
        val offsets = paragraphOffsets
        if (offsets.isEmpty()) return 0
        var low = 0
        var high = offsets.size - 1
        var ans = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (offsets[mid] <= offset) {
                ans = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return ans
    }

    fun getPageForOffset(offset: Int): Int {
        if (pageStartOffsets.isEmpty()) return 0
        
        var pageIdx = 0
        for (i in pageStartOffsets.indices) {
            if (pageStartOffsets[i] <= offset) {
                pageIdx = i
            } else {
                break
            }
        }
        return pageIdx
    }

    fun getOffsetForPage(page: Int): Int {
        if (pageStartOffsets.isEmpty() || page < 0 || page >= pageStartOffsets.size) return 0
        return pageStartOffsets[page]
    }

    fun getContentText(): String = content

    fun getOffsetForParagraphIndex(pIndex: Int): Int {
        val offsets = paragraphOffsets
        if (offsets.isEmpty()) return 0
        if (pIndex in offsets.indices) {
            return offsets[pIndex]
        }
        return offsets.last()
    }

    init {
        loadSettings()
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            SettingsManager.settingsChanged
                .debounce(150)
                .collect {
                    loadSettings()
                }
        }
    }

    private fun loadSettings() {
        val context = appContext
        val newFontSize = SettingsManager.getFontSize(context)
        val newLineSpacing = SettingsManager.getLineSpacing(context)
        val newTheme = SettingsManager.getReadingTheme(context)
        val newFontFamily = SettingsManager.getFontFamily(context)
        
        val weightInt = SettingsManager.getFontWeightAsInt(context)
        val newFontWeight = if (weightInt >= 600) 1 else 0
        
        val newFontAlignment = sharedPrefs.getString("saved_font_alignment", "justify") ?: "justify"
        val newPageMargins = sharedPrefs.getBoolean("saved_page_margins", true)
        val newScrollDirection = SettingsManager.getPageAnimation(context)
        val newTwoPagesLandscape = sharedPrefs.getBoolean("saved_two_pages_landscape", false)
        
        var changed = false
        if (_fontSizeState.value != newFontSize) changed = true
        if (_lineSpacingState.value != newLineSpacing) changed = true
        if (_fontFamilyState.value != newFontFamily) changed = true
        if (_fontWeightState.value != newFontWeight) changed = true
        if (_fontAlignmentState.value != newFontAlignment) changed = true
        if (_pageMarginsState.value != newPageMargins) changed = true
        if (_twoPagesLandscapeState.value != newTwoPagesLandscape) changed = true
        
        _fontSizeState.value = newFontSize
        _lineSpacingState.value = newLineSpacing
        _themeState.value = newTheme
        _fontFamilyState.value = newFontFamily
        _fontWeightState.value = newFontWeight
        _fontAlignmentState.value = newFontAlignment
        _pageMarginsState.value = newPageMargins
        _scrollDirectionState.value = newScrollDirection
        _twoPagesLandscapeState.value = newTwoPagesLandscape
        
        if (changed) {
            _fontSettingsChanged.tryEmit(Unit)
            repaginate()
        }
    }

    private var pendingTargetOffset: Int = -1

    fun loadBook(bookSha1: String, targetOffset: Int = -1) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = bookDao.getBookBySha1(bookSha1)
            if (book != null) {
                pendingTargetOffset = targetOffset
                
                val isWebView = book.filePath?.endsWith(".fb2", true) == true || 
                                book.filePath?.endsWith(".fb2.zip", true) == true || 
                                book.filePath?.endsWith(".zip", true) == true ||
                                book.filePath?.endsWith(".epub", true) == true

                if (BookCache.sha1 == bookSha1 && BookCache.content.isNotEmpty() && BookCache.paragraphOffsets.isNotEmpty()) {
                    content = BookCache.content
                    paragraphOffsets = BookCache.paragraphOffsets
                    totalParagraphCount = BookCache.totalParagraphCount
                } else {
                    if (BookCache.sha1 == bookSha1 && BookCache.content.isNotEmpty()) {
                        content = BookCache.content
                    } else {
                        val file = java.io.File(book.filePath ?: "")
                        if (file.exists()) {
                            val rawContent = if (file.extension.lowercase() == "zip") {
                                readZipFile(file)
                            } else if (file.extension.lowercase() == "epub") {
                                com.nightread.app.service.EpubParser.parse(file, file.nameWithoutExtension).content
                            } else {
                                file.readText(java.nio.charset.StandardCharsets.UTF_8)
                            }
                            
                            if (file.extension.lowercase() == "fb2" || file.extension.lowercase() == "zip" || file.extension.lowercase() == "epub") {
                                content = rawContent
                            } else {
                                content = TextCleaner.cleanText(rawContent) as String
                            }
                        } else {
                            content = ""
                        }
                    }
                    
                    if (isWebView && content.isNotEmpty()) {
                        val offsets = mutableListOf<Int>()
                        var i = 0
                        val len = content.length
                        while (i < len) {
                            val nextTagStart = content.indexOf('<', i)
                            if (nextTagStart == -1) break
                            i = nextTagStart + 1
                            if (i < len && content[i] != '/') {
                                var endNameIdx = i
                                while (endNameIdx < len && content[endNameIdx] != ' ' && content[endNameIdx] != '>' && content[endNameIdx] != '\t' && content[endNameIdx] != '\n' && content[endNameIdx] != '\r') {
                                    endNameIdx++
                                }
                                if (endNameIdx > i && (endNameIdx - i) <= 8) {
                                    val tagName = content.substring(i, endNameIdx).lowercase()
                                    if (tagName == "p" || tagName == "title" || tagName == "subtitle" || 
                                        tagName == "h1" || tagName == "h2" || tagName == "h3" || 
                                        tagName == "h4" || tagName == "h5" || tagName == "h6") {
                                        offsets.add(nextTagStart)
                                    }
                                }
                            }
                        }
                        paragraphOffsets = offsets
                        totalParagraphCount = paragraphOffsets.size.coerceAtLeast(1)
                    } else {
                        paragraphOffsets = emptyList()
                        totalParagraphCount = 1
                    }
                    
                    BookCache.clear()
                    BookCache.sha1 = bookSha1
                    BookCache.content = content
                    BookCache.paragraphOffsets = paragraphOffsets
                    BookCache.totalParagraphCount = totalParagraphCount
                }
                
                val finalBook = if (targetOffset != -1 && isWebView) {
                    book.copy(currentProgressChar = getParagraphIndexFromOffset(targetOffset))
                } else {
                    book
                }

                withContext(Dispatchers.Main) {
                    _bookState.value = finalBook
                    _currentPage.value = finalBook.currentPageIndex
                }
                
                if (availableWidth > 0 && availableHeight > 0) {
                    if (isWebView) {
                        _pagesState.value = listOf("WEBVIEW_CONTENT_${System.currentTimeMillis()}")
                    } else {
                        repaginate()
                    }
                } else {
                    fallbackPagination(finalBook)
                }
            }
        }
    }

    private fun fallbackPagination(book: BookEntity) {
        if (content.isEmpty()) {
            _pagesState.value = listOf("[BOOK_COVER]", "Документ пуст.")
            pageStartOffsets = listOf(0, 0)
            return
        }

        val pages = mutableListOf<String>()
        val offsets = mutableListOf<Int>()
        
        pages.add("[BOOK_COVER]")
        offsets.add(0)

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

        val savedPage = book.currentPageIndex
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

        // Clear the PageSplitter layout cache for fresh pagination
        com.nightread.app.ui.customlayout.PageSplitter.clearCache()

        if (book.filePath?.endsWith(".fb2", true) == true || 
            book.filePath?.endsWith(".fb2.zip", true) == true || 
            book.filePath?.endsWith(".zip", true) == true ||
            book.filePath?.endsWith(".epub", true) == true) {
            
            // For WebView books, we reset pagesState to trigger a reload in Activity, 
            // but we MUST NOT reset _currentPage to 0 if it was already set.
            // Activity will use the current _currentPage value to scroll the WebView after reload.
            _pagesState.value = listOf("WEBVIEW_CONTENT_${System.currentTimeMillis()}")
            return
        }

        // Take snapshot of current state before launching async calculation
        val currentOffsetsSnapshot = ArrayList(pageStartOffsets)
        val currentPageSnapshot = _currentPage.value
        val savedOffset = book.currentProgressChar
        val savedPage = book.currentPageIndex

        repaginateJob?.cancel()
        repaginateJob = viewModelScope.launch(Dispatchers.Default) {
            // 1. Determine current reading position (character offset)
            val currentOffset: Int = if (pendingTargetOffset != -1) {
                pendingTargetOffset
            } else if (currentOffsetsSnapshot.isNotEmpty() && currentPageSnapshot < currentOffsetsSnapshot.size && currentPageSnapshot > 0) {
                currentOffsetsSnapshot[currentPageSnapshot]
            } else if (savedOffset > 0) {
                savedOffset
            } else {
                -1
            }
            
            // clear the pending target offset so subsequent repaginations (like font changes) keep the current page
            pendingTargetOffset = -1

            // 2. Measure and slice text into pages based on actual font parameters using PageSplitter
            val paint = android.text.TextPaint().apply {
                textSize = _fontSizeState.value * displayDensity
                textLocale = java.util.Locale("ru", "RU")
                
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
            
            val currentKey = "${availableWidth}_${availableHeight}_${paint.textSize}_${family}_${weightVal}_${lineSpacing}_align=${alignment}_hyphen=$hyphenationEnabled"
            
            // Try in-memory cache first
            if (BookCache.sha1 == book.sha1 && BookCache.layoutKey == currentKey && BookCache.splitResult != null) {
                val splitResult = BookCache.splitResult!!
                val offsets = splitResult.offsets
                val pages = splitResult.pages
                
                var newPageIndex = 0
                if (currentOffset == -1) {
                    newPageIndex = 0
                } else {
                    for (i in 0 until offsets.size) {
                        if (offsets[i] <= currentOffset) {
                            newPageIndex = i
                        } else {
                            break
                        }
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
            val textToFormat = if (hyphenationEnabled) {
                com.nightread.app.ui.HyphenatorHelper.hyphenate(content, appContext, paint)
            } else {
                content
            }
            val formattedText = TextFormatter.formatChapterSpans(appContext, textToFormat, paint.textSize)
            
            if (cachedOffsets != null) {
                var cacheValid = true
                for (offset in cachedOffsets) {
                    if (offset < 0 || offset > formattedText.length) {
                        cacheValid = false
                        break
                    }
                }
                if (cacheValid && cachedOffsets.isNotEmpty()) {
                    for (i in 0 until cachedOffsets.size - 1) {
                        if (cachedOffsets[i] >= cachedOffsets[i + 1]) {
                            cacheValid = false
                            break
                        }
                    }
                }
                
                if (cacheValid && cachedOffsets.isNotEmpty()) {
                    val pages = ArrayList<CharSequence>()
                    val finalOffsets = ArrayList<Int>()
                    try {
                        // Prepend cover page
                        pages.add("[BOOK_COVER]")
                        finalOffsets.add(0)
                        
                        for (i in cachedOffsets.indices) {
                            val startIdx = cachedOffsets[i]
                            val endIdx = if (i < cachedOffsets.size - 1) cachedOffsets[i + 1] else formattedText.length
                            pages.add(formattedText.subSequence(startIdx, endIdx))
                            finalOffsets.add(startIdx)
                        }
                        
                        var newPageIndex = 0
                        if (currentOffset == -1) {
                            newPageIndex = 0
                        } else {
                            for (i in 0 until finalOffsets.size) {
                                if (finalOffsets[i] <= currentOffset) {
                                    newPageIndex = i
                                } else {
                                    break
                                }
                            }
                        }
                        val clampedPageIndex = newPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

                        viewModelScope.launch(Dispatchers.Main) {
                            _pagesState.value = pages
                            pageStartOffsets = finalOffsets
                            _currentPage.value = clampedPageIndex
                            // Warm up in-memory cache
                            BookCache.sha1 = book.sha1
                            BookCache.content = content
                            BookCache.layoutKey = currentKey
                            BookCache.splitResult = TextFormatter.PageResult(pages, finalOffsets, true)
                        }
                        android.util.Log.d("ReaderViewModel", "Successfully loaded pagination from disk cache! Total pages: ${pages.size}")
                        return@launch
                    } catch (e: Exception) {
                        android.util.Log.e("ReaderViewModel", "Disk cache offsets invalid on subSequence", e)
                    }
                } else {
                    android.util.Log.w("ReaderViewModel", "Disk cache offsets out of bounds or invalid, ignoring cache.")
                }
            }

            val isJustify = alignment.lowercase() == "justify"
            val builder = com.nightread.app.ui.customlayout.TextLayoutBuilder()
                .setText(formattedText)
                .setWidth(availableWidth)
                .setHeight(availableHeight)
                .setPaint(paint)
                .setLetterSpacing(if (isJustify) 0.01f else -0.02f)
                .setLineSpacing(0f, lineSpacing)
                .setFontFamily(family)
                .setFontWeight(weightVal)
                .setAlignment(when (alignment.lowercase()) {
                    "left" -> android.text.Layout.Alignment.ALIGN_NORMAL
                    "right" -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                    "center" -> android.text.Layout.Alignment.ALIGN_CENTER
                    else -> android.text.Layout.Alignment.ALIGN_NORMAL
                })
                .setJustify(isJustify)
                
            builder.buildPagination { offsets, finished ->
                val pages = ArrayList<CharSequence>()
                val finalOffsets = ArrayList<Int>()
                
                // Prepend cover page
                pages.add("[BOOK_COVER]")
                finalOffsets.add(0)
                
                for (i in offsets.indices) {
                    val startIdx = offsets[i]
                    val endIdx = if (i < offsets.size - 1) offsets[i + 1] else formattedText.length
                    // Для частичных результатов используем остаток текста
                    val actualEnd = if (endIdx > formattedText.length) formattedText.length else endIdx
                    pages.add(formattedText.subSequence(startIdx, actualEnd))
                    finalOffsets.add(startIdx)
                }
                
                // 3. Find the best matching page in the new layout
                var newPageIndex = 0
                if (currentOffset == -1) {
                    newPageIndex = 0
                } else {
                    for (i in 0 until finalOffsets.size) {
                        if (finalOffsets[i] <= currentOffset) {
                            newPageIndex = i
                        } else {
                            break
                        }
                    }
                }
                val clampedPageIndex = newPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

                viewModelScope.launch(Dispatchers.Main) {
                    _pagesState.value = pages
                    pageStartOffsets = finalOffsets
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
                        BookCache.splitResult = TextFormatter.PageResult(pages, finalOffsets, true)

                        // Trigger pre-rendering
                        viewModelScope.launch(Dispatchers.Default) {
                            com.nightread.app.ui.customlayout.PageSplitter.startBackgroundRendering(
                                pages.map { listOf(it.toString()) },
                                _currentPage.value,
                                paint,
                                availableWidth,
                                com.nightread.app.ui.customlayout.PageSplitter.createStaticLayout(formattedText, 0, formattedText.length, paint, availableWidth, com.nightread.app.ui.customlayout.PageSplitter.createStaticLayout(formattedText, 0, formattedText.length, paint, availableWidth, android.text.Layout.Alignment.ALIGN_NORMAL, lineSpacing, 0f, hyphenationEnabled).alignment, lineSpacing, 0f, hyphenationEnabled).alignment,
                                lineSpacing,
                                0f,
                                hyphenationEnabled,
                                isJustify
                            )
                        }
                    }
                }
            }
        }
    }

    fun setWebViewPageCount(count: Int) {
        val dummyList = ArrayList<CharSequence>()
        dummyList.add("[BOOK_COVER]")
        for (i in 0 until count.coerceAtLeast(1)) {
            dummyList.add("WEBVIEW_PAGE_$i")
        }
        _pagesState.value = dummyList
    }

    fun setCurrentPage(page: Int) {
        val pages = _pagesState.value
        val isWebViewBook = pages.any { it.toString().startsWith("WEBVIEW_CONTENT") || it.toString().startsWith("WEBVIEW_PAGE_") }
        
        if (isWebViewBook) {
            // For WebView books, the pagesState might temporarily have size 1 during repagination.
            // We avoid clamping to 0 if it was already set to a higher value.
            _currentPage.value = page.coerceAtLeast(0)
        } else {
            val maxPage = (pages.size - 1).coerceAtLeast(0)
            _currentPage.value = page.coerceIn(0, maxPage)
        }
        saveProgress()
    }



    fun updateWebViewParagraphProgress(pIndex: Int) {
        val book = _bookState.value ?: return
        
        val totalParagraphs = BookCache.totalParagraphCount

        _bookState.value = book.copy(
            currentProgressChar = pIndex,
            currentPageIndex = _currentPage.value,
            totalCharacters = totalParagraphs,
            lastReadTime = System.currentTimeMillis()
        )

        sharedPrefs.edit()
            .putInt("book_page_${book.sha1}", _currentPage.value)
            .putInt("book_char_offset_${book.sha1}", pIndex)
            .apply()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookDao.updateProgressAndPage(
                    book.sha1,
                    pIndex,
                    _currentPage.value,
                    totalParagraphs,
                    System.currentTimeMillis()
                )
            }
        }
    }

    fun setWebViewPageRestored(pageIndex: Int) {
        _currentPage.value = pageIndex
        val book = _bookState.value ?: return
        
        sharedPrefs.edit()
            .putInt("book_page_${book.sha1}", pageIndex)
            .apply()

        _bookState.value = book.copy(
            currentPageIndex = pageIndex,
            lastReadTime = System.currentTimeMillis()
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookDao.updateProgressAndPage(
                    book.sha1,
                    book.currentProgressChar,
                    pageIndex,
                    book.totalCharacters,
                    System.currentTimeMillis()
                )
            }
        }
    }

    fun saveProgress() {
        val book = _bookState.value ?: return
        val pageIdx = _currentPage.value
        
        val isWebViewBook = book.filePath?.endsWith(".fb2", true) == true || 
                            book.filePath?.endsWith(".fb2.zip", true) == true || 
                            book.filePath?.endsWith(".zip", true) == true ||
                            book.filePath?.endsWith(".epub", true) == true

        if (isWebViewBook) {
            val savedParagraphIndex = book.currentProgressChar
            val totalParagraphs = BookCache.totalParagraphCount
            
            _bookState.value = book.copy(
                currentPageIndex = pageIdx,
                currentProgressChar = savedParagraphIndex,
                totalCharacters = totalParagraphs,
                lastReadTime = System.currentTimeMillis()
            )

            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    bookDao.updateProgressAndPage(book.sha1, savedParagraphIndex, pageIdx, totalParagraphs, System.currentTimeMillis())
                }
            }
            return
        }

        val charOffset = if (pageStartOffsets.isNotEmpty() && pageIdx < pageStartOffsets.size) {
            pageStartOffsets[pageIdx]
        } else {
            book.currentProgressChar
        }

        sharedPrefs.edit()
            .putInt("book_page_${book.sha1}", pageIdx)
            .putInt("book_char_offset_${book.sha1}", charOffset)
            .apply()

        _bookState.value = book.copy(
            currentPageIndex = pageIdx,
            currentProgressChar = charOffset,
            lastReadTime = System.currentTimeMillis()
        )

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

    fun getNotesForBook(bookSha1: String): kotlinx.coroutines.flow.Flow<List<com.nightread.app.data.NoteEntity>> {
        return repository.getNotesForBook(bookSha1)
    }

    fun addNote(selectedText: String, noteText: String) {
        val book = _bookState.value ?: return
        val offset = getOffsetForPage(_currentPage.value)
        viewModelScope.launch(Dispatchers.IO) {
            val note = com.nightread.app.data.NoteEntity(
                bookId = book.sha1,
                bookTitle = book.title ?: "Unknown",
                selectedText = selectedText,
                noteText = noteText,
                charOffset = offset
            )
            repository.insertNote(note)
        }
    }

    fun deleteNote(noteId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            noteDao.deleteNoteById(noteId)
        }
    }
}
