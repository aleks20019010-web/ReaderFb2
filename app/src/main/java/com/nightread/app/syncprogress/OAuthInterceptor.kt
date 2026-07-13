package com.nightread.app.syncprogress

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp интерцептор для автоматической авторизации запросов через OAuth заголовок.
 */
class OAuthInterceptor(private val tokenProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenProvider()
        
        return if (token.isNotBlank()) {
            val authHeaderValue = if (token.startsWith("OAuth ")) token else "OAuth $token"
            val requestWithAuth = originalRequest.newBuilder()
                .header("Authorization", authHeaderValue)
                .build()
            chain.proceed(requestWithAuth)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
