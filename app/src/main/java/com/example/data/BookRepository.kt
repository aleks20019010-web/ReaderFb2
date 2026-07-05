package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class BookRepository(
    private val bookDao: BookDao,
    private val noteDao: NoteDao
) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()

    suspend fun getBookBySha1(sha1: String): BookEntity? = bookDao.getBookBySha1(sha1)

    fun getBooksByAuthor(author: String): Flow<List<BookEntity>> = bookDao.getBooksByAuthor(author)

    fun getBooksBySeries(series: String): Flow<List<BookEntity>> = bookDao.getBooksBySeries(series)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun insertBookIfUnique(book: BookEntity): Boolean = bookDao.insertBookIfUnique(book)

    suspend fun insertBooks(books: List<BookEntity>) = bookDao.insertBooks(books)

    suspend fun saveBooks(books: List<BookEntity>) {
        withContext(Dispatchers.IO) {
            bookDao.insertBooks(books)
            Log.d("BookRepository", "Saved ${books.size} books")
        }
    }

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun updateProgress(sha1: String, charOffset: Int) {
        bookDao.updateProgress(sha1, charOffset, System.currentTimeMillis())
    }

    suspend fun deleteBookBySha1(sha1: String) = bookDao.deleteBookBySha1(sha1)

    fun getNotesForBook(bookSha1: String): Flow<List<NoteEntity>> = noteDao.getNotesForBook(bookSha1)

    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)

    suspend fun deleteNoteById(id: Int) = noteDao.deleteNoteById(id)
}
