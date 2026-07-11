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
        if (isLoaded && llamaHelperInstance != null) {
            Log.i("LocalLLM", "Модель уже загружена.")
            return@withContext true
        }
        
        val file = File(path)
        if (!file.exists()) {
            Log.e("LocalLLM", "[ОШИБКА] Файл модели не найден: $path")
            return@withContext false
        }

        val fileSizeMb = file.length() / (1024 * 1024)
        val runtime = Runtime.getRuntime()
        val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
        val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
        val allocatedMemoryMb = totalMemoryMb - freeMemoryMb
        val availableHeapMb = maxMemoryMb - allocatedMemoryMb

        Log.i("LocalLLM", "=== Диагностика перед загрузкой модели ===")
        Log.i("LocalLLM", "Путь к модели: $path")
        Log.i("LocalLLM", "Размер файла модели: $fileSizeMb MB")
        Log.i("LocalLLM", "Максимальная JVM память (maxMemory): $maxMemoryMb MB")
        Log.i("LocalLLM", "Выделенная JVM память (totalMemory): $totalMemoryMb MB")
        Log.i("LocalLLM", "Свободная JVM память (freeMemory): $freeMemoryMb MB")
        Log.i("LocalLLM", "Фактически занятая JVM память: $allocatedMemoryMb MB")
        Log.i("LocalLLM", "Доступная память в куче (heap): $availableHeapMb MB")
        Log.i("LocalLLM", "Вызывается LlamaHelper инициализация...")

        return@withContext suspendCancellableCoroutine { continuation ->
            var resumed = false
            try {
                Log.i("LocalLLM", "Создание инстанса LlamaHelper...")
                val helper = LlamaHelper(appContext.contentResolver, scope, sharedFlow)
                
                val job = scope.launch {
                    try {
                        sharedFlow.collect { event ->
                            Log.i("LocalLLM", "[СОБЫТИЕ] Получено событие от LlamaHelper: ${event.javaClass.simpleName} ($event)")
                            if (event is LlamaHelper.LLMEvent.Error) {
                                Log.e("LocalLLM", "[ОШИБКА] Событие ошибки при загрузке модели: ${event.message}")
                                if (!resumed) {
                                    resumed = true
                                    isLoaded = false
                                    llamaHelperInstance = null
                                    continuation.resume(false) { }
                                }
                                this.cancel()
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("LocalLLM", "[ОШИБКА] Ошибка при сборе событий sharedFlow", t)
                    }
                }

                val finalPath = if (path.startsWith("content://") || path.startsWith("file://")) {
                    path
                } else {
                    try {
                        android.net.Uri.fromFile(File(path)).toString()
                    } catch (e: Exception) {
                        Log.e("LocalLLM", "[ПРЕДУПРЕЖДЕНИЕ] Ошибка преобразования пути в Uri, используем исходный путь", e)
                        path
                    }
                }

                Log.i("LocalLLM", "Вызов helper.load() для пути: $finalPath (исходный путь: $path)")
                try {
                    helper.load(finalPath, 2048, "") { handle ->
                        Log.i("LocalLLM", "[УСПЕХ] Модель загружена успешно. Дескриптор (Handle): $handle")
                        job.cancel()
                        if (!resumed) {
                            resumed = true
                            isLoaded = true
                            llamaHelperInstance = helper
                            continuation.resume(true) { }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("LocalLLM", "[ОШИБКА] Исключение непосредственно при вызове helper.load()", t)
                    job.cancel()
                    if (!resumed) {
                        resumed = true
                        isLoaded = false
                        llamaHelperInstance = null
                        continuation.resume(false) { }
                    }
                }

                continuation.invokeOnCancellation {
                    Log.w("LocalLLM", "Загрузка модели была отменена.")
                    job.cancel()
                }

            } catch (e: OutOfMemoryError) {
                Log.e("LocalLLM", "[КРИТИЧЕСКАЯ ОШИБКА] Недостаточно памяти (OutOfMemoryError) при загрузке модели AI!", e)
                isLoaded = false
                llamaHelperInstance = null
                if (!resumed) {
                    resumed = true
                    continuation.resume(false) { }
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e("LocalLLM", "[КРИТИЧЕСКАЯ ОШИБКА] Ошибка загрузки нативной библиотеки JNI (UnsatisfiedLinkError)!", e)
                isLoaded = false
                llamaHelperInstance = null
                if (!resumed) {
                    resumed = true
                    continuation.resume(false) { }
                }
            } catch (e: Throwable) {
                Log.e("LocalLLM", "[КРИТИЧЕСКАЯ ОШИБКА] Исключение во время загрузки модели: ${e.javaClass.simpleName} - ${e.message}", e)
                isLoaded = false
                llamaHelperInstance = null
                if (!resumed) {
                    resumed = true
                    continuation.resume(false) { }
                }
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
