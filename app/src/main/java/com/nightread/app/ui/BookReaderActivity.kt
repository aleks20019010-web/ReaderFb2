package com.nightread.app.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * Custom WebView exposing computeHorizontalScrollRange and computeVerticalScrollRange.
 */
class BookWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    fun getHorizontalScrollRange(): Int {
        return computeHorizontalScrollRange()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

class BookReaderActivity : AppCompatActivity() {

    private lateinit var webView: BookWebView
    private lateinit var pageIndicatorView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rootLayout: FrameLayout

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var currentPage: Int = 0
    private var totalPages: Int = 1

    private var fontSize: Int = 18
    private var lineHeight: Float = 1.6f
    private var fontFamily: String = "Georgia, 'Times New Roman', serif"
    private var paddingDp = 24
    private var paddingValue: Int = 24

    private var bookText: String = ""
    private var bookLoaded = false
    private var isDimensionsReady = false
    private var touchStartX: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book)

        rootLayout = findViewById(R.id.rootView)
        webView = findViewById(R.id.bookWebView)

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

        setupWebView()
        setupTouchListener()

        webView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    webView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    screenWidth = webView.width
                    screenHeight = webView.height
                    paddingValue = (paddingDp * resources.displayMetrics.density).toInt()

                    isDimensionsReady = true
                    tryLoadBook()
                }
            }
        )

        // Load content
        val sha1 = intent.getStringExtra("BOOK_SHA1")
        if (!sha1.isNullOrEmpty()) {
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(this@BookReaderActivity)
                val book = withContext(Dispatchers.IO) {
                    db.bookDao().getBookBySha1(sha1)
                }
                if (book != null) {
                    if (!book.filePath.isNullOrEmpty()) {
                        val file = File(book.filePath)
                        if (file.exists()) {
                            val rawText = withContext(Dispatchers.IO) { com.nightread.app.service.Fb2Parser.parse(file, "Unknown").content }
                            bookText = fixEncoding(rawText).trim().trim('\u000C').trim()
                        } else {
                            bookText = getDefaultBookText()
                        }
                    } else {
                        bookText = getDefaultBookText()
                    }
                }
                progressBar.visibility = View.GONE
                tryLoadBook()
            }
        } else {
            bookText = intent.getStringExtra("book_text") ?: getDefaultBookText()
            tryLoadBook()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = false
            useWideViewPort = false
            loadWithOverviewMode = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val totalWidth = webView.getHorizontalScrollRange()
                    totalPages = Math.max(1, totalWidth / screenWidth)
                    currentPage = 0
                    updatePageIndicator()
                }
            }
        }
    }

    private fun setupTouchListener() {
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diff = event.x - touchStartX
                    if (Math.abs(diff) > 100) {
                        if (diff > 0) prevPage() else nextPage()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    fun loadBook(text: String) {
        bookText = text
        if (screenWidth <= 0 || screenHeight <= 0) return

        progressBar.visibility = View.VISIBLE
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <style>
                body { margin:0; padding:0; width: auto; height: ${screenHeight}px; overflow:hidden; }
                .content { padding: ${paddingValue}px; font-size: ${fontSize}px; line-height: ${lineHeight}; font-family: $fontFamily; text-align: justify; column-width: ${screenWidth}px; column-gap: 0px; column-fill: auto; height: ${screenHeight - 2 * paddingValue}px; }
                p { margin-bottom:1em; text-indent:1.5em; }
            </style>
            </head>
            <body>
                <div class="content">
                    ${text.replace("\n\n", "</p><p>").replace("\n", "<br>")}
                </div>
            </body>
            </html>
        """.trimIndent()
        
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        progressBar.visibility = View.GONE
    }

    private fun fixEncoding(text: String): String {
        if (text.contains("Ñ") || text.contains("ð") || text.contains("å") || text.contains("è")) {
            try {
                val bytes = text.toByteArray(Charsets.ISO_8859_1)
                return String(bytes, Charset.forName("windows-1251"))
            } catch (e: Exception) {
                Log.e("BookReader", "Failed to fix encoding", e)
            }
        }
        return text
    }

    fun nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++
            webView.scrollTo(currentPage * screenWidth, 0)
            updatePageIndicator()
        }
    }

    fun prevPage() {
        if (currentPage > 0) {
            currentPage--
            webView.scrollTo(currentPage * screenWidth, 0)
            updatePageIndicator()
        }
    }

    fun loadPage(pageNumber: Int) {
        currentPage = pageNumber.coerceIn(0, totalPages - 1)
        webView.scrollTo(currentPage * screenWidth, 0)
        updatePageIndicator()
    }

    fun showFootnote(noteId: String) {}
    fun performSmartSearch(word: String) {}

    private fun updatePageIndicator() {
        pageIndicatorView.text = "Стр.${currentPage + 1}/$totalPages"
    }

    private fun tryLoadBook() {
        if (!bookLoaded && isDimensionsReady && bookText.isNotEmpty()) {
            bookLoaded = true
            loadBook(bookText)
        }
    }

    private fun getDefaultBookText(): String = "Тестовая книга\n\nГлава I\n\nТекст первой главы."
}
