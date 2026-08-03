import re

with open('app/src/main/java/com/nightread/app/ui/ReaderViewModel.kt', 'r') as f:
    code = f.read()

code = code.replace(
    '''sharedPrefs.edit()
            .putInt("book_page_${book.sha1}", _currentPage.value)
            .putInt("book_char_offset_${book.sha1}", pIndex)
            .commit()''',
    '''sharedPrefs.edit()
            .putInt("book_page_${book.sha1}", _currentPage.value)
            .putInt("book_char_offset_${book.sha1}", pIndex)
            .commit()
            
        saveProgress()'''
)

with open('app/src/main/java/com/nightread/app/ui/ReaderViewModel.kt', 'w') as f:
    f.write(code)

