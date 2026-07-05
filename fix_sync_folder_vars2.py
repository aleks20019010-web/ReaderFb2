with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

import re

# Insert val syncFolder = getSyncFolder(context) inside pushProgressToCloud
content = re.sub(
    r'(suspend fun pushProgressToCloud[\s\S]*?withContext\(Dispatchers\.IO\) \{)',
    r'\1\n        val syncFolder = getSyncFolder(context)',
    content
)

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
