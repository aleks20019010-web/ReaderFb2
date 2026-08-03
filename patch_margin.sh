sed -i -e '/fun updatePreferences() {/i\    fun updateTopMargin(cutoutPx: Int) {\
        if (isAdded && webView != null) {\
            val density = resources.displayMetrics.density\
            val cutoutDp = (cutoutPx / density).toInt()\
            val topMarginDp = cutoutDp + 3\
            val js = "document.documentElement.style.setProperty('--top-margin', '"'"'${topMarginDp}px'"'"'); calculatePages();"\
            webView?.evaluateJavascript(js, null)\
        }\
    }' app/src/main/java/com/nightread/app/ui/BookReaderFragment.kt
