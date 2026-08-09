with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderPager.kt", "r") as f:
    content = f.read()

content = content.replace(
    "val cached = com.nightread.app.ui.PaginationDiskCache.getChapterChunkPages(context, document.bookId, layoutKey, ch, ck)",
    "val cached = com.nightread.app.ui.PaginationDiskCache.getChapterChunkPages(context, document.bookId, layoutKey, ch, ck, chunk, config.fontSize)"
)

with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderPager.kt", "w") as f:
    f.write(content)
