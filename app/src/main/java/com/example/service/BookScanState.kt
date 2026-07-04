package com.example.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

object BookScanState {
    val isScanning = MutableStateFlow(false)
    val scanProgressText = MutableStateFlow("")
    val totalFiles = MutableStateFlow(0)
    val processedFiles = MutableStateFlow(0)
    val errorText = MutableStateFlow<String?>(null)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
        isScanning.value = prefs.getBoolean("is_scanning", false)
        scanProgressText.value = prefs.getString("progress_text", "") ?: ""
        totalFiles.value = prefs.getInt("total_files", 0)
        processedFiles.value = prefs.getInt("processed_files", 0)
        errorText.value = prefs.getString("error_text", null)
    }

    fun updateScanning(context: Context, active: Boolean, text: String, total: Int = 0, processed: Int = 0, error: String? = null) {
        isScanning.value = active
        scanProgressText.value = text
        totalFiles.value = total
        processedFiles.value = processed
        errorText.value = error
        
        context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_scanning", active)
            .putString("progress_text", text)
            .putInt("total_files", total)
            .putInt("processed_files", processed)
            .putString("error_text", error)
            .apply()
    }
}
