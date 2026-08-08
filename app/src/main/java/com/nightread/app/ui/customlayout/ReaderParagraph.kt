package com.nightread.app.ui.customlayout

data class ReaderParagraph(
    val rawText: String,
    val inlines: List<ReaderInline>,
    val globalStartOffset: Int,
    val globalEndOffset: Int
)
