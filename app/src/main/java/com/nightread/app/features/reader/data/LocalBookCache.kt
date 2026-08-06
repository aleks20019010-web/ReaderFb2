package com.nightread.app.features.reader.data

import android.content.Context
import android.util.LruCache

class LocalBookCache(context: Context) {

    private val pageCache = LruCache<String, List<String>>(10)

    fun getCachedPages(bookSha1: String, fontSize: Float): List<String>? {
        val key = "${bookSha1}_$fontSize"
        return pageCache.get(key)
    }

    fun putCachedPages(bookSha1: String, fontSize: Float, pages: List<String>) {
        val key = "${bookSha1}_$fontSize"
        pageCache.put(key, pages)
    }

    fun clearCache() {
        pageCache.evictAll()
    }
}
