import re

with open('app/src/main/java/com/nightread/app/data/SettingsManager.kt', 'r') as f:
    content = f.read()

pattern = r"    fun getBrightness\(context: Context\): Float \{\s+if \(cachedBrightness == null\) \{\s+cachedBrightness = getPrefs\(context\)\.getFloat\(KEY_BRIGHTNESS, -1f\)\s+\}\s+return cachedBrightness!!\s+\}\s+fun setBrightness\(context: Context, brightness: Float\) \{\s+if \(cachedBrightness == brightness\) return\s+cachedBrightness = brightness\s+getPrefs\(context\)\.edit\(\)\.putFloat\(KEY_BRIGHTNESS, brightness\)\.apply\(\)\s+\}\s+\}$"

content = re.sub(pattern, "}\n", content, flags=re.DOTALL)

with open('app/src/main/java/com/nightread/app/data/SettingsManager.kt', 'w') as f:
    f.write(content)
