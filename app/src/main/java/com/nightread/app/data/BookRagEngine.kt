package com.nightread.app.data

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import java.util.Locale

data class RagChunk(
    val index: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val text: String,
    val normalizedText: String
)

data class RagSearchResult(
    val chunkIndex: Int,
    val startCharOffset: Int,
    val pageIndex: Int,
    val snippet: CharSequence,
    val rawText: String,
    val score: Double,
    val matchCount: Int
)

object BookRagEngine {
    private const val CHUNK_SIZE = 350
    private const val CHUNK_OVERLAP = 70

    private var cachedSha1: String = ""
    private var cachedChunks: List<RagChunk> = emptyList()

    fun indexBook(sha1: String, fullText: String): List<RagChunk> {
        if (sha1 == cachedSha1 && cachedChunks.isNotEmpty()) {
            return cachedChunks
        }

        if (fullText.isEmpty()) {
            cachedSha1 = sha1
            cachedChunks = emptyList()
            return emptyList()
        }

        val chunks = mutableListOf<RagChunk>()
        var offset = 0
        val textLength = fullText.length
        var chunkIndex = 0

        while (offset < textLength) {
            val end = (offset + CHUNK_SIZE).coerceAtMost(textLength)
            var actualEnd = end

            if (actualEnd < textLength) {
                val boundary = fullText.indexOfAny(charArrayOf('\n', '.', '!', '?'), startIndex = (actualEnd - 40).coerceAtLeast(offset))
                if (boundary in (offset + 100)..actualEnd) {
                    actualEnd = boundary + 1
                }
            }

            val chunkText = fullText.substring(offset, actualEnd)
            val normalized = chunkText.lowercase(Locale.ROOT)

            chunks.add(
                RagChunk(
                    index = chunkIndex++,
                    startCharOffset = offset,
                    endCharOffset = actualEnd,
                    text = chunkText,
                    normalizedText = normalized
                )
            )

            if (actualEnd >= textLength) break
            offset = (actualEnd - CHUNK_OVERLAP).coerceAtLeast(offset + 1)
        }

        cachedSha1 = sha1
        cachedChunks = chunks
        return chunks
    }

    fun search(
        sha1: String,
        fullText: String,
        query: String,
        pageResolver: (Int) -> Int,
        maxResults: Int = 60
    ): List<RagSearchResult> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) return emptyList()

        val chunks = indexBook(sha1, fullText)
        if (chunks.isEmpty()) return emptyList()

        val queryLower = trimmedQuery.lowercase(Locale.ROOT)
        val queryTokens = queryLower.split(Regex("[\\s,.:;!\"'()-]+"))
            .filter { it.length >= 2 }
            .distinct()

        val results = mutableListOf<RagSearchResult>()

        for (chunk in chunks) {
            var score = 0.0
            var matchCount = 0

            // 1. Exact phrase match boost
            if (chunk.normalizedText.contains(queryLower)) {
                score += 100.0
                matchCount += 1
            }

            // 2. Token matches
            var matchedTokensCount = 0
            for (token in queryTokens) {
                if (chunk.normalizedText.contains(token)) {
                    matchedTokensCount++
                    val occurrences = countOccurrences(chunk.normalizedText, token)
                    score += occurrences * 10.0
                    matchCount += occurrences
                }
            }

            if (matchedTokensCount == 0 && score == 0.0) continue

            // Coverage ratio bonus
            if (queryTokens.isNotEmpty()) {
                val coverage = matchedTokensCount.toDouble() / queryTokens.size
                score += coverage * 50.0
            }

            val pageIdx = pageResolver(chunk.startCharOffset)
            val snippet = createHighlightedSnippet(chunk.text, queryLower, queryTokens)

            results.add(
                RagSearchResult(
                    chunkIndex = chunk.index,
                    startCharOffset = chunk.startCharOffset,
                    pageIndex = pageIdx,
                    snippet = snippet,
                    rawText = chunk.text,
                    score = score,
                    matchCount = matchCount
                )
            )
        }

        return results.sortedByDescending { it.score }.take(maxResults)
    }

    private fun countOccurrences(text: String, target: String): Int {
        var count = 0
        var pos = 0
        while (pos < text.length) {
            val index = text.indexOf(target, pos)
            if (index == -1) break
            count++
            pos = index + target.length
        }
        return count
    }

    private fun createHighlightedSnippet(
        text: String,
        queryLower: String,
        queryTokens: List<String>
    ): CharSequence {
        val textLower = text.lowercase(Locale.ROOT)
        var firstMatch = textLower.indexOf(queryLower)
        if (firstMatch == -1) {
            for (token in queryTokens) {
                val idx = textLower.indexOf(token)
                if (idx != -1) {
                    firstMatch = idx
                    break
                }
            }
        }

        if (firstMatch == -1) firstMatch = 0

        val start = (firstMatch - 30).coerceAtLeast(0)
        val end = (firstMatch + 130).coerceAtMost(text.length)

        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""

        val rawSnippet = prefix + text.substring(start, end) + suffix
        val spannable = SpannableStringBuilder(rawSnippet)
        val snippetLower = rawSnippet.lowercase(Locale.ROOT)

        val highlightColor = 0xFFFFC107.toInt() // Warm amber highlight

        for (token in queryTokens) {
            var searchPos = 0
            while (searchPos < snippetLower.length) {
                val foundPos = snippetLower.indexOf(token, searchPos)
                if (foundPos == -1) break

                val endPos = (foundPos + token.length).coerceAtMost(snippetLower.length)
                spannable.setSpan(
                    ForegroundColorSpan(highlightColor),
                    foundPos,
                    endPos,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    foundPos,
                    endPos,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                searchPos = foundPos + token.length
            }
        }

        return spannable
    }

    fun clearCache() {
        cachedSha1 = ""
        cachedChunks = emptyList()
    }
}
