import re

with open("app/src/main/java/com/nightread/app/ui/ReadingActivity.kt", "r") as f:
    content = f.read()

target = """    private fun updateBottomBar(position: Int) {
        val total = splitResult.pages.size
        if (isSplittingFinished) {"""

replacement = """    private fun updateBottomBar(position: Int) {
        val total = splitResult.pages.size
        
        val progressView = findViewById<com.nightread.app.ui.customlayout.ReadingProgressView>(R.id.readingProgressView)
        
        if (isSplittingFinished) {
            if (total > 1) {
                progressView?.progress = position.toFloat() / (total - 1)
            } else {
                progressView?.progress = 0f
            }
"""

if target in content:
    with open("app/src/main/java/com/nightread/app/ui/ReadingActivity.kt", "w") as f:
        f.write(content.replace(target, replacement))
    print("Patched updateBottomBar")
else:
    print("Target not found in ReadingActivity.kt")

