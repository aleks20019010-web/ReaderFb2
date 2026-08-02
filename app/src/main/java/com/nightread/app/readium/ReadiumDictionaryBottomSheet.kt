package com.nightread.app.readium

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import java.net.URLEncoder

class ReadiumDictionaryBottomSheet : BottomSheetDialogFragment() {

    private var word: String = ""

    override fun getTheme(): Int = R.style.DarkPurpleBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_reader_lookup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvWord = view.findViewById<TextView>(R.id.tvLookupWord)
        val btnClose = view.findViewById<ImageView>(R.id.btnCloseLookup)
        val webView = view.findViewById<WebView>(R.id.wvLookup)
        val progressBar = view.findViewById<ProgressBar>(R.id.pbLookup)

        tvWord?.text = word
        btnClose?.setOnClickListener { dismiss() }

        if (webView != null) {
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar?.visibility = View.GONE
                }
            }

            val encodedWord = URLEncoder.encode(word, "UTF-8")
            val lookupUrl = if (containsCyrillic(word)) {
                "https://ru.wiktionary.org/wiki/$encodedWord"
            } else {
                "https://en.wiktionary.org/wiki/$encodedWord"
            }
            webView.loadUrl(lookupUrl)
        }
    }

    private fun containsCyrillic(text: String): Boolean {
        return text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC }
    }

    companion object {
        fun newInstance(wordToLookup: String): ReadiumDictionaryBottomSheet {
            return ReadiumDictionaryBottomSheet().apply {
                word = wordToLookup.trim()
            }
        }
    }
}
