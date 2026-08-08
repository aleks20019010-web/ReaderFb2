package com.nightread.app.ui.customlayout

import androidx.compose.ui.text.AnnotatedString

data class ReaderPage(
    val pageIndex: Int,
    val text: AnnotatedString,
    val startOffset: Int,
    val endOffset: Int
)
