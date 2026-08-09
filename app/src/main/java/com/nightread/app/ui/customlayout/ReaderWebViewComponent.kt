package com.nightread.app.ui.customlayout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
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
    onNoteClicked: (String) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

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
                setOnTouchListener { _, _ -> true } // Disable direct free-scroll; gestures handled by Compose pager/swipes
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }

                val bridge = ReaderWebViewBridge(
                    onPositionChanged = { offset, page, total ->
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

                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        update = { view ->
            val width = view.width
            val height = view.height
            Log.d("WEBVIEW_ENGINE", "WebView update: width=$width, height=$height, currentPage=$currentPage, targetOffset=$targetOffset")
            
            view.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            view.postDelayed({
                if (targetOffset > 0) {
                    view.evaluateJavascript("window.scrollToOffset($targetOffset);", null)
                } else {
                    view.evaluateJavascript("window.scrollToPage($currentPage);", null)
                }
            }, 100)
        }
    )
}
