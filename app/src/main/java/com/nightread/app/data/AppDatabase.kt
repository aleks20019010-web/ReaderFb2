package com.nightread.app.data

import android.content.Context
import android.os.Environment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nightread.app.R
import java.io.File

@Database(entities = [BookEntity::class, NoteEntity::class, CloudFileEntity::class, CacheEntry::class, DeletedBook::class, ReadingProgressEntity::class], version = 13, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao
    abstract fun cloudFileDao(): CloudFileDao
    abstract fun sha1CacheDao(): Sha1CacheDao
    abstract fun deletedBookDao(): DeletedBookDao
    abstract fun readingProgressDao(): ReadingProgressDao

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
            val internalFile = context.getDatabasePath(name)
            val externalFile = File(getAppDir(context), name)
            try {
                if (internalFile.exists() && (!externalFile.exists() || externalFile.length() == 0L)) {
                    externalFile.parentFile?.mkdirs()
                    internalFile.copyTo(externalFile, overwrite = true)
                    val internalWal = File(internalFile.path + "-wal")
                    if (internalWal.exists()) internalWal.copyTo(File(externalFile.path + "-wal"), overwrite = true)
                    val internalShm = File(internalFile.path + "-shm")
                    if (internalShm.exists()) internalShm.copyTo(File(externalFile.path + "-shm"), overwrite = true)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Error syncing internal DB to external", e)
            }
            externalFile.parentFile?.mkdirs()
            return externalFile
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = getDatabaseFile(context, "books.db")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbFile.absolutePath
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
