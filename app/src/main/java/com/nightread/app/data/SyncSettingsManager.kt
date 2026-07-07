package com.nightread.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

object SyncSettingsManager {
    private const val PREFS_NAME = "sync_settings_prefs"
    private const val KEY_DOWNLOAD_FOLDER_URI = "download_folder_uri"

    /**
     * Возвращает URI выбранной локальной папки в виде строки
     */
    fun getDownloadFolderUri(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DOWNLOAD_FOLDER_URI, null)
    }

    /**
     * Сохраняет URI выбранной локальной папки
     */
    fun setDownloadFolderUri(context: Context, uriString: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DOWNLOAD_FOLDER_URI, uriString).apply()
    }

    /**
     * Возвращает понятное пользователю название папки для отображения в UI
     */
    fun getDownloadFolderDisplayName(context: Context): String {
        val uriStr = getDownloadFolderUri(context) ?: return "По умолчанию (/Books)"
        return try {
            val uri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromTreeUri(context, uri)
            if (docFile != null && docFile.exists()) {
                docFile.name ?: "Выбранная папка"
            } else {
                "Папка недоступна"
            }
        } catch (e: Exception) {
            "По умолчанию (/Books)"
        }
    }

    /**
     * Проверяет, доступна ли выбранная папка
     */
    fun isFolderAccessible(context: Context): Boolean {
        val uriStr = getDownloadFolderUri(context) ?: return true // По умолчанию всегда доступно
        return try {
            val uri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromTreeUri(context, uri)
            docFile != null && docFile.exists() && docFile.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Преобразует дерево URI SAF в физический путь, если это возможно.
     * Это необходимо, так как библиотека и читалка работают со стандартными файлами (File).
     */
    fun resolveUriToPath(context: Context, uri: Uri): String? {
        try {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            if (documentId != null) {
                val parts = documentId.split(":")
                if (parts.size >= 2 && parts[0].equals("primary", ignoreCase = true)) {
                    val relativePath = parts[1]
                    val externalStorageDir = Environment.getExternalStorageDirectory()
                    val physicalFile = File(externalStorageDir, relativePath)
                    return physicalFile.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("SyncSettingsManager", "Error resolving Uri to physical path: ${e.message}", e)
        }
        return null
    }
}
