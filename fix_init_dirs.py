with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

import re
old_init = """    private suspend fun initDirectories(authHeader: String) {
        val paths = listOf("disk:/SmartReader", "disk:/SmartReader/Books", "disk:/SmartReader/Progress")
        for (path in paths) {
            try {
                api.createDirectory(authHeader, path)
                Log.d(TAG, "Directory created or verified: $path")
            } catch (e: Exception) {
                // If it fails because folder already exists (typically 409), ignore
                Log.d(TAG, "Directory already exists or could not be created: $path. Error: ${e.message}")
            }
        }
    }"""

new_init = """    private suspend fun initDirectories(authHeader: String, syncFolder: String) {
        val progressFolder = "$syncFolder/Progress"
        val paths = listOf(syncFolder, progressFolder)
        for (path in paths) {
            try {
                api.createDirectory(authHeader, path)
                Log.d(TAG, "Directory created or verified: $path")
            } catch (e: Exception) {
                // If it fails because folder already exists (typically 409), ignore
                Log.d(TAG, "Directory already exists or could not be created: $path. Error: ${e.message}")
            }
        }
    }"""

content = content.replace(old_init, new_init)

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
