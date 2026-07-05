with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

import re

# Need to replace paths inside calculateSyncStats, executeSync, syncProgressOnly
# I will do text replacements:
# "disk:/SmartReader" -> syncFolder
# Wait, let's be careful. Let's see the functions.
