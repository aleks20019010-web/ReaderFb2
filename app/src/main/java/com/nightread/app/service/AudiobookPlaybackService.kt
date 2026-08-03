package com.nightread.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudiobookPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "nightread_audiobook_channel"
        const val NOTIFICATION_ID = 4002

        const val ACTION_PLAY = "com.nightread.app.action.AUDIOBOOK_PLAY"
        const val ACTION_PAUSE = "com.nightread.app.action.AUDIOBOOK_PAUSE"
        const val ACTION_SEEK = "com.nightread.app.action.AUDIOBOOK_SEEK"
        const val ACTION_SPEED = "com.nightread.app.action.AUDIOBOOK_SPEED"
        const val ACTION_STOP = "com.nightread.app.action.AUDIOBOOK_STOP"
        const val ACTION_SKIP_FORWARD = "com.nightread.app.action.AUDIOBOOK_SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.nightread.app.action.AUDIOBOOK_SKIP_BACKWARD"
        const val ACTION_SLEEP_TIMER = "com.nightread.app.action.AUDIOBOOK_SLEEP_TIMER"

        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_SHA1 = "extra_sha1"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_AUTHOR = "extra_author"
        const val EXTRA_SEEK_POSITION = "extra_seek_position"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_TIMER_DURATION = "extra_timer_duration"

        const val BROADCAST_AUDIOBOOK_STATUS = "com.nightread.app.broadcast.AUDIOBOOK_STATUS"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_SLEEP_TIMER_REMAINING = "extra_sleep_timer_remaining"

        var isPlayingAudiobook = false
            private set
        var currentFilePath: String? = null
            private set
    }

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    private var title: String = "Аудиокнига"
    private var author: String = "NightRead"
    private var speed: Float = 1.0f
    private var currentSha1: String? = null
    private var coverBitmap: Bitmap? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val handler = Handler(Looper.getMainLooper())
    private var lastSavedPos = 0
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    sendProgressBroadcast(
                        isPlaying = true,
                        position = pos,
                        duration = dur
                    )
                    
                    if (Math.abs(pos - lastSavedPos) >= 5000) {
                        lastSavedPos = pos
                        saveProgress(pos, dur)
                    }
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    private var sleepTimerRemainingSec = 0
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val sleepTimerCountdownRunnable = object : Runnable {
        override fun run() {
            if (sleepTimerRemainingSec > 0) {
                sleepTimerRemainingSec--
                if (sleepTimerRemainingSec == 0) {
                    pausePlayback()
                } else {
                    sleepTimerHandler.postDelayed(this, 1000)
                }
                mediaPlayer?.let { player ->
                    sendProgressBroadcast(
                        isPlaying = isPlayingAudiobook,
                        position = player.currentPosition,
                        duration = player.duration
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "NightReadAudiobookSession").apply {
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_PLAY -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                val newTitle = intent.getStringExtra(EXTRA_TITLE) ?: title
                val newAuthor = intent.getStringExtra(EXTRA_AUTHOR) ?: author
                val seekPos = intent.getIntExtra(EXTRA_SEEK_POSITION, -1)
                val sha1 = intent.getStringExtra(EXTRA_SHA1)

                title = newTitle
                author = newAuthor

                if (filePath != null && filePath != currentFilePath) {
                    currentFilePath = filePath
                    currentSha1 = sha1
                    serviceScope.launch {
                        val db = AppDatabase.getDatabase(this@AudiobookPlaybackService)
                        val book = if (!sha1.isNullOrEmpty()) {
                            db.bookDao().getBookBySha1(sha1)
                        } else {
                            db.bookDao().getAllBooksSync().find { it.filePath == filePath }
                        }
                        
                        val savedPos = book?.currentProgressChar ?: 0
                        currentSha1 = book?.sha1
                        
                        coverBitmap = loadCoverBitmap(book?.coverPath)
                        
                        withContext(Dispatchers.Main) {
                            val startPos = if (seekPos >= 0) seekPos else savedPos
                            initAndPlay(filePath, startPos)
                        }
                    }
                } else if (mediaPlayer != null) {
                    val targetPos = if (seekPos >= 0) seekPos else mediaPlayer?.currentPosition ?: 0
                    if (seekPos >= 0) mediaPlayer?.seekTo(seekPos)
                    mediaPlayer?.start()
                    isPlayingAudiobook = true
                    updateMetadata()
                    startForeground(NOTIFICATION_ID, buildNotification(true))
                    startProgressTracker()
                }
            }
            ACTION_PAUSE -> {
                pausePlayback()
            }
            ACTION_SEEK -> {
                val seekPos = intent.getIntExtra(EXTRA_SEEK_POSITION, 0)
                mediaPlayer?.seekTo(seekPos)
                val curPos = mediaPlayer?.currentPosition ?: 0
                val dur = mediaPlayer?.duration ?: 0
                saveProgress(curPos, dur)
                sendProgressBroadcast(
                    isPlaying = mediaPlayer?.isPlaying == true,
                    position = curPos,
                    duration = dur
                )
            }
            ACTION_SPEED -> {
                speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                setPlaybackSpeed(speed)
            }
            ACTION_SLEEP_TIMER -> {
                val durationMin = intent.getIntExtra(EXTRA_TIMER_DURATION, 0)
                startSleepTimer(durationMin)
            }
            ACTION_SKIP_FORWARD -> {
                mediaPlayer?.let { player ->
                    val target = (player.currentPosition + 30000).coerceAtMost(player.duration)
                    player.seekTo(target)
                    saveProgress(target, player.duration)
                }
            }
            ACTION_SKIP_BACKWARD -> {
                mediaPlayer?.let { player ->
                    val target = (player.currentPosition - 15000).coerceAtLeast(0)
                    player.seekTo(target)
                    saveProgress(target, player.duration)
                }
            }
            ACTION_STOP -> {
                stopPlayback()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun initAndPlay(filePath: String, seekPos: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            setOnPreparedListener { mp ->
                if (seekPos > 0) mp.seekTo(seekPos)
                setPlaybackSpeed(speed)
                mp.start()
                isPlayingAudiobook = true
                updateMetadata()
                startForeground(NOTIFICATION_ID, buildNotification(true))
                startProgressTracker()
            }
            setOnCompletionListener {
                isPlayingAudiobook = false
                stopProgressTracker()
                updateNotification(false)
                sendProgressBroadcast(isPlaying = false, position = duration, duration = duration)
            }
            prepareAsync()
        }
    }

    private fun setPlaybackSpeed(s: Float) {
        speed = s
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { player ->
                try {
                    val isPlaying = player.isPlaying
                    val params = player.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    player.playbackParams = params
                    if (!isPlaying) player.pause()
                } catch (e: Exception) {
                    // Ignore speed set exception
                }
            }
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        isPlayingAudiobook = false
        stopProgressTracker()
        updateNotification(false)
        val pos = mediaPlayer?.currentPosition ?: 0
        val dur = mediaPlayer?.duration ?: 0
        saveProgress(pos, dur)
        sendProgressBroadcast(
            isPlaying = false,
            position = pos,
            duration = dur
        )
    }

    private fun stopPlayback() {
        stopProgressTracker()
        mediaPlayer?.let { player ->
            val pos = player.currentPosition
            val dur = player.duration
            saveProgress(pos, dur)
            player.stop()
            player.release()
        }
        mediaPlayer = null
        isPlayingAudiobook = false
        currentFilePath = null
        sendProgressBroadcast(isPlaying = false, position = 0, duration = 0)
    }

    private fun startProgressTracker() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun stopProgressTracker() {
        handler.removeCallbacks(progressRunnable)
    }

    private fun updateNotification(isPlaying: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun updateMetadata() {
        val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, author)
        coverBitmap?.let {
            metadataBuilder.putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }
        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_AUDIOBOOKS", true)
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, AudiobookPlaybackService::class.java).apply { action = ACTION_PAUSE }
        val pendingPause = PendingIntent.getService(this, 10, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playIntent = Intent(this, AudiobookPlaybackService::class.java).apply { action = ACTION_PLAY }
        val pendingPlay = PendingIntent.getService(this, 11, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val skipBackIntent = Intent(this, AudiobookPlaybackService::class.java).apply { action = ACTION_SKIP_BACKWARD }
        val pendingSkipBack = PendingIntent.getService(this, 12, skipBackIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val skipFwdIntent = Intent(this, AudiobookPlaybackService::class.java).apply { action = ACTION_SKIP_FORWARD }
        val pendingSkipFwd = PendingIntent.getService(this, 13, skipFwdIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action.Builder(R.drawable.ic_media_pause_custom, "Пауза", pendingPause).build()
        } else {
            NotificationCompat.Action.Builder(R.drawable.ic_media_play_custom, "Воспроизведение", pendingPlay).build()
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(title)
            .setContentText(author)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .setContentIntent(pendingOpenApp)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_media_prev_custom, "-15с", pendingSkipBack).build())
            .addAction(playPauseAction)
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_media_next_custom, "+30с", pendingSkipFwd).build())

        coverBitmap?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Аудиокниги",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновое воспроизведение аудиокниг"
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendProgressBroadcast(isPlaying: Boolean, position: Int, duration: Int) {
        val intent = Intent(BROADCAST_AUDIOBOOK_STATUS).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_CURRENT_POSITION, position)
            putExtra(EXTRA_DURATION, duration)
            putExtra(EXTRA_SPEED, speed)
            putExtra(EXTRA_SLEEP_TIMER_REMAINING, sleepTimerRemainingSec)
        }
        sendBroadcast(intent)
    }

    private fun loadCoverBitmap(path: String?): Bitmap? {
        if (path.isNullOrEmpty()) return null
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveProgress(position: Int, duration: Int) {
        val sha1 = currentSha1 ?: return
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@AudiobookPlaybackService)
                val book = db.bookDao().getBookBySha1(sha1)
                if (book != null) {
                    val updatedBook = book.copy(
                        currentProgressChar = position,
                        totalCharacters = duration,
                        lastReadTime = System.currentTimeMillis()
                    )
                    db.bookDao().updateBook(updatedBook)
                }
            } catch (e: Exception) {
                // Ignore DB save errors
            }
        }
    }

    private fun startSleepTimer(minutes: Int) {
        sleepTimerHandler.removeCallbacks(sleepTimerCountdownRunnable)
        if (minutes <= 0) {
            sleepTimerRemainingSec = 0
        } else {
            sleepTimerRemainingSec = minutes * 60
            sleepTimerHandler.post(sleepTimerCountdownRunnable)
        }
        mediaPlayer?.let { player ->
            sendProgressBroadcast(
                isPlaying = isPlayingAudiobook,
                position = player.currentPosition,
                duration = player.duration
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopProgressTracker()
        sleepTimerHandler.removeCallbacks(sleepTimerCountdownRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        isPlayingAudiobook = false
        currentFilePath = null
        super.onDestroy()
    }
}
