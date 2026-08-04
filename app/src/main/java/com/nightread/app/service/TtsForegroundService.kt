package com.nightread.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.nightread.app.R
import com.nightread.app.ui.BookReaderActivity
import java.util.Locale

class TtsForegroundService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "nightread_tts_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START = "com.nightread.app.action.TTS_START"
        const val ACTION_PAUSE = "com.nightread.app.action.TTS_PAUSE"
        const val ACTION_RESUME = "com.nightread.app.action.TTS_RESUME"
        const val ACTION_STOP = "com.nightread.app.action.TTS_STOP"
        const val ACTION_SET_SPEED = "com.nightread.app.action.TTS_SET_SPEED"
        const val ACTION_SET_PITCH = "com.nightread.app.action.TTS_SET_PITCH"
        const val ACTION_SET_VOICE = "com.nightread.app.action.TTS_SET_VOICE"

        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_BOOK_TITLE = "extra_book_title"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_PITCH = "extra_pitch"
        const val EXTRA_VOICE = "extra_voice"

        const val BROADCAST_TTS_STATUS = "com.nightread.app.broadcast.TTS_STATUS"
        const val EXTRA_IS_SPEAKING = "extra_is_speaking"
        const val EXTRA_UTTERANCE_DONE = "extra_utterance_done"
        const val EXTRA_START_IDX = "extra_start_idx"
        const val EXTRA_END_IDX = "extra_end_idx"
        const val EXTRA_PARAGRAPH_ID = "extra_paragraph_id"

        var isServiceRunning = false
            private set
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var currentText: String = ""
    private var currentParagraphId: String = ""
    private var currentParagraphIndex: Int = 0
    private var currentBookTitle: String = "NightRead"
    private var speechRate: Float = 1.0f
    private var speechPitch: Float = 1.0f
    private var selectedVoiceName: String? = null
    private var isSpeakingState: Boolean = false
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "NightReadTtsSession").apply {
            isActive = true
        }

        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
            }

            val langResult = tts?.setLanguage(Locale("ru"))
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(speechRate)
                acquireWakeLock()
            tts?.setPitch(speechPitch)
            applySelectedVoice()

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeakingState = true
                    updateNotification(true)
                    
                    var pIndex = -1
                    if (utteranceId != null && utteranceId != "UTTERANCE_CUSTOM_TEXT") {
                        pIndex = TtsDataProvider.paragraphs.indexOfFirst { it.id == utteranceId }
                        if (pIndex >= 0) {
                            currentParagraphIndex = pIndex
                            currentParagraphId = utteranceId
                            currentText = TtsDataProvider.paragraphs[pIndex].text
                        }
                    }
                    
                    sendStatusBroadcast(isPlaying = true, isDone = false, start = 0, end = 0, paragraphId = utteranceId ?: "")
                    
                    val prefs = getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
                    val continuous = prefs.getBoolean("tts_continuous", true)
                    if (continuous && pIndex >= 0) {
                        val nextIndex = pIndex + 1
                        if (nextIndex < TtsDataProvider.paragraphs.size) {
                            val nextP = TtsDataProvider.paragraphs[nextIndex]
                            val params = Bundle().apply {
                                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, nextP.id)
                            }
                            tts?.speak(nextP.text, TextToSpeech.QUEUE_ADD, params, nextP.id)
                        }
                    }
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    super.onRangeStart(utteranceId, start, end, frame)
                }

                override fun onDone(utteranceId: String?) {
                    val prefs = getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
                    val continuous = prefs.getBoolean("tts_continuous", true)
                    
                    var isLast = false
                    if (utteranceId != null && utteranceId != "UTTERANCE_CUSTOM_TEXT") {
                        val pIndex = TtsDataProvider.paragraphs.indexOfFirst { it.id == utteranceId }
                        if (pIndex == TtsDataProvider.paragraphs.size - 1) {
                            isLast = true
                        }
                    } else if (utteranceId == "UTTERANCE_CUSTOM_TEXT") {
                        isLast = true
                    }

                    if (!continuous || isLast) {
                        isSpeakingState = false
                        updateNotification(false)
                        releaseWakeLock()
                        sendStatusBroadcast(isPlaying = false, isDone = true, start = -1, end = -1, paragraphId = utteranceId ?: "")
                    }
                }

                override fun onError(utteranceId: String?) {
                    isSpeakingState = false
                    updateNotification(false)
                    releaseWakeLock()
                    sendStatusBroadcast(isPlaying = false, isDone = false, start = -1, end = -1, paragraphId = utteranceId ?: "")
                }
            })

            if (currentText.isNotEmpty() || (currentParagraphIndex >= 0 && TtsDataProvider.paragraphs.isNotEmpty())) {
                speakCurrentText()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val customText = intent.getStringExtra(EXTRA_TEXT) ?: ""
                val startIdx = intent.getIntExtra(EXTRA_START_IDX, 0)
                currentBookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: "NightRead"
                speechRate = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                speechPitch = intent.getFloatExtra(EXTRA_PITCH, 1.0f)
                selectedVoiceName = intent.getStringExtra(EXTRA_VOICE)

                if (customText.isNotEmpty()) {
                    currentText = customText
                    currentParagraphIndex = -1
                } else {
                    val targetId = "p_$startIdx"
                    val foundIndex = TtsDataProvider.paragraphs.indexOfFirst { it.id == targetId }
                    currentParagraphIndex = if (foundIndex != -1) foundIndex else 0
                }

                tts?.setSpeechRate(speechRate)
                acquireWakeLock()
                tts?.setPitch(speechPitch)
                applySelectedVoice()

                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            buildNotification(true),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification(true))
                    }
                } catch (e: Exception) {
                    Log.e("TtsForegroundService", "Error starting foreground in TTS service", e)
                }

                if (isTtsInitialized) {
                    speakCurrentText()
                }
            }
            ACTION_PAUSE -> {
                pauseTts()
            }
            ACTION_RESUME -> {
                resumeTts()
            }
            ACTION_STOP -> {
                stopTts()
                stopForeground(true)
                stopSelf()
            }
            ACTION_SET_SPEED -> {
                speechRate = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                tts?.setSpeechRate(speechRate)
                acquireWakeLock()
                if (isSpeakingState) {
                    speakCurrentText()
                }
            }
            ACTION_SET_PITCH -> {
                speechPitch = intent.getFloatExtra(EXTRA_PITCH, 1.0f)
                tts?.setPitch(speechPitch)
            }
            ACTION_SET_VOICE -> {
                selectedVoiceName = intent.getStringExtra(EXTRA_VOICE)
                applySelectedVoice()
                if (isSpeakingState || currentText.isNotEmpty()) {
                    speakCurrentText()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun applySelectedVoice() {
        if (!isTtsInitialized) return
        val voices = tts?.voices
        if (!voices.isNullOrEmpty()) {
            val voiceName = selectedVoiceName
            val targetVoice = if (!voiceName.isNullOrBlank()) {
                voices.find { it.name == voiceName }
            } else null

            val voiceToUse = targetVoice ?: voices.find { voice ->
                voice.locale.language.equals("ru", ignoreCase = true) &&
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            } ?: voices.firstOrNull()

            if (voiceToUse != null) {
                try {
                    tts?.voice = voiceToUse
                } catch (e: Exception) {
                    Log.e("TtsForegroundService", "Error setting TTS voice", e)
                }
            }
        }
    }

    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "NightRead:TtsWakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun speakCurrentText(flush: Boolean = true) {
        if (!isTtsInitialized) return
        
        if (currentParagraphIndex >= 0 && TtsDataProvider.paragraphs.isNotEmpty() && currentParagraphIndex in TtsDataProvider.paragraphs.indices) {
            val p = TtsDataProvider.paragraphs[currentParagraphIndex]
            currentText = p.text
            currentParagraphId = p.id
        } else if (currentText.isBlank()) {
            return
        }

        if (flush) {
            tts?.stop()
        }
        
        isSpeakingState = true
        acquireWakeLock()
        
        val uId = if (currentParagraphIndex >= 0) currentParagraphId else "UTTERANCE_CUSTOM_TEXT"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uId)
        }
        
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(currentText, queueMode, params, uId)
    }

    private fun pauseTts() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
        }
        isSpeakingState = false
        updateNotification(false)
        releaseWakeLock()
        sendStatusBroadcast(isPlaying = false, isDone = false)
    }

    private fun resumeTts() {
        if (currentText.isNotEmpty() || (currentParagraphIndex >= 0 && TtsDataProvider.paragraphs.isNotEmpty())) {
            speakCurrentText()
        }
    }

    private fun stopTts() {
        tts?.stop()
        isSpeakingState = false
        releaseWakeLock()
        sendStatusBroadcast(isPlaying = false, isDone = false)
    }

    private fun updateNotification(isPlaying: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, BookReaderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, TtsForegroundService::class.java).apply { action = ACTION_PAUSE }
        val pendingPause = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(this, TtsForegroundService::class.java).apply { action = ACTION_RESUME }
        val pendingResume = PendingIntent.getService(
            this, 2, resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TtsForegroundService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(
                R.drawable.ic_media_pause_custom, "Пауза", pendingPause
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                R.drawable.ic_media_play_custom, "Воспроизведение", pendingResume
            ).build()
        }

        val stopAction = NotificationCompat.Action.Builder(
            R.drawable.ic_media_stop_custom, "Стоп", pendingStop
        ).build()

        val displayText = if (currentText.length > 80) {
            currentText.substring(0, 80) + "…"
        } else {
            currentText.ifEmpty { "Озвучка текста" }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(currentBookTitle)
            .setContentText(displayText)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .setContentIntent(pendingOpenApp)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Озвучка книг (TTS)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновое воспроизведение TTS для чтения книг при заблокированном экране"
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendStatusBroadcast(isPlaying: Boolean, isDone: Boolean, start: Int = -1, end: Int = -1, paragraphId: String = "") {
        val intent = Intent(BROADCAST_TTS_STATUS).apply {
            putExtra(EXTRA_IS_SPEAKING, isPlaying)
            putExtra(EXTRA_UTTERANCE_DONE, isDone)
            putExtra(EXTRA_START_IDX, start)
            putExtra(EXTRA_END_IDX, end)
            putExtra(EXTRA_PARAGRAPH_ID, paragraphId)
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {

        isServiceRunning = false
        releaseWakeLock()
        tts?.stop()
        tts?.shutdown()
        tts = null
        mediaSession?.release()
        super.onDestroy()
    }
}
