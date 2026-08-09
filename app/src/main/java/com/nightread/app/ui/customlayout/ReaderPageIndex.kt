package com.nightread.app.ui.customlayout

data class PageIndexEntry(
    val pageIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val chapterIndex: Int
)

class ReaderPageIndex(
    private val entries: List<PageIndexEntry>
) {
    fun getPage(index: Int): PageIndexEntry? = entries.getOrNull(index)

    fun findPageByOffset(offset: Int): Int {
        if (entries.isEmpty()) return 0
        for ((idx, entry) in entries.withIndex()) {
            if (offset in entry.startOffset..entry.endOffset) {
                return idx
            }
        }
        var closest = 0
        var minDiff = Int.MAX_VALUE
        for ((idx, entry) in entries.withIndex()) {
            val diff = kotlin.math.abs(entry.startOffset - offset)
            if (diff < minDiff) {
                minDiff = diff
                closest = idx
            }
        }
        return closest
    }

    fun pageCount(): Int = entries.size

    fun allEntries(): List<PageIndexEntry> = entries
}
