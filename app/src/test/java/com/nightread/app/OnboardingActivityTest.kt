package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import com.nightread.app.ui.OnboardingActivity

@RunWith(RobolectricTestRunner::class)
class OnboardingActivityTest {
    @Test
    fun testOnboardingActivityStarts() {
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).create().start().resume().get()
        assert(activity != null)
    }
}
