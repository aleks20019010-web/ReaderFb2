package com.nightread.app.scanner

sealed class ScannerState {
    object Idle : ScannerState()
    object Initializing : ScannerState()
    object Scanning : ScannerState()
    data class Processing(
        val current: Int,
        val total: Int,
        val added: Int,
        val currentFile: String,
        val eta: String,
        val speed: String
    ) : ScannerState()
    data class Completed(val added: Int, val total: Int) : ScannerState()
    data class Error(val message: String) : ScannerState()
    object Cancelled : ScannerState()
}

data class ScanProgress(
    val phase: ScanPhase = ScanPhase.IDLE,
    val phaseProgress: Int = 0,
    val overallProgress: Int = 0,
    val booksFound: Int = 0,
    val booksProcessed: Int = 0,
    val booksAdded: Int = 0,
    val booksSkipped: Int = 0,
    val currentFile: String = "",
    val eta: String = "",
    val speed: String = "",
    val memoryUsed: String = ""
) {
    fun toScannerState(): ScannerState {
        return when (phase) {
            ScanPhase.IDLE -> ScannerState.Idle
            ScanPhase.INITIALIZING -> ScannerState.Initializing
            ScanPhase.SCANNING_FILES -> ScannerState.Scanning
            ScanPhase.ANALYZING_CACHE -> ScannerState.Scanning
            ScanPhase.PROCESSING_BOOKS -> ScannerState.Processing(
                current = booksProcessed,
                total = booksFound,
                added = booksAdded,
                currentFile = currentFile,
                eta = eta,
                speed = speed
            )
            ScanPhase.COMPLETED -> ScannerState.Completed(booksAdded, booksFound)
            ScanPhase.CANCELLED -> ScannerState.Cancelled
            ScanPhase.ERROR -> ScannerState.Error(currentFile)
        }
    }
}

enum class ScanPhase {
    IDLE,
    INITIALIZING,
    SCANNING_FILES,
    ANALYZING_CACHE,
    PROCESSING_BOOKS,
    COMPLETED,
    CANCELLED,
    ERROR
}
