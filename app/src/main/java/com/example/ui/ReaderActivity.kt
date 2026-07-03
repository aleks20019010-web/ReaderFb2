package com.example.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ReaderActivity : AppCompatActivity() {
    lateinit var viewModel: ReaderViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: TextView
    private lateinit var rootLayout: FrameLayout
    private var isSystemUiVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Enable Edge-To-Edge and layout in system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val bookId = intent.getIntExtra("BOOK_ID", -1)
        if (bookId == -1) {
            finish()
            return
        }

        val vm: ReaderViewModel by viewModels()
        viewModel = vm
        viewModel.loadBook(bookId)

        // 2. Programmatic Layout Structure
        val density = resources.displayMetrics.density
        rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(viewPager)

        // Bottom page progress indicator (e.g. "94 из 239")
        pageIndicator = TextView(this).apply {
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).toInt()
            }
            layoutParams = lp
            textSize = 14f
            setTextColor(Color.GRAY)
        }
        rootLayout.addView(pageIndicator)

        setContentView(rootLayout)

        // Hide system UI (immersive mode) by default on entry
        hideSystemUi()

        // 3. Observe ViewModel StateFlows
        lifecycleScope.launch {
            viewModel.pagesState.collect { pages ->
                if (pages.isNotEmpty()) {
                    val adapter = ReaderPagerAdapter(this@ReaderActivity, pages)
                    viewPager.adapter = adapter
                    
                    // Set current item once pages are loaded
                    viewPager.setCurrentItem(viewModel.currentPage.value, false)
                    updatePageIndicator(viewModel.currentPage.value, pages.size)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.currentPage.collect { page ->
                if (viewPager.currentItem != page) {
                    viewPager.setCurrentItem(page, false)
                }
                val pagesSize = viewModel.pagesState.value.size
                if (pagesSize > 0) {
                    updatePageIndicator(page, pagesSize)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.themeState.collect { theme ->
                applyTheme(theme)
            }
        }

        // Propagate page change in ViewPager2 back to ViewModel state
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setCurrentPage(position)
            }
        })
    }

    private fun updatePageIndicator(currentPage: Int, totalPages: Int) {
        pageIndicator.text = "${currentPage + 1} из $totalPages"
    }

    private fun applyTheme(theme: String) {
        if (theme == "night") {
            rootLayout.setBackgroundColor(Color.parseColor("#1a1a1a"))
            pageIndicator.setTextColor(Color.parseColor("#80e0e0e0"))
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#f5f0e8"))
            pageIndicator.setTextColor(Color.parseColor("#803a3a3a"))
        }
    }

    fun toggleSystemUi() {
        if (isSystemUiVisible) {
            hideSystemUi()
        } else {
            showSystemUi()
        }
    }

    private fun hideSystemUi() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        isSystemUiVisible = false
    }

    private fun showSystemUi() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        isSystemUiVisible = true
    }

    // Capture volume down/up keys to change pages forwards/backwards
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val nextPage = viewModel.currentPage.value + 1
                if (nextPage < viewModel.pagesState.value.size) {
                    viewModel.setCurrentPage(nextPage)
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                val prevPage = viewModel.currentPage.value - 1
                if (prevPage >= 0) {
                    viewModel.setCurrentPage(prevPage)
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        // Save current progress on pause
        viewModel.saveProgress()
    }
}
