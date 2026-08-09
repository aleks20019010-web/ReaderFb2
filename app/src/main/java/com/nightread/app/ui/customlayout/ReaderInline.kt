package com.nightread.app.ui.customlayout

import androidx.compose.ui.text.SpanStyle

sealed interface ReaderInline {
    data class Text(val content: String, val globalStartOffset: Int, val globalEndOffset: Int) : ReaderInline
    data class Styled(val content: String, val style: SpanStyle, val globalStartOffset: Int, val globalEndOffset: Int) : ReaderInline
    data class Image(val src: String, val alt: String?, val width: Int, val height: Int, val globalStartOffset: Int, val globalEndOffset: Int) : ReaderInline
}

