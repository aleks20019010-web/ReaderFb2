package com.nightread.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

interface TokenCallback {
    fun onToken(token: String)
}

object LlamaEngine {
    private const val TAG = "LlamaEngine"
    private const val MODEL_FILENAME = "gemma-2b-it-q4_k_m.gguf"
    private const val AUTO_UNLOAD_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    private var isJniLoaded = false
    private var unloadJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        try {
            System.loadLibrary("llama_jni")
            isJniLoaded = true
            Log.i(TAG, "Native library llama_jni loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library llama_jni", e)
            isJniLoaded = false
        }
    }

    private external fun nativeLoadModel(path: String): Boolean
    private external fun nativeGenerate(prompt: String, temperature: Float, topK: Int, maxTokens: Int): String
    private external fun nativeGenerateStream(prompt: String, temperature: Float, topK: Int, maxTokens: Int, callback: TokenCallback)
    private external fun nativeStop()
    private external fun nativeUnload()
    private external fun nativeIsLoaded(): Boolean

    fun initialize(context: Context) {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
    }

    fun getModelFile(context: Context): File {
        return File(File(context.filesDir, "models"), MODEL_FILENAME)
    }

    fun getModelSize(context: Context): Long {
        val file = getModelFile(context)
        return if (file.exists()) file.length() else 0L
    }

    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (!file.exists()) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val inputStream = FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            val hashBytes = digest.digest()
            val sb = StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString().equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-256", e)
            false
        }
    }

    fun loadModel(context: Context): Boolean {
        resetAutoUnloadTimer()
        val file = getModelFile(context)
        if (!file.exists()) {
            Log.w(TAG, "Model file does not exist at ${file.absolutePath}")
            return false
        }
        if (!isJniLoaded) {
            Log.w(TAG, "JNI library is not available, falling back to LocalAiEngine fallback")
            return false
        }
        val success = nativeLoadModel(file.absolutePath)
        Log.i(TAG, "Native load model status: $success")
        return success
    }

    fun generate(
        context: Context,
        prompt: String,
        temperature: Float = 0.2f,
        topK: Int = 40,
        maxTokens: Int = 256
    ): String {
        resetAutoUnloadTimer()
        if (!isModelLoaded()) {
            val loaded = loadModel(context)
            if (!loaded) {
                return LocalAiEngine.customAiPrompt(context, prompt, null, "")
            }
        }
        return if (isJniLoaded) {
            nativeGenerate(prompt, temperature, topK, maxTokens)
        } else {
            LocalAiEngine.customAiPrompt(context, prompt, null, "")
        }
    }

    fun generateStream(
        context: Context,
        prompt: String,
        temperature: Float = 0.2f,
        topK: Int = 40,
        maxTokens: Int = 256,
        onToken: (String) -> Unit
    ) {
        resetAutoUnloadTimer()
        if (!isModelLoaded()) {
            loadModel(context)
        }

        if (isJniLoaded && isModelLoaded()) {
            nativeGenerateStream(prompt, temperature, topK, maxTokens, object : TokenCallback {
                override fun onToken(token: String) {
                    resetAutoUnloadTimer()
                    onToken(token)
                }
            })
        } else {
            // Fallback token streaming for devices without native model
            val fullResponse = LocalAiEngine.customAiPrompt(context, prompt, null, "")
            val words = fullResponse.split(" ")
            for (w in words) {
                onToken("$w ")
                Thread.sleep(30)
            }
        }
    }

    fun stopGeneration() {
        if (isJniLoaded) {
            nativeStop()
        }
    }

    fun unloadModel() {
        if (isJniLoaded) {
            nativeUnload()
        }
        unloadJob?.cancel()
        Log.i(TAG, "Model unloaded from memory")
    }

    fun isModelLoaded(): Boolean {
        return isJniLoaded && nativeIsLoaded()
    }

    private fun resetAutoUnloadTimer() {
        unloadJob?.cancel()
        unloadJob = engineScope.launch {
            delay(AUTO_UNLOAD_TIMEOUT_MS)
            Log.i(TAG, "Auto-unloading model due to 5 minutes of inactivity")
            unloadModel()
        }
    }
}
