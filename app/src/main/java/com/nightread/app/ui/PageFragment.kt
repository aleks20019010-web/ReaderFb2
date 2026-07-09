package com.nightread.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nightread.app.R
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.launch

class PageFragment : Fragment() {
    private var pageText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageText = arguments?.getString("PAGE_TEXT") ?: ""
    }

    @Suppress("WrongConstant")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val view = inflater.inflate(R.layout.fragment_page, container, false)
        val root = view.findViewById<FrameLayout>(R.id.rootContainer)
        val textView = view.findViewById<TextView>(R.id.textView)
        
        textView.text = pageText

        updateStyle(root, textView)

        // Dynamic padding adjustment under display cutout / camera notch
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val displayCutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val topInset = maxOf(statusBarInsets.top, displayCutoutInsets.top)
            
            // Set top padding dynamically, keep left, right, bottom from XML
            textView.setPadding(textView.paddingLeft, topInset, textView.paddingRight, textView.paddingBottom)
            insets
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
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
        val fontWeight = SettingsManager.getFontWeight(context)
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
        
        val baseTypeface = when (fontFamily) {
            "Roboto" -> android.graphics.Typeface.SANS_SERIF
            "Times New Roman" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            "Georgia" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            "Merriweather" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            "OpenDyslexic" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
            "Monospace" -> android.graphics.Typeface.MONOSPACE
            else -> android.graphics.Typeface.DEFAULT
        }
        
        val numericWeight = SettingsManager.getFontWeightAsInt(context)
        val style = if (numericWeight >= 600) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            textView.typeface = android.graphics.Typeface.create(baseTypeface, numericWeight, false)
        } else {
            textView.typeface = android.graphics.Typeface.create(baseTypeface, style)
        }

        textView.setLineSpacing(0f, lineSpacingMultiplier)
        
        val formatted = PageSplitter.formatChapterSpans(pageText, textView.textSize)
        textView.text = formatted
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            textView.breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            textView.hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
        }
    }

    companion object {
        fun newInstance(pageText: String): PageFragment {
            val fragment = PageFragment()
            val args = Bundle().apply {
                putString("PAGE_TEXT", pageText)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
