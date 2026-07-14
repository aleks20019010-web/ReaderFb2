import re

with open("app/src/main/java/com/nightread/app/ui/ReadingActivity.kt", "r") as f:
    content = f.read()

target = """        findViewById<android.view.View>(R.id.rootView)?.setBackgroundColor(parsedColor)"""

replacement = """        findViewById<android.view.View>(R.id.rootView)?.setBackgroundColor(parsedColor)
        
        val progressColor = when (themeName) {
            "light", "sepia", "sepia_contrast", "beige" -> android.graphics.Color.parseColor("#40000000") // Darker progress line for light themes
            else -> android.graphics.Color.parseColor("#40FFFFFF") // Lighter progress line for dark themes
        }
        findViewById<com.nightread.app.ui.customlayout.ReadingProgressView>(R.id.readingProgressView)?.setColorHint(progressColor)
"""

if target in content:
    with open("app/src/main/java/com/nightread/app/ui/ReadingActivity.kt", "w") as f:
        f.write(content.replace(target, replacement))
    print("Patched ReadingActivity color logic")
else:
    print("Target not found in ReadingActivity")

