package com.nightread.app.domain

import com.nightread.app.data.BookRepository
import com.nightread.app.data.NoteEntity
import kotlinx.coroutines.flow.Flow

class ManageNotesUseCase(private val bookRepository: BookRepository) {

    fun getNotesForBook(bookSha1: String): Flow<List<NoteEntity>> {
        return bookRepository.getNotesForBook(bookSha1)
    }

    suspend fun addNote(note: NoteEntity): Long {
        return bookRepository.insertNote(note)
    }

    suspend fun deleteNoteById(id: Int) {
        bookRepository.deleteNoteById(id)
    }
}
