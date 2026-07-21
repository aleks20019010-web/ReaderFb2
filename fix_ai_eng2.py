import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

# Replace file checks
content = content.replace('val modelFile = java.io.File(context.filesDir, "gemma.bin")', 'val modelFile = java.io.File(context.filesDir, "model.task").takeIf { it.exists() } ?: java.io.File(context.filesDir, "model.bin").takeIf { it.exists() } ?: java.io.File(context.filesDir, "gemma.bin")')

with open(file_path, "w") as f:
    f.write(content)
