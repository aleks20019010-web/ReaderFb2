package com.example.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class PageFragment : Fragment() {
    private var pageText: String = ""

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
        val density = context.resources.displayMetrics.density
        val padding16 = (16 * density).toInt()

        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#FAF6EE")) // Warm paper background
        }

        val textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(padding16, padding16, padding16, padding16)
            text = pageText
            textSize = 18f
            setTextColor(android.graphics.Color.parseColor("#2C2C2C")) // Highly legible charcoal text
            setLineSpacing(0f, 1.3f)
            gravity = Gravity.TOP or Gravity.START
        }

        root.addView(textView)

        // Single tap to toggle UI controls in the parent Activity
        val clickListener = View.OnClickListener {
            (requireActivity() as? ReaderActivity)?.toggleSystemUi()
        }
        root.setOnClickListener(clickListener)
        textView.setOnClickListener(clickListener)

        return root
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
