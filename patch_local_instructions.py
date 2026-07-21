import re

file_path = "app/src/main/res/layout/fragment_local_ai.xml"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("Скачать и установить полную модель", "Скачать Llama 3.2 1B (~800 МБ)")
content = content.replace("полную ИИ-модель", "Llama 3.2 1B")
content = content.replace("полной ИИ-модели", "Llama 3.2 1B")

with open(file_path, "w") as f:
    f.write(content)
