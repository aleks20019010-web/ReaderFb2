import re

with open('app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt', 'r') as f:
    code = f.read()

code = code.replace(
    'private fun hideReaderSplash() {',
    'fun hideReaderSplash() {'
)

code = code.replace(
    '''fun onWebViewPagesCalculated(totalPages: Int) {
        hideReaderSplash()''',
    '''fun onWebViewPagesCalculated(totalPages: Int) {'''
)

with open('app/src/main/java/com/nightread/app/ui/BookReaderActivity.kt', 'w') as f:
    f.write(code)

