import sys

content = open('app/src/main/java/com/nightread/app/data/SettingsManager.kt').read()
cached_var = "    private var cachedPageAnimation: String? = null\n    private var cachedHyphenationEnabled: Boolean? = null"
content = content.replace("    private var cachedPageAnimation: String? = null", cached_var)

methods = """
    fun isHyphenationEnabled(context: Context): Boolean {
        if (cachedHyphenationEnabled == null) {
            cachedHyphenationEnabled = getPrefs(context).getBoolean(KEY_HYPHENATION_ENABLED, true)
        }
        return cachedHyphenationEnabled!!
    }

    fun setHyphenationEnabled(context: Context, enabled: Boolean) {
        if (cachedHyphenationEnabled == enabled) return
        cachedHyphenationEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_HYPHENATION_ENABLED, enabled).apply()
        notifyChanged()
    }
}"""
content = content.replace("}", methods, 1) # This might replace the wrong bracket.
open('app/src/main/java/com/nightread/app/data/SettingsManager.kt', 'w').write(content)
