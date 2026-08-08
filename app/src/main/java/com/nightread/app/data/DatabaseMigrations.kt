package com.nightread.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE books ADD COLUMN lastRead INTEGER DEFAULT 0")
            database.execSQL("ALTER TABLE books ADD COLUMN rating INTEGER DEFAULT 0")
        }
    }
    
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bookId INTEGER NOT NULL,
                    chapter TEXT,
                    text TEXT NOT NULL,
                    created INTEGER NOT NULL,
                    updated INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS reading_progress (
                    bookId INTEGER PRIMARY KEY,
                    chapter TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    percentage INTEGER NOT NULL,
                    lastRead INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE books ADD COLUMN coverPath TEXT")
        }
    }
    
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS cloud_files (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    path TEXT NOT NULL UNIQUE,
                    sha1 TEXT NOT NULL,
                    title TEXT,
                    size INTEGER NOT NULL,
                    modified INTEGER NOT NULL,
                    synced INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS cache_entries (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    expires INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS deleted_books (
                    sha1 TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    deletedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS scanner_cache (
                    sha1 TEXT PRIMARY KEY,
                    path TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    modified INTEGER NOT NULL,
                    scanned INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try {
                database.execSQL("ALTER TABLE reading_progress ADD COLUMN textOffset INTEGER DEFAULT 0")
            } catch (e: Exception) {}
        }
    }
    
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_15_16
        )
    }
}
