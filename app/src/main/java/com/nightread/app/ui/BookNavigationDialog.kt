package com.nightread.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.nightread.app.R
import com.nightread.app.data.BookmarkDatabase
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookNavigationDialog : DialogFragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var pageChapters: View
    private lateinit var pageBookmarks: View
    private lateinit var pageQuotes: View

    private lateinit var rvChapters: RecyclerView
    private lateinit var layoutChaptersEmpty: View
    private lateinit var rvBookmarks: RecyclerView
    private lateinit var layoutBookmarksEmpty: View
    private lateinit var rvQuotes: RecyclerView
    private lateinit var layoutQuotesEmpty: View
    private lateinit var etSearchQuotes: android.widget.EditText
    private var allNotes: List<com.nightread.app.data.NoteEntity> = emptyList()

    private lateinit var navigationCardRoot: CardView
    private lateinit var navigationToolbar: View
    private lateinit var btnBack: ImageButton
    private lateinit var tvBookTitle: TextView
    private lateinit var dividerTabs: View

    private lateinit var viewModel: ReaderViewModel
    private lateinit var bookmarkAdapter: BookBookmarkAdapter
    private lateinit var noteAdapter: NoteAdapter
    private var bookSha1: String = ""
    private var activeTheme: String = "dark"
    private var initialTab: Int = 0

    companion object {
        private const val ARG_BOOK_SHA1 = "BOOK_SHA1"
        private const val ARG_INITIAL_TAB = "INITIAL_TAB"

        fun newInstance(bookSha1: String, initialTab: Int = 0): BookNavigationDialog {
            val fragment = BookNavigationDialog()
            val args = Bundle().apply {
                putString(ARG_BOOK_SHA1, bookSha1)
                putInt(ARG_INITIAL_TAB, initialTab)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.SettingsDialogStyle)
        bookSha1 = arguments?.getString(ARG_BOOK_SHA1) ?: ""
        initialTab = arguments?.getInt(ARG_INITIAL_TAB) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_book_navigation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setCanceledOnTouchOutside(true)

        val context = requireContext()
        activeTheme = SettingsManager.getTheme(context)
        viewModel = ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java)

        // Find Toolbar and Base views
        navigationCardRoot = view.findViewById(R.id.navigationCardRoot)
        navigationToolbar = view.findViewById(R.id.navigationToolbar)
        btnBack = view.findViewById(R.id.btnBack)
        btnBack.setOnClickListener { dismiss() }
        // btnMenu was removed
        tvBookTitle = view.findViewById(R.id.tvBookTitle)
        dividerTabs = view.findViewById(R.id.dividerTabs)

        // Set book title
        val book = viewModel.bookState.value
        tvBookTitle.text = book?.title ?: "Навигация"

        // Find Tab views
        tabLayout = view.findViewById(R.id.tabLayout)
        pageChapters = view.findViewById(R.id.pageChapters)
        pageBookmarks = view.findViewById(R.id.pageBookmarks)
        pageQuotes = view.findViewById(R.id.pageQuotes)

        // Initialize lists
        rvChapters = view.findViewById(R.id.rvChapters)
        layoutChaptersEmpty = view.findViewById(R.id.layoutChaptersEmpty)
        rvBookmarks = view.findViewById(R.id.rvBookmarks)
        layoutBookmarksEmpty = view.findViewById(R.id.layoutBookmarksEmpty)
        rvQuotes = view.findViewById(R.id.rvQuotes)
        layoutQuotesEmpty = view.findViewById(R.id.layoutQuotesEmpty)
        etSearchQuotes = view.findViewById(R.id.etSearchQuotes)

        rvChapters.layoutManager = LinearLayoutManager(context)
        rvBookmarks.layoutManager = LinearLayoutManager(context)
        rvQuotes.layoutManager = LinearLayoutManager(context)

        // Setup tabs (hidden but kept for compatibility/safe references)
        setupTabLayout()
        tabLayout.visibility = View.VISIBLE
        dividerTabs.visibility = View.VISIBLE

        // Setup Chapters logic
        setupChaptersList()

        // Setup Bookmarks logic
        setupBookmarksList()

        // Setup Notes logic
        setupQuotesList()

        // Apply theme-specific colors and backgrounds dynamically
        applyThemeColors(activeTheme, view)

        // Pre-select initial tab
        tabLayout.post {
            val tab = tabLayout.getTabAt(initialTab)
            tab?.select()
            switchPage(initialTab)
        }
    }

    private fun setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("ОГЛАВЛЕНИЕ"))
        tabLayout.addTab(tabLayout.newTab().setText("ЗАКЛАДКИ"))
        tabLayout.addTab(tabLayout.newTab().setText("ЦИТАТЫ"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { switchPage(it.position) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun switchPage(position: Int) {
        pageChapters.visibility = if (position == 0) View.VISIBLE else View.GONE
        pageBookmarks.visibility = if (position == 1) View.VISIBLE else View.GONE
        pageQuotes.visibility = if (position == 2) View.VISIBLE else View.GONE
    }

    private fun setupChaptersList() {
        val content = BookCache.content
        if (content.isEmpty() || BookCache.sha1 != bookSha1) {
            rvChapters.visibility = View.GONE
            layoutChaptersEmpty.visibility = View.VISIBLE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val chapterOffsets = mutableListOf<Int>()
            val chapterTitles = mutableListOf<String>()

            // 1. Try to find standard HTML heading tags <h1>, <h2>, <h3>, <h4>
            val headingRegex = Regex("<h([1-4])[^>]*>(.*?)</h\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val divSectionRegex = Regex("<div\\s+class\\s*=\\s*['\"]chapter-section['\"]>", RegexOption.IGNORE_CASE)

            val tempMatches = mutableListOf<Pair<Int, String>>()

            val headingMatches = headingRegex.findAll(content)
            for (match in headingMatches) {
                val offset = match.range.first
                val rawTitle = match.groupValues[2]
                val cleanTitle = cleanHtmlText(rawTitle)
                if (cleanTitle.isNotEmpty() && cleanTitle.length < 150) {
                    tempMatches.add(Pair(offset, cleanTitle))
                }
            }

            // 2. Also look for <div class='chapter-section'> divs and map them
            val sectionMatches = divSectionRegex.findAll(content)
            for (match in sectionMatches) {
                val offset = match.range.first
                if (tempMatches.none { Math.abs(it.first - offset) < 200 }) {
                    // Try to extract a title from the next 300 characters
                    val searchEnd = (offset + 300).coerceAtMost(content.length)
                    val searchArea = content.substring(offset, searchEnd)
                    val titleMatch = Regex("<h[1-4][^>]*>(.*?)</h[1-4]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(searchArea)
                    val title = if (titleMatch != null) {
                        cleanHtmlText(titleMatch.groupValues[1])
                    } else {
                        val paraMatch = Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(searchArea)
                        if (paraMatch != null) {
                            val cleanPara = cleanHtmlText(paraMatch.groupValues[1])
                            if (cleanPara.length > 50) cleanPara.take(47) + "..." else cleanPara
                        } else {
                            ""
                        }
                    }
                    if (title.isNotEmpty()) {
                        tempMatches.add(Pair(offset, title))
                    } else {
                        tempMatches.add(Pair(offset, "Глава ${tempMatches.size + 1}"))
                    }
                }
            }

            // 3. Fallback for plain text or books with no structural headings
            if (tempMatches.isEmpty()) {
                val paraRegex = Regex("<p[^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val paraMatches = paraRegex.findAll(content)
                for (match in paraMatches) {
                    val offset = match.range.first
                    val rawText = cleanHtmlText(match.groupValues[1])
                    if (rawText.startsWith("Глава", ignoreCase = true) || 
                        rawText.startsWith("Chapter", ignoreCase = true) || 
                        rawText.startsWith("Часть", ignoreCase = true) ||
                        rawText.startsWith("Раздел", ignoreCase = true) ||
                        rawText.startsWith("Оглавление", ignoreCase = true) ||
                        rawText.startsWith("Введение", ignoreCase = true) ||
                        rawText.startsWith("Пролог", ignoreCase = true) ||
                        rawText.startsWith("Эпилог", ignoreCase = true) ||
                        rawText.startsWith("Заключение", ignoreCase = true)) {
                        
                        val cleanTitle = if (rawText.length > 80) rawText.take(80) + "..." else rawText
                        tempMatches.add(Pair(offset, cleanTitle))
                    }
                }
            }

            // Sort chronologically by offset
            val sortedMatches = tempMatches.sortedBy { it.first }

            // Deduplicate matching close offsets
            val uniqueMatches = mutableListOf<Pair<Int, String>>()
            for (match in sortedMatches) {
                if (uniqueMatches.isEmpty() || match.first - uniqueMatches.last().first > 500) {
                    uniqueMatches.add(match)
                }
            }

            // 4. Ultimate synthetic fallback if still no chapters found
            if (uniqueMatches.isEmpty()) {
                val totalLength = content.length
                if (totalLength > 10000) {
                    val step = totalLength / 10
                    for (i in 0 until 10) {
                        val offset = i * step
                        uniqueMatches.add(Pair(offset, "Часть ${i + 1}"))
                    }
                } else {
                    uniqueMatches.add(Pair(0, "Начало книги"))
                }
            }

            for (match in uniqueMatches) {
                chapterOffsets.add(match.first)
                chapterTitles.add(match.second)
            }

            withContext(Dispatchers.Main) {
                if (chapterOffsets.isEmpty()) {
                    rvChapters.visibility = View.GONE
                    layoutChaptersEmpty.visibility = View.VISIBLE
                } else {
                    rvChapters.visibility = View.VISIBLE
                    layoutChaptersEmpty.visibility = View.GONE
                    rvChapters.adapter = ChapterNavigationAdapter(chapterOffsets, chapterTitles) { offset ->
                        val filePath = viewModel.bookState.value?.filePath ?: ""
                        val isWebViewBook = filePath.endsWith(".fb2", true) || 
                                           filePath.endsWith(".fb2.zip", true) || 
                                           filePath.endsWith(".zip", true) ||
                                           filePath.endsWith(".epub", true)
                        
                        val readerActivity = activity as? BookReaderActivity
                        if (isWebViewBook && readerActivity != null) {
                            val subContent = content.substring(0, offset.coerceAtMost(content.length))
                            val tagRegex = Regex("<(p|title|subtitle|h1|h2|h3|h4|h5|h6)(\\s+[^>]*|\\s*)>", RegexOption.IGNORE_CASE)
                            val pIndex = tagRegex.findAll(subContent).count()
                            readerActivity.navigateToParagraph(pIndex)
                        } else {
                            val pageIdx = viewModel.getPageForOffset(offset)
                            readerActivity?.loadPage(pageIdx)
                        }
                        dismiss()
                    }
                }
            }
        }
    }

    private fun cleanHtmlText(text: String): String {
        return text.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
            .replace("&#171;", "«")
            .replace("&#187;", "»")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun setupBookmarksList() {
        bookmarkAdapter = BookBookmarkAdapter(
            themeKey = activeTheme,
            onBookmarkClicked = { bookmark ->
                (activity as? BookReaderActivity)?.navigateToOffset(bookmark.charOffset)
                dismiss()
            },
            onBookmarkDeleteClicked = { bookmark ->
                confirmAndDeleteBookmark(bookmark)
            },
            onBookmarkLongClicked = { bookmark ->
                confirmAndDeleteBookmark(bookmark)
            }
        )
        rvBookmarks.adapter = bookmarkAdapter

        // Swipe gesture to delete bookmark
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val bookmark = bookmarkAdapter.getBookmarkAt(position)
                if (bookmark != null) {
                    confirmAndDeleteBookmark(bookmark) {
                        bookmarkAdapter.notifyItemChanged(position)
                    }
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvBookmarks)

        // Observe and load bookmarks from Database
        lifecycleScope.launch {
            val db = BookmarkDatabase.getDatabase(requireContext())
            db.bookmarkDao().getBookmarksForBook(bookSha1).collectLatest { bookmarks ->
                if (bookmarks.isEmpty()) {
                    rvBookmarks.visibility = View.GONE
                    layoutBookmarksEmpty.visibility = View.VISIBLE
                } else {
                    rvBookmarks.visibility = View.VISIBLE
                    layoutBookmarksEmpty.visibility = View.GONE
                    bookmarkAdapter.submitList(bookmarks)
                }
            }
        }
    }

    private fun setupQuotesList() {
        noteAdapter = NoteAdapter(
            onNoteClicked = { note ->
                val pageIdx = viewModel.getPageForOffset(note.charOffset)
                (activity as? BookReaderActivity)?.loadPage(pageIdx)
                dismiss()
            },
            onNoteDeleteClicked = { note ->
                confirmAndDeleteNote(note)
            }
        )
        rvQuotes.adapter = noteAdapter

        etSearchQuotes.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().lowercase()
                val filtered = allNotes.filter {
                    it.noteText.lowercase().contains(query) || it.selectedText.lowercase().contains(query) || it.bookTitle.lowercase().contains(query)
                }
                noteAdapter.submitList(filtered)
                layoutQuotesEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        })

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val note = noteAdapter.getNoteAt(position)
                if (note != null) {
                    confirmAndDeleteNote(note) {
                        noteAdapter.notifyItemChanged(position)
                    }
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvQuotes)

        lifecycleScope.launch {
            viewModel.getNotesForBook(bookSha1).collectLatest { notes ->
                allNotes = notes
                if (notes.isEmpty()) {
                    rvQuotes.visibility = View.GONE
                    layoutQuotesEmpty.visibility = View.VISIBLE
                } else {
                    rvQuotes.visibility = View.VISIBLE
                    layoutQuotesEmpty.visibility = View.GONE
                    noteAdapter.submitList(notes)
                }
            }
        }
    }

    private fun confirmAndDeleteNote(note: com.nightread.app.data.NoteEntity, onCancelled: () -> Unit = {}) {
        val context = requireContext()
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Удалить заметку?")
            .setMessage("Вы действительно хотите удалить эту заметку?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteNote(note.id)
                CustomToast.show(context, "Заметка удалена")
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
                onCancelled()
            }
            .setOnCancelListener {
                onCancelled()
            }
            .show()
    }

    private fun confirmAndDeleteBookmark(bookmark: BookmarkEntity, onCancelled: () -> Unit = {}) {
        val context = requireContext()
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Удалить закладку?")
            .setMessage("Вы действительно хотите удалить эту закладку?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = BookmarkDatabase.getDatabase(context)
                    db.bookmarkDao().deleteBookmark(bookmark)
                    withContext(Dispatchers.Main) {
                        CustomToast.show(context, "Закладка удалена")
                    }
                }
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
                onCancelled()
            }
            .setOnCancelListener {
                onCancelled()
            }
            .show()
    }

    private fun applyThemeColors(themeKey: String, rootView: View) {
        val cardBgHex: String
        val toolbarBgHex: String
        val accentHex: String
        val textPrimaryHex: String
        val textSecondaryHex: String
        val dividerHex: String

        when (themeKey) {
            "light", "beige" -> {
                cardBgHex = "#FAF6F0"
                toolbarBgHex = "#F0E7D8"
                accentHex = "#D35400"
                textPrimaryHex = "#2C3E50"
                textSecondaryHex = "#7F8C8D"
                dividerHex = "#E0D5C1"
            }
            "sepia", "sepia_contrast" -> {
                cardBgHex = "#F4ECD8"
                toolbarBgHex = "#E8DCBF"
                accentHex = "#8E44AD"
                textPrimaryHex = "#5B3A29"
                textSecondaryHex = "#8F7365"
                dividerHex = "#D5C5B5"
            }
            "contrast" -> {
                cardBgHex = "#000000"
                toolbarBgHex = "#121212"
                accentHex = "#FFFFFF"
                textPrimaryHex = "#FFFFFF"
                textSecondaryHex = "#AAAAAA"
                dividerHex = "#333333"
            }
            else -> { // "dark" or default
                cardBgHex = "#1A0D2A"
                toolbarBgHex = "#150924"
                accentHex = "#9B59B6"
                textPrimaryHex = "#E8D8F0"
                textSecondaryHex = "#B8A0C8"
                dividerHex = "#3A2A4E"
            }
        }

        val cardBgColor = Color.parseColor(cardBgHex)
        val toolbarBgColor = Color.parseColor(toolbarBgHex)
        val accentColor = Color.parseColor(accentHex)
        val textPrimaryColor = Color.parseColor(textPrimaryHex)
        val textSecondaryColor = Color.parseColor(textSecondaryHex)
        val dividerColor = Color.parseColor(dividerHex)

        // 1. Root and Toolbar Backgrounds
        navigationCardRoot.setCardBackgroundColor(cardBgColor)
        navigationToolbar.setBackgroundColor(toolbarBgColor)
        dividerTabs.setBackgroundColor(dividerColor)

        // 2. Toolbar Elements
        tvBookTitle.setTextColor(textPrimaryColor)
        btnBack.imageTintList = ColorStateList.valueOf(textPrimaryColor)

        // 3. TabLayout Colors
        tabLayout.setBackgroundColor(toolbarBgColor)
        tabLayout.setSelectedTabIndicatorColor(accentColor)
        tabLayout.setTabTextColors(textSecondaryColor, textPrimaryColor)

        // 4. Empty State Icons and Texts
        rootView.findViewById<TextView>(R.id.tvChaptersEmptyTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvChaptersEmptyMessage)?.setTextColor(textSecondaryColor)
        rootView.findViewById<ImageView>(R.id.ivChaptersEmptyIcon)?.imageTintList = ColorStateList.valueOf(accentColor)

        rootView.findViewById<TextView>(R.id.tvBookmarksEmptyTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvBookmarksEmptyMessage)?.setTextColor(textSecondaryColor)
        rootView.findViewById<ImageView>(R.id.ivBookmarksEmptyIcon)?.imageTintList = ColorStateList.valueOf(accentColor)

        rootView.findViewById<TextView>(R.id.tvQuotesEmptyTitle)?.setTextColor(textPrimaryColor)
        rootView.findViewById<TextView>(R.id.tvQuotesEmptyMessage)?.setTextColor(textSecondaryColor)
        rootView.findViewById<ImageView>(R.id.ivQuotesEmptyIcon)?.imageTintList = ColorStateList.valueOf(accentColor)
        rootView.findViewById<android.widget.EditText>(R.id.etSearchQuotes)?.let {
            it.setTextColor(textPrimaryColor)
            it.setHintTextColor(textSecondaryColor)
            it.background.setTint(textSecondaryColor) // Simple tint for background
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.applyStarryBackground()
        dialog?.window?.let { window ->
            val metrics = resources.displayMetrics
            val isTablet = metrics.widthPixels > metrics.heightPixels && metrics.widthPixels > 1200
            val width = if (isTablet) {
                (metrics.widthPixels * 0.60).toInt()
            } else {
                WindowManager.LayoutParams.MATCH_PARENT
            }
            window.setLayout(width, WindowManager.LayoutParams.MATCH_PARENT)
            window.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
    }

    // CHAPTER ADAPTER FOR UNIFIED SCREEN
    private inner class ChapterNavigationAdapter(
        private val offsets: List<Int>,
        private val titles: List<String>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ChapterNavigationAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvChapterTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chapter_navigation, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val offset = offsets[position]
            holder.tvTitle.text = titles.getOrNull(position) ?: "Глава ${position + 1}"

            // Map and style backgrounds matching activeTheme
            val itemBgHex = when (activeTheme) {
                "light", "beige" -> "#EFE9E2"
                "sepia", "sepia_contrast" -> "#EADCB9"
                "contrast" -> "#1A1A1A"
                else -> "#2A1A3E" // dark
            }
            val textPrimaryHex = when (activeTheme) {
                "light", "beige" -> "#2C3E50"
                "sepia", "sepia_contrast" -> "#5B3A29"
                "contrast" -> "#FFFFFF"
                else -> "#E8D8F0"
            }

            val itemBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor(itemBgHex))
            }
            holder.itemView.background = itemBg
            holder.tvTitle.setTextColor(Color.parseColor(textPrimaryHex))

            holder.itemView.setOnClickListener { onClick(offset) }
        }

        override fun getItemCount(): Int = offsets.size
    }
}
