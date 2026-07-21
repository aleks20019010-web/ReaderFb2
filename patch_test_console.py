import re

file_path = "app/src/main/java/com/nightread/app/ui/LocalAiFragment.kt"
with open(file_path, "r") as f:
    content = f.read()

content = content.replace("val response = com.nightread.app.data.LocalAiEngine.explainWord(context, prompt, null)", "val response = com.nightread.app.data.LocalAiEngine.customAiPrompt(context, prompt, null, \"\")")

with open(file_path, "w") as f:
    f.write(content)
