package com.nightread.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString("MODEL_URL") ?: return@withContext Result.failure()
        val modelFileName = inputData.getString("MODEL_FILENAME") ?: return@withContext Result.failure()
        
        val modelsDir = File(applicationContext.filesDir, "ai_models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        
        val outputFile = File(modelsDir, modelFileName)
        
        try {
            val url = URL(modelUrl)
            val connection = url.openConnection()
            connection.connect()
            
            val fileLength = connection.contentLength
            val input = connection.getInputStream()
            val output = FileOutputStream(outputFile)
            
            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            
            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                
                if (fileLength > 0) {
                    val progress = (total * 100 / fileLength).toInt()
                    setProgress(workDataOf("PROGRESS" to progress))
                }
                output.write(data, 0, count)
                
                if (isStopped) {
                    output.flush()
                    output.close()
                    input.close()
                    outputFile.delete()
                    return@withContext Result.failure()
                }
            }
            
            output.flush()
            output.close()
            input.close()
            
            return@withContext Result.success(workDataOf("FILE_PATH" to outputFile.absolutePath))
        } catch (e: Exception) {
            e.printStackTrace()
            if (outputFile.exists()) outputFile.delete()
            return@withContext Result.failure()
        }
    }
}
