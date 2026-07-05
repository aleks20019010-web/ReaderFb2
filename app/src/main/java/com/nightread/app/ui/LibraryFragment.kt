package com.nightread.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.nightread.app.R
import com.nightread.app.data.BookEntity
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
    private var isSwipeRescanInProgress: Boolean = false

    // View bindings
    private lateinit var btnToggleViewMode: com.google.android.material.button.MaterialButton
    private var isGridView: Boolean = true
    private lateinit var btnSearchToggle: View
    private lateinit var btnAutoScan: View
    private lateinit var btnImport: View
    private lateinit var btnMenu: View
    private lateinit var tvTitle: TextView
    private lateinit var etSearch: androidx.appcompat.widget.SearchView
    
    // Detailed Scan progress bindings
    private lateinit var layoutScanProgress: View
    private lateinit var tvScanStatus: TextView
    private lateinit var tvTimeElapsed: TextView
    private lateinit var progressBarSpinner: ProgressBar
    private lateinit var progressBarScanProgress: ProgressBar
    
    private lateinit var rvBooks: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyStateTitle: TextView
    private lateinit var tvEmptyStateDesc: TextView
    private lateinit var btnEmptyStateScan: com.google.android.material.button.MaterialButton
    private lateinit var ivEmptyIllustration: ImageView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

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
        // Customize SearchView text color, hint, and close button to match theme
        val searchEditText = etSearch.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText?.apply {
            setTextColor(resources.getColor(R.color.text_primary, null))
            setHintTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 14f
        }
        val closeButton = etSearch.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeButton?.setColorFilter(resources.getColor(R.color.icon_tint, null))
        
        tvTitle.text = when (filterType) {
            "reading" -> "Читаю"
            "read" -> "Прочитано"
            else -> "Библиотека"
        }
        
        btnMenu.setOnClickListener {
            (requireActivity() as? com.nightread.app.MainActivity)?.openDrawer()
        }
        
        layoutScanProgress = view.findViewById(R.id.layoutScanProgress)
        tvScanStatus = view.findViewById(R.id.tvScanStatus)
        tvTimeElapsed = view.findViewById(R.id.tvTimeElapsed)
        progressBarSpinner = view.findViewById(R.id.progressBarSpinner)
        progressBarScanProgress = view.findViewById(R.id.progressBarScanProgress)
        
        rvBooks = view.findViewById(R.id.rvBooks)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        tvEmptyStateTitle = view.findViewById(R.id.tvEmptyStateTitle)
        tvEmptyStateDesc = view.findViewById(R.id.tvEmptyStateDesc)
        btnEmptyStateScan = view.findViewById(R.id.btnEmptyStateScan)
        ivEmptyIllustration = view.findViewById(R.id.ivEmptyIllustration)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)

        // Style SwipeRefreshLayout to match the app's theme
        swipeRefresh.setColorSchemeResources(R.color.accent, R.color.text_primary)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.bg_card)

        // Setup Continue Reading RecyclerView

        btnToggleViewMode = view.findViewById(R.id.btnToggleViewMode)

        // Setup RecyclerView
        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book ->
                viewModel.openBook(book)
            },
            onDeleteBook = { book ->
                showDeleteConfirmationDialog(book)
            }
        )

        rvBooks.adapter = adapter

        // Setup Swipe-to-Delete gestures on the library RecyclerView
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val book = adapter.getBookAt(position)
                    showDeleteConfirmationDialog(book)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val background = ColorDrawable()
                    background.color = resources.getColor(R.color.accent_hover, null) // Dark accent for deletion

                    if (dX > 0) { // Swiping to the right
                        background.setBounds(
                            itemView.left,
                            itemView.top,
                            itemView.left + dX.toInt(),
                            itemView.bottom
                        )
                    } else if (dX < 0) { // Swiping to the left
                        background.setBounds(
                            itemView.right + dX.toInt(),
                            itemView.top,
                            itemView.right,
                            itemView.bottom
                        )
                    } else {
                        background.setBounds(0, 0, 0, 0)
                    }
                    background.draw(c)

                    // Draw a centered trash bin icon inside the swipe background
                    val deleteIcon = ContextCompat.getDrawable(
                        itemView.context,
                        android.R.drawable.ic_menu_delete
                    )
                    if (deleteIcon != null) {
                        val intrinsicWidth = deleteIcon.intrinsicWidth
                        val intrinsicHeight = deleteIcon.intrinsicHeight
                        val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                        val deleteIconMargin = (itemHeight - intrinsicHeight) / 2

                        if (dX > 0) { // Swiping to the right
                            val deleteIconLeft = itemView.left + deleteIconMargin
                            val deleteIconRight = itemView.left + deleteIconMargin + intrinsicWidth
                            val deleteIconBottom = deleteIconTop + intrinsicHeight

                            deleteIcon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
                            if (dX > deleteIconMargin) {
                                deleteIcon.draw(c)
                            }
                        } else if (dX < 0) { // Swiping to the left
                            val deleteIconLeft = itemView.right - deleteIconMargin - intrinsicWidth
                            val deleteIconRight = itemView.right - deleteIconMargin
                            val deleteIconBottom = deleteIconTop + intrinsicHeight

                            deleteIcon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
                            if (dX < -deleteIconMargin) {
                                deleteIcon.draw(c)
                            }
                        }
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvBooks)

        applyViewMode()

        // Setup Listeners
        setupListeners()

        // Observe State Flow from ViewModel
        observeViewModel()
    }

    private fun applyViewMode() {
        val prefs = requireContext().getSharedPreferences("library_prefs", android.content.Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean("key_is_grid_view", true)
        
        adapter.setGridView(isGridView)

        if (isGridView) {
            rvBooks.layoutManager = GridLayoutManager(requireContext(), 3)
            btnToggleViewMode.setIconResource(R.drawable.ic_custom_list)
            btnToggleViewMode.contentDescription = "Режим списка"
        } else {
            rvBooks.layoutManager = LinearLayoutManager(requireContext())
            btnToggleViewMode.setIconResource(R.drawable.ic_custom_grid)
            btnToggleViewMode.contentDescription = "Режим сетки"
        }
    }

    private fun setupListeners() {
        // Toggle Grid/List view mode
        btnToggleViewMode.setOnClickListener {
            isGridView = !isGridView
            requireContext().getSharedPreferences("library_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("key_is_grid_view", isGridView)
                .apply()
            applyViewMode()
        }

        // Toggle Search Input visibility
        btnSearchToggle.setOnClickListener {
            if (etSearch.visibility == View.VISIBLE) {
                etSearch.visibility = View.GONE
                btnSearchToggle.animate().rotation(0f).setDuration(300).start()
                etSearch.setQuery("", false)
                currentSearchQuery = ""
                viewModel.setSearchQuery("")
                filterAndApplyBooks()
            } else {
                etSearch.visibility = View.VISIBLE
                btnSearchToggle.animate().rotation(90f).setDuration(300).start()
                etSearch.requestFocus()
            }
        }

        // Live text change listener for real-time search
        etSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query ?: ""
                viewModel.setSearchQuery(currentSearchQuery)
                filterAndApplyBooks()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText ?: ""
                viewModel.setSearchQuery(currentSearchQuery)
                filterAndApplyBooks()
                return true
            }
        })

        // Manual upload / SAF document import
        btnImport.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        // Compact Auto-Scan action
        btnAutoScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        // Empty state Auto-Scan action
        btnEmptyStateScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        // Swipe refresh layout manual scan trigger
        swipeRefresh.setOnRefreshListener {
            if (viewModel.scanState.value.isScanning) {
                Toast.makeText(requireContext(), "Сканирование уже выполняется", Toast.LENGTH_SHORT).show()
                swipeRefresh.isRefreshing = false
            } else {
                isSwipeRescanInProgress = true
                checkPermissionsAndScan()
            }
        }
        
        // Hide/dismiss progress layout on tap
        layoutScanProgress.setOnClickListener {
            layoutScanProgress.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        // Observe Books Stream
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchedBooks.collectLatest { books ->
                allBooksList = books
                filterAndApplyBooks()
            }
        }

        // Observe Scan Progress state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
                updateScanUI(state)
            }
        }
    }

    private fun updateScanUI(state: com.nightread.app.service.ScanState) {
        val active = state.isScanning
        btnAutoScan.isEnabled = !active
        if (active) {
            startPulsing(btnAutoScan)
        } else {
            stopPulsing(btnAutoScan)
            isSwipeRescanInProgress = false
        }
        btnAutoScan.alpha = if (active) 0.7f else 1.0f
        
        if (::swipeRefresh.isInitialized) {
            swipeRefresh.isRefreshing = active
        }
        
        if (active) {
            wasScanning = true
            layoutScanProgress.visibility = View.VISIBLE
            progressBarSpinner.visibility = View.VISIBLE
        } else {
            progressBarSpinner.visibility = View.GONE
            
            if (wasScanning) {
                wasScanning = false
                if (state.status.startsWith("Scan finished") || state.status.startsWith("No books")) {
                    Toast.makeText(requireContext(), state.status, Toast.LENGTH_LONG).show()
                }
            }
            if (state.status.isBlank()) {
                layoutScanProgress.visibility = View.GONE
            }
        }
        
        if (state.status.isNotBlank()) {
            layoutScanProgress.visibility = View.VISIBLE
            if (isSwipeRescanInProgress) {
                tvScanStatus.text = "Обновление: ${state.status}"
            } else {
                tvScanStatus.text = state.status
            }
            
            if (state.status.startsWith("Error", ignoreCase = true) || state.status.startsWith("Ошибка", ignoreCase = true)) {
                Toast.makeText(requireContext(), state.status, Toast.LENGTH_LONG).show()
            }
        }
        
        updateProgressValues(state.totalFiles, state.processedFiles)
        filterAndApplyBooks()
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
        if (allBooksList.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvBooks.visibility = View.GONE
            
            if (viewModel.scanState.value.isScanning) {
                ivEmptyIllustration.visibility = View.VISIBLE
                startPulsing(ivEmptyIllustration)
                btnEmptyStateScan.visibility = View.GONE
                tvEmptyStateTitle.text = "Сканирование памяти..."
                if (isSwipeRescanInProgress) {
                    tvEmptyStateDesc.text = "Выполняется обновление библиотеки по запросу...\nПожалуйста, подождите."
                } else {
                    tvEmptyStateDesc.text = "Идёт автоматический поиск книг...\nПожалуйста, подождите."
                }
            } else {
                stopPulsing(ivEmptyIllustration)
                ivEmptyIllustration.visibility = View.VISIBLE
                btnEmptyStateScan.visibility = View.VISIBLE
                tvEmptyStateTitle.text = "Библиотека пока пустая"
                tvEmptyStateDesc.text = "Начните сканирование или импортируйте книги"
            }
            return
        }

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
            layoutEmptyState.visibility = View.VISIBLE
            ivEmptyIllustration.visibility = View.GONE
            btnEmptyStateScan.visibility = View.GONE
            tvEmptyStateTitle.text = "Ничего не найдено"
            tvEmptyStateDesc.text = "Попробуйте изменить поисковый запрос."
            rvBooks.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvBooks.visibility = View.VISIBLE
        }

        // Display Continue Reading horizontal list if on "All" tab and no active search query
        if (filterType == "all" && currentSearchQuery.isBlank()) {
            val recentlyRead = allBooksList
                .filter { it.lastReadTime > 0 }
                .sortedByDescending { it.lastReadTime }
                .take(3)
            
            if (recentlyRead.isNotEmpty()) {
            } else {
            }
        } else {
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
                            if (viewModel.isScanning) {
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

    private fun showDeleteConfirmationDialog(book: BookEntity) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Удалить книгу?")
            .setMessage("Вы уверены, что хотите удалить книгу \"${book.title}\" из библиотеки?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteBook(book.sha1)
                Toast.makeText(requireContext(), "Книга удалена", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена") { _, _ ->
                val pos = adapter.getPositionOfBook(book)
                if (pos != -1) {
                    adapter.notifyItemChanged(pos)
                }
            }
            .setOnCancelListener {
                val pos = adapter.getPositionOfBook(book)
                if (pos != -1) {
                    adapter.notifyItemChanged(pos)
                }
            }
            .show()
    }
}
