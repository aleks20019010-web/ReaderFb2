package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SplashActivityTest {
    @Test
    fun testSplashActivityStarts() {
        val activity = Robolectric.buildActivity(SplashActivity::class.java).create().start().resume().get()
        assert(activity != null)
    }
}
