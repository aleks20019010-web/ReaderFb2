package com.nightread.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nightread.app.R
import com.nightread.app.service.AudiobookPlaybackService

class AudioPlayerBottomSheet : BottomSheetDialogFragment() {

    private var filePath: String = ""
    private var title: String = ""
    private var author: String = ""

    private lateinit var tvTitle: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentPosition: TextView
    private lateinit var tvTotalDuration: TextView
    private lateinit var btnSpeedToggle: MaterialButton
    private lateinit var btnSkipBackward: ImageButton
    private lateinit var btnSkipForward: ImageButton
    private lateinit var fabPlayPause: FloatingActionButton

    private var isPlaying = false
    private var isUserTrackingSeekBar = false
    private var currentSpeed = 1.0f

    private val audioStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudiobookPlaybackService.BROADCAST_AUDIOBOOK_STATUS) {
                isPlaying = intent.getBooleanExtra(AudiobookPlaybackService.EXTRA_IS_PLAYING, false)
                val pos = intent.getIntExtra(AudiobookPlaybackService.EXTRA_CURRENT_POSITION, 0)
                val duration = intent.getIntExtra(AudiobookPlaybackService.EXTRA_DURATION, 0)

                updatePlayPauseUI()

                if (!isUserTrackingSeekBar && duration > 0) {
                    seekBar.max = duration
                    seekBar.progress = pos
                    tvCurrentPosition.text = formatTime(pos)
                    tvTotalDuration.text = formatTime(duration)
                }
            }
        }
    }

    companion object {
        fun newInstance(filePath: String, title: String, author: String): AudioPlayerBottomSheet {
            val fragment = AudioPlayerBottomSheet()
            val args = Bundle().apply {
                putString("EXTRA_FILE_PATH", filePath)
                putString("EXTRA_TITLE", title)
                putString("EXTRA_AUTHOR", author)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        filePath = arguments?.getString("EXTRA_FILE_PATH") ?: ""
        title = arguments?.getString("EXTRA_TITLE") ?: "Аудиокнига"
        author = arguments?.getString("EXTRA_AUTHOR") ?: "NightRead"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_audio_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTitle = view.findViewById(R.id.tvPlayerTitle)
        tvAuthor = view.findViewById(R.id.tvPlayerAuthor)
        seekBar = view.findViewById(R.id.seekBarAudio)
        tvCurrentPosition = view.findViewById(R.id.tvCurrentPosition)
        tvTotalDuration = view.findViewById(R.id.tvTotalDuration)
        btnSpeedToggle = view.findViewById(R.id.btnSpeedToggle)
        btnSkipBackward = view.findViewById(R.id.btnSkipBackward)
        btnSkipForward = view.findViewById(R.id.btnSkipForward)
        fabPlayPause = view.findViewById(R.id.fabPlayerPlayPause)

        tvTitle.text = title
        tvAuthor.text = author

        fabPlayPause.setOnClickListener {
            if (isPlaying) {
                val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                    action = AudiobookPlaybackService.ACTION_PAUSE
                }
                requireContext().startService(intent)
            } else {
                val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                    action = AudiobookPlaybackService.ACTION_PLAY
                    putExtra(AudiobookPlaybackService.EXTRA_FILE_PATH, filePath)
                    putExtra(AudiobookPlaybackService.EXTRA_TITLE, title)
                    putExtra(AudiobookPlaybackService.EXTRA_AUTHOR, author)
                }
                requireContext().startService(intent)
            }
        }

        btnSkipBackward.setOnClickListener {
            val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                action = AudiobookPlaybackService.ACTION_SKIP_BACKWARD
            }
            requireContext().startService(intent)
        }

        btnSkipForward.setOnClickListener {
            val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                action = AudiobookPlaybackService.ACTION_SKIP_FORWARD
            }
            requireContext().startService(intent)
        }

        btnSpeedToggle.setOnClickListener {
            currentSpeed = when (currentSpeed) {
                1.0f -> 1.25f
                1.25f -> 1.5f
                1.5f -> 2.0f
                2.0f -> 0.75f
                else -> 1.0f
            }
            btnSpeedToggle.text = String.format("%.2fx", currentSpeed)
            val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                action = AudiobookPlaybackService.ACTION_SPEED
                putExtra(AudiobookPlaybackService.EXTRA_SPEED, currentSpeed)
            }
            requireContext().startService(intent)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentPosition.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserTrackingSeekBar = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                isUserTrackingSeekBar = false
                val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                    action = AudiobookPlaybackService.ACTION_SEEK
                    putExtra(AudiobookPlaybackService.EXTRA_SEEK_POSITION, seekBar.progress)
                }
                requireContext().startService(intent)
            }
        })

        // Auto-play on open if not already playing this file
        if (!AudiobookPlaybackService.isPlayingAudiobook || AudiobookPlaybackService.currentFilePath != filePath) {
            val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                action = AudiobookPlaybackService.ACTION_PLAY
                putExtra(AudiobookPlaybackService.EXTRA_FILE_PATH, filePath)
                putExtra(AudiobookPlaybackService.EXTRA_TITLE, title)
                putExtra(AudiobookPlaybackService.EXTRA_AUTHOR, author)
            }
            requireContext().startService(intent)
        }
    }

    private fun updatePlayPauseUI() {
        if (isPlaying) {
            fabPlayPause.setImageResource(R.drawable.ic_media_pause_custom)
        } else {
            fabPlayPause.setImageResource(R.drawable.ic_media_play_custom)
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(AudiobookPlaybackService.BROADCAST_AUDIOBOOK_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(audioStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(audioStatusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(audioStatusReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
