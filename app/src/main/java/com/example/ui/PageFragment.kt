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
        
        rootContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // TextView optimized for reading with minimal margins and full width/height
        textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // Apply high-quality Russian hyphenation suggestions
            text = RussianHyphenator.hyphenate(pageText)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
                breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            }
            gravity = Gravity.TOP or Gravity.START
        }

        rootContainer.addView(textView)

        // Set up cutout/notch and system bar safe margins (Left/Right/Bottom = 16dp, Top = cutout height + 12dp)
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val cutout = insets.displayCutout
            val cutoutHeight = cutout?.safeInsetTop ?: 0
            val density = view.resources.displayMetrics.density
            val topPadding = cutoutHeight + (12 * density).toInt()
            val leftPadding = (16 * density).toInt()
            val rightPadding = (16 * density).toInt()
            val bottomPadding = (48 * density).toInt() // Room for page indicator/navigation controls
            view.setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
            insets
        }

        // Retrieve shared ReaderViewModel from activity
        viewModel = (requireActivity() as ReaderActivity).viewModel

        // Observe settings flow to update styling dynamically without page reload
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.themeState.collect { theme ->
                if (theme == "night") {
                    rootContainer.setBackgroundColor(android.graphics.Color.parseColor("#1a1a1a"))
                    textView.setTextColor(android.graphics.Color.parseColor("#e0e0e0"))
                } else {
                    rootContainer.setBackgroundColor(android.graphics.Color.parseColor("#f5f0e8"))
                    textView.setTextColor(android.graphics.Color.parseColor("#3a3a3a"))
                }
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

        // Double-purpose tap guesture handler (Left-Top corner -> Theme, Center -> Toggle system UI controls)
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val x = e.x
                val y = e.y
                val density = resources.displayMetrics.density
                val area100dp = 100 * density
                
                if (x < area100dp && y < area100dp) {
                    // Tap on top-left (100x100 dp zone): switch day/night mode
                    viewModel.toggleTheme()
                    return true
                }
                
                // Tap in the middle 50% width & height zone: toggle fullscreen/immersive controls
                val width = rootContainer.width
                val height = rootContainer.height
                val centerX = width / 2f
                val centerY = height / 2f
                val radiusX = width * 0.25f
                val radiusY = height * 0.25f
                if (x in (centerX - radiusX)..(centerX + radiusX) && y in (centerY - radiusY)..(centerY + radiusY)) {
                    (requireActivity() as ReaderActivity).toggleSystemUi()
                    return true
                }
                
                return false
            }
        })

        rootContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Bubble touch event up to ViewPager2 so swiping to change pages remains seamless
            false
        }

        return rootContainer
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
