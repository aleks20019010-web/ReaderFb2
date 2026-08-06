package com.nightread.app.data.prefs

import android.content.SharedPreferences

class TtsPreferences(private val getPrefs: () -> SharedPreferences?) {

    fun getTtsSpeed(): Float = getPrefs()?.getFloat("tts_speed", 1.0f) ?: 1.0f
    fun setTtsSpeed(value: Float) {
        getPrefs()?.edit()?.putFloat("tts_speed", value)?.apply()
    }

    fun getTtsPitch(): Float = getPrefs()?.getFloat("tts_pitch", 1.0f) ?: 1.0f
    fun setTtsPitch(value: Float) {
        getPrefs()?.edit()?.putFloat("tts_pitch", value)?.apply()
    }

    fun getTtsVoice(): String = getPrefs()?.getString("tts_voice_name", "") ?: ""
    fun setTtsVoice(value: String) {
        getPrefs()?.edit()?.putString("tts_voice_name", value)?.apply()
    }
}
