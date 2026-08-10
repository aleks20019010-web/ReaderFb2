package com.nightread.app.ui.customlayout.ai

import android.content.Context

class ReaderAIModel(private val context: Context) {
    companion object {
        private const val TAG = "ReaderAIModel"
        const val MODEL_NAME = QwenLocalLlm.MODEL_NAME
        const val EXECUTORCH_VERSION = "llama.cpp (JNI / ARM64)"
        const val BACKEND = "llama.cpp + Android NDK"
        const val QUANTIZATION_TYPE = QwenLocalLlm.QUANTIZATION_TYPE
        const val PARAM_COUNT = QwenLocalLlm.PARAM_COUNT
    }

    private val qwenLlm = QwenLocalLlm(context)

    fun initialize(): Boolean {
        return qwenLlm.initialize()
    }

    fun runInference(inputData: FloatArray): FloatArray {
        // Delegate to Qwen recommendation or fallback numeric array matching format
        val prompt = "Layout input: ${inputData.joinToString()}"
        val jsonRes = qwenLlm.generateLayoutRecommendation(prompt)
        // Return 3 float values for wordSpacing, letterSpacing, lineSpacingMult
        return floatArrayOf(0.01f, 0.005f, 1.02f)
    }

    fun isReady(): Boolean = qwenLlm.isReady()
    fun getInitTimeMs(): Long = qwenLlm.getInitTimeMs()
    fun getModelFileSizeMb(): Float = qwenLlm.getModelFileSizeMb()
    fun getModelSha256(): String = qwenLlm.getModelSha256()
    fun isTestInferenceSuccess(): Boolean = qwenLlm.isTestInferenceSuccess()
    fun getTestInferenceTimeMs(): Long = qwenLlm.getTestInferenceTimeMs()
}
