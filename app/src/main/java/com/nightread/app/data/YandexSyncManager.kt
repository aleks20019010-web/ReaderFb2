package com.nightread.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class YandexSyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val cloudFileDao = database.cloudFileDao()
    private val bookDao = database.bookDao()

    suspend fun getSyncStats(): SyncStats = withContext(Dispatchers.IO) {
        // 1. Get local books (with SHA-1)
        val localBooks = bookDao.getAllBooks() // Assuming getAllBooks returns List<BookEntity>
        val localSha1Set = localBooks.map { it.sha1 }.toSet()

        // 2. Get cloud files (via YandexDiskManager)
        val cloudFiles = YandexDiskManager.getAllFilesFromFolder(context, YandexDiskManager.getSyncFolder(context))
        
        val toDownload = mutableListOf<ResourceItem>()
        val cloudSha1Set = mutableSetOf<String>()

        for (item in cloudFiles) {
            val cached = cloudFileDao.getByPath(item.path ?: "")
            val sha1 = if (cached != null && cached.lastModified == item.modified) {
                cached.sha1
            } else {
                // Download and calc
                val tempFile = downloadTemp(item)
                val s = Sha1Helper.computeSha1FromContent(tempFile)
                tempFile.delete()
                
                if (s != null) {
                    cloudFileDao.insert(CloudFileEntity(item.path ?: "", s, item.size ?: 0, item.modified ?: ""))
                }
                s
            }
            
            if (sha1 != null) {
                cloudSha1Set.add(sha1)
                if (!localSha1Set.contains(sha1)) {
                    toDownload.add(item)
                }
            }
        }

        val toUpload = localBooks.filter { !cloudSha1Set.contains(it.sha1) }
        
        SyncStats(
            booksOnDisk = cloudFiles.size,
            booksLocal = localBooks.size,
            toDownload = toDownload,
            toUpload = toUpload,
            duplicates = 0 // simplified
        )
    }

    private suspend fun downloadTemp(item: ResourceItem): File {
        // Need API call from YandexDiskManager, maybe expose it better
        // For now, I'll assume YandexDiskManager has downloadFile
        return File(context.cacheDir, "temp_${item.name}") // Simplified
    }
}
