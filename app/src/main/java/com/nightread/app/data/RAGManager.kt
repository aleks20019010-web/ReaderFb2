package com.nightread.app.data

import android.content.Context
import java.util.Locale

data class BookChunk(
    val id: Int,
    val text: String,
    val charOffset: Int,
    val progressPercent: Int
)

object RAGManager {
    private const val CHUNK_SIZE = 350
    private const val CHUNK_OVERLAP = 50

    private val indexedChunks = mutableListOf<BookChunk>()

    fun indexBook(bookText: String, totalLength: Int = bookText.length) {
        synchronized(indexedChunks) {
            indexedChunks.clear()
            val normalized = LocalAiEngine.normalizeText(bookText)
            if (normalized.isBlank()) return

            var start = 0
            var id = 0
            while (start < normalized.length) {
                val end = (start + CHUNK_SIZE).coerceAtMost(normalized.length)
                val chunkText = normalized.substring(start, end).trim()
                if (chunkText.isNotBlank()) {
                    val progress = if (totalLength > 0) ((start.toDouble() / totalLength) * 100).toInt() else 0
                    indexedChunks.add(BookChunk(id++, chunkText, start, progress))
                }
                if (end >= normalized.length) break
                start += (CHUNK_SIZE - CHUNK_OVERLAP)
            }
        }
    }

    fun searchWithProgress(query: String, maxProgressPercent: Int, topK: Int = 5): List<String> {
        val cleanQuery = query.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9\\s]"), "")
            .trim()
        val keywords = cleanQuery.split("\\s+".toRegex()).filter { it.length >= 3 }

        synchronized(indexedChunks) {
            val eligibleChunks = indexedChunks.filter { it.progressPercent <= maxProgressPercent }
            if (eligibleChunks.isEmpty()) {
                return indexedChunks.take(topK).map { it.text }
            }

            val scored = eligibleChunks.map { chunk ->
                val lowerText = chunk.text.lowercase(Locale.ROOT)
                var score = 0
                if (keywords.isNotEmpty()) {
                    for (kw in keywords) {
                        if (lowerText.contains(kw)) score += 2
                    }
                } else if (lowerText.contains(cleanQuery)) {
                    score += 5
                }
                Pair(chunk, score)
            }

            return scored.sortedByDescending { it.second }
                .take(topK)
                .map { it.first.text }
        }
    }

    fun search(query: String, topK: Int = 5): List<String> {
        return searchWithProgress(query, maxProgressPercent = 100, topK = topK)
    }

    fun searchRAG(context: Context, book: BookEntity, query: String, topK: Int = 5): List<String> {
        val sampleText = LocalAiEngine.getBookSampleText(book)
        if (sampleText.isNotBlank()) {
            indexBook(sampleText)
        }
        return search(query, topK)
    }

    fun formatSearchResults(results: List<String>): String {
        if (results.isEmpty()) return ""
        return results.mapIndexed { index, text ->
            "Отрывок ${index + 1}:\n\"$text\""
        }.joinToString("\n\n")
    }

    fun getChunksCount(): Int = synchronized(indexedChunks) { indexedChunks.size }
}
