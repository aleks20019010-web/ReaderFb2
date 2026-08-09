with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderPager.kt", "r") as f:
    content = f.read()

replacement = """
        val finalPages = contiguousPages.mapIndexed { idx, page ->
            page.copy(pageIndex = idx)
        }
        
        _pages.value = finalPages
        ReaderMetrics.onVisiblePagesUpdated(finalPages.size)
    }
"""

import re
content = re.sub(r'val finalPages = contiguousPages\.mapIndexed \{ idx, page ->.*?page\.copy\(pageIndex = idx\)\s*\}\s*_pages\.value = finalPages\s*\}', replacement.strip(), content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderPager.kt", "w") as f:
    f.write(content)
