package com.nightread.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.nightread.app.R
import com.nightread.app.data.SettingsManager
import com.nightread.app.service.TtsForegroundService

class TtsSettingsBottomSheet : BottomSheetDialogFragment() {

    interface TtsSettingsListener {
        fun onTtsStartRequested(speed: Float, pitch: Float, voiceName: String?, continuous: Boolean)
        fun onTtsPauseRequested()
        fun onTtsStopRequested()
        fun onTtsSpeedChanged(speed: Float)
        fun onTtsPitchChanged(pitch: Float)
        fun onTtsVoiceChanged(voiceName: String)
    }

    private var listener: TtsSettingsListener? = null
    private var currentTextToSpeak: String = ""
    private var bookTitle: String = "NightRead"

    private lateinit var tvVoiceLabel: TextView
    private lateinit var spinnerVoice: Spinner
    private lateinit var tvSpeedLabel: TextView
    private lateinit var seekBarSpeed: SeekBar
    private lateinit var tvPitchLabel: TextView
    private lateinit var seekBarPitch: SeekBar
    private lateinit var switchContinuous: SwitchMaterial
    private lateinit var btnPlayPause: MaterialButton
    private lateinit var btnStop: MaterialButton

    private var isCurrentlySpeaking = false
    private var tempTts: TextToSpeech? = null
    private var selectedVoiceName: String? = null
    private var voiceList: List<Voice> = emptyList()

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

        tvVoiceLabel = view.findViewById(R.id.tvVoiceLabel)
        spinnerVoice = view.findViewById(R.id.spinnerVoice)
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

        val context = requireContext()
        applyThemeColors(SettingsManager.getReadingTheme(context), view)

        val savedSpeed = SettingsManager.getTtsSpeed(context)
        val savedPitch = SettingsManager.getTtsPitch(context)
        selectedVoiceName = SettingsManager.getTtsVoice(context)

        val prefs = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)
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
                SettingsManager.setTtsSpeed(requireContext(), speed)
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
                SettingsManager.setTtsPitch(requireContext(), pitch)
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
                listener?.onTtsStartRequested(speed, pitch, selectedVoiceName, continuous)
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

        tempTts = TextToSpeech(requireContext().applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val availableVoices = tempTts?.voices?.filter { voice ->
                        !voice.isNetworkConnectionRequired &&
                        voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
                    }?.sortedBy { it.locale.displayName } ?: emptyList()

                    voiceList = if (availableVoices.isNotEmpty()) availableVoices else (tempTts?.voices?.toList() ?: emptyList())
                    setupVoiceSpinner()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun setupVoiceSpinner() {
        if (!isAdded) return
        val ctx = context ?: return

        val displayItems = mutableListOf<String>()
        var selectedIndex = 0

        if (voiceList.isEmpty()) {
            displayItems.add("Голос по умолчанию (Система)")
        } else {
            val ruLocale = java.util.Locale("ru")
            voiceList.forEachIndexed { index, voice ->
                val langName = voice.locale.getDisplayName(ruLocale).replaceFirstChar { it.uppercase() }
                val variant = voice.name.substringAfterLast("-").ifEmpty { voice.name.takeLast(6) }
                val label = "$langName ($variant)"
                displayItems.add(label)

                if (selectedVoiceName != null && voice.name == selectedVoiceName) {
                    selectedIndex = index
                }
            }
        }

        val adapter = ArrayAdapter(ctx, R.layout.spinner_item, displayItems)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerVoice.adapter = adapter
        if (selectedIndex < displayItems.size) {
            spinnerVoice.setSelection(selectedIndex)
            tvVoiceLabel.text = "Голос озвучки: ${displayItems[selectedIndex]}"
        }

        spinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (displayItems.isNotEmpty() && position in displayItems.indices) {
                    tvVoiceLabel.text = "Голос озвучки: ${displayItems[position]}"
                }
                if (voiceList.isNotEmpty() && position in voiceList.indices) {
                    val newVoice = voiceList[position].name
                    if (newVoice != selectedVoiceName) {
                        selectedVoiceName = newVoice
                        SettingsManager.setTtsVoice(requireContext(), newVoice)
                        listener?.onTtsVoiceChanged(newVoice)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateSpeedText(speed: Float) {
        tvSpeedLabel.text = String.format("Скорость речи: %.1fx", speed)
    }

    private fun updatePitchText(pitch: Float) {
        tvPitchLabel.text = String.format("Высота голоса: %.1fx", pitch)
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

        try {
            rootView.setBackgroundColor(android.graphics.Color.parseColor(bgHex))
        } catch (e: Exception) {}

        rootView.findViewById<TextView>(R.id.tvTtsSettingsTitle)?.setTextColor(android.graphics.Color.parseColor(textPrimaryHex))
        tvVoiceLabel.setTextColor(android.graphics.Color.parseColor(textPrimaryHex))
        tvSpeedLabel.setTextColor(android.graphics.Color.parseColor(textPrimaryHex))
        tvPitchLabel.setTextColor(android.graphics.Color.parseColor(textPrimaryHex))
        switchContinuous.setTextColor(android.graphics.Color.parseColor(textPrimaryHex))
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

    override fun onDestroyView() {
        try {
            tempTts?.stop()
            tempTts?.shutdown()
            tempTts = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroyView()
    }
}
