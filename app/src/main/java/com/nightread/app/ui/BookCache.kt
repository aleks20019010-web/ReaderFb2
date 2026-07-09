package com.nightread.app.ui

object BookCache {
    var sha1: String = ""
    var content: String = ""
    var layoutKey: String = ""
    var splitResult: PageSplitter.PageResult? = null

    fun clear() {
        sha1 = ""
        content = ""
        layoutKey = ""
        splitResult = null
    }
}
