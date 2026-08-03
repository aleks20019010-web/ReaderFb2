package com.nightread.app.ui

import android.graphics.Color
import android.os.Bundle
import android.text.Layout
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nightread.app.R
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class PageFragment : Fragment() {
    private var pageText: CharSequence = ""
    private var mWebView: android.webkit.WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageText = arguments?.getCharSequence("PAGE_TEXT") ?: ""
    }

    @Suppress("WrongConstant")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_page, container, false)
        val root = view.findViewById<FrameLayout>(R.id.rootContainer)
        val webView = view.findViewById<android.webkit.WebView>(R.id.bookWebView)
        mWebView = webView
        
        updateStyle(root, webView)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val root = view.findViewById<FrameLayout>(R.id.rootContainer)
        var cachedMaxTop = 0
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, windowInsets ->
            val displayCutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val currentMeasuredTop = maxOf(statusBarInsets.top, displayCutoutInsets.top)
            if (currentMeasuredTop > cachedMaxTop) {
                cachedMaxTop = currentMeasuredTop
            }
            val minTop = (36 * v.resources.displayMetrics.density).toInt()
            val topInset = maxOf(currentMeasuredTop, cachedMaxTop, minTop)
            
            val dp6 = (6 * v.resources.displayMetrics.density).toInt()
            val dp8 = (8 * v.resources.displayMetrics.density).toInt()
            val dp14 = (14 * v.resources.displayMetrics.density).toInt()
            
            v.setPadding(dp6, dp8 + topInset, dp6, dp14)
            windowInsets
        }
        view.requestApplyInsets()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SettingsManager.settingsChanged.collect {
                    val root = view.findViewById<FrameLayout>(R.id.rootContainer)
                    val webView = view.findViewById<android.webkit.WebView>(R.id.bookWebView)
                    updateStyle(root, webView)
                }
            }
        }
    }

    @Suppress("WrongConstant")
    private fun updateStyle(root: FrameLayout, webView: android.webkit.WebView) {
        val context = requireContext()
        val fontSize = SettingsManager.getFontSize(context)
        val themeName = SettingsManager.getReadingTheme(context)
        val fontFamily = SettingsManager.getFontFamily(context)
        val lineSpacingMultiplier = SettingsManager.getLineSpacing(context)

        val (bgColor, textColor, linkColor) = when (themeName) {
            "light" -> Triple("#FFFFFF", "#121212", "#E53935")
            "dark" -> Triple("#1A1A1A", "#E0E0E0", "#FF8A80")
            "sepia" -> Triple("#F5F0E8", "#2C2C2C", "#D32F2F")
            "sepia_contrast" -> Triple("#F5E6C8", "#1A1A1A", "#C62828")
            "contrast" -> Triple("#000000", "#FFFF00", "#FF5252")
            "beige" -> Triple("#F4ECD8", "#3B2F1F", "#8D6E63")
            "amoled" -> Triple("#000000", "#D9CEE2", "#B388FF")
            else -> Triple("#F5F0E8", "#2C2C2C", "#D32F2F")
        }

        root.setBackgroundColor(Color.parseColor(bgColor))
        webView.setBackgroundColor(Color.parseColor(bgColor))

        webView.settings.apply {
            javaScriptEnabled = true
            blockNetworkImage = true
            blockNetworkLoads = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            allowContentAccess = false
            allowFileAccess = true
        }

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        webView.isLongClickable = true
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.addJavascriptInterface(BookReaderActivity.WebAppInterface(activity as? BookReaderActivity ?: return), "AndroidInterface")

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                url?.let {
                    if (it.startsWith("note:")) {
                        val noteId = it.substring(5)
                        val bookReaderActivity = activity as? BookReaderActivity
                        bookReaderActivity?.showFootnote(noteId)
                        return true
                    }
                }
                return false
            }

            @android.annotation.TargetApi(android.os.Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                url?.let {
                    if (it.startsWith("note:")) {
                        val noteId = it.substring(5)
                        val bookReaderActivity = activity as? BookReaderActivity
                        bookReaderActivity?.showFootnote(noteId)
                        return true
                    }
                }
                return false
            }
        }

        val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                val width = webView.width.toFloat()
                val height = webView.height.toFloat()
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
                val width = webView.width.toFloat()
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

        webView.setOnTouchListener { _, event ->
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

        // Calculate exact font size and line height in physical pixels
        val paint = android.text.TextPaint().apply {
            isAntiAlias = true
            textSize = fontSize * resources.displayMetrics.scaledDensity
            typeface = FontUtils.createTypefaceWithOpticalBalance(context, fontFamily, SettingsManager.getFontWeightAsInt(context))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                letterSpacing = SettingsManager.getLetterSpacing(context)
            }
        }
        val fm = paint.fontMetrics
        val fontHeight = fm.descent - fm.ascent
        val lineHeight = fontHeight * lineSpacingMultiplier

        // Convert to CSS px (which acts as dp in WebView with standard viewport settings)
        val density = resources.displayMetrics.density
        val cssFontSize = paint.textSize / density
        val cssLineHeight = lineHeight / density
        val letterSpacing = SettingsManager.getLetterSpacing(context)

        val htmlString = buildBookHtml(context, pageText, cssFontSize, cssLineHeight, fontFamily, themeName, letterSpacing)
        webView.loadDataWithBaseURL("file:///android_asset/", htmlString, "text/html", "UTF-8", null)
    }

    private fun buildBookHtml(
        context: android.content.Context,
        text: CharSequence,
        cssFontSize: Float,
        cssLineHeight: Float,
        fontFamily: String,
        themeName: String,
        letterSpacing: Float
    ): String {
        val htmlContent = spannedToHtml(text)

        val (bgColor, textColor, linkColor) = when (themeName) {
            "light" -> Triple("#FFFFFF", "#121212", "#E53935")
            "dark" -> Triple("#1A1A1A", "#E0E0E0", "#FF8A80")
            "sepia" -> Triple("#F5F0E8", "#2C2C2C", "#D32F2F")
            "sepia_contrast" -> Triple("#F5E6C8", "#1A1A1A", "#C62828")
            "contrast" -> Triple("#000000", "#FFFF00", "#FF5252")
            "beige" -> Triple("#F4ECD8", "#3B2F1F", "#8D6E63")
            "amoled" -> Triple("#000000", "#D9CEE2", "#B388FF")
            else -> Triple("#F5F0E8", "#2C2C2C", "#D32F2F")
        }

        val fontFaceCss = when (fontFamily) {
            "Lora" -> """
                @font-face {
                    font-family: 'CustomFont';
                    src: url('file://${getFontCachePath(context, "lora.ttf")}');
                }
            """.trimIndent()
            "EB Garamond" -> """
                @font-face {
                    font-family: 'CustomFont';
                    src: url('file://${getFontCachePath(context, "eb_garamond.ttf")}');
                }
            """.trimIndent()
            "Literata" -> """
                @font-face {
                    font-family: 'CustomFont';
                    src: url('file://${getFontCachePath(context, "literata.ttf")}');
                }
            """.trimIndent()
            else -> ""
        }

        val fontStyle = if (fontFaceCss.isNotEmpty()) "font-family: 'CustomFont', serif;" else "font-family: sans-serif;"

        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    $fontFaceCss
                    
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                    }
                    
                    body {
                        background-color: $bgColor;
                        color: $textColor;
                        font-size: ${cssFontSize}px;
                        line-height: ${cssLineHeight}px;
                        $fontStyle
                        font-weight: ${SettingsManager.getFontWeightAsInt(context)};
                        letter-spacing: ${letterSpacing}em;
                        text-align: justify;
                        text-align-last: left;
                        -webkit-text-align-last: left;
                        -webkit-hyphens: auto;
                        -moz-hyphens: auto;
                        -ms-hyphens: auto;
                        hyphens: auto;
                        overflow: hidden;
                        word-wrap: break-word;
                        word-break: normal;
                        overflow-wrap: break-word;
                        -webkit-user-select: text;
                        user-select: text;
                        -webkit-touch-callout: default;
                    }
                    
                    p {
                        margin-top: 0;
                        margin-bottom: 0.15em;
                        padding: 0;
                        text-indent: 1.25em;
                        text-align: justify;
                        text-align-last: left;
                        -webkit-text-align-last: left;
                        -webkit-hyphens: auto;
                        -moz-hyphens: auto;
                        -ms-hyphens: auto;
                        hyphens: auto;
                        word-break: normal;
                        overflow-wrap: break-word;
                    }
                    
                    a {
                        color: $linkColor;
                        text-decoration: none;
                    }
                    
                    .chapter {
                        display: block;
                        text-align: center !important;
                        font-weight: bold !important;
                        font-size: 1.4em;
                        margin-top: 1.2em;
                        margin-bottom: 1.2em;
                    }

                    .tts-highlight {
                        background-color: rgba(255, 235, 59, 0.4);
                        border-radius: 4px;
                    }
                </style>
                <script type="text/javascript">
                    document.addEventListener('touchend', function() {
                        var selection = window.getSelection();
                        var selectedText = selection.toString().trim();
                        if (selectedText.length > 0) {
                            var container = null;
                            if (selection.rangeCount > 0) {
                                var range = selection.getRangeAt(0);
                                container = range.commonAncestorContainer;
                                while (container && container.nodeType !== 1) {
                                    container = container.parentNode;
                                }
                            }
                            var contextSnippet = container ? container.innerText || "" : "";
                            if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onTextSelected) {
                                AndroidInterface.onTextSelected(selectedText, contextSnippet);
                            }
                        }
                    });

                    function highlightTtsText(start, end) {
                        clearTtsHighlight();
                        if (start < 0 || end <= start) return;
                        let charCount = 0;
                        let walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                        let node;
                        let startNode = null, startOffset = 0;
                        let endNode = null, endOffset = 0;
                        while (node = walker.nextNode()) {
                            let nodeLen = node.nodeValue.length;
                            if (startNode === null && charCount + nodeLen >= start) {
                                startNode = node;
                                startOffset = start - charCount;
                            }
                            if (endNode === null && charCount + nodeLen >= end) {
                                endNode = node;
                                endOffset = end - charCount;
                                break;
                            }
                            charCount += nodeLen;
                        }
                        if (startNode && endNode) {
                            try {
                                let range = document.createRange();
                                range.setStart(startNode, startOffset);
                                range.setEnd(endNode, endOffset);
                                let span = document.createElement('span');
                                span.className = 'tts-highlight';
                                range.surroundContents(span);
                                span.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            } catch (e) {}
                        }
                    }

                    function clearTtsHighlight() {
                        let highlights = document.querySelectorAll('.tts-highlight');
                        highlights.forEach(span => {
                            let parent = span.parentNode;
                            if (parent) {
                                parent.replaceChild(document.createTextNode(span.textContent), span);
                                parent.normalize();
                            }
                        });
                    }
                </script>
            </head>
            <body>
                $htmlContent
            </body>
            </html>
        """.trimIndent()
    }

    private fun spannedToHtml(text: CharSequence): String {
        if (text !is Spanned) {
            return escapeHtml(text.toString()).replace("\n", "<br>")
        }

        val sb = java.lang.StringBuilder()
        var i = 0
        while (i < text.length) {
            val next = text.nextSpanTransition(i, text.length, Any::class.java)
            val chunk = text.subSequence(i, next)

            val spans = text.getSpans(i, next, Any::class.java)
            var htmlChunk = escapeHtml(chunk.toString()).replace("\n", "<br>")

            for (span in spans) {
                when (span) {
                    is StyleSpan -> {
                        if (span.style == android.graphics.Typeface.BOLD) {
                            htmlChunk = "<b>$htmlChunk</b>"
                        } else if (span.style == android.graphics.Typeface.ITALIC) {
                            htmlChunk = "<i>$htmlChunk</i>"
                        }
                    }
                    is android.text.style.SuperscriptSpan -> {
                        htmlChunk = "<sup>$htmlChunk</sup>"
                    }
                    is android.text.style.RelativeSizeSpan -> {
                        htmlChunk = "<span style='font-size: 75%;'>$htmlChunk</span>"
                    }
                    is android.text.style.AbsoluteSizeSpan -> {
                        if (span.size > 0) {
                            htmlChunk = "<span class='chapter'>$htmlChunk</span>"
                        } else {
                            htmlChunk = "" // Hide zero size tags
                        }
                    }
                    is AlignmentSpan -> {
                        if (span.alignment == Layout.Alignment.ALIGN_CENTER) {
                            htmlChunk = "<div style='text-align: center;'>$htmlChunk</div>"
                        }
                    }
                    is android.text.Annotation -> {
                        if (span.key == "note") {
                            htmlChunk = "<a href='note:${span.value}'>$htmlChunk</a>"
                        }
                    }
                }
            }
            sb.append(htmlChunk)
            i = next
        }
        return sb.toString()
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun getFontCachePath(context: android.content.Context, fontName: String): String {
        val fontsDir = File(context.cacheDir, "fonts")
        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }
        val fontFile = File(fontsDir, fontName)
        if (!fontFile.exists() || fontFile.length() == 0L) {
            try {
                val resId = when (fontName) {
                    "eb_garamond.ttf" -> R.font.eb_garamond
                    "literata.ttf" -> R.font.literata
                    "lora.ttf" -> R.font.lora
                    else -> return ""
                }
                context.resources.openRawResource(resId).use { input ->
                    FileOutputStream(fontFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return fontFile.absolutePath
    }

    fun highlightCurrentSelection(colorHex: String) {
        val wv = mWebView ?: return
        val js = """
            (function() {
                var sel = window.getSelection();
                if (sel && !sel.isCollapsed && sel.rangeCount > 0) {
                    var range = sel.getRangeAt(0);
                    var span = document.createElement('mark');
                    span.style.backgroundColor = '$colorHex';
                    span.style.color = 'inherit';
                    span.style.borderRadius = '3px';
                    span.style.padding = '0 2px';
                    range.surroundContents(span);
                    sel.removeAllRanges();
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    fun highlightTtsRange(start: Int, end: Int) {
        mWebView?.evaluateJavascript("javascript:highlightTtsText($start, $end);", null)
    }

    fun clearTtsHighlight() {
        mWebView?.evaluateJavascript("javascript:clearTtsHighlight();", null)
    }

    companion object {
        fun newInstance(pageText: CharSequence, offset: Int = 0): PageFragment {
            val fragment = PageFragment()
            val args = Bundle().apply {
                putCharSequence("PAGE_TEXT", pageText)
                putInt("PAGE_OFFSET", offset)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
