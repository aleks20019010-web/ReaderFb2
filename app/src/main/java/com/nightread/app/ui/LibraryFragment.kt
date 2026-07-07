package com.nightread.app.ui

import android.view.ViewGroup

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var tvBookCount: TextView
    private lateinit var etSearch: androidx.appcompat.widget.SearchView
    
    // Detailed Scan progress bindings
    private lateinit var layoutScanProgress: View
    private lateinit var tvScanStatus: TextView
    private lateinit var tvTimeElapsed: TextView
    private lateinit var progressBarSpinner: ProgressBar
    private lateinit var progressBarScanProgress: ProgressBar
    private lateinit var headerProgressBar: ProgressBar
    private lateinit var progressBarEmptyState: ProgressBar
    
    private lateinit var rvBooks: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyStateTitle: TextView
    private lateinit var tvEmptyStateDesc: TextView
    private lateinit var btnEmptyStateScan: com.google.android.material.button.MaterialButton
    private lateinit var ivEmptyIllustration: ImageView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var shimmerContainer: com.facebook.shimmer.ShimmerFrameLayout

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
            context?.let { ctx ->
                Toast.makeText(ctx, "Необходимо разрешение для поиска книг", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) {
                startScan()
            } else {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Необходимо разрешение для поиска книг", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val ctx = context ?: return
        if (!isAdded) return
        
        android.util.Log.d("LibraryFragment", "checkPermissionsAndScan: Checking storage permissions")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                if (android.os.Environment.isExternalStorageManager()) {
                    android.util.Log.d("LibraryFragment", "checkPermissionsAndScan: All Files Access granted")
                    startScan()
                } else {
                    android.util.Log.d("LibraryFragment", "checkPermissionsAndScan: Requesting All Files Access")
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                        requestManageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("LibraryFragment", "Failed to launch ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, trying general settings", e)
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            requestManageStorageLauncher.launch(intent)
                        } catch (ex: Exception) {
                            android.util.Log.e("LibraryFragment", "Failed to launch ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, falling back to standard READ_EXTERNAL_STORAGE", ex)
                            requestStandardStoragePermission()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryFragment", "Error checking isExternalStorageManager, falling back to standard permission", e)
                requestStandardStoragePermission()
            }
        } else {
            requestStandardStoragePermission()
        }
    }

    private fun requestStandardStoragePermission() {
        val ctx = context ?: return
        if (!isAdded) return
        
        android.util.Log.d("LibraryFragment", "requestStandardStoragePermission: Checking standard READ_EXTERNAL_STORAGE permission")
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startScan()
        } else {
            android.util.Log.d("LibraryFragment", "requestStandardStoragePermission: Launching standard permission request")
            try {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            } catch (e: Exception) {
                android.util.Log.e("LibraryFragment", "Failed standard permission launcher", e)
            }
        }
    }

    private fun startScan() {
        val ctx = context ?: return
        if (!isAdded) return
        if (isSwipeRescanInProgress) {
            android.util.Log.d("LibraryFragment", "startScan: Starting incremental book scan on ViewModel")
            viewModel.startIncrementalBookScan()
            Toast.makeText(ctx, "Быстрая проверка новых книг...", Toast.LENGTH_SHORT).show()
        } else {
            android.util.Log.d("LibraryFragment", "startScan: Starting local deep book scan on ViewModel")
            viewModel.startLocalBookScan()
            Toast.makeText(ctx, "Начато сканирование папок...", Toast.LENGTH_SHORT).show()
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
        btnMenu = view.findViewById(R.id.btnMenu)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvBookCount = view.findViewById(R.id.tvBookCount)
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
        headerProgressBar = view.findViewById(R.id.headerProgressBar)
        progressBarEmptyState = view.findViewById(R.id.progressBarEmptyState)
        
        rvBooks = view.findViewById(R.id.rvBooks)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        tvEmptyStateTitle = view.findViewById(R.id.tvEmptyStateTitle)
        tvEmptyStateDesc = view.findViewById(R.id.tvEmptyStateDesc)
        btnEmptyStateScan = view.findViewById(R.id.btnEmptyStateScan)
        ivEmptyIllustration = view.findViewById(R.id.ivEmptyIllustration)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        shimmerContainer = view.findViewById(R.id.shimmer_view_container)
        shimmerContainer.startShimmer()
        shimmerContainer.visibility = View.VISIBLE
        // Parallax effect for background and book covers
        val textureBackground = view.findViewById<View>(R.id.textureBackground)
        rvBooks.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            var totalScrollY = 0
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                totalScrollY += dy
                textureBackground?.translationY = -(totalScrollY * 0.1f)
                
                // Cover Parallax
                val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                if (layoutManager != null) {
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    val rvCenter = recyclerView.height / 2f
                    
                    for (i in firstVisible..lastVisible) {
                        val child = layoutManager.findViewByPosition(i) ?: continue
                        val ivCover = child.findViewById<ImageView>(R.id.ivCover) ?: continue
                        val childCenter = child.y + child.height / 2f
                        val offset = (childCenter - rvCenter) * 0.05f
                        ivCover.translationY = offset
                    }
                }
            }
        })


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
                val intent = android.content.Intent(requireContext(), BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                startActivity(intent)
            },
            onDeleteBook = { book ->
                showDeleteConfirmationDialog(book)
            }
        )
        rvBooks.adapter = adapter
        rvBooks.itemAnimator = HighlightItemAnimator(adapter)

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val book = adapter.getBookAt(position)
                    if (book != null) {
                        showDeleteConfirmationDialog(book)
                    } else {
                        adapter.notifyItemChanged(position)
                    }
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
            val widthDp = resources.configuration.screenWidthDp
            val spanCount = when {
                widthDp >= 800 -> 4
                widthDp >= 600 -> 3
                else -> 2
            }
            val gridLayoutManager = GridLayoutManager(requireContext(), spanCount)
            rvBooks.layoutManager = gridLayoutManager
            
            // Set margins/padding symmetrically for the grid
            val padding = (6 * resources.displayMetrics.density).toInt()
            rvBooks.setPadding(padding, padding, padding, padding)
            rvBooks.clipToPadding = false
            
            btnToggleViewMode.setIconResource(R.drawable.ic_custom_list)
            btnToggleViewMode.contentDescription = "Режим списка"
        } else {
            rvBooks.layoutManager = LinearLayoutManager(requireContext())
            rvBooks.setPadding(0, 0, 0, 0)
            
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
        setBounceAnimation(btnAutoScan)
        btnAutoScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        // Empty state Auto-Scan action
        setBounceAnimation(btnEmptyStateScan)
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

    private var scanAddedCount = 0

    private fun observeViewModel() {
        // Observe Books Stream
        viewLifecycleOwner.lifecycleScope.launch {
            // Artificial delay to show shimmer for better UX as Room loads extremely fast
            kotlinx.coroutines.delay(800)
            viewModel.searchedBooks.collectLatest { books ->
                if (viewModel.scanState.value.isScanning && allBooksList.isNotEmpty()) {
                    val currentSha1s = allBooksList.map { it.sha1 }.toSet()
                    val newBooks = books.filter { it.sha1 !in currentSha1s }
                    
                    if (newBooks.isNotEmpty()) {
                        val chunks = newBooks.chunked(20) // process in batches of 20
                        var tempAllBooks = allBooksList.toMutableList()
                        
                        for (chunk in chunks) {
                            tempAllBooks.addAll(chunk)
                            allBooksList = tempAllBooks.toList()
                            scanAddedCount += chunk.size
                            
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (::tvScanStatus.isInitialized) {
                                    tvScanStatus.text = "Добавлено: $scanAddedCount книг"
                                }
                                // We don't call filterAndApplyBooks() directly because we want to use adapter.addBooks
                                // But we must filter it. 
                                val filtered = applyFilters(allBooksList)
                                adapter.addBooks(chunk, filtered)
                                updateBookCount(filtered.size)
                                
                                if (filtered.isEmpty()) {
                                    layoutEmptyState.visibility = View.VISIBLE
                                    rvBooks.visibility = View.GONE
                                } else {
                                    layoutEmptyState.visibility = View.GONE
                                    rvBooks.visibility = View.VISIBLE
                                }
                            }
                            kotlinx.coroutines.delay(100) // Small pause between chunks
                        }
                        
                        // Ensure final state exactly matches DB to account for any deletions/updates
                        allBooksList = books
                        val finalFiltered = applyFilters(allBooksList)
                        adapter.updateData(finalFiltered)
                        updateBookCount(finalFiltered.size)
                    } else {
                        allBooksList = books
                        filterAndApplyBooks()
                    }
                } else {
                    scanAddedCount = 0
                    allBooksList = books
                    filterAndApplyBooks()
                }
            }
        }

        // Observe Scan Progress state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
                updateScanUI(state)
                if (!state.isScanning) {
                    scanAddedCount = 0
                }
            }
        }
    }

    private fun updateScanUI(state: com.nightread.app.service.ScanState) {
        val active = state.isScanning
        btnAutoScan.isEnabled = !active
        if (active) {
            startRotating(btnAutoScan)
        } else {
            stopRotating(btnAutoScan)
        }
        btnAutoScan.alpha = if (active) 0.7f else 1.0f
        
        if (::headerProgressBar.isInitialized) {
            headerProgressBar.visibility = if (active) View.VISIBLE else View.GONE
        }
        
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
                if (state.status.isNotBlank()) {
                    context?.let { ctx ->
                        Toast.makeText(ctx, state.status, Toast.LENGTH_LONG).show()
                    }
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
                context?.let { ctx ->
                    Toast.makeText(ctx, state.status, Toast.LENGTH_LONG).show()
                }
            }
        }
        
        updateProgressValues(state.totalFiles, state.processedFiles)
        filterAndApplyBooks()
        if (!active) {
            isSwipeRescanInProgress = false
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

    private fun updateBookCount(count: Int) {
        if (!::tvBookCount.isInitialized) return
        val remainder10 = count % 10
        val remainder100 = count % 100
        val countText = when {
            remainder100 in 11..19 -> "$count книг"
            remainder10 == 1 -> "$count книга"
            remainder10 in 2..4 -> "$count книги"
            else -> "$count книг"
        }
        tvBookCount.text = countText
    }

    private fun applyFilters(books: List<BookEntity>): List<BookEntity> {
        var filtered = books
        
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
        return filtered
    }

    private fun filterAndApplyBooks() {
        if (shimmerContainer.visibility == View.VISIBLE) {
            shimmerContainer.stopShimmer()
            shimmerContainer.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    shimmerContainer.visibility = View.GONE
                    shimmerContainer.alpha = 1f
                }
                .start()
            
            swipeRefresh.alpha = 0f
            swipeRefresh.visibility = View.VISIBLE
            swipeRefresh.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }

        if (allBooksList.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvBooks.visibility = View.GONE
            updateBookCount(0)
            
            if (viewModel.scanState.value.isScanning) {
                if (::progressBarEmptyState.isInitialized) {
                    progressBarEmptyState.visibility = View.VISIBLE
                }
                ivEmptyIllustration.visibility = View.GONE
                stopPulsing(ivEmptyIllustration)
                btnEmptyStateScan.visibility = View.GONE
                tvEmptyStateTitle.text = "Сканирование памяти..."
                if (isSwipeRescanInProgress) {
                    tvEmptyStateDesc.text = "Выполняется обновление библиотеки по запросу...\nПожалуйста, подождите."
                } else {
                    tvEmptyStateDesc.text = "Идёт автоматический поиск книг...\nПожалуйста, подождите."
                }
            } else {
                if (::progressBarEmptyState.isInitialized) {
                    progressBarEmptyState.visibility = View.GONE
                }
                stopPulsing(ivEmptyIllustration)
                ivEmptyIllustration.visibility = View.VISIBLE
                btnEmptyStateScan.visibility = View.VISIBLE
                tvEmptyStateTitle.text = "Библиотека пока пустая"
                tvEmptyStateDesc.text = "Начните сканирование или импортируйте книги"
            }
            return
        }

        val filtered = applyFilters(allBooksList)

        adapter.updateData(filtered)
        updateBookCount(filtered.size)

        if (filtered.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            if (::progressBarEmptyState.isInitialized) {
                progressBarEmptyState.visibility = View.GONE
            }
            ivEmptyIllustration.visibility = View.GONE
            btnEmptyStateScan.visibility = View.GONE
            tvEmptyStateTitle.text = "Ничего не найдено"
            tvEmptyStateDesc.text = "Попробуйте изменить поисковый запрос."
            rvBooks.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            if (::progressBarEmptyState.isInitialized) {
                progressBarEmptyState.visibility = View.GONE
            }
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

    private fun startRotating(view: View) {
        view.animate().cancel()
        val animator = ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f)
        animator.duration = 1000 // 1 revolution per second
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = android.view.animation.LinearInterpolator()
        view.setTag(R.id.breathing_animator, animator) // reuse tag or create new
        animator.start()
    }

    private fun stopRotating(view: View) {
        val animator = view.getTag(R.id.breathing_animator) as? ObjectAnimator
        animator?.cancel()
        view.animate().rotation(0f).setDuration(300).start()
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

    private fun setBounceAnimation(view: View, scaleDownValue: Float = 0.92f) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", scaleDownValue)
                    val scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", scaleDownValue)
                    scaleDownX.duration = 100
                    scaleDownY.duration = 100
                    val scaleDown = AnimatorSet()
                    scaleDown.play(scaleDownX).with(scaleDownY)
                    scaleDown.start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1.0f)
                    val scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1.0f)
                    scaleUpX.duration = 300
                    scaleUpY.duration = 300
                    scaleUpX.interpolator = OvershootInterpolator(1.5f)
                    scaleUpY.interpolator = OvershootInterpolator(1.5f)
                    val scaleUp = AnimatorSet()
                    scaleUp.play(scaleUpX).with(scaleUpY)
                    scaleUp.start()
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                }
            }
            true
        }
    }
}
