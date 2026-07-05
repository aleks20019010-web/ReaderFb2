import re

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "r") as f:
    content = f.read()

new_content = content.replace("if (sha1 != null) {", "val finalSha1 = sha1\n                        if (finalSha1 != null) {")
new_content = new_content.replace("it.sha1 == sha1", "it.sha1 == finalSha1")
new_content = new_content.replace("sha1!!", "finalSha1")
new_content = new_content.replace("cloudSha1s.add(sha1)", "cloudSha1s.add(finalSha1)")
new_content = new_content.replace("localBooks.any { it.sha1 == finalSha1!! }", "localBooks.any { it.sha1 == finalSha1 }")

with open("app/src/main/java/com/nightread/app/data/YandexDiskManager.kt", "w") as f:
    f.write(new_content)

print("Patched YandexDiskManager.kt successfully!")
