package com.nightread.app.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
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
import com.nightread.app.service.BookParser
import com.nightread.app.service.EpubParser
import com.nightread.app.service.MobiParser
import com.nightread.app.service.TxtParser
import com.nightread.app.service.Fb2Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class BookWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    fun getHorizontalScrollRange(): Int {
        return computeHorizontalScrollRange()
    }

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

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var currentPage: Int = 0
    private var totalPages: Int = 1
    private var pages: List<String> = emptyList()

    private var fontSize: Int = 18
    private var lineHeight: Float = 1.6f
    private var fontFamily: String = "Georgia, 'Times New Roman', serif"
    private var currentFontPath: String = ""
    private var paddingValue: Int = 20

    private val paddingDp = 24

    private var bookText: String = ""
    private var isBookLoading: Boolean = false
    private var isDimensionsReady = false
    private var bookLoaded = false

    private var touchStartX = 0f
    private var touchStartY = 0f
    private val swipeThreshold = 100f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book)

        val rootLayout = findViewById<FrameLayout>(R.id.rootView)
        webView = findViewById(R.id.bookWebView)

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
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

        // Загружаем тестовый текст когда размеры готовы
        webView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    webView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    screenWidth = webView.width
                    screenHeight = webView.height
                    val density = resources.displayMetrics.density
                    paddingValue = (paddingDp * density).toInt()

                    Log.d("BookReader", "Screen dimensions: ${screenWidth}x${screenHeight}, Padding: ${paddingValue}px")

                    if (screenWidth > 0 && screenHeight > 0) {
                        isDimensionsReady = true
                        bookText = getDefaultBookText()
                        tryLoadBook()
                    }
                }
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_page", currentPage)
        outState.putInt("font_size", fontSize)
        outState.putFloat("line_height", lineHeight)
        outState.putString("font_family", fontFamily)
        outState.putString("current_font_path", currentFontPath)
    }

    private fun setupWebView() {
        setupWebViewSettings(webView)
    }

    private fun setupWebViewSettings(targetWebView: WebView) {
        val settings = targetWebView.settings

        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        settings.javaScriptEnabled = false
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false

        targetWebView.isVerticalScrollBarEnabled = false
        targetWebView.isHorizontalScrollBarEnabled = false
        targetWebView.overScrollMode = View.OVER_SCROLL_NEVER
    }

    fun loadBook(text: String) {
        Log.d("BookReader", "loadBook called. screenWidth=$screenWidth, screenHeight=$screenHeight, textLength=${text.length}")
        bookText = text
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.e("BookReader", "Cannot load book: dimensions not ready yet")
            return
        }

        progressBar.visibility = View.VISIBLE
        webView.visibility = View.INVISIBLE

        lifecycleScope.launch {
            val parsedPages = splitTextIntoPages(text)
            pages = parsedPages
            totalPages = pages.size
            if (currentPage >= totalPages) {
                currentPage = totalPages - 1
            }
            if (currentPage < 0) {
                currentPage = 0
            }

            progressBar.visibility = View.GONE
            webView.visibility = View.VISIBLE
            Log.d("BookReader", "loadBook: loading page $currentPage of $totalPages")
            loadPage(currentPage)
        }
    }

    private fun tryLoadBook() {
        if (!bookLoaded && isDimensionsReady && bookText.isNotEmpty()) {
            bookLoaded = true
            loadBook(bookText)
        }
    }

    private suspend fun splitTextIntoPages(text: String): List<String> {
        val startTime = System.currentTimeMillis()
        Log.d("BookReader", "Starting splitTextIntoPages computation...")

        val paragraphs = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val resultPages = ArrayList<String>()

        val tempWebView = withContext(Dispatchers.Main) {
            val tw = BookWebView(this@BookReaderActivity)
            setupWebViewSettings(tw)
            tw.layout(0, 0, screenWidth, screenHeight)
            tw
        }

        var currentPageParagraphs = ArrayList<String>()

        for (paragraph in paragraphs) {
            if (currentPageParagraphs.isEmpty()) {
                currentPageParagraphs.add(paragraph)
                continue
            }

            val testList = ArrayList(currentPageParagraphs)
            testList.add(paragraph)
            val testText = testList.joinToString("\n\n")
            val testHtml = buildPageHtml(testText, false)

            val scrollHeight = measurePageScrollHeight(tempWebView, testHtml)
            if (scrollHeight <= screenHeight) {
                currentPageParagraphs.add(paragraph)
            } else {
                val pageText = currentPageParagraphs.joinToString("\n\n")
                resultPages.add(buildPageHtml(pageText))

                currentPageParagraphs.clear()
                currentPageParagraphs.add(paragraph)
            }
        }

        if (currentPageParagraphs.isNotEmpty()) {
            val pageText = currentPageParagraphs.joinToString("\n\n")
            resultPages.add(buildPageHtml(pageText))
        }

        withContext(Dispatchers.Main) {
            tempWebView.destroy()
        }

        Log.d("BookReader", "splitTextIntoPages finished in ${System.currentTimeMillis() - startTime}ms. Pages count: ${resultPages.size}")
        return resultPages
    }

    private suspend fun measurePageScrollHeight(tempWebView: BookWebView, html: String): Int = withContext(Dispatchers.Main) {
        val latch = CountDownLatch(1)
        var height = 0
        Log.d("BookReader", "measurePageScrollHeight: starting measurement")

        tempWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d("BookReader", "measurePageScrollHeight: onPageFinished called")
                tempWebView.postDelayed({
                    height = tempWebView.getVerticalScrollRange()
                    Log.d("BookReader", "measurePageScrollHeight: height measured=$height")
                    latch.countDown()
                }, 100)
            }
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                Log.e("BookReader", "measurePageScrollHeight: onReceivedError ${error?.description}")
                latch.countDown()
            }
        }

        tempWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

        withContext(Dispatchers.IO) {
            val finished = latch.await(2000, TimeUnit.MILLISECONDS)
            Log.d("BookReader", "measurePageScrollHeight: latch await finished=$finished")
        }

        if (height == 0) {
            height = screenHeight
            Log.d("BookReader", "measurePageScrollHeight: used fallback height=$height")
        }

        Log.d("BookReader", "Измерено: height=$height, screenHeight=$screenHeight, превышение=${height - screenHeight}px")
        height
    }

    private fun buildPageHtml(text: String, useFonts: Boolean = true): String {
        val paragraphs = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val paragraphsHtml = paragraphs.joinToString("") { p ->
            "<p>${p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</p>"
        }

        val fontFaceCss = if (useFonts && currentFontPath.isNotEmpty()) {
            val cachedPath = copyFontToCache(currentFontPath)
            if (cachedPath.isNotEmpty()) {
                """
                @font-face {
                    font-family: 'CustomFont';
                    src: url('file://$cachedPath');
                }
                """.trimIndent()
            } else ""
        } else ""

        val fontFamilyStyle = if (useFonts && currentFontPath.isNotEmpty()) "'CustomFont'" else fontFamily

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=${screenWidth}px, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                $fontFaceCss

                html, body {
                    margin: 0;
                    padding: 0;
                    width: ${screenWidth}px;
                    height: ${screenHeight}px;
                    overflow: hidden;
                    background-color: #FFFFFF;
                }
                .content {
                    box-sizing: border-box;
                    width: ${screenWidth}px;
                    height: ${screenHeight}px;
                    padding: ${paddingValue}px;
                    font-size: ${fontSize}px;
                    line-height: ${lineHeight};
                    font-family: $fontFamilyStyle;
                    text-align: justify;
                    -webkit-hyphens: auto;
                    -moz-hyphens: auto;
                    hyphens: auto;
                    word-wrap: break-word;
                    widows: 2;
                    orphans: 2;
                    overflow: hidden;
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
                <div class="content">
                    $paragraphsHtml
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun loadPage(pageNumber: Int) {
        if (pages.isEmpty()) return
        currentPage = pageNumber.coerceIn(0, pages.size - 1)

        val htmlContent = pages[currentPage]
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        updatePageIndicator()
    }

    private fun setupTouchListener() {
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.x - touchStartX
                    val diffY = event.y - touchStartY

                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > swipeThreshold) {
                        if (diffX > 0) {
                            if (currentPage > 0) {
                                loadPage(currentPage - 1)
                            }
                        } else {
                            if (currentPage < totalPages - 1) {
                                loadPage(currentPage + 1)
                            }
                        }
                    } else {
                        val tapX = event.x
                        val width = webView.width
                        if (tapX < width * 0.3f) {
                            if (currentPage > 0) {
                                loadPage(currentPage - 1)
                            }
                        } else if (tapX > width * 0.7f) {
                            if (currentPage < totalPages - 1) {
                                loadPage(currentPage + 1)
                            }
                        }
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun updatePageIndicator() {
        runOnUiThread {
            pageIndicatorView.text = "Стр.${currentPage + 1}/$totalPages | Экран:${screenWidth}x${screenHeight} | Шрифт:${fontSize}px/${lineHeight}"
        }
    }

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

    private fun copyFontToCache(assetFontPath: String): String {
        if (assetFontPath.isEmpty()) return ""
        return try {
            val fileName = assetFontPath.substringAfterLast('/')
            val cacheDir = File(cacheDir, "fonts")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val cacheFile = File(cacheDir, fileName)
            if (!cacheFile.exists()) {
                assets.open(assetFontPath).use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            cacheFile.absolutePath
        } catch (e: Exception) {
            Log.e("BookReader", "Error copying font $assetFontPath to cache", e)
            ""
        }
    }

    private fun parseBookFile(file: File): BookParser.ParsedBook {
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "fb2", "xml" -> {
                Fb2Parser.parse(file, file.nameWithoutExtension)
            }
            "epub" -> {
                EpubParser.parse(file, file.nameWithoutExtension)
            }
            "mobi", "azw3" -> {
                MobiParser.parse(file, file.nameWithoutExtension)
            }
            "pdf" -> {
                com.nightread.app.service.PdfParser.parse(file, file.nameWithoutExtension)
            }
            "txt" -> {
                TxtParser.parse(file, file.nameWithoutExtension)
            }
            "zip" -> {
                var parsed = BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
                try {
                    FileInputStream(file).use { fis ->
                        ZipInputStream(fis).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && (entry.name.endsWith(".fb2") || entry.name.endsWith(".xml"))) {
                                    val tempFile = File.createTempFile("zip_fb2", ".fb2")
                                    tempFile.deleteOnExit()
                                    tempFile.outputStream().use { fos ->
                                        zis.copyTo(fos)
                                    }
                                    parsed = Fb2Parser.parse(tempFile, file.nameWithoutExtension)
                                    tempFile.delete()
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BookReader", "Error parsing zipped FB2", e)
                }
                parsed
            }
            else -> BookParser.ParsedBook(file.nameWithoutExtension, "Неизвестен", "")
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
