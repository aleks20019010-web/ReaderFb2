package com.nightread.app.viewmodels

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.nightread.app.ui.ReaderViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var application: Application

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSearchRagSuccess() = testScope.runTest {
        val viewModel = ReaderViewModel(application)
        
        val results = viewModel.searchRag("test query")
        advanceUntilIdle()
        
        assertNotNull(results)
        assertTrue(viewModel.isSearching.value == false)
    }

    @Test
    fun testSearchRagCache() = testScope.runTest {
        val viewModel = ReaderViewModel(application)
        
        // Первый поиск
        val results1 = viewModel.searchRag("test")
        advanceUntilIdle()
        
        // Второй поиск (должен вернуть из кэша)
        val results2 = viewModel.searchRag("test")
        advanceUntilIdle()
        
        assertEquals(results1, results2)
    }

    @Test
    fun testSearchRagError() = testScope.runTest {
        val viewModel = ReaderViewModel(application)
        
        // Мокаем ошибку
        viewModel.searchRag("")
        advanceUntilIdle()
        
        val error = viewModel.searchError.value
        assertNotNull(error)
    }

    @Test
    fun testSearchRagCancellation() = testScope.runTest {
        val viewModel = ReaderViewModel(application)
        
        val job = launch {
            viewModel.searchRag("query")
        }
        
        job.cancel()
        advanceUntilIdle()
        
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun testClearSearch() = testScope.runTest {
        val viewModel = ReaderViewModel(application)
        
        viewModel.searchRag("test")
        advanceUntilIdle()
        
        viewModel.clearSearch()
        
        assertTrue(viewModel.searchResults.value.isEmpty())
        assertNull(viewModel.searchError.value)
        assertFalse(viewModel.isSearching.value)
    }
}
