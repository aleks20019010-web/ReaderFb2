import sys

file = 'app/src/main/java/com/nightread/app/ui/ReadingActivity.kt'
content = open(file).read()

old_ts = 'textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.density'
new_ts = 'textSize = SettingsManager.getFontSize(this@ReadingActivity) * resources.displayMetrics.scaledDensity'
content = content.replace(old_ts, new_ts)

open(file, 'w').write(content)
