package com.nightread.app.features.sync.domain

import com.nightread.app.syncprogress.SyncReadingProgressUseCase as SyncReadingProgressServiceUseCase

class SyncReadingProgressUseCase(private val syncUseCase: SyncReadingProgressServiceUseCase) {

    suspend operator fun invoke(
        token: String,
        accountId: String,
        cloudPath: String,
        fileSize: Long,
        fileModified: String
    ) = syncUseCase.syncBookProgress(token, accountId, cloudPath, fileSize, fileModified)
}
