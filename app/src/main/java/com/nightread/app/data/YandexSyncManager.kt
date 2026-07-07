package com.nightread.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class YandexSyncManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val cloudFileDao = database.cloudFileDao()
    private val bookDao = database.bookDao()

    suspend fun getSyncStats(): SyncStats = withContext(Dispatchers.IO) {
        val localBooks = bookDao.getAllBooks().first()
        val localSha1Set = localBooks.map { it.sha1 }.toSet()

        val token = YandexDiskManager.getToken(context) ?: ""
        val authHeader = "OAuth $token"

        val originalFolder = YandexDiskManager.getSyncFolder(context)
        val syncFolder = YandexDiskManager.resolveCaseInsensitivePath(context, originalFolder)
        
        val cloudFiles = YandexDiskManager.getAllFilesFromFolder(authHeader, syncFolder)
            .filter {
                val lowerName = it.name.lowercase()
                lowerName.endsWith(".fb2") || lowerName.endsWith(".fb2.zip") || lowerName.endsWith(".epub")
            }
        
        val toDownload = mutableListOf<CloudFileEntry>()
        val cloudSha1Set = mutableSetOf<String>()

        for (item in cloudFiles) {
            val cached = cloudFileDao.getByPath(item.path ?: "")
            val sha1 = if (cached != null && cached.lastModified == item.modified) {
                cached.sha1
            } else {
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
                    toDownload.add(CloudFileEntry(item.name ?: "", item.size ?: 0, item.modified ?: "", sha1))
                }
            }
        }

        val toUpload = localBooks.filter { !cloudSha1Set.contains(it.sha1) }
        
        SyncStats(
            booksOnDisk = cloudFiles.size,
            booksLocal = localBooks.size,
            toDownload = toDownload,
            toUpload = toUpload,
            duplicates = 0,
            cloudProgressItems = emptyList() // Added missing parameter
        )
    }

    private suspend fun downloadTemp(item: ResourceItem): File {
        return File(context.cacheDir, "temp_${item.name}")
    }
}
