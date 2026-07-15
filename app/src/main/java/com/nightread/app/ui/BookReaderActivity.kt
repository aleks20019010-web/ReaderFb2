package com.nightread.app.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nightread.app.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Custom WebView exposing computeVerticalScrollRange for precise height measurement.
 */
class MeasurableWebView(context: Context) : WebView(context) {
    fun getVerticalScrollRange(): Int {
        return super.computeVerticalScrollRange()
    }
}

class BookReaderActivity : AppCompatActivity() {

    // Target UI elements
    private lateinit var webView: MeasurableWebView
    private lateinit var progressBar: ProgressBar
    private lateinit var gestureDetector: GestureDetector

    // Settings variables with dynamic parameters
    private var fontSize: Int = 18
    private var lineHeight: Float = 1.6f
    private var fontFamily: String = "Georgia, 'Times New Roman', serif"
    private var paddingHorizontal: Int = 20
    private var paddingVertical: Int = 24

    // Screen dimensions
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    // Pagination state
    private var bookText: String = ""
    private var currentPage: Int = 0
    private val pagesList = ArrayList<String>()

    // Cache system
    private val paginationCache = HashMap<String, List<String>>()

    // Temporary webview for background page verification
    private var tempWebView: MeasurableWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Restore state if available
        if (savedInstanceState != null) {
            currentPage = savedInstanceState.getInt("current_page", 0)
            fontSize = savedInstanceState.getInt("font_size", 18)
            lineHeight = savedInstanceState.getFloat("line_height", 1.6f)
            fontFamily = savedInstanceState.getString("font_family", "Georgia, 'Times New Roman', serif")
        }

        // Set layout activity_book.xml
        setContentView(R.layout.activity_book)

        // Initialize UI components
        val rootLayout = findViewById<FrameLayout>(R.id.rootView)
        
        // Programmatically replace template webview with our MeasurableWebView if needed, 
        // or just add it dynamically to activity_book.xml
        webView = MeasurableWebView(this)
        webView.id = R.id.bookWebView
        val webViewParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.addView(webView, 0, webViewParams)

        progressBar = findViewById(R.id.progressBar)

        setupWebView(webView)
        setupGestures()

        // Load content
        bookText = intent.getStringExtra("book_text") ?: getDefaultBookText()

        // Wait until WebView layout is complete to get exact dimensions
        webView.post {
            screenWidth = webView.width
            screenHeight = webView.height
            Log.d("BookReader", "Screen dimensions received: ${screenWidth}x${screenHeight}")
            if (screenWidth > 0 && screenHeight > 0) {
                repaginate()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_page", currentPage)
        outState.putInt("font_size", fontSize)
        outState.putFloat("line_height", lineHeight)
        outState.putString("font_family", fontFamily)
    }

    private fun setupWebView(targetWebView: WebView) {
        val settings = targetWebView.settings
        
        // Strict Offline and security configurations
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        
        // Prevent zooms, scrolls, and enable basic layout
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.javaScriptEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        targetWebView.isVerticalScrollBarEnabled = false
        targetWebView.isHorizontalScrollBarEnabled = false
        targetWebView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, BookGestureListener())
        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun getCacheKey(): String {
        return "$fontSize-$lineHeight-$fontFamily-$screenWidth-$screenHeight"
    }

    /**
     * Start the background pagination thread.
     */
    private fun repaginate() {
        val cacheKey = getCacheKey()
        if (paginationCache.containsKey(cacheKey)) {
            val cached = paginationCache[cacheKey]!!
            Log.d("BookReader", "Cache HIT for key: $cacheKey. Pages: ${cached.size}")
            pagesList.clear()
            pagesList.addAll(cached)
            loadPage(currentPage)
            return
        }

        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            Log.d("BookReader", "Starting pagination computation...")

            // 1. Split text into individual paragraphs
            val paragraphs = bookText.split(Regex("\n\n+")).map { it.trim() }.filter { it.isNotEmpty() }

            // 2. Rough split using high speed StaticLayout
            val roughPages = withContext(Dispatchers.Default) {
                roughSplitWithStaticLayout(paragraphs)
            }

            // 3. Verify and adjust boundaries using WebView height measurements
            val finalPages = verifyAndFixWithWebView(roughPages)

            // Cache the result
            paginationCache[cacheKey] = finalPages

            // Update UI state
            pagesList.clear()
            pagesList.addAll(finalPages)
            
            progressBar.visibility = View.GONE
            Log.d("BookReader", "Pagination complete in ${System.currentTimeMillis() - startTime}ms. Total pages: ${pagesList.size}")

            loadPage(currentPage)
        }
    }

