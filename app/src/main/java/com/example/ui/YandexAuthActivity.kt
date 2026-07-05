package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.example.R
import com.example.data.YandexDiskManager

class YandexAuthActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple fullscreen dynamic layout for the WebView activity
        val layout = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        webView = WebView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
        
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                8
            ).apply {
                gravity = android.view.Gravity.TOP
            }
            isIndeterminate = true
            visibility = View.GONE
        }
        
        layout.addView(webView)
        layout.addView(progressBar)
        setContentView(layout)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                if (url != null) {
                    checkAndExtractToken(url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                if (url != null) {
                    checkAndExtractToken(url)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (url != null && checkAndExtractToken(url)) {
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

        val authUrl = "https://oauth.yandex.ru/authorize?client_id=${YandexDiskManager.CLIENT_ID}&response_type=token"
        webView.loadUrl(authUrl)
    }

    private fun checkAndExtractToken(url: String): Boolean {
        // Yandex default blank.html redirect or containing access_token in hash/parameters
        if (url.contains("access_token=")) {
            try {
                // Fragment usually contains token after '#'
                val fragment = if (url.contains("#")) url.substringAfter("#") else url.substringAfter("?")
                val params = fragment.split("&")
                val tokenParam = params.find { it.startsWith("access_token=") }
                val token = tokenParam?.substringAfter("=")
                
                if (!token.isNullOrBlank()) {
                    YandexDiskManager.saveToken(this, token)
                    setResult(RESULT_OK)
                    finish()
                    return true
                }
            } catch (e: Exception) {
                android.util.Log.e("YandexAuthActivity", "Error parsing access_token from url: $url", e)
            }
        }
        return false
    }

    override fun onDestroy() {
        webView.stopLoading()
        super.onDestroy()
    }
}
