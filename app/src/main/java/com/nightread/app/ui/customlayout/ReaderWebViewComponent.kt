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
    fontFamily: String,
    fontSize: Float,
    fontWeight: Float,
    lineHeight: Float,
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
    onVerticalScroll: (Float, Float) -> Unit = { _, _ -> },
    onWebViewCreated: (WebView) -> Unit = {}
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedHtml by remember { mutableStateOf("") }
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
                    loadWithOverviewMode = false
                    useWideViewPort = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }

                val bridge = ReaderWebViewBridge(
                    onPositionChanged = { offset, page, total ->
                        if (offset > 0) {
                            lastValidOffset = offset
                        }
                        lastReportedOffset = offset
                        lastReportedPage = page
                        currentOnPositionChanged(offset, page, total)
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
                            if (target != null) {
                                currentOnTargetOffsetHandled()
                            }
                        } else {
                            view?.evaluateJavascript("window.scrollToPage($currentCurrentPage);", null)
                        }
                    }
                }

                var downX = 0f
                var downY = 0f
                var lastY = 0f
                var downTime = 0L
                var isDragging = false

                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                            lastY = event.y
                            downTime = System.currentTimeMillis()
                            isDragging = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val currentX = event.x
                            val currentY = event.y
                            val deltaX = currentX - downX
                            val deltaY = currentY - downY
                            
                            if (isDragging) {
                                val stepY = currentY - lastY
                                onVerticalScroll(downX, stepY)
                                lastY = currentY
                            } else if (Math.abs(deltaY) > 20 && Math.abs(deltaY) > Math.abs(deltaX) * 1.2f) {
                                isDragging = true
                                lastY = currentY
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            val upX = event.x
                            val upY = event.y
                            val deltaX = upX - downX
                            val deltaY = upY - downY
                            val duration = System.currentTimeMillis() - downTime

                            if (isDragging) {
                                // Handled via ACTION_MOVE
                            } else {
                                if (duration < 400 && Math.abs(deltaX) < 35 && Math.abs(deltaY) < 35) {
                                    // Single tap detected!
                                    val screenWidth = width
                                    if (upX < screenWidth * 0.3f) {
                                        this.evaluateJavascript("window.prevPage();", null)
                                        onPreviousPage()
                                    } else if (upX > screenWidth * 0.7f) {
                                        this.evaluateJavascript("window.nextPage();", null)
                                        onNextPage()
                                    } else {
                                        onToggleBars()
                                    }
                                } else if (Math.abs(deltaX) > 15 && Math.abs(deltaX) > Math.abs(deltaY)) {
                                    // Swipe gesture detected!
                                    if (deltaX > 0) {
                                        this.evaluateJavascript("window.prevPage();", null)
                                        onPreviousPage()
                                    } else {
                                        this.evaluateJavascript("window.nextPage();", null)
                                        onNextPage()
                                    }
                                }
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            isDragging = false
                        }
                    }
                    true // Consume all touch events to handle navigation ourselves cleanly
                }

                lastLoadedHtml = htmlContent
                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                webViewRef = this
                onWebViewCreated(this)
            }
        },
        update = { view ->
            if (htmlContent != lastLoadedHtml) {
                lastLoadedHtml = htmlContent
                Log.d("WEBVIEW_ENGINE", "WebView content changed, reloading HTML.")
                view.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } else if (targetOffset != null) {
                Log.d("WEBVIEW_ENGINE", "WebView scrolling to targetOffset: $targetOffset")
                view.evaluateJavascript("window.scrollToOffset($targetOffset);", null)
                onTargetOffsetHandled()
            }
        }
    )
}
