package com.nightread.app.ui.customlayout

import kotlinx.coroutines.*
import java.util.PriorityQueue

enum class PaginationPriority {
    P0_CURRENT,
    P1_NEARBY,
    P2_DIRECTIONAL,
    P3_BACKGROUND
}

data class PaginationTask(
    val chapterIndex: Int,
    val chunkIndex: Int,
    val priority: PaginationPriority,
    val distance: Int // For tie-breaking within same priority
) : Comparable<PaginationTask> {
    override fun compareTo(other: PaginationTask): Int {
        val pComp = priority.ordinal.compareTo(other.priority.ordinal)
        if (pComp != 0) return pComp
        return distance.compareTo(other.distance)
    }
}

class PriorityPaginationController(
    private val document: ReaderDocument,
    private val scope: CoroutineScope,
    private val paginateChunkCallback: suspend (Int, Int) -> List<ReaderPage>,
    private val onChunkReady: (Int, Int, List<ReaderPage>) -> Unit
) {
    private val queue = PriorityQueue<PaginationTask>()
    private var workerJob: Job? = null
    private val processed = mutableSetOf<Pair<Int, Int>>()
    private val enqueued = mutableMapOf<Pair<Int, Int>, PaginationPriority>()
    
    // Call this when the user scrolls or jumps to a new page
    fun updatePriority(currentChapter: Int, currentChunk: Int, isMovingForward: Boolean) {
        workerJob?.cancel()
        
        queue.clear()
        enqueued.clear()
        
        // P0: Current
        enqueueIfNeeded(currentChapter, currentChunk, PaginationPriority.P0_CURRENT, 0)
        
        // P1: Immediate Prev/Next
        val next = getNextChunk(currentChapter, currentChunk)
        if (next != null) enqueueIfNeeded(next.first, next.second, PaginationPriority.P1_NEARBY, 1)
        val prev = getPrevChunk(currentChapter, currentChunk)
        if (prev != null) enqueueIfNeeded(prev.first, prev.second, PaginationPriority.P1_NEARBY, 1)
        
        // P2: Directional (3-5 chunks ahead)
        var dirFocus = if (isMovingForward) next else prev
        var dist = 2
        for (i in 0 until 4) {
            if (dirFocus == null) break
            enqueueIfNeeded(dirFocus.first, dirFocus.second, PaginationPriority.P2_DIRECTIONAL, dist++)
            dirFocus = if (isMovingForward) getNextChunk(dirFocus.first, dirFocus.second) else getPrevChunk(dirFocus.first, dirFocus.second)
        }
        
        // P3: Background (everything else)
        for (chIdx in document.chapters.indices) {
            val ch = document.chapters[chIdx]
            for (chunkIdx in ch.chunks.indices) {
                enqueueIfNeeded(chIdx, chunkIdx, PaginationPriority.P3_BACKGROUND, 1000 + Math.abs(chIdx - currentChapter))
            }
        }
        
        startWorker()
    }
    
    private fun enqueueIfNeeded(ch: Int, chunk: Int, priority: PaginationPriority, dist: Int) {
        val key = Pair(ch, chunk)
        if (!processed.contains(key) && enqueued[key] != priority) {
            enqueued[key] = priority
            queue.add(PaginationTask(ch, chunk, priority, dist))
        }
    }
    
    private fun startWorker() {
        workerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val task = synchronized(queue) { queue.poll() } ?: break
                val key = Pair(task.chapterIndex, task.chunkIndex)
                if (processed.contains(key)) continue
                
                // Process
                ReaderMetrics.jobStarted()
                try {
                    val pages = paginateChunkCallback(task.chapterIndex, task.chunkIndex)
                    processed.add(key)
                    withContext(Dispatchers.Main) {
                        onChunkReady(task.chapterIndex, task.chunkIndex, pages)
                    }
                } catch (e: CancellationException) {
                    ReaderMetrics.logPaginationCancelled(task.chapterIndex, task.chunkIndex)
                    throw e
                } finally {
                    ReaderMetrics.jobFinished()
                }
            }
        }
    }
    
    private fun getNextChunk(ch: Int, chunk: Int): Pair<Int, Int>? {
        val chapter = document.chapters.getOrNull(ch) ?: return null
        if (chunk + 1 < chapter.chunks.size) return Pair(ch, chunk + 1)
        if (ch + 1 < document.chapters.size) return Pair(ch + 1, 0)
        return null
    }
    
    private fun getPrevChunk(ch: Int, chunk: Int): Pair<Int, Int>? {
        if (chunk - 1 >= 0) return Pair(ch, chunk - 1)
        if (ch - 1 >= 0) return Pair(ch - 1, document.chapters[ch - 1].chunks.size - 1)
        return null
    }
}
