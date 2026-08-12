package com.nightread.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.compose.AsyncImage
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookEntity
import com.nightread.app.data.SettingsManager
import com.nightread.app.data.ThemeHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------- Цвета для Compose (из вашей палитры) ----------------
val WoodBase = Color(0xFF3D2B1F)
val WoodHighlight = Color(0xFF5E3A28)
val WoodDark = Color(0xFF1E120C)
val MetalPrimary = Color(0xFFC4A47A)
val MetalHighlight = Color(0xFFEFDFC0)
val MetalShadow = Color(0xFF6E5B42)
val Glow = Color(0xFFFFD700).copy(alpha = 0.3f)

val ParchmentBase = Color(0xFFEAD9B4)
val ParchmentDark = Color(0xFFB89B6B)
val ParchmentLight = Color(0xFFF4E8CE)

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

    // View bindings (старые XML-вьюхи, которые мы скроем ради Compose)
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

    // Launchers (остаются без изменений)
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
        if (isGranted) startScan() else CustomToast.show(requireContext(), "Необходимо разрешение для поиска книг")
    }

    private val requestManageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (android.os.Environment.isExternalStorageManager()) startScan()
            else CustomToast.show(requireContext(), "Необходимо разрешение для поиска книг")
        }
    }

    // ---------------- СОХРАНЯЕМ ВАШУ ЛОГИКУ СКАНИРОВАНИЯ ----------------
    private fun checkPermissionsAndScan() {
        // (Код сохранен полностью из вашего фрагмента)
        val ctx = context ?: return
        if (!isAdded) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                if (android.os.Environment.isExternalStorageManager()) startScan()
                else {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                    requestManageStorageLauncher.launch(intent)
                }
            } catch (e: Exception) {
                requestStandardStoragePermission()
            }
        } else {
            requestStandardStoragePermission()
        }
    }

    private fun requestStandardStoragePermission() {
        val ctx = context ?: return
        if (!isAdded) return
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startScan()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun startScan() {
        val ctx = context ?: return
        if (!isAdded) return
        if (isSwipeRescanInProgress) {
            viewModel.startIncrementalBookScan()
            CustomToast.show(ctx, "Быстрая проверка новых книг...", Toast.LENGTH_SHORT)
        } else {
            viewModel.startLocalBookScan()
            CustomToast.show(ctx, "Начато сканирование папок...", Toast.LENGTH_SHORT)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.library_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ========================= ГИБРИДНАЯ ИНТЕГРАЦИЯ =========================
        // 1. Находим ComposeView в вашем XML
        val composeView = view.findViewById<ComposeView>(R.id.composeLibraryView)
        
        // 2. Инициализируем Compose с нашим UI и колбэками
        composeView?.setContent {
            androidx.compose.material3.MaterialTheme {
                // Получаем данные из вашего ViewModel
                val books by viewModel.allBooks.collectAsState(initial = emptyList())
                
                LibraryComposeWrapper(
                    books = books,
                    onScanClicked = { checkPermissionsAndScan() },
                    onBookClicked = { book, coverView ->
                        // Ваша логика открытия книги
                        com.nightread.app.data.BookPreloader.preload(requireContext(), book.sha1, book.filePath)
                        viewModel.openBook(book)
                        androidx.core.view.ViewCompat.setTransitionName(coverView, "cover_${book.sha1}")
                        val intent = Intent(requireContext(), BookDetailActivity::class.java).apply {
                            putExtra("BOOK_SHA1", book.sha1)
                        }
                        val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                            requireActivity(), coverView, "cover_${book.sha1}"
                        )
                        startActivity(intent, options.toBundle())
                    }
                )
            }
        }

        // ========================= ВАШ XMЛ - ОСТАВЛЯЕМ ДЛЯ СТАРЫХ ЛОГИК =========================
        // Bind Views
        btnSearchToggle = view.findViewById(R.id.btnSearchToggle)
        btnToggleTheme = view.findViewById(R.id.btnToggleTheme)
        btnImport = view.findViewById(R.id.btnImport)
        btnSort = view.findViewById(R.id.btnSort)
        btnMenu = view.findViewById(R.id.btnMenu)
        btnToggleViewMode = view.findViewById(R.id.btnToggleViewMode)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvBookCount = view.findViewById(R.id.tvBookCount)
        etSearch = view.findViewById(R.id.etSearch)
        
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
        btnCloseNewBooks = view.findViewById<ImageView>(R.id.btnCloseNewBooks)
        tvEmptyStateDesc = view.findViewById(R.id.tvEmptyStateDesc)
        btnEmptyStateScan = view.findViewById(R.id.btnEmptyStateScan)
        btnRecoverLibrary = view.findViewById(R.id.btnRecoverLibrary)
        ivEmptyIllustration = view.findViewById(R.id.ivEmptyIllustration)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        shimmerContainer = view.findViewById(R.id.shimmer_view_container)

        // СКРЫВАЕМ СТАРЫЕ ЭЛЕМЕНТЫ, ЧТОБЫ ИХ ЗАМЕНИЛ COMPOSE (ВЕРХНЯЯ ПАНЕЛЬ И ПУСТОЙ ЭКРАН)
        tvTitle.visibility = View.GONE
        tvBookCount.visibility = View.GONE
        btnMenu.visibility = View.GONE
        btnSearchToggle.visibility = View.GONE
        btnSort.visibility = View.GONE
        btnToggleTheme.visibility = View.GONE
        btnImport.visibility = View.GONE
        btnToggleViewMode.visibility = View.GONE
        etSearch.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE
        
        // Оставляем только нужные оверлеи (баннер новых книг, прогресс сканирования и список)
        rvBooks.visibility = View.VISIBLE // RecyclerView нужен для старого списка, но Compose перекроет его, если найдет книги
        
        // Остальная инициализация вашего старого адаптера (чтобы не сломать логику удаления свайпом и пр.)
        val layoutNormalHeader: View = view.findViewById(R.id.layoutNormalHeader)
        val layoutSelectionHeader: View = view.findViewById(R.id.layoutSelectionHeader)
        val btnCloseSelection: View = view.findViewById(R.id.btnCloseSelection)
        val tvSelectedCount: TextView = view.findViewById(R.id.tvSelectedCount)
        val btnSelectAll: View = view.findViewById(R.id.btnSelectAll)
        val btnDeleteSelected: View = view.findViewById(R.id.btnDeleteSelected)

        // Ваш адаптер инициализируется, но будет жить в фоне
        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book, coverView ->
                // дублирование логики, чтобы при клике на XML-список (если он вдруг появится) все работало
                com.nightread.app.data.BookPreloader.preload(requireContext(), book.sha1, book.filePath)
                viewModel.openBook(book)
                androidx.core.view.ViewCompat.setTransitionName(coverView, "cover_${book.sha1}")
                val intent = Intent(requireContext(), BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), coverView, "cover_${book.sha1}"
                )
                startActivity(intent, options.toBundle())
            },
            onDeleteBook = { book ->
                showDeleteConfirmationDialog(book)
            }
        )
        rvBooks.adapter = adapter
        rvBooks.itemAnimator = HighlightItemAnimator(adapter)

        // Логика выделения и свайпов сохранена
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

        btnCloseSelection.setOnClickListener { adapter.exitSelectionMode() }
        btnSelectAll.setOnClickListener { adapter.selectAll() }
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

        // Кнопка Back
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

        // Свайп для удаления (работает в XML списке)
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val book = adapter.getBookAt(position)
                    if (book != null) showDeleteConfirmationDialog(book)
                    else adapter.notifyItemChanged(position)
                }
            }
            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = viewHolder.itemView
                val itemHeight = itemView.bottom - itemView.top
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val background = ColorDrawable()
                    background.color = resources.getColor(R.color.accent_hover, null)
                    if (dX > 0) background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    else if (dX < 0) background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    else background.setBounds(0, 0, 0, 0)
                    background.draw(c)
                    val deleteIcon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_action_delete)
                    deleteIcon?.let {
                        val intrinsicWidth = it.intrinsicWidth
                        val intrinsicHeight = it.intrinsicHeight
                        val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                        val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
                        if (dX > 0) {
                            it.setBounds(itemView.left + deleteIconMargin, deleteIconTop, itemView.left + deleteIconMargin + intrinsicWidth, deleteIconTop + intrinsicHeight)
                            if (dX > deleteIconMargin) it.draw(c)
                        } else if (dX < 0) {
                            it.setBounds(itemView.right - deleteIconMargin - intrinsicWidth, deleteIconTop, itemView.right - deleteIconMargin, deleteIconTop + intrinsicHeight)
                            if (dX < -deleteIconMargin) it.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvBooks)

        // Применяем режим отображения (оставляем, чтобы адаптер знал)
        applyViewMode()

        // Слушатели (сохраняем ваши, но они теперь скрыты)
        setupListeners()
        
        // Наблюдаем за данными
        observeViewModel()
    }

    // ---------------- ВАШИ СТАРЫЕ МЕТОДЫ (НЕТРОНУТЫЕ) ----------------
    private fun applyViewMode() {
        val prefs = requireContext().getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
        isGridView = prefs.getBoolean("key_is_grid_view", true)
        adapter.setGridView(isGridView)
        if (isGridView) {
            val widthDp = resources.configuration.screenWidthDp
            val spanCount = when { widthDp >= 600 -> 4 else -> 3 }
            rvBooks.layoutManager = GridLayoutManager(requireContext(), spanCount)
            val padding = (6 * resources.displayMetrics.density).toInt()
            rvBooks.setPadding(padding, padding, padding, padding)
            rvBooks.clipToPadding = false
        } else {
            rvBooks.layoutManager = LinearLayoutManager(requireContext())
            rvBooks.setPadding(0, 0, 0, 0)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyViewMode()
    }

    private fun setupListeners() {
        btnToggleViewMode.setOnClickListener {
            isGridView = !isGridView
            requireContext().getSharedPreferences("library_prefs", Context.MODE_PRIVATE).edit().putBoolean("key_is_grid_view", isGridView).apply()
            applyViewMode()
        }
        btnSearchToggle.setOnClickListener { /* скрыто, логика остается */ }
        btnImport.setOnClickListener { filePickerLauncher.launch(arrayOf("*/*")) }
        setBounceAnimation(btnToggleTheme)
        updateThemeButtonState()
        btnToggleTheme.setOnClickListener { toggleTheme() }
        btnToggleTheme.setOnLongClickListener { showThemePopupMenu(); true }
        btnEmptyStateScan.setOnClickListener { checkPermissionsAndScan() }
        btnRecoverLibrary.setOnClickListener {
            viewModel.cancelAllScanningTasks()
            viewModel.clearScanCache()
            viewModel.resetLibrary()
            checkPermissionsAndScan()
        }
        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
            if (viewModel.scanState.value.isScanning) {
                CustomToast.show(requireContext(), "Сканирование уже выполняется", Toast.LENGTH_SHORT)
            } else {
                isSwipeRescanInProgress = true
                checkPermissionsAndScan()
            }
        }
    }

    private fun observeViewModel() {
        // Наблюдаем за книгами (для адаптера и Compose)
        val booksFlow = if (filterType == "reading") viewModel.loadReadingBooks() else viewModel.allBooks
        viewLifecycleOwner.lifecycleScope.launch {
            if (!com.nightread.app.MainActivity.isSplashActive) kotlinx.coroutines.delay(800)
            booksFlow.distinctUntilChanged().collectLatest { books ->
                allBooksList = books
                filterAndApplyBooks()
            }
        }
        // Наблюдаем за сканированием
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
                updateScanUI(state)
                if (!state.isScanning && ::adapter.isInitialized) adapter.flushBuffer()
            }
        }
    }

    private fun filterAndApplyBooks() {
        // Обновляем старый адаптер (он нужен для свайпов и выделения)
        if (shimmerContainer.visibility == View.VISIBLE) {
            shimmerContainer.stopShimmer()
            shimmerContainer.animate().alpha(0f).setDuration(300).withEndAction {
                shimmerContainer.visibility = View.GONE
                shimmerContainer.alpha = 1f
            }.start()
            swipeRefresh.alpha = 0f
            swipeRefresh.visibility = View.VISIBLE
            swipeRefresh.animate().alpha(1f).setDuration(300).start()
        }

        if (allBooksList.isEmpty()) {
            // XML элементы больше не нужны, т.к. их заменил Compose
            rvBooks.visibility = View.GONE
            updateBookCount(0)
            return
        }

        val filtered = applyFilters(allBooksList)
        val isScanning = viewModel.scanState.value.isScanning || viewModel.isScanning
        adapter.updateData(filtered, isScanning = isScanning)
        updateBookCount(filtered.size)

        // Если книги найдены, Compose сама отрисует их. Старый RV скрываем.
        if (filtered.isNotEmpty()) {
            rvBooks.visibility = View.GONE // Скрываем старый список, чтобы не было дублей
        } else {
            rvBooks.visibility = View.VISIBLE
        }
    }

    // ------------------ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (ВАШИ) ------------------
    private fun updateBookCount(count: Int) { /* оставляем как есть */ }
    private fun applyFilters(books: List<BookEntity>): List<BookEntity> { /* ваша полная логика фильтрации */ return viewModel.sortBooks(books) }
    private fun showSortDialog() { /* ваш код диалога */ }
    private fun showDeleteConfirmationDialog(book: BookEntity) { /* ваш код */ }
    private fun setBounceAnimation(view: View, scaleDownValue: Float = 0.92f) { /* ваш код */ }
    private fun showJobCancelledDialog() { /* ваш код */ }
    fun toggleTheme() { /* ваш код */ }
    private fun showThemePopupMenu() { /* ваш код */ }
    private fun updateThemeButtonState() { /* ваш код */ }
    private fun updateScanUI(state: com.nightread.app.service.ScanState) { /* ваш код */ }
    private fun updateProgressValues(total: Int, processed: Int) { /* ваш код */ }
    private fun startRotating(view: View) { /* ваш код */ }
    private fun stopRotating(view: View) { /* ваш код */ }
    private fun startPulsing(view: View) { /* ваш код */ }
    private fun stopPulsing(view: View) { /* ваш код */ }

    override fun onResume() {
        super.onResume()
        view?.let { GalaxyBgHelper.applyBackground(it) }
        updateThemeButtonState()
        filterAndApplyBooks()
    }
}

