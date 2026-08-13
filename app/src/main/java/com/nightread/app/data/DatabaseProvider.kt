package com.nightread.app.data

import android.content.Context
import android.os.Environment
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Провайдер базы данных с правильным управлением внутренним хранилищем.
 * База данных всегда находится во внутреннем хранилище приложения.
 * Внешнее хранилище используется только для бэкапов.
 */
class DatabaseProvider(private val context: Context) {
    
    companion object {
        private const val TAG = "DatabaseProvider"
        private const val DATABASE_NAME = "books.db"
        private const val BACKUP_DIR_NAME = "database_backup"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private val LOCK = Any()
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(LOCK) {
                INSTANCE ?: DatabaseProvider(context).buildDatabase().also {
                    INSTANCE = it
                }
            }
        }
    }
    
    private fun buildDatabase(): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            DATABASE_NAME
        )
        .addMigrations(*DatabaseMigrations.getAllMigrations())
        .setQueryExecutor(DatabaseExecutors.queryExecutor)
        .setTransactionExecutor(DatabaseExecutors.transactionExecutor)
        .fallbackToDestructiveMigrationOnDowngrade() // Только при понижении версии
        .build()
    }
    
    /**
     * База данных всегда во внутреннем хранилище - это безопасно и стабильно
     */
    private fun getDatabaseFile(): File {
        return context.getDatabasePath(DATABASE_NAME)
    }
    
    /**
     * Экспорт базы данных во внешнее хранилище для бэкапа
     */
    suspend fun exportDatabase(): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dbFile = getDatabaseFile()
            if (!dbFile.exists()) {
                return@withContext Result.failure(IOException("Database file not found"))
            }
            
            // Закрываем БД перед копированием
            closeDatabase()
            
            val backupFile = getBackupFile()
            backupFile.parentFile?.mkdirs()
            
            // Копируем основную БД
            dbFile.copyTo(backupFile, overwrite = true)
            
            // Копируем WAL файлы если есть
            copyWalFiles(dbFile, backupFile)
            
            // Переоткрываем БД
            getInstance(context)
            
            Result.success(backupFile)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to export database", e)
            Result.failure(e)
        }
    }
    
    /**
     * Импорт базы данных из внешнего хранилища
     */
    suspend fun importDatabase(backupFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists()) {
                return@withContext Result.failure(IOException("Backup file not found"))
            }
            
            // Проверяем целостность бэкапа
            if (!isDatabaseValid(backupFile)) {
                return@withContext Result.failure(IOException("Backup file is corrupted"))
            }
            
            // Закрываем текущую БД
            closeDatabase()
            
            val dbFile = getDatabaseFile()
            dbFile.parentFile?.mkdirs()
            
            // Копируем бэкап
            backupFile.copyTo(dbFile, overwrite = true)
            
            // Копируем WAL файлы если есть
            copyWalFiles(backupFile, dbFile)
            
            // Переоткрываем БД
            getInstance(context)
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to import database", e)
            Result.failure(e)
        }
    }
    
    /**
     * Создание автоматического бэкапа
     */
    suspend fun createAutoBackup(): Result<File> {
        return try {
            // Сначала выполняем checkpoint для WAL
            executeCheckpoint()
            
            // Затем экспортируем
            exportDatabase()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Выполнение checkpoint для WAL
     */
    private fun executeCheckpoint() {
        try {
            val db = getInstance(context)
            db.query("PRAGMA wal_checkpoint(TRUNCATE);", emptyArray())
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to execute checkpoint", e)
        }
    }
    
    /**
     * Закрытие базы данных
     */
    private fun closeDatabase() {
        synchronized(LOCK) {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
    
    /**
     * Получение файла для бэкапа
     */
    private fun getBackupFile(): File {
        val backupDir = getBackupDirectory()
        val timestamp = System.currentTimeMillis()
        return File(backupDir, "books_$timestamp.db")
    }
    
    /**
     * Получение директории для бэкапов
     */
    private fun getBackupDirectory(): File {
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: throw IOException("External storage not available")
        
        val backupDir = File(docsDir, BACKUP_DIR_NAME)
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw IOException("Cannot create backup directory")
        }
        return backupDir
    }
    
    /**
     * Копирование WAL файлов
     */
    private fun copyWalFiles(source: File, destination: File) {
        val extensions = listOf("-wal", "-shm", "-journal")
        
        extensions.forEach { ext ->
            val sourceFile = File(source.path + ext)
            if (sourceFile.exists()) {
                try {
                    sourceFile.copyTo(
                        File(destination.path + ext),
                        overwrite = true
                    )
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to copy $ext file: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Проверка целостности БД
     */
    private fun isDatabaseValid(file: File): Boolean {
        return try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { true }
        } catch (e: Exception) {
            false
        }
    }
}
