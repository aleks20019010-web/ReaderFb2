package com.nightread.app.ui.customlayout

data class ReaderDocument(
    val bookId: String,
    val rawMainText: String,
    val paragraphs: List<ReaderParagraph>
)
