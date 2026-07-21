import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace('prefs.getBoolean("model_installed", false)', 'java.io.File(context.filesDir, "gemma.bin").exists()')
content = content.replace('prefs.edit().putBoolean("is_model_downloaded", true).apply()', '')

with open(file_path, "w") as f:
    f.write(content)
