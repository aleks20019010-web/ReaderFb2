import re

with open('app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt', 'r') as f:
    code = f.read()

code = code.replace(
    'fun onWebViewPagesCalculated(totalPages: Int) {',
    'fun onWebViewPagesCalculated(totalPages: Int) {\n        hideReaderSplash()'
)

with open('app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt', 'w') as f:
    f.write(code)

