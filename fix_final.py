import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

# Strip out ALL Search UI Overlay blocks!
pattern = r"            // Search UI Overlay.*?            }\n"
content = re.sub(pattern, "", content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
