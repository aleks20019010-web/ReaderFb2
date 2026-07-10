package com.nightread.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ProgressBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nightread.app.R
import com.nightread.app.data.SettingsManager
import com.nightread.app.service.LocalAIManager
import kotlinx.coroutines.launch

class PageFragment : Fragment() {
    private var pageText: CharSequence = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageText = arguments?.getCharSequence("PAGE_TEXT") ?: ""
    }

    @Suppress("WrongConstant")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_page, container, false)
        val root = view.findViewById<FrameLayout>(R.id.rootContainer)
        val textView = view.findViewById<TextView>(R.id.textView)
        
        updateStyle(root, textView)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val textView = view.findViewById<TextView>(R.id.textView)
        ViewCompat.setOnApplyWindowInsetsListener(textView) { v, windowInsets ->
            val displayCutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val topInset = maxOf(statusBarInsets.top, displayCutoutInsets.top)
            
            val dp8 = (8 * v.resources.displayMetrics.density).toInt()
            val dp16 = (16 * v.resources.displayMetrics.density).toInt()
            
            v.setPadding(dp16, dp8 + topInset, dp16, 0)
            windowInsets
        }
        view.requestApplyInsets()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SettingsManager.settingsChanged.collect {
                    val root = view.findViewById<FrameLayout>(R.id.rootContainer)
                    val textView = view.findViewById<TextView>(R.id.textView)
                    updateStyle(root, textView)
                }
            }
        }
    }

    @Suppress("WrongConstant")
    private fun updateStyle(root: FrameLayout, textView: TextView) {
        val context = requireContext()
        val fontSize = SettingsManager.getFontSize(context)
        val themeName = SettingsManager.getTheme(context)
        val fontFamily = SettingsManager.getFontFamily(context)
        val numericWeight = SettingsManager.getFontWeightAsInt(context)
        val lineSpacingMultiplier = SettingsManager.getLineSpacing(context)
        
        val (bgColor, textColor) = when (themeName) {
            "light" -> Pair("#FFFFFF", "#121212")
            "dark" -> Pair("#1A1A1A", "#E0E0E0")
            "sepia" -> Pair("#F5F0E8", "#2C2C2C")
            "sepia_contrast" -> Pair("#F5E6C8", "#1A1A1A")
            "contrast" -> Pair("#000000", "#FFFF00")
            "beige" -> Pair("#F4ECD8", "#3B2F1F")
            else -> Pair("#F5F0E8", "#2C2C2C")
        }

        root.setBackgroundColor(Color.parseColor(bgColor))
        textView.textSize = fontSize
        textView.setTextColor(Color.parseColor(textColor))
        
        textView.typeface = FontUtils.createTypeface(fontFamily, numericWeight)

        textView.setLineSpacing(0f, lineSpacingMultiplier)
        
        android.util.Log.d("PageFragment", "updateStyle: setting text. contains soft hyphens: ${pageText.contains('\u00AD')} (count: ${pageText.count { it == '\u00AD' }})")
        textView.text = pageText
        
        var lastTouchX = 0f
        var lastTouchY = 0f

        textView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            false
        }

        textView.setOnLongClickListener {
            if (SettingsManager.isAiEnabled(requireContext()) && LocalAIManager.isLoaded()) {
                val word = extractWordAt(textView, lastTouchX, lastTouchY)
                if (word.isNotEmpty()) {
                    val contextSnippet = extractContextAround(textView, lastTouchX, lastTouchY)
                    WordExplanationBottomSheet.newInstance(word, contextSnippet)
                        .show(childFragmentManager, "WordExplanation")
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            textView.breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
            textView.hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        }
    }

    private fun extractWordAt(textView: TextView, x: Float, y: Float): String {
        val offset = textView.getOffsetForPosition(x, y)
        val text = textView.text.toString()
        if (offset < 0 || offset >= text.length) return ""

        var start = offset
        while (start > 0 && text[start - 1].isLetterOrDigit()) {
            start--
        }
        var end = offset
        while (end < text.length && text[end].isLetterOrDigit()) {
            end++
        }
        return text.substring(start, end)
    }

    private fun extractContextAround(textView: TextView, x: Float, y: Float): String {
        val offset = textView.getOffsetForPosition(x, y)
        val text = textView.text.toString()
        if (offset < 0 || offset >= text.length) return ""

        val start = (offset - 100).coerceAtLeast(0)
        val end = (offset + 100).coerceAtMost(text.length)
        return text.substring(start, end)
    }

    companion object {
        fun newInstance(pageText: CharSequence): PageFragment {
            val fragment = PageFragment()
            val args = Bundle().apply {
                putCharSequence("PAGE_TEXT", pageText)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
