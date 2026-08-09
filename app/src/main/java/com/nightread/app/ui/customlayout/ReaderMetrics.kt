package com.nightread.app.ui.customlayout

import android.util.Log

object ReaderMetrics {
    var isEnabled = true
    
    // Instrumentation states
    private var startTimeMs: Long = 0L
    private var firstVisiblePageLogged = false
    private var firstPaginatedChunkLogged = false
    
    // Counters
    private var cacheHits = 0
    private var cacheMisses = 0
    private var cancelledJobs = 0
    private var activeJobs = 0
    private var maxConcurrentJobs = 0
    
    // Memory
    private var peakMemoryBytes: Long = 0L
    
    // Throughput
    private var totalChunksPaginated = 0
    private var totalPaginationTimeMs = 0L

    fun startSession() {
        startTimeMs = System.currentTimeMillis()
        firstVisiblePageLogged = false
        firstPaginatedChunkLogged = false
        cacheHits = 0
        cacheMisses = 0
        cancelledJobs = 0
        activeJobs = 0
        maxConcurrentJobs = 0
        peakMemoryBytes = 0L
        totalChunksPaginated = 0
        totalPaginationTimeMs = 0L
    }

    fun onVisiblePagesUpdated(pageCount: Int) {
        if (!firstVisiblePageLogged && pageCount > 0) {
            firstVisiblePageLogged = true
            val time = System.currentTimeMillis() - startTimeMs
            if (isEnabled) Log.i("ReaderMetrics", "FIRST_PAGE_READY in ${time}ms")
        }
    }

    fun jobStarted() {
        activeJobs++
        if (activeJobs > maxConcurrentJobs) maxConcurrentJobs = activeJobs
    }

    fun jobFinished() {
        activeJobs--
    }

    fun logFirstPageReady(timeMs: Long) {
        if (isEnabled) Log.i("ReaderMetrics", "FIRST_PAGE_READY in ${timeMs}ms")
    }

    fun logChunkReady(ch: Int, ck: Int, timeMs: Long, source: String) {
        if (!firstPaginatedChunkLogged && source == "paginate") {
            firstPaginatedChunkLogged = true
            val totalTime = System.currentTimeMillis() - startTimeMs
            if (isEnabled) Log.i("ReaderMetrics", "FIRST_PAGINATED_CHUNK_READY in ${totalTime}ms")
        }
        if (source == "paginate") {
            totalChunksPaginated++
            totalPaginationTimeMs += timeMs
        }
        
        val mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        if (mem > peakMemoryBytes) peakMemoryBytes = mem
        
        if (isEnabled) Log.d("ReaderMetrics", "CHUNK_READY [$ch, $ck] in ${timeMs}ms (source: $source)")
    }

    fun logCacheHit(ch: Int, ck: Int, timeMs: Long) {
        cacheHits++
        if (isEnabled) Log.d("ReaderMetrics", "CACHE_HIT [$ch, $ck] in ${timeMs}ms")
    }

    fun logCacheMiss(ch: Int, ck: Int) {
        cacheMisses++
        if (isEnabled) Log.d("ReaderMetrics", "CACHE_MISS [$ch, $ck]")
    }

    fun logPaginationCancelled(ch: Int, ck: Int) {
        cancelledJobs++
        if (isEnabled) Log.d("ReaderMetrics", "PAGINATION_CANCELLED [$ch, $ck]")
    }

    fun logCacheWrite(timeMs: Long) {
        if (isEnabled) Log.d("ReaderMetrics", "CACHE_WRITE_TIME ${timeMs}ms")
    }

    fun logPageTurn(isNext: Boolean, timeMs: Long) {
        val dir = if (isNext) "next" else "previous"
        if (isEnabled) Log.d("ReaderMetrics", "TIME_TO_PAGE_TURN ($dir) in ${timeMs}ms")
    }

    fun logFullBookReady(timeMs: Long, pageCount: Int) {
        if (isEnabled) Log.i("ReaderMetrics", "FULL_BOOK_READ in ${timeMs}ms (total pages: $pageCount)")
    }
    
    fun dumpSessionMetrics() {
        if (!isEnabled) return
        val throughput = if (totalPaginationTimeMs > 0) {
            (totalChunksPaginated * 1000f) / totalPaginationTimeMs
        } else 0f
        
        Log.i("ReaderMetrics", "=== METRICS DUMP ===")
        Log.i("ReaderMetrics", "Cache Hits: $cacheHits")
        Log.i("ReaderMetrics", "Cache Misses: $cacheMisses")
        Log.i("ReaderMetrics", "Cancelled Jobs: $cancelledJobs")
        Log.i("ReaderMetrics", "Peak Concurrent Jobs: $maxConcurrentJobs")
        Log.i("ReaderMetrics", "Peak Memory (MB): ${peakMemoryBytes / 1024 / 1024}")
        Log.i("ReaderMetrics", "Background Throughput (chunks/sec): $throughput")
        Log.i("ReaderMetrics", "Total Pagination Time: ${totalPaginationTimeMs}ms")
        Log.i("ReaderMetrics", "====================")
    }
}