    /**
     * Rapid preliminary page splitting via Android's native StaticLayout on a background thread.
     */
    private fun roughSplitWithStaticLayout(paragraphs: List<String>): List<String> {
        val startTime = System.currentTimeMillis()
        Log.d("BookReader", "roughSplitWithStaticLayout started. Paragraphs count: ${paragraphs.size}")

        val paint = TextPaint().apply {
            textSize = fontSize * resources.displayMetrics.scaledDensity
            val style = Typeface.NORMAL
            typeface = when {
                fontFamily.contains("sans", true) -> Typeface.create(Typeface.SANS_SERIF, style)
                fontFamily.contains("serif", true) -> Typeface.create(Typeface.SERIF, style)
                fontFamily.contains("mono", true) -> Typeface.create(Typeface.MONOSPACE, style)
                else -> Typeface.create(fontFamily, style)
            }
        }

        // Calculate available width and height inside the padding bounds
        val density = resources.displayMetrics.density
        val padHorizontalPx = (paddingHorizontal * density).toInt()
        val padVerticalPx = (paddingVertical * density).toInt()

        val availableWidth = (screenWidth - 2 * padHorizontalPx).coerceAtLeast(100)
        val availableHeight = (screenHeight - 2 * padVerticalPx).coerceAtLeast(100)

        // Calculate precise height of a single line
        val fm = paint.fontMetrics
        val singleLineHeight = (fm.descent - fm.ascent) * lineHeight
        val maxLinesPerPage = (availableHeight / singleLineHeight).toInt().coerceAtLeast(1)

        val roughPages = ArrayList<String>()
        var currentPageParagraphs = ArrayList<String>()
        var currentLineCount = 0

        for (paragraph in paragraphs) {
            val paragraphLines = getParagraphLineCount(paragraph, paint, availableWidth)
            val additionalLines = if (currentPageParagraphs.isEmpty()) 0 else 1 // blank line spacing between paragraphs

            if (currentLineCount + paragraphLines + additionalLines <= maxLinesPerPage) {
                currentPageParagraphs.add(paragraph)
                currentLineCount += paragraphLines + additionalLines
            } else {
                if (currentPageParagraphs.isNotEmpty()) {
                    roughPages.add(currentPageParagraphs.joinToString("\n\n"))
                    currentPageParagraphs = ArrayList()
                    currentLineCount = 0
                }

                // If a single paragraph is too large to fit in a single empty page, split it or place it anyway
                val linesInNewPage = getParagraphLineCount(paragraph, paint, availableWidth)
                currentPageParagraphs.add(paragraph)
                currentLineCount = linesInNewPage
            }
        }

        if (currentPageParagraphs.isNotEmpty()) {
            roughPages.add(currentPageParagraphs.joinToString("\n\n"))
        }

        Log.d("BookReader", "roughSplitWithStaticLayout finished in ${System.currentTimeMillis() - startTime}ms. Pages: ${roughPages.size}")
        return roughPages
    }

