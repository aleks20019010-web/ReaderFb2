package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import com.nightread.app.MainActivity

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    @Test
    fun testActivityStarts() {
        Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
    }
}
