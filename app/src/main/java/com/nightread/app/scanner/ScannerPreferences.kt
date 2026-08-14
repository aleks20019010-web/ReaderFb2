package com.nightread.app.scanner

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ScannerPreferences(private val context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "ScannerPreferences"
        private const val KEY_LAST_SCAN_TIME = "last_scan_time"
        private const val KEY_LAST_SCAN_COUNT = "last_scan_count"
        private const val KEY_LIBRARY_HASH = "library_hash"
        private const val KEY_TOTAL_BOOKS = "total_books"
        private const val KEY_LAST_SCAN_DURATION = "last_scan_duration"
    }
    
    fun saveLastScanTime(time: Long) {
        try {
            prefs.edit().putLong(KEY_LAST_SCAN_TIME, time).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last scan time", e)
        }
    }
    
    fun getLastScanTime(): Long {
        return try {
            prefs.getLong(KEY_LAST_SCAN_TIME, 0)
        } catch (e: Exception) {
            0
        }
    }
    
    fun saveLastScanCount(count: Int) {
        try {
            prefs.edit().putInt(KEY_LAST_SCAN_COUNT, count).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving scan count", e)
        }
    }
    
    fun getLastScanCount(): Int {
        return try {
            prefs.getInt(KEY_LAST_SCAN_COUNT, 0)
        } catch (e: Exception) {
            0
        }
    }
    
    fun saveLibraryHash(hash: String) {
        try {
            prefs.edit().putString(KEY_LIBRARY_HASH, hash).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving library hash", e)
        }
    }
    
    fun getLibraryHash(): String? {
        return try {
            prefs.getString(KEY_LIBRARY_HASH, null)
        } catch (e: Exception) {
            null
        }
    }
    
    fun saveTotalBooks(count: Int) {
        try {
            prefs.edit().putInt(KEY_TOTAL_BOOKS, count).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving total books", e)
        }
    }
    
    fun getTotalBooks(): Int {
        return try {
            prefs.getInt(KEY_TOTAL_BOOKS, 0)
        } catch (e: Exception) {
            0
        }
    }
    
    fun saveLastScanDuration(duration: Long) {
        try {
            prefs.edit().putLong(KEY_LAST_SCAN_DURATION, duration).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving scan duration", e)
        }
    }
    
    fun getLastScanDuration(): Long {
        return try {
            prefs.getLong(KEY_LAST_SCAN_DURATION, 0)
        } catch (e: Exception) {
            0
        }
    }
    
    fun isLibraryChanged(currentHash: String): Boolean {
        return try {
            val savedHash = getLibraryHash()
            savedHash != currentHash
        } catch (e: Exception) {
            true // Если ошибка — считаем, что библиотека изменилась
        }
    }
    
    suspend fun calculateMediaStoreHash(): String {
        return withContext(Dispatchers.IO) {
            try {
                val externalStorage = Environment.getExternalStorageDirectory()
                val bookDirs = listOf(
                    "Books", "books", "Книги", "книги",
                    "Download", "Downloads", "Загрузки",
                    "Documents", "Документы",
                    "Ebooks", "eBooks", "Library", "library"
                )
                
                val dirs = mutableListOf<File>()
                
                // Добавляем только существующие и доступные директории
                for (dirName in bookDirs) {
                    try {
                        val dir = File(externalStorage, dirName)
                        if (dir.exists() && dir.isDirectory && dir.canRead()) {
                            dirs.add(dir)
                        }
                    } catch (e: Exception) {
                        // Пропускаем
                    }
                }
                
                // Добавляем app-specific директории
                try {
                    val appDirs = context.getExternalFilesDirs(null)
                    for (dir in appDirs) {
                        if (dir != null && dir.exists() && dir.canRead()) {
                            dirs.add(dir)
                        }
                    }
                    if (context.filesDir != null && context.filesDir.exists()) {
                        dirs.add(context.filesDir)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting app dirs", e)
                }
                
                // Если нет директорий — возвращаем текущее время как хеш
                if (dirs.isEmpty()) {
                    Log.w(TAG, "No scan directories found")
                    return@withContext System.currentTimeMillis().toString()
                }
                
                val sb = StringBuilder()
                var totalCount = 0
                
                for (dir in dirs) {
                    try {
                        // Используем безопасное сканирование
                        scanDirectoryForHash(dir, sb, 0)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error scanning dir for hash: ${dir.absolutePath}", e)
                    }
                }
                
                val input = if (sb.isNotEmpty()) sb.toString() else System.currentTimeMillis().toString()
                MessageDigest.getInstance("SHA-1")
                    .digest(input.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating hash", e)
                System.currentTimeMillis().toString()
            }
        }
    }
    
    /**
     * Безопасное сканирование директории для подсчета хеша
     */
    private fun scanDirectoryForHash(dir: File, sb: StringBuilder, depth: Int) {
        if (depth > 3) return // Ограничиваем глубину
        if (sb.length() > 100_000) return // Ограничиваем размер хеша
        
        try {
            // Проверяем символические ссылки
            if (dir.canonicalFile != dir.absoluteFile) return
            
            val files = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            }
            
            if (files == null) return
            
            // Обрабатываем файлы
            for (file in files) {
                if (sb.length() > 100_000) return
                
                try {
                    if (file.isFile && isBookFile(file)) {
                        sb.append(file.absolutePath)
                          .append("_")
                          .append(file.length())
                          .append("_")
                          .append(file.lastModified())
                          .append("|")
                    }
                } catch (e: Exception) {
                    // Пропускаем
                }
            }
            
            // Рекурсивно обрабатываем поддиректории
            for (subDir in files) {
                if (sb.length() > 100_000) return
                
                try {
                    if (subDir.isDirectory && !subDir.name.startsWith(".")) {
                        scanDirectoryForHash(subDir, sb, depth + 1)
                    }
                } catch (e: Exception) {
                    // Пропускаем
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanDirectoryForHash", e)
        }
    }

    private fun isBookFile(file: File): Boolean {
        return try {
            val name = file.name.lowercase()
            name.endsWith(".fb2") || name.endsWith(".fb2.zip") || name.endsWith(".fbz") ||
                    name.endsWith(".epub") || name.endsWith(".fb3") || name.endsWith(".fb3.zip") ||
                    name.endsWith(".mobi") || name.endsWith(".azw") || name.endsWith(".azw3") ||
                    name.endsWith(".zip")
        } catch (e: Exception) {
            false
        }
    }
    
    fun clear() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing prefs", e)
        }
    }
}
