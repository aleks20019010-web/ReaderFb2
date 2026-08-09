package com.nightread.app.ui.customlayout

data class ReaderChunk(
    val chunkIndex: Int,
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val paragraphs: List<ReaderParagraph>
)
