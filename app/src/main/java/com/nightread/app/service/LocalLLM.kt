package com.nightread.app.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File
import org.nehuatl.llamacpp.LlamaHelper

/**
 * Класс для работы с локальной LLM через библиотеку llama.cpp.
 */
class LocalLLM private constructor(context: Context) {

    private val appContext = context.applicationContext
    private var llamaHelperInstance: LlamaHelper? = null
    private val sharedFlow = MutableSharedFlow<LlamaHelper.LLMEvent>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isLoaded = false

    companion object {
        @Volatile
        private var instance: LocalLLM? = null

        fun getInstance(context: Context): LocalLLM {
            return instance ?: synchronized(this) {
                instance ?: LocalLLM(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Загружает модель GGUF по указанному пути.
     */
    suspend fun loadModel(path: String): Boolean = withContext(Dispatchers.Main) {
        if (isLoaded && llamaHelperInstance != null) return@withContext true
        
        val file = File(path)
        if (!file.exists()) {
            Log.e("LocalLLM", "Файл модели не найден: $path")
            return@withContext false
        }

        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                Log.i("LocalLLM", "Инициализация модели: $path")
                val helper = LlamaHelper(appContext.contentResolver, scope, sharedFlow)
                
                var resumed = false
                val job = scope.launch {
                    sharedFlow.collect { event ->
                        if (event is LlamaHelper.LLMEvent.Error) {
                            Log.e("LocalLLM", "Ошибка при загрузке модели: ${event.message}")
                            if (!resumed) {
                                resumed = true
                                isLoaded = false
                                llamaHelperInstance = null
                                continuation.resume(false) { }
                            }
                            this.cancel()
                        }
                    }
                }

                helper.load(path, 2048, "") { handle ->
                    Log.i("LocalLLM", "Модель загружена успешно. Handle: $handle")
                    job.cancel()
                    if (!resumed) {
                        resumed = true
                        isLoaded = true
                        llamaHelperInstance = helper
                        continuation.resume(true) { }
                    }
                }

                continuation.invokeOnCancellation {
                    job.cancel()
                }

            } catch (e: Exception) {
                Log.e("LocalLLM", "Критическая ошибка при загрузке модели", e)
                isLoaded = false
                llamaHelperInstance = null
                continuation.resume(false) { }
            }
        }
    }

    /**
     * Генерирует ответ на основе промпта.
     */
    suspend fun generate(prompt: String, maxTokens: Int = 200): String = withContext(Dispatchers.Main) {
        val helper = llamaHelperInstance ?: return@withContext "Ошибка: Модель не загружена. Проверьте настройки AI."

        return@withContext suspendCancellableCoroutine { continuation ->
            val job = scope.launch {
                try {
                    sharedFlow.collect { event ->
                        when (event) {
                            is LlamaHelper.LLMEvent.Done -> {
                                if (continuation.isActive) {
                                    continuation.resume(event.fullText) { }
                                }
                                this.cancel()
                            }
                            is LlamaHelper.LLMEvent.Error -> {
                                if (continuation.isActive) {
                                    continuation.resume("Ошибка во время работы AI: ${event.message}") { }
                                }
                                this.cancel()
                            }
                            else -> {
                                // Do nothing for Ongoing/Loaded/Started
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume("Ошибка во время сбора ответов: ${e.message}") { }
                    }
                }
            }

            try {
                helper.predict(prompt, "", false)
            } catch (e: Exception) {
                job.cancel()
                if (continuation.isActive) {
                    continuation.resume("Ошибка при запуске генерации: ${e.message}") { }
                }
            }

            continuation.invokeOnCancellation {
                job.cancel()
                helper.stopPrediction()
            }
        }
    }

    /**
     * Освобождает ресурсы модели.
     */
    fun release() {
        llamaHelperInstance?.release()
        llamaHelperInstance = null
        isLoaded = false
    }

    fun isModelLoaded(): Boolean = isLoaded
}
