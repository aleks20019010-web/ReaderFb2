package org.readium.r2.navigator.tts

class AndroidTtsPreferencesFilter {
    fun filter(preferences: AndroidTtsPreferences): AndroidTtsPreferences {
        return preferences.copy(
            rate = preferences.rate?.coerceIn(0.25f, 4.0f),
            pitch = preferences.pitch?.coerceIn(0.5f, 2.0f)
        )
    }
}