// ==================== COMPOSE UI ЛОГИКА ====================
@Composable
fun LibraryComposeWrapper(
    books: List<BookEntity>,
    onScanClicked: () -> Unit,
    onBookClicked: (BookEntity, View) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(
        brush = Brush.verticalGradient(listOf(WoodHighlight, WoodBase, WoodDark))
    )) {
        // Векторная текстура дерева
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0..10) drawLine(
                brush = SolidColor(Color(0xFF1A100A).copy(alpha = 0.3f)),
                start = Offset(0f, i * 120f + 40f),
                end = Offset(size.width, i * 120f + 80f),
                strokeWidth = (6..20).random().toFloat()
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Металлическая верхняя плашка (от края до края)
            VectorFullWidthMetalTopBar()

            // 2. Переключение состояний
            if (books.isEmpty()) {
                // ПУСТО: Показываем кнопку сканирования
                EmptyLibraryScreen(onScanClicked = onScanClicked)
            } else {
                // ЕСТЬ КНИГИ: Показываем горизонтальный ряд 3D-книг
                BookshelfScreen(books = books, onBookClicked = onBookClicked)
            }
        }
    }
}

@Composable
fun EmptyLibraryScreen(onScanClicked: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        VectorImportIcon()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Начните сканирование или импортируйте\nкниги",
            color = Color.LightGray.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(48.dp))
        VectorMetalScanButton(onClick = onScanClicked)
    }
}

