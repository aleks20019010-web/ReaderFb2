import sys

content = open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt').read()

old_padding = """        val paddingHorizontal = (26 * resources.displayMetrics.density).toInt()
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()

        val width = viewPager.width
        val height = viewPager.height"""

new_padding = """        val paddingHorizontal = (26 * resources.displayMetrics.density).toInt()
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()

        val width = viewPager.width
        val height = viewPager.height"""

content = content.replace(old_padding, new_padding)
open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w').write(content)
