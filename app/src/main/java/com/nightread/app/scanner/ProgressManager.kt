package com.nightread.app.scanner

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProgressManager(
    private val updateIntervalMs: Long = 500L
) {
    companion object {
        private const val TAG = "ProgressManager"
    }
    
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()
    
    private val mutex = Mutex()
    private var lastUpdateTime = 0L
    private var pendingUpdate: ScanProgress? = null

    private fun publishToNewState(p: ScanProgress) {
        try {
            val isScanning = p.phase != ScanPhase.IDLE && 
                            p.phase != ScanPhase.COMPLETED && 
                            p.phase != ScanPhase.CANCELLED && 
                            p.phase != ScanPhase.ERROR
            
            val statusText = when (p.phase) {
                ScanPhase.IDLE -> ""
                ScanPhase.INITIALIZING -> "Инициализация..."
                ScanPhase.SCANNING_FILES -> if (p.currentFile.isNotEmpty()) p.currentFile else "Поиск файлов..."
                ScanPhase.ANALYZING_CACHE -> "Анализ кэша..."
                ScanPhase.PROCESSING_BOOKS -> "Обработка книг: ${p.booksProcessed}/${p.booksFound}${if (p.currentFile.isNotEmpty()) " (${p.currentFile})" else ""}"
                ScanPhase.COMPLETED -> if (p.eta.isNotEmpty()) p.eta else "Сканирование завершено. Добавлено: ${p.booksAdded}"
                ScanPhase.CANCELLED -> "Сканирование отменено"
                ScanPhase.ERROR -> p.currentFile.ifEmpty { "Ошибка сканирования" }
            }
            
            val newState = com.nightread.app.service.ScannerState(
                isScanning = isScanning,
                status = statusText,
                totalFiles = p.booksFound,
                processedFiles = p.booksProcessed,
                addedBooks = p.booksAdded,
                skippedBooks = p.booksSkipped,
                progress = p.overallProgress
            )
            
            // Оборачиваем в try-catch, чтобы ошибка не убила сканирование
            try {
                com.nightread.app.service.NewBookScanState.updateState(newState)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating NewBookScanState", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in publishToNewState", e)
        }
    }
    
    // Обычный update без suspend
    fun update(block: (ScanProgress) -> ScanProgress) {
        try {
            val current = _progress.value
            val newProgress = block(current)
            
            val now = System.currentTimeMillis()
            val timePassed = now - lastUpdateTime
            
            val isImportant = newProgress.phase != current.phase ||
                    newProgress.booksAdded != current.booksAdded ||
                    newProgress.booksProcessed != current.booksProcessed ||
                    newProgress.booksFound != current.booksFound
            
            if (timePassed >= updateIntervalMs || isImportant) {
                _progress.value = newProgress
                lastUpdateTime = now
                pendingUpdate = null
                publishToNewState(newProgress)
            } else {
                pendingUpdate = newProgress
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in update", e)
        }
    }
    
    // forceUpdate без suspend
    fun forceUpdate(block: (ScanProgress) -> ScanProgress) {
        try {
            val newProgress = block(_progress.value)
            _progress.value = newProgress
            lastUpdateTime = System.currentTimeMillis()
            pendingUpdate = null
            publishToNewState(newProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Error in forceUpdate", e)
        }
    }
    
    // flush без suspend
    fun flush() {
        try {
            pendingUpdate?.let {
                _progress.value = it
                pendingUpdate = null
                lastUpdateTime = System.currentTimeMillis()
                publishToNewState(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in flush", e)
        }
    }
    
    fun getCurrent(): ScanProgress = _progress.value
}
