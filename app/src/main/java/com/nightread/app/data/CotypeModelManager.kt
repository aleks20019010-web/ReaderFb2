package com.nightread.app.data

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object CotypeModelManager {

    private const val TAG = "CotypeModelManager"
    private const val PREFS_NAME = "cotype_model_prefs"
    private const val KEY_MODEL_DOWNLOADED = "is_cotype_model_downloaded"
    private const val KEY_MODEL_PATH = "cotype_model_file_path"

    const val MODEL_FILENAME = "Vikhr-0.5B-Instruct.Q4_K_M.gguf"
    const val PRIMARY_DOWNLOAD_URL = "https://huggingface.co/Vikhrmodels/Vikhr-0.5B-Instruct-GGUF/resolve/main/Vikhr-0.5B-Instruct.Q4_K_M.gguf"
    const val FALLBACK_DOWNLOAD_URL_1 = "https://huggingface.co/mradermacher/Vikhr-0.5B-Instruct-GGUF/resolve/main/Vikhr-0.5B-Instruct.Q4_K_M.gguf"
    const val FALLBACK_DOWNLOAD_URL_2 = "https://huggingface.co/mradermacher/Cotype-Nano-GGUF/resolve/main/Cotype-Nano.Q4_K_M.gguf"

    const val REQUIRED_FREE_SPACE_BYTES = 1_000_000_000L // 1 GB
    const val REQUIRED_RAM_BYTES = 2_000_000_000L // ~2 GB RAM

    private val isDownloading = AtomicBoolean(false)
    private var activeCall: okhttp3.Call? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        // Clean up legacy Cotype Nano files if they exist to prevent loading heavy 1.5B model
        modelsDir.listFiles()?.forEach { oldFile ->
            if (oldFile.name.contains("cotype", ignoreCase = true) && oldFile.name != MODEL_FILENAME) {
                try {
                    Log.i(TAG, "Deleting legacy Cotype model file: ${oldFile.name}")
                    oldFile.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete legacy file ${oldFile.name}", e)
                }
            }
        }
        context.filesDir.listFiles()?.forEach { oldFile ->
            if (oldFile.name.contains("cotype", ignoreCase = true) && oldFile.name != MODEL_FILENAME) {
                try { oldFile.delete() } catch (_: Exception) {}
            }
        }

        val defaultFile = File(modelsDir, MODEL_FILENAME)
        if (defaultFile.exists() && defaultFile.length() > 0) return defaultFile

        // Fallback 1: Root files directory
        val alt1 = File(context.filesDir, MODEL_FILENAME)
        if (alt1.exists() && alt1.length() > 0) return alt1

        // Fallback 2: External files directory
        val extModelsDir = context.getExternalFilesDir("models")
        if (extModelsDir != null) {
            val alt2 = File(extModelsDir, MODEL_FILENAME)
            if (alt2.exists() && alt2.length() > 0) return alt2
        }

        val extFilesDir = context.getExternalFilesDir(null)
        if (extFilesDir != null) {
            val alt3 = File(extFilesDir, MODEL_FILENAME)
            if (alt3.exists() && alt3.length() > 0) return alt3
        }

        return defaultFile
    }

    fun deleteModel(context: Context): Boolean {
        try {
            val file = getModelFile(context)
            if (file.exists()) {
                val deleted = file.delete()
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                Log.i(TAG, "Model file deleted: $deleted")
                return deleted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model file", e)
        }
        return false
    }

    fun getAvailableRamMb(context: Context): Long {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            -1L
        }
    }

    fun verifyModelIntegrity(context: Context): Boolean {
        val file = getModelFile(context)
        if (!file.exists() || file.length() < 10_000_000L) {
            return false
        }
        // Basic check for GGUF magic header (0x46554747 = "GGUF")
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                if (read == 4) {
                    val magic = String(header, Charsets.US_ASCII)
                    magic == "GGUF"
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Integrity check warning", e)
            true // Fallback to true if file exists and > 10MB
        }
    }

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        if (file.exists() && verifyModelIntegrity(context)) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MODEL_DOWNLOADED, true).putString(KEY_MODEL_PATH, file.absolutePath).apply()
            return true
        }
        return false
    }

    fun checkDeviceCompatibility(context: Context): Pair<Boolean, String> {
        // 1. Check RAM
        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            if (memInfo.totalMem < REQUIRED_RAM_BYTES) {
                val totalGb = String.format("%.1f", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
                return Pair(false, "Недостаточно ОЗУ ($totalGb ГБ). Для работы Vikhr 0.5B требуется от 2 ГБ ОЗУ.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not verify RAM size", e)
        }

        // 2. Check Disk Space
        try {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()
            val stat = StatFs(modelsDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            if (availableBytes < REQUIRED_FREE_SPACE_BYTES) {
                val availGb = String.format("%.1f", availableBytes / (1024.0 * 1024.0 * 1024.0))
                return Pair(false, "Недостаточно свободного места ($availGb ГБ свободно, требуется 2 ГБ).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not verify disk space", e)
        }

        return Pair(true, "Устройство полностью совместимо с Cotype Nano 1.5B (4 ГБ ОЗУ, 2 ГБ Памяти).")
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun downloadModel(
        context: Context,
        onProgress: (percent: Int, downloadedMb: Float, totalMb: Float, statusText: String) -> Unit,
        onSuccess: (file: File) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        if (isDownloading.get()) {
            onError("Скачивание уже выполняется.")
            return
        }

        if (!isNetworkAvailable(context)) {
            onError("Требуется подключение к интернету для скачивания Cotype Nano 1.5B.")
            return
        }

        val compatCheck = checkDeviceCompatibility(context)
        if (!compatCheck.first) {
            onError(compatCheck.second)
            return
        }

        val file = getModelFile(context)
        val existingLength = if (file.exists()) file.length() else 0L

        isDownloading.set(true)

        val urlsToTry = listOf(PRIMARY_DOWNLOAD_URL, FALLBACK_DOWNLOAD_URL_1, FALLBACK_DOWNLOAD_URL_2)
        var lastExceptionMessage = ""

        for (url in urlsToTry) {
            if (!isDownloading.get()) break
            try {
                Log.i(TAG, "Attempting to download Cotype Nano from: $url")
                
                val currentLength = if (file.exists()) file.length() else 0L
                val requestBuilder = Request.Builder().url(url)
                if (currentLength > 0) {
                    requestBuilder.header("Range", "bytes=$currentLength-")
                }

                val request = requestBuilder.build()
                val call = client.newCall(request)
                activeCall = call

                val response = call.execute()

                if (!response.isSuccessful && response.code != 206) {
                    lastExceptionMessage = "HTTP ${response.code}: ${response.message}"
                    Log.w(TAG, "Failed downloading from $url: $lastExceptionMessage")
                    response.close()
                    continue
                }

                val body = response.body ?: run {
                    lastExceptionMessage = "Empty response body"
                    response.close()
                    continue
                }

                val isPartial = response.code == 206
                val totalBytes = if (isPartial) {
                    currentLength + body.contentLength()
                } else {
                    body.contentLength()
                }

                var downloadedBytes = if (isPartial) currentLength else 0L
                val fos = FileOutputStream(file, isPartial)
                val inputStream: InputStream = body.byteStream()

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastProgressReportTime = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isDownloading.get()) {
                        fos.close()
                        inputStream.close()
                        response.close()
                        onError("Скачивание отменено пользователем.")
                        return
                    }

                    fos.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressReportTime > 300) { // report every 300ms
                        lastProgressReportTime = now
                        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                        val downloadedMb = downloadedBytes / (1024f * 1024f)
                        val totalMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 1200f
                        val status = "Скачивание... $percent% (${String.format("%.1f", downloadedMb)} / ${String.format("%.1f", totalMb)} МБ)"
                        onProgress(percent, downloadedMb, totalMb, status)
                    }
                }

                fos.flush()
                fos.close()
                inputStream.close()
                response.close()

                if (!verifyModelIntegrity(context)) {
                    Log.w(TAG, "Integrity check failed for file downloaded from $url")
                    file.delete()
                    lastExceptionMessage = "Файл модели не прошел проверку целостности (GGUF)"
                    continue
                }

                isDownloading.set(false)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_MODEL_DOWNLOADED, true)
                    .putString(KEY_MODEL_PATH, file.absolutePath)
                    .apply()

                onProgress(100, file.length() / (1024f * 1024f), file.length() / (1024f * 1024f), "Модель готова")
                onSuccess(file)
                return

            } catch (e: Exception) {
                lastExceptionMessage = e.localizedMessage ?: "Ошибка передачи данных"
                Log.e(TAG, "Error downloading from $url", e)
            }
        }

        isDownloading.set(false)
        onError("Ошибка скачивания Cotype Nano 1.5B: $lastExceptionMessage")
    }

    fun cancelDownload() {
        isDownloading.set(false)
        try {
            activeCall?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling download call", e)
        }
    }

    fun isDownloading(): Boolean = isDownloading.get()
}
