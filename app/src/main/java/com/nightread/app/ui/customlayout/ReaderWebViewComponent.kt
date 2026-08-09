package com.nightread.app.ui.customlayout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
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
                        Log.d("WEBVIEW_ENGINE", "WebView page finished loading. Scrolling to targetOffset: $targetOffset")
                        if (targetOffset > 0) {
                            view?.evaluateJavascript("window.scrollToOffset($targetOffset);", null)
                        }
                        view?.evaluateJavascript("window.ReaderBridge.reportPosition($targetOffset, 0, 1);", null)
                    }
                }

                loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        update = { view ->
            // Update HTML or configuration if needed
            val width = view.width
            val height = view.height
            Log.d("WEBVIEW_ENGINE", "WebView update: width=$width, height=$height, targetOffset=$targetOffset, fontSize=$fontSize")
            
            // Re-load data if content or styling changed significantly
            view.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            if (targetOffset > 0) {
                view.postDelayed({
                    view.evaluateJavascript("window.scrollToOffset($targetOffset);", null)
                }, 150)
            }
        }
    )
}
