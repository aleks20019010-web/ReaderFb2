package com.nightread.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object DictionaryDownloader {
    private const val DICT_URL = "https://github.com/danpla/dict/raw/master/en-ru.sqlite"
    const val DICT_DIR = "dictionary"
    const val DICT_FILE_NAME = "en-ru.sqlite"

    fun getDictionaryFile(context: Context): File {
        val dir = File(context.filesDir, DICT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, DICT_FILE_NAME)
    }

    fun isDictionaryDownloaded(context: Context): Boolean {
        val file = getDictionaryFile(context)
        return file.exists() && file.length() > 0
    }

    suspend fun downloadDictionary(context: Context, onProgress: (Int) -> Unit = {}): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(DICT_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext false
                }

                val file = getDictionaryFile(context)
                if (file.exists()) {
                    file.delete()
                }

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(file)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                val fileSize = connection.contentLength.toFloat()
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (fileSize > 0) {
                        val progress = ((totalBytesRead / fileSize) * 100).toInt()
                        onProgress(progress.coerceIn(0, 100))
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
