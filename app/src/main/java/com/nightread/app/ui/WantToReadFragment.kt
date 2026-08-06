package com.nightread.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightread.app.MainActivity
import com.nightread.app.R
import com.nightread.app.data.AppDatabase
import kotlinx.coroutines.launch

class WantToReadFragment : Fragment() {

    private lateinit var rvWantToRead: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var adapter: BookAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_want_to_read, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        GalaxyBgHelper.applyBackground(view)

        val glassHeader = view.findViewById<View>(R.id.glassHeader)
        glassHeader.findViewById<TextView>(R.id.header_title).text = getString(R.string.drawer_want_to_read)
        val btnLeft = glassHeader.findViewById<ImageButton>(R.id.header_btn_left)
        btnLeft.setImageResource(R.drawable.ic_action_menu)
        btnLeft.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        rvWantToRead = view.findViewById(R.id.rvWantToRead)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        rvWantToRead.layoutManager = GridLayoutManager(requireContext(), 3)

        adapter = BookAdapter(
            books = emptyList(),
            onOpenBook = { book, coverView ->
                val intent = Intent(requireContext(), BookDetailActivity::class.java).apply {
                    putExtra("BOOK_SHA1", book.sha1)
                }
                val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(),
                    coverView,
                    "cover_${book.sha1}"
                )
                startActivity(intent, options.toBundle())
            }
        )
        rvWantToRead.adapter = adapter

        loadWantToReadBooks()
    }

    private fun loadWantToReadBooks() {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.bookDao().getWantToReadBooks().collect { books ->
                adapter.updateData(books)
                if (books.isEmpty()) {
                    layoutEmptyState.visibility = View.VISIBLE
                    rvWantToRead.visibility = View.GONE
                } else {
                    layoutEmptyState.visibility = View.GONE
                    rvWantToRead.visibility = View.VISIBLE
                }
            }
        }
    }
}
