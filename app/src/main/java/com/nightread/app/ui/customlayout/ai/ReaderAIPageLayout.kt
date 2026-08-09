package com.nightread.app.ui.customlayout.ai

data class ReaderAIPageLayout(
    val pageIndex: Int,
    val pageStartOffset: Int,
    val pageEndOffset: Int,
    val pageText: String,
    val htmlContent: String,
    val lineBreaks: List<Int> = emptyList(),
    val wordBreaks: List<Int> = emptyList(),
    val wordSpacingEm: Float = 0f,
    val letterSpacingEm: Float = 0f,
    val lineSpacingMultiplier: Float = 1.3f,
    val predictedLineCount: Int = 0,
    val actualLineCount: Int = 0,
    val heightUsedPx: Float = 0f,
    val safeHeightPx: Float = 0f,
    val containsImage: Boolean = false,
    val isHeading: Boolean = false,
    val isFallback: Boolean = false
)
