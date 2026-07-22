package com.nightread.app.data

import android.util.Log

data class LlamaModelParams(
    val nCtx: Int = 1024,
    val nThreads: Int = 4,
    val nGpuLayers: Int = 0,
    val useMMap: Boolean = false,
    val useMLock: Boolean = false,
    val directIo: Boolean = false
)

data class GenerationParams(
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val stopTokens: List<String> = listOf("User:", "\n\n\n", "<|im_end|>", "<|im_start|>")
)

interface LlamaStreamCallback {
    fun onToken(token: String)
}

object LlamaEngine {

    private const val TAG = "LlamaEngine"

    init {
        try {
            System.loadLibrary("llama_jni")
            Log.i(TAG, "Native library llama_jni loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Could not load native library llama_jni", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading llama_jni library", e)
        }
    }

    external fun nativeLoadModel(
        path: String,
        nCtx: Int,
        nThreads: Int,
        nGpuLayers: Int,
        useMMap: Boolean,
        useMLock: Boolean,
        directIo: Boolean
    ): Boolean

    external fun nativeGenerate(
        prompt: String,
        temperature: Float,
        topK: Int,
        maxTokens: Int,
        topP: Float,
        repeatPenalty: Float
    ): String

    external fun nativeGenerateStream(
        prompt: String,
        temperature: Float = 0.7f,
        topK: Int = 40,
        maxTokens: Int = 1024,
        callback: LlamaStreamCallback
    )

    external fun nativeStop()
    external fun nativeUnload()
    external fun nativeIsLoaded(): Boolean

    fun isLoaded(): Boolean {
        return try {
            nativeIsLoaded()
        } catch (e: Throwable) {
            false
        }
    }

    fun loadModel(
        path: String,
        params: LlamaModelParams = LlamaModelParams()
    ): Boolean {
        return try {
            nativeLoadModel(
                path = path,
                nCtx = params.nCtx,
                nThreads = params.nThreads,
                nGpuLayers = params.nGpuLayers,
                useMMap = params.useMMap,
                useMLock = params.useMLock,
                directIo = params.directIo
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error in nativeLoadModel", e)
            false
        }
    }

    fun generate(
        prompt: String,
        params: GenerationParams = GenerationParams(),
        maxTokens: Int = 1024
    ): String {
        return try {
            nativeGenerate(
                prompt = prompt,
                temperature = params.temperature,
                topK = params.topK,
                maxTokens = maxTokens,
                topP = params.topP,
                repeatPenalty = params.repeatPenalty
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error in nativeGenerate", e)
            "Ошибка при локальном запуске Cotype Nano 1.5B."
        }
    }
}
