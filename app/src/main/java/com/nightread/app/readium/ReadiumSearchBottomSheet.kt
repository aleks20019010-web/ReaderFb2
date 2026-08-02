package com.nightread.app.readium

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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.SearchIterator
import org.readium.r2.shared.publication.services.search.search

class ReadiumSearchBottomSheet : BottomSheetDialogFragment() {

    private var publication: Publication? = null
    private var onLocatorSelectedListener: ((Locator) -> Unit)? = null
    private var searchJob: Job? = null
    private var currentIterator: SearchIterator? = null

    private lateinit var etQuery: EditText
    private lateinit var btnClear: ImageView
    private lateinit var btnClose: ImageView
    private lateinit var tvResultCounter: TextView
    private lateinit var pbSearching: ProgressBar
    private lateinit var rvResults: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: SearchResultsAdapter

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

        etQuery = view.findViewById(R.id.etRagSearchQuery)
        btnClear = view.findViewById(R.id.btnClearRagQuery)
        btnClose = view.findViewById(R.id.btnCloseRagSearch)
        tvResultCounter = view.findViewById(R.id.tvRagResultCounter)
        pbSearching = view.findViewById(R.id.pbRagSearching)
        rvResults = view.findViewById(R.id.rvRagResults)
        emptyView = view.findViewById(R.id.layoutRagEmptyState)

        etQuery.hint = "Поиск по всей книге (Readium)..."
        tvResultCounter.text = "Введите слово или фразу для поиска"

        rvResults.layoutManager = LinearLayoutManager(requireContext())
        adapter = SearchResultsAdapter { locator ->
            onLocatorSelectedListener?.invoke(locator)
            dismiss()
        }
        rvResults.adapter = adapter

        btnClose.setOnClickListener { dismiss() }
        btnClear.setOnClickListener { etQuery.setText("") }

        etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                btnClear.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(text)
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

    fun setPublication(pub: Publication, listener: (Locator) -> Unit) {
        this.publication = pub
        this.onLocatorSelectedListener = listener
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < 2) {
            pbSearching.visibility = View.GONE
            tvResultCounter.text = "Введите запрос для поиска"
            adapter.submitList(emptyList())
            emptyView.visibility = View.GONE
            rvResults.visibility = View.VISIBLE
            return
        }

        val pub = publication ?: run {
            tvResultCounter.text = "Книга не загружена"
            return
        }

        pbSearching.visibility = View.VISIBLE
        tvResultCounter.text = "Поиск по тексту Readium..."

        searchJob = lifecycleScope.launch {
            delay(200)

            val results = mutableListOf<Locator>()
            withContext(Dispatchers.IO) {
                try {
                    val iterator = pub.search(trimmed)
                    currentIterator = iterator
                    var res = iterator?.next()?.getOrNull()
                    var count = 0
                    while (res != null && count < 50) {
                        val locs = res.locators
                        results.addAll(locs)
                        count += locs.size
                        res = iterator?.next()?.getOrNull()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!isAdded) return@launch

            pbSearching.visibility = View.GONE

            if (results.isEmpty()) {
                tvResultCounter.text = "Ничего не найдено"
                adapter.submitList(emptyList())
                emptyView.visibility = View.VISIBLE
                rvResults.visibility = View.GONE
            } else {
                tvResultCounter.text = "Найдено совпадений: ${results.size}"
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
        fun newInstance(pub: Publication, onLocatorSelected: (Locator) -> Unit): ReadiumSearchBottomSheet {
            return ReadiumSearchBottomSheet().apply {
                setPublication(pub, onLocatorSelected)
            }
        }
    }
}

class SearchResultsAdapter(
    private val onItemClick: (Locator) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

    private var items: List<Locator> = emptyList()

    fun submitList(newList: List<Locator>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rag_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPageBadge: TextView = itemView.findViewById(R.id.tvRagPageBadge)
        private val tvScoreInfo: TextView = itemView.findViewById(R.id.tvRagScoreInfo)
        private val tvSnippetText: TextView = itemView.findViewById(R.id.tvRagSnippetText)

        fun bind(locator: Locator, onItemClick: (Locator) -> Unit) {
            val title = locator.title ?: locator.href.toString()
            tvPageBadge.text = title
            tvScoreInfo.text = if (locator.locations.progression != null) {
                "${(locator.locations.progression!! * 100).toInt()}%"
            } else ""

            val before = locator.text.before ?: ""
            val highlight = locator.text.highlight ?: ""
            val after = locator.text.after ?: ""

            val fullSnippet = "$before $highlight $after".trim()
            tvSnippetText.text = if (fullSnippet.isNotEmpty()) fullSnippet else "Фрагмент текста"

            itemView.setOnClickListener { onItemClick(locator) }
        }
    }
}
