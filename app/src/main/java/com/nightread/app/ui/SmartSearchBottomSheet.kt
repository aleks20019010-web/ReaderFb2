package com.nightread.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.service.LocalAIManager
import kotlinx.coroutines.launch

class SmartSearchBottomSheet : BottomSheetDialogFragment() {

    private var query: String = ""
    private var bookText: String = ""
    private var onResultClick: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        query = arguments?.getString(ARG_QUERY) ?: ""
        bookText = arguments?.getString(ARG_BOOK_TEXT) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_smart_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvResults = view.findViewById<RecyclerView>(R.id.rvResults)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        rvResults.layoutManager = LinearLayoutManager(context)
        btnClose.setOnClickListener { dismiss() }

        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            // Split text into fragments for search
            val fragments = bookText.split("\n\n").filter { it.isNotBlank() && it.length > 50 }
            val results = LocalAIManager.smartSearch(requireContext(), query, fragments)
            
            rvResults.adapter = SmartSearchAdapter(results) { resultText ->
                val offset = bookText.indexOf(resultText)
                if (offset != -1) {
                    onResultClick?.invoke(offset)
                    dismiss()
                }
            }
            
            progressBar.visibility = View.GONE
        }
    }

    fun setOnResultClickListener(listener: (Int) -> Unit) {
        onResultClick = listener
    }

    private class SmartSearchAdapter(
        private val results: List<LocalAIManager.SmartSearchResult>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<SmartSearchAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvSnippet: TextView = view.findViewById(R.id.tvSnippet)
            val tvRelevance: TextView = view.findViewById(R.id.tvRelevance)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_smart_search_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = results[position]
            holder.tvSnippet.text = result.text
            holder.tvRelevance.text = "Релевантность: ${(result.score * 100).toInt()}%"
            holder.itemView.setOnClickListener { onClick(result.text) }
        }

        override fun getItemCount(): Int = results.size
    }

    companion object {
        private const val ARG_QUERY = "arg_query"
        private const val ARG_BOOK_TEXT = "arg_book_text"

        fun newInstance(query: String, bookText: String): SmartSearchBottomSheet {
            val fragment = SmartSearchBottomSheet()
            fragment.arguments = Bundle().apply {
                putString(ARG_QUERY, query)
                putString(ARG_BOOK_TEXT, bookText)
            }
            return fragment
        }
    }
}
