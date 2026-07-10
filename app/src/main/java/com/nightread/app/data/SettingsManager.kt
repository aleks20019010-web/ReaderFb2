package com.nightread.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SettingsManager {
    private const val PREFS_NAME = "reader_prefs"
    
    const val KEY_THEME = "theme"
    const val KEY_PREVIOUS_THEME = "previous_theme"
    const val KEY_FONT_SIZE = "font_size"
    const val KEY_FONT_FAMILY = "font_family"
    const val KEY_FONT_WEIGHT = "font_weight"
    const val KEY_HYPHENATION_ENABLED = "hyphenation_enabled"
    const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    const val KEY_LINE_SPACING = "line_spacing"
    const val KEY_BRIGHTNESS = "brightness"
    const val KEY_PAGE_ANIMATION = "page_animation"

    const val KEY_AUTO_DISCOVERY = "auto_discovery"
    const val KEY_AUTO_THEME = "auto_theme"
    const val KEY_CLOUD_SYNC_URL = "cloud_sync_url"
    const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    const val KEY_FIRESTORE_SYNC_ENABLED = "firestore_sync_enabled"
    const val KEY_FIRESTORE_USER_ID = "firestore_user_id"
    const val KEY_AI_ENABLED = "ai_enabled"
    const val KEY_AI_MODEL_PATH = "ai_model_path"
    const val KEY_AI_MODEL_ID = "ai_model_id"
    const val KEY_AI_AUTO_LOAD = "ai_auto_load"

    private val _settingsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val settingsChanged: SharedFlow<Unit> = _settingsChanged.asSharedFlow()
    
    private var prefs: SharedPreferences? = null
    
    // Cache variables
    private var cachedTheme: String? = null
    private var cachedPrevTheme: String? = null
    private var cachedFontSize: Float? = null
    private var cachedFontFamily: String? = null
    private var cachedFontWeight: String? = null
    private var cachedLineSpacing: Float? = null
    private var cachedBrightness: Float? = null
    private var cachedPageAnimation: String? = null
    private var cachedHyphenationEnabled: Boolean? = null
    private var cachedOnboardingCompleted: Boolean? = null 
    private var cachedAutoDiscovery: Boolean? = null
    private var cachedAutoTheme: Boolean? = null
    private var cachedCloudSyncUrl: String? = null
    private var cachedCloudSyncEnabled: Boolean? = null
    private var cachedFirestoreSyncEnabled: Boolean? = null
    private var cachedFirestoreUserId: String? = null

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    fun notifyChanged() {
        _settingsChanged.tryEmit(Unit)
    }

    fun isAutoDiscoveryEnabled(context: Context): Boolean {
        if (cachedAutoDiscovery == null) {
            cachedAutoDiscovery = getPrefs(context).getBoolean(KEY_AUTO_DISCOVERY, true)
        }
        return cachedAutoDiscovery!!
    }

    fun setAutoDiscoveryEnabled(context: Context, enabled: Boolean) {
        if (cachedAutoDiscovery == enabled) return
        cachedAutoDiscovery = enabled
        getPrefs(context).edit().putBoolean(KEY_AUTO_DISCOVERY, enabled).apply()
        notifyChanged()
    }

    fun isAutoThemeEnabled(context: Context): Boolean {
        if (cachedAutoTheme == null) {
            cachedAutoTheme = getPrefs(context).getBoolean(KEY_AUTO_THEME, false)
        }
        return cachedAutoTheme!!
    }

    fun setAutoThemeEnabled(context: Context, enabled: Boolean) {
        if (cachedAutoTheme == enabled) return
        cachedAutoTheme = enabled
        getPrefs(context).edit().putBoolean(KEY_AUTO_THEME, enabled).apply()
        notifyChanged()
    }

    fun isCloudSyncEnabled(context: Context): Boolean {
        if (cachedCloudSyncEnabled == null) {
            cachedCloudSyncEnabled = getPrefs(context).getBoolean(KEY_CLOUD_SYNC_ENABLED, false)
        }
        return cachedCloudSyncEnabled!!
    }

    fun setCloudSyncEnabled(context: Context, enabled: Boolean) {
        if (cachedCloudSyncEnabled == enabled) return
        cachedCloudSyncEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun getCloudSyncUrl(context: Context): String {
        if (cachedCloudSyncUrl == null) {
            cachedCloudSyncUrl = getPrefs(context).getString(KEY_CLOUD_SYNC_URL, "") ?: ""
        }
        return cachedCloudSyncUrl!!
    }

    fun setCloudSyncUrl(context: Context, url: String) {
        if (cachedCloudSyncUrl == url) return
        cachedCloudSyncUrl = url
        getPrefs(context).edit().putString(KEY_CLOUD_SYNC_URL, url).apply()
        notifyChanged()
    }

    fun getTheme(context: Context): String {
        if (cachedTheme == null) {
            cachedTheme = getPrefs(context).getString(KEY_THEME, "sepia") ?: "sepia"
        }
        return cachedTheme!!
    }

    fun setTheme(context: Context, theme: String) {
        if (cachedAutoTheme != false) {
            cachedAutoTheme = false
            getPrefs(context).edit().putBoolean(KEY_AUTO_THEME, false).apply()
        }
        if (cachedTheme == theme) return
        cachedTheme = theme
        val prefs = getPrefs(context)
        if (theme != "dark") {
            cachedPrevTheme = theme
            prefs.edit().putString(KEY_PREVIOUS_THEME, theme).apply()
        }
        prefs.edit().putString(KEY_THEME, theme).apply()
        notifyChanged()
    }

    fun getPreviousTheme(context: Context): String {
        if (cachedPrevTheme == null) {
            cachedPrevTheme = getPrefs(context).getString(KEY_PREVIOUS_THEME, "sepia") ?: "sepia"
        }
        return cachedPrevTheme!!
    }

    fun getFontSize(context: Context): Float {
        if (cachedFontSize == null) {
            cachedFontSize = getPrefs(context).getFloat(KEY_FONT_SIZE, 18f)
        }
        return cachedFontSize!!
    }

    fun setFontSize(context: Context, size: Float) {
        if (cachedFontSize == size) return
        cachedFontSize = size
        getPrefs(context).edit().putFloat(KEY_FONT_SIZE, size).apply()
        notifyChanged()
    }

    fun getFontFamily(context: Context): String {
        if (cachedFontFamily == null) {
            cachedFontFamily = getPrefs(context).getString(KEY_FONT_FAMILY, "Roboto") ?: "Roboto"
        }
        return cachedFontFamily!!
    }

    fun setFontFamily(context: Context, family: String) {
        if (cachedFontFamily == family) return
        cachedFontFamily = family
        getPrefs(context).edit().putString(KEY_FONT_FAMILY, family).apply()
        notifyChanged()
    }

    fun getFontWeight(context: Context): String {
        if (cachedFontWeight == null) {
            cachedFontWeight = getPrefs(context).getString(KEY_FONT_WEIGHT, "400") ?: "400"
        }
        return cachedFontWeight!!
    }

    fun getFontWeightAsInt(context: Context): Int {
        val weightStr = getFontWeight(context)
        return weightStr.toIntOrNull() ?: when (weightStr) {
            "Normal" -> 400
            "Medium" -> 500
            "Bold" -> 700
            "ExtraBold" -> 800
            else -> 400
        }
    }

    fun setFontWeight(context: Context, weight: String) {
        if (cachedFontWeight == weight) return
        cachedFontWeight = weight
        getPrefs(context).edit().putString(KEY_FONT_WEIGHT, weight).apply()
        notifyChanged()
    }

    fun getLineSpacing(context: Context): Float {
        if (cachedLineSpacing == null) {
            cachedLineSpacing = getPrefs(context).getFloat(KEY_LINE_SPACING, 1.2f)
        }
        return cachedLineSpacing!!
    }

    fun setLineSpacing(context: Context, spacing: Float) {
        if (cachedLineSpacing == spacing) return
        cachedLineSpacing = spacing
        getPrefs(context).edit().putFloat(KEY_LINE_SPACING, spacing).apply()
        notifyChanged()
    }

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

    fun isFirestoreSyncEnabled(context: Context): Boolean {
        if (cachedFirestoreSyncEnabled == null) {
            cachedFirestoreSyncEnabled = getPrefs(context).getBoolean(KEY_FIRESTORE_SYNC_ENABLED, true)
        }
        return cachedFirestoreSyncEnabled!!
    }

    fun setFirestoreSyncEnabled(context: Context, enabled: Boolean) {
        if (cachedFirestoreSyncEnabled == enabled) return
        cachedFirestoreSyncEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_FIRESTORE_SYNC_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun getFirestoreUserId(context: Context): String {
        if (cachedFirestoreUserId == null) {
            cachedFirestoreUserId = getPrefs(context).getString(KEY_FIRESTORE_USER_ID, "") ?: ""
        }
        return cachedFirestoreUserId!!
    }

    fun setFirestoreUserId(context: Context, userId: String) {
        if (cachedFirestoreUserId == userId) return
        cachedFirestoreUserId = userId
        getPrefs(context).edit().putString(KEY_FIRESTORE_USER_ID, userId).apply()
        notifyChanged()
    }

    fun getPageAnimation(context: Context): String {
        if (cachedPageAnimation == null) {
            cachedPageAnimation = getPrefs(context).getString(KEY_PAGE_ANIMATION, "slide") ?: "slide"
        }
        return cachedPageAnimation!!
    }

    fun setPageAnimation(context: Context, mode: String) {
        if (cachedPageAnimation == mode) return
        cachedPageAnimation = mode
        getPrefs(context).edit().putString(KEY_PAGE_ANIMATION, mode).apply()
        notifyChanged()
    }

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

    fun isOnboardingCompleted(context: Context): Boolean {
        if (cachedOnboardingCompleted == null) {
            cachedOnboardingCompleted = getPrefs(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }
        return cachedOnboardingCompleted!!
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        if (cachedOnboardingCompleted == completed) return
        cachedOnboardingCompleted = completed
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        notifyChanged()
    }

    fun isAiEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AI_ENABLED, false)
    }

    fun setAiEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun isAiAutoLoad(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AI_AUTO_LOAD, true)
    }

    fun setAiAutoLoad(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AI_AUTO_LOAD, enabled).apply()
        notifyChanged()
    }

    fun getAiModelPath(context: Context): String? {
        return getPrefs(context).getString(KEY_AI_MODEL_PATH, null)
    }

    fun setAiModelPath(context: Context, path: String?) {
        getPrefs(context).edit().putString(KEY_AI_MODEL_PATH, path).apply()
    }

    fun getAiModelId(context: Context): String? {
        return getPrefs(context).getString(KEY_AI_MODEL_ID, null)
    }

    fun setAiModelId(context: Context, id: String?) {
        getPrefs(context).edit().putString(KEY_AI_MODEL_ID, id).apply()
    }
}
