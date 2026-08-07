package com.nightread.app.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseBackupManager(private val context: Context) {
    
    private val databaseProvider = DatabaseProvider(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()
    
    sealed class BackupState {
        object Idle : BackupState()
        object InProgress : BackupState()
        data class Success(val file: File) : BackupState()
        data class Error(val message: String) : BackupState()
    }
    
    /**
     * Автоматический бэкап при определенных событиях
     */
    fun scheduleAutoBackup() {
        scope.launch {
            try {
                _backupState.value = BackupState.InProgress
                
                val result = databaseProvider.createAutoBackup()
                _backupState.value = result.fold(
                    onSuccess = { BackupState.Success(it) },
                    onFailure = { BackupState.Error(it.message ?: "Unknown error") }
                )
                
                // Очистка старых бэкапов
                cleanupOldBackups(keepCount = 5)
            } catch (e: Exception) {
                _backupState.value = BackupState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Очистка старых бэкапов
     */
    private fun cleanupOldBackups(keepCount: Int) {
        try {
            val backupDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "database_backup"
            )
            
            if (!backupDir.exists()) return
            
            val backups = backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("books_") && file.name.endsWith(".db")
            }?.sortedByDescending { it.lastModified() } ?: return
            
            if (backups.size > keepCount) {
                backups.drop(keepCount).forEach { it.delete() }
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupManager", "Failed to cleanup old backups", e)
        }
    }
    
    /**
     * Список доступных бэкапов
     */
    fun getAvailableBackups(): List<BackupInfo> {
        return try {
            val backupDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "database_backup"
            )
            
            if (!backupDir.exists()) return emptyList()
            
            backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("books_") && file.name.endsWith(".db")
            }?.map { file ->
                BackupInfo(
                    context = context,
                    file = file,
                    timestamp = file.lastModified(),
                    size = file.length(),
                    name = file.name
                )
            }?.sortedByDescending { it.timestamp } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    data class BackupInfo(
        private val context: Context,
        val file: File,
        val timestamp: Long,
        val size: Long,
        val name: String
    ) {
        fun getFormattedDate(): String {
            return SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                .format(Date(timestamp))
        }
        
        fun getFormattedSize(): String {
            return android.text.format.Formatter.formatFileSize(context, size)
        }
    }
}
