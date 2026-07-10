import sys

content = open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt').read()

old_recalc_start = """        // Minimal paddings: 8dp
        val paddingHorizontal = (8 * resources.displayMetrics.density).toInt() * 2
        val paddingVertical = (8 * resources.displayMetrics.density).toInt() + getTopInset()
        
        val availableWidth = width - paddingHorizontal
        val availableHeight = height - paddingVertical"""

new_recalc_start = """        // Match padding in PageFragment: 16dp left + 16dp right = 32dp
        val paddingHorizontal = (32 * resources.displayMetrics.density).toInt()
        // Match padding in PageFragment: 8dp top + 8dp bottom = 16dp + topInset
        val paddingVertical = (16 * resources.displayMetrics.density).toInt() + getTopInset()
        
        val availableWidth = width - paddingHorizontal
        val availableHeight = height - paddingVertical"""

content = content.replace(old_recalc_start, new_recalc_start)

# We should also patch getTopInset to ensure it doesn't crash on older APIs if we called it improperly, but it looks fine.
open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w').write(content)
