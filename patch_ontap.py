with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "r") as f:
    content = f.read()

replacement = """
                                                onTap = { offset ->
                                                    val screenWidth = size.width
                                                    if (offset.x < screenWidth * 0.25f) {
                                                        coroutineScope.launch {
                                                            if (pagerState.currentPage > 0) {
                                                                val start = System.currentTimeMillis()
                                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                                com.nightread.app.ui.customlayout.ReaderMetrics.logPageTurn(false, System.currentTimeMillis() - start)
                                                            }
                                                        }
                                                    } else if (offset.x > screenWidth * 0.75f) {
                                                        coroutineScope.launch {
                                                            if (pagerState.currentPage < pagerState.pageCount - 1) {
                                                                val start = System.currentTimeMillis()
                                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                                com.nightread.app.ui.customlayout.ReaderMetrics.logPageTurn(true, System.currentTimeMillis() - start)
                                                            }
                                                        }
                                                    } else {
                                                        isHideBars = !isHideBars
                                                        lastInteractionTime = System.currentTimeMillis()
                                                    }
                                                }
"""

import re
content = re.sub(r'onTap = \{ offset ->.*?isHideBars = !isHideBars.*?lastInteractionTime = System\.currentTimeMillis\(\).*?\}\s*\}', replacement.strip(), content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/ReaderComposeScreen.kt", "w") as f:
    f.write(content)
