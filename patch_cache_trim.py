with open("app/src/main/java/com/nightread/app/ui/PaginationDiskCache.kt", "r") as f:
    content = f.read()

content = content.replace(
    "com.nightread.app.ui.customlayout.ReaderLayoutEngine.trimTrailingWhitespace(annotated.subSequence(b.startDisp, b.endDisp))",
    "with(com.nightread.app.ui.customlayout.ReaderLayoutEngine) { annotated.subSequence(b.startDisp, b.endDisp).trimTrailingWhitespace() }"
)

with open("app/src/main/java/com/nightread/app/ui/PaginationDiskCache.kt", "w") as f:
    f.write(content)
