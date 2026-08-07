package com.nightread.app.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightread.app.R
import com.nightread.app.data.RagSearchResult
import com.nightread.app.databinding.DialogBookRagSearchBinding
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * BottomSheet для RAG поиска по тексту книги.
 * Использует Flow + collectLatest для эффективного поиска с автоматической отменой.
 */
class BookRagSearchBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogBookRagSearchBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ReaderViewModel by activityViewModels()
    private lateinit var adapter: RagResultsAdapter
    
    private val searchQuery = MutableStateFlow("")
    private var onResultSelectedListener: ((Int, Int) -> Unit)? = null

    companion object {
        private const val TAG = "BookRagSearchBottomSheet"
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 200
        private const val SEARCH_DEBOUNCE_MS = 300L

        fun newInstance() = BookRagSearchBottomSheet()
    }

    override fun getTheme(): Int = R.style.DarkPurpleBottomSheetDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBookRagSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupListeners()
        setupSearchFlow()
        focusInput()
    }

    private fun setupRecyclerView() {
        adapter = RagResultsAdapter { result ->
            onResultSelectedListener?.invoke(result.startCharOffset, result.pageIndex)
            dismiss()
        }
        binding.rvRagResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BookRagSearchBottomSheet.adapter
        }
    }

    private fun setupListeners() {
        binding.btnCloseRagSearch.setOnClickListener { dismiss() }
        
        binding.btnClearRagQuery.setOnClickListener {
            binding.etRagSearchQuery.setText("")
            binding.etRagSearchQuery.requestFocus()
        }
        
        binding.etRagSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        // Современный способ обработки текста
        binding.etRagSearchQuery.doAfterTextChanged { editable ->
            val text = editable?.toString() ?: ""
            binding.btnClearRagQuery.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
            searchQuery.value = text
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupSearchFlow() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchQuery
                    .debounce(SEARCH_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collectLatest { query ->
                        val trimmed = query.trim().take(MAX_QUERY_LENGTH)
                        
                        // Обрабатываем короткий запрос
                        if (trimmed.length < MIN_QUERY_LENGTH) {
                            showInitialState()
                            return@collectLatest
                        }
                        
                        // Показываем загрузку
                        showLoadingState()
                        
                        // Выполняем поиск
                        val results = viewModel.searchRag(trimmed)
                        
                        // Отображаем результаты
                        displayResults(results)
                    }
            }
        }
    }

    private fun focusInput() {
        binding.etRagSearchQuery.post {
            binding.etRagSearchQuery.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etRagSearchQuery, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showInitialState() {
        binding.pbRagSearching.visibility = View.GONE
        binding.tvRagResultCounter.text = getString(R.string.rag_search_hint)
        adapter.submitList(emptyList())
        binding.rvRagResults.visibility = View.VISIBLE
        binding.layoutRagEmptyState.visibility = View.GONE
    }

    private fun showLoadingState() {
        binding.pbRagSearching.visibility = View.VISIBLE
        binding.tvRagResultCounter.text = getString(R.string.rag_searching)
        binding.rvRagResults.visibility = View.VISIBLE
        binding.layoutRagEmptyState.visibility = View.GONE
    }

    private fun displayResults(results: List<RagSearchResult>) {
        binding.pbRagSearching.visibility = View.GONE
        
        if (results.isEmpty()) {
            binding.tvRagResultCounter.text = getString(R.string.rag_no_results)
            adapter.submitList(emptyList())
            binding.layoutRagEmptyState.visibility = View.VISIBLE
            binding.rvRagResults.visibility = View.GONE
        } else {
            binding.tvRagResultCounter.text = getString(R.string.rag_results_count, results.size)
            adapter.submitList(results)
            binding.layoutRagEmptyState.visibility = View.GONE
            binding.rvRagResults.visibility = View.VISIBLE
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etRagSearchQuery.windowToken, 0)
    }

    fun setOnResultSelectedListener(listener: (Int, Int) -> Unit) {
        onResultSelectedListener = listener
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

/**
 * Адаптер для отображения результатов RAG поиска с использованием ListAdapter и DiffUtil.
 */
class RagResultsAdapter(
    private val onItemClick: (RagSearchResult) -> Unit
) : ListAdapter<RagSearchResult, RagResultsAdapter.ViewHolder>(
    DiffCallback
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rag_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPageBadge: TextView = itemView.findViewById(R.id.tvRagPageBadge)
        private val tvScoreInfo: TextView = itemView.findViewById(R.id.tvRagScoreInfo)
        private val tvSnippetText: TextView = itemView.findViewById(R.id.tvRagSnippetText)

        fun bind(item: RagSearchResult, onItemClick: (RagSearchResult) -> Unit) {
            tvPageBadge.text = itemView.context.getString(R.string.rag_page, item.pageIndex + 1)
            tvScoreInfo.text = itemView.context.getString(R.string.rag_matches, item.matchCount)
            tvSnippetText.text = item.snippet

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RagSearchResult>() {
        override fun areItemsTheSame(oldItem: RagSearchResult, newItem: RagSearchResult): Boolean {
            return oldItem.startCharOffset == newItem.startCharOffset
        }

        override fun areContentsTheSame(oldItem: RagSearchResult, newItem: RagSearchResult): Boolean {
            return oldItem == newItem
        }
    }
}
