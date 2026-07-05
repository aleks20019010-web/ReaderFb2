with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

get_folders = """
    suspend fun getFolders(context: Context, path: String = "disk:/"): List<ResourceItem> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val token = AuthManager.getYandexToken(context) ?: return@withContext emptyList()
            val authHeader = "OAuth $token"
            try {
                val response = RetrofitClient.yandexDiskApi.getResource(authHeader, path, limit = 500)
                response.embedded?.items?.filter { it.type == "dir" } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
"""

content = content.replace("object YandexDiskManager {", "object YandexDiskManager {" + get_folders)

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(content)
