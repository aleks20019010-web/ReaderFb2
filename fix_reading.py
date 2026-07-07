import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

old_save_progress = r"""    private fun saveProgress\(\) \{
        if \(sha1\.isEmpty\(\) \|\| splitResult == null\) return
        val currentIdx = viewPager\.currentItem
        if \(currentIdx < splitResult!!\.offsets\.size\) \{
            val charOffset = splitResult!!\.offsets\[currentIdx\]
            lifecycleScope\.launch\(Dispatchers\.IO\) \{
                AppDatabase\.getDatabase\(this@ReadingActivity\)
                    \.bookDao\(\)\.updateProgressAndPage\(sha1, charOffset, currentIdx, System\.currentTimeMillis\(\)\)
            \}
        \}
    \}"""

new_save_progress = r"""    private fun saveProgress() {
        if (sha1.isEmpty() || splitResult == null) return
        val currentIdx = viewPager.currentItem
        if (currentIdx < splitResult!!.offsets.size) {
            val charOffset = splitResult!!.offsets[currentIdx]
            val totalChars = if (bookContent.isNotEmpty()) bookContent.length else 0
            lifecycleScope.launch(Dispatchers.IO) {
                AppDatabase.getDatabase(this@ReadingActivity)
                    .bookDao().updateProgressAndPage(sha1, charOffset, currentIdx, totalChars, System.currentTimeMillis())
            }
        }
    }"""

content = re.sub(old_save_progress, new_save_progress, content, flags=re.DOTALL)

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)
