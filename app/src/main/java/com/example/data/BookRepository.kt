package com.example.data

import kotlinx.coroutines.flow.Flow

class BookRepository(
    private val bookDao: BookDao,
    private val noteDao: NoteDao
) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()

    suspend fun getBookById(id: Int): BookEntity? = bookDao.getBookById(id)

    suspend fun insertBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun insertBooks(books: List<BookEntity>) = bookDao.insertBooks(books)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    suspend fun updateProgress(id: Int, charOffset: Int) {
        bookDao.updateProgress(id, charOffset, System.currentTimeMillis())
    }

    suspend fun deleteBookById(id: Int) = bookDao.deleteBookById(id)

    fun getNotesForBook(bookId: Int): Flow<List<NoteEntity>> = noteDao.getNotesForBook(bookId)

    suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)

    suspend fun deleteNoteById(id: Int) = noteDao.deleteNoteById(id)
}
