package com.nightread.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString("MODEL_URL") ?: return@withContext Result.failure()
        val modelFileName = inputData.getString("MODEL_FILENAME") ?: return@withContext Result.failure()
        val modelName = inputData.getString("MODEL_NAME") ?: modelFileName
        val notificationId = modelUrl.hashCode()

        val modelsDir = File(applicationContext.filesDir, "ai_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        val outputFile = File(modelsDir, modelFileName)
        val tempFile = File(modelsDir, "$modelFileName.tmp")

        try {
            Log.i("ModelDownloadWorker", "Starting download from $modelUrl")
            ModelNotificationHelper.showDownloadNotification(applicationContext, modelName, 0, notificationId)

            val request = Request.Builder()
                .url(modelUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Unexpected code $response")

                val body = response.body ?: throw Exception("Response body is null")
                val fileLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val data = ByteArray(16384)
                var total: Long = 0
                var count: Int
                var lastProgressUpdate = 0L

                while (inputStream.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    outputStream.write(data, 0, count)

                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        
                        // Update progress in WorkManager and Notification every 500ms or 1%
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProgressUpdate > 500) {
                            setProgress(workDataOf("PROGRESS" to progress))
                            ModelNotificationHelper.showDownloadNotification(applicationContext, modelName, progress, notificationId)
                            lastProgressUpdate = currentTime
                        }
                    }

                    if (isStopped) {
                        outputStream.close()
                        inputStream.close()
                        if (tempFile.exists()) tempFile.delete()
                        ModelNotificationHelper.cancelNotification(applicationContext, notificationId)
                        return@withContext Result.failure()
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
            }

            if (tempFile.exists()) {
                if (outputFile.exists()) outputFile.delete()
                tempFile.renameTo(outputFile)
            }

            ModelNotificationHelper.showFinishedNotification(applicationContext, modelName, notificationId)
            return@withContext Result.success(workDataOf("FILE_PATH" to outputFile.absolutePath))
        } catch (e: Exception) {
            Log.e("ModelDownloadWorker", "Download failed", e)
            if (tempFile.exists()) tempFile.delete()
            ModelNotificationHelper.showErrorNotification(applicationContext, modelName, notificationId)
            return@withContext Result.failure()
        }
    }
}
