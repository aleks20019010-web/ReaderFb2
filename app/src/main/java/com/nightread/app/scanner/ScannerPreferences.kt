package com.nightread.app.scanner

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ScannerPreferences(private val context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_LAST_SCAN_TIME = "last_scan_time"
        private const val KEY_LAST_SCAN_COUNT = "last_scan_count"
        private const val KEY_LIBRARY_HASH = "library_hash"
        private const val KEY_TOTAL_BOOKS = "total_books"
        private const val KEY_LAST_SCAN_DURATION = "last_scan_duration"
    }
    
    fun saveLastScanTime(time: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN_TIME, time).apply()
    }
    
    fun getLastScanTime(): Long {
        return prefs.getLong(KEY_LAST_SCAN_TIME, 0)
    }
    
    fun saveLastScanCount(count: Int) {
        prefs.edit().putInt(KEY_LAST_SCAN_COUNT, count).apply()
    }
    
    fun getLastScanCount(): Int {
        return prefs.getInt(KEY_LAST_SCAN_COUNT, 0)
    }
    
    fun saveLibraryHash(hash: String) {
        prefs.edit().putString(KEY_LIBRARY_HASH, hash).apply()
    }
    
    fun getLibraryHash(): String? {
        return prefs.getString(KEY_LIBRARY_HASH, null)
    }
    
    fun saveTotalBooks(count: Int) {
        prefs.edit().putInt(KEY_TOTAL_BOOKS, count).apply()
    }
    
    fun getTotalBooks(): Int {
        return prefs.getInt(KEY_TOTAL_BOOKS, 0)
    }
    
    fun saveLastScanDuration(duration: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN_DURATION, duration).apply()
    }
    
    fun getLastScanDuration(): Long {
        return prefs.getLong(KEY_LAST_SCAN_DURATION, 0)
    }
    
    fun isLibraryChanged(currentHash: String): Boolean {
        val savedHash = getLibraryHash()
        return savedHash != currentHash
    }
    
    suspend fun calculateMediaStoreHash(): String {
        return withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    MediaStore.Files.FileColumns.SIZE
                )
                
                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IS NULL OR " +
                        "${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE ?"
                val selectionArgs = arrayOf("image/%")
                
                val cursor = try {
                    context.contentResolver.query(
                        MediaStore.Files.getContentUri("external"),
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )
                } catch (e: Throwable) {
                    null
                }
                
                cursor?.use {
                    val idColumn = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val modifiedColumn = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val sizeColumn = it.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    
                    val ids = mutableListOf<String>()
                    while (it.moveToNext()) {
                        val id = if (idColumn != -1) it.getLong(idColumn) else 0L
                        val modified = if (modifiedColumn != -1) it.getLong(modifiedColumn) else 0L
                        val size = if (sizeColumn != -1) it.getLong(sizeColumn) else 0L
                        ids.add("$id:$modified:$size")
                    }
                    
                    val input = ids.joinToString("|")
                    MessageDigest.getInstance("SHA-1")
                        .digest(input.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                } ?: ""
            } catch (e: Throwable) {
                // Fallback: hash external storage book files
                val externalStorage = Environment.getExternalStorageDirectory()
                val bookDirs = listOf("Books", "books", "Книги", "книги", "Download", "Downloads", "Загрузки", "Documents", "Документы")
                val sb = StringBuilder()
                for (dirName in bookDirs) {
                    val dir = File(externalStorage, dirName)
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { file ->
                            if (file.isFile) {
                                sb.append(file.absolutePath).append("_").append(file.length()).append("_").append(file.lastModified()).append("|")
                            }
                        }
                    }
                }
                val input = if (sb.isNotEmpty()) sb.toString() else System.currentTimeMillis().toString()
                MessageDigest.getInstance("SHA-1")
                    .digest(input.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
        }
    }
    
    fun clear() {
        prefs.edit().clear().apply()
    }
}
