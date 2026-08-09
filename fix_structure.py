import re

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

# Find the start of Top Panel (Glassmorphism)
top_panel_marker = "            // Top Panel (Glassmorphism)"
start_idx = content.find(top_panel_marker)

if start_idx != -1:
    # Find the end of ReaderComposeScreen (which is right before SettingsContent)
    settings_marker = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun SettingsContent("
    end_idx = content.find(settings_marker)
    
    if end_idx != -1:
        replacement = """            // Top Panel (Glassmorphism)
            AnimatedVisibility(
                visible = !isHideBars && !isSearchMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = glassBgColor,
                    border = glassBorder,
                    shadowElevation = 8.dp
                ) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        title = {
                            Column {
                                Text(
                                    text = bookTitle,
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = authorAndChapter,
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = textColor)
                            }
                        },
                        actions = {
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
                        }
                    )
                }
            }

            // Bottom Panel (Glassmorphism)
            AnimatedVisibility(
                visible = !isHideBars && !isSearchMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = glassBgColor,
                    border = glassBorder,
                    shadowElevation = 8.dp
                ) {
                    var isDraggingSlider by remember { mutableStateOf(false) }
                    var sliderPageValue by remember { mutableStateOf(0f) }

                    val totalPages = pagerState.pageCount
                    val maxPage = (totalPages - 1).coerceAtLeast(0)
                    val currentOffset = if (readerPages.isNotEmpty() && pagerState.currentPage < readerPages.size) readerPages[pagerState.currentPage].startOffset else 0
                    val totalChars = mainText.length.coerceAtLeast(1)
                    val currentPercent = (currentOffset.toFloat() / totalChars) * 100f

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Прогресс",
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format("%.1f%%", currentPercent),
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val currentSliderVal = if (isDraggingSlider) sliderPageValue else currentPercent
                        androidx.compose.material3.Slider(
                            value = currentSliderVal.coerceIn(0f, 100f),
                            onValueChange = { newValue ->
                                isDraggingSlider = true
                                sliderPageValue = newValue
                            },
                            onValueChangeFinished = {
                                val targetOffset = ((sliderPageValue / 100f) * totalChars).toInt().coerceIn(0, totalChars)
                                pendingTargetOffset = targetOffset
                                isDraggingSlider = false
                            },
                            valueRange = 0f..100f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = textColor,
                                activeTrackColor = textColor,
                                inactiveTrackColor = textColor.copy(alpha = 0.2f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(textColor, CircleShape)
                                )
                            },
                            track = { sliderState ->
                                androidx.compose.material3.SliderDefaults.Track(
                                    sliderState = sliderState,
                                    colors = androidx.compose.material3.SliderDefaults.colors(
                                        activeTrackColor = textColor,
                                        inactiveTrackColor = textColor.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.height(4.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // Search UI Overlay
            AnimatedVisibility(
                visible = isSearchMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = glassBgColor,
                    border = glassBorder,
                    shadowElevation = 8.dp
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    searchQuery = query
                                    if (query.isNotEmpty()) {
                                        coroutineScope.launch {
                                            searchResults = searchEngine.search(query)
                                            if (searchResults.isNotEmpty()) {
                                                currentSearchIndex = 0
                                                pendingTargetOffset = searchResults[0].sourceStartOffset
                                            } else {
                                                currentSearchIndex = -1
                                            }
                                        }
                                    } else {
                                        searchResults = emptyList()
                                        currentSearchIndex = -1
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Поиск...", color = textColor.copy(alpha = 0.5f)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    cursorColor = textColor,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                            
                            IconButton(onClick = {
                                isSearchMode = false
                                searchQuery = ""
                                searchResults = emptyList()
                                currentSearchIndex = -1
                                isHideBars = false
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = textColor)
                            }
                        }
                        
                        if (searchResults.isNotEmpty()) {
                            androidx.compose.material3.Divider(color = textColor.copy(alpha = 0.1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("${currentSearchIndex + 1} из ${searchResults.size}", color = textColor)
                                Row {
                                    IconButton(onClick = {
                                        if (currentSearchIndex > 0) {
                                            currentSearchIndex--
                                            pendingTargetOffset = searchResults[currentSearchIndex].sourceStartOffset
                                        }
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Предыдущий", tint = textColor)
                                    }
                                    IconButton(onClick = {
                                        if (currentSearchIndex < searchResults.size - 1) {
                                            currentSearchIndex++
                                            pendingTargetOffset = searchResults[currentSearchIndex].sourceStartOffset
                                        }
                                    }) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Следующий", tint = textColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

"""
        
        new_content = content[:start_idx] + replacement + content[end_idx:]
        with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
            f.write(new_content)
        print("Successfully updated ReaderComposeScreen!")
    else:
        print("Error: Could not find SettingsContent marker.")
else:
    print("Error: Could not find Top Panel marker.")
