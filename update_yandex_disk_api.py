with open("app/src/main/java/com/nightread/app/data/YandexDiskApi.kt", "r") as f:
    content = f.read()

content = content.replace(
    """    @Json(name = "sha256") val sha256: String? = null
)""",
    """    @Json(name = "sha256") val sha256: String? = null,
    @Json(name = "modified") val modified: String? = null
)"""
)

with open("app/src/main/java/com/nightread/app/data/YandexDiskApi.kt", "w") as f:
    f.write(content)
