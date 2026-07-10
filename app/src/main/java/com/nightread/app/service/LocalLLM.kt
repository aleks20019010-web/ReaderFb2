package com.nightread.app.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Класс для работы с локальной LLM через библиотеку llama.cpp.
 * Ожидается наличие скомпилированной нативной библиотеки libllama.so в jniLibs.
 */
class LocalLLM private constructor(context: Context) {

    private var modelHandle: Long = 0
    private var isLoaded = false

    companion object {
        @Volatile
        private var instance: LocalLLM? = null

        fun getInstance(context: Context): LocalLLM {
            return instance ?: synchronized(this) {
                instance ?: LocalLLM(context.applicationContext).also { instance = it }
            }
        }

        init {
            try {
                System.loadLibrary("llama")
                Log.i("LocalLLM", "Нативная библиотека llama успешно загружена")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("LocalLLM", "Не удалось загрузить нативную библиотеку llama. Убедитесь, что .so файлы добавлены в проект.", e)
            }
        }
    }

    // --- Нативные методы ---
    
    private external fun nativeInitContext(modelPath: String): Long
    private external fun nativeGenerate(handle: Long, prompt: String, threads: Int, maxTokens: Int): String
    private external fun nativeReleaseContext(handle: Long)

    /**
     * Загружает модель GGUF по указанному пути.
     */
    suspend fun loadModel(path: String): Boolean = withContext(Dispatchers.IO) {
        if (isLoaded && modelHandle != 0L) return@withContext true
        
        val file = File(path)
        if (!file.exists()) {
            Log.e("LocalLLM", "Файл модели не найден: $path")
            return@withContext false
        }

        return@withContext try {
            modelHandle = nativeInitContext(path)
            isLoaded = modelHandle != 0L
            if (isLoaded) {
                Log.i("LocalLLM", "Модель загружена успешно. Handle: $modelHandle")
            } else {
                Log.e("LocalLLM", "Ошибка при инициализации контекста модели (nativeInitContext вернул 0)")
            }
            isLoaded
        } catch (e: Exception) {
            Log.e("LocalLLM", "Критическая ошибка при загрузке модели", e)
            false
        } catch (e: UnsatisfiedLinkError) {
            Log.e("LocalLLM", "Нативный метод nativeInitContext не найден (библиотека не загружена?)")
            false
        }
    }

    /**
     * Генерирует ответ на основе промпта.
     */
    suspend fun generate(prompt: String, maxTokens: Int = 200): String = withContext(Dispatchers.IO) {
        if (!isLoaded || modelHandle == 0L) {
            return@withContext "Ошибка: Модель не загружена. Проверьте настройки AI."
        }

        return@withContext try {
            val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            nativeGenerate(modelHandle, prompt, threads, maxTokens)
        } catch (e: Exception) {
            Log.e("LocalLLM", "Ошибка при генерации текста", e)
            "Ошибка во время работы AI."
        } catch (e: UnsatisfiedLinkError) {
            "Ошибка: Нативные методы AI недоступны."
        }
    }

    /**
     * Освобождает ресурсы модели.
     */
    fun release() {
        if (modelHandle != 0L) {
            try {
                nativeReleaseContext(modelHandle)
            } catch (e: Exception) {
                Log.e("LocalLLM", "Ошибка при освобождении контекста", e)
            }
            modelHandle = 0L
            isLoaded = false
        }
    }

    fun isModelLoaded(): Boolean = isLoaded
}
