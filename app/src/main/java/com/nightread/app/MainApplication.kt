package com.nightread.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("MainApplication", "MainApplication onCreate: Initializing app.")
        
        // Handle first launch and demo books insertion
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("first_launch", true)
        
        val database = AppDatabase.getDatabase(this)
        val bookDao = database.bookDao()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existingBooks = bookDao.getAllSha1s()
                if (isFirstLaunch || existingBooks.isEmpty()) {
                    Log.d("MainApplication", "First launch or empty database detected (isFirstLaunch=$isFirstLaunch, existingCount=${existingBooks.size}), preparing demo books.")
                    val demoBooks = listOf(
                        BookEntity(
                            sha1 = "dostoevsky_crime_punishment_sha1",
                            title = "Преступление и наказание",
                            author = "Фёдор Достоевский",
                            category = "Классика"
                        ),
                        BookEntity(
                            sha1 = "lermontov_hero_of_our_time_sha1",
                            title = "Герой нашего времени",
                            author = "Михаил Лермонтов",
                            category = "Классика"
                        ),
                        BookEntity(
                            sha1 = "pushkin_eugene_onegin_sha1",
                            title = "Евгений Онегин",
                            author = "Александр Пушкин",
                            category = "Поэзия"
                        ),
                        BookEntity(
                            sha1 = "chekhov_ward_number_6_sha1",
                            title = "Палата №6",
                            author = "Антон Чехов",
                            category = "Проза"
                        )
                    )
                    
                    bookDao.insertBooks(demoBooks)
                    prefs.edit().putBoolean("first_launch", false).apply()
                    Log.d("MainApplication", "Successfully inserted 4 demo books and marked first_launch as false.")
                } else {
                    Log.d("MainApplication", "Database already contains ${existingBooks.size} books, skipping demo books insertion.")
                }
            } catch (e: Exception) {
                Log.e("MainApplication", "Failed to insert demo books on first launch", e)
            }
        }
    }
}
