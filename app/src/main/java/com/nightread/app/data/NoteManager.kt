package com.nightread.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NoteManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val noteDao = db.noteDao()

    fun getNotesForBook(bookId: String): Flow<List<NoteEntity>> {
        return noteDao.getNotesForBook(bookId)
    }

    fun getAllNotes(): Flow<List<NoteEntity>> {
        return noteDao.getAllNotes()
    }

    suspend fun addNote(bookId: String, bookTitle: String, selectedText: String, noteText: String, charOffset: Int = 0): Long {
        return withContext(Dispatchers.IO) {
            val entity = NoteEntity(
                bookId = bookId,
                bookTitle = bookTitle,
                selectedText = selectedText,
                noteText = noteText,
                charOffset = charOffset,
                timestamp = System.currentTimeMillis()
            )
            noteDao.insertNote(entity)
        }
    }

    suspend fun deleteNote(noteId: Int) {
        withContext(Dispatchers.IO) {
            noteDao.deleteNoteById(noteId)
        }
    }
}
