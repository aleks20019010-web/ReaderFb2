package com.nightread.app.data

/**
 * Отчёт о результатах синхронизации.
 */
data class SyncResultReporter(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0
) {
    fun toSummaryString(): String {
        return "Загружено: $uploaded, скачано: $downloaded, пропущено: $skipped, ошибок: $errors"
    }
}
