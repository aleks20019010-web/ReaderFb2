package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.BookEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private val viewModel: BookViewModel by activityViewModels()
    
    private lateinit var adapter: BookAdapter
    private var allBooksList: List<BookEntity> = emptyList()
    private var currentSearchQuery: String = ""

    // View bindings
    private lateinit var btnSearchToggle: View
    private lateinit var btnAutoScan: View
    private lateinit var btnImport: View
    private lateinit var etSearch: EditText
    
    // Detailed Scan progress bindings
    private lateinit var layoutScanProgress: View
    private lateinit var tvScanStatus: TextView
    private lateinit var progressBarScanProgress: ProgressBar
    
    private lateinit var rvBooks: RecyclerView
    private lateinit var tvEmptyLibrary: TextView

    // Register Document Picker for single manual import
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importBookFromUri(it, requireContext()) { success, message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.library_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind Views
        btnSearchToggle = view.findViewById(R.id.btnSearchToggle)
        btnAutoScan = view.findViewById(R.id.btnAutoScan)
        btnImport = view.findViewById(R.id.btnImport)
        etSearch = view.findViewById(R.id.etSearch)
        
        layoutScanProgress = view.findViewById(R.id.layoutScanProgress)
        tvScanStatus = view.findViewById(R.id.tvScanStatus)
        progressBarScanProgress = view.findViewById(R.id.progressBarScanProgress)
        
        rvBooks = view.findViewById(R.id.rvBooks)
        tvEmptyLibrary = view.findViewById(R.id.tvEmptyLibrary)

        // Setup RecyclerView
        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book ->
                viewModel.openBook(book)
            },
            onDeleteBook = { book ->
                viewModel.deleteBook(book.sha1)
                Toast.makeText(requireContext(), "Книга удалена", Toast.LENGTH_SHORT).show()
            }
        )

        rvBooks.layoutManager = GridLayoutManager(requireContext(), 3)
        rvBooks.adapter = adapter

        // Setup Listeners
        setupListeners()

        // Observe State Flow from ViewModel
        observeViewModel()
    }

    private fun setupListeners() {
        // Toggle Search Input visibility
        btnSearchToggle.setOnClickListener {
            if (etSearch.visibility == View.VISIBLE) {
                etSearch.visibility = View.GONE
                etSearch.setText("")
                currentSearchQuery = ""
                filterAndApplyBooks()
            } else {
                etSearch.visibility = View.VISIBLE
                etSearch.requestFocus()
            }
        }

        // Live text change listener for real-time search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                filterAndApplyBooks()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Manual upload / SAF document import
        btnImport.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        // Compact Auto-Scan action
        btnAutoScan.setOnClickListener {
            viewModel.startLocalBookScan()
            Toast.makeText(requireContext(), "Сканирование запущено...", Toast.LENGTH_SHORT).show()
        }
        
        // Hide/dismiss progress layout on tap
        layoutScanProgress.setOnClickListener {
            layoutScanProgress.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        // Observe Books Stream
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allBooks.collectLatest { books ->
                allBooksList = books
                filterAndApplyBooks()
            }
        }

        // Observe Scan Progress state using flow
        viewLifecycleOwner.lifecycleScope.launch {
            com.example.service.BookScanState.isScanning.collectLatest { active ->
                btnAutoScan.isEnabled = !active
                btnAutoScan.alpha = if (active) 0.5f else 1.0f
                
                if (active) {
                    layoutScanProgress.visibility = View.VISIBLE
                } else {
                    // When scanning completes, if there is a message, keep showing it so the user can read the result.
                    // Clicking it will dismiss it.
                    val statusText = com.example.service.BookScanState.scanProgressText.value
                    if (statusText.isBlank()) {
                        layoutScanProgress.visibility = View.GONE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            com.example.service.BookScanState.scanProgressText.collectLatest { text ->
                if (text.isNotBlank()) {
                    layoutScanProgress.visibility = View.VISIBLE
                    tvScanStatus.text = text
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            com.example.service.BookScanState.totalFiles.collectLatest { total ->
                val processed = com.example.service.BookScanState.processedFiles.value
                updateProgressValues(total, processed)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            com.example.service.BookScanState.processedFiles.collectLatest { processed ->
                val total = com.example.service.BookScanState.totalFiles.value
                updateProgressValues(total, processed)
            }
        }
    }

    private fun updateProgressValues(total: Int, processed: Int) {
        if (total > 0) {
            progressBarScanProgress.isIndeterminate = false
            progressBarScanProgress.max = total
            progressBarScanProgress.progress = processed
        } else {
            progressBarScanProgress.isIndeterminate = true
        }
    }

    private fun filterAndApplyBooks() {
        val filtered = if (currentSearchQuery.isBlank()) {
            allBooksList
        } else {
            allBooksList.filter { book ->
                book.title.contains(currentSearchQuery, ignoreCase = true) ||
                        (book.author ?: "").contains(currentSearchQuery, ignoreCase = true)
            }
        }

        adapter.updateData(filtered)

        if (filtered.isEmpty()) {
            tvEmptyLibrary.visibility = View.VISIBLE
            rvBooks.visibility = View.GONE
        } else {
            tvEmptyLibrary.visibility = View.GONE
            rvBooks.visibility = View.VISIBLE
        }
    }
}
