package com.example.ui

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

        // Read preferences for dynamic styling
        val sharedPrefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
        val fontSize = sharedPrefs.getFloat("font_size", 18f)
        val themeName = sharedPrefs.getString("theme", "sepia") ?: "sepia"

        val (bgColor, textColor) = when (themeName) {
            "light" -> Pair("#FFFFFF", "#121212")
            "dark" -> Pair("#1a1a1a", "#E0E0E0")
            else -> Pair("#f5f0e8", "#2C2C2C") // sepia / warm paper
        }

        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor(bgColor))
        }

        val textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(padding16, padding16, padding16, padding16)
            text = pageText
            textSize = fontSize
            setTextColor(Color.parseColor(textColor))
            
            // Set minimal line spacing: extra 2dp, multiplier 1.0f
            setLineSpacing(2 * density, 1.0f)
            
            // Break strategy and hyphenation
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
                hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_FULL
            }
            gravity = Gravity.TOP or Gravity.START
        }

        root.addView(textView)

        // Dynamic padding adjustment under display cutout / camera notch
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val displayCutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topInset = maxOf(statusBarInsets.top, displayCutoutInsets.top)
            
            textView.setPadding(padding16, padding16 + topInset, padding16, padding16)
            insets
        }

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
