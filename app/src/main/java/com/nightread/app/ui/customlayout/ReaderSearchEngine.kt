package com.nightread.app.ui.customlayout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReaderSearchResult(
    val sourceStartOffset: Int,
    val sourceEndOffset: Int,
    val contextBefore: String,
    val matchedText: String,
    val contextAfter: String
)

class ReaderSearchEngine(private val rawMainText: String) {

    suspend fun search(
        query: String,
        ignoreCase: Boolean = true
    ): List<ReaderSearchResult> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()
        
        val results = mutableListOf<ReaderSearchResult>()
        var currentIndex = 0
        
        while (currentIndex < rawMainText.length) {
            val index = rawMainText.indexOf(query, currentIndex, ignoreCase = ignoreCase)
            if (index == -1) break
            
            val startOffset = index
            val endOffset = index + query.length
            
            // Extract context (e.g., 30 chars before and after, but truncate if needed)
            val contextBeforeStart = maxOf(0, startOffset - 40)
            val contextAfterEnd = minOf(rawMainText.length, endOffset + 40)
            
            val contextBefore = rawMainText.substring(contextBeforeStart, startOffset).replace('\n', ' ')
            val matchedText = rawMainText.substring(startOffset, endOffset).replace('\n', ' ')
            val contextAfter = rawMainText.substring(endOffset, contextAfterEnd).replace('\n', ' ')
            
            results.add(
                ReaderSearchResult(
                    sourceStartOffset = startOffset,
                    sourceEndOffset = endOffset,
                    contextBefore = contextBefore,
                    matchedText = matchedText,
                    contextAfter = contextAfter
                )
            )
            
            currentIndex = startOffset + 1
        }
        
        results
    }
}
