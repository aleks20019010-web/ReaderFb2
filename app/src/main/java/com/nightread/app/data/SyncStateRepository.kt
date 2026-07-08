package com.nightread.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Централизованное хранилище состояния синхронизации.
 */
class SyncStateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("sync_state_repo", Context.MODE_PRIVATE)
    private val TAG = "SYNC_STATE"

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SyncState> = _state

    data class SyncState(
        val isRunning: Boolean,
        val stage: String,
        val progress: Int,
        val lastError: String?,
        val startTime: Long
    )

    fun updateState(isRunning: Boolean, stage: String, progress: Int, lastError: String? = null) {
        val newState = SyncState(isRunning, stage, progress, lastError, if (isRunning) System.currentTimeMillis() else 0L)
        _state.value = newState
        saveState(newState)
        Log.d(TAG, "State updated: $newState")
    }

    private fun saveState(state: SyncState) {
        prefs.edit().apply {
            putBoolean("is_running", state.isRunning)
            putString("stage", state.stage)
            putInt("progress", state.progress)
            putString("last_error", state.lastError)
            putLong("start_time", state.startTime)
            apply()
        }
    }

    private fun loadState(): SyncState {
        return SyncState(
            prefs.getBoolean("is_running", false),
            prefs.getString("stage", "IDLE") ?: "IDLE",
            prefs.getInt("progress", 0),
            prefs.getString("last_error", null),
            prefs.getLong("start_time", 0L)
        )
    }
}
