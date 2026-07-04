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

    private var filterType: String = "all"
    
    companion object {
        fun newInstance(filter: String): LibraryFragment {
            val fragment = LibraryFragment()
            val args = Bundle()
            args.putString("FILTER_TYPE", filter)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterType = arguments?.getString("FILTER_TYPE") ?: "all"
    }

    private val viewModel: BookViewModel by activityViewModels()
    
    private lateinit var adapter: BookAdapter
    private var allBooksList: List<BookEntity> = emptyList()
    private var currentSearchQuery: String = ""

    private var wasScanning: Boolean = false

    // View bindings
    private lateinit var btnSearchToggle: View
    private lateinit var btnAutoScan: View
    private lateinit var btnImport: View
    private lateinit var btnMenu: View
    private lateinit var tvTitle: TextView
    private lateinit var etSearch: EditText
    
    // Detailed Scan progress bindings
    private lateinit var layoutScanProgress: View
    private lateinit var tvScanStatus: TextView
    private lateinit var tvTimeElapsed: TextView
    private lateinit var progressBarSpinner: ProgressBar
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScan()
        } else {
            Toast.makeText(requireContext(), "Необходимо разрешение для поиска книг", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) {
                startScan()
            } else {
                Toast.makeText(requireContext(), "Необходимо разрешение для поиска книг", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndScan() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) {
                startScan()
            } else {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse("package:${requireContext().packageName}")
                    requestManageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    requestManageStorageLauncher.launch(intent)
                }
            }
        } else {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                startScan()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun startScan() {
        viewModel.startLocalBookScan()
        Toast.makeText(requireContext(), "Начато сканирование папок...", Toast.LENGTH_SHORT).show()
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
        btnMenu = view.findViewById(R.id.btnMenu)
        tvTitle = view.findViewById(R.id.tvTitle)
        etSearch = view.findViewById(R.id.etSearch)
        
        tvTitle.text = when (filterType) {
            "reading" -> "Читаю"
            "read" -> "Прочитано"
            else -> "Библиотека"
        }
        
        btnMenu.setOnClickListener {
            (requireActivity() as? com.example.MainActivity)?.openDrawer()
        }
        
        layoutScanProgress = view.findViewById(R.id.layoutScanProgress)
        tvScanStatus = view.findViewById(R.id.tvScanStatus)
        tvTimeElapsed = view.findViewById(R.id.tvTimeElapsed)
        progressBarSpinner = view.findViewById(R.id.progressBarSpinner)
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
                btnSearchToggle.animate().rotation(0f).setDuration(300).start()
                etSearch.setText("")
                currentSearchQuery = ""
                filterAndApplyBooks()
            } else {
                etSearch.visibility = View.VISIBLE
                btnSearchToggle.animate().rotation(90f).setDuration(300).start()
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
            checkPermissionsAndScan()
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
                if (active) {
                    startPulsing(btnAutoScan)
                } else {
                    stopPulsing(btnAutoScan)
                }
                btnAutoScan.alpha = if (active) 0.7f else 1.0f
                
                if (active) {
                    wasScanning = true
                    layoutScanProgress.visibility = View.VISIBLE
                    progressBarSpinner.visibility = View.VISIBLE
                } else {
                    progressBarSpinner.visibility = View.GONE
                    // When scanning completes, if there is a message, keep showing it so the user can read the result.
                    // Clicking it will dismiss it.
                    val statusText = com.example.service.BookScanState.scanProgressText.value
                    if (wasScanning) {
                        wasScanning = false
                        if (statusText.startsWith("Сканирование завершено")) {
                            val toastMsg = statusText.replace("Сканирование завершено. ", "")
                            Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                    if (statusText.isBlank()) {
                        layoutScanProgress.visibility = View.GONE
                    }
                }
            }
        }

        // Ticker for elapsed time
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                if (com.example.service.BookScanState.isScanning.value) {
                    val lastUpdate = com.example.service.BookScanState.lastUpdateTime.value
                    if (lastUpdate > 0) {
                        val elapsed = System.currentTimeMillis() - lastUpdate
                        if (elapsed > 2000) {
                            tvTimeElapsed.text = "(зависло: ${elapsed / 1000}с)"
                        } else {
                            tvTimeElapsed.text = ""
                        }
                    }
                } else {
                    tvTimeElapsed.text = ""
                }
                kotlinx.coroutines.delay(1000)
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
        var filtered = allBooksList
        
        filtered = when (filterType) {
            "reading" -> filtered.filter { book -> 
                val percent = if (book.totalCharacters > 0) ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt() else 0
                percent in 1..99
            }
            "read" -> filtered.filter { book -> 
                val percent = if (book.totalCharacters > 0) ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt() else 0
                percent >= 100
            }
            else -> filtered
        }

        if (currentSearchQuery.isNotBlank()) {
            filtered = filtered.filter { book ->
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

    private fun startPulsing(view: View) {
        view.animate().cancel()
        view.scaleX = 1.0f
        view.scaleY = 1.0f
        
        fun pulse() {
            view.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(600)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(600)
                        .withEndAction {
                            if (com.example.service.BookScanState.isScanning.value) {
                                pulse()
                            }
                        }
                        .start()
                }
                .start()
        }
        pulse()
    }

    private fun stopPulsing(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .start()
    }
}
