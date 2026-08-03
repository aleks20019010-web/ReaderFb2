package com.nightread.app.data

import android.content.Context
import android.os.Environment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nightread.app.R
import java.io.File

@Database(entities = [BookEntity::class, NoteEntity::class, CloudFileEntity::class, CacheEntry::class, DeletedBook::class], version = 12, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao
    abstract fun cloudFileDao(): CloudFileDao
    abstract fun sha1CacheDao(): Sha1CacheDao
    abstract fun deletedBookDao(): DeletedBookDao

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
                if (externalFile.exists() && !internalFile.exists()) {
                    internalFile.parentFile?.mkdirs()
                    externalFile.copyTo(internalFile, overwrite = true)
                    val externalWal = File(externalFile.path + "-wal")
                    if (externalWal.exists()) externalWal.copyTo(File(internalFile.path + "-wal"), overwrite = true)
                    val externalShm = File(externalFile.path + "-shm")
                    if (externalShm.exists()) externalShm.copyTo(File(internalFile.path + "-shm"), overwrite = true)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Error migrating external DB to internal", e)
            }
            internalFile.parentFile?.mkdirs()
            return internalFile
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
