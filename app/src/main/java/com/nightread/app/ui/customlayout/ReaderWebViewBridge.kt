package com.nightread.app.ui.customlayout

import android.webkit.JavascriptInterface

class ReaderWebViewBridge(
    private val onPositionChanged: (Int, Int, Int) -> Unit, // sourceOffset, pageIndex, totalPages
    private val onWordSelected: (String) -> Unit,
    private val onNoteClicked: (String) -> Unit
) {
    @JavascriptInterface
    fun reportPosition(sourceOffset: Int, pageIndex: Int, totalPages: Int) {
        android.util.Log.d("WEBVIEW_ENGINE", "Bridge reportPosition: offset=$sourceOffset, page=$pageIndex/$totalPages")
        onPositionChanged(sourceOffset, pageIndex, totalPages)
    }

    @JavascriptInterface
    fun onWordClick(word: String) {
        android.util.Log.d("WEBVIEW_ENGINE", "Bridge onWordClick: $word")
        onWordSelected(word)
    }

    @JavascriptInterface
    fun onNoteClick(noteId: String) {
        android.util.Log.d("WEBVIEW_ENGINE", "Bridge onNoteClick: $noteId")
        onNoteClicked(noteId)
    }
}
