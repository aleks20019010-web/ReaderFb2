with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderLayoutEngine.kt", "r") as f:
    content = f.read()

import re
content = re.sub(
    r'chunkPages\.add\(\s*ReaderPage\(\s*pageIndex = localPageIndex\+\+,\s*text = slice,\s*startOffset = startSource,\s*endOffset = endSource\s*\)\s*\)',
    'chunkPages.add(\n                    ReaderPage(\n                        pageIndex = localPageIndex++,\n                        text = slice,\n                        startOffset = startSource,\n                        endOffset = endSource,\n                        startDisplayOffset = startAnnotated,\n                        endDisplayOffset = endAnnotated\n                    )\n                )',
    content
)

with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderLayoutEngine.kt", "w") as f:
    f.write(content)
