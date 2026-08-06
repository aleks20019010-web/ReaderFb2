package com.nightread.app.features.audiobooks.domain

import android.content.Context
import android.content.Intent
import com.nightread.app.service.AudiobookPlaybackService

class PlayAudiobookUseCase(private val context: Context) {

    operator fun invoke(audioPath: String, title: String) {
        val intent = Intent(context, AudiobookPlaybackService::class.java).apply {
            action = "ACTION_PLAY"
            putExtra("EXTRA_PATH", audioPath)
            putExtra("EXTRA_TITLE", title)
        }
        context.startService(intent)
    }
}
