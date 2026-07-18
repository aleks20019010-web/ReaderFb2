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
    const val KEY_READING_THEME = "reading_theme"


    const val KEY_AUTO_DISCOVERY = "auto_discovery"
    const val KEY_AUTO_THEME = "auto_theme"
    const val KEY_AUTO_LIGHT_NIGHT = "auto_light_night"
    const val KEY_AMBER_FILTER_ENABLED = "amber_filter_enabled"
    const val KEY_AMBER_FILTER_INTENSITY = "amber_filter_intensity"
    const val KEY_EXTRA_DIM_ENABLED = "extra_dim_enabled"
    const val KEY_EXTRA_DIM_INTENSITY = "extra_dim_intensity"
    const val KEY_SLEEP_TIMER_ENABLED = "sleep_timer_enabled"
    const val KEY_SLEEP_TIMER_DURATION = "sleep_timer_duration"
    const val KEY_SHAKE_TO_EXTEND_ENABLED = "shake_to_extend_enabled"
    const val KEY_LAST_READ_BOOK_SHA1 = "last_read_book_sha1"
    const val KEY_IS_READING = "is_currently_reading"
    const val KEY_AI_ENABLED = "ai_enabled"
    const val KEY_AI_MODEL_PATH = "ai_model_path"
    const val KEY_AI_MODEL_ID = "ai_model_id"
    const val KEY_AI_AUTO_LOAD = "ai_auto_load"
    const val KEY_SHOW_ALL_FORMATS = "show_all_formats"
    const val KEY_AUTO_SYNC = "auto_sync"
    const val KEY_AUTO_SYNC_INTERVAL_DAYS = "auto_sync_interval_days"
    const val KEY_AUTO_SYNC_START_TIME = "auto_sync_start_time"
    const val KEY_AMBIENT_GLOW_ENABLED = "ambient_glow_enabled"
    const val KEY_AMBIENT_GLOW_INTENSITY = "ambient_glow_intensity"
    const val KEY_AMBIENT_GLOW_COLOR = "ambient_glow_color"
    const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
    const val KEY_LETTER_SPACING = "letter_spacing"
    const val KEY_PARAGRAPH_INDENT = "paragraph_indent"

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
    private var cachedReadingTheme: String? = null

    private var cachedOnboardingCompleted: Boolean? = null 
    private var cachedAutoDiscovery: Boolean? = null
    private var cachedAutoTheme: Boolean? = null
    private var cachedAutoLightNight: Boolean? = null
    private var cachedAmberFilterEnabled: Boolean? = null
    private var cachedAmberFilterIntensity: Int? = null
    private var cachedExtraDimEnabled: Boolean? = null
    private var cachedExtraDimIntensity: Int? = null
    private var cachedSleepTimerEnabled: Boolean? = null
    private var cachedSleepTimerDuration: Int? = null
    private var cachedShakeToExtendEnabled: Boolean? = null
    private var cachedShowAllFormats: Boolean? = null
    private var cachedAmbientGlowEnabled: Boolean? = null
    private var cachedAmbientGlowIntensity: Int? = null
    private var cachedAmbientGlowColor: String? = null
    private var cachedHapticFeedbackEnabled: Boolean? = null
    private var cachedLetterSpacing: Float? = null
    private var cachedParagraphIndent: Int? = null

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
            cachedAutoTheme = getPrefs(context).getBoolean(KEY_AUTO_THEME, true)
        }
        return cachedAutoTheme!!
    }

    fun setAutoThemeEnabled(context: Context, enabled: Boolean) {
        if (cachedAutoTheme == enabled) return
        cachedAutoTheme = enabled
        getPrefs(context).edit().putBoolean(KEY_AUTO_THEME, enabled).apply()
        notifyChanged()
    }

    fun isAutoLightNightEnabled(context: Context): Boolean {
        if (cachedAutoLightNight == null) {
            cachedAutoLightNight = getPrefs(context).getBoolean(KEY_AUTO_LIGHT_NIGHT, false)
        }
        return cachedAutoLightNight!!
    }

    fun setAutoLightNightEnabled(context: Context, enabled: Boolean) {
        if (cachedAutoLightNight == enabled) return
        cachedAutoLightNight = enabled
        getPrefs(context).edit().putBoolean(KEY_AUTO_LIGHT_NIGHT, enabled).apply()
        notifyChanged()
    }

    fun isAmberFilterEnabled(context: Context): Boolean {
        if (cachedAmberFilterEnabled == null) {
            cachedAmberFilterEnabled = getPrefs(context).getBoolean(KEY_AMBER_FILTER_ENABLED, false)
        }
        return cachedAmberFilterEnabled!!
    }

    fun setAmberFilterEnabled(context: Context, enabled: Boolean) {
        if (cachedAmberFilterEnabled == enabled) return
        cachedAmberFilterEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_AMBER_FILTER_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun getAmberFilterIntensity(context: Context): Int {
        if (cachedAmberFilterIntensity == null) {
            cachedAmberFilterIntensity = getPrefs(context).getInt(KEY_AMBER_FILTER_INTENSITY, 30)
        }
        return cachedAmberFilterIntensity!!
    }

    fun setAmberFilterIntensity(context: Context, intensity: Int) {
        if (cachedAmberFilterIntensity == intensity) return
        cachedAmberFilterIntensity = intensity
        getPrefs(context).edit().putInt(KEY_AMBER_FILTER_INTENSITY, intensity).apply()
        notifyChanged()
    }

    fun isSleepTimerEnabled(context: Context): Boolean {
        if (cachedSleepTimerEnabled == null) {
            cachedSleepTimerEnabled = getPrefs(context).getBoolean(KEY_SLEEP_TIMER_ENABLED, false)
        }
        return cachedSleepTimerEnabled!!
    }

    fun setSleepTimerEnabled(context: Context, enabled: Boolean) {
        if (cachedSleepTimerEnabled == enabled) return
        cachedSleepTimerEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_SLEEP_TIMER_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun getSleepTimerDuration(context: Context): Int {
        if (cachedSleepTimerDuration == null) {
            cachedSleepTimerDuration = getPrefs(context).getInt(KEY_SLEEP_TIMER_DURATION, 30)
        }
        return cachedSleepTimerDuration!!
    }

    fun setSleepTimerDuration(context: Context, duration: Int) {
        if (cachedSleepTimerDuration == duration) return
        cachedSleepTimerDuration = duration
        getPrefs(context).edit().putInt(KEY_SLEEP_TIMER_DURATION, duration).apply()
        notifyChanged()
    }

    fun isShakeToExtendEnabled(context: Context): Boolean {
        if (cachedShakeToExtendEnabled == null) {
            cachedShakeToExtendEnabled = getPrefs(context).getBoolean(KEY_SHAKE_TO_EXTEND_ENABLED, true)
        }
        return cachedShakeToExtendEnabled!!
    }

    fun setShakeToExtendEnabled(context: Context, enabled: Boolean) {
        if (cachedShakeToExtendEnabled == enabled) return
        cachedShakeToExtendEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_SHAKE_TO_EXTEND_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun isExtraDimEnabled(context: Context): Boolean {
        if (cachedExtraDimEnabled == null) {
            cachedExtraDimEnabled = getPrefs(context).getBoolean(KEY_EXTRA_DIM_ENABLED, false)
        }
        return cachedExtraDimEnabled!!
    }

    fun setExtraDimEnabled(context: Context, enabled: Boolean) {
        if (cachedExtraDimEnabled == enabled) return
        cachedExtraDimEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_EXTRA_DIM_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun getExtraDimIntensity(context: Context): Int {
        if (cachedExtraDimIntensity == null) {
            cachedExtraDimIntensity = getPrefs(context).getInt(KEY_EXTRA_DIM_INTENSITY, 40)
        }
        return cachedExtraDimIntensity!!
    }

    fun setExtraDimIntensity(context: Context, intensity: Int) {
        if (cachedExtraDimIntensity == intensity) return
        cachedExtraDimIntensity = intensity
        getPrefs(context).edit().putInt(KEY_EXTRA_DIM_INTENSITY, intensity).apply()
        notifyChanged()
    }

    fun getTheme(context: Context): String {
        if (cachedTheme == null) {
            cachedTheme = getPrefs(context).getString(KEY_THEME, "light") ?: "light"
        }
        return cachedTheme!!
    }

    fun setTheme(context: Context, theme: String) {
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
            cachedPrevTheme = getPrefs(context).getString(KEY_PREVIOUS_THEME, "light") ?: "light"
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

    fun getLastReadBookSha1(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_READ_BOOK_SHA1, null)
    }

    fun setLastReadBookSha1(context: Context, sha1: String?) {
        getPrefs(context).edit().putString(KEY_LAST_READ_BOOK_SHA1, sha1).apply()
    }

    fun getReadingTheme(context: Context): String {
        if (cachedReadingTheme == null) {
            cachedReadingTheme = getPrefs(context).getString(KEY_READING_THEME, "sepia") ?: "sepia"
        }
        return cachedReadingTheme!!
    }

    fun setReadingTheme(context: Context, theme: String) {
        if (cachedReadingTheme == theme) return
        cachedReadingTheme = theme
        getPrefs(context).edit().putString(KEY_READING_THEME, theme).apply()
        notifyChanged()
    }



    fun isShowAllFormatsEnabled(context: Context): Boolean {
        if (cachedShowAllFormats == null) {
            cachedShowAllFormats = getPrefs(context).getBoolean(KEY_SHOW_ALL_FORMATS, false)
        }
        return cachedShowAllFormats!!
    }

    fun setShowAllFormatsEnabled(context: Context, enabled: Boolean) {
        if (cachedShowAllFormats == enabled) return
        cachedShowAllFormats = enabled
        getPrefs(context).edit().putBoolean(KEY_SHOW_ALL_FORMATS, enabled).apply()
        notifyChanged()
    }

    fun isCurrentlyReading(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_READING, false)
    }

    fun setCurrentlyReading(context: Context, isReading: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_READING, isReading).apply()
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_SYNC, false)
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        notifyChanged()
    }

    fun getAutoSyncIntervalDays(context: Context): Int {
        return getPrefs(context).getInt(KEY_AUTO_SYNC_INTERVAL_DAYS, 1)
    }

    fun setAutoSyncIntervalDays(context: Context, days: Int) {
        getPrefs(context).edit().putInt(KEY_AUTO_SYNC_INTERVAL_DAYS, days).apply()
        notifyChanged()
    }

    fun getAutoSyncStartTime(context: Context): String {
        return getPrefs(context).getString(KEY_AUTO_SYNC_START_TIME, "03:00") ?: "03:00"
    }

    fun setAutoSyncStartTime(context: Context, time: String) {
        getPrefs(context).edit().putString(KEY_AUTO_SYNC_START_TIME, time).apply()
        notifyChanged()
    }

    private var ambientLux: Float = 100f

    fun getAmbientLux(): Float = ambientLux

    fun setAmbientLux(context: Context, lux: Float) {
        val oldBand = when {
            ambientLux < 10f -> 0
            ambientLux <= 150f -> 1
            else -> 2
        }
        val newBand = when {
            lux < 10f -> 0
            lux <= 150f -> 1
            else -> 2
        }
        ambientLux = lux
        if (oldBand != newBand) {
            notifyChanged()
        }
    }

    fun isAmbientGlowEnabled(context: Context): Boolean {
        if (cachedAmbientGlowEnabled == null) {
            cachedAmbientGlowEnabled = getPrefs(context).getBoolean(KEY_AMBIENT_GLOW_ENABLED, false)
        }
        return cachedAmbientGlowEnabled!!
    }

    fun setAmbientGlowEnabled(context: Context, enabled: Boolean) {
        if (cachedAmbientGlowEnabled == enabled) return
        cachedAmbientGlowEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_AMBIENT_GLOW_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun getAmbientGlowIntensity(context: Context): Int {
        if (cachedAmbientGlowIntensity == null) {
            cachedAmbientGlowIntensity = getPrefs(context).getInt(KEY_AMBIENT_GLOW_INTENSITY, 40)
        }
        return cachedAmbientGlowIntensity!!
    }

    fun setAmbientGlowIntensity(context: Context, intensity: Int) {
        if (cachedAmbientGlowIntensity == intensity) return
        cachedAmbientGlowIntensity = intensity
        getPrefs(context).edit().putInt(KEY_AMBIENT_GLOW_INTENSITY, intensity).apply()
        notifyChanged()
    }

    fun getAmbientGlowColor(context: Context): String {
        if (cachedAmbientGlowColor == null) {
            cachedAmbientGlowColor = getPrefs(context).getString(KEY_AMBIENT_GLOW_COLOR, "amber") ?: "amber"
        }
        return cachedAmbientGlowColor!!
    }

    fun setAmbientGlowColor(context: Context, color: String) {
        if (cachedAmbientGlowColor == color) return
        cachedAmbientGlowColor = color
        getPrefs(context).edit().putString(KEY_AMBIENT_GLOW_COLOR, color).apply()
        notifyChanged()
    }

    fun isHapticFeedbackEnabled(context: Context): Boolean {
        if (cachedHapticFeedbackEnabled == null) {
            cachedHapticFeedbackEnabled = getPrefs(context).getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
        }
        return cachedHapticFeedbackEnabled!!
    }

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        if (cachedHapticFeedbackEnabled == enabled) return
        cachedHapticFeedbackEnabled = enabled
        getPrefs(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun getLetterSpacing(context: Context): Float {
        if (cachedLetterSpacing == null) {
            cachedLetterSpacing = getPrefs(context).getFloat(KEY_LETTER_SPACING, 0.0f)
        }
        return cachedLetterSpacing!!
    }

    fun setLetterSpacing(context: Context, spacing: Float) {
        if (cachedLetterSpacing == spacing) return
        cachedLetterSpacing = spacing
        getPrefs(context).edit().putFloat(KEY_LETTER_SPACING, spacing).apply()
        notifyChanged()
    }

    fun getParagraphIndent(context: Context): Int {
        if (cachedParagraphIndent == null) {
            // Default 12dp indent is standard and beautiful for literature reading
            cachedParagraphIndent = getPrefs(context).getInt(KEY_PARAGRAPH_INDENT, 12)
        }
        return cachedParagraphIndent!!
    }

    fun setParagraphIndent(context: Context, indent: Int) {
        if (cachedParagraphIndent == indent) return
        cachedParagraphIndent = indent
        getPrefs(context).edit().putInt(KEY_PARAGRAPH_INDENT, indent).apply()
        notifyChanged()
    }
}
