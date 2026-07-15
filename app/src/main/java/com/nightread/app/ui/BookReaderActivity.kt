package com.nightread.app.ui

import android.graphics.Color
import android.os.Bundle
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.nightread.app.R
import com.nightread.app.ui.customlayout.CustomReaderPageView
import com.nightread.app.ui.customlayout.PageSplitter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class BookReaderActivity : AppCompatActivity() {

    private lateinit var readerView: CustomReaderPageView
    private lateinit var pageIndicatorView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rootLayout: FrameLayout

    private lateinit var viewModel: ReaderViewModel
    private var touchStartX: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book)

        readerView = findViewById(R.id.bookReaderView)
        rootLayout = findViewById(R.id.rootView)
        viewModel = ViewModelProvider(this).get(ReaderViewModel::class.java)

        progressBar = ProgressBar(this).apply { visibility = View.GONE }
        val progressParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        rootLayout.addView(progressBar, progressParams)

        pageIndicatorView = TextView(this).apply {
            setTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            val paddingPx = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, paddingPx)
        }
        val indicatorParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        rootLayout.addView(pageIndicatorView, indicatorParams)

        readerView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                readerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                viewModel.updateDimensions(readerView.width, readerView.height, resources.displayMetrics.density)
            }
        })

        lifecycleScope.launch {
            viewModel.currentPage.collectLatest { updatePage(); updatePageIndicator() }
        }
        lifecycleScope.launch {
            viewModel.pagesState.collectLatest { updatePage(); updatePageIndicator() }
        }

        readerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> touchStartX = event.x
                MotionEvent.ACTION_UP -> {
                    val diff = event.x - touchStartX
                    if (Math.abs(diff) > 100) {
                        if (diff > 0) viewModel.setCurrentPage(viewModel.currentPage.value - 1)
                        else viewModel.setCurrentPage(viewModel.currentPage.value + 1)
                    }
                }
            }
            true
        }

        val sha1 = intent.getStringExtra("BOOK_SHA1") ?: ""
        if (sha1.isNotEmpty()) viewModel.loadBook(sha1)
    }

    private fun updatePage() {
        val pages = viewModel.pagesState.value
        val pageIdx = viewModel.currentPage.value
        if (pages.isEmpty() || pageIdx !in pages.indices) return

        val text = pages[pageIdx]
        
        val paint = TextPaint().apply {
            textSize = viewModel.fontSizeState.value * resources.displayMetrics.density
            typeface = android.graphics.Typeface.DEFAULT
        }
        
        val availableWidth = readerView.width - readerView.paddingStart - readerView.paddingEnd
        val layout = PageSplitter.createStaticLayout(
            text, 0, text.length, paint, availableWidth,
            android.text.Layout.Alignment.ALIGN_NORMAL,
            viewModel.lineSpacingState.value, 0f, false
        )
        readerView.setLayout(layout)
    }

    private fun updatePageIndicator() {
        val page = viewModel.currentPage.value + 1
        val total = viewModel.pagesState.value.size
        pageIndicatorView.text = "Стр.$page/$total"
    }

    fun loadPage(pageNumber: Int) {
        viewModel.setCurrentPage(pageNumber)
    }

    fun showFootnote(noteId: String) {}
    fun performSmartSearch(word: String) {}
}
