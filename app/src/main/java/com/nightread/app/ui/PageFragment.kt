package com.nightread.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

import android.widget.ProgressBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nightread.app.R
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.launch
import android.widget.Toast

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
        val textView = view.findViewById<com.nightread.app.ui.customlayout.CustomReaderPageView>(R.id.textView)
        
        updateStyle(root, textView)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val textView = view.findViewById<com.nightread.app.ui.customlayout.CustomReaderPageView>(R.id.textView)
        ViewCompat.setOnApplyWindowInsetsListener(textView) { v, windowInsets ->
            val displayCutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val topInset = maxOf(statusBarInsets.top, displayCutoutInsets.top)
            
            val dp6 = (6 * v.resources.displayMetrics.density).toInt()
            val dp8 = (8 * v.resources.displayMetrics.density).toInt()
            
            v.setPadding(dp6, dp8 + topInset, dp6, dp8)
            windowInsets
        }
        view.requestApplyInsets()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SettingsManager.settingsChanged.collect {
                    val root = view.findViewById<FrameLayout>(R.id.rootContainer)
                    val textView = view.findViewById<com.nightread.app.ui.customlayout.CustomReaderPageView>(R.id.textView)
                    updateStyle(root, textView)
                }
            }
        }
    }

    @Suppress("WrongConstant")
    private fun updateStyle(root: FrameLayout, textView: com.nightread.app.ui.customlayout.CustomReaderPageView) {
        val context = requireContext()
        val fontSize = SettingsManager.getFontSize(context)
        val themeName = SettingsManager.getReadingTheme(context)
        val fontFamily = SettingsManager.getFontFamily(context)
        val numericWeight = SettingsManager.getFontWeightAsInt(context)
        val lineSpacingMultiplier = SettingsManager.getLineSpacing(context)
        val lineSpacingExtra = 0f // Simplified for now

        val (bgColor, textColor) = when (themeName) {
            "light" -> Pair("#FFFFFF", "#121212")
            "dark" -> Pair("#1A1A1A", "#E0E0E0")
            "sepia" -> Pair("#F5F0E8", "#2C2C2C")
            "sepia_contrast" -> Pair("#F5E6C8", "#1A1A1A")
            "contrast" -> Pair("#000000", "#FFFF00")
            "beige" -> Pair("#F4ECD8", "#3B2F1F")
            "amoled" -> Pair("#000000", "#D9CEE2")
            else -> Pair("#F5F0E8", "#2C2C2C")
        }

        root.setBackgroundColor(Color.parseColor(bgColor))
        
        // Create TextPaint with styling
        val paint = android.text.TextPaint().apply {
            isAntiAlias = true
            textSize = fontSize * resources.displayMetrics.scaledDensity
            color = Color.parseColor(textColor)
            typeface = FontUtils.createTypefaceWithOpticalBalance(context, fontFamily, numericWeight)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                letterSpacing = SettingsManager.getLetterSpacing(context)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                textLocales = android.os.LocaleList(java.util.Locale("ru"))
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                textLocale = java.util.Locale("ru")
            }
        }

        // We need the width of the view. Since updateStyle is called in onCreate/onViewCreated, 
        // the view might not be measured yet. We can use post to delay the layout calculation.
        textView.post {
            val width = textView.width - textView.paddingLeft - textView.paddingRight
            val height = textView.height - textView.paddingTop - textView.paddingBottom
            
            if (width > 0 && height > 0) {
                val offset = arguments?.getInt("PAGE_OFFSET") ?: 0
                val formattedTextWithClicks = TextFormatter.addClickableSpans(pageText) { noteId ->
                    val readingActivity = activity as? ReadingActivity
                    readingActivity?.showFootnote(noteId)
                }

                val builder = com.nightread.app.ui.customlayout.TextLayoutBuilder()
                    .setText(formattedTextWithClicks)
                    .setWidth(width)
                    .setHeight(height)
                    .setPaint(paint)
                    .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                    .setHyphenation(SettingsManager.isHyphenationEnabled(context))
                    
                val layout = builder.buildPageLayout(0, formattedTextWithClicks.length)
                
                (textView as com.nightread.app.ui.customlayout.CustomReaderPageView).setLayout(layout)
            }
        }
        
        // Handle clicks if needed, might need a custom touch listener on com.nightread.app.ui.customlayout.CustomReaderPageView
        // textView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
    }

    companion object {
        fun newInstance(pageText: CharSequence, offset: Int = 0): PageFragment {
            val fragment = PageFragment()
            val args = Bundle().apply {
                putCharSequence("PAGE_TEXT", pageText)
                putInt("PAGE_OFFSET", offset)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
