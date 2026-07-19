package com.nightread.app.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import com.nightread.app.data.BookmarkDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarksLibraryFragment : Fragment(R.layout.fragment_bookmarks_library) {

    private lateinit var rvBooks: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var adapter: BookAdapter

    companion object {
        fun newInstance(): BookmarksLibraryFragment {
            return BookmarksLibraryFragment()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnMenu = view.findViewById<ImageButton>(R.id.btnMenu)
        btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        rvBooks = view.findViewById(R.id.rvBooks)
        rvBooks.layoutManager = GridLayoutManager(requireContext(), 3)

        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book, _ ->
                // Show bookmarks for this book in a bottom sheet
                BookmarksListBottomSheet.newInstance(book.sha1).show(parentFragmentManager, "bookmarks_list")
            },
            onDeleteBook = { } // Not needed here
        )
        rvBooks.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val db = BookmarkDatabase.getDatabase(requireContext())
            val appDb = AppDatabase.getDatabase(requireContext())
            
            db.bookmarkDao().getBookSha1sWithBookmarks().collectLatest { sha1s ->
                val books = withContext(Dispatchers.IO) {
                    sha1s.mapNotNull { appDb.bookDao().getBookBySha1(it) }
                }
                
                if (books.isEmpty()) {
                    rvBooks.visibility = View.GONE
                    layoutEmptyState.visibility = View.VISIBLE
                } else {
                    rvBooks.visibility = View.VISIBLE
                    layoutEmptyState.visibility = View.GONE
                    adapter.updateData(books)
                }
            }
        }
    }
}