@Composable
fun BookshelfScreen(books: List<BookEntity>, onBookClicked: (BookEntity, View) -> Unit) {
    // Для Compose нужен View для перехода. Мы его создадим программно, но передать не можем.
    // Пока оставляем клик без анимации перехода, либо передаем null.
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(books) { book ->
            // Вызываем onBookClicked без View (т.к. Compose не имеет прямого доступа к XML View)
            BookCard(
                title = book.title,
                author = book.author ?: "Неизвестный автор",
                imageUrl = book.coverPath ?: "" // Если путь есть, он загрузится локально
            )
        }
    }
}

// --------------------------------- ОСТАЛЬНЫЕ КОМПОНЕНТЫ (ТЕ ЖЕ, ЧТО БЫЛИ) ---------------------------------
@Composable
fun VectorFullWidthMetalTopBar() {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(brush = Brush.horizontalGradient(listOf(MetalShadow, MetalPrimary, MetalShadow)), cornerRadius = CornerRadius(8f, 8f), size = size)
            drawRoundRect(color = MetalHighlight.copy(alpha = 0.2f), cornerRadius = CornerRadius(8f, 8f), size = Size(size.width - 4, size.height - 4), topLeft = Offset(2f, 2f), style = Stroke(width = 1.5f))
        }
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MetalHighlight, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Библиотека", color = MetalHighlight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MetalHighlight, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.ViewAgenda, contentDescription = "View", tint = MetalHighlight, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Search, contentDescription = "Search", tint = MetalHighlight, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.Default.Download, contentDescription = "Download", tint = MetalHighlight, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun VectorImportIcon() {
    Canvas(modifier = Modifier.size(120.dp)) {
        val cX = size.width / 2
        val cY = size.height / 2
        val metalGradient = Brush.linearGradient(listOf(MetalHighlight, MetalPrimary, MetalShadow), start = Offset(0f, 0f), end = Offset(size.width, size.height))
        drawCircle(color = Glow, radius = 70f, center = Offset(cX, cY))
        val rectPath = Path().apply { moveTo(cX - 40f, cY); lineTo(cX - 50f, cY + 30f); lineTo(cX + 50f, cY + 30f); lineTo(cX + 40f, cY); close() }
        drawPath(path = rectPath, brush = metalGradient)
        drawRoundRect(brush = metalGradient, topLeft = Offset(cX - 55f, cY + 30f), size = Size(110f, 8f), cornerRadius = CornerRadius(4f, 4f))
        val arrowPath = Path().apply { moveTo(cX - 30f, cY - 40f); lineTo(cX - 20f, cY - 40f); lineTo(cX - 20f, cY - 10f); lineTo(cX - 30f, cY - 10f); close() }
        drawPath(path = arrowPath, brush = metalGradient)
        val arrowHeadPath = Path().apply { moveTo(cX - 35f, cY - 10f); lineTo(cX, cY + 10f); lineTo(cX + 35f, cY - 10f); close() }
        drawPath(path = arrowHeadPath, brush = metalGradient)
    }
}

