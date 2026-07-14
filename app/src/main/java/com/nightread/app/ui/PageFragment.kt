package com.nightread.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.nightread.app.ui.ReaderPageView
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
        val textView = view.findViewById<ReaderPageView>(R.id.textView)
        
        updateStyle(root, textView)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val textView = view.findViewById<ReaderPageView>(R.id.textView)
        ViewCompat.setOnApplyWindowInsetsListener(textView) { v, windowInsets ->
            val displayCutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val topInset = maxOf(statusBarInsets.top, displayCutoutInsets.top)
            
            val dp18 = (18 * v.resources.displayMetrics.density).toInt()
            val dp8 = (8 * v.resources.displayMetrics.density).toInt()
            
            v.setPadding(dp18, dp8 + topInset, dp18, dp8)
            windowInsets
        }
        view.requestApplyInsets()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SettingsManager.settingsChanged.collect {
                    val root = view.findViewById<FrameLayout>(R.id.rootContainer)
                    val textView = view.findViewById<ReaderPageView>(R.id.textView)
                    updateStyle(root, textView)
                }
            }
        }
    }

    @Suppress("WrongConstant")
    private fun updateStyle(root: FrameLayout, textView: ReaderPageView) {
        val context = requireContext()
        val fontSize = SettingsManager.getFontSize(context)
        val themeName = SettingsManager.getReadingTheme(context)
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
            "amoled" -> Pair("#000000", "#D9CEE2")
            else -> Pair("#F5F0E8", "#2C2C2C")
        }

        root.setBackgroundColor(Color.parseColor(bgColor))
        textView.textSize = fontSize
        textView.setTextColor(Color.parseColor(textColor))
        
        textView.typeface = FontUtils.createTypefaceWithOpticalBalance(context, fontFamily, numericWeight)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            textView.letterSpacing = SettingsManager.getLetterSpacing(context)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            textView.textLocales = android.os.LocaleList(java.util.Locale("ru"))
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.textLocale = java.util.Locale("ru")
        }

        textView.setLineSpacing(0f, lineSpacingMultiplier)
        
        val offset = arguments?.getInt("PAGE_OFFSET") ?: 0
        android.util.Log.d("PageFragment", "updateStyle: setting text. contains soft hyphens: ${pageText.contains('\u00AD')} (count: ${pageText.count { it == '\u00AD' }})")
        val formattedTextWithClicks = PageSplitter.formatAllSpans(context, pageText, textView.textSize, offset) { noteId ->
            val readingActivity = activity as? ReadingActivity
            readingActivity?.showFootnote(noteId)
        }
        textView.text = formattedTextWithClicks
        textView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        
        textView.isLongClickable = true
        textView.setTextIsSelectable(true)
        textView.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                val start = textView.selectionStart
                val end = textView.selectionEnd
                if (start != -1 && end != -1 && start != end) {
                    val selectedText = textView.text.subSequence(start, end).toString().trim()
                    if (selectedText.isNotEmpty()) {
                        val fullText = textView.text.toString()
                        val contextStart = maxOf(0, start - 150)
                        val contextEnd = minOf(fullText.length, end + 150)
                        val contextSnippet = fullText.substring(contextStart, contextEnd)
                        
                        val bottomSheet = WordActionBottomSheet.newInstance(selectedText, contextSnippet)
                        bottomSheet.show(parentFragmentManager, "WordAction")
                    }
                }
                mode?.finish()
                return false
            }

            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean {
                return false
            }

            override fun onDestroyActionMode(mode: android.view.ActionMode?) {
            }
        }

        val hyphenationEnabled = SettingsManager.isHyphenationEnabled(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            textView.breakStrategy = if (hyphenationEnabled) android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY else android.text.Layout.BREAK_STRATEGY_SIMPLE
            textView.hyphenationFrequency = if (hyphenationEnabled) android.text.Layout.HYPHENATION_FREQUENCY_FULL else android.text.Layout.HYPHENATION_FREQUENCY_NONE
        }
        /*
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        }
        */
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
