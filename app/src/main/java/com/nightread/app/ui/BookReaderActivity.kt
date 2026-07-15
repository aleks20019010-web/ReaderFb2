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
import com.nightread.app.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Locale
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Custom WebView exposing computeHorizontalScrollRange and computeVerticalScrollRange.
 */
class BookWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    fun getVerticalScrollRange(): Int {
        return computeVerticalScrollRange()
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
    private var pages: List<String> = emptyList()

    private var fontSize: Int = 18
    private var lineHeight: Float = 1.6f
    private var fontFamily: String = "Georgia, 'Times New Roman', serif"
    private var currentFontPath: String = ""
    private var paddingValue: Int = 24

    private val paddingDp = 24

    private var bookText: String = ""
    private var bookLoaded = false
    private var isDimensionsReady = false

    private var touchStartX = 0f
    private val swipeThreshold = 100f

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
                if (book != null && !book.filePath.isNullOrEmpty()) {
                    val file = File(book.filePath)
                    if (file.exists()) {
                        bookText = withContext(Dispatchers.IO) { com.nightread.app.service.Fb2Parser.parse(file, "Unknown").content }.trim().trim('\u000C').trim()
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
        val settings = webView.settings
        settings.apply {
            allowFileAccess = true
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptEnabled = false
            useWideViewPort = false
            loadWithOverviewMode = false
        }
        webView.apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    fun loadBook(text: String) {
        bookText = text
        if (screenWidth <= 0 || screenHeight <= 0) return

        progressBar.visibility = View.VISIBLE
        webView.visibility = View.INVISIBLE

        lifecycleScope.launch {
            val parsedPages = withTimeoutOrNull(15000) {
                splitTextIntoPages(text)
            }
            
            if (parsedPages != null && parsedPages.isNotEmpty()) {
                pages = parsedPages
            } else {
                Log.w("BookReader", "Pagination failed or timeout, using single page fallback")
                pages = listOf(buildPageHtml(text))
            }
            
            totalPages = pages.size
            currentPage = 0

            progressBar.visibility = View.GONE
            webView.visibility = View.VISIBLE
            loadPage(currentPage)
        }
    }

    private suspend fun splitTextIntoPages(text: String): List<String> {
        val paragraphs = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val resultPages = ArrayList<String>()

        val tempWebView = withContext(Dispatchers.Main) {
            BookWebView(this@BookReaderActivity).apply {
                setupWebViewSettings(this)
                layout(0, 0, screenWidth, screenHeight)
                visibility = View.INVISIBLE
                rootLayout.addView(this)
            }
        }

        var currentPageParagraphs = ArrayList<String>()

        for (paragraph in paragraphs) {
            val isChapter = isChapterHeading(paragraph)
            
            if (currentPageParagraphs.isEmpty()) {
                currentPageParagraphs.add(paragraph)
                continue
            }

            if (isChapter) {
                resultPages.add(buildPageHtml(currentPageParagraphs.joinToString("\n\n")))
                currentPageParagraphs.clear()
                currentPageParagraphs.add(paragraph)
                continue
            }

            val testList = ArrayList(currentPageParagraphs)
            testList.add(paragraph)
            val testText = testList.joinToString("\n\n")
            val testHtml = buildPageHtml(testText, false)

            if (measurePageScrollHeight(tempWebView, testHtml) <= screenHeight) {
                currentPageParagraphs.add(paragraph)
            } else {
                resultPages.add(buildPageHtml(currentPageParagraphs.joinToString("\n\n")))
                currentPageParagraphs.clear()
                currentPageParagraphs.add(paragraph)
            }
        }

        if (currentPageParagraphs.isNotEmpty()) {
            resultPages.add(buildPageHtml(currentPageParagraphs.joinToString("\n\n")))
        }

        withContext(Dispatchers.Main) {
            rootLayout.removeView(tempWebView)
            tempWebView.destroy()
        }
        return resultPages
    }

    private fun isChapterHeading(paragraph: String): Boolean {
        val p = paragraph.trim()
        val regex = Regex("^(Глава|Chapter|ГЛАВА|I+\\.|II+\\.|III+\\.|IV+\\.|[0-9]+\\.)", RegexOption.IGNORE_CASE)
        return regex.containsMatchIn(p)
    }

    private fun setupWebViewSettings(targetWebView: WebView) {
        targetWebView.settings.apply {
            javaScriptEnabled = true // Required for measurement
            blockNetworkLoads = true
        }
    }

    private suspend fun measurePageScrollHeight(tempWebView: BookWebView, html: String): Int = withContext(Dispatchers.Main) {
        val latch = CountDownLatch(1)
        var height = 0

        tempWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d("BookReader", "measurePageScrollHeight: onPageFinished")
                tempWebView.postDelayed({
                    height = tempWebView.getVerticalScrollRange()
                    Log.d("BookReader", "measurePageScrollHeight: measured height=$height")
                    latch.countDown()
                }, 100)
            }
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                Log.e("BookReader", "measurePageScrollHeight: onReceivedError ${error?.description}")
                latch.countDown()
            }
        }
        tempWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

