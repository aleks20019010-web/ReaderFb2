package com.nightread.app.data

import android.content.Context
import android.util.Log
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
        
        Log.d("YandexSyncManager", "getSyncStats: Original folder='$originalFolder' resolved to='$syncFolder'")
        if (syncFolder.contains("disk:")) {
            Log.e("YandexSyncManager", "getSyncStats ERROR: Resolved path contains 'disk:' prefix!")
        } else {
            Log.d("YandexSyncManager", "getSyncStats: Verified path '$syncFolder' has no 'disk:' prefix.")
        }

        val maskedToken = if (token.length > 8) "${token.take(4)}...${token.takeLast(4)}" else "***"
        Log.d("YandexSyncManager", "getSyncStats Auth Header: 'OAuth $maskedToken' (Length: ${token.length})")

        val allCloudFiles = YandexDiskManager.getAllFilesFromFolder(context, authHeader, syncFolder)
        
        Log.d("YandexSyncManager", "getSyncStats: Retrieved ${allCloudFiles.size} files from disk.")
        if (allCloudFiles.isNotEmpty()) {
            Log.d("YandexSyncManager", "getSyncStats: Showing first 5 files from disk for extension check:")
            allCloudFiles.take(5).forEachIndexed { idx, item ->
                Log.d("YandexSyncManager", "  [$idx] Name: '${item.name}', Type: '${item.type}', Path: '${item.path}'")
            }
        } else {
            Log.d("YandexSyncManager", "getSyncStats: No files found in folder.")
        }

        val cloudFiles = allCloudFiles.filter {
            val lowerName = it.name.lowercase()
            val isFb2 = lowerName.endsWith(".fb2")
            val isFb2Zip = lowerName.endsWith(".fb2.zip")
            val isEpub = lowerName.endsWith(".epub")
            val matches = isFb2 || isFb2Zip || isEpub
            Log.v("YandexSyncManager", "File: '${it.name}' -> isFb2=$isFb2, isFb2Zip=$isFb2Zip, isEpub=$isEpub -> matches=$matches")
            matches
        }
        val booksOnDisk = cloudFiles.size
        Log.d("YandexSyncManager", "getSyncStats: Filtering complete. Found $booksOnDisk books after extension filter.")

        if (cloudFiles.isNotEmpty()) {
            Log.d("YandexSyncManager", "getSyncStats: First 5 MATCHED books:")
            cloudFiles.take(5).forEachIndexed { idx, item ->
                Log.d("YandexSyncManager", "  [$idx] Book Name: '${item.name}', Path: '${item.path}'")
            }
        }
        
        val toDownload = mutableListOf<CloudFileEntry>()
        val cloudSha1Set = mutableSetOf<String>()

        for (item in cloudFiles) {
            val cached = cloudFileDao.getByPath(item.path ?: "")
            val sha1 = if (cached != null && cached.lastModified == item.modified) {
                cached.sha1
            } else {
                val tempFile = File(context.cacheDir, "temp_${item.name}")
                val downloadSuccess = YandexDiskManager.downloadFileToTemp(context, item.path ?: "", tempFile)
                val s = if (downloadSuccess) Sha1Helper.computeSha1FromContent(tempFile) else null
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
}
