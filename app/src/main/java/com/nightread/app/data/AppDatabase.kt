package com.nightread.app.data

import android.content.Context
import android.os.Environment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(entities = [BookEntity::class, NoteEntity::class, CloudFileEntity::class, CacheEntry::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao
    abstract fun cloudFileDao(): CloudFileDao
    abstract fun sha1CacheDao(): Sha1CacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getReaderFb2Dir(context: Context): File {
            val docsPath = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath
                ?: (Environment.getExternalStorageDirectory().absolutePath + "/Documents")
            val dir = File(docsPath, "ReaderFb2")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = File(getReaderFb2Dir(context), "books.db")
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
