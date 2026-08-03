package com.nightread.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.data.SettingsManager
import com.nightread.app.service.TtsForegroundService

class TtsControlBottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.DarkPurpleBottomSheetDialog

    private lateinit var tvTitle: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var seekBarSpeed: SeekBar
    private lateinit var tvSpeedLabel: TextView
    private lateinit var seekBarPitch: SeekBar
    private lateinit var tvPitchLabel: TextView

    private var isSpeaking = false

    private val ttsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TtsForegroundService.BROADCAST_TTS_STATUS) {
                isSpeaking = intent.getBooleanExtra(TtsForegroundService.EXTRA_IS_SPEAKING, false)
                updatePlayPauseIcon()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_tts_control, container, false)

        tvTitle = view.findViewById(R.id.tvTtsBookTitle)
        btnPlayPause = view.findViewById(R.id.btnTtsPlayPause)
        btnStop = view.findViewById(R.id.btnTtsStop)
        btnPrev = view.findViewById(R.id.btnTtsPrev)
        btnNext = view.findViewById(R.id.btnTtsNext)
        seekBarSpeed = view.findViewById(R.id.seekBarTtsSpeed)
        tvSpeedLabel = view.findViewById(R.id.tvTtsSpeedLabel)
        seekBarPitch = view.findViewById(R.id.seekBarTtsPitch)
        tvPitchLabel = view.findViewById(R.id.tvTtsPitchLabel)

        val readerActivity = activity as? BookReaderActivity
        val currentBookTitle = readerActivity?.getOpenedBookTitle() ?: "Книга"
        tvTitle.text = currentBookTitle

        isSpeaking = TtsForegroundService.isServiceRunning

        updatePlayPauseIcon()

        val context = requireContext()
        val currentSpeed = SettingsManager.getTtsSpeed(context)
        val currentPitch = SettingsManager.getTtsPitch(context)

        // Speed mapping: progress 0..25 => speed 0.5f..3.0f
        seekBarSpeed.max = 25
        val speedProgress = ((currentSpeed - 0.5f) / 0.1f).toInt().coerceIn(0, 25)
        seekBarSpeed.progress = speedProgress
        tvSpeedLabel.text = String.format("Скорость речи: %.1fx", currentSpeed)

        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSpeed = 0.5f + (progress * 0.1f)
                tvSpeedLabel.text = String.format("Скорость речи: %.1fx", newSpeed)
                if (fromUser) {
                    SettingsManager.setTtsSpeed(requireContext(), newSpeed)
                    sendTtsAction(TtsForegroundService.ACTION_SET_SPEED, newSpeed)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Pitch mapping: progress 0..15 => pitch 0.5f..2.0f
        seekBarPitch.max = 15
        val pitchProgress = ((currentPitch - 0.5f) / 0.1f).toInt().coerceIn(0, 15)
        seekBarPitch.progress = pitchProgress
        tvPitchLabel.text = String.format("Тон голоса: %.1fx", currentPitch)

        seekBarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newPitch = 0.5f + (progress * 0.1f)
                tvPitchLabel.text = String.format("Тон голоса: %.1fx", newPitch)
                if (fromUser) {
                    SettingsManager.setTtsPitch(requireContext(), newPitch)
                    sendTtsAction(TtsForegroundService.ACTION_SET_PITCH, pitch = newPitch)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnPlayPause.setOnClickListener {
            val act = activity as? BookReaderActivity ?: return@setOnClickListener
            if (isSpeaking) {
                act.pauseTts()
            } else {
                act.startOrResumeTts()
            }
        }

        btnStop.setOnClickListener {
            val act = activity as? BookReaderActivity
            act?.stopTts()
            dismiss()
        }

        btnPrev.setOnClickListener {
            val act = activity as? BookReaderActivity
            act?.readPreviousTtsChunk()
        }

        btnNext.setOnClickListener {
            val act = activity as? BookReaderActivity
            act?.readNextTtsChunk()
        }

        return view
    }

    private fun updatePlayPauseIcon() {
        if (isSpeaking) {
            btnPlayPause.setImageResource(R.drawable.ic_media_pause_custom)
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_media_play_custom)
        }
    }

    private fun sendTtsAction(action: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        val intent = Intent(requireContext(), TtsForegroundService::class.java).apply {
            this.action = action
            putExtra(TtsForegroundService.EXTRA_SPEED, speed)
            putExtra(TtsForegroundService.EXTRA_PITCH, pitch)
        }
        try {
            requireContext().startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(TtsForegroundService.BROADCAST_TTS_STATUS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(ttsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(ttsReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(ttsReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun newInstance(): TtsControlBottomSheet {
            return TtsControlBottomSheet()
        }
    }
}
