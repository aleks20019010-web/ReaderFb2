package com.nightread.app.core.preferences

import android.content.Context
import android.content.SharedPreferences

class TtsPreferences(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SPEECH_RATE, value).apply()

    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_PITCH, value).apply()

    var voiceName: String?
        get() = prefs.getString(KEY_VOICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_VOICE_NAME, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ru") ?: "ru"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var isContinuousPlay: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS, value).apply()

    companion object {
        private const val PREFS_NAME = "tts_prefs"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_VOICE_NAME = "voice_name"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CONTINUOUS = "tts_continuous"
    }
}
