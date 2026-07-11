package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import com.nightread.app.SplashActivity

@RunWith(RobolectricTestRunner::class)
class SplashActivityTest {
    @Test
    fun testActivityStarts() {
        Robolectric.buildActivity(SplashActivity::class.java).create().start().resume().visible()
    }
}
