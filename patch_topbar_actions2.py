import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

# Replace everything from `val activity = context as? androidx.fragment.app.FragmentActivity` down to `openTtsSettingsSheet` ... wait, let's just use regex.

pattern = r"(actions = \{.*?val activity = context as\? androidx\.fragment\.app\.FragmentActivity)(.*?)(?=\}\n\s*\}\n\s*\}\n\s*\}\n\s*// Bottom Panel)"
replacement = r"""\1
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
                                Icon(Icons.Filled.List, contentDescription = "Оглавление", tint = textColor)
                            }
                            
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    BookmarksListBottomSheet.newInstance(sha1).show(fm, "BookmarksList")
                                }
                            }) {
                                Icon(Icons.Filled.Bookmark, contentDescription = "Список закладок", tint = textColor)
                            }
"""

new_content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(new_content)
