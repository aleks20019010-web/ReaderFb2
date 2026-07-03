package com.example.ui

import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import com.example.R

class PageFragment : Fragment() {
    private var pageText: String = ""
    private lateinit var textView: TextView
    private lateinit var rootContainer: FrameLayout
    private lateinit var viewModel: ReaderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageText = arguments?.getString("PAGE_TEXT") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        
        // Inflate from XML to ensure standard Android-compliant layout structure
        val view = inflater.inflate(R.layout.fragment_page, container, false)
        rootContainer = view.findViewById(R.id.rootContainer)
        textView = view.findViewById(R.id.textView)

        // Ensure TextView takes 100% space and uses hyphenation
        textView.apply {
            text = RussianHyphenator.hyphenate(pageText)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
                breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            }
            gravity = Gravity.TOP or Gravity.START
        }

        // Retrieve shared ReaderViewModel from activity
        viewModel = (requireActivity() as ReaderActivity).viewModel

        // Set up cutout/notch and system bar safe margins (Left/Right/Bottom customized, Top = cutout height + 12dp)
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val cutout = insets.displayCutout
            val cutoutHeight = cutout?.safeInsetTop ?: systemBars.top
            val density = view.resources.displayMetrics.density
            
            val isMarginsEnabled = viewModel.pageMarginsState.value
            val sideMarginDp = if (isMarginsEnabled) 16 else 6
            val bottomMarginDp = if (isMarginsEnabled) 48 else 32
            
            val topPadding = cutoutHeight + (12 * density).toInt()
            val leftPadding = (sideMarginDp * density).toInt()
            val rightPadding = (sideMarginDp * density).toInt()
            val bottomPadding = (bottomMarginDp * density).toInt() // Room for page indicator/navigation controls
            
            view.setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
            insets
        }

        // Add logging of heights post-layout as requested
        rootContainer.post {
            if (isAdded) {
                val dm = resources.displayMetrics
                val screenH = dm.heightPixels
                val viewPagerH = (requireActivity() as? ReaderActivity)?.findViewById<View>(android.R.id.content)?.height ?: -1
                val rootH = rootContainer.height
                val textH = textView.height
                android.util.Log.d("ReaderLayoutLog", "Layout heights -> Screen: ${screenH}px, ViewPager(Estimated): ${viewPagerH}px, RootContainer: ${rootH}px, TextView: ${textH}px")
            }
        }

        // Observe settings flow to update styling dynamically without page reload
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.themeState.collect { theme ->
                val bg = when (theme) {
                    "night" -> "#1a1a1a"
                    "sepia" -> "#f4ecd8"
                    else -> "#f5f0e8"
                }
                val fg = when (theme) {
                    "night" -> "#e0e0e0"
                    "sepia" -> "#3b2f2f"
                    else -> "#3a3a3a"
                }
                rootContainer.setBackgroundColor(android.graphics.Color.parseColor(bg))
                textView.setTextColor(android.graphics.Color.parseColor(fg))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fontSizeState.collect { size ->
                textView.textSize = size
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lineSpacingState.collect { spacing ->
                textView.setLineSpacing(0f, spacing)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fontFamilyState.collect { _ ->
                updateTextTypeface()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fontWeightState.collect { _ ->
                updateTextTypeface()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fontAlignmentState.collect { _ ->
                updateTextAlignment()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pageMarginsState.collect { _ ->
                rootContainer.requestApplyInsets()
            }
        }

        // Tap anywhere on the page to toggle system UI controls
        val clickListener = View.OnClickListener {
            if (isAdded) {
                (requireActivity() as? ReaderActivity)?.toggleSystemUi()
            }
        }
        rootContainer.setOnClickListener(clickListener)
        textView.setOnClickListener(clickListener)

        return rootContainer
    }

    private fun updateTextTypeface() {
        if (!::textView.isInitialized || !::viewModel.isInitialized) return
        val family = viewModel.fontFamilyState.value
        val weight = viewModel.fontWeightState.value
        val style = if (weight == 1) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        val tf = when (family) {
            "Roboto", "Sans Serif" -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, style)
            "Serif" -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, style)
            "Monospace" -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, style)
            "Merriweather" -> android.graphics.Typeface.create("serif", style)
            else -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, style)
        }
        textView.typeface = tf
    }

    private fun updateTextAlignment() {
        if (!::textView.isInitialized || !::viewModel.isInitialized) return
        val alignment = viewModel.fontAlignmentState.value
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.justificationMode = if (alignment == "justify") {
                android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
            } else {
                android.text.Layout.JUSTIFICATION_MODE_NONE
            }
        }
        textView.gravity = when (alignment) {
            "left" -> Gravity.TOP or Gravity.START
            "right" -> Gravity.TOP or Gravity.END
            "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            else -> Gravity.TOP or Gravity.START
        }
    }

    companion object {
        fun newInstance(pageText: String): PageFragment {
            val fragment = PageFragment()
            val args = Bundle()
            args.putString("PAGE_TEXT", pageText)
            fragment.arguments = args
            return fragment
        }
    }
}
