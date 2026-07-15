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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.nightread.app.R
import java.io.File

/**
 * Custom WebView exposing computeHorizontalScrollRange for column count computation.
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

    // Target UI elements
    private lateinit var webView: BookWebView
    private lateinit var pageIndicatorView: TextView

    // Class variables for screen size and column calculations
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var currentColumn: Int = 0
    private var totalColumns: Int = 1

    // Settings variables with default values
    private var fontSize: Int = 18
    private var lineHeight: Float = 1.6f
    private var fontFamily: String = "Georgia, 'Times New Roman', serif"
    private var currentFontPath: String = ""
    private var paddingValue: Int = 20

    // Constant padding in dp
    private val paddingDp = 24

    // Book content text
    private var bookText: String = ""

    // Touch gesture properties
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val swipeThreshold = 100f // threshold in pixels

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Restore state if available
        if (savedInstanceState != null) {
            currentColumn = savedInstanceState.getInt("current_column", 0)
            fontSize = savedInstanceState.getInt("font_size", 18)
            lineHeight = savedInstanceState.getFloat("line_height", 1.6f)
            fontFamily = savedInstanceState.getString("font_family", "Georgia, 'Times New Roman', serif")
            currentFontPath = savedInstanceState.getString("current_font_path", "")
        }

        // Set layout activity_book.xml
        setContentView(R.layout.activity_book)

        // Initialize UI components
        val rootLayout = findViewById<FrameLayout>(R.id.rootView)
        webView = findViewById(R.id.bookWebView)

        // Dynamically add page indicator on top of FrameLayout to preserve clean XML layout
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

        // Load content
        bookText = intent.getStringExtra("book_text") ?: getDefaultBookText()

        // Wait until WebView layout is complete to get exact dimensions
        webView.post {
            screenWidth = webView.width
            screenHeight = webView.height
            val density = resources.displayMetrics.density
            paddingValue = (paddingDp * density).toInt()

            Log.d("BookReader", "Screen dimensions: ${screenWidth}x${screenHeight}, Padding: ${paddingValue}px")

            if (screenWidth > 0 && screenHeight > 0) {
                loadBook(bookText)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_column", currentColumn)
        outState.putInt("font_size", fontSize)
        outState.putFloat("line_height", lineHeight)
        outState.putString("font_family", fontFamily)
        outState.putString("current_font_path", currentFontPath)
    }

    /**
     * Basic settings for offline-mode without internet.
     */
    private fun setupWebView() {
        val settings = webView.settings
        
        // Offline security settings
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        
        // Prevent zoom controls
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        
        // Enable scripts and adaptive viewport
        settings.javaScriptEnabled = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false

        // Disable standard scrollbars and scroll mechanics
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        // Override client to intercept layout changes
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.post {
                    val range = webView.getHorizontalScrollRange()
                    totalColumns = if (screenWidth > 0) (range + screenWidth - 1) / screenWidth else 1
                    if (totalColumns <= 0) totalColumns = 1

                    Log.d("BookReader", "Page Loaded. HorizontalScrollRange: $range, TotalColumns: $totalColumns")

                    // Constrain column index to valid bounds
                    if (currentColumn >= totalColumns) {
                        currentColumn = totalColumns - 1
                    }
                    if (currentColumn < 0) {
                        currentColumn = 0
                    }

                    // Perform scroll to the target column
                    webView.scrollTo(currentColumn * screenWidth, 0)
                    updatePageIndicator()
                }
            }
        }
    }

    /**
     * Loads the entire book as one HTML in the WebView.
     */
    fun loadBook(text: String) {
        bookText = text
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.e("BookReader", "Cannot load book: dimensions not ready yet")
            return
        }

        val htmlContent = buildFullBookHtml(text)
        webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
    }

    /**
     * Creates full HTML document with optimized CSS multi-column pagination.
     */
    private fun buildFullBookHtml(text: String): String {
        // Convert simple text into HTML paragraphs
        val paragraphs = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val paragraphsHtml = paragraphs.joinToString("") { p ->
            "<p>${p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</p>"
        }

        // Custom font face embedding
        val fontFaceCss = if (currentFontPath.isNotEmpty()) {
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

        val fontFamilyStyle = if (currentFontPath.isNotEmpty()) "'CustomFont'" else fontFamily

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
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
                body {
                    column-width: ${screenWidth}px;
                    column-gap: 0px;
                    column-fill: auto;
                    -webkit-column-width: ${screenWidth}px;
                    -webkit-column-gap: 0px;
                    -webkit-column-fill: auto;
                }
                .content {
                    box-sizing: border-box;
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

    /**
     * Copies a custom font file from assets folder into internal cache folder for file URL access.
     */
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

    /**
     * Touch gesture logic for horizontal column switching.
     */
    private fun setupTouchListener() {
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    true // Consume ACTION_DOWN to capture subsequent touch movements and release
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.x - touchStartX
                    val diffY = event.y - touchStartY

                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > swipeThreshold) {
                        if (diffX > 0) {
                            // Swipe Right -> previous page
                            if (currentColumn > 0) {
                                currentColumn--
                                webView.scrollTo(currentColumn * screenWidth, 0)
                                updatePageIndicator()
                            }
                        } else {
                            // Swipe Left -> next page
                            if (currentColumn < totalColumns - 1) {
                                currentColumn++
                                webView.scrollTo(currentColumn * screenWidth, 0)
                                updatePageIndicator()
                            }
                        }
                    } else {
                        // Handle simple click taps on edges
                        val tapX = event.x
                        val width = webView.width
                        if (tapX < width * 0.3f) {
                            if (currentColumn > 0) {
                                currentColumn--
                                webView.scrollTo(currentColumn * screenWidth, 0)
                                updatePageIndicator()
                            }
                        } else if (tapX > width * 0.7f) {
                            if (currentColumn < totalColumns - 1) {
                                currentColumn++
                                webView.scrollTo(currentColumn * screenWidth, 0)
                                updatePageIndicator()
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

    /**
     * Updates page indicator.
     */
    private fun updatePageIndicator() {
        runOnUiThread {
            val displayPage = currentColumn + 1
            val displayTotal = totalColumns
            pageIndicatorView.text = "$displayPage / $displayTotal"
        }
    }

    // Public Settings Customization APIs
    fun increaseFontSize() {
        fontSize += 2
        loadBook(bookText)
    }

    fun decreaseFontSize() {
        fontSize = (fontSize - 2).coerceAtLeast(10)
        loadBook(bookText)
    }

    fun setLineHeight(multiplier: Float) {
        lineHeight = multiplier
        loadBook(bookText)
    }

    fun setFont(fontName: String, fontPath: String) {
        fontFamily = fontName
        currentFontPath = fontPath
        loadBook(bookText)
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
