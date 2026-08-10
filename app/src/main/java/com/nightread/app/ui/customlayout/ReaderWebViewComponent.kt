package com.nightread.app.ui.customlayout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.GestureDetector
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

                val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    private val SWIPE_THRESHOLD = 80
                    private val SWIPE_VELOCITY_THRESHOLD = 80

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
                                    this@apply.evaluateJavascript("window.prevPage();", null)
                                } else {
                                    this@apply.evaluateJavascript("window.nextPage();", null)
                                }
                                return true
                            }
                        }
                        return false
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        val screenWidth = width
                        if (e.x < screenWidth * 0.25f) {
                            this@apply.evaluateJavascript("window.prevPage();", null)
                        } else if (e.x > screenWidth * 0.75f) {
                            this@apply.evaluateJavascript("window.nextPage();", null)
                        } else {
                            onToggleBars()
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        onToggleBars()
                        return true
                    }

                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float
                    ): Boolean {
                        if (e1 == null) return false
                        val diffX = e2.x - e1.x
                        val diffY = e2.y - e1.y
                        if (Math.abs(diffY) > Math.abs(diffX)) {
                            onVerticalScroll(e1.x, -distanceY)
                            return true
                        }
                        return false
                    }
                })

                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    true // Consume all touch events to block native free horizontal scrolling
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
