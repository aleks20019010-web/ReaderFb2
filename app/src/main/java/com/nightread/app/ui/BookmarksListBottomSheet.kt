package com.nightread.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.BookmarkDatabase
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarksListBottomSheet : DialogFragment() {

    private lateinit var rvBookmarks: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var ivEmptyIcon: ImageView
    private lateinit var dragHandle: View
    private lateinit var tvBookmarksTitle: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var dividerTop: View
    private lateinit var adapter: BookBookmarkAdapter

    private lateinit var etSearchBookmarks: android.widget.EditText
    private var allBookmarks: List<BookmarkEntity> = emptyList()
    private var bookSha1: String = ""
    private var activeTheme: String = "dark"

    companion object {
        fun newInstance(bookSha1: String): BookmarksListBottomSheet {
            val fragment = BookmarksListBottomSheet()
            val args = Bundle()
            args.putString("BOOK_SHA1", bookSha1)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.SettingsDialogStyle)
        bookSha1 = arguments?.getString("BOOK_SHA1") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_bookmarks_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setCanceledOnTouchOutside(true)

        val context = requireContext()
        activeTheme = SettingsManager.getReadingTheme(context)

        // Find views
        rvBookmarks = view.findViewById(R.id.rvBookmarks)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        tvEmptyTitle = view.findViewById(R.id.tvEmptyTitle)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        ivEmptyIcon = view.findViewById(R.id.ivEmptyIcon)
        dragHandle = view.findViewById(R.id.dragHandle)
        tvBookmarksTitle = view.findViewById(R.id.tvBookmarksTitle)
        btnClose = view.findViewById(R.id.btnClose)
        dividerTop = view.findViewById(R.id.dividerTop)

        btnClose.setOnClickListener {
            dismiss()
        }

        etSearchBookmarks = view.findViewById(R.id.etSearchBookmarks)
        rvBookmarks.layoutManager = LinearLayoutManager(context)
        adapter = BookBookmarkAdapter(
            themeKey = activeTheme,
            onBookmarkClicked = { bookmark ->
                // Navigate to this bookmark in BookReaderActivity
                dismiss()
                val currentActivity = activity
                if (currentActivity is BookReaderActivity) {
                    currentActivity.navigateToOffset(bookmark.charOffset)
                } else if (currentActivity is MainActivity) {
                    val intent = android.content.Intent(currentActivity, BookReaderActivity::class.java).apply {
                        putExtra("BOOK_SHA1", bookmark.bookSha1)
                        putExtra("NAVIGATE_TO_OFFSET", bookmark.charOffset)
                    }
                    currentActivity.startActivity(intent)
                }
            },
            onBookmarkDeleteClicked = { bookmark ->
                confirmAndDeleteBookmark(bookmark)
            },
            onBookmarkLongClicked = { bookmark ->
                confirmAndDeleteBookmark(bookmark)
            },
            onBookmarkEditClicked = { bookmark ->
                showEditBookmarkDialog(bookmark)
            },
            onBookmarkShareClicked = { bookmark ->
                shareBookmark(bookmark)
            }
        )
        rvBookmarks.adapter = adapter

        etSearchBookmarks.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().lowercase()
                val filtered = allBookmarks.filter {
                    it.snippet.lowercase().contains(query) || "страница ${it.pageIndex + 1}".contains(query)
                }
                adapter.submitList(filtered)
                layoutEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
        })

        // Setup swipe to delete gesture
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val bookmark = adapter.getBookmarkAt(position)
                if (bookmark != null) {
                    confirmAndDeleteBookmark(bookmark) {
                        adapter.notifyItemChanged(position)
                    }
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(rvBookmarks)

        // Stylize bottom sheet view elements dynamically
        applyThemeColors(activeTheme, view)

        // Observe bookmarks
        lifecycleScope.launch {
            val db = BookmarkDatabase.getDatabase(context)
            db.bookmarkDao().getBookmarksForBook(bookSha1).collectLatest { bookmarks ->
                allBookmarks = bookmarks
                val query = etSearchBookmarks.text.toString().lowercase()
                val filtered = bookmarks.filter {
                    it.snippet.lowercase().contains(query) || "страница ${it.pageIndex + 1}".contains(query)
                }
                if (filtered.isEmpty()) {
                    rvBookmarks.visibility = View.GONE
                    layoutEmptyState.visibility = View.VISIBLE
                } else {
                    rvBookmarks.visibility = View.VISIBLE
                    layoutEmptyState.visibility = View.GONE
                    adapter.submitList(filtered)
                }
            }
        }
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

    private fun showEditBookmarkDialog(bookmark: BookmarkEntity) {
        val cardBgHex = "#12141A"
        val accentHex = "#E5C07B"
        val textPrimaryHex = "#F4F5F7"
        val textSecondaryHex = "#9BA1AE"

        val cardBgColor = Color.parseColor(cardBgHex)
        val accentColor = Color.parseColor(accentHex)
        val textPrimaryColor = Color.parseColor(textPrimaryHex)
        val textSecondaryColor = Color.parseColor(textSecondaryHex)

        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.SettingsDialogStyle)
        builder.setTitle("Редактировать закладку")

        val input = android.widget.EditText(requireContext()).apply {
            setText(bookmark.snippet)
            setSelection(bookmark.snippet.length)
            setTextColor(textPrimaryColor)
            setHintTextColor(textSecondaryColor)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 8f
                setColor(Color.parseColor(when (activeTheme) {
                    "light", "beige" -> "#10000000"
                    "sepia", "sepia_contrast" -> "#0A000000"
                    "contrast" -> "#22ffffff"
                    else -> "#15ffffff"
                }))
            }
            setPadding(32, 32, 32, 32)
        }

        val container = android.widget.FrameLayout(requireContext()).apply {
            addView(input)
            setPadding(32, 16, 32, 16)
        }
        builder.setView(container)

        builder.setPositiveButton("Сохранить") { dialog, _ ->
            val updatedText = input.text.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                val db = BookmarkDatabase.getDatabase(requireContext())
                val updatedBookmark = bookmark.copy(snippet = updatedText, timestamp = System.currentTimeMillis())
                db.bookmarkDao().insertBookmark(updatedBookmark)
                withContext(Dispatchers.Main) {
                    CustomToast.show(requireContext(), "Закладка обновлена")
                }
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(cardBgColor))
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(accentColor)
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.setTextColor(textSecondaryColor)
        }
        dialog.show()
    }

    private fun shareBookmark(bookmark: BookmarkEntity) {
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Закладка из книги")
            val shareText = "Закладка из книги \"${bookmark.bookTitle}\" (Страница ${bookmark.pageIndex + 1}):\n\n${bookmark.snippet}"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "Поделиться закладкой"))
    }

    private fun applyThemeColors(themeKey: String, rootView: View) {
        val cardBgHex = "#12141A"
        val accentHex = "#E5C07B"
        val textPrimaryHex = "#F4F5F7"
        val textSecondaryHex = "#9BA1AE"
        val dividerHex = "#282D38"

        val cardBgColor = Color.parseColor(cardBgHex)
        val accentColor = Color.parseColor(accentHex)
        val textPrimaryColor = Color.parseColor(textPrimaryHex)
        val textSecondaryColor = Color.parseColor(textSecondaryHex)
        val dividerColor = Color.parseColor(dividerHex)

        // Apply background to Card Root + subtle LiquidWave shimmer
        val cardRoot = rootView.findViewById<androidx.cardview.widget.CardView>(R.id.bookmarksCardRoot)
        cardRoot?.setCardBackgroundColor(cardBgColor)
        if (cardRoot != null && cardRoot.findViewWithTag<View>("liquid_wave_bg") == null) {
            val waveBg = LiquidWaveBackgroundView(requireContext()).apply {
                tag = "liquid_wave_bg"
                alpha = 0.22f
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            cardRoot.addView(waveBg, 0)
        }

        // Apply text colors
        tvBookmarksTitle.setTextColor(textPrimaryColor)
        tvEmptyTitle.setTextColor(textPrimaryColor)
        tvEmptyMessage.setTextColor(textSecondaryColor)

        // Tint close button & empty state icon & drag handle
        btnClose.imageTintList = ColorStateList.valueOf(textPrimaryColor)
        ivEmptyIcon.imageTintList = ColorStateList.valueOf(accentColor)
        
        val dragBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f
            setColor(dividerColor)
        }
        dragHandle.background = dragBg
        dividerTop.setBackgroundColor(dividerColor)

        // Style Search Input dynamically
        etSearchBookmarks.setTextColor(textPrimaryColor)
        etSearchBookmarks.setHintTextColor(textSecondaryColor)
        etSearchBookmarks.backgroundTintList = null
        etSearchBookmarks.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.parseColor("#1C2029"))
            setStroke(2, dividerColor)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.applyStarryBackground()
        dialog?.window?.let { window ->
            val metrics = resources.displayMetrics
            
            // Width: 95% of screen size on mobile/portrait, 50% on tablet/landscape
            val isTablet = metrics.widthPixels > metrics.heightPixels && metrics.widthPixels > 1200
            val width = if (isTablet) {
                (metrics.widthPixels * 0.50).toInt()
            } else {
                (metrics.widthPixels * 0.95).toInt()
            }

            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)

            val params = window.attributes
            params.y = 24 // offset from bottom edge for beautiful floating card effect
            window.attributes = params

            // Beautiful slide up animation from bottom
            window.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
    }
}
