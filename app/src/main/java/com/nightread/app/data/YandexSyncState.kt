package com.nightread.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Состояние фоновой синхронизации с Яндекс Диском.
 * Позволяет передавать информацию о прогрессе между SyncService и YandexSyncFragment в реальном времени.
 */
data class YandexSyncState(
    val isRunning: Boolean = false,
    val stage: Stage = Stage.IDLE,
    val statusText: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
    val remainingTimeSeconds: Long = -1L,
    val downloadedCount: Int = 0,
    val uploadedCount: Int = 0,
    val success: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
    val currentFileName: String? = null,
    val currentFileBytesTransferred: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val duplicatesToResolve: List<DuplicateGroup>? = null
) {
    enum class Stage {
        IDLE, PREPARING, SCANNING, DOWNLOADING, UPLOADING, PROGRESS_SYNC, COMPLETED, ERROR
    }

    companion object {
        private val _state = MutableStateFlow(YandexSyncState())
        val state: StateFlow<YandexSyncState> = _state.asStateFlow()

        var duplicateResolution: kotlinx.coroutines.CompletableDeferred<List<String>>? = null

        fun update(transform: (YandexSyncState) -> YandexSyncState) {
            _state.value = transform(_state.value)
        }

        fun reset() {
            _state.value = YandexSyncState()
            duplicateResolution = null
        }
    }
}
