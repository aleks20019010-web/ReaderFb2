package com.nightread.app.ui

object BookCache {
    var sha1: String = ""
    var content: String = ""
    var notes: Map<String, String> = emptyMap()
    var layoutKey: String = ""
    var splitResult: TextFormatter.PageResult? = null
    var hyphenatedContent: CharSequence? = null
    var isHyphenated: Boolean? = null
    var paragraphOffsets: List<Int> = emptyList()
    var totalParagraphCount: Int = 1

    fun clear() {
        sha1 = ""
        content = ""
        notes = emptyMap()
        layoutKey = ""
        splitResult = null
        hyphenatedContent = null
        isHyphenated = null
        paragraphOffsets = emptyList()
        totalParagraphCount = 1
    }

    fun getCachedText(targetSha1: String): String? {
        return if (sha1 == targetSha1) content else null
    }
}
