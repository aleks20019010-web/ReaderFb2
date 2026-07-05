with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

import re

# Add syncFolder retrieval to calculateSyncStats
content = content.replace("    suspend fun calculateSyncStats(context: Context, onStatus: (String) -> Unit): SyncStats? {",
                          "    suspend fun calculateSyncStats(context: Context, onStatus: (String) -> Unit): SyncStats? {\n        val syncFolder = getSyncFolder(context)")

# Add syncFolder retrieval to executeSync
content = content.replace("    suspend fun executeSync(context: Context, stats: SyncStats, onProgress: (String, Int, Int) -> Unit): Boolean {",
                          "    suspend fun executeSync(context: Context, stats: SyncStats, onProgress: (String, Int, Int) -> Unit): Boolean {\n        val syncFolder = getSyncFolder(context)")

# Add syncFolder retrieval to syncProgressOnly
content = content.replace("    suspend fun syncProgressOnly(context: Context): Boolean {",
                          "    suspend fun syncProgressOnly(context: Context): Boolean {\n        val syncFolder = getSyncFolder(context)")

# Fix initDirectories calls
content = content.replace("initDirectories(authHeader)", "initDirectories(authHeader, syncFolder)")

# Replace paths
content = content.replace('"disk:/SmartReader/Books"', 'syncFolder')
content = content.replace('"disk:/SmartReader/Books/sync_manifest.json"', '"$syncFolder/sync_manifest.json"')
content = content.replace('"disk:/SmartReader/Books/${cloudItem.name}"', '"$syncFolder/${cloudItem.name}"')
content = content.replace('"disk:/SmartReader/Books/$filename"', '"$syncFolder/$filename"')

content = content.replace('"disk:/SmartReader/Progress"', '"$syncFolder/Progress"')
content = content.replace('"disk:/SmartReader/Progress/${progressItem.name}"', '"$syncFolder/Progress/${progressItem.name}"')
content = content.replace('"disk:/SmartReader/Progress/$cloudProgressName"', '"$syncFolder/Progress/$cloudProgressName"')

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
