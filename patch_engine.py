with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderLayoutEngine.kt", "r") as f:
    content = f.read()

replacement = """
    fun createPager(
        context: Context,
        document: ReaderDocument,
        config: ReaderConfiguration,
        viewport: ReaderViewport,
        textMeasurer: TextMeasurer,
        scope: CoroutineScope,
        initialTargetOffset: Int = 0
    ): ReaderPager {
        ReaderMetrics.startSession()
        val layoutKey = buildLayoutKey(document.bookId, config)
"""

import re
content = re.sub(r'fun createPager\(.*?initialTargetOffset: Int = 0\s*\):\s*ReaderPager\s*\{\s*val layoutKey = buildLayoutKey\(document\.bookId, config\)', replacement.strip(), content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/customlayout/ReaderLayoutEngine.kt", "w") as f:
    f.write(content)
