package com.nightread.app.syncprogress

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Book::class, ReadingProgress::class, Sha1Cache::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Iso8601TypeConverter::class)
abstract class SyncDatabase : RoomDatabase() {

    abstract fun syncBookDao(): SyncBookDao
    abstract fun syncReadingProgressDao(): SyncReadingProgressDao
    abstract fun syncSha1CacheDao(): SyncSha1CacheDao

    companion object {
        @Volatile
        private var INSTANCE: SyncDatabase? = null

        fun getDatabase(context: Context): SyncDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SyncDatabase::class.java,
                    "sync_progress.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
