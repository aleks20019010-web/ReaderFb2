import re

with open('app/src/main/java/com/nightread/app/ui/BookReaderFragment.kt', 'r') as f:
    code = f.read()

# 1. Update calculatePages
code = code.replace(
    '''if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onPagesCalculated) {
                                AndroidInterface.onPagesCalculated(totalPages);
                            }''',
    '''if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onPagesCalculated) {
                                AndroidInterface.onPagesCalculated(totalPages);
                            }
                            restoreSavedParagraph();'''
)

# 2. Update restoreSavedParagraph
code = code.replace(
    '''function restoreSavedParagraph() {
                    if (savedParagraphId) {
                        scrollToParagraph(savedParagraphId);
                        savedParagraphId = null;
                    }
                }''',
    '''function restoreSavedParagraph() {
                    if (savedParagraphId) {
                        scrollToParagraph(savedParagraphId);
                        savedParagraphId = null;
                    }
                    if (typeof AndroidInterface !== 'undefined' && AndroidInterface.onReadyToDisplay) {
                        AndroidInterface.onReadyToDisplay();
                    }
                }'''
)

# 3. Update updateStyles
code = code.replace(
    '''clearTimeout(resizeTimer);
                    resizeTimer = setTimeout(function() {
                        calculatePages();
                        setTimeout(restoreSavedParagraph, 100);
                    }, 200);''',
    '''clearTimeout(resizeTimer);
                    resizeTimer = setTimeout(function() {
                        calculatePages();
                    }, 200);'''
)

# 4. Add onReadyToDisplay to JavascriptInterface
code = code.replace(
    '''@JavascriptInterface
            fun onPagesCalculated(total: Int) {''',
    '''@JavascriptInterface
            fun onReadyToDisplay() {
                activity?.runOnUiThread {
                    (activity as? BookReaderActivity)?.hideReaderSplash()
                }
            }
            
            @JavascriptInterface
            fun onPagesCalculated(total: Int) {'''
)

with open('app/src/main/java/com/nightread/app/ui/BookReaderFragment.kt', 'w') as f:
    f.write(code)

