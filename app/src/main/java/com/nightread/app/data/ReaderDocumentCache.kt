package com.nightread.app.data

import android.util.LruCache
import com.nightread.app.ui.customlayout.ReaderDocument

/**
 * Fast in-memory LRU cache for parsed book contents and reader structures.
 * Provides instant (<10ms) reopening for recently read books.
 */
object ReaderDocumentCache {
    private const val MAX_CACHE_ENTRIES = 5
    
    // In-memory LRU cache for raw extracted text/HTML content
    private val rawContentCache = object : LruCache<String, String>(MAX_CACHE_ENTRIES) {}
    
    // In-memory LRU cache for structured ReaderDocuments
    private val documentCache = object : LruCache<String, ReaderDocument>(MAX_CACHE_ENTRIES) {}

    @Synchronized
    fun getRawContent(sha1OrPath: String): String? {
        if (sha1OrPath.isEmpty()) return null
        return rawContentCache.get(sha1OrPath)
    }

    @Synchronized
    fun putRawContent(sha1OrPath: String, content: String) {
        if (sha1OrPath.isNotEmpty() && content.isNotEmpty()) {
            rawContentCache.put(sha1OrPath, content)
        }
    }

    @Synchronized
    fun getDocument(key: String): ReaderDocument? {
        if (key.isEmpty()) return null
        return documentCache.get(key)
    }

    @Synchronized
    fun putDocument(key: String, document: ReaderDocument) {
        if (key.isNotEmpty()) {
            documentCache.put(key, document)
        }
    }

    @Synchronized
    fun clear() {
        rawContentCache.evictAll()
        documentCache.evictAll()
    }
}
