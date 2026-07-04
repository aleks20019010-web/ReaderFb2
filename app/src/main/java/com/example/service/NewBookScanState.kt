package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScannerState(
    val isScanning: Boolean = false,
    val status: String = "",
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val addedBooks: Int = 0,
    val skippedBooks: Int = 0
)

object NewBookScanState {
    private val _state = MutableStateFlow(ScannerState())
    val state = _state.asStateFlow()

    fun updateState(newState: ScannerState) {
        _state.value = newState
    }
}
