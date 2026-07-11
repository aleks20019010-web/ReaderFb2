package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import com.nightread.app.service.AutoDiscoveryService

@RunWith(RobolectricTestRunner::class)
class ServiceTest {
    @Test
    fun testStartService() {
        val context = RuntimeEnvironment.getApplication()
        AutoDiscoveryService.start(context)
    }
}
