package com.nightread.app.ui.customlayout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun ReaderWebViewComponent(
    modifier: Modifier = Modifier,
    htmlContent: String,
    fontFamily: String = "Serif",
    fontSize: Float,
    fontWeight: Float,
    lineHeight: Float,
    textColorHex: String = "#000000",
    bgColorHex: String = "#FFFFFF",
    isHyphenationEnabled: Boolean = true,
    pageAnimation: String = "slide",
    topPaddingDp: Int = 0,
    bottomPaddingDp: Int = 20,
    leftPaddingDp: Int = 8,
    rightPaddingDp: Int = 8,
    themeColor: Color,
    bgColor: Color,
    currentPage: Int = 0,
    targetOffset: Int? = null,
    onTargetOffsetHandled: () -> Unit = {},
    onPositionChanged: (Int, Int, Int) -> Unit,
    onWordSelected: (String) -> Unit,
    onNoteClicked: (String) -> Unit,
    onNextPage: () -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onToggleBars: () -> Unit = {},
    onTopLeftTap: () -> Unit = {},
    onVerticalScroll: (Float, Float) -> Unit = { _, _ -> },
    onWebViewCreated: (WebView) -> Unit = {}
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val latestOnNextPage by rememberUpdatedState(onNextPage)
    val latestOnPreviousPage by rememberUpdatedState(onPreviousPage)
    val latestOnTopLeftTap by rememberUpdatedState(onTopLeftTap)
    var lastLoadedHtml by remember { mutableStateOf("") }
    var lastStyleKey by remember { mutableStateOf("") }
    var lastAnimationMode by remember { mutableStateOf(pageAnimation) }
    var lastReportedOffset by remember { mutableIntStateOf(-1) }
    var lastReportedPage by remember { mutableIntStateOf(-1) }
    var lastValidOffset by remember { mutableIntStateOf(-1) }

    val currentTargetOffset by rememberUpdatedState(targetOffset)
    val currentOnTargetOffsetHandled by rememberUpdatedState(onTargetOffsetHandled)
    val currentCurrentPage by rememberUpdatedState(currentPage)
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(
                    AndroidColor.argb(
                        (bgColor.alpha * 255).toInt(),
                        (bgColor.red * 255).toInt(),
                        (bgColor.green * 255).toInt(),
                        (bgColor.blue * 255).toInt()
                    )
                )
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }

                alpha = 0f
                val bridge = ReaderWebViewBridge(
                    onPositionChanged = { offset, page, total ->
                        val target = currentTargetOffset
                        if (target != null && target > 100 && offset <= 30 && page == 0) {
                            Log.d("WEBVIEW_ENGINE", "Ignoring initial position report offset=$offset while waiting for targetOffset=$target")
                            return@ReaderWebViewBridge
                        }
                        if (offset >= 0) {
                            lastValidOffset = offset
                        }
                        lastReportedOffset = offset
                        lastReportedPage = page
                        currentOnPositionChanged(offset, page, total)
                        post {
                            alpha = 1f
                        }
                        currentOnTargetOffsetHandled()
                    },
                    onWordSelected = onWordSelected,
                    onNoteClicked = onNoteClicked
                )
                addJavascriptInterface(bridge, "ReaderBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val target = currentTargetOffset
                        val offsetToScroll = when {
                            target != null && target > 0 -> target
                            lastValidOffset > 0 -> lastValidOffset
                            else -> null
                        }
                        Log.d("WEBVIEW_ENGINE", "onPageFinished: offsetToScroll=$offsetToScroll, target=$target, lastValid=$lastValidOffset, page=$currentCurrentPage")
                        if (offsetToScroll != null) {
                            view?.evaluateJavascript("window.scrollToOffset($offsetToScroll);", null)
                        } else {
                            view?.evaluateJavascript("window.scrollToPage($currentCurrentPage);", null)
                        }

                        view?.postDelayed({
                            if (view?.alpha ?: 1f < 1f) {
                                view?.alpha = 1f
                            }
                        }, 500)
                    }
                }

                var downX = 0f
                var downY = 0f
                var lastY = 0f
                var downTime = 0L
                var isVerticalDrag = false
                var isHorizontalSwipe = false

                setOnTouchListener { _, event ->
                    val densityPx = resources.displayMetrics.density.coerceAtLeast(1f)
                    val dragThreshold = 14f * densityPx
                    val tapSlop = 16f * densityPx

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            lastY = event.y
                            downTime = System.currentTimeMillis()
                            isVerticalDrag = false
                            isHorizontalSwipe = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val currentX = event.x
                            val currentY = event.y
                            val deltaX = currentX - downX
                            val deltaY = currentY - downY

                            if (!isVerticalDrag && !isHorizontalSwipe) {
                                if (Math.abs(deltaX) > dragThreshold && Math.abs(deltaX) >= Math.abs(deltaY)) {
                                    isHorizontalSwipe = true
                                } else if (Math.abs(deltaY) > dragThreshold * 1.4f && Math.abs(deltaY) > Math.abs(deltaX) * 1.3f) {
                                    isVerticalDrag = true
                                    lastY = currentY
                                }
                            }

                            if (isHorizontalSwipe) {
                                val deltaCssPx = deltaX / densityPx
                                this.evaluateJavascript("if(window.onSwipeDrag){window.onSwipeDrag($deltaCssPx);}", null)
                            } else if (isVerticalDrag) {
                                val stepY = currentY - lastY
                                onVerticalScroll(downX, stepY)
                                lastY = currentY
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            val upX = event.x
                            val upY = event.y
                            val deltaX = upX - downX
                            val deltaY = upY - downY
                            val duration = System.currentTimeMillis() - downTime
                            val screenWidth = width.coerceAtLeast(1)

                            if (isHorizontalSwipe) {
                                val swipeCommitDistance = screenWidth * 0.12f
                                val isQuickFlick = Math.abs(deltaX) > 36f * densityPx && duration < 320
                                if (Math.abs(deltaX) > swipeCommitDistance || isQuickFlick) {
                                    if (deltaX > 0) {
                                        this.evaluateJavascript("if(window.prevPage){window.prevPage();}", null)
                                        latestOnPreviousPage()
                                    } else {
                                        this.evaluateJavascript("if(window.nextPage){window.nextPage();}", null)
                                        latestOnNextPage()
                                    }
                                } else {
                                    this.evaluateJavascript("if(window.snapCurrentPage){window.snapCurrentPage();}", null)
                                }
                            } else if (!isVerticalDrag) {
                                if (duration < 400 && Math.abs(deltaX) < tapSlop && Math.abs(deltaY) < tapSlop) {
                                    // Tap zones: top-left corner toggle sepia/night, left 25% prev page, right 25% next page, center 50% toggle fullscreen
                                    val screenHeight = height.coerceAtLeast(1)
                                    when {
                                        upX < screenWidth * 0.22f && upY < screenHeight * 0.14f -> {
                                            latestOnTopLeftTap()
                                        }
                                        upX < screenWidth * 0.25f -> {
                                            this.evaluateJavascript("if(window.prevPage){window.prevPage();}", null)
                                            latestOnPreviousPage()
                                        }
                                        upX > screenWidth * 0.75f -> {
                                            this.evaluateJavascript("if(window.nextPage){window.nextPage();}", null)
                                            latestOnNextPage()
                                        }
                                        else -> {
                                            onToggleBars()
                                        }
                                    }
                                }
                            }
                            isVerticalDrag = false
                            isHorizontalSwipe = false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (isHorizontalSwipe) {
                                this.evaluateJavascript("if(window.snapCurrentPage){window.snapCurrentPage();}", null)
                            }
                            isVerticalDrag = false
                            isHorizontalSwipe = false
                        }
                    }
                    true
                }

                lastLoadedHtml = htmlContent
                lastStyleKey = "$fontFamily-$fontSize-$fontWeight-$lineHeight-$textColorHex-$bgColorHex-$isHyphenationEnabled-$topPaddingDp-$bottomPaddingDp-$leftPaddingDp-$rightPaddingDp"
                lastAnimationMode = pageAnimation
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                webViewRef = this
                onWebViewCreated(this)
            }
        },
        update = { view ->
            val currentStyleKey = "$fontFamily-$fontSize-$fontWeight-$lineHeight-$textColorHex-$bgColorHex-$isHyphenationEnabled-$topPaddingDp-$bottomPaddingDp-$leftPaddingDp-$rightPaddingDp"

            if (htmlContent != lastLoadedHtml) {
                lastLoadedHtml = htmlContent
                lastStyleKey = currentStyleKey
                lastAnimationMode = pageAnimation
                Log.d("WEBVIEW_ENGINE", "WebView content changed, reloading HTML.")
                view.alpha = 0f
                view.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } else {
                if (pageAnimation != lastAnimationMode) {
                    lastAnimationMode = pageAnimation
                    view.evaluateJavascript("if (window.setPageAnimation) { window.setPageAnimation('$pageAnimation'); }", null)
                }
                if (currentStyleKey != lastStyleKey) {
                    lastStyleKey = currentStyleKey
                    Log.d("WEBVIEW_ENGINE", "Updating WebView styles seamlessly without full reload.")
                    val jsCode = "if (window.updateStyles) { window.updateStyles('$fontFamily', $fontSize, $fontWeight, $lineHeight, '$textColorHex', '$bgColorHex', ${if (isHyphenationEnabled) "true" else "false"}, $topPaddingDp, $bottomPaddingDp, $leftPaddingDp, $rightPaddingDp); }"
                    view.evaluateJavascript(jsCode, null)
                } else if (targetOffset != null) {
                    Log.d("WEBVIEW_ENGINE", "WebView scrolling to targetOffset: $targetOffset")
                    view.evaluateJavascript("window.scrollToOffset($targetOffset);", null)
                    onTargetOffsetHandled()
                }
            }
        }
    )
}
