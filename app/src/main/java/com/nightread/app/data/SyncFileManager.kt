package com.nightread.app.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID

/**
 * Управление временными файлами.
 */
class SyncFileManager(private val context: Context) {
    companion object {
        private const val TAG = "SYNC_FILE"
    }

    fun createTempFile(prefix: String): File {
        val file = File(context.cacheDir, "${prefix}_${UUID.randomUUID()}.tmp")
        Log.d(TAG, "Created temp file: ${file.absolutePath}")
        return file
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up temp files")
        context.cacheDir.listFiles { _, name -> name.endsWith(".tmp") }?.forEach {
            if (it.delete()) Log.d(TAG, "Deleted: ${it.name}")
        }
    }
}
