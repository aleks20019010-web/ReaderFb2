package com.nightread.app.ui

import android.content.Context
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
import androidx.lifecycle.lifecycleScope

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
        val density = context.resources.displayMetrics.density
        val padding16 = (16 * density).toInt()

        // Read preferences for dynamic styling using SettingsManager
        val fontSize = com.nightread.app.data.SettingsManager.getFontSize(context)
        val themeName = com.nightread.app.data.SettingsManager.getTheme(context)
        val fontFamily = com.nightread.app.data.SettingsManager.getFontFamily(context)
        val fontWeight = com.nightread.app.data.SettingsManager.getFontWeight(context)
        val lineSpacingMultiplier = com.nightread.app.data.SettingsManager.getLineSpacing(context)

                val (bgColor, textColor) = when (themeName) {
            "light" -> Pair("#FFFFFF", "#121212")
            "dark" -> Pair("#1a1a1a", "#E0E0E0")
            "sepia" -> Pair("#f5f0e8", "#2C2C2C")
            "sepia_contrast" -> Pair("#f5e6c8", "#1a1a1a")
            "contrast" -> Pair("#000000", "#FFFF00")
            "beige" -> Pair("#F4ECD8", "#3B2F1F")
            else -> Pair("#f5f0e8", "#2C2C2C")
        }

        val view = inflater.inflate(com.nightread.app.R.layout.fragment_page, container, false)
        val root = view.findViewById<FrameLayout>(com.nightread.app.R.id.rootContainer).apply {
            setBackgroundColor(Color.parseColor(bgColor))
        }

        val textView = view.findViewById<TextView>(com.nightread.app.R.id.textView).apply {
            setPadding(padding16, paddingTop, padding16, this.paddingBottom) // keep original padding from XML
            includeFontPadding = false
            text = pageText
            textSize = fontSize
            setTextColor(Color.parseColor(textColor))
            
            // Resolve custom typeface
            val baseTypeface = when (fontFamily) {
                "Roboto" -> android.graphics.Typeface.SANS_SERIF
                "Times New Roman" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "Georgia" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "Merriweather" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
                "OpenDyslexic" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
                "Monospace" -> android.graphics.Typeface.MONOSPACE
                else -> android.graphics.Typeface.DEFAULT
            }
            
            val style = when (fontWeight) {
                "Bold" -> android.graphics.Typeface.BOLD
                else -> android.graphics.Typeface.NORMAL
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val numericWeight = when (fontWeight) {
                    "Normal" -> 400
                    "Medium" -> 500
                    "Bold" -> 700
                    "ExtraBold" -> 900
                    else -> 400
                }
                typeface = android.graphics.Typeface.create(baseTypeface, numericWeight, false)
            } else {
                typeface = android.graphics.Typeface.create(baseTypeface, style)
            }

            // Set line spacing multiplier from SettingsManager and 0 extra
            setLineSpacing(0f, lineSpacingMultiplier)
            
            // Break strategy and hyphenation
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
                hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
            }
            gravity = Gravity.TOP or Gravity.START
        }

        // Dynamic padding adjustment under display cutout / camera notch
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val displayCutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val topInset = maxOf(statusBarInsets.top, displayCutoutInsets.top)
            
            textView.setPadding(padding16, topInset, padding16, textView.paddingBottom)
            insets
        }

        textView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                textView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val layout = textView.layout
                val lastLineBottom = if (layout != null && layout.lineCount > 0) {
                    layout.getLineBottom(layout.lineCount - 1)
                } else 0
                val freeSpace = textView.height - textView.paddingTop - textView.paddingBottom - lastLineBottom
                android.util.Log.d("READING_DEBUG", "PageFragment: TextView.height=${textView.height}, paddingTop=${textView.paddingTop}, paddingBottom=${textView.paddingBottom}, lastLineBottom=$lastLineBottom, freeSpace=$freeSpace")
            }
        })

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            com.nightread.app.data.SettingsManager.settingsChanged.collect {
                val context = requireContext()
                val themeName = com.nightread.app.data.SettingsManager.getTheme(context)
                val (bgColor, textColor) = when (themeName) {
                    "light" -> Pair("#FFFFFF", "#121212")
                    "dark" -> Pair("#1a1a1a", "#E0E0E0")
                    "sepia" -> Pair("#f5f0e8", "#2C2C2C")
                    "sepia_contrast" -> Pair("#f5e6c8", "#1a1a1a")
                    "contrast" -> Pair("#000000", "#FFFF00")
                    "beige" -> Pair("#F4ECD8", "#3B2F1F")
                    else -> Pair("#f5f0e8", "#2C2C2C")
                }
                
                val root = view.findViewById<FrameLayout>(com.nightread.app.R.id.rootContainer)
                root.setBackgroundColor(Color.parseColor(bgColor))
                
                val textView = view.findViewById<TextView>(com.nightread.app.R.id.textView)
                textView.setTextColor(Color.parseColor(textColor))
            }
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
