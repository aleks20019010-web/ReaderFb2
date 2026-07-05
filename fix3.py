import re

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "r") as f:
    content = f.read()

# I will find the boundaries
start_idx = content.find('val msg =')
end_idx = content.find('builder.setMessage(msg)')

if start_idx != -1 and end_idx != -1:
    new_str = '            val msg = """Книг на диске: ${stats.booksOnDisk}\nКниг в библиотеке: ${stats.booksLocal}\nБудет скачано: ${stats.toDownload.size} (новых)\nБудет загружено: ${stats.toUpload.size} (новых)\nПропущено (дубликаты): ${stats.duplicates}"""\n            '
    content = content[:start_idx] + new_str + content[end_idx:]

start_idx2 = content.find('val report =')
end_idx2 = content.find('android.app.AlertDialog.Builder(context)')

if start_idx2 != -1 and end_idx2 != -1:
    new_report = '                val report = """Скачано: ${stats.toDownload.size} книг\nЗагружено: ${stats.toUpload.size} книг\nПропущено (дубликаты): ${stats.duplicates} книг"""\n                '
    content = content[:start_idx2] + new_report + content[end_idx2:]

with open("app/src/main/java/com/nightread/app/ui/SyncFragment.kt", "w") as f:
    f.write(content)
print("Done")
