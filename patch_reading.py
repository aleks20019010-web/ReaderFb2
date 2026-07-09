import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

# Replace variables
content = content.replace('private var splitResult: PageSplitter.PageResult? = null', 'private var splitResult = PageSplitter.PageResult()\n    private var progressiveJob: kotlinx.coroutines.Job? = null\n    private var isSplittingFinished = false')

# Replace loadBook to use BookCache and update progress text
load_book_regex = re.compile(r'private fun loadBook\(\) \{.*?(?=private fun preprocessTextAndHyphenate)', re.DOTALL)

new_load_book = """private fun loadBook() {
        progressBar.visibility = View.VISIBLE
        val tvLoadingProgress = findViewById<TextView>(R.id.tvLoadingProgress)
        tvLoadingProgress.visibility = View.VISIBLE
        tvLoadingProgress.text = "Загрузка книги..."

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@ReadingActivity)
            val book = withContext(Dispatchers.IO) {
                db.bookDao().getBookBySha1(sha1)
            }
            if (book == null) {
                CustomToast.show(this@ReadingActivity, "Книга не найдена в БД")
                finish()
                return@launch
            }
            bookTitle = book.title
            tvTitle.text = bookTitle

            try {
                if (BookCache.sha1 == sha1 && BookCache.content.isNotEmpty()) {
                    tvLoadingProgress.text = "Книга загружена из кэша..."
                    bookContent = BookCache.content
                } else {
                    val filePath = book.filePath
                    if (filePath.isNullOrEmpty()) {
                        CustomToast.show(this@ReadingActivity, "Путь к файлу пуст")
                        finish()
                        return@launch
                    }
                    val file = File(filePath)
                    if (!file.exists()) {
                        CustomToast.show(this@ReadingActivity, "Файл не найден на диске")
                        finish()
                        return@launch
                    }
                    tvLoadingProgress.text = "Чтение файла..."
                    Log.d("READING_DEBUG", "Loading file: $filePath, size: ${file.length()}")
                    
                    var rawContent = withContext(Dispatchers.IO) {
                        extractTextFromFile(file)
                    }
                    
                    tvLoadingProgress.text = "Обработка текста..."
                    bookContent = withContext(Dispatchers.Default) {
                        preprocessTextAndHyphenate(rawContent)
                    }
                    
                    if (bookContent.isEmpty()) {
                        CustomToast.show(this@ReadingActivity, "Не удалось прочитать текст")
                        finish()
                        return@launch
                    }
                    
                    BookCache.sha1 = sha1
                    BookCache.content = bookContent
                }

                // Wait for view to be laid out
                viewPager.post {
                    lifecycleScope.launch {
                        recalculatePages(book.currentProgressChar)
                    }
                }
            } catch (e: Exception) {
                Log.e("READING_DEBUG", "Error loading book", e)
                CustomToast.show(this@ReadingActivity, "Ошибка чтения файла")
                finish()
            }
        }
    }
"""
content = load_book_regex.sub(new_load_book, content)

# Replace recalculatePages to use progressive splitting
recalc_regex = re.compile(r'private suspend fun recalculatePages\(.*?\}', re.DOTALL)

# Let's find exactly the end of recalculatePages. It's better to just replace the function body
def replace_func(func_name, new_code):
    global content
    pattern = re.compile(r'private suspend fun ' + func_name + r'\(.*?\)( \{.*?^    \})', re.MULTILINE | re.DOTALL)
    match = pattern.search(content)
    if match:
        content = content[:match.start()] + new_code + content[match.end():]

