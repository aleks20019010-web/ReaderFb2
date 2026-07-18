package com.nightread.app.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TTSManager(
    context: Context,
    private val onUtteranceStart: (String) -> Unit,
    private val onUtteranceDone: (String) -> Unit,
    private val onRangeStart: ((String, Int, Int) -> Unit)? = null
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = android.media.AudioManager.AUDIO_SESSION_ID_GENERATE

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val containsCyrillic = false // Default during init
            var targetLocale = Locale.getDefault()
            var result = tts?.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTSManager", "Language $targetLocale not supported, trying US")
                result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTSManager", "US locale also not supported")
                }
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d("TTSManager", "TTS Started: $utteranceId")
                    utteranceId?.let { onUtteranceStart(it) }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    Log.d("TTSManager", "TTS Range Start: $start, $end")
                    utteranceId?.let { onRangeStart?.invoke(it, start, end) }
                }

                override fun onDone(utteranceId: String?) {
                    Log.d("TTSManager", "TTS Done: $utteranceId")
                    utteranceId?.let { onUtteranceDone(it) }
                }

                override fun onError(utteranceId: String?) {
                    Log.e("TTSManager", "TTS Error: $utteranceId")
                }
            })
            isInitialized = true
        } else {
            Log.e("TTSManager", "TTS initialization failed: $status")
        }
    }

    fun isInitialized(): Boolean = isInitialized

    fun getAvailableVoices(): Set<android.speech.tts.Voice>? {
        return tts?.voices
    }

    fun getAvailableVoiceNames(): List<String> {
        return tts?.voices?.map { it.name }?.toList() ?: emptyList()
    }

    fun setVoiceByName(voiceName: String) {
        tts?.voices?.find { it.name == voiceName }?.let {
            tts?.voice = it
        }
    }

    fun setVoice(voice: android.speech.tts.Voice) {
        tts?.voice = voice
    }

    private fun initLoudnessEnhancer(context: Context) {
        if (com.nightread.app.data.SettingsManager.getTtsNormalizeVolume(context)) {
            try {
                if (currentAudioSessionId == android.media.AudioManager.AUDIO_SESSION_ID_GENERATE) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        currentAudioSessionId = audioManager.generateAudioSessionId()
                    }
                }
                if (loudnessEnhancer == null && currentAudioSessionId != android.media.AudioManager.AUDIO_SESSION_ID_GENERATE) {
                    loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(currentAudioSessionId).apply {
                        setTargetGain(800) // Boost gain by 8dB
                        enabled = true
                    }
                } else {
                    loudnessEnhancer?.enabled = true
                }
            } catch (e: Exception) {
                Log.e("TTSManager", "Failed to init LoudnessEnhancer", e)
            }
        } else {
            try {
                loudnessEnhancer?.enabled = false
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun speak(text: String, utteranceId: String, context: Context) {
        if (isInitialized) {
            initLoudnessEnhancer(context)
            val params = android.os.Bundle()
            if (com.nightread.app.data.SettingsManager.getTtsNormalizeVolume(context) && currentAudioSessionId != android.media.AudioManager.AUDIO_SESSION_ID_GENERATE) {
                params.putInt("audioSessionId", currentAudioSessionId)
            }
            
            // Language auto-detection for Cyrillic/Russian books
            val containsCyrillic = text.any { it in '\u0400'..'\u04FF' }
            val targetLocale = if (containsCyrillic) Locale("ru") else Locale.getDefault()
            try {
                tts?.setLanguage(targetLocale)
            } catch (e: Exception) {
                Log.e("TTSManager", "Failed to set language to $targetLocale", e)
            }

            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun pause() {
        tts?.stop()
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
    }

    fun shutdown() {
        tts?.stop()
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        tts?.shutdown()
    }
}
