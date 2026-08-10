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
    targetOffset: Int = 0,
    onPositionChanged: (Int, Int, Int) -> Unit,
    onWordSelected: (String) -> Unit,
    onNoteClicked: (String) -> Unit,
    onNextPage: () -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onToggleBars: () -> Unit = {},
    onVerticalScroll: (Float, Float) -> Unit = { _, _ -> }
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastLoadedHtml by remember { mutableStateOf("") }
    var lastReportedOffset by remember { mutableStateOf(-1) }
    var lastReportedPage by remember { mutableStateOf(-1) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(AndroidColor.TRANSPARENT)
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
                        lastReportedOffset = offset
                        lastReportedPage = page
                        onPositionChanged(offset, page, total)
                    },
                    onWordSelected = onWordSelected,
                    onNoteClicked = onNoteClicked
                )
                addJavascriptInterface(bridge, "ReaderBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("WEBVIEW_ENGINE", "WebView page finished loading. Scrolling to currentPage: $currentPage, targetOffset: $targetOffset")
                        if (targetOffset > 0) {
                            view?.evaluateJavascript("window.scrollToOffset($targetOffset);", null)
                        } else {
                            view?.evaluateJavascript("window.scrollToPage($currentPage);", null)
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
                                if (duration < 300 && Math.abs(deltaX) < 25 && Math.abs(deltaY) < 25) {
                                    // Single tap detected!
                                    val screenWidth = width
                                    if (upX < screenWidth * 0.25f) {
                                        this.evaluateJavascript("window.prevPage();", null)
                                    } else if (upX > screenWidth * 0.75f) {
                                        this.evaluateJavascript("window.nextPage();", null)
                                    } else {
                                        onToggleBars()
                                    }
                                } else if (duration < 600 && Math.abs(deltaX) > Math.abs(deltaY) * 1.2f) {
                                    // Swipe gesture detected!
                                    if (Math.abs(deltaX) > 60) {
                                        if (deltaX > 0) {
                                            this.evaluateJavascript("window.prevPage();", null)
                                        } else {
                                            this.evaluateJavascript("window.nextPage();", null)
                                        }
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
            }
        },
        update = { view ->
            if (htmlContent != lastLoadedHtml) {
                lastLoadedHtml = htmlContent
                Log.d("WEBVIEW_ENGINE", "WebView content changed, reloading HTML.")
                view.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } else {
                val shouldScroll = (targetOffset > 0 && targetOffset != lastReportedOffset) ||
                                   (targetOffset <= 0 && currentPage != lastReportedPage)
                if (shouldScroll) {
                    Log.d("WEBVIEW_ENGINE", "WebView scrolling to page: $currentPage, offset: $targetOffset (lastReportedOffset=$lastReportedOffset, lastReportedPage=$lastReportedPage)")
                    if (targetOffset > 0) {
                        view.evaluateJavascript("window.scrollToOffset($targetOffset);", null)
                    } else {
                        view.evaluateJavascript("window.scrollToPage($currentPage);", null)
                    }
                } else {
                    Log.d("WEBVIEW_ENGINE", "WebView scroll skipped: offset=$targetOffset, page=$currentPage already matches last reported")
                }
            }
        }
    )
}
