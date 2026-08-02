package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.BookmarkDatabase
import com.nightread.app.data.BookmarkEntity
import com.nightread.app.data.BookmarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarksFragment : Fragment(R.layout.fragment_bookmarks) {

    private lateinit var rvBookmarks: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var tvBookmarkCount: TextView
    private lateinit var adapter: BookmarkAdapter

    companion object {
        fun newInstance(): BookmarksFragment {
            return BookmarksFragment()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        GalaxyBgHelper.applyBackground(view)

        val btnMenu = view.findViewById<ImageButton>(R.id.header_btn_left) ?: view.findViewById<ImageButton>(R.id.btnMenu)
        view.findViewById<TextView>(R.id.header_title)?.text = "Закладки"
        btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        tvBookmarkCount = view.findViewById(R.id.header_subtitle)
        tvBookmarkCount.visibility = View.VISIBLE
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        rvBookmarks = view.findViewById(R.id.rvBookmarks)
        rvBookmarks.layoutManager = LinearLayoutManager(requireContext())

        val repository = BookmarkRepository(BookmarkDatabase.getDatabase(requireContext()).bookmarkDao())

        adapter = BookmarkAdapter(
            onBookmarkClicked = { bookmark ->
                val intent = Intent(requireContext(), BookReaderActivity::class.java).apply {
                    putExtra("BOOK_SHA1", bookmark.bookSha1)
                    putExtra("TARGET_OFFSET", bookmark.charOffset)
                }
                startActivity(intent)
                activity?.overridePendingTransition(R.anim.fade_in_custom, R.anim.fade_out_custom)
            },
            onBookmarkDeleteClicked = { bookmark ->
                lifecycleScope.launch {
                    repository.deleteBookmark(bookmark)
                    withContext(Dispatchers.Main) {
                        CustomToast.show(requireContext(), "Закладка удалена")
                    }
                }
            }
        )
        rvBookmarks.adapter = adapter

        // Collect Bookmarks from Room Flow
        viewLifecycleOwner.lifecycleScope.launch {
            repository.allBookmarks.collectLatest { bookmarks ->
                if (bookmarks.isEmpty()) {
                    rvBookmarks.visibility = View.GONE
                    layoutEmptyState.visibility = View.VISIBLE
                    tvBookmarkCount.text = "0 закладок"
                } else {
                    rvBookmarks.visibility = View.VISIBLE
                    layoutEmptyState.visibility = View.GONE
                    tvBookmarkCount.text = "${bookmarks.size} закладок"
                    adapter.submitList(bookmarks)
                }
            }
        }
    }
}
