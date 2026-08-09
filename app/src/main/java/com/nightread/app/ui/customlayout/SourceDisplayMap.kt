package com.nightread.app.ui.customlayout

data class SourceDisplayMap(
    val displayToSource: IntArray,
    val sourceToDisplayStart: IntArray,
    val sourceToDisplayEnd: IntArray
) {
    fun displayToSource(displayIndex: Int): Int {
        if (displayIndex < 0) return 0
        if (displayIndex >= displayToSource.size) return displayToSource.lastOrNull() ?: 0
        return displayToSource[displayIndex]
    }
}
