package com.nightread.app.ui.customlayout

enum class PaginationPriority {
    CURRENT,
    NEARBY,
    BACKGROUND
}

data class PaginationTask(
    val chapterIndex: Int,
    val chunkIndex: Int,
    val priority: PaginationPriority,
    val targetOffset: Int
) : Comparable<PaginationTask> {
    override fun compareTo(other: PaginationTask): Int {
        val pComp = priority.ordinal.compareTo(other.priority.ordinal)
        if (pComp != 0) return pComp
        val chComp = chapterIndex.compareTo(other.chapterIndex)
        if (chComp != 0) return chComp
        return chunkIndex.compareTo(other.chunkIndex)
    }
}
