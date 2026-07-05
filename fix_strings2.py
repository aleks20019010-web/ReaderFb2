import re

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "r") as f:
    content = f.read()

new_str = '            val msg = "Книг на диске: ${stats.booksOnDisk}\\nКниг в библиотеке: ${stats.booksLocal}\\nБудет скачано: ${stats.toDownload.size} (новых)\\nБудет загружено: ${stats.toUpload.size} (новых)\\nПропущено (дубликаты): ${stats.duplicates}"'

content = re.sub(r'val msg = "Книг на диске:.*?\$\{stats\.duplicates\}"', new_str, content)

new_report = '                val report = "Скачано: ${stats.toDownload.size} книг\\nЗагружено: ${stats.toUpload.size} книг\\nПропущено (дубликаты): ${stats.duplicates} книг"'
content = re.sub(r'val report = "Скачано:.*?\$\{stats\.duplicates\} книг"', new_report, content)

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "w") as f:
    f.write(content)
print("Done")
