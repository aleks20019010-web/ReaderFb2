package com.nightread.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.nightread.app.R
import com.nightread.app.data.SettingsManager
import com.nightread.app.service.EpubToHtmlConverter
import com.nightread.app.service.Fb2ToHtmlConverterAdvanced
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

class BookReaderFragment : Fragment() {

    private var webView: WebView? = null
    private var bookFile: File? = null
    private var bookSha1: String? = null

    private lateinit var viewModel: ReaderViewModel

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_readium_container, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java)

        val container = view.findViewById<FrameLayout>(R.id.readiumFragmentContainer)

        val wv = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(wv)
        webView = wv

        setupWebView(wv)
        loadBookContent()
    }

    private fun setupWebView(wv: WebView) {
        val themeName = SettingsManager.getReadingTheme(requireContext())
        val (bgColor, _) = when (themeName) {
            "light", "beige" -> "#FFFBF0" to "#1A1A1A"
            "sepia", "sepia_contrast" -> "#F4ECD8" to "#5C4033"
            "dark", "contrast" -> "#121212" to "#E0E0E0"
            "amoled" -> "#000000" to "#FFFFFF"
            else -> "#FFFBF0" to "#1A1A1A"
        }
        wv.setBackgroundColor(Color.parseColor(bgColor))

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            textZoom = 100
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false
        wv.overScrollMode = View.OVER_SCROLL_NEVER

        // Javascript Interface for callbacks
        wv.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun onPagesCalculated(total: Int) {
                activity?.runOnUiThread {
                    _totalPages.value = total.coerceAtLeast(1)
                    val act = activity as? BookReaderActivity
                    act?.onWebViewPagesCalculated(total)
                    updateProgressUI()
                }
            }

            @JavascriptInterface
            fun onParagraphVisible(pId: String) {
                activity?.runOnUiThread {
                    val pIndex = pId.substringAfter("p_").toIntOrNull() ?: 0
                    viewModel.updateWebViewParagraphProgress(pIndex)
                }
            }

            @JavascriptInterface
            fun onPageRestored(pageIndex: Int) {
                activity?.runOnUiThread {
                    _currentPage.value = pageIndex
                    updateProgressUI()
                }
            }

            @JavascriptInterface
            fun onTextSelected(selectedText: String, contextSnippet: String) {
                activity?.runOnUiThread {
                    val act = activity as? BookReaderActivity
                    act?.showCustomSelectionBottomSheet(selectedText, contextSnippet)
                }
            }
        }, "AndroidInterface")

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updatePreferences()
                wv.postDelayed({
                    val savedProgress = viewModel.bookState.value?.currentProgressChar ?: 0
                    scrollToParagraph("p_$savedProgress")
                }, 300)
            }
        }

        // Setup gestures
        val gestureDetector = android.view.GestureDetector(requireContext(), object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                val width = wv.width.toFloat()
                val height = wv.height.toFloat()
                if (width <= 0 || height <= 0) return false

                val x = e.x
                val y = e.y
                val act = activity as? BookReaderActivity ?: return false

                if (x > width * 0.75f && y < height * 0.20f) {
                    act.toggleBookmark()
                    return true
                }
                if (x < width * 0.25f) {
                    act.onReaderTapLeft()
                    return true
                }
                if (x > width * 0.75f) {
                    act.onReaderTapRight()
                    return true
                }
                act.toggleToolbars()
                return true
            }

            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val act = activity as? BookReaderActivity ?: return false

                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 80 && Math.abs(velocityX) > 150) {
                    if (dx < 0) {
                        act.onReaderSwipeLeft()
                        return true
                    } else {
                        act.onReaderSwipeRight()
                        return true
                    }
                }
                return false
            }

            override fun onScroll(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null) return false
                val act = activity as? BookReaderActivity ?: return false
                val width = wv.width.toFloat()
                if (width <= 0) return false

                val startX = e1.x
                if (startX < width * 0.35f && Math.abs(distanceY) > Math.abs(distanceX) * 1.5f) {
                    act.adjustBrightnessByDrag(distanceY)
                    return true
                }
                if (startX > width * 0.65f && Math.abs(distanceY) > Math.abs(distanceX) * 1.5f) {
                    act.adjustWarmthByDrag(distanceY)
                    return true
                }
                return false
            }
        })

        wv.setOnTouchListener { _, event ->
            val handled = gestureDetector.onTouchEvent(event)
            if (handled) {
                true
            } else {
                if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    (activity as? BookReaderActivity)?.onGestureEnded()
                }
                false
            }
        }
    }

    fun initBook(file: File, sha1: String) {
        this.bookFile = file
        this.bookSha1 = sha1
        if (isAdded && webView != null) {
            loadBookContent()
        }
    }

    private fun loadBookContent() {
        val wv = webView ?: return
        val context = requireContext()
        val file = bookFile ?: return

        lifecycleScope.launch {
            val contentStr = withContext(Dispatchers.IO) { viewModel.getContentText() }
            val theme = SettingsManager.getReadingTheme(context)
            val fontSize = SettingsManager.getFontSize(context)
            val lineSpacing = SettingsManager.getLineSpacing(context)
            val fontFamily = SettingsManager.getFontFamily(context)
            val fontWeight = SettingsManager.getFontWeightAsInt(context)

            val density = resources.displayMetrics.density
            val leftRightMarginDp = 8
            val bottomMarginDp = 16

            val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeightPx = if (resourceId > 0) {
                context.resources.getDimensionPixelSize(resourceId)
            } else {
                0
            }
            val statusBarHeightDp = (statusBarHeightPx / density).toInt()
            val topMarginDp = statusBarHeightDp + 3

            val html = withContext(Dispatchers.Default) {
                val sha1 = bookSha1 ?: "unknown_book"
                val cacheFile = java.io.File(context.cacheDir, "$sha1.html")
                if (cacheFile.exists()) {
                    cacheFile.readText()
                } else {
                    val converted = if (file.extension.lowercase() == "fb2" || file.name.endsWith(".fb2.zip", true) || file.name.endsWith(".zip", true)) {
                        Fb2ToHtmlConverterAdvanced.convert(
                            fb2Xml = contentStr,
                            theme = theme,
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            fontAlignment = "JUSTIFY",
                            pageMargins = true,
                            paddingTop = topMarginDp,
                            paddingBottom = bottomMarginDp,
                            paddingLeft = leftRightMarginDp,
                            paddingRight = leftRightMarginDp
                        )
                    } else if (file.extension.lowercase() == "epub") {
                        EpubToHtmlConverter.convert(
                            xhtmlContent = contentStr,
                            theme = theme,
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            fontAlignment = "JUSTIFY",
                            pageMargins = true,
                            paddingTop = topMarginDp,
                            paddingBottom = bottomMarginDp,
                            paddingLeft = leftRightMarginDp,
                            paddingRight = leftRightMarginDp
                        )
                    } else {
                        val cleanHtml = contentStr.replace("\n", "<br/>")
                        EpubToHtmlConverter.convert(
                            xhtmlContent = "<div>$cleanHtml</div>",
                            theme = theme,
                            fontSize = fontSize,
                            lineSpacing = lineSpacing,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            fontAlignment = "JUSTIFY",
                            pageMargins = true,
                            paddingTop = topMarginDp,
                            paddingBottom = bottomMarginDp,
                            paddingLeft = leftRightMarginDp,
                            paddingRight = leftRightMarginDp
                        )
                    }
                    try { cacheFile.writeText(converted) } catch (e: Exception) { e.printStackTrace() }
                    converted
                }
            }

            val paragraphs = withContext(Dispatchers.Default) {
                com.nightread.app.service.TtsExtractor.extractParagraphs(html)
            }
            com.nightread.app.service.TtsDataProvider.paragraphs = paragraphs

            val modifiedHtml = injectCustomScript(html)
            wv.loadDataWithBaseURL("file:///android_asset/", modifiedHtml, "text/html", "UTF-8", null)
        }
    }

    private fun injectCustomScript(html: String): String {
        val script = """
            <script type="text/javascript">
                var totalPages = 1;
                var currentPage = 0;
                
                function calculatePages() {
                    var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                    if (pageWidth > 0) {
                        var sideMarginStr = getComputedStyle(document.documentElement).getPropertyValue('--side-margin');
                        var sideMargin = parseInt(sideMarginStr) || 0;
                        var colWidth = pageWidth - (sideMargin * 2);
                        var colGap = sideMargin * 2;
                        document.documentElement.style.setProperty('--column-width', colWidth + 'px');
                        document.documentElement.style.setProperty('--column-gap', colGap + 'px');
                        
                        setTimeout(function() {
                            var totalWidth = Math.max(
                                document.body.scrollWidth || 0,
                                document.documentElement.scrollWidth || 0,
                                document.body.offsetWidth || 0
                            );
                            totalPages = Math.max(1, Math.round(totalWidth / pageWidth));
                            if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onPagesCalculated) {
                                AndroidInterface.onPagesCalculated(totalPages);
                            }
                        }, 50);
                    }
                }
                
                function scrollToPage(pageIndex) {
                    var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                    if (pageWidth <= 0) return;
                    currentPage = pageIndex;
                    var x = Math.round(pageIndex * pageWidth);
                    window.scrollTo(x, 0);
                    document.body.scrollLeft = x;
                    document.documentElement.scrollLeft = x;
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onPageRestored) {
                        AndroidInterface.onPageRestored(pageIndex);
                    }
                    reportCurrentParagraph();
                }
                
                function reportCurrentParagraph() {
                    var elements = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6');
                    var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                    for (var i = 0; i < elements.length; i++) {
                        var rect = elements[i].getBoundingClientRect();
                        if (rect.right > 5 && rect.left < pageWidth) {
                            if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onParagraphVisible) {
                                AndroidInterface.onParagraphVisible(elements[i].id);
                            }
                            break;
                        }
                    }
                }
                
                function scrollToParagraph(pId) {
                    var element = document.getElementById(pId);
                    if (element) {
                        var rect = element.getBoundingClientRect();
                        var scrollLeft = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft;
                        var elementLeft = rect.left + scrollLeft;
                        var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                        if (pageWidth > 0) {
                            var pageIndex = Math.floor(elementLeft / pageWidth);
                            scrollToPage(pageIndex);
                        }
                    } else {
                        scrollToPage(0);
                    }
                }
                
                function highlightParagraph(pId) {
                    clearHighlight();
                    var element = document.getElementById(pId);
                    if (element) {
                        element.classList.add('tts-highlight');
                        element.style.backgroundColor = 'rgba(255, 255, 0, 0.4)';
                        element.style.borderRadius = '4px';
                    }
                }
                
                function clearHighlight() {
                    var elements = document.getElementsByClassName('tts-highlight');
                    while(elements.length > 0){
                        elements[0].style.backgroundColor = '';
                        elements[0].style.borderRadius = '';
                        elements[0].classList.remove('tts-highlight');
                    }
                }
                
                var resizeTimer;
                var savedParagraphId = null;

                function saveCurrentParagraph() {
                    if (!savedParagraphId) {
                        var elements = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6');
                        var pageWidth = window.innerWidth || document.documentElement.clientWidth;
                        for (var i = 0; i < elements.length; i++) {
                            var rect = elements[i].getBoundingClientRect();
                            if (rect.right > 5 && rect.left < pageWidth) {
                                savedParagraphId = elements[i].id;
                                break;
                            }
                        }
                    }
                }

                function restoreSavedParagraph() {
                    if (savedParagraphId) {
                        scrollToParagraph(savedParagraphId);
                        savedParagraphId = null;
                    }
                }

                function updateStyles(bgColor, textColor, fontFamily, fontSize, fontWeight, lineSpacing) {
                    saveCurrentParagraph();
                    document.documentElement.style.setProperty('--bg-color', bgColor);
                    document.documentElement.style.setProperty('--text-color', textColor);
                    document.documentElement.style.setProperty('--font-family', fontFamily);
                    document.documentElement.style.setProperty('--font-size', fontSize + 'px');
                    document.documentElement.style.setProperty('--font-weight', fontWeight);
                    document.documentElement.style.setProperty('--line-spacing', lineSpacing);
                    
                    clearTimeout(resizeTimer);
                    resizeTimer = setTimeout(function() {
                        calculatePages();
                        setTimeout(restoreSavedParagraph, 100);
                    }, 200);
                }
                
                window.addEventListener('load', function() {
                    calculatePages();
                    window.addEventListener('resize', function() {
                        saveCurrentParagraph();
                        clearTimeout(resizeTimer);
                        resizeTimer = setTimeout(function() {
                            calculatePages();
                            setTimeout(restoreSavedParagraph, 100);
                        }, 200);
                    });
                });
            </script>
        """.trimIndent()

        return if (html.contains("</head>")) {
            html.replace("</head>", "$script</head>")
        } else {
            html + script
        }
    }

    fun goForward(animated: Boolean = true): Boolean {
        val cur = _currentPage.value
        val total = _totalPages.value
        if (cur < total - 1) {
            scrollToPage(cur + 1, animated)
            return true
        }
        return false
    }

    fun goBackward(animated: Boolean = true): Boolean {
        val cur = _currentPage.value
        if (cur > 0) {
            scrollToPage(cur - 1, animated)
            return true
        }
        return false
    }

    fun go(pageIndex: Int) {
        scrollToPage(pageIndex, false)
    }

    fun go(pId: String) {
        scrollToParagraph(pId)
    }

    private fun scrollToPage(index: Int, animated: Boolean = false) {
        if (!isAdded || webView == null) return
        val cur = _currentPage.value
        val animMode = if (animated) SettingsManager.getPageAnimation(requireContext()) else "none"

        if (animMode != "none" && cur != index) {
            val wv = webView!!
            val bitmap = android.graphics.Bitmap.createBitmap(wv.width, wv.height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            wv.draw(canvas)

            val imageView = android.widget.ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                setImageBitmap(bitmap)
            }
            val container = view?.findViewById<FrameLayout>(R.id.readiumFragmentContainer)
            container?.addView(imageView)

            _currentPage.value = index
            wv.evaluateJavascript("scrollToPage($index)", null)
            updateProgressUI()

            if (animMode == "slide") {
                val isForward = index > cur
                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                imageView.animate()
                    .translationX(if (isForward) -screenWidth else screenWidth)
                    .setDuration(250)
                    .withEndAction {
                        container?.removeView(imageView)
                        bitmap.recycle()
                    }
                    .start()
            } else if (animMode == "fade") {
                imageView.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction {
                        container?.removeView(imageView)
                        bitmap.recycle()
                    }
                    .start()
            } else if (animMode == "depth") {
                imageView.animate()
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction {
                        container?.removeView(imageView)
                        bitmap.recycle()
                    }
                    .start()
            } else {
                container?.removeView(imageView)
                bitmap.recycle()
            }
        } else {
            _currentPage.value = index
            webView?.evaluateJavascript("scrollToPage($index)", null)
            updateProgressUI()
        }
    }

    private fun scrollToParagraph(pId: String) {
        webView?.evaluateJavascript("scrollToParagraph('$pId')", null)
    }

    fun highlightParagraph(pId: String) {
        webView?.evaluateJavascript("highlightParagraph('$pId')", null)
    }

    fun clearHighlight() {
        webView?.evaluateJavascript("clearHighlight()", null)
    }

    fun updatePreferences() {
        if (isAdded && webView != null) {
            val context = requireContext()
            val theme = SettingsManager.getReadingTheme(context)
            val fontSize = SettingsManager.getFontSize(context)
            val lineSpacing = SettingsManager.getLineSpacing(context)
            val fontFamily = SettingsManager.getFontFamily(context)
            val fontWeight = SettingsManager.getFontWeightAsInt(context)

            val cssFontFamily = when (fontFamily) {
                "EB Garamond" -> "'EB Garamond', serif"
                "Literata" -> "'Literata', serif"
                "Lora" -> "'Lora', serif"
                "Roboto", "Sans Serif" -> "'Roboto', sans-serif"
                "Serif", "Times New Roman" -> "serif"
                "Monospace" -> "monospace"
                else -> "sans-serif"
            }

            val (bgColor, textColor) = when (theme.lowercase()) {
                "light", "beige" -> "#FFFBF0" to "#1A1A1A"
                "sepia", "sepia_contrast" -> "#F4ECD8" to "#5C4033"
                "dark", "contrast" -> "#121212" to "#E0E0E0"
                "amoled" -> "#000000" to "#FFFFFF"
                else -> "#FFFBF0" to "#1A1A1A"
            }

            val fontWeightCss = if (fontWeight > 0) "bold" else "normal"

            val js = "updateStyles('$bgColor', '$textColor', \"${cssFontFamily}\", $fontSize, '$fontWeightCss', $lineSpacing);"
            webView?.evaluateJavascript(js, null)
        }
    }

    private fun updateProgressUI() {
        val act = activity as? BookReaderActivity
        val cur = _currentPage.value
        val total = _totalPages.value
        act?.updateProgressFromFragment(cur, total)
    }
}
