import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

content = content.replace("override fun onCreate(savedInstanceState: Bundle?) {", 
"""override fun onCreate(savedInstanceState: Bundle?) {
        val savedBrightness = SettingsManager.getBrightness(this)
        if (savedBrightness > 0) {
            BrightnessHelper.setBrightness(this, savedBrightness)
        }
""")

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)
