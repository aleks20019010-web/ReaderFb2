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
        activeTheme = SettingsManager.getTheme(context)

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
            }
        )
        rvBookmarks.adapter = adapter

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
                if (bookmarks.isEmpty()) {
                    rvBookmarks.visibility = View.GONE
                    layoutEmptyState.visibility = View.VISIBLE
                } else {
                    rvBookmarks.visibility = View.VISIBLE
                    layoutEmptyState.visibility = View.GONE
                    adapter.submitList(bookmarks)
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

    private fun applyThemeColors(themeKey: String, rootView: View) {
        val cardBgHex: String
        val accentHex: String
        val textPrimaryHex: String
        val textSecondaryHex: String
        val dividerHex: String

        when (themeKey) {
            "light", "beige" -> {
                cardBgHex = "#FAF6F0"
                accentHex = "#D35400"
                textPrimaryHex = "#2C3E50"
                textSecondaryHex = "#7F8C8D"
                dividerHex = "#E0D5C1"
            }
            "sepia", "sepia_contrast" -> {
                cardBgHex = "#F4ECD8"
                accentHex = "#8E44AD"
                textPrimaryHex = "#5B3A29"
                textSecondaryHex = "#8F7365"
                dividerHex = "#D5C5B5"
            }
            "contrast" -> {
                cardBgHex = "#000000"
                accentHex = "#FFFFFF"
                textPrimaryHex = "#FFFFFF"
                textSecondaryHex = "#AAAAAA"
                dividerHex = "#333333"
            }
            else -> { // "dark" or default
                cardBgHex = "#1A0D2A"
                accentHex = "#9B59B6"
                textPrimaryHex = "#E8D8F0"
                textSecondaryHex = "#B8A0C8"
                dividerHex = "#3A2A4E"
            }
        }

        val cardBgColor = Color.parseColor(cardBgHex)
        val accentColor = Color.parseColor(accentHex)
        val textPrimaryColor = Color.parseColor(textPrimaryHex)
        val textSecondaryColor = Color.parseColor(textSecondaryHex)
        val dividerColor = Color.parseColor(dividerHex)

        // Apply background to Card Root
        val cardRoot = rootView.findViewById<androidx.cardview.widget.CardView>(R.id.bookmarksCardRoot)
        cardRoot?.setCardBackgroundColor(cardBgColor)

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
