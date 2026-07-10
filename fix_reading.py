import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

bad_str = """    private fun loadBook()
        
        var lastWidth = 0
        var lastHeight = 0
        viewPager.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight) && lastWidth != 0 && lastHeight != 0) {
                lastWidth = w
                lastHeight = h
                if (bookContent.isNotEmpty() && isSplittingFinished) {
                    val currentIdx = viewPager.currentItem
                    val targetOffset = if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                        splitResult.offsets[currentIdx]
                    } else {
                        -1
                    }
                    lifecycleScope.launch {
                        recalculatePages(targetOffset)
                    }
                }
            } else if (w > 0 && h > 0) {
                lastWidth = w
                lastHeight = h
            }
        } {"""
content = content.replace(bad_str, "    private fun loadBook() {")

# Now properly insert it inside onCreate before loadBook()
good_str = """
        var lastWidth = 0
        var lastHeight = 0
        viewPager.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight) && lastWidth != 0 && lastHeight != 0) {
                lastWidth = w
                lastHeight = h
                if (bookContent.isNotEmpty() && isSplittingFinished) {
                    val currentIdx = viewPager.currentItem
                    val targetOffset = if (currentIdx >= 0 && currentIdx < splitResult.offsets.size) {
                        splitResult.offsets[currentIdx]
                    } else {
                        -1
                    }
                    lifecycleScope.launch {
                        recalculatePages(targetOffset)
                    }
                }
            } else if (w > 0 && h > 0) {
                lastWidth = w
                lastHeight = h
            }
        }
        
        loadBook()"""
content = content.replace("        loadBook()\n    }\n\n    private fun loadBook() {", good_str + "\n    }\n\n    private fun loadBook() {")
open(file, 'w').write(content)
