package com.nightread.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nightread.app.R
import com.nightread.app.service.AudiobookPlaybackService
import com.nightread.app.data.SettingsManager

data class AudiobookChapter(val name: String, val startMs: Int, val durationMs: Int)

class AudioPlayerBottomSheet : BottomSheetDialogFragment() {

    private var filePath: String = ""
    private var title: String = ""
    private var author: String = ""
    private var sha1: String = ""

    private lateinit var tvTitle: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentPosition: TextView
    private lateinit var tvTotalDuration: TextView
    private lateinit var tvPlayerChapter: TextView
    private lateinit var btnSpeedToggle: MaterialButton
    private lateinit var btnSkipBackward: ImageButton
    private lateinit var btnSkipForward: ImageButton
    private lateinit var fabPlayPause: FloatingActionButton
    private lateinit var btnSleepTimer: ImageButton
    private lateinit var btnChapters: ImageButton

    private var isPlaying = false
    private var isUserTrackingSeekBar = false
    private var currentSpeed = 1.0f
    private var chaptersList: List<AudiobookChapter> = emptyList()

    private val audioStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudiobookPlaybackService.BROADCAST_AUDIOBOOK_STATUS) {
                isPlaying = intent.getBooleanExtra(AudiobookPlaybackService.EXTRA_IS_PLAYING, false)
                val pos = intent.getIntExtra(AudiobookPlaybackService.EXTRA_CURRENT_POSITION, 0)
                val intentDuration = intent.getIntExtra(AudiobookPlaybackService.EXTRA_DURATION, 0)
                val duration = if (intentDuration > 0) intentDuration else getAudioFileDuration(filePath)
                currentSpeed = intent.getFloatExtra(AudiobookPlaybackService.EXTRA_SPEED, 1.0f)
                val timerRemaining = intent.getIntExtra(AudiobookPlaybackService.EXTRA_SLEEP_TIMER_REMAINING, 0)

                updatePlayPauseUI()
                updateSleepTimerUI(timerRemaining)

                btnSpeedToggle.text = "${currentSpeed}x"

                tvCurrentPosition.text = formatTime(pos)
                if (duration > 0) {
                    seekBar.max = duration
                    tvTotalDuration.text = formatTime(duration)
                    if (chaptersList.isEmpty()) {
                        generateChapters(duration)
                    }
                }

                if (!isUserTrackingSeekBar && duration > 0) {
                    seekBar.progress = pos
                }
                
                updateChapterUI(pos)
            }
        }
    }

    companion object {
        fun newInstance(filePath: String, title: String, author: String, sha1: String = ""): AudioPlayerBottomSheet {
            val fragment = AudioPlayerBottomSheet()
            val args = Bundle().apply {
                putString("EXTRA_FILE_PATH", filePath)
                putString("EXTRA_TITLE", title)
                putString("EXTRA_AUTHOR", author)
                putString("EXTRA_SHA1", sha1)
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
        sha1 = arguments?.getString("EXTRA_SHA1") ?: ""
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
        tvPlayerChapter = view.findViewById(R.id.tvPlayerChapter)
        seekBar = view.findViewById(R.id.seekBarAudio)
        tvCurrentPosition = view.findViewById(R.id.tvCurrentPosition)
        tvTotalDuration = view.findViewById(R.id.tvTotalDuration)
        btnSpeedToggle = view.findViewById(R.id.btnSpeedToggle)
        btnSkipBackward = view.findViewById(R.id.btnSkipBackward)
        btnSkipForward = view.findViewById(R.id.btnSkipForward)
        fabPlayPause = view.findViewById(R.id.fabPlayerPlayPause)
        btnSleepTimer = view.findViewById(R.id.btnSleepTimer)
        btnChapters = view.findViewById(R.id.btnChapters)

        tvTitle.text = title
        tvAuthor.text = author

        val initialDuration = getAudioFileDuration(filePath)
        if (initialDuration > 0) {
            seekBar.max = initialDuration
            tvTotalDuration.text = formatTime(initialDuration)
            generateChapters(initialDuration)
        } else {
            tvTotalDuration.text = "--:--"
        }
        tvCurrentPosition.text = formatTime(0)

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
                    putExtra(AudiobookPlaybackService.EXTRA_SHA1, sha1)
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
                1.0f -> 1.5f
                1.5f -> 2.0f
                2.0f -> 0.5f
                0.5f -> 1.0f
                else -> 1.0f
            }
            btnSpeedToggle.text = "${currentSpeed}x"
            val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                action = AudiobookPlaybackService.ACTION_SPEED
                putExtra(AudiobookPlaybackService.EXTRA_SPEED, currentSpeed)
            }
            requireContext().startService(intent)
        }

        btnSleepTimer.setOnClickListener {
            showSleepTimerDialog()
        }

        btnChapters.setOnClickListener {
            showChaptersDialog()
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

        val themeKey = SettingsManager.getReadingTheme(requireContext())
        applyThemeColors(themeKey, view)

        // Auto-play on open if not already playing this file
        if (!AudiobookPlaybackService.isPlayingAudiobook || AudiobookPlaybackService.currentFilePath != filePath) {
            val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                action = AudiobookPlaybackService.ACTION_PLAY
                putExtra(AudiobookPlaybackService.EXTRA_FILE_PATH, filePath)
                putExtra(AudiobookPlaybackService.EXTRA_TITLE, title)
                putExtra(AudiobookPlaybackService.EXTRA_AUTHOR, author)
                putExtra(AudiobookPlaybackService.EXTRA_SHA1, sha1)
            }
            requireContext().startService(intent)
        }
    }

    private fun generateChapters(durationMs: Int) {
        val list = mutableListOf<AudiobookChapter>()
        val chapterDurationMs = when {
            durationMs < 5 * 60 * 1000 -> 1 * 60 * 1000
            durationMs < 30 * 60 * 1000 -> 5 * 60 * 1000
            else -> 10 * 60 * 1000
        }

        var currentStart = 0
        var index = 1
        while (currentStart < durationMs) {
            val nextStart = currentStart + chapterDurationMs
            val end = if (nextStart > durationMs) durationMs else nextStart
            list.add(AudiobookChapter("Глава $index", currentStart, end - currentStart))
            currentStart = nextStart
            index++
        }
        chaptersList = list
    }

    private fun updateChapterUI(currentMs: Int) {
        if (chaptersList.isEmpty()) {
            tvPlayerChapter.visibility = View.GONE
            return
        }
        val currentChapter = chaptersList.find { currentMs >= it.startMs && currentMs < (it.startMs + it.durationMs) }
        if (currentChapter != null) {
            tvPlayerChapter.text = currentChapter.name
            tvPlayerChapter.visibility = View.VISIBLE
        } else {
            tvPlayerChapter.visibility = View.GONE
        }
    }

    private fun showChaptersDialog() {
        if (chaptersList.isEmpty()) {
            Toast.makeText(requireContext(), "Главы недоступны", Toast.LENGTH_SHORT).show()
            return
        }

        val currentPos = seekBar.progress
        val items = chaptersList.map { chapter ->
            val isCurrent = currentPos >= chapter.startMs && currentPos < (chapter.startMs + chapter.durationMs)
            val indicator = if (isCurrent) "▶ " else ""
            "$indicator${chapter.name} (${formatTime(chapter.startMs)})"
        }.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Главы аудиокниги")
            .setItems(items) { _, which ->
                val selectedChapter = chaptersList[which]
                val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                    action = AudiobookPlaybackService.ACTION_SEEK
                    putExtra(AudiobookPlaybackService.EXTRA_SEEK_POSITION, selectedChapter.startMs)
                }
                requireContext().startService(intent)
                Toast.makeText(requireContext(), "Переход к ${selectedChapter.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showSleepTimerDialog() {
        val options = arrayOf(
            "Выключить таймер",
            "5 минут",
            "15 минут",
            "30 минут",
            "45 минут",
            "60 минут"
        )
        val minutes = intArrayOf(0, 5, 15, 30, 45, 60)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Таймер сна")
            .setItems(options) { _, which ->
                val selectedMin = minutes[which]
                val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
                    action = AudiobookPlaybackService.ACTION_SLEEP_TIMER
                    putExtra(AudiobookPlaybackService.EXTRA_TIMER_DURATION, selectedMin)
                }
                requireContext().startService(intent)
                if (selectedMin > 0) {
                    Toast.makeText(requireContext(), "Таймер установлен на $selectedMin минут", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Таймер сна выключен", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSleepTimerUI(timerRemaining: Int) {
        if (timerRemaining > 0) {
            btnSleepTimer.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent))
        } else {
            btnSleepTimer.imageTintList = ColorStateList.valueOf(0xA0A0C0.toInt() or 0xFF000000.toInt())
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

    private fun getAudioFileDuration(path: String): Int {
        if (path.isEmpty()) return 0
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(path)
            val timeStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            timeStr?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
    }

    private fun applyThemeColors(themeKey: String, rootView: View) {
        val isDark = themeKey == "dark" || themeKey == "amoled" || themeKey == "contrast"
        val bgHex = when (themeKey) {
            "light" -> "#FFFFFF"
            "sepia" -> "#F4ECD8"
            "dark" -> "#1A1A24"
            "amoled" -> "#000000"
            "contrast" -> "#0D0D0D"
            else -> "#1A1A24"
        }
        val textPrimaryHex = if (isDark) "#E0E0E0" else "#2A1A36"
        val textSecondaryHex = if (isDark) "#A0A0B0" else "#605070"
        val accentHex = if (isDark) "#9B59B6" else "#8e44ad"
        val accentColor = android.graphics.Color.parseColor(accentHex)
        val primaryColor = android.graphics.Color.parseColor(textPrimaryHex)
        val secondaryColor = android.graphics.Color.parseColor(textSecondaryHex)

        try {
            val shape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                val density = rootView.resources.displayMetrics.density
                val cornerRadiusValue = 24 * density
                cornerRadii = floatArrayOf(cornerRadiusValue, cornerRadiusValue, cornerRadiusValue, cornerRadiusValue, 0f, 0f, 0f, 0f)
                setColor(android.graphics.Color.parseColor(bgHex))
            }
            rootView.background = shape
        } catch (e: Exception) {}

        tvTitle.setTextColor(primaryColor)
        tvAuthor.setTextColor(secondaryColor)
        tvPlayerChapter.setTextColor(accentColor)
        tvCurrentPosition.setTextColor(secondaryColor)
        tvTotalDuration.setTextColor(secondaryColor)

        btnSpeedToggle.setTextColor(accentColor)
        btnSkipBackward.imageTintList = ColorStateList.valueOf(primaryColor)
        btnSkipForward.imageTintList = ColorStateList.valueOf(primaryColor)
        
        btnSleepTimer.imageTintList = ColorStateList.valueOf(secondaryColor)
        btnChapters.imageTintList = ColorStateList.valueOf(secondaryColor)

        // SeekBar styling
        val trackColor = android.graphics.Color.parseColor(if (isDark) "#4A3B5E" else "#D1C4E9")
        seekBar.progressTintList = ColorStateList.valueOf(accentColor)
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(trackColor)
        seekBar.thumbTintList = ColorStateList.valueOf(accentColor)

        // Floating Action Button styling
        fabPlayPause.backgroundTintList = ColorStateList.valueOf(accentColor)
        fabPlayPause.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(AudiobookPlaybackService.BROADCAST_AUDIOBOOK_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(audioStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(audioStatusReceiver, filter)
        }
        
        // Request immediate state from the service to populate the UI correctly on startup
        val intent = Intent(requireContext(), AudiobookPlaybackService::class.java).apply {
            action = AudiobookPlaybackService.ACTION_GET_STATUS
        }
        requireContext().startService(intent)
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
