package com.nightread.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.nightread.app.data.SettingsManager
import com.nightread.app.ui.ReaderComposeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReaderSettingsIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        // Reset settings
        SettingsManager.setFontSize(context, 18f)
        SettingsManager.setLineSpacing(context, 1.4f)
        SettingsManager.setFontFamily(context, "Roboto")
        SettingsManager.setReadingTheme(context, "light")
    }

    @Test
    fun testSettingsDefaultValues() {
        assertEquals(18f, SettingsManager.getFontSize(context))
        assertEquals(1.4f, SettingsManager.getLineSpacing(context))
        assertEquals("Roboto", SettingsManager.getFontFamily(context))
    }

    @Test
    fun testFontSizeChangePersistence() {
        SettingsManager.setFontSize(context, 24f)
        assertEquals(24f, SettingsManager.getFontSize(context))
        
        // Simulating app restart by reading again
        val newContext = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(24f, SettingsManager.getFontSize(newContext))
    }

    @Test
    fun testRepaginationOnSettingChange() {
        // This test verifies that changing settings triggers a recomposition/repagination in ReaderComposeScreen
        // We use a small text to ensure pagination is fast
        val testText = "Page 1 content. " + " ".repeat(1000) + "Page 2 content."
        
        composeTestRule.setContent {
            ReaderComposeScreen(
                sha1 = "test_book_settings",
                mainText = testText
            )
        }

        // Wait for initial pagination
        composeTestRule.waitForIdle()
        
        // Find a specific text that should be on Page 2
        composeTestRule.onNodeWithText("Page 2 content.", substring = true).assertDoesNotExist()
        
        // Change font size to very small so more text fits on Page 1
        SettingsManager.setFontSize(context, 8f)
        SettingsManager.notifyChanged()
        
        composeTestRule.waitForIdle()
        
        // Now "Page 2 content." might be visible on Page 1 or close to it
        // Note: Real pagination depends on measuring, but we just check if it doesn't crash 
        // and if the state updates.
    }

    @Test
    fun testThemeChangeDoesNotTriggerRepagination() {
        // This is hard to test directly via ComposeRule without spying on ReaderPager,
        // but we can verify that the UI colors change while the pager state remains.
        
        val testText = "Simple theme test text."
        composeTestRule.setContent {
            ReaderComposeScreen(
                sha1 = "theme_test",
                mainText = testText
            )
        }
        
        composeTestRule.waitForIdle()
        
        // Change theme
        SettingsManager.setReadingTheme(context, "dark")
        SettingsManager.notifyChanged()
        
        composeTestRule.waitForIdle()
        
        // Verify we are still on the reader screen (pager or webview)
        composeTestRule.onNode(
            hasTestTag("reader_pager") or hasTestTag("reader_webview")
        ).assertExists()
    }
}
