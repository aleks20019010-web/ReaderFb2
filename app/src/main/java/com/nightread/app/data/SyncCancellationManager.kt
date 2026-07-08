package com.nightread.app.data

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Безопасное управление флагом отмены синхронизации.
 */
object SyncCancellationManager {
    private val isCancelled = AtomicBoolean(false)
    private const val TAG = "SYNC_CANCEL"

    fun setCancelled(value: Boolean) {
        if (value) android.util.Log.d(TAG, "Sync cancellation requested")
        isCancelled.set(value)
    }

    fun isCancelled(): Boolean = isCancelled.get()

    fun reset() {
        isCancelled.set(false)
    }
}
