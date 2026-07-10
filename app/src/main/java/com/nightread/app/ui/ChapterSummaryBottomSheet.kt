package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.service.LocalAIManager
import kotlinx.coroutines.launch

class ChapterSummaryBottomSheet : BottomSheetDialogFragment() {

    private var chapterText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chapterText = arguments?.getString(ARG_CHAPTER_TEXT) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_ai_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSummary = view.findViewById<TextView>(R.id.tvSummary)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        tvSummary.text = "AI анализирует главу..."
        progressBar.visibility = View.VISIBLE

        btnClose.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            val summary = LocalAIManager.summarizeChapter(requireContext(), chapterText)
            tvSummary.text = summary
            progressBar.visibility = View.GONE
        }
    }

    companion object {
        private const val ARG_CHAPTER_TEXT = "arg_chapter_text"

        fun newInstance(chapterText: String): ChapterSummaryBottomSheet {
            val fragment = ChapterSummaryBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_CHAPTER_TEXT, chapterText)
            }
            return fragment
        }
    }
}
