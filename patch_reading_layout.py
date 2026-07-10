import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_load_book = "loadBook()"
new_load_book = """loadBook()
        
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
        }"""
content = content.replace(old_load_book, new_load_book)
open(file, 'w').write(content)
