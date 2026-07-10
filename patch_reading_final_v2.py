
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

        val paddingHorizontal = (16 * resources.displayMetrics.density).toInt() * 2
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()
        
        val availableWidth = width - paddingHorizontal
        val availableHeight = height - paddingVertical

        val hyphenationEnabled = com.nightread.app.data.SettingsManager.isHyphenationEnabled(this@ReadingActivity)
        val currentKey = "${width}_${height}_${paint.textSize}_${SettingsManager.getFontFamily(this@ReadingActivity)}_${SettingsManager.getFontWeightAsInt(this@ReadingActivity)}_${SettingsManager.getLineSpacing(this@ReadingActivity)}_hyphen=$hyphenationEnabled"
        if (BookCache.sha1 == sha1 && BookCache.layoutKey == currentKey && BookCache.splitResult?.isFinished == true) {"""

# Calculate padding directly from the root view or just hardcode to something small as requested.
# Request: "с минимальными отступами"
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

        // Minimal paddings: 8dp
        val paddingHorizontal = (8 * resources.displayMetrics.density).toInt() * 2
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()
        
        val availableWidth = width - paddingHorizontal
        val availableHeight = height - paddingVertical

        val hyphenationEnabled = com.nightread.app.data.SettingsManager.isHyphenationEnabled(this@ReadingActivity)
        val currentKey = "${width}_${height}_${paint.textSize}_${SettingsManager.getFontFamily(this@ReadingActivity)}_${SettingsManager.getFontWeightAsInt(this@ReadingActivity)}_${SettingsManager.getLineSpacing(this@ReadingActivity)}_hyphen=$hyphenationEnabled"
        if (BookCache.sha1 == sha1 && BookCache.layoutKey == currentKey && BookCache.splitResult?.isFinished == true) {"""

content = content.replace(old_recalc_start, new_recalc_start)

# 2. Add fix to not use PageSplitter's heuristic
# Already done in PageSplitter.kt in previous turn? 
# Wait, let me check the file PageSplitter.kt again.
# The user asks "Не использовать «поиск пробела назад на 100 символов»"
# I should re-check PageSplitter.kt content

open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w').write(content)
