with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

import re

new_functions = """    fun getSyncFolder(context: Context): String {
        val prefs = context.getSharedPreferences("yandex_sync", Context.MODE_PRIVATE)
        return prefs.getString("sync_folder", "disk:/Books") ?: "disk:/Books"
    }

    fun setSyncFolder(context: Context, folder: String) {
        val prefs = context.getSharedPreferences("yandex_sync", Context.MODE_PRIVATE)
        prefs.edit().putString("sync_folder", folder).apply()
    }
"""

# Insert new functions
content = content.replace("object YandexDiskManager {", "object YandexDiskManager {\n" + new_functions)

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
