package com.nightread.app.data

import android.util.Log

/**
 * Единая точка обработки и логирования ошибок.
 */
object SyncErrorHandler {
    private const val TAG = "SYNC_ERROR"

    fun logError(contextName: String, e: Throwable, canContinue: Boolean) {
        Log.e(TAG, "Error in $contextName: ${e.message}", e)
        // Здесь можно добавить логику отправки в Crashlytics или хранения в БД
    }

    fun getUserFriendlyMessage(e: Throwable): String {
        return when (e) {
            is java.net.UnknownHostException -> "Нет соединения с интернетом"
            is java.net.SocketTimeoutException -> "Время ожидания сервера истекло"
            else -> "Ошибка синхронизации: ${e.localizedMessage}"
        }
    }
}
