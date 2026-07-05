with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

import re

# Insert val syncFolder = getSyncFolder(context) inside the withContext block of calculateSyncStats
content = re.sub(
    r'(suspend fun calculateSyncStats[\s\S]*?withContext\(Dispatchers\.IO\) \{)',
    r'\1\n        val syncFolder = getSyncFolder(context)',
    content
)

# Insert val syncFolder = getSyncFolder(context) inside executeSync
content = re.sub(
    r'(suspend fun executeSync[\s\S]*?withContext\(Dispatchers\.IO\) \{)',
    r'\1\n        val syncFolder = getSyncFolder(context)',
    content
)

# Insert val syncFolder = getSyncFolder(context) inside syncProgressOnly
content = re.sub(
    r'(suspend fun syncProgressOnly[\s\S]*?withContext\(Dispatchers\.IO\) \{)',
    r'\1\n        val syncFolder = getSyncFolder(context)',
    content
)

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
