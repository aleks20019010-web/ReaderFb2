import re

with open('app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt', 'r') as f:
    code = f.read()

# Remove immediate hide
code = code.replace(
    'withContext(Dispatchers.Main) {\n                splashOverlay?.visibility = View.GONE',
    'withContext(Dispatchers.Main) {'
)

# Add hideReaderSplash to onWebViewPagesCalculated
code = code.replace(
    'fun onWebViewPagesCalculated(total: Int) {',
    'fun onWebViewPagesCalculated(total: Int) {\n        hideReaderSplash()'
)

with open('app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt', 'w') as f:
    f.write(code)