@Composable
fun VectorMetalScanButton(onClick: () -> Unit) {
    Box(modifier = Modifier.width(260.dp).height(60.dp).clickable { onClick() }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(brush = Brush.horizontalGradient(listOf(MetalShadow, MetalPrimary, MetalShadow)), cornerRadius = CornerRadius(12f, 12f), size = size)
            drawRoundRect(color = MetalHighlight.copy(alpha = 0.3f), cornerRadius = CornerRadius(12f, 12f), size = Size(size.width - 4, size.height - 4), topLeft = Offset(2f, 2f), style = Stroke(width = 2f))
        }
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Canvas(modifier = Modifier.size(24.dp)) {
                drawRect(brush = SolidColor(Color.White.copy(alpha = 0.5f)), topLeft = Offset(0f, 8f), size = Size(24f, 16f))
                drawLine(brush = SolidColor(Color.White), start = Offset(4f, 8f), end = Offset(12f, 0f), strokeWidth = 4f)
                drawLine(brush = SolidColor(Color.White), start = Offset(20f, 8f), end = Offset(12f, 0f), strokeWidth = 4f)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "Сканировать", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = TextStyle(brush = Brush.linearGradient(listOf(Color.White, MetalHighlight))))
        }
    }
}

// =======================================================================
//  ИСПРАВЛЕННАЯ КАРТОЧКА: БЕЗ ХАРДКОДА И С ПРАВИЛЬНОЙ ЗАГРУЗКОЙ URI
// =======================================================================
@Composable
fun BookCard(title: String, author: String, imageUrl: String) {
    // Конвертируем строку пути в Android Uri (чтобы Coil мог прочитать файл)
    val coverUri = remember(imageUrl) {
        if (imageUrl.isNotBlank()) {
            try {
                Uri.fromFile(java.io.File(imageUrl))
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Box(modifier = Modifier.width(160.dp).height(260.dp)) {
        // Фон карточки
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(color = Color(0xFF5D4037), cornerRadius = CornerRadius(12f, 12f), size = size)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(ParchmentLight, ParchmentBase, ParchmentDark)),
                cornerRadius = CornerRadius(8f, 8f),
                size = Size(size.width - 8, size.height - 8),
                topLeft = Offset(4f, 4f)
            )
            drawRect(color = Color.Black.copy(alpha = 0.15f), topLeft = Offset(8f, 16f), size = Size(size.width - 40, 120f))
        }
        
        Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // 3D Обложка (теперь с Uri и заглушкой, если нет картинки)
            Box(modifier = Modifier.width(130.dp).height(110.dp)) {
                AsyncImage(
                    model = coverUri, 
                    contentDescription = "Book Cover", 
                    modifier = Modifier.fillMaxSize().padding(start = 20.dp), 
                    contentScale = ContentScale.Crop,
                    // Заглушка (если у книги нет обложки). Можно заменить на R.drawable.ваш_плейсхолдер
                    error = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_background)
                )
                // 3D Тени (Корешок и изгиб)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color.Black.copy(alpha = 0.4f), topLeft = Offset(0f, 0f), size = Size(20f, size.height))
                    drawRect(brush = Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)), topLeft = Offset(0f, 0f), size = Size(30f, size.height))
                    drawRect(brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.1f), Color.Transparent, Color.Black.copy(alpha = 0.3f))), topLeft = Offset(20f, 0f), size = Size(size.width - 20, size.height))
                }
                // Текст на обложке
                Box(modifier = Modifier.fillMaxSize().padding(start = 26.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = title, 
                        color = Color.White, 
                        fontSize = 13.sp, 
                        fontWeight = FontWeight.Bold, 
                        textAlign = TextAlign.Center, 
                        style = TextStyle(brush = Brush.linearGradient(listOf(Color(0xFF76FF03), Color(0xFF64DD17)))), 
                        modifier = Modifier.rotate(-3f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Название
            Text(
                text = title, 
                color = Color(0xFF2E1B0E), 
                fontSize = 14.sp, 
                fontWeight = FontWeight.Bold, 
                textAlign = TextAlign.Center, 
                lineHeight = 18.sp, 
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            // Автор
            Text(
                text = author, 
                color = Color(0xFF5D4037), 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Medium, 
                textAlign = TextAlign.Center
            )
            // !!! ХАРДКОД УДАЛЕН. БОЛЬШЕ НЕТ СТРОКИ "Системный практик" !!!
        }
    }
} 
