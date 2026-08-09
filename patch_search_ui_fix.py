import sys

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    lines = f.readlines()

# Remove the previously injected block (which is outside the function).
# The block starts with "        // Search UI Overlay"
start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if "        // Search UI Overlay" in line:
        start_idx = i
        break

if start_idx != -1:
    for i in range(start_idx, len(lines)):
        if "        }" in lines[i] and "        }" in lines[i-1] and i > start_idx + 30:
            end_idx = i + 1
            break
    if end_idx != -1:
        del lines[start_idx:end_idx]


# Now find the true end of the Box inside ReaderComposeScreen.
# The Box ends right before the `}` that closes the function.
fn_end = -1
for i in range(len(lines)):
    if "fun AnnotatedString.trimTrailingWhitespace" in lines[i]:
        fn_end = i - 1
        # walk back to find the first closing brace
        while fn_end > 0 and "}" not in lines[fn_end]:
            fn_end -= 1
        break

if fn_end != -1:
    # go back two more lines, which should be the closing braces of Box and BoxWithConstraints?
    insert_idx = fn_end - 2
else:
    print("Could not find function end")
    sys.exit(1)


search_ui = """
            // Search UI Overlay
            AnimatedVisibility(
                visible = isSearchMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, textColor.copy(alpha = 0.18f)),
                    shadowElevation = 8.dp
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { q ->
                                    searchQuery = q
                                    searchJob?.cancel()
                                    if (q.isNotEmpty()) {
                                        searchJob = coroutineScope.launch {
                                            delay(300) // debounce
                                            searchResults = searchEngine.search(q)
                                            currentSearchIndex = if (searchResults.isNotEmpty()) 0 else -1
                                            if (currentSearchIndex != -1) {
                                                pendingTargetOffset = searchResults[currentSearchIndex].sourceStartOffset
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
                                    cursorColor = textColor
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
                            Divider(color = textColor.copy(alpha = 0.1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
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
"""

# Let's verify where to insert
lines.insert(insert_idx, search_ui)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.writelines(lines)
print("Patched successfully")
