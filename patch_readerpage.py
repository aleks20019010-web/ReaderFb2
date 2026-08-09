with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderPage.kt", "r") as f:
    content = f.read()

content = content.replace(
    "val endOffset: Int",
    "val endOffset: Int,\n    val startDisplayOffset: Int = 0,\n    val endDisplayOffset: Int = 0"
)

with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderPage.kt", "w") as f:
    f.write(content)
