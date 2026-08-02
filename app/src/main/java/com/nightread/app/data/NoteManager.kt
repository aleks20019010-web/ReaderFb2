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

    suspend fun addNote(
        bookId: String,
        bookTitle: String,
        selectedText: String,
        noteText: String,
        charOffset: Int = 0,
        locatorJson: String? = null,
        color: Int = 0xFFFFEE58.toInt()
    ): Long {
        return withContext(Dispatchers.IO) {
            val entity = NoteEntity(
                bookId = bookId,
                bookTitle = bookTitle,
                selectedText = selectedText,
                noteText = noteText,
                charOffset = charOffset,
                timestamp = System.currentTimeMillis(),
                locatorJson = locatorJson,
                color = color
            )
            noteDao.insertNote(entity)
        }
    }

    suspend fun deleteNote(noteId: Int) {
        withContext(Dispatchers.IO) {
            noteDao.deleteNoteById(noteId)
        }
    }

    suspend fun getNoteById(noteId: Int): NoteEntity? {
        return withContext(Dispatchers.IO) {
            noteDao.getNoteById(noteId)
        }
    }

    suspend fun updateNoteText(noteId: Int, newNoteText: String) {
        withContext(Dispatchers.IO) {
            val note = noteDao.getNoteById(noteId) ?: return@withContext
            val updated = note.copy(noteText = newNoteText, timestamp = System.currentTimeMillis())
            noteDao.insertNote(updated)
        }
    }
}
