package com.nightread.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.core.graphics.ColorUtils
import com.nightread.app.R
import com.nightread.app.data.SettingsManager

class TtsBottomSheet : DialogFragment() {

    private lateinit var tvCurrentSentence: TextView
    private lateinit var tvSpeedValue: TextView
    private lateinit var tvPitchValue: TextView
    private lateinit var sbSpeed: SeekBar
    private lateinit var sbPitch: SeekBar
    private lateinit var spinnerVoice: Spinner
    private lateinit var spinnerTimer: Spinner
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var dragHandle: View
    private lateinit var dividerTop: View

    private var activeTheme = "dark"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.SettingsDialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_tts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        dialog?.setCanceledOnTouchOutside(true)

        // Initialize views
        tvCurrentSentence = view.findViewById(R.id.tvCurrentSentence)
        tvSpeedValue = view.findViewById(R.id.tvSpeedValue)
        tvPitchValue = view.findViewById(R.id.tvPitchValue)
        sbSpeed = view.findViewById(R.id.sbSpeed)
        sbPitch = view.findViewById(R.id.sbPitch)
        spinnerVoice = view.findViewById(R.id.spinnerVoice)
        spinnerTimer = view.findViewById(R.id.spinnerTimer)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnStop = view.findViewById(R.id.btnStop)
        btnNext = view.findViewById(R.id.btnNext)
        dragHandle = view.findViewById(R.id.dragHandle)
        dividerTop = view.findViewById(R.id.dividerTop)

