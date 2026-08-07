package com.nightread.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScannerState(
    val isScanning: Boolean = false,
    val status: String = "",
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val addedBooks: Int = 0,
    val skippedBooks: Int = 0,
    val progress: Int = 0
)

typealias ScanState = ScannerState

object NewBookScanState {
    private val _state = MutableStateFlow(ScannerState())
    val state = _state.asStateFlow()

    fun updateState(newState: ScannerState) {
        val current = _state.value
        if (!current.isScanning && newState.isScanning) {
            _state.value = newState
            return
        }
        if (current.isScanning && newState.isScanning) {
            val total = if (newState.totalFiles == 0 && current.totalFiles > 0) current.totalFiles else newState.totalFiles
            val processed = maxOf(current.processedFiles, newState.processedFiles)
            val added = maxOf(current.addedBooks, newState.addedBooks)
            val skipped = maxOf(current.skippedBooks, newState.skippedBooks)
            val prog = if (newState.progress == 0 && current.progress > 0) current.progress else newState.progress
            
            _state.value = newState.copy(
                totalFiles = total,
                processedFiles = processed,
                addedBooks = added,
                skippedBooks = skipped,
                progress = prog
            )
            return
        }
        _state.value = newState
    }

    fun reset() {
        _state.value = ScannerState()
    }
}
