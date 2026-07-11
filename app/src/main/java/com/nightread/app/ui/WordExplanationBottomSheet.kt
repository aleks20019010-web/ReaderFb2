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

class WordExplanationBottomSheet : BottomSheetDialogFragment() {

    private var word: String = ""
    private var contextSnippet: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DarkPurpleBottomSheetDialog)
        word = arguments?.getString(ARG_WORD) ?: ""
        contextSnippet = arguments?.getString(ARG_CONTEXT) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_ai_word, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvWord = view.findViewById<TextView>(R.id.tvWord)
        val tvExplanation = view.findViewById<TextView>(R.id.tvExplanation)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnOk = view.findViewById<View>(R.id.btnOk)

        tvWord.text = "Объяснение слова: $word"
        tvExplanation.text = "AI думает..."
        progressBar.visibility = View.VISIBLE

        btnOk.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            val explanation = LocalAIManager.explainWord(requireContext(), word, contextSnippet)
            tvExplanation.text = explanation
            progressBar.visibility = View.GONE
        }
    }

    companion object {
        private const val ARG_WORD = "arg_word"
        private const val ARG_CONTEXT = "arg_context"

        fun newInstance(word: String, contextSnippet: String): WordExplanationBottomSheet {
            val fragment = WordExplanationBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_WORD, word)
                putString(ARG_CONTEXT, contextSnippet)
            }
            return fragment
        }
    }
}
