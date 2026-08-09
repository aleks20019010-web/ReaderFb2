import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

# Strip out ALL Search UI Overlay blocks!
pattern = r"            // Search UI Overlay.*?addStyle\(style, start, end\)"
replacement = r"        addStyle(style, start, end)"
content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
