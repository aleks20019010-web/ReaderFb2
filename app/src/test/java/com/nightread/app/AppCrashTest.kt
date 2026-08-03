package com.nightread.app

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
}
