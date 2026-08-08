package com.nightread.app.data

import android.content.Context
import android.os.Environment
import androidx.room.Database
import androidx.room.RoomDatabase
import com.nightread.app.R
import java.io.File

@Database(
    entities = [
        BookEntity::class,
        NoteEntity::class,
        CloudFileEntity::class,
        CacheEntry::class,
        DeletedBook::class,
        ReadingProgressEntity::class,
        BookCache::class,
        ScannerCacheEntity::class
    ],
    version = 16,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao
    abstract fun cloudFileDao(): CloudFileDao
    abstract fun sha1CacheDao(): Sha1CacheDao
    abstract fun deletedBookDao(): DeletedBookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookCacheDao(): BookCacheDao
    abstract fun scannerCacheDao(): ScannerCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getAppDir(context: Context): File {
            val appName = try {
                context.getString(R.string.app_name)
            } catch (e: Exception) {
                "NightRead"
            }
            val docsPath = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath
                ?: (Environment.getExternalStorageDirectory().absolutePath + "/Documents")
            val dir = File(docsPath, appName)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        fun getReaderFb2Dir(context: Context): File = getAppDir(context)

        fun getDatabaseFile(context: Context, name: String): File {
            return context.getDatabasePath(name)
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseProvider.getInstance(context).also {
                    INSTANCE = it
                }
            }
        }
        
        /**
         * Принудительное закрытие БД (использовать с осторожностью)
         */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                DatabaseExecutors.shutdown()
            }
        }
    }
}
