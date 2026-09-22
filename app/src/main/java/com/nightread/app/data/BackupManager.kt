package com.nightread.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {
    private const val TAG = "BackupManager"

    suspend fun createBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val books = db.bookDao().getAllBooksSync()
        val notes = db.noteDao().getAllNotesSync()

        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())
        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))

        val booksArray = JSONArray()
        for (b in books) {
            val bookObj = JSONObject().apply {
                put("sha1", b.sha1)
                put("title", b.title)
                put("author", b.author ?: "")
                put("series", b.series ?: "")
                put("seriesIndex", b.seriesIndex ?: 0)
                put("currentProgressChar", b.currentProgressChar)
                put("currentPageIndex", b.currentPageIndex)
                put("totalCharacters", b.totalCharacters)
                put("lastReadTime", b.lastReadTime)
                put("isFavorite", b.isFavorite)
                put("isWantToRead", b.isWantToRead)
                put("category", b.category)
                put("language", b.language ?: "")
            }
            booksArray.put(bookObj)
        }
        root.put("books", booksArray)

        val notesArray = JSONArray()
        for (n in notes) {
            val noteObj = JSONObject().apply {
                put("id", n.id)
                put("bookId", n.bookId)
                put("bookTitle", n.bookTitle)
                put("selectedText", n.selectedText)
                put("noteText", n.noteText)
                put("timestamp", n.timestamp)
                put("charOffset", n.charOffset)
                put("locatorJson", n.locatorJson ?: "")
                put("color", n.color)
            }
            notesArray.put(noteObj)
        }
        root.put("notes", notesArray)

        root.toString(2)
    }

    suspend fun saveBackupToFile(context: Context): File = withContext(Dispatchers.IO) {
        val json = createBackupJson(context)
        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(backupDir, "NightRead_Backup_$dateStr.json")
        file.writeText(json, Charsets.UTF_8)
        file
    }

    suspend fun restoreBackupFromJson(context: Context, jsonString: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val db = AppDatabase.getDatabase(context)
            val bookDao = db.bookDao()
            val noteDao = db.noteDao()

            var booksRestored = 0
            var notesRestored = 0

            if (root.has("books")) {
                val booksArray = root.getJSONArray("books")
                for (i in 0 until booksArray.length()) {
                    val obj = booksArray.getJSONObject(i)
                    val sha1 = obj.optString("sha1", "")
                    if (sha1.isNotEmpty()) {
                        val existing = bookDao.getBookBySha1(sha1)
                        if (existing != null) {
                            val updated = existing.copy(
                                currentProgressChar = obj.optInt("currentProgressChar", existing.currentProgressChar),
                                currentPageIndex = obj.optInt("currentPageIndex", existing.currentPageIndex),
                                totalCharacters = if (obj.optInt("totalCharacters", 0) > 0) obj.optInt("totalCharacters") else existing.totalCharacters,
                                lastReadTime = maxOf(existing.lastReadTime, obj.optLong("lastReadTime", 0L)),
                                isFavorite = obj.optBoolean("isFavorite", existing.isFavorite),
                                isWantToRead = obj.optBoolean("isWantToRead", existing.isWantToRead)
                            )
                            bookDao.updateBook(updated)
                            booksRestored++
                        }
                    }
                }
            }

            if (root.has("notes")) {
                val notesArray = root.getJSONArray("notes")
                val notesToInsert = mutableListOf<NoteEntity>()
                for (i in 0 until notesArray.length()) {
                    val obj = notesArray.getJSONObject(i)
                    val bookId = obj.optString("bookId", "")
                    val bookTitle = obj.optString("bookTitle", "Книга")
                    val selectedText = obj.optString("selectedText", "")
                    val noteText = obj.optString("noteText", "")
                    if (bookId.isNotEmpty()) {
                        notesToInsert.add(
                            NoteEntity(
                                bookId = bookId,
                                bookTitle = bookTitle,
                                selectedText = selectedText,
                                noteText = noteText,
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                charOffset = obj.optInt("charOffset", 0),
                                locatorJson = obj.optString("locatorJson", null),
                                color = obj.optInt("color", 0xFFFFEE58.toInt())
                            )
                        )
                    }
                }
                if (notesToInsert.isNotEmpty()) {
                    noteDao.insertNotes(notesToInsert)
                    notesRestored = notesToInsert.size
                }
            }

            Pair(booksRestored, notesRestored)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup", e)
            throw e
        }
    }
}
