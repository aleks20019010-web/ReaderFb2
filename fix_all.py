import re

# Fix SyncStats.kt
with open("app/src/main/java/com/nightread/app/data/SyncStats.kt", "r") as f:
    content = f.read()
content = content.replace("import com.nightread.app.data.database.BookEntity", "import com.nightread.app.data.BookEntity")
with open("app/src/main/java/com/nightread/app/data/SyncStats.kt", "w") as f:
    f.write(content)

# Fix YandexDiskManager.kt
with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

# Fix "val sha1" in calculateSyncStats
# The code is:
#                         if (sha1 != null) {
#                             manifest.books[cloudItem.name] = sha1
#                         }
content = content.replace("if (sha1 != null) {\n                            manifest.books[cloudItem.name] = sha1", 
                          "val finalSha1 = sha1\n                        if (finalSha1 != null) {\n                            manifest.books[cloudItem.name] = finalSha1")

# Fix "val sha1 = stats.manifest.books..." in executeSync? No, it's just the one above.

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)

# Fix SyncFragment.kt
with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "r") as f:
    content = f.read()

# Fix multi-line strings
content = content.replace('val msg = "Книг на диске: ${stats.booksOnDisk}\\n" +\n                      "Книг в библиотеке: ${stats.booksLocal}\\n" +\n                      "Будет скачано: ${stats.toDownload.size} (новых)\\n" +\n                      "Будет загружено: ${stats.toUpload.size} (новых)\\n" +\n                      "Пропущено (дубликаты): ${stats.duplicates}"',
                          'val msg = "Книг на диске: ${stats.booksOnDisk}\\nКниг в библиотеке: ${stats.booksLocal}\\nБудет скачано: ${stats.toDownload.size} (новых)\\nБудет загружено: ${stats.toUpload.size} (новых)\\nПропущено (дубликаты): ${stats.duplicates}"')

content = content.replace('val report = "Скачано: ${stats.toDownload.size} книг\\n" +\n                             "Загружено: ${stats.toUpload.size} книг\\n" +\n                             "Пропущено (дубликаты): ${stats.duplicates} книг"',
                          'val report = "Скачано: ${stats.toDownload.size} книг\\nЗагружено: ${stats.toUpload.size} книг\\nПропущено (дубликаты): ${stats.duplicates} книг"')

# Delete oldTriggerSync
idx = content.find("private fun oldTriggerSync()")
if idx != -1:
    content = content[:idx]

# Fix trailing braces if necessary
content = content.strip() + "\n}\n"

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "w") as f:
    f.write(content)

print("Fix applied.")
