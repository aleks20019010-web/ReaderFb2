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

class TermExplanationBottomSheet : BottomSheetDialogFragment() {

    private var term: String = ""
    private var contextSnippet: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        term = arguments?.getString(ARG_TERM) ?: ""
        contextSnippet = arguments?.getString(ARG_CONTEXT) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_term_explanation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTerm = view.findViewById<TextView>(R.id.tvTerm)
        val tvExplanation = view.findViewById<TextView>(R.id.tvExplanation)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnOk = view.findViewById<View>(R.id.btnOk)

        tvTerm.text = term
        tvExplanation.text = "Анализируем термин в контексте..."
        progressBar.visibility = View.VISIBLE

        btnOk.setOnClickListener { dismiss() }

        lifecycleScope.launch {
            val explanation = LocalAIManager.explainTerm(requireContext(), term, contextSnippet)
            tvExplanation.text = explanation
            progressBar.visibility = View.GONE
        }
    }

    companion object {
        private const val ARG_TERM = "arg_term"
        private const val ARG_CONTEXT = "arg_context"

        fun newInstance(term: String, contextSnippet: String): TermExplanationBottomSheet {
            val fragment = TermExplanationBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_TERM, term)
                putString(ARG_CONTEXT, contextSnippet)
            }
            return fragment
        }
    }
}
