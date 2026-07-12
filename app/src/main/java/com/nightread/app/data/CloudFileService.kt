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
        val allFiles = YandexDiskManager.getAllFilesFromFolder(context, authHeader, path)
        return allFiles.filter { item ->
            val name = item.name.lowercase()
            name.endsWith(".fb2") || name.endsWith(".fb2.zip") || name.endsWith(".zip") || name.endsWith(".epub") || name.endsWith(".mobi") || name.endsWith(".azw3") || name.endsWith(".pdf")
        }
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
