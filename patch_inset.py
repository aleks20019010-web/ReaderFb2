import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_inset = """    private fun getTopInset(): Int {
        val insets = WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets, window.decorView)
        val displayCutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        return maxOf(statusBarInsets.top, displayCutoutInsets.top)
    }"""

new_inset = """    private fun getTopInset(): Int {
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(window.decorView) ?: return 0
        val displayCutoutInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())
        val statusBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        return maxOf(statusBarInsets.top, displayCutoutInsets.top)
    }"""

content = content.replace(old_inset, new_inset)
open(file, 'w').write(content)
