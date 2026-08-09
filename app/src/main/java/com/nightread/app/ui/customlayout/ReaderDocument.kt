package com.nightread.app.ui.customlayout

data class ReaderChapter(
    val chapterIndex: Int,
    val title: String,
    val startOffset: Int,
    val endOffset: Int,
    val paragraphs: List<ReaderParagraph>
)

data class ReaderDocument(
    val bookId: String,
    val rawMainText: String,
    val paragraphs: List<ReaderParagraph>,
    val chapters: List<ReaderChapter>
)
