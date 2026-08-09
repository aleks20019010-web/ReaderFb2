import re

with open("app/src/test/java/com/nightread/app/ReaderLifecycleRestoreTest.kt", "r") as f:
    content = f.read()

# Add a helper function
helper = """
    private suspend fun waitForPages(pager: ReaderPager) {
        var attempts = 0
        while (pager.pages.value.isEmpty() && attempts < 50) {
            delay(100)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            attempts++
        }
    }
"""

content = content.replace("private fun findPageForOffset", helper + "\n    private fun findPageForOffset")

# Replace loops and delays
content = re.sub(r'var attempts.*?\n.*?while.*?\{.*?\n.*?\n.*?\n.*?\}', 'waitForPages(pager)', content)
content = re.sub(r'var attempts2 = 0.*?attempts2\+\+ \}', 'waitForPages(pager)', content)
content = content.replace("waitForPages(pager)", "waitForPages(pager)") # normalize
content = content.replace("val pages2 = pager2.pages.value", "waitForPages(pager2)\n        val pages2 = pager2.pages.value")
content = content.replace("val pages3 = pager3.pages.value", "waitForPages(pager3)\n        val pages3 = pager3.pages.value")
content = content.replace("val pages4 = pager4.pages.value", "waitForPages(pager4)\n        val pages4 = pager4.pages.value")
content = content.replace("val pages5 = pager5.pages.value", "waitForPages(pager5)\n        val pages5 = pager5.pages.value")

with open("app/src/test/java/com/nightread/app/ReaderLifecycleRestoreTest.kt", "w") as f:
    f.write(content)
