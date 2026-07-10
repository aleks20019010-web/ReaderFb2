import sys

content = open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt').read()

# 1. Update recalculatePages
old_recalc_start = """    private suspend fun recalculatePages(targetCharOffset: Int = -1) {
        if (!kotlin.coroutines.coroutineContext.isActive) return
        
        var resolvedCharOffset = targetCharOffset
        if (resolvedCharOffset < 0 && splitResult != null) {
            val currentIdx = viewPager.currentItem
            if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                resolvedCharOffset = splitResult.offsets[currentIdx]
            }
        }
        
        val width = viewPager.width
        val height = viewPager.height
        if (width <= 0 || height <= 0) return

        progressBar.visibility = View.VISIBLE
        val tvLoadingProgress = findViewById<TextView>(R.id.tvLoadingProgress)
        tvLoadingProgress.visibility = View.VISIBLE
        tvLoadingProgress.text = "Разбивка на страницы..."

        val paint = TextPaint().apply {
            textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.density
            val family = SettingsManager.getFontFamily(this@ReadingActivity)
            val numericWeight = SettingsManager.getFontWeightAsInt(this@ReadingActivity)
            typeface = FontUtils.createTypeface(family, numericWeight)
        }

        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()
        
        val availableWidth = width
        val availableHeight = height - paddingVertical

        val currentKey = "${width}_${height}_${paint.textSize}_${SettingsManager.getFontFamily(this@ReadingActivity)}_${SettingsManager.getFontWeightAsInt(this@ReadingActivity)}_${SettingsManager.getLineSpacing(this@ReadingActivity)}"
        if (BookCache.sha1 == sha1 && BookCache.layoutKey == currentKey && BookCache.splitResult?.isFinished == true) {"""

new_recalc_start = """    private suspend fun recalculatePages(targetCharOffset: Int = -1) {
        if (!kotlin.coroutines.coroutineContext.isActive) return
        
        var resolvedCharOffset = targetCharOffset
        if (resolvedCharOffset < 0 && splitResult != null) {
            val currentIdx = viewPager.currentItem
            if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                resolvedCharOffset = splitResult.offsets[currentIdx]
            }
        }
        
        val width = viewPager.width
        val height = viewPager.height
        if (width <= 0 || height <= 0) return

        progressBar.visibility = View.VISIBLE
        val tvLoadingProgress = findViewById<TextView>(R.id.tvLoadingProgress)
        tvLoadingProgress.visibility = View.VISIBLE
        tvLoadingProgress.text = "Разбивка на страницы..."

        val paint = TextPaint().apply {
            textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.density
            val family = SettingsManager.getFontFamily(this@ReadingActivity)
            val numericWeight = SettingsManager.getFontWeightAsInt(this@ReadingActivity)
            typeface = FontUtils.createTypeface(family, numericWeight)
        }

        val paddingHorizontal = (16 * resources.displayMetrics.density).toInt() * 2
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()
        
        val availableWidth = width - paddingHorizontal
        val availableHeight = height - paddingVertical

        val hyphenationEnabled = com.nightread.app.data.SettingsManager.isHyphenationEnabled(this@ReadingActivity)
        val currentKey = "${width}_${height}_${paint.textSize}_${SettingsManager.getFontFamily(this@ReadingActivity)}_${SettingsManager.getFontWeightAsInt(this@ReadingActivity)}_${SettingsManager.getLineSpacing(this@ReadingActivity)}_hyphen=$hyphenationEnabled"
        if (BookCache.sha1 == sha1 && BookCache.layoutKey == currentKey && BookCache.splitResult?.isFinished == true) {"""

content = content.replace(old_recalc_start, new_recalc_start)

# 2. Update progressiveJob launch to apply hyphenation if needed
old_launch = """        progressiveJob = lifecycleScope.launch {
            PageSplitter.splitTextProgressive(
                text = bookContent,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                paint = paint,
                lineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity),
                alignment = "justify"
            ) { result ->"""

new_launch = """        val textToSplit = if (hyphenationEnabled) {
            com.nightread.app.ui.HyphenationPatterns.load("ru")
            com.nightread.app.ui.HyphenatorHelper.hyphenate(bookContent)
        } else {
            bookContent
        }

        progressiveJob = lifecycleScope.launch {
            PageSplitter.splitTextProgressive(
                text = textToSplit,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                paint = paint,
                lineSpacing = SettingsManager.getLineSpacing(this@ReadingActivity),
                alignment = "justify"
            ) { result ->"""

content = content.replace(old_launch, new_launch)

# 3. Clean up the unused preprocessTextAndHyphenate function
unused_func = """    private fun preprocessTextAndHyphenate(text: String): String {
        var processedText = text.replace(Regex("([ \t\r\n]*\n[ \t\r\n]*)+"), "\n    ")
        processedText = processedText.trim().trim('\u000C').trim()
        HyphenationPatterns.load("ru")
        val result = com.nightread.app.ui.HyphenatorHelper.hyphenate(processedText)
        val hyphensInResult = result.count { it == '\u00AD' }
        Log.d("ReadingActivity", "preprocessTextAndHyphenate: original text length: ${text.length}, processed length: ${result.length}, contains $hyphensInResult soft hyphens.")
        Log.d("ReadingActivity", "Sample: ${result.take(200)}")
        return result
    }
"""
content = content.replace(unused_func, "")

open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w').write(content)
