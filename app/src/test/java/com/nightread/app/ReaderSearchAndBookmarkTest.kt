package com.nightread.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*
import com.nightread.app.ui.customlayout.ReaderSearchEngine

@RunWith(RobolectricTestRunner::class)
class ReaderSearchAndBookmarkTest {

    @Test
    fun testSearchFound() {
        val text = "The old house was standing near the old road."
        val engine = ReaderSearchEngine(text)
        val results = kotlinx.coroutines.runBlocking { engine.search("old") }
        
        assertEquals(2, results.size)
        assertEquals("old", results[0].matchedText)
        assertEquals(4, results[0].sourceStartOffset)
        assertEquals(7, results[0].sourceEndOffset)
        
        assertEquals(36, results[1].sourceStartOffset)
    }

    @Test
    fun testSearchNotFound() {
        val text = "The old house was standing near the old road."
        val engine = ReaderSearchEngine(text)
        val results = kotlinx.coroutines.runBlocking { engine.search("castle") }
        
        assertTrue(results.isEmpty())
    }

    @Test
    fun testSearchCaseInsensitive() {
        val text = "The OLD house was standing near the old road."
        val engine = ReaderSearchEngine(text)
        val results = kotlinx.coroutines.runBlocking { engine.search("old") }
        
        assertEquals(2, results.size)
        assertEquals("OLD", results[0].matchedText)
    }

    @Test
    fun testSearchMultipleResults() {
        val text = "war war war"
        val engine = ReaderSearchEngine(text)
        val results = kotlinx.coroutines.runBlocking { engine.search("war") }
        
        assertEquals(3, results.size)
    }

}
