import sys

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    lines = f.readlines()

start_idx = -1
end_idx = -1

for i, line in enumerate(lines):
    if "actions = {" in line:
        start_idx = i
    if start_idx != -1 and i > start_idx and "SettingsBottomSheet" in line:
        # Find the next closing brace for actions block
        for j in range(i, len(lines)):
            if "                        }" in lines[j]:
                end_idx = j
                break
        if end_idx != -1:
            break

if start_idx == -1 or end_idx == -1:
    print("Could not find bounds")
    sys.exit(1)

new_actions = """                        actions = {
                            val activity = context as? androidx.fragment.app.FragmentActivity
                            val currentOffset = if (readerPages.isNotEmpty() && pagerState.currentPage < readerPages.size) readerPages[pagerState.currentPage].startOffset else 0
                            val isBookmarked = bookmarks.any { it.charOffset == currentOffset }
                            
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    if (isBookmarked) {
                                        bookmarkRepo.deleteBookmarkAtOffset(sha1, currentOffset)
                                    } else {
                                        bookmarkRepo.insertBookmark(BookmarkEntity(bookSha1 = sha1, bookTitle = bookTitle, charOffset = currentOffset, pageIndex = pagerState.currentPage, snippet = "Закладка на позиции $currentOffset", timestamp = System.currentTimeMillis()))
                                    }
                                }
                            }) {
                                Icon(if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Закладка", tint = textColor)
                            }

                            IconButton(onClick = {
                                isSearchMode = true
                                isHideBars = true
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = textColor)
                            }

                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    val sheet = ChapterListBottomSheet.newInstance(sha1, mainText)
                                    sheet.setOnChapterClickListener { offset ->
                                        pendingTargetOffset = offset
                                    }
                                    sheet.show(fm, "ChapterList")
                                }
                            }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Оглавление", tint = textColor)
                            }
                            
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    BookmarksListBottomSheet.newInstance(sha1).show(fm, "BookmarksList")
                                }
                            }) {
                                Icon(Icons.Filled.List, contentDescription = "Список закладок", tint = textColor)
                            }
                            
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    SettingsBottomSheet().show(fm, "SettingsBottomSheet")
                                }
                            }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = textColor)
                            }
"""

lines[start_idx:end_idx] = [new_actions]

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.writelines(lines)
print("Patched successfully")
