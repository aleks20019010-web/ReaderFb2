package com.nightread.app.ui.customlayout

enum class PaginationPriority {
    CURRENT,
    NEARBY,
    BACKGROUND
}

data class PaginationTask(
    val chapterIndex: Int,
    val priority: PaginationPriority,
    val targetOffset: Int
)
