import re

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

content = content.replace(
"""        // Cache logic for main folder
        if (path == getSyncFolder(null.hashCode().toString())) { // just a placeholder, won't use context here since we pass authHeader
            // We need a better way to cache. Let's cache globally with a timestamp.
        }""",
""
)

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
