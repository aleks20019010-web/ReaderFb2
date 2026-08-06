package com.nightread.app.features.library.domain

import android.content.Context
import com.nightread.app.data.AppDatabase
import com.nightread.app.service.NewBookScanner

class ScanNewBooksUseCase(private val context: Context) {

    suspend operator fun invoke() {
        val database = AppDatabase.getDatabase(context)
        val scanner = NewBookScanner(context, database.bookDao())
        scanner.checkForNewBooks().join()
    }
}
