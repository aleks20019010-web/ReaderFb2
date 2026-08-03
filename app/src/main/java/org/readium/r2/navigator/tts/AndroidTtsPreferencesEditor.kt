package org.readium.r2.navigator.tts

class AndroidTtsPreferencesEditor(
    var preferences: AndroidTtsPreferences
) {
    fun setRate(rate: Float?) {
        preferences = preferences.copy(rate = rate)
    }
    fun setPitch(pitch: Float?) {
        preferences = preferences.copy(pitch = pitch)
    }
    fun setVoice(voice: String?) {
        preferences = preferences.copy(voice = voice)
    }
}
