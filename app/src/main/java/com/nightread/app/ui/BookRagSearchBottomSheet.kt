package com.nightread.app.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.data.BookRagEngine
import com.nightread.app.data.RagSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookRagSearchBottomSheet : BottomSheetDialogFragment() {

    private var onResultSelectedListener: ((offset: Int, pageIndex: Int) -> Unit)? = null
    private lateinit var viewModel: ReaderViewModel
    private var searchJob: Job? = null

    private lateinit var etQuery: EditText
    private lateinit var btnClear: ImageView
    private lateinit var btnClose: ImageView
    private lateinit var tvResultCounter: TextView
    private lateinit var pbSearching: ProgressBar
    private lateinit var rvResults: RecyclerView
    private lateinit var layoutEmptyState: LinearLayoutManager
    private lateinit var adapter: RagResultsAdapter

    override fun getTheme(): Int = R.style.DarkPurpleBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_book_rag_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java)

        etQuery = view.findViewById(R.id.etRagSearchQuery)
        btnClear = view.findViewById(R.id.btnClearRagQuery)
        btnClose = view.findViewById(R.id.btnCloseRagSearch)
        tvResultCounter = view.findViewById(R.id.tvRagResultCounter)
        pbSearching = view.findViewById(R.id.pbRagSearching)
        rvResults = view.findViewById(R.id.rvRagResults)

        val emptyView = view.findViewById<View>(R.id.layoutRagEmptyState)

        rvResults.layoutManager = LinearLayoutManager(requireContext())
        adapter = RagResultsAdapter { result ->
            onResultSelectedListener?.invoke(result.startCharOffset, result.pageIndex)
            dismiss()
        }
        rvResults.adapter = adapter

        btnClose.setOnClickListener {
            dismiss()
        }

        btnClear.setOnClickListener {
            etQuery.setText("")
        }

        etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                btnClear.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                performRagSearch(text, emptyView)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        etQuery.post {
            etQuery.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etQuery, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun setOnResultSelectedListener(listener: (offset: Int, pageIndex: Int) -> Unit) {
        onResultSelectedListener = listener
    }

    private fun performRagSearch(query: String, emptyView: View) {
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < 2) {
            pbSearching.visibility = View.GONE
            tvResultCounter.text = "Введите запрос для RAG поиска"
            adapter.submitList(emptyList())
            emptyView.visibility = View.GONE
            rvResults.visibility = View.VISIBLE
            return
        }

        pbSearching.visibility = View.VISIBLE
        tvResultCounter.text = "Поиск фрагментов RAG..."

        searchJob = lifecycleScope.launch {
            delay(150) // Debounce typing

            val book = viewModel.bookState.value
            val sha1 = book?.sha1 ?: "current_book"
            val fullText = viewModel.getContentText()

            val results = withContext(Dispatchers.Default) {
                BookRagEngine.search(
                    sha1 = sha1,
                    fullText = fullText,
                    query = trimmed,
                    pageResolver = { offset -> viewModel.getPageForOffset(offset) }
                )
            }

            if (!isAdded) return@launch

            pbSearching.visibility = View.GONE

            if (results.isEmpty()) {
                tvResultCounter.text = "Фрагменты не найдены"
                adapter.submitList(emptyList())
                emptyView.visibility = View.VISIBLE
                rvResults.visibility = View.GONE
            } else {
                tvResultCounter.text = "Найдено ${results.size} фрагментов (RAG)"
                adapter.submitList(results)
                emptyView.visibility = View.GONE
                rvResults.visibility = View.VISIBLE
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etQuery.windowToken, 0)
    }

    companion object {
        fun newInstance(): BookRagSearchBottomSheet {
            return BookRagSearchBottomSheet()
        }
    }
}

class RagResultsAdapter(
    private val onItemClick: (RagSearchResult) -> Unit
) : RecyclerView.Adapter<RagResultsAdapter.ViewHolder>() {

    private var items: List<RagSearchResult> = emptyList()

    fun submitList(newList: List<RagSearchResult>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rag_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPageBadge: TextView = itemView.findViewById(R.id.tvRagPageBadge)
        private val tvScoreInfo: TextView = itemView.findViewById(R.id.tvRagScoreInfo)
        private val tvSnippetText: TextView = itemView.findViewById(R.id.tvRagSnippetText)

        fun bind(item: RagSearchResult, onItemClick: (RagSearchResult) -> Unit) {
            tvPageBadge.text = "Стр. ${item.pageIndex + 1}"
            tvScoreInfo.text = "Coвпадений: ${item.matchCount}"
            tvSnippetText.text = item.snippet

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
