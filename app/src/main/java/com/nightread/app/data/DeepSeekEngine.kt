package com.nightread.app.data

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object DeepSeekEngine {

    private const val TAG = "DeepSeekEngine"
    private const val PREFS_NAME = "deepseek_cache_prefs"

    private val PROXIES = listOf(
        "https://api.deepseek-free.com/v1/chat/completions",
        "https://deepseek-proxy.workers.dev/v1/chat/completions",
        "https://deepseek-api-free.vercel.app/v1/chat/completions"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun query(
        context: Context,
        systemPrompt: String,
        userPrompt: String,
        temperature: Float = 0.7f,
        topP: Float = 0.95f,
        useCache: Boolean = true
    ): String {
        val cacheKey = hashKey("$systemPrompt::$userPrompt")
        if (useCache) {
            val cachedResponse = getFromCache(context, cacheKey)
            if (!cachedResponse.isNullOrBlank()) {
                Log.d(TAG, "Returning cached DeepSeek response for key $cacheKey")
                return cachedResponse
            }
        }

        val requestJson = JSONObject().apply {
            put("model", "deepseek-chat")
            val messages = JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            put("messages", messages)
            put("temperature", temperature)
            put("top_p", topP)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        var lastErrorMessage = ""

        for (endpoint in PROXIES) {
            try {
                Log.i(TAG, "Sending query to DeepSeek via $endpoint...")
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "NightReadApp/2.3.3 (Android)")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val parsedContent = parseDeepSeekResponse(responseBody)
                    if (parsedContent.isNotBlank()) {
                        saveToCache(context, cacheKey, parsedContent)
                        return parsedContent
                    }
                } else {
                    lastErrorMessage = "HTTP ${response.code}: ${responseBody ?: response.message}"
                    Log.w(TAG, "Proxy $endpoint returned error: $lastErrorMessage")
                }
            } catch (e: Exception) {
                lastErrorMessage = e.localizedMessage ?: "Network error"
                Log.e(TAG, "Error connecting to $endpoint", e)
            }
        }

        Log.e(TAG, "All DeepSeek proxies failed. Last error: $lastErrorMessage")
        return "Не удалось получить ответ от DeepSeek ($lastErrorMessage). Проверьте интернет-соединение."
    }

    private fun parseDeepSeekResponse(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val choices = root.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content")
                if (!content.isNullOrBlank()) {
                    return content.trim()
                }
            }
            root.optString("result", "")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON response: $jsonStr", e)
            ""
        }
    }

    private fun getFromCache(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(key, null)
    }

    private fun saveToCache(context: Context, key: String, value: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    private fun hashKey(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
}
