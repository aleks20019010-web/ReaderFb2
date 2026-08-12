package com.nightread.app.ui

import com.nightread.app.data.SettingsManager
import com.nightread.app.data.ThemeHelper
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
import kotlinx.coroutines.withContext
import com.nightread.app.data.AppDatabase
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private var isJobCancelledDialogShown: Boolean = false
    private var isScanCompletionDismissed: Boolean = false

    // View bindings
    private lateinit var btnToggleViewMode: com.google.android.material.button.MaterialButton
    private lateinit var btnSort: View
    private var isGridView: Boolean = true
    private lateinit var btnSearchToggle: View
    private lateinit var btnToggleTheme: com.google.android.material.button.MaterialButton
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
    private lateinit var layoutNewBooksBanner: LinearLayout
    private lateinit var tvNewBooksCount: TextView
    private lateinit var btnShowNewBooks: TextView
    private lateinit var btnCloseNewBooks: ImageView
    private val hideBannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable { layoutNewBooksBanner.visibility = View.GONE }
    private var scanDismissJob: kotlinx.coroutines.Job? = null
    private lateinit var tvEmptyStateDesc: TextView
    private lateinit var btnEmptyStateScan: com.google.android.material.button.MaterialButton
    private lateinit var btnRecoverLibrary: com.google.android.material.button.MaterialButton
    private lateinit var ivEmptyIllustration: ImageView
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var shimmerContainer: com.facebook.shimmer.ShimmerFrameLayout

    // Register Document Picker for single manual import
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importBookFromUri(it, requireContext()) { success, message ->
                CustomToast.show(requireContext(), message)
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
                CustomToast.show(ctx, "Необходимо разрешение для поиска книг")
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
                    CustomToast.show(ctx, "Необходимо разрешение для поиска книг")
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
            CustomToast.show(ctx, "Быстрая проверка новых книг...", android.widget.Toast.LENGTH_SHORT)
        } else {
            android.util.Log.d("LibraryFragment", "startScan: Starting local deep book scan on ViewModel")
            viewModel.startLocalBookScan()
            CustomToast.show(ctx, "Начато сканирование папок...", android.widget.Toast.LENGTH_SHORT)
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

        val layoutNormalHeader: View = view.findViewById(R.id.layoutNormalHeader)
        val layoutSelectionHeader: View = view.findViewById(R.id.layoutSelectionHeader)
        val btnCloseSelection: View = view.findViewById(R.id.btnCloseSelection)
        val tvSelectedCount: TextView = view.findViewById(R.id.tvSelectedCount)
        val btnSelectAll: View = view.findViewById(R.id.btnSelectAll)
        val btnDeleteSelected: View = view.findViewById(R.id.btnDeleteSelected)

        // Bind Views
        btnSearchToggle = view.findViewById(R.id.btnSearchToggle)
        btnToggleTheme = view.findViewById(R.id.btnToggleTheme)
        btnImport = view.findViewById(R.id.btnImport)
        btnSort = view.findViewById(R.id.btnSort)
        btnMenu = view.findViewById(R.id.btnMenu)

        btnSort.setOnClickListener {
            showSortDialog()
        }
        tvTitle = view.findViewById(R.id.tvTitle)
        tvBookCount = view.findViewById(R.id.tvBookCount)
        etSearch = view.findViewById(R.id.etSearch)
        // Customize SearchView text color, hint, and close button to match theme
        val searchEditText = etSearch.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        val textPrimaryColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val textSecondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        searchEditText?.apply {
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            textSize = 14f
        }
        val closeButton = etSearch.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeButton?.setColorFilter(resources.getColor(R.color.icon_tint, null))
        
        tvTitle.text = when (filterType) {
            "reading" -> getString(R.string.drawer_reading)
            "read" -> getString(R.string.drawer_read)
            else -> getString(R.string.drawer_library)
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
        layoutNewBooksBanner = view.findViewById<LinearLayout>(R.id.layoutNewBooksBanner)
        tvNewBooksCount = view.findViewById<TextView>(R.id.tvNewBooksCount)
        btnShowNewBooks = view.findViewById<TextView>(R.id.btnShowNewBooks)
        btnCloseNewBooks = view.findViewById<android.widget.ImageView>(R.id.btnCloseNewBooks)

        btnShowNewBooks.setOnClickListener {
            layoutNewBooksBanner.visibility = View.GONE
            startActivity(android.content.Intent(requireContext(), ScanResultActivity::class.java))
        }

        btnCloseNewBooks.setOnClickListener {
            layoutNewBooksBanner.visibility = View.GONE
            hideBannerHandler.removeCallbacks(hideBannerRunnable)
        }
        tvEmptyStateDesc = view.findViewById(R.id.tvEmptyStateDesc)
        btnEmptyStateScan = view.findViewById(R.id.btnEmptyStateScan)
        btnRecoverLibrary = view.findViewById(R.id.btnRecoverLibrary)
        ivEmptyIllustration = view.findViewById(R.id.ivEmptyIllustration)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        shimmerContainer = view.findViewById(R.id.shimmer_view_container)
        shimmerContainer.startShimmer()
        shimmerContainer.visibility = View.VISIBLE
        // Parallax effect for background
        val textureBackground = view.findViewById<View>(R.id.textureBackground)
        val customBg = view.findViewById<View>(R.id.ivCustomLibraryBg)
        val starryOverlay = view.findViewById<com.nightread.app.ui.StarryNightView>(R.id.starryOverlay)
        starryOverlay?.transparentBackground = true
        GalaxyBgHelper.applyBackground(view)
        val headerCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.glassHeaderContainer)
        val ivDaliTopClock = view.findViewById<ImageView>(R.id.ivDaliTopClock)
        DaliThemeHelper.styleLibraryHeader(
            requireContext(),
            headerCard,
            ivDaliTopClock,
            tvTitle,
            tvBookCount,
            btnMenu,
            btnSearchToggle,
            btnSort,
            btnToggleTheme
        )

        rvBooks.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            var totalScrollY = 0
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                totalScrollY += dy
                textureBackground?.translationY = -(totalScrollY * 0.1f)
                // customBg and starryOverlay remain completely static
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
            onOpenBook = { book, coverView ->
                com.nightread.app.data.BookPreloader.preload(requireContext(), book.sha1, book.filePath)
                viewModel.openBook(book)
                androidx.core.view.ViewCompat.setTransitionName(coverView, "cover_${book.sha1}")
                val intent = android.content.Intent(requireContext(), BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    coverView,
                    "cover_${book.sha1}"
                )
                startActivity(intent, options.toBundle())
            },
            onDeleteBook = { book ->
                showDeleteConfirmationDialog(book)
            }
        )
        rvBooks.adapter = adapter
        rvBooks.itemAnimator = HighlightItemAnimator(adapter)

        adapter.onSelectionModeChanged = { isSelectedMode ->
            if (isSelectedMode) {
                layoutNormalHeader.visibility = View.GONE
                layoutSelectionHeader.visibility = View.VISIBLE
                swipeRefresh.isEnabled = false
            } else {
                layoutSelectionHeader.visibility = View.GONE
                layoutNormalHeader.visibility = View.VISIBLE
                swipeRefresh.isEnabled = true
            }
        }

        adapter.onSelectionCountChanged = { count ->
            tvSelectedCount.text = "Выбрано: $count"
        }

        btnCloseSelection.setOnClickListener {
            adapter.exitSelectionMode()
        }

        btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        btnDeleteSelected.setOnClickListener {
            val selectedBooks = adapter.getSelectedBooks()
            if (selectedBooks.isEmpty()) return@setOnClickListener

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Удаление книг")
                .setMessage("Вы уверены, что хотите удалить выбранные книги (${selectedBooks.size} шт.)?\nФайлы также будут физически удалены с устройства.")
                .setPositiveButton("Удалить") { _, _ ->
                    viewModel.deleteSelectedBooks(selectedBooks) {
                        adapter.exitSelectionMode()
                        CustomToast.show(requireContext(), "Удалено книг: ${selectedBooks.size}")
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.isSelectionMode) {
                    adapter.exitSelectionMode()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

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
                        R.drawable.ic_action_delete
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
                widthDp >= 600 -> 4
                else -> 3
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyViewMode()
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
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            if (etSearch.visibility == View.VISIBLE) {
                imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
                etSearch.visibility = View.GONE
                tvTitle.visibility = View.VISIBLE
                tvBookCount.visibility = View.VISIBLE
                btnImport.visibility = View.VISIBLE
                btnToggleTheme.visibility = View.VISIBLE
                btnToggleViewMode.visibility = View.VISIBLE
                btnSearchToggle.animate().rotation(0f).setDuration(300).start()
                etSearch.setQuery("", false)
                currentSearchQuery = ""
                viewModel.setSearchQuery("")
                filterAndApplyBooks()
            } else {
                etSearch.visibility = View.VISIBLE
                tvTitle.visibility = View.GONE
                tvBookCount.visibility = View.GONE
                btnImport.visibility = View.GONE
                btnToggleTheme.visibility = View.GONE
                btnToggleViewMode.visibility = View.GONE
                btnSearchToggle.animate().rotation(90f).setDuration(300).start()
                
                etSearch.isIconified = false
                val searchEditText = etSearch.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                searchEditText?.requestFocus() ?: etSearch.requestFocus()
                
                val showKeyboardAction = Runnable {
                    val targetView = searchEditText ?: etSearch
                    targetView.requestFocus()
                    imm?.showSoftInput(targetView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                (searchEditText ?: etSearch).post(showKeyboardAction)
                (searchEditText ?: etSearch).postDelayed(showKeyboardAction, 100)
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

        // Theme Toggle Button
        setBounceAnimation(btnToggleTheme)
        updateThemeButtonState()

        btnToggleTheme.setOnClickListener {
            toggleTheme()
        }
        btnToggleTheme.setOnLongClickListener {
            showThemePopupMenu()
            true
        }

        // Empty state Auto-Scan action
        setBounceAnimation(btnEmptyStateScan)
        btnEmptyStateScan.setOnClickListener {
            checkPermissionsAndScan()
        }

        // Empty state Recovery action
        setBounceAnimation(btnRecoverLibrary)
        btnRecoverLibrary.setOnClickListener {
            viewModel.cancelAllScanningTasks()
            viewModel.clearScanCache()
            viewModel.resetLibrary()
            checkPermissionsAndScan()
        }

        // Swipe refresh layout manual scan trigger
        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
            if (viewModel.scanState.value.isScanning) {
                CustomToast.show(requireContext(), "Сканирование уже выполняется", android.widget.Toast.LENGTH_SHORT)
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
        viewLifecycleOwner.lifecycleScope.launch {
            com.nightread.app.data.SettingsManager.settingsChanged.collect {
                updateThemeButtonState()
            }
        }

        // Observe Books Stream
        val booksFlow = if (filterType == "reading") {
            viewModel.loadReadingBooks()
        } else {
            viewModel.allBooks
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // Artificial delay to show shimmer for better UX as Room loads extremely fast
            if (!com.nightread.app.MainActivity.isSplashActive) {
                kotlinx.coroutines.delay(800)
            }
            booksFlow.distinctUntilChanged().collectLatest { books ->
                allBooksList = books
                filterAndApplyBooks()
            }
        }

        // Observe Scan Progress state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
                updateScanUI(state)
                if (!state.isScanning) {
                    scanAddedCount = 0
                    if (::adapter.isInitialized) {
                        adapter.flushBuffer()
                    }
                }
            }
        }
    }

    private fun showScanProgressWithFadeIn() {
        if (layoutScanProgress.visibility != View.VISIBLE) {
            layoutScanProgress.alpha = 0f
            layoutScanProgress.visibility = View.VISIBLE
            layoutScanProgress.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null)
                .start()
        } else {
            layoutScanProgress.alpha = 1f
        }
    }

    private fun showNewBooksBannerWithFadeIn() {
        if (layoutNewBooksBanner.visibility != View.VISIBLE) {
            layoutNewBooksBanner.alpha = 0f
            layoutNewBooksBanner.visibility = View.VISIBLE
            layoutNewBooksBanner.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null)
                .start()
        } else {
            layoutNewBooksBanner.alpha = 1f
        }
    }

    private fun updateScanUI(state: com.nightread.app.service.ScanState) {
        val active = state.isScanning
        
        if (::headerProgressBar.isInitialized) {
            headerProgressBar.visibility = if (active) View.VISIBLE else View.GONE
        }
        
        if (::swipeRefresh.isInitialized) {
            swipeRefresh.isRefreshing = false
        }
        
        if (active) {
            wasScanning = true
            isScanCompletionDismissed = false
            showScanProgressWithFadeIn()
            progressBarSpinner.visibility = View.GONE
            context?.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)?.edit()
                ?.putBoolean("no_books_banner_dismissed", false)
                ?.apply()
        } else {
            progressBarSpinner.visibility = View.GONE
            
            if (wasScanning) {
                wasScanning = false
                if (state.status.isNotBlank()) {
                    context?.let { ctx ->
                        CustomToast.show(ctx, state.status, android.widget.Toast.LENGTH_SHORT)
                    }
                }
                // Check new books count
                lifecycleScope.launch {
                    val ctx = context ?: return@launch
                    val db = AppDatabase.getDatabase(ctx)
                    val newBooks = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try { db.bookDao().getNewBooks() } catch (e: Exception) { emptyList() }
                    }
                    if (!isAdded) return@launch
                    if (newBooks.isNotEmpty()) {
                        val prefs = ctx.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
                        val shownSha1s = prefs.getStringSet("shown_new_books_sha1", emptySet()) ?: emptySet()
                        val unseenBooks = newBooks.filter { it.sha1 !in shownSha1s }
                        
                        if (unseenBooks.isNotEmpty()) {
                            showNewBooksBannerWithFadeIn()
                            tvNewBooksCount.text = "Найдено новых книг: ${newBooks.size}"
                            hideBannerHandler.removeCallbacks(hideBannerRunnable)
                            hideBannerHandler.postDelayed(hideBannerRunnable, 2000)
                            
                            val updatedShown = shownSha1s.toMutableSet().apply {
                                addAll(newBooks.map { it.sha1 })
                            }
                            prefs.edit().putStringSet("shown_new_books_sha1", updatedShown).apply()
                        } else {
                            layoutNewBooksBanner.visibility = View.GONE
                        }
                    } else {
                        layoutNewBooksBanner.visibility = View.GONE
                    }
                }
            }

            if (state.status.isBlank() || isScanCompletionDismissed) {
                layoutScanProgress.visibility = View.GONE
            }
        }
        
        if (state.status.isNotBlank()) {
            if (active) {
                scanDismissJob?.cancel()
                scanDismissJob = null
                showScanProgressWithFadeIn()
                if (isSwipeRescanInProgress) {
                    tvScanStatus.text = "Обновление: ${state.status}"
                } else {
                    tvScanStatus.text = state.status
                }
            } else {
                if (!isScanCompletionDismissed) {
                    showScanProgressWithFadeIn()
                    if (isSwipeRescanInProgress) {
                        tvScanStatus.text = "Обновление: ${state.status}"
                    } else {
                        tvScanStatus.text = state.status
                    }
                    
                    scanDismissJob?.cancel()
                    scanDismissJob = viewLifecycleOwner.lifecycleScope.launch {
                        kotlinx.coroutines.delay(2000)
                        if (isAdded) {
                            isScanCompletionDismissed = true
                            layoutScanProgress.visibility = View.GONE
                        }
                    }
                } else {
                    layoutScanProgress.visibility = View.GONE
                }
            }
            
            if (state.status.contains("Job was cancelled", ignoreCase = true)) {
                if (!isJobCancelledDialogShown) {
                    isJobCancelledDialogShown = true
                    showJobCancelledDialog()
                }
            } else {
                isJobCancelledDialogShown = false
            }
            
            if (state.status.startsWith("Error", ignoreCase = true) || state.status.startsWith("Ошибка", ignoreCase = true)) {
                context?.let { ctx ->
                    CustomToast.show(ctx, state.status)
                }
            }
        }
        
        updateProgressValues(state.totalFiles, state.processedFiles)
        filterAndApplyBooks()
        if (!active) {
            if (::adapter.isInitialized) {
                adapter.flushBuffer()
            }
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
        val filteredByFormat = viewModel.repository.filterBooksByFormat(books, false)
        var filtered = filteredByFormat
        
        filtered = when (filterType) {
            "reading" -> filtered.filter { book -> 
                val percent = if (book.totalCharacters > 0) {
                    val calculated = ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
                    if (calculated >= 98) 100 else calculated
                } else {
                    0
                }
                book.lastReadTime > 0 && percent < 100
            }
            "read" -> filtered.filter { book -> 
                val percent = if (book.totalCharacters > 0) {
                    val calculated = ((book.currentProgressChar.toFloat() / book.totalCharacters) * 100).toInt().coerceIn(0, 100)
                    if (calculated >= 98) 100 else calculated
                } else {
                    0
                }
                percent >= 100
            }
            else -> filtered
        }

        if (currentSearchQuery.isNotBlank()) {
            val query = currentSearchQuery.trim()
            filtered = filtered.filter { book ->
                book.title.contains(query, ignoreCase = true) ||
                        (book.author ?: "").contains(query, ignoreCase = true)
            }
        }
        if (filterType == "reading") {
            return filtered.sortedWith(
                compareByDescending<BookEntity> { it.lastReadTime }
                    .thenByDescending { it.dateAdded }
                    .thenBy { it.title }
            )
        }
        return viewModel.sortBooks(filtered)
    }

    private fun showSortDialog() {
        val options = arrayOf(
            "По названию (А — Я)",
            "По названию (Я — А)",
            "По автору (А — Я)",
            "По автору (Я — А)",
            "По дате добавления (новые сверху)",
            "По дате добавления (старые сверху)",
            "По прогрессу (от большего)",
            "По прогрессу (от меньшего)"
        )

        val sortKeys = arrayOf(
            com.nightread.app.data.SettingsManager.SORT_TITLE_ASC,
            com.nightread.app.data.SettingsManager.SORT_TITLE_DESC,
            com.nightread.app.data.SettingsManager.SORT_AUTHOR_ASC,
            com.nightread.app.data.SettingsManager.SORT_AUTHOR_DESC,
            com.nightread.app.data.SettingsManager.SORT_DATE_DESC,
            com.nightread.app.data.SettingsManager.SORT_DATE_ASC,
            com.nightread.app.data.SettingsManager.SORT_PROGRESS_DESC,
            com.nightread.app.data.SettingsManager.SORT_PROGRESS_ASC
        )

        val ctx = context ?: return
        val currentSort = com.nightread.app.data.SettingsManager.getSortOption(ctx)
        val selectedIndex = sortKeys.indexOf(currentSort).let { if (it >= 0) it else 4 }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx, R.style.Theme_NightRead_Dialog)
            .setTitle("Сортировка книг")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val selectedKey = sortKeys[which]
                viewModel.setSortOption(selectedKey)
                filterAndApplyBooks()
                CustomToast.show(ctx, "Сортировка: ${options[which]}")
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
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

        val isScanning = viewModel.scanState.value.isScanning || viewModel.isScanning
        adapter.updateData(filtered, isScanning = isScanning)
        updateBookCount(filtered.size)

        // Preload top recent books in background
        context?.let { ctx ->
            filtered.take(3).forEach { book ->
                com.nightread.app.data.BookPreloader.preload(ctx, book.sha1, book.filePath)
            }
        }

        if (filtered.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            if (::progressBarEmptyState.isInitialized) {
                progressBarEmptyState.visibility = View.GONE
            }
            ivEmptyIllustration.visibility = View.VISIBLE
            btnEmptyStateScan.visibility = View.VISIBLE
            btnRecoverLibrary.visibility = if (!viewModel.isScanning) View.VISIBLE else View.GONE
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
                CustomToast.show(requireContext(), "Книга удалена")
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

    private fun showJobCancelledDialog() {
        if (!isAdded) return
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Сканирование прервано")
            .setMessage("Предыдущее сканирование было прервано. Это могло повредить кэш библиотеки, из-за чего книги пропускаются.\n\nРекомендуется очистить кэш сканирования и запустить полное сканирование заново.")
            .setPositiveButton("Очистить кэш и пересканировать") { _, _ ->
                viewModel.clearScanCache()
                viewModel.cancelAllScanningTasks()
                isSwipeRescanInProgress = false
                checkPermissionsAndScan()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Переключает тему приложения с выведением подсказки и обновлением иконки.
     */
    fun toggleTheme() {
        val ctx = context ?: return
        val isNight = ThemeHelper.shouldBeNightMode(ctx)
        val newNight = !isNight
        val newThemeStr = if (newNight) "dark" else "light"

        SettingsManager.setAppAutoThemeEnabled(ctx, false)
        SettingsManager.setTheme(ctx, newThemeStr)
        ThemeHelper.applyTheme(ctx)

        val message = if (newNight) "Включена тёмная тема" else "Включена светлая тема"
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        updateThemeButtonState()
        activity?.recreate()
    }

    private fun showThemePopupMenu() {
        val ctx = context ?: return
        if (!::btnToggleTheme.isInitialized) return
        val popup = androidx.appcompat.widget.PopupMenu(ctx, btnToggleTheme)
        popup.menu.add(0, 1, 0, "☀️ Светлая тема")
        popup.menu.add(0, 2, 1, "🌙 Тёмная тема")
        popup.menu.add(0, 3, 2, "🌀 Сюрреализм Дали")
        popup.menu.add(0, 4, 3, "⚙️ Системная тема (авто)")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    SettingsManager.setAppAutoThemeEnabled(ctx, false)
                    SettingsManager.setTheme(ctx, "light")
                    ThemeHelper.applyTheme(ctx)
                    Toast.makeText(ctx, "Включена светлая тема", Toast.LENGTH_SHORT).show()
                    activity?.recreate()
                }
                2 -> {
                    SettingsManager.setAppAutoThemeEnabled(ctx, false)
                    SettingsManager.setTheme(ctx, "dark")
                    ThemeHelper.applyTheme(ctx)
                    Toast.makeText(ctx, "Включена тёмная тема", Toast.LENGTH_SHORT).show()
                    activity?.recreate()
                }
                3 -> {
                    SettingsManager.setAppAutoThemeEnabled(ctx, false)
                    SettingsManager.setTheme(ctx, "dali")
                    ThemeHelper.applyTheme(ctx)
                    Toast.makeText(ctx, "Включена тема «Сюрреализм Дали»", Toast.LENGTH_SHORT).show()
                    activity?.recreate()
                }
                4 -> {
                    SettingsManager.setAppAutoThemeEnabled(ctx, true)
                    ThemeHelper.applyTheme(ctx)
                    Toast.makeText(ctx, "Включена автоматическая тема", Toast.LENGTH_SHORT).show()
                    activity?.recreate()
                }
            }
            updateThemeButtonState()
            true
        }
        popup.show()
    }

    private fun updateThemeButtonState() {
        if (!::btnToggleTheme.isInitialized) return
        val ctx = context ?: return
        val isAuto = SettingsManager.isAppAutoThemeEnabled(ctx)
        val isNight = ThemeHelper.shouldBeNightMode(ctx)

        btnToggleTheme.isEnabled = true
        btnToggleTheme.alpha = 1.0f

        if (isAuto) {
            btnToggleTheme.setIconResource(R.drawable.ic_theme_auto)
            btnToggleTheme.setIconTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFC107")))
            val desc = "Тема: Авто (системная). Нажмите для переключения или зажмите для выбора"
            btnToggleTheme.contentDescription = desc
            androidx.appcompat.widget.TooltipCompat.setTooltipText(btnToggleTheme, desc)
        } else if (isNight) {
            btnToggleTheme.setIconResource(R.drawable.ic_theme_sun)
            btnToggleTheme.setIconTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFD54F")))
            val desc = "Тема: Тёмная. Нажмите для светлой темы или зажмите для выбора"
            btnToggleTheme.contentDescription = desc
            androidx.appcompat.widget.TooltipCompat.setTooltipText(btnToggleTheme, desc)
        } else {
            btnToggleTheme.setIconResource(R.drawable.ic_theme_moon)
            btnToggleTheme.setIconTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#7E57C2")))
            val desc = "Тема: Светлая. Нажмите для тёмной темы или зажмите для выбора"
            btnToggleTheme.contentDescription = desc
            androidx.appcompat.widget.TooltipCompat.setTooltipText(btnToggleTheme, desc)
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { GalaxyBgHelper.applyBackground(it) }
        updateThemeButtonState()
        filterAndApplyBooks()
    }
}
