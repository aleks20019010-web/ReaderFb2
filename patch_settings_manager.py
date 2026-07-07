import re

with open('app/src/main/java/com/nightread/app/data/SettingsManager.kt', 'r') as f:
    content = f.read()

content = content.replace('const val KEY_LINE_SPACING = "line_spacing"', 
"""const val KEY_LINE_SPACING = "line_spacing"
    const val KEY_BRIGHTNESS = "brightness\"""")

content = content.replace('private var cachedLineSpacing: Float? = null',
"""private var cachedLineSpacing: Float? = null
    private var cachedBrightness: Float? = null""")

content = content + """
    fun getBrightness(context: Context): Float {
        if (cachedBrightness == null) {
            cachedBrightness = getPrefs(context).getFloat(KEY_BRIGHTNESS, -1f)
        }
        return cachedBrightness!!
    }

    fun setBrightness(context: Context, brightness: Float) {
        if (cachedBrightness == brightness) return
        cachedBrightness = brightness
        getPrefs(context).edit().putFloat(KEY_BRIGHTNESS, brightness).apply()
        // notifyChanged() // Usually brightness doesn't need a UI redraw, handled directly
    }
}
"""

# wait, we appended to the end, but the file ends with a brace.
# Let's fix that.
content = content.replace("}\n\n    fun getBrightness", "    fun getBrightness")
content = content[:content.rfind("}")] + """
    fun getBrightness(context: Context): Float {
        if (cachedBrightness == null) {
            cachedBrightness = getPrefs(context).getFloat(KEY_BRIGHTNESS, -1f)
        }
        return cachedBrightness!!
    }

    fun setBrightness(context: Context, brightness: Float) {
        if (cachedBrightness == brightness) return
        cachedBrightness = brightness
        getPrefs(context).edit().putFloat(KEY_BRIGHTNESS, brightness).apply()
    }
}
"""

with open('app/src/main/java/com/nightread/app/data/SettingsManager.kt', 'w') as f:
    f.write(content)
