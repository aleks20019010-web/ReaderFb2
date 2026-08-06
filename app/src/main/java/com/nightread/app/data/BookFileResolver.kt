package com.nightread.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BookFileResolver {
    private const val TAG = "BookFileResolver"

    suspend fun resolveBookFile(context: Context, book: BookEntity, db: AppDatabase? = null): File? = withContext(Dispatchers.IO) {
        val rawPath = book.filePath
        if (rawPath.isNullOrBlank()) return@withContext null

        // 1. Direct path check (strip file:// if present)
        val cleanPath = if (rawPath.startsWith("file://")) {
            Uri.parse(rawPath).path ?: rawPath.removePrefix("file://")
        } else {
            rawPath
        }

        var file = File(cleanPath)
        if (file.exists() && file.length() > 0) {
            if (cleanPath != rawPath && db != null) {
                try { db.bookDao().updateFilePath(book.sha1, file.absolutePath) } catch (e: Exception) {}
            }
            return@withContext file
        }

        // 2. URI resolution (content:// or SAF)
        val uri = try { Uri.parse(rawPath) } catch (e: Exception) { null }
        if (uri != null && uri.scheme == "content") {
            // Try MediaStore _data column
            try {
                context.contentResolver.query(uri, arrayOf(MediaStore.Files.FileColumns.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                        if (dataIdx != -1) {
                            val mediaPath = cursor.getString(dataIdx)
                            if (!mediaPath.isNullOrBlank()) {
                                val mediaFile = File(mediaPath)
                                if (mediaFile.exists() && mediaFile.length() > 0) {
                                    if (db != null) {
                                        try { db.bookDao().updateFilePath(book.sha1, mediaFile.absolutePath) } catch (e: Exception) {}
                                    }
                                    return@withContext mediaFile
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying DATA column for $uri", e)
            }

            // Copy stream to local imported directory
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val importedDir = File(context.filesDir, "imported").apply { mkdirs() }
                    val ext = when {
                        rawPath.endsWith(".fb2.zip", true) -> "fb2.zip"
                        rawPath.endsWith(".fb3.zip", true) -> "fb3.zip"
                        rawPath.endsWith(".epub", true) -> "epub"
                        rawPath.endsWith(".mobi", true) -> "mobi"
                        rawPath.endsWith(".azw3", true) -> "azw3"
                        rawPath.endsWith(".azw", true) -> "azw"
                        rawPath.endsWith(".fb3", true) -> "fb3"
                        rawPath.endsWith(".fb2", true) -> "fb2"
                        rawPath.endsWith(".zip", true) -> "zip"
                        else -> "fb2"
                    }
                    val localFile = File(importedDir, "${book.sha1}.$ext")
                    if (!localFile.exists() || localFile.length() == 0L) {
                        localFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (localFile.exists() && localFile.length() > 0) {
                        if (db != null) {
                            try { db.bookDao().updateFilePath(book.sha1, localFile.absolutePath) } catch (e: Exception) {}
                        }
                        return@withContext localFile
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying content stream for ${book.title}", e)
            }
        }

        // 3. Search fallback in Downloads/Documents/Files by filename or SHA1
        try {
            val fileName = File(cleanPath).name.ifBlank { book.title }
            val legacyRoots = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStorageDirectory(),
                File(context.filesDir, "imported")
            )
            for (root in legacyRoots) {
                if (!root.exists()) continue
                val matchingFile = root.walkTopDown().maxDepth(3).firstOrNull { f ->
                    f.isFile && f.length() > 0 && (f.name == fileName || f.name.contains(book.sha1) || f.name.equals(book.title, ignoreCase = true))
                }
                if (matchingFile != null && matchingFile.exists() && matchingFile.length() > 0) {
                    if (db != null) {
                        try { db.bookDao().updateFilePath(book.sha1, matchingFile.absolutePath) } catch (e: Exception) {}
                    }
                    return@withContext matchingFile
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search fallback error", e)
        }

        return@withContext null
    }
}
