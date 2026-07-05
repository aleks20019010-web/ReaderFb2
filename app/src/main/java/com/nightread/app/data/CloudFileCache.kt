package com.nightread.app.data

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class CloudFileEntry(
    val name: String,
    val size: Long,
    val modified: String,
    val sha1: String
)

@JsonClass(generateAdapter = true)
data class CloudCacheData(
    val entries: MutableMap<String, CloudFileEntry> = mutableMapOf()
)

object CloudFileCache {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(CloudCacheData::class.java)

    private fun getCacheFile(context: Context): File {
        return File(context.filesDir, "cloud_file_cache.json")
    }

    suspend fun loadCache(context: Context): CloudCacheData = withContext(Dispatchers.IO) {
        val file = getCacheFile(context)
        if (!file.exists()) return@withContext CloudCacheData()
        return@withContext try {
            adapter.fromJson(file.readText()) ?: CloudCacheData()
        } catch (e: Exception) {
            CloudCacheData()
        }
    }

    suspend fun saveCache(context: Context, data: CloudCacheData) = withContext(Dispatchers.IO) {
        try {
            getCacheFile(context).writeText(adapter.toJson(data))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
