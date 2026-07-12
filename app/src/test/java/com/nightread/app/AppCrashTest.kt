package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import org.robolectric.RuntimeEnvironment

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
}
