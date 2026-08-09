import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

# Add bookmark toggle button and list button
topbar_actions = """
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
                                activity?.supportFragmentManager?.let { fm ->
                                    BookmarksListBottomSheet.newInstance(sha1).show(fm, "BookmarksList")
                                }
                            }) {
                                Icon(Icons.Filled.List, contentDescription = "Список закладок", tint = textColor)
                            }

                            IconButton(onClick = {
                                isSearchMode = true
                                isHideBars = true
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = textColor)
                            }
"""

old_actions = """                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    BookRagSearchBottomSheet.newInstance().show(fm, "BookRagSearch")
                                }
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = textColor)
                            }
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    val sheet = ChapterListBottomSheet.newInstance(sha1, mainText)
                                    sheet.setOnChapterClickListener { offset ->
                                        val targetPage = findPageForOffset(pageStartOffsets, offset)
                                        coroutineScope.launch {
                                            pagerState.scrollToPage(targetPage)
                                        }
                                    }
                                    sheet.show(fm, "ChapterList")
                                }
                            }) {
                                Icon(Icons.Filled.List, contentDescription = "Оглавление", tint = textColor)
                            }
                            IconButton(onClick = {
                                openTtsSettingsSheet(activity, mainText, bookTitle)
                            }) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = "TTS Озвучка", tint = textColor)
                            }
                            IconButton(onClick = {
                                activity?.supportFragmentManager?.let { fm ->
                                    BookmarksListBottomSheet.newInstance(sha1).show(fm, "BookmarksList")
                                }
                            }) {
                                Icon(Icons.Filled.Favorite, contentDescription = "Закладки", tint = textColor)
                            }"""

content = content.replace(old_actions, topbar_actions)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
