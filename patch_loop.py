with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderLayoutEngine.kt", "r") as f:
    content = f.read()

content = content.replace(
    "currentGlobalOffset = lineEnd + if (isLastLine) 0 else 1",
    "currentGlobalOffset = lineEnd + 1"
)

with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderLayoutEngine.kt", "w") as f:
    f.write(content)
