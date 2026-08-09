import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

pattern = r"    if \(style != null\) \{.*?        addStyle\(style, start, end\)\n    \}"
replacement = r"""    if (style != null) {
        addStyle(style, start, end)
    }"""
content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
