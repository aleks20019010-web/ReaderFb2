import re

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("val trimmed = text.trim()\n        if (trimmed.isEmpty()) return \"Пожалуйста, выделите текст.\"\n        \n        if (llmInference != null) {", "val trimmed = text.trim()\n        if (trimmed.isEmpty()) return \"Пожалуйста, выделите текст.\"\n        \n        ensureModelInitialized(context)\n        if (llmInference != null) {")

with open(file_path, "w") as f:
    f.write(content)
