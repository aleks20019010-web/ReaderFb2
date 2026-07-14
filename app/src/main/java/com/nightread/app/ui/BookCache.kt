package com.nightread.app.ui

object BookCache {
    var sha1: String = ""
    var content: String = ""
    var notes: Map<String, String> = emptyMap()
    var layoutKey: String = ""
    var splitResult: TextFormatter.PageResult? = null
    var hyphenatedContent: CharSequence? = null
    var isHyphenated: Boolean? = null

    fun clear() {
        sha1 = ""
        content = ""
        notes = emptyMap()
        layoutKey = ""
        splitResult = null
        hyphenatedContent = null
        isHyphenated = null
    }

    fun getCachedText(targetSha1: String): String? {
        return if (sha1 == targetSha1) content else null
    }
}
