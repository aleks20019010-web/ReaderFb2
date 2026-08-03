package org.readium.r2.navigator.tts

class AndroidTtsPreferencesSerializer {
    fun serialize(preferences: AndroidTtsPreferences): Map<String, Any?> {
        return mapOf(
            "rate" to preferences.rate,
            "pitch" to preferences.pitch,
            "voice" to preferences.voice
        )
    }

    fun deserialize(json: Map<String, Any?>): AndroidTtsPreferences {
        return AndroidTtsPreferences(
            rate = (json["rate"] as? Number)?.toFloat(),
            pitch = (json["pitch"] as? Number)?.toFloat(),
            voice = json["voice"] as? String
        )
    }
}
