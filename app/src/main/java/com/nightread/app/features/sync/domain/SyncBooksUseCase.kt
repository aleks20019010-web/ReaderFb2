package com.nightread.app.features.sync.domain

import com.nightread.app.data.SyncStats
import com.nightread.app.data.YandexSyncManager

class SyncBooksUseCase(private val syncManager: YandexSyncManager) {

    suspend operator fun invoke(onProgress: (String) -> Unit = {}): SyncStats? {
        return syncManager.calculateSyncStats(onProgress)
    }
}