    private fun getParagraphLineCount(text: String, paint: TextPaint, widthPx: Int): Int {
        if (text.isEmpty()) return 0
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineHeight)
                .setIncludePad(false)
            builder.build().lineCount
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, widthPx, android.text.Layout.Alignment.ALIGN_NORMAL, lineHeight, 0f, false).lineCount
        }
    }

    /**
     * Verification of page heights through a real WebView, only checking problematic boundaries
     * to keep execution within 100-200ms.
     */
    private suspend fun verifyAndFixWithWebView(roughPages: List<String>): List<String> {
        val startTime = System.currentTimeMillis()
        Log.d("BookReader", "verifyAndFixWithWebView started. Input pages: ${roughPages.size}")

        val pages = roughPages.map { page ->
            page.split("\n\n").filter { it.isNotEmpty() }.toMutableList()
        }.toMutableList()

        // Select the pages to verify (portions / ends of chunks) to maintain fast speed
        val pagesToVerify = java.util.LinkedHashSet<Int>()
        if (pages.size <= 15) {
            pagesToVerify.addAll(pages.indices)
        } else {
            val portionSize = (pages.size / 10).coerceAtLeast(5)
            for (i in portionSize - 1 until pages.size step portionSize) {
                pagesToVerify.add(i)
            }
            // Always include first few and the very last page
            for (i in 0 until minOf(5, pages.size)) {
                pagesToVerify.add(i)
            }
            pagesToVerify.add(pages.size - 1)
        }

        var checkCount = 0
        for (i in pages.indices) {
            if (i !in pagesToVerify) continue
            checkCount++

            var pageParagraphs = pages[i]
            if (pageParagraphs.isEmpty()) continue

            var html = buildPageHtmlContent(pageParagraphs)
            var scrollRange = measurePageScrollHeight(html)

            // Adjust if page overflows
            var adjusted = false
            while (scrollRange > screenHeight && pageParagraphs.size > 1) {
                val lastParagraph = pageParagraphs.removeAt(pageParagraphs.size - 1)
                if (i + 1 < pages.size) {
                    pages[i + 1].add(0, lastParagraph)
                } else {
                    pages.add(mutableListOf(lastParagraph))
                }
                html = buildPageHtmlContent(pageParagraphs)
                scrollRange = measurePageScrollHeight(html)
                adjusted = true
            }

            // Adjust if too short (remains significant blank space)
            if (!adjusted && i + 1 < pages.size) {
                while (pages[i + 1].isNotEmpty()) {
                    val nextParagraph = pages[i + 1][0]
                    val testParagraphs = pageParagraphs.toMutableList()
                    testParagraphs.add(nextParagraph)
                    val testHtml = buildPageHtmlContent(testParagraphs)
                    val testScrollRange = measurePageScrollHeight(testHtml)

                    if (testScrollRange <= screenHeight) {
                        pages[i + 1].removeAt(0)
                        pageParagraphs.add(nextParagraph)
                        scrollRange = testScrollRange
                    } else {
                        break
                    }
                }
            }
        }

        val result = pages.map { it.joinToString("\n\n") }.filter { it.isNotEmpty() }
        Log.d("BookReader", "verifyAndFixWithWebView completed in ${System.currentTimeMillis() - startTime}ms. Checked $checkCount pages. Output pages: ${result.size}")
        return result
    }

    /**
     * Executes measurements of WebView scroll ranges safely in main thread using CountDownLatch.
     */
    private suspend fun measurePageScrollHeight(htmlContent: String): Int = withContext(Dispatchers.Main) {
        val latch = CountDownLatch(1)
        var scrollRange = 0

        val temp = tempWebView ?: MeasurableWebView(this@BookReaderActivity).also {
            setupWebView(it)
            tempWebView = it
        }

        temp.layout(0, 0, screenWidth, screenHeight)

        temp.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val textLength = htmlContent.length
                val delayMs = when {
                    textLength < 500 -> 30L
                    textLength < 2000 -> 70L
                    else -> 120L
                }
                temp.postDelayed({
                    scrollRange = temp.getVerticalScrollRange()
                    latch.countDown()
                }, delayMs)
            }
        }

        temp.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)

        withContext(Dispatchers.IO) {
            latch.await(300, TimeUnit.MILLISECONDS)
        }
        scrollRange
    }

    private fun buildPageHtmlContent(paragraphs: List<String>): String {
        val paragraphsHtml = paragraphs.joinToString("") { p ->
            "<p>${p.replace("\n", "<br/>")}</p>"
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    width: ${screenWidth}px;
                    height: ${screenHeight}px;
                    overflow: hidden;
                    background-color: #FFFFFF;
                }
                body {
                    font-size: ${fontSize}px;
                    line-height: ${lineHeight};
                    font-family: ${fontFamily};
                    padding: ${paddingVertical}px ${paddingHorizontal}px;
                    box-sizing: border-box;
                    text-align: justify;
                    -webkit-hyphens: auto;
                    -moz-hyphens: auto;
                    hyphens: auto;
                    word-wrap: break-word;
                }
                p {
                    margin: 0 0 1em 0;
                    text-indent: 1.5em;
                }
                p:last-child {
                    margin-bottom: 0;
                }
            </style>
            </head>
            <body>
                $paragraphsHtml
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Loads a page with a given index securely.
     */
    private fun loadPage(index: Int) {
        if (pagesList.isEmpty()) return
        currentPage = index.coerceIn(0, pagesList.size - 1)

        val paragraphs = pagesList[currentPage].split("\n\n").filter { it.isNotEmpty() }
        val htmlContent = buildPageHtmlContent(paragraphs)

        webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
        Log.d("BookReader", "Page $currentPage loaded successfully.")
    }

    // Settings Modification APIs
    fun increaseFontSize() {
        fontSize = (fontSize + 1).coerceAtMost(36)
        Log.d("BookReader", "Font size increased to $fontSize")
        repaginate()
    }

    fun decreaseFontSize() {
        fontSize = (fontSize - 1).coerceAtLeast(12)
        Log.d("BookReader", "Font size decreased to $fontSize")
        repaginate()
    }

    fun setLineHeight(height: Float) {
        lineHeight = height.coerceIn(1.0f, 2.5f)
        Log.d("BookReader", "Line height set to $lineHeight")
        repaginate()
    }

    fun setFontFamily(family: String) {
        fontFamily = family
        Log.d("BookReader", "Font family set to $fontFamily")
        repaginate()
    }

    private inner class BookGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        loadPage(currentPage - 1)
                    } else {
                        loadPage(currentPage + 1)
                    }
                    return true
                }
            }
            return false
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val x = e.x
            val width = webView.width
            if (x < width * 0.3f) {
                loadPage(currentPage - 1)
                return true
            } else if (x > width * 0.7f) {
                loadPage(currentPage + 1)
                return true
            }
            return false
        }
    }

    private fun getDefaultBookText(): String {
        return """
            Глава I

            В уездном городе N, в одной из лучших гостиниц, остановилась в начале июля 1826 года прекрасная коляска на рессорах, запряженная тройкою почтовых лошадей. В ней сидел молодой человек лет двадцати пяти, стройный, красивый, с умным выражением лица и тонкими чертами.

            Дорога была тяжелая, пыль летела со всех сторон, покрывая толстым слоем дорожную карету и лицо путника. Он нетерпеливо поглядывал на часы, словно ожидая долгожданной встречи, ради которой проделал столь долгий и утомительный путь из самого Петербурга.

            Город встретил его тишиной, лениво раскинувшимися улочками и редкими прохожими, которые с любопытством провожали глазами заезжий экипаж. Все вокруг дышало покоем русской провинции, где время словно остановилось веками назад.

            Молодой человек вышел из кареты и направился к крыльцу гостиницы. Его шаги гулко отдавались в полуденной тишине. Слуга поспешил открыть перед ним тяжелую дубовую дверь, вежливо кланяясь важному гостю.

            Глава II

            Комната, отведенная приезжему, оказалась просторной, но обставленной весьма скромно. Деревянный стол в углу, пара стульев с высокой обитой спинкой и огромный диван, обтянутый потертой зеленой кожей. На стене висело старое зеркало в потемневшей медной раме.

            Приезжий подошел к окну и распахнул тяжелые ставни. Свежий воздух ворвался в душную комнату, принося с собой ароматы цветущей липы из соседнего сада и далекий шум реки. Он глубоко вздохнул, чувствуя, как дорожная усталость постепенно уступает место приятному волнению.

            Здесь, вдали от столичной суеты и интриг, он надеялся найти долгожданное умиротворение и закончить свою новую рукопись, над которой трудился уже несколько месяцев. Провинция казалась ему идеальным убежищем для творческой души.
        """.trimIndent()
    }
}
