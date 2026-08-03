package org.readium.r2.navigator.tts

class AndroidTtsSettingsResolver(
    val defaults: AndroidTtsDefaults = AndroidTtsDefaults()
) {
    fun settings(preferences: AndroidTtsPreferences): AndroidTtsSettings {
        return AndroidTtsSettings(
            rate = preferences.rate ?: defaults.rate,
            pitch = preferences.pitch ?: defaults.pitch,
            voice = preferences.voice ?: defaults.voice
        )
    }
}
