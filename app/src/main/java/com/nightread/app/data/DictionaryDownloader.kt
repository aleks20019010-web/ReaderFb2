package com.nightread.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object DictionaryDownloader {
    private const val TAG = "DictionaryDownloader"
    private const val DICT_URL = "https://raw.githubusercontent.com/danpla/dict/master/en-ru.sqlite"
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
            var conn: HttpURLConnection? = null
            try {
                var currentUrl = DICT_URL
                var redirectCount = 0
                val maxRedirects = 5

                while (redirectCount < maxRedirects) {
                    val url = URL(currentUrl)
                    conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; NightRead)")
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.connect()

                    val responseCode = conn.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                        responseCode == 307 || responseCode == 308) {
                        val newUrl = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (newUrl != null) {
                            currentUrl = newUrl
                            redirectCount++
                            continue
                        } else {
                            Log.e(TAG, "Redirect location is null")
                            return@withContext false
                        }
                    } else if (responseCode == HttpURLConnection.HTTP_OK) {
                        break
                    } else {
                        Log.e(TAG, "HTTP error code: $responseCode")
                        conn.disconnect()
                        return@withContext false
                    }
                }

                if (conn == null || conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext false
                }

                val file = getDictionaryFile(context)
                if (file.exists()) {
                    file.delete()
                }

                val fileSize = conn.contentLength.toFloat()
                val inputStream = conn.inputStream
                val outputStream = FileOutputStream(file)
                val buffer = ByteArray(8192)
                var bytesRead: Int
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
                conn.disconnect()
                Log.d(TAG, "Dictionary successfully downloaded to ${file.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading dictionary", e)
                conn?.disconnect()
                false
            }
        }
    }
}

