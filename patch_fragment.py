import re

with open('app/src/main/java/com/nightread/app/ui/BookReaderFragment.kt', 'r') as f:
    code = f.read()

code = code.replace(
    '''            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updatePreferences()
                val cutoutPx = (activity as? BookReaderActivity)?.systemCutoutTop ?: 0
                updateTopMargin(cutoutPx)
                wv.postDelayed({
                    val savedProgress = viewModel.bookState.value?.currentProgressChar ?: 0
                    scrollToParagraph("p_$savedProgress")
                }, 300)
            }''',
    '''            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val savedProgress = viewModel.bookState.value?.currentProgressChar ?: 0
                wv.evaluateJavascript("savedParagraphId = 'p_$savedProgress';", null)
                updatePreferences()
                val cutoutPx = (activity as? BookReaderActivity)?.systemCutoutTop ?: 0
                updateTopMargin(cutoutPx)
            }'''
)

with open('app/src/main/java/com/nightread/app/ui/BookReaderFragment.kt', 'w') as f:
    f.write(code)

