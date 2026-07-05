import re

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "r") as f:
    content = f.read()

# I'll just use regex to replace everything between builder.setTitle and builder.setMessage
new_str = '            val msg = "Книг на диске: ${stats.booksOnDisk}\\nКниг в библиотеке: ${stats.booksLocal}\\nБудет скачано: ${stats.toDownload.size} (новых)\\nБудет загружено: ${stats.toUpload.size} (новых)\\nПропущено (дубликаты): ${stats.duplicates}"\n'

content = re.sub(r'val msg = .*?"Пропущено \(дубликаты\): \$\{stats\.duplicates\}"', new_str.strip(), content, flags=re.DOTALL)

new_report = '            val report = "Скачано: ${stats.toDownload.size} книг\\nЗагружено: ${stats.toUpload.size} книг\\nПропущено (дубликаты): ${stats.duplicates} книг"\n'
content = re.sub(r'val report = .*?"Пропущено \(дубликаты\): \$\{stats\.duplicates\} книг"', new_report.strip(), content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "w") as f:
    f.write(content)
print("Done")
