package com.nightread.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class BookRepository(
    private val bookDao: BookDao,
    private val noteDao: NoteDao
) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun filterBooksByFormat(books: List<BookEntity>, showAll: Boolean): List<BookEntity> {
        if (showAll) return books
        return books.filter { book ->
            val path = book.filePath?.lowercase() ?: ""
            path.endsWith(".fb2") || path.endsWith(".fb2.zip") || path.endsWith(".epub") || path.endsWith(".zip") || book.filePath.isNullOrEmpty()
        }
    }

    fun getFilteredBooks(showAll: Boolean): Flow<List<BookEntity>> {
        return bookDao.getAllBooks().map { books ->
            filterBooksByFormat(books, showAll)
        }
    }

    fun searchBooks(query: String): Flow<List<BookEntity>> {
        val sqlQuery = "%$query%"
        return bookDao.searchBooks(sqlQuery)
    }

    suspend fun getBookBySha1(sha1: String): BookEntity? = bookDao.getBookBySha1(sha1)

    suspend fun getBooksBySha1(sha1: String): List<BookEntity> = withContext(Dispatchers.IO) {
        bookDao.getBooksBySha1(sha1)
    }

    fun getBooksByAuthor(author: String): Flow<List<BookEntity>> = bookDao.getBooksByAuthor(author)

    fun getBooksBySeries(series: String): Flow<List<BookEntity>> = bookDao.getBooksBySeries(series)

    suspend fun insertBook(book: BookEntity): Long = withContext(Dispatchers.IO) {
        Log.d("BookRepo", "Inserting single book: ${book.title}")
        val id = bookDao.insertBook(book)
        Log.d("BookRepo", "Inserted single book with ID: $id")
        id
    }

    suspend fun insertBookIfUnique(book: BookEntity): Boolean = withContext(Dispatchers.IO) {
        Log.d("BookRepo", "Inserting if unique: ${book.title}")
        val inserted = bookDao.insertBookIfUnique(book)
        Log.d("BookRepo", "Insert if unique result: $inserted")
        inserted
    }

    suspend fun insertBookSafely(book: BookEntity): Boolean = withContext(Dispatchers.IO) {
        bookDao.insertBookSafely(book)
    }
    
    suspend fun insertBooks(books: List<BookEntity>) = withContext(Dispatchers.IO) {
        Log.d("BookRepo", "Inserting ${books.size} books")
        bookDao.insertBooks(books)
        Log.d("BookRepo", "Inserted ${books.size} books successfully")
    }

    suspend fun saveBooks(books: List<BookEntity>) {
        withContext(Dispatchers.IO) {
            Log.d("BookRepo", "Inserting ${books.size} books")
            bookDao.insertBooks(books)
            Log.d("BookRepo", "Inserted ${books.size} books successfully")
        }
    }

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun updateProgress(sha1: String, charOffset: Int) {
        bookDao.updateProgress(sha1, charOffset, System.currentTimeMillis())
    }

    suspend fun updateProgressAndPage(sha1: String, charOffset: Int, pageIndex: Int, totalChars: Int = 0) {
        bookDao.updateProgressAndPage(sha1, charOffset, pageIndex, totalChars, System.currentTimeMillis())
    }

    suspend fun deleteBookBySha1(sha1: String) = bookDao.deleteBookBySha1(sha1)

    suspend fun getBooksCount(): Int = withContext(Dispatchers.IO) {
        bookDao.getBooksCount()
    }

    suspend fun getLastReadBook(): BookEntity? = withContext(Dispatchers.IO) {
        bookDao.getLastReadBook()
    }

    suspend fun deleteAllBooks() = bookDao.deleteAllBooks()

    suspend fun clearLibrary() = withContext(Dispatchers.IO) {
        bookDao.deleteAllBooks()
        bookDao.deleteAllScannedFiles()
    }

    suspend fun resetDatabase() = withContext(Dispatchers.IO) {
        // Fallback to destructive migration or clear all tables
        bookDao.deleteAllBooks()
        bookDao.deleteAllScannedFiles()
    }

    suspend fun clearScanCache(context: android.content.Context) = withContext(Dispatchers.IO) {
        bookDao.deleteAllScannedFiles()
        val prefs = context.getSharedPreferences("book_scanner_cache", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun getReadingBooks(): Flow<List<BookEntity>> = bookDao.getReadingBooks()

    fun getFavoriteBooks(): Flow<List<BookEntity>> = bookDao.getFavoriteBooks()

    fun getNotesForBook(bookSha1: String): Flow<List<NoteEntity>> = noteDao.getNotesForBook(bookSha1)

    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)

    suspend fun deleteNoteById(id: Int) = noteDao.deleteNoteById(id)

    suspend fun checkForNewBooks(context: android.content.Context) {
        val scanner = com.nightread.app.service.NewBookScanner(context, bookDao)
        scanner.checkForNewBooks()
    }
}
