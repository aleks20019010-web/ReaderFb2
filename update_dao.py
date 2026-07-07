import re

with open('app/src/main/java/com/nightread/app/data/BookDao.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '@Query("UPDATE books SET currentProgressChar = :charOffset, currentPageIndex = :pageIndex, lastReadTime = :timestamp WHERE sha1 = :sha1")\n    suspend fun updateProgressAndPage(sha1: String, charOffset: Int, pageIndex: Int, timestamp: Long)',
    '@Query("UPDATE books SET currentProgressChar = :charOffset, currentPageIndex = :pageIndex, totalCharacters = :totalChars, lastReadTime = :timestamp WHERE sha1 = :sha1")\n    suspend fun updateProgressAndPage(sha1: String, charOffset: Int, pageIndex: Int, totalChars: Int, timestamp: Long)'
)

with open('app/src/main/java/com/nightread/app/data/BookDao.kt', 'w') as f:
    f.write(content)
