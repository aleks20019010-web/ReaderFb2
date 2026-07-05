with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

import re

# Fix getFolders
content = re.sub(r'val token = AuthManager\.getYandexToken\(context\) \?\: return@withContext emptyList\(\)', r'val token = getToken(context) ?: return@withContext emptyList()', content)
content = re.sub(r'val response = RetrofitClient\.yandexDiskApi\.getResource\(authHeader, path, limit = 500\)', r'val response = api.getResource(authHeader, path, limit = 500)', content)

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
