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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.nightread.app.R
import com.nightread.app.service.TtsForegroundService

class TtsSettingsBottomSheet : BottomSheetDialogFragment() {

    interface TtsSettingsListener {
        fun onTtsStartRequested(speed: Float, pitch: Float, continuous: Boolean)
        fun onTtsPauseRequested()
        fun onTtsStopRequested()
        fun onTtsSpeedChanged(speed: Float)
        fun onTtsPitchChanged(pitch: Float)
    }

    private var listener: TtsSettingsListener? = null
    private var currentTextToSpeak: String = ""
    private var bookTitle: String = "NightRead"

    private lateinit var tvSpeedLabel: TextView
    private lateinit var seekBarSpeed: SeekBar
    private lateinit var tvPitchLabel: TextView
    private lateinit var seekBarPitch: SeekBar
    private lateinit var switchContinuous: SwitchMaterial
    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnStop: MaterialButton

    private var isCurrentlySpeaking = false

    private val ttsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TtsForegroundService.BROADCAST_TTS_STATUS) {
                isCurrentlySpeaking = intent.getBooleanExtra(TtsForegroundService.EXTRA_IS_SPEAKING, false)
                updatePlayPauseButtonUI()
            }
        }
    }

    companion object {
        fun newInstance(textToSpeak: String, bookTitle: String): TtsSettingsBottomSheet {
            val fragment = TtsSettingsBottomSheet()
            val args = Bundle().apply {
                putString("EXTRA_TEXT", textToSpeak)
                putString("EXTRA_BOOK_TITLE", bookTitle)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.Theme_Design_BottomSheetDialog)
        currentTextToSpeak = arguments?.getString("EXTRA_TEXT") ?: ""
        bookTitle = arguments?.getString("EXTRA_BOOK_TITLE") ?: "NightRead"
    }

    fun setTtsListener(listener: TtsSettingsListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_tts_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSpeedLabel = view.findViewById(R.id.tvSpeedLabel)
        seekBarSpeed = view.findViewById(R.id.seekBarSpeed)
        tvPitchLabel = view.findViewById(R.id.tvPitchLabel)
        seekBarPitch = view.findViewById(R.id.seekBarPitch)
        switchContinuous = view.findViewById(R.id.switchContinuous)
        btnPlayPause = view.findViewById(R.id.btnTtsPlayPause)
        btnStop = view.findViewById(R.id.btnTtsStop)

        view.findViewById<ImageButton>(R.id.btnCloseTts)?.setOnClickListener {
            dismiss()
        }

        val prefs = requireContext().getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
        val savedSpeed = prefs.getFloat("tts_speed", 1.0f)
        val savedPitch = prefs.getFloat("tts_pitch", 1.0f)
        val savedContinuous = prefs.getBoolean("tts_continuous", true)

        // Speed mapping: progress 0..25 => 0.5x..3.0x
        val speedProgress = ((savedSpeed - 0.5f) * 10).toInt().coerceIn(0, 25)
        seekBarSpeed.progress = speedProgress
        updateSpeedText(savedSpeed)

        // Pitch mapping: progress 0..10 => 0.5x..1.5x
        val pitchProgress = ((savedPitch - 0.5f) * 10).toInt().coerceIn(0, 10)
        seekBarPitch.progress = pitchProgress
        updatePitchText(savedPitch)

        switchContinuous.isChecked = savedContinuous

        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress * 0.1f)
                updateSpeedText(speed)
                prefs.edit().putFloat("tts_speed", speed).apply()
                listener?.onTtsSpeedChanged(speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress * 0.1f)
                updatePitchText(pitch)
                prefs.edit().putFloat("tts_pitch", pitch).apply()
                listener?.onTtsPitchChanged(pitch)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        switchContinuous.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("tts_continuous", isChecked).apply()
        }

        btnPlayPause.setOnClickListener {
            val speed = 0.5f + (seekBarSpeed.progress * 0.1f)
            val pitch = 0.5f + (seekBarPitch.progress * 0.1f)
            val continuous = switchContinuous.isChecked

            if (isCurrentlySpeaking) {
                listener?.onTtsPauseRequested()
                isCurrentlySpeaking = false
            } else {
                listener?.onTtsStartRequested(speed, pitch, continuous)
                isCurrentlySpeaking = true
            }
            updatePlayPauseButtonUI()
        }

        btnStop.setOnClickListener {
            listener?.onTtsStopRequested()
            isCurrentlySpeaking = false
            updatePlayPauseButtonUI()
        }

        isCurrentlySpeaking = TtsForegroundService.isServiceRunning
        updatePlayPauseButtonUI()
    }

    private fun updateSpeedText(speed: Float) {
        tvSpeedLabel.text = String.format("Скорость речи: %.1fx", speed)
    }

    private fun updatePitchText(pitch: Float) {
        tvPitchLabel.text = String.format("Высота голоса: %.1fx", pitch)
    }

    private fun updatePlayPauseButtonUI() {
        if (isCurrentlySpeaking) {
            btnPlayPause.text = "Пауза"
            btnPlayPause.setIconResource(R.drawable.ic_media_pause_custom)
        } else {
            btnPlayPause.text = "Озвучить"
            btnPlayPause.setIconResource(R.drawable.ic_media_play_custom)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(TtsForegroundService.BROADCAST_TTS_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(ttsStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(ttsStatusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().unregisterReceiver(ttsStatusReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
