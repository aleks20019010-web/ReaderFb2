package com.nightread.app.data

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class CloudFileService(private val context: Context) {
    private val api = YandexDiskManager.api
    private val TAG = "CloudFileService"

    suspend fun getFileList(path: String): List<ResourceItem> {
        val token = YandexDiskManager.getToken(context) ?: return emptyList()
        val authHeader = "OAuth $token"
        val cleanPath = YandexDiskManager.normalizePath(path)
        
        val items = mutableListOf<ResourceItem>()
        var offset = 0
        val limit = 100
        var hasMore = true
        var page = 1

        while (hasMore) {
            try {
                val response = api.getResource(
                    token = authHeader,
                    path = cleanPath,
                    limit = limit,
                    offset = offset
                )

                val embedded = response.embedded
                val pageItems = embedded?.items ?: emptyList()

                if (pageItems.isEmpty()) {
                    hasMore = false
                } else {
                    val fileItems = pageItems.filter { it.type == "file" }
                    items.addAll(fileItems)

                    val total = embedded?.total
                    val returnedLimit = embedded?.limit ?: limit

                    if (total != null) {
                        offset += pageItems.size
                        if (offset >= total) {
                            hasMore = false
                        } else {
                            page++
                        }
                    } else {
                        if (pageItems.size < returnedLimit) {
                            hasMore = false
                        } else {
                            offset += pageItems.size
                            page++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching file list from path: $cleanPath", e)
                hasMore = false
            }
        }
        return items
    }

    suspend fun downloadFile(remotePath: String, destinationFile: File): Boolean {
        val token = YandexDiskManager.getToken(context) ?: return false
        val authHeader = "OAuth $token"
        val cleanPath = YandexDiskManager.normalizePath(remotePath)
        return try {
            val linkResponse = api.getDownloadLink(authHeader, cleanPath)
            val responseBody = api.downloadFile(linkResponse.href)
            destinationFile.outputStream().use { output ->
                responseBody.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $cleanPath", e)
            false
        }
    }

    suspend fun uploadFile(localFile: File, remotePath: String): Boolean {
        val token = YandexDiskManager.getToken(context) ?: return false
        val authHeader = "OAuth $token"
        val cleanPath = YandexDiskManager.normalizePath(remotePath)
        return try {
            val linkResponse = api.getUploadLink(authHeader, cleanPath)
            val baseBody = localFile.readBytes().toRequestBody("application/octet-stream".toMediaType())
            api.uploadFile(linkResponse.href, baseBody)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload $cleanPath", e)
            false
        }
    }

    suspend fun deleteFile(remotePath: String): Boolean {
        val token = YandexDiskManager.getToken(context) ?: return false
        val authHeader = "OAuth $token"
        val cleanPath = YandexDiskManager.normalizePath(remotePath)
        return try {
            api.deleteResource(authHeader, cleanPath)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete $cleanPath", e)
            false
        }
    }
}