        withContext(Dispatchers.IO) { latch.await(2000, TimeUnit.MILLISECONDS) }
        Log.d("BookReader", "measurePageScrollHeight: finished, height=$height")
        if (height == 0) {
            Log.w("BookReader", "measurePageScrollHeight: height is 0, fallback to screenHeight")
            height = screenHeight
        }
        height
    }

    private fun buildPageHtml(text: String, useFonts: Boolean = true): String {
        val paragraphsHtml = text.split(Regex("\n+")).joinToString("") { p ->
            val className = if (isChapterHeading(p)) "chapter-title" else "paragraph"
            "<p class=\"$className\">${p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</p>"
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <style>
                html, body { margin: 0; padding: 0; width: ${screenWidth}px; height: ${screenHeight}px; overflow: hidden; background: #FFFFFF; }
                .content { width: ${screenWidth}px; height: ${screenHeight}px; padding: ${paddingValue}px; box-sizing: border-box; font-size: ${fontSize}px; line-height: ${lineHeight}; font-family: $fontFamily; text-align: justify; }
                .paragraph { margin: 0 0 1em 0; text-indent: 1.5em; }
                .chapter-title { text-indent: 0; font-weight: bold; margin-top: 1em; text-align: center; }
            </style>
            </head>
            <body>
                <div class="content">$paragraphsHtml</div>
            </body>
            </html>
        """.trimIndent()
    }

    fun loadPage(pageNumber: Int) {
        if (pages.isEmpty()) return
        currentPage = pageNumber.coerceIn(0, pages.size - 1)
        webView.loadDataWithBaseURL(null, pages[currentPage], "text/html", "UTF-8", null)
        updatePageIndicator()
    }

    fun showFootnote(noteId: String) {
        // TODO: Implement footnote display
    }

    fun performSmartSearch(word: String) {
        // TODO: Implement smart search
    }

    private fun setupTouchListener() {
        webView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) touchStartX = event.x
            else if (event.action == MotionEvent.ACTION_UP) {
                val diffX = event.x - touchStartX
                if (Math.abs(diffX) > swipeThreshold) {
                    if (diffX > 0 && currentPage > 0) loadPage(currentPage - 1)
                    else if (diffX < 0 && currentPage < totalPages - 1) loadPage(currentPage + 1)
                } else {
                    if (event.x < webView.width * 0.3f && currentPage > 0) loadPage(currentPage - 1)
                    else if (event.x > webView.width * 0.7f && currentPage < totalPages - 1) loadPage(currentPage + 1)
                }
                v.performClick()
            }
            true
        }
    }

    private fun updatePageIndicator() {
        pageIndicatorView.text = "Стр.${currentPage + 1}/$totalPages"
    }

    private fun getDefaultBookText(): String {
        return """
            Глава I
            В уездном городе N остановилась коляска. В ней сидел молодой человек.
            
            Глава II
            Комната была обставлена скромно. Приезжий подошел к окну.
        """.trimIndent()
    }

    private fun tryLoadBook() {
        if (!bookLoaded && isDimensionsReady && bookText.isNotEmpty()) {
            bookLoaded = true
            loadBook(bookText)
        }
    }

    // Public Settings Customization APIs
    fun increaseFontSize() {
        fontSize += 2
        bookLoaded = false
        loadBook(bookText)
    }

    fun decreaseFontSize() {
        fontSize = (fontSize - 2).coerceAtLeast(10)
        bookLoaded = false
        loadBook(bookText)
    }

    fun setLineHeight(multiplier: Float) {
        lineHeight = multiplier
        bookLoaded = false
        loadBook(bookText)
    }

    fun setFont(fontName: String, fontPath: String) {
        fontFamily = fontName
        currentFontPath = fontPath
        bookLoaded = false
        loadBook(bookText)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (currentPage < totalPages - 1) {
                    loadPage(currentPage + 1)
                    true
                } else super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (currentPage > 0) {
                    loadPage(currentPage - 1)
                    true
                } else super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    // Brightness and Warmth control (simplified gestures)
    private var touchStartY = 0f
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val diffY = touchStartY - event.y
                if (Math.abs(diffY) > 50) {
                    // Logic for brightness or warmth would be here.
                    // For now, it's just a gesture detection.
                    touchStartY = event.y
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