new_recalculate = """private suspend fun recalculatePages(targetCharOffset: Int = -1) {
        if (!kotlin.coroutines.coroutineContext.isActive) return
        
        val width = viewPager.width
        val height = viewPager.height
        if (width <= 0 || height <= 0) return

        progressBar.visibility = View.VISIBLE
        val tvLoadingProgress = findViewById<TextView>(R.id.tvLoadingProgress)
        tvLoadingProgress.visibility = View.VISIBLE
        tvLoadingProgress.text = "Разбивка на страницы..."

        val paint = TextPaint().apply {
            textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.scaledDensity
            val family = SettingsManager.getFontFamily(this@ReadingActivity)
            val weight = SettingsManager.getFontWeight(this@ReadingActivity)

            val baseTypeface = when (family) {
                "Roboto" -> android.graphics.Typeface.SANS_SERIF
                "Times New Roman" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "Georgia" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "Merriweather" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "OpenDyslexic" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
                "Monospace" -> android.graphics.Typeface.MONOSPACE
                else -> android.graphics.Typeface.DEFAULT
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val numericWeight = when (weight) {
                    "Normal" -> 400
                    "Medium" -> 500
                    "Bold" -> 700
                    "ExtraBold" -> 800
                    else -> 400
                }
                typeface = android.graphics.Typeface.create(baseTypeface, numericWeight, false)
            } else {
                val style = when (weight) {
                    "Bold", "ExtraBold" -> android.graphics.Typeface.BOLD
                    else -> android.graphics.Typeface.NORMAL
                }
                typeface = android.graphics.Typeface.create(baseTypeface, style)
            }
        }

        val extraHorizontalMargin = SettingsManager.getSideMargin(this@ReadingActivity)
        val paddingHorizontal = (26 * resources.displayMetrics.density).toInt() + (extraHorizontalMargin * 2)
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()
        
        val availableWidth = width - paddingHorizontal
        val availableHeight = height - paddingVertical

        val currentKey = "${width}_${height}_${paint.textSize}_${SettingsManager.getFontFamily(this@ReadingActivity)}_${SettingsManager.getLineSpacing(this@ReadingActivity)}"
        if (BookCache.sha1 == sha1 && BookCache.layoutKey == currentKey && BookCache.splitResult?.isFinished == true) {
            splitResult = BookCache.splitResult!!
            isSplittingFinished = true
            tvLoadingProgress.visibility = View.GONE
            progressBar.visibility = View.GONE
            
            if (viewPager.adapter == null) {
                viewPager.adapter = ReaderPagerAdapter(this@ReadingActivity, splitResult.pages)
            } else {
                (viewPager.adapter as ReaderPagerAdapter).pages = splitResult.pages
                viewPager.adapter?.notifyDataSetChanged()
            }
            
            var targetPage = 0
            if (targetCharOffset >= 0) {
                targetPage = splitResult.offsets.indexOfLast { it <= targetCharOffset }.coerceAtLeast(0)
            } else {
                val currentIdx = viewPager.currentItem
                val oldOffsets = splitResult.offsets
                if (currentIdx < oldOffsets.size) {
                    val offset = oldOffsets[currentIdx]
                    targetPage = splitResult.offsets.indexOfLast { it <= offset }.coerceAtLeast(0)
                }
            }
            if (targetPage < splitResult.pages.size) {
                viewPager.setCurrentItem(targetPage, false)
            }
            updateBottomBar(targetPage)
            return
        }

        progressiveJob?.cancel()
        viewPager.adapter = ReaderPagerAdapter(this@ReadingActivity, splitResult.pages)
        isSplittingFinished = false
        var isFirstRender = true

        progressiveJob = lifecycleScope.launch {
            PageSplitter.splitTextProgressive(
                text = bookContent,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                paint = paint,
                lineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity),
                alignment = "justify"
            ) { result ->
                val oldCount = splitResult.pages.size
                splitResult = result
                isSplittingFinished = result.isFinished
                
                (viewPager.adapter as ReaderPagerAdapter).pages = result.pages
                viewPager.adapter?.notifyDataSetChanged()
                
                if (isFirstRender && result.pages.isNotEmpty()) {
                    isFirstRender = false
                    progressBar.visibility = View.GONE
                    tvLoadingProgress.visibility = View.GONE
                    
                    var targetPage = 0
                    if (targetCharOffset >= 0) {
                        targetPage = result.offsets.indexOfLast { it <= targetCharOffset }.coerceAtLeast(0)
                    } else {
                        targetPage = 0
                    }
                    if (targetPage < result.pages.size) {
                        viewPager.setCurrentItem(targetPage, false)
                    }
                    updateBottomBar(targetPage)
                } else if (result.isFinished) {
                    BookCache.layoutKey = currentKey
                    BookCache.splitResult = result
                    updateBottomBar(viewPager.currentItem)
                } else {
                    updateBottomBar(viewPager.currentItem)
                }
            }
        }
    }"""
    
replace_func('recalculatePages', new_recalculate)

# Now we need to update 'updateBottomBar'
def replace_func2(func_name, new_code):
    global content
    pattern = re.compile(r'private fun ' + func_name + r'\(.*?\)( \{.*?^    \})', re.MULTILINE | re.DOTALL)
    match = pattern.search(content)
    if match:
        content = content[:match.start()] + new_code + content[match.end():]

new_update_bottom = """private fun updateBottomBar(position: Int) {
        val total = splitResult.pages.size
        if (isSplittingFinished) {
            tvPageInfo.text = "Стр. ${position + 1} из $total"
            if (total > 1) {
                seekBar.progress = (position * 100) / (total - 1)
            } else {
                seekBar.progress = 0
            }
        } else {
            tvPageInfo.text = "Стр. ${position + 1} из $total (загрузка...)"
            seekBar.progress = 0
        }
    }"""
replace_func2('updateBottomBar', new_update_bottom)

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)