        // Close button
        view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            dismiss()
        }

        // Connect controls to Activity
        val activity = requireActivity() as BookReaderActivity

        // SeekBars
        sbSpeed.max = 25 // 0.5 to 3.0 (step 0.1)
        val currentSpeed = activity.getTtsSpeed()
        sbSpeed.progress = ((currentSpeed - 0.5f) * 10f).toInt().coerceIn(0, 25)
        tvSpeedValue.text = String.format("%.1fx", currentSpeed)

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + (progress * 0.1f)
                tvSpeedValue.text = String.format("%.1fx", rate)
                if (fromUser) {
                    activity.setTtsSpeed(rate)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbPitch.max = 15 // 0.5 to 2.0 (step 0.1)
        val currentPitch = activity.getTtsPitch()
        sbPitch.progress = ((currentPitch - 0.5f) * 10f).toInt().coerceIn(0, 15)
        tvPitchValue.text = String.format("%.1f", currentPitch)

        sbPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress * 0.1f)
                tvPitchValue.text = String.format("%.1f", pitch)
                if (fromUser) {
                    activity.setTtsPitch(pitch)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Spinners setup
        populateVoices()
        setupTimerSpinner()

        // Media Buttons
        btnPrev.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            activity.skipPrevTtsSentence()
        }

        btnNext.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            activity.skipNextTtsSentence()
        }

        btnPlayPause.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            activity.toggleTtsPlayPause()
        }

        btnStop.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            activity.stopTts()
        }

        // Apply theme colors
        activeTheme = SettingsManager.getReadingTheme(context)
        applyThemeColors(activeTheme, view)

        // Register BottomSheet with activity
        activity.registerTtsBottomSheet(this)

        // Sync initial state
        updatePlayPauseButtonIcon(activity.isTtsActiveAndPlaying())
        val curText = activity.getCurrentSpeakingSentence()
        if (curText.isNotEmpty()) {
            updateCurrentSentenceText(curText)
        }
    }

    override fun onDestroyView() {
        val activity = requireActivity() as? BookReaderActivity
        activity?.unregisterTtsBottomSheet()
        super.onDestroyView()
    }

    fun updatePlayPauseButtonIcon(isPlaying: Boolean) {
        if (!isAdded) return
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_media_pause_custom else R.drawable.ic_media_play_custom
        )
    }

    fun updateCurrentSentenceText(text: String) {
        if (!isAdded) return
        tvCurrentSentence.text = text
    }

    fun populateVoices() {
        if (!isAdded) return
        val activity = requireActivity() as BookReaderActivity
        val voiceOptions = activity.getAvailableTtsLanguages()
        val voiceNames = voiceOptions.map { it.first }

        val adapter = TtsSpinnerAdapter(requireContext(), voiceNames).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinnerVoice.adapter = adapter

        val currentLocale = activity.getTtsLanguage()
        val currentIdx = voiceOptions.indexOfFirst { it.second.language == currentLocale.language }.coerceAtLeast(0)
        spinnerVoice.setSelection(currentIdx)

        spinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLocale = voiceOptions[position].second
                if (selectedLocale.language != activity.getTtsLanguage().language) {
                    activity.setTtsLanguage(selectedLocale)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupTimerSpinner() {
        val timerOptions = listOf(
            Pair("Выкл", 0),
            Pair("5 мин", 5),
            Pair("10 мин", 10),
            Pair("15 мин", 15),
            Pair("30 мин", 30),
            Pair("45 мин", 45),
            Pair("60 мин", 60)
        )
        val timerNames = timerOptions.map { it.first }
        val adapter = TtsSpinnerAdapter(requireContext(), timerNames).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        spinnerTimer.adapter = adapter

        val activity = requireActivity() as BookReaderActivity
        val currentTimerMinutes = activity.getTtsTimerMinutes()
        val initialIdx = timerOptions.indexOfFirst { it.second == currentTimerMinutes }.coerceAtLeast(0)
        spinnerTimer.setSelection(initialIdx)

        spinnerTimer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val minutes = timerOptions[position].second
                activity.setTtsTimer(minutes)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyThemeColors(themeKey: String, rootView: View) {
        val cardBgHex: String
        val accentHex: String
        val textPrimaryHex: String
        val textSecondaryHex: String
        val itemBgHex: String

        when (themeKey) {
            "light", "beige" -> {
                cardBgHex = "#FAF6F0"
                accentHex = "#D35400"
                textPrimaryHex = "#2C3E50"
                textSecondaryHex = "#7F8C8D"
                itemBgHex = "#F0EAE1"
            }
            "sepia", "sepia_contrast" -> {
                cardBgHex = "#F4ECD8"
                accentHex = "#8E44AD"
                textPrimaryHex = "#5B3A29"
                textSecondaryHex = "#8F7365"
                itemBgHex = "#EADEC9"
            }
            "contrast" -> {
                cardBgHex = "#000000"
                accentHex = "#FFFFFF"
                textPrimaryHex = "#FFFFFF"
                textSecondaryHex = "#AAAAAA"
                itemBgHex = "#1C1C1C"
            }
            else -> { // dark
                cardBgHex = "#1A0D2A"
                accentHex = "#9B59B6"
                textPrimaryHex = "#E8D8F0"
                textSecondaryHex = "#B8A0C8"
                itemBgHex = "#2A1640"
            }
        }

        val cardBgColor = Color.parseColor(cardBgHex)
        val accentColor = Color.parseColor(accentHex)
        val textPrimaryColor = Color.parseColor(textPrimaryHex)
        val textSecondaryColor = Color.parseColor(textSecondaryHex)
        val itemBgColor = Color.parseColor(itemBgHex)
        val dividerColor = ColorUtils.setAlphaComponent(textPrimaryColor, 0x1A)

        // Root Dialog view tint
        rootView.findViewById<androidx.cardview.widget.CardView>(R.id.ttsCardRoot)?.setCardBackgroundColor(cardBgColor)

        // Headers & Labels
        rootView.findViewById<TextView>(R.id.tvTtsTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.lblSpeed)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvSpeedValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.lblPitch)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.tvPitchValue)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.lblVoice)?.setTextColor(textSecondaryColor)
        rootView.findViewById<TextView>(R.id.lblTimer)?.setTextColor(textSecondaryColor)

        // Current sentence display
        val cardSubtitle = rootView.findViewById<androidx.cardview.widget.CardView>(R.id.cardSubtitle)
        cardSubtitle?.setCardBackgroundColor(itemBgColor)
        tvCurrentSentence.setTextColor(textPrimaryColor)

        // Close button
        rootView.findViewById<ImageButton>(R.id.btnClose)?.imageTintList = ColorStateList.valueOf(textPrimaryColor)

        // Dividers
        dragHandle.setBackgroundColor(dividerColor)
        dividerTop.setBackgroundColor(dividerColor)

        // SeekBars
        val tintStateList = ColorStateList.valueOf(accentColor)
        val trackStateList = ColorStateList.valueOf(dividerColor)

        sbSpeed.progressTintList = tintStateList
        sbSpeed.thumbTintList = tintStateList
        sbSpeed.progressBackgroundTintList = trackStateList

        sbPitch.progressTintList = tintStateList
        sbPitch.thumbTintList = tintStateList
        sbPitch.progressBackgroundTintList = trackStateList

        // Style media buttons with generous circles & accent tint
        val buttonBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor)
        }
        val buttonBgPrevNext = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(itemBgColor)
        }

        btnPlayPause.background = buttonBg
        btnPlayPause.imageTintList = ColorStateList.valueOf(cardBgColor)

        btnStop.background = buttonBgPrevNext
        btnStop.imageTintList = ColorStateList.valueOf(textPrimaryColor)

        btnPrev.background = buttonBgPrevNext
        btnPrev.imageTintList = ColorStateList.valueOf(textPrimaryColor)

        btnNext.background = buttonBgPrevNext
        btnNext.imageTintList = ColorStateList.valueOf(textPrimaryColor)

        // Update active spinners
        (spinnerVoice.adapter as? TtsSpinnerAdapter)?.let { adapter ->
            adapter.textColor = textPrimaryColor
            adapter.itemBgColor = itemBgColor
            adapter.notifyDataSetChanged()
        }
        (spinnerTimer.adapter as? TtsSpinnerAdapter)?.let { adapter ->
            adapter.textColor = textPrimaryColor
            adapter.itemBgColor = itemBgColor
            adapter.notifyDataSetChanged()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.applyStarryBackground()
        dialog?.window?.let { window ->
            val metrics = resources.displayMetrics
            val isTablet = metrics.widthPixels > metrics.heightPixels && metrics.widthPixels > 1200
            val width = if (isTablet) {
                (metrics.widthPixels * 0.50).toInt()
            } else {
                (metrics.widthPixels * 0.95).toInt()
            }

            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)

            val params = window.attributes
            params.y = 24 // beautiful bottom floating style
            window.attributes = params

            window.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
    }

    private class TtsSpinnerAdapter(
        context: android.content.Context,
        objects: List<String>
    ) : ArrayAdapter<String>(context, R.layout.spinner_item, objects) {
        var textColor: Int = Color.parseColor("#E8D8F0")
        var itemBgColor: Int = Color.parseColor("#2A1640")

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            if (view is TextView) {
                view.setTextColor(textColor)
                view.setBackgroundColor(Color.TRANSPARENT)
            }
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            if (view is TextView) {
                view.setTextColor(textColor)
                view.setBackgroundColor(itemBgColor)
            }
            return view
        }
    }
}
