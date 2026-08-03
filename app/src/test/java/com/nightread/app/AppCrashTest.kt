package com.nightread.app

import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import org.robolectric.RuntimeEnvironment
import android.content.Intent

@RunWith(RobolectricTestRunner::class)
class AppCrashTest {
    init {
        ShadowLog.stream = System.out
    }

    @Test
    fun testMainActivityStarts() {
        try {
            val activity = Robolectric.buildActivity(com.nightread.app.MainActivity::class.java).create().start().resume().get()
            assert(activity != null)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testBookReaderActivityStarts() {
        try {
            val intent = Intent(RuntimeEnvironment.getApplication(), com.nightread.app.ui.BookReaderActivity::class.java).apply {
                putExtra("BOOK_SHA1", "test_sha1")
            }
            val activity = Robolectric.buildActivity(com.nightread.app.ui.BookReaderActivity::class.java, intent).create().start().resume().get()
            assert(activity != null)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testBookScannerScanBooks() {
        try {
            val context = RuntimeEnvironment.getApplication()
            val db = com.nightread.app.data.AppDatabase.getDatabase(context)
            val scanner = com.nightread.app.service.NewBookScanner(context, db.bookDao())
            kotlinx.coroutines.runBlocking {
                scanner.scanBooks()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testBookReaderWithActualBook() {
        try {
            val context = RuntimeEnvironment.getApplication()
            val db = com.nightread.app.data.AppDatabase.getDatabase(context)
            
            // Create a dummy FB2 file
            val tempDir = context.cacheDir
            val fb2File = File(tempDir, "test_book.fb2")
            fb2File.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\">\n" +
                    "    <description>\n" +
                    "        <title-info>\n" +
                    "            <book-title>Test Book</book-title>\n" +
                    "            <author><first-name>John</first-name><last-name>Doe</last-name></author>\n" +
                    "        </title-info>\n" +
                    "    </description>\n" +
                    "    <body>\n" +
                    "        <section>\n" +
                    "            <title><p>Chapter 1</p></title>\n" +
                    "            <p>This is a test book content.</p>\n" +
                    "        </section>\n" +
                    "    </body>\n" +
                    "</FictionBook>")
            
            // Compute its SHA1
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val sha1 = md.digest(fb2File.readBytes()).joinToString("") { "%02x".format(it) }
            
            // Insert into Database
            val book = com.nightread.app.data.BookEntity(
                sha1 = sha1,
                title = "Test Book",
                author = "John Doe",
                filePath = fb2File.absolutePath,
                fileSize = fb2File.length()
            )
            
            kotlinx.coroutines.runBlocking {
                db.bookDao().insertBook(book)
            }
            
            // Launch Activity
            val intent = Intent(context, com.nightread.app.ui.BookReaderActivity::class.java).apply {
                putExtra("BOOK_SHA1", sha1)
            }
            val activity = Robolectric.buildActivity(com.nightread.app.ui.BookReaderActivity::class.java, intent).create().start().resume().get()
            assert(activity != null)
            
            // Let it run for a bit to trigger the async load
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
