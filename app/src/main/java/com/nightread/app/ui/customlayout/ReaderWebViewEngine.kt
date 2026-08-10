package com.nightread.app.ui.customlayout

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.AnnotatedString

data class ReaderWebViewPage(
    val pageIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val htmlContent: String
)

object ReaderWebViewEngine {
    private const val TAG = "ReaderWebViewEngine"

    fun prepareHtmlForBook(
        context: Context?,
        bookId: String,
        mainText: String,
        fontFamily: String,
        fontSize: Float,
        fontWeight: Float,
        lineHeight: Float,
        textColorHex: String,
        bgColorHex: String,
        viewportWidth: Int,
        viewportHeight: Int,
        pageAnimation: String = "slide",
        topPadding: Int = 0,
        bottomPadding: Int = 20,
        leftPadding: Int = 8,
        rightPadding: Int = 8
    ): String {
        Log.d(TAG, "Preparing HTML for book: id=$bookId, length=${mainText.length}, viewport=${viewportWidth}x$viewportHeight, anim=$pageAnimation")
        return ReaderWebViewPaginator.sanitizeAndWrapHtml(
            rawText = mainText,
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            textColorHex = textColorHex,
            bgColorHex = bgColorHex,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            pageAnimation = pageAnimation,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            leftPadding = leftPadding,
            rightPadding = rightPadding
        )
    }

    fun findOffsetForPage(pages: List<ReaderWebViewPage>, pageIndex: Int): Int {
        return if (pageIndex in pages.indices) pages[pageIndex].startOffset else 0
    }

    fun findPageForOffset(pages: List<ReaderWebViewPage>, targetOffset: Int): Int {
        for (i in pages.indices) {
            val page = pages[i]
            if (targetOffset >= page.startOffset && targetOffset <= page.endOffset) {
                return i
            }
        }
        return 0
    }
}
