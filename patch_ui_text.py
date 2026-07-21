import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("Gemma 2B", "Llama 3.2 1B")
content = content.replace("Скачать и установить полную модель", "Скачать Llama 3.2 1B (~800 МБ)")

with open(file_path, "w") as f:
    f.write(content)

file_path = "app/src/main/java/com/nightread/app/data/LocalAiEngine.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("Gemma", "Llama 3.2 1B")

with open(file_path, "w") as f:
    f.write(content)
