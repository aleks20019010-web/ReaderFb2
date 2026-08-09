package com.nightread.app.ui.customlayout

import android.content.Context
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.unit.sp

class ReaderPager(
    val document: ReaderDocument,
    val config: ReaderConfiguration,
    val viewport: ReaderViewport,
    val textMeasurer: TextMeasurer,
    val context: Context,
    val layoutKey: String,
    val scope: CoroutineScope,
    val initialTargetOffset: Int = 0
) {
    private val _pages = MutableStateFlow<List<ReaderPage>>(emptyList())
    val pages: StateFlow<List<ReaderPage>> = _pages.asStateFlow()
    
    private val materializedChunks = mutableMapOf<Pair<Int, Int>, List<ReaderPage>>()
    
    private var currentChapter = 0
    private var currentChunk = 0
    
    val controller = PriorityPaginationController(
        document = document,
        scope = scope,
        paginateChunkCallback = { ch, ck ->
            val startMs = System.currentTimeMillis()
            val chunk = document.chapters[ch].chunks[ck]
            
            val textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = config.fontSize,
                fontFamily = config.fontFamily,
                fontWeight = config.fontWeight,
                textAlign = androidx.compose.ui.text.style.TextAlign.Justify,
                lineHeight = (config.fontSize.value * config.lineSpacing).sp,
                letterSpacing = 0.1.sp,
                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
                hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
            )
            
            val cached = com.nightread.app.ui.PaginationDiskCache.getChapterChunkPages(context, document.bookId, layoutKey, ch, ck, chunk, config.fontSize)
            if (cached != null) {
                ReaderMetrics.logCacheHit(ch, ck, System.currentTimeMillis() - startMs)
                cached
            } else {
                ReaderMetrics.logCacheMiss(ch, ck)
                val pgs = ReaderLayoutEngine.paginateChunkPublic(
                    context, document.bookId, layoutKey, ch, chunk, textMeasurer, textStyle, viewport.widthPx, viewport.heightPx
                )
                com.nightread.app.ui.PaginationDiskCache.saveChapterChunkPages(context, document.bookId, layoutKey, ch, ck, pgs)
                ReaderMetrics.logChunkReady(ch, ck, System.currentTimeMillis() - startMs, "paginate")
                pgs
            }
        },
        onChunkReady = { ch, ck, pages ->
            materializedChunks[Pair(ch, ck)] = pages
            updateVisiblePages()
        }
    )
    
    init {
        goToOffset(initialTargetOffset)
    }
    
    fun goToOffset(offset: Int) {
        var foundCh = 0
        var foundCk = 0
        outer@ for ((chIdx, ch) in document.chapters.withIndex()) {
            if (offset in ch.startOffset..ch.endOffset) {
                foundCh = chIdx
                for ((ckIdx, ck) in ch.chunks.withIndex()) {
                    if (offset in ck.startOffset..ck.endOffset) {
                        foundCk = ckIdx
                        break@outer
                    }
                }
            }
        }
        
        currentChapter = foundCh
        currentChunk = foundCk
        
        controller.updatePriority(currentChapter, currentChunk, true)
    }
    
    fun notifyPageChanged(pageOffset: Int) {
        // Find which chunk this offset belongs to
        goToOffset(pageOffset)
    }

    fun currentSourceOffset(): Int {
        val currentList = _pages.value
        if (currentList.isEmpty()) return initialTargetOffset
        // we assume the page at index 0 of contiguousPages or the middle one is the current one
        // Wait, how does ReaderComposeScreen track current page? 
        return 0 // We'll fix this based on how pager is used.
    }
    
    private fun updateVisiblePages() {
        val contiguousPages = mutableListOf<ReaderPage>()
        
        var ch = currentChapter
        var ck = currentChunk
        val backwardPages = mutableListOf<ReaderPage>()
        while (true) {
            val key = Pair(ch, ck)
            val chunkPages = materializedChunks[key]
            if (chunkPages != null) {
                if (ch != currentChapter || ck != currentChunk) {
                    backwardPages.addAll(0, chunkPages)
                }
            } else {
                break
            }
            if (ck - 1 >= 0) ck -= 1
            else if (ch - 1 >= 0) { ch -= 1; ck = document.chapters[ch].chunks.size - 1 }
            else break
        }
        
        contiguousPages.addAll(backwardPages)
        
        ch = currentChapter
        ck = currentChunk
        while (true) {
            val key = Pair(ch, ck)
            val chunkPages = materializedChunks[key]
            if (chunkPages != null) {
                contiguousPages.addAll(chunkPages)
            } else {
                break
            }
            if (ck + 1 < document.chapters[ch].chunks.size) ck += 1
            else if (ch + 1 < document.chapters.size) { ch += 1; ck = 0 }
            else break
        }
        
        val finalPages = contiguousPages.mapIndexed { idx, page ->
            page.copy(pageIndex = idx)
        }
        
        _pages.value = finalPages
        ReaderMetrics.onVisiblePagesUpdated(finalPages.size)
    }
}
