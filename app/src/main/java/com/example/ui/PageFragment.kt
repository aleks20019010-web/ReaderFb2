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

        textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            text = pageText
            textSize = 18f
            setLineSpacing(0f, 1.25f)
            gravity = Gravity.TOP or Gravity.START
        }

        rootContainer.addView(textView)

        // Set up cutout/notch padding (Left, Right, Bottom = 24dp, Top = cutout height + 16dp)
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val cutout = insets.displayCutout
            val cutoutHeight = cutout?.safeInsetTop ?: 0
            val density = view.resources.displayMetrics.density
            val topPadding = cutoutHeight + (16 * density).toInt()
            val leftPadding = (24 * density).toInt()
            val rightPadding = (24 * density).toInt()
            val bottomPadding = (48 * density).toInt() // leave space for bottom progress indicator
            view.setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
            insets
        }

        // Get the shared ReaderViewModel from the activity
        viewModel = (requireActivity() as ReaderActivity).viewModel

        // Observe theme flow to update background and text colors dynamically
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

        // Gesture detector to capture taps without disrupting swiping
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val x = e.x
                val y = e.y
                val density = resources.displayMetrics.density
                val area100dp = 100 * density
                
                if (x < area100dp && y < area100dp) {
                    // Left top corner: toggle theme!
                    viewModel.toggleTheme()
                    return true
                }
                
                // Center area tap: toggle full screen (immersive)
                val width = rootContainer.width
                val height = rootContainer.height
                val centerX = width / 2f
                val centerY = height / 2f
                val radiusX = width * 0.25f // 50% width in the center
                val radiusY = height * 0.25f // 50% height in the center
                if (x in (centerX - radiusX)..(centerX + radiusX) && y in (centerY - radiusY)..(centerY + radiusY)) {
                    (requireActivity() as ReaderActivity).toggleSystemUi()
                    return true
                }
                
                return false
            }
        })

        rootContainer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Return false so the event bubbles up to ViewPager2 for page swipes!
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
