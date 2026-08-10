package com.nightread.app.ui.customlayout.ai

import android.content.Context
import android.util.Log
import org.json.JSONObject

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
        val startTime = System.currentTimeMillis()
        val prompt = buildString {
            append("You are an offline ebook layout assistant. ")
            append("Return ONLY valid JSON with keys: wordSpacing (-0.05 to 0.08), ")
            append("letterSpacing (-0.02 to 0.03), lineSpacingMultiplier (0.95 to 1.10), confidence (0.0 to 1.0). ")
            append("Input: width=${inputData.getOrNull(0) ?: 1080f}, ")
            append("height=${inputData.getOrNull(1) ?: 1920f}, ")
            append("fontSize=${inputData.getOrNull(2) ?: 18f}, ")
            append("startOffset=${inputData.getOrNull(4) ?: 0f}, ")
            append("paragraphs=${inputData.getOrNull(6) ?: 0f}.")
        }

        Log.i(TAG, "AI_QWEN_INFERENCE_START: prompt_length=${prompt.length}")

        val jsonRes = qwenLlm.generateLayoutRecommendation(prompt)
        val inferenceTime = System.currentTimeMillis() - startTime

        var wordSpacing = 0.01f
        var letterSpacing = 0.005f
        var lineSpacingMultiplier = 1.02f
        var parseSuccess = false
        var fallbackReason = ""

        try {
            val trimmedJson = jsonRes.trim()
            val jsonToParse = if (trimmedJson.startsWith("```")) {
                val firstBrace = trimmedJson.indexOf('{')
                val lastBrace = trimmedJson.lastIndexOf('}')
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    trimmedJson.substring(firstBrace, lastBrace + 1)
                } else {
                    trimmedJson
                }
            } else {
                trimmedJson
            }

            val json = JSONObject(jsonToParse)
            val ws = json.optDouble("wordSpacing", Double.NaN).toFloat()
            val ls = json.optDouble("letterSpacing", Double.NaN).toFloat()
            val lsm = json.optDouble("lineSpacingMultiplier", Double.NaN).toFloat()
            val conf = json.optDouble("confidence", Double.NaN).toFloat()

            if (!ws.isNaN() && !ls.isNaN() && !lsm.isNaN() && !conf.isNaN() &&
                conf in 0.0f..1.0f &&
                ws in -0.05f..0.08f &&
                ls in -0.02f..0.03f &&
                lsm in 0.95f..1.10f
            ) {
                wordSpacing = ws
                letterSpacing = ls
                lineSpacingMultiplier = lsm
                parseSuccess = true
                Log.i(TAG, "AI_QWEN_PARSE_SUCCESS: wordSpacing=$wordSpacing, letterSpacing=$letterSpacing, lineSpacingMultiplier=$lineSpacingMultiplier, confidence=$conf, inferenceTimeMs=$inferenceTime, raw=$jsonRes")
            } else {
                fallbackReason = "Values out of range or missing keys: ws=$ws, ls=$ls, lsm=$lsm, conf=$conf"
                Log.w(TAG, "AI_QWEN_PARSE_FAILURE: $fallbackReason, raw=$jsonRes")
            }
        } catch (e: Exception) {
            fallbackReason = "JSON parse exception: ${e.message}"
            Log.w(TAG, "AI_QWEN_PARSE_FAILURE: $fallbackReason, raw=$jsonRes")
        }

        if (!parseSuccess) {
            wordSpacing = 0.01f
            letterSpacing = 0.005f
            lineSpacingMultiplier = 1.02f
            Log.i(TAG, "AI_QWEN_FALLBACK_USED: reason=$fallbackReason, inferenceTimeMs=$inferenceTime")
        }

        val lsmParam = (lineSpacingMultiplier - 1.0f) / 0.05f
        return floatArrayOf(wordSpacing, letterSpacing, lsmParam)
    }

    fun isReady(): Boolean = qwenLlm.isReady()
    fun getInitTimeMs(): Long = qwenLlm.getInitTimeMs()
    fun getModelFileSizeMb(): Float = qwenLlm.getModelFileSizeMb()
    fun getModelSha256(): String = qwenLlm.getModelSha256()
    fun isTestInferenceSuccess(): Boolean = qwenLlm.isTestInferenceSuccess()
    fun getTestInferenceTimeMs(): Long = qwenLlm.getTestInferenceTimeMs()
}

